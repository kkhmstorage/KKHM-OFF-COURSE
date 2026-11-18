package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class RemoteAppRelease(
    val versionCode: Int,
    val versionName: String,
    val buildNumber: Int = 0,
    val commitSha: String = "",
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val apkUrl: String,
    val releasePageUrl: String
)

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : UpdateCheckUiState

    data class UpdateAvailable(
        val release: RemoteAppRelease,
        val currentVersionName: String,
        val currentVersionCode: Int,
        val isDismissedBanner: Boolean = false
    ) : UpdateCheckUiState

    data class Error(val message: String) : UpdateCheckUiState
}

object AppUpdateHelper {
    const val GITHUB_OWNER = "kkhmstorage"
    const val GITHUB_REPO = "KKHM-OFF-COURSE"
    const val DEFAULT_APK_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/latest/KKHM-Off-Course-debug.apk"
    const val DEFAULT_RELEASE_PAGE_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/latest"
    const val VERSION_JSON_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/latest/version.json"
    const val GITHUB_LATEST_TAG_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/latest"

    fun isUpdateAvailable(
        localVersionCode: Int,
        localBuildNumber: Int,
        localCommitSha: String,
        remote: RemoteAppRelease
    ): Boolean {
        if (remote.versionCode > 0 && localVersionCode > 0) {
            return remote.versionCode > localVersionCode
        }
        if (remote.buildNumber > 0 && localBuildNumber > 0) {
            return remote.buildNumber > localBuildNumber
        }
        if (remote.commitSha.isNotBlank() && localCommitSha.isNotBlank()) {
            return !remote.commitSha.equals(localCommitSha, ignoreCase = true)
        }
        return false
    }

    fun parseVersionJson(rawJson: String): RemoteAppRelease? {
        if (rawJson.isBlank()) return null
        val versionCode = extractJsonInt(rawJson, "versionCode") ?: return null
        val versionName = extractJsonString(rawJson, "versionName").ifBlank { "1.0.$versionCode" }
        val buildNumber = extractJsonInt(rawJson, "buildNumber") ?: 0
        val commitSha = extractJsonString(rawJson, "commitSha")
        val releaseNotes = extractJsonString(rawJson, "releaseNotes")
        val publishedAt = extractJsonString(rawJson, "publishedAt")
        val apkUrl = extractJsonString(rawJson, "apkUrl").ifBlank { DEFAULT_APK_URL }
        val releasePageUrl = extractJsonString(rawJson, "releasePageUrl").ifBlank { DEFAULT_RELEASE_PAGE_URL }
        return RemoteAppRelease(
            versionCode = versionCode,
            versionName = versionName,
            buildNumber = buildNumber,
            commitSha = commitSha,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            apkUrl = apkUrl,
            releasePageUrl = releasePageUrl
        )
    }

    fun parseGitHubReleaseApiJson(rawJson: String): RemoteAppRelease? {
        if (rawJson.isBlank()) return null
        val htmlUrl = extractJsonString(rawJson, "html_url").ifBlank { DEFAULT_RELEASE_PAGE_URL }
        val title = extractJsonString(rawJson, "name")
        val body = extractJsonString(rawJson, "body")
        val publishedAt = extractJsonString(rawJson, "published_at")

        val buildMatch = Regex("""Build\s+(\d+)""", RegexOption.IGNORE_CASE).find("$title $body")
        val buildNumber = buildMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val vcMatch = Regex("""Version\s*Code[:`\s]+(\d+)""", RegexOption.IGNORE_CASE).find(body)
        val versionCode = vcMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: if (buildNumber > 0) 2 + buildNumber else 0
        if (versionCode <= 0) return null

        val vnMatch = Regex("""v(\d+\.\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
        val versionName = vnMatch?.groupValues?.getOrNull(1)
            ?: if (buildNumber > 0) "1.1.$buildNumber" else "1.1"

        val apkMatch = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+\.apk)"""").find(rawJson)
        val apkUrl = apkMatch?.groupValues?.getOrNull(1) ?: DEFAULT_APK_URL

        return RemoteAppRelease(
            versionCode = versionCode,
            versionName = versionName,
            buildNumber = buildNumber,
            releaseNotes = body.ifBlank { title },
            publishedAt = publishedAt,
            apkUrl = apkUrl,
            releasePageUrl = htmlUrl
        )
    }

    fun openDownloadUrl(context: Context, url: String): Result<Unit> {
        return try {
            val clean = url.trim()
            if (!clean.startsWith("https://")) {
                return Result.failure(IllegalArgumentException("Invalid download URL"))
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = pattern.find(json)?.groupValues?.getOrNull(1) ?: return ""
        return raw
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\\\", "\\")
            .trim()
    }
}

class AppUpdateRepository {
    suspend fun fetchLatestRelease(): Result<RemoteAppRelease> = withContext(Dispatchers.IO) {
        try {
            val cacheBuster = System.currentTimeMillis()
            val versionJsonResult = httpGet("${AppUpdateHelper.VERSION_JSON_URL}?t=$cacheBuster")
            if (versionJsonResult != null) {
                val parsed = AppUpdateHelper.parseVersionJson(versionJsonResult)
                if (parsed != null) return@withContext Result.success(parsed)
            }

            val apiJsonResult = httpGet(
                url = AppUpdateHelper.GITHUB_LATEST_TAG_API_URL,
                acceptHeader = "application/vnd.github+json"
            )
            if (apiJsonResult != null) {
                val parsed = AppUpdateHelper.parseGitHubReleaseApiJson(apiJsonResult)
                if (parsed != null) return@withContext Result.success(parsed)
            }

            Result.failure(IllegalStateException("Could not find release metadata on GitHub."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun httpGet(url: String, acceptHeader: String = "application/json"): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "KKHM-Off-Course-Android")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
