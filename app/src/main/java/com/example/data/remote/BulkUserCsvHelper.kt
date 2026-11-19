package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.AccountStatus
import com.example.data.model.CIC_DAYS
import com.example.data.model.CicStream
import com.example.data.model.GuideType
import com.example.data.model.UserRole
import java.io.File
import java.io.FileOutputStream

data class BulkImportRow(
    val rowNumber: Int,
    val username: String,
    val displayName: String,
    val passwordOrNull: String?,
    val role: UserRole,
    val accountStatus: AccountStatus,
    val cicAdNo: String,
    val classDepartment: String,
    val stream: String,
    val assignedGuideUsername: String,
    val phone: String,
    val email: String = "",
    val guideType: String = "",
    val institution: String = "",
    val academicYear: String = "",
    val semester: String = "",
    val offCourseSubject: String = "",
    val exemptedSubject: String = "",
    val credit: Long = 4L,
    val timetableSlots: List<String> = emptyList(),
    val proposalAppNo: String = "",
    val referenceBooks: String = "",
    val isExistingUpdate: Boolean,
    val validationError: String? = null
) {
    val isValid: Boolean get() = validationError == null
    val hasOffCourseEnrollment: Boolean get() = role == UserRole.STUDENT && offCourseSubject.isNotBlank()
}

data class BulkCsvParseResult(
    val fileName: String,
    val rows: List<BulkImportRow>,
    val fatalError: String? = null
) {
    val validRows: List<BulkImportRow> get() = rows.filter { it.isValid }
    val invalidRows: List<BulkImportRow> get() = rows.filterNot { it.isValid }
    val newCount: Int get() = validRows.count { !it.isExistingUpdate }
    val updateCount: Int get() = validRows.count { it.isExistingUpdate }
    val offCourseEnrollCount: Int get() = validRows.count { it.hasOffCourseEnrollment }
}

data class BulkImportUiState(
    val isImporting: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val enrolledRecordsCount: Int = 0,
    val failedRows: List<Pair<String, String>> = emptyList(),
    val completed: Boolean = false
)

object BulkUserCsvHelper {

    const val TEMPLATE_FILE_NAME = "kkhm_bulk_users_offcourse_template.csv"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L
    private const val MAX_ROWS = 500

    val EXPECTED_COLUMNS = listOf(
        "username",
        "display_name",
        "password",
        "role",
        "account_status",
        "cic_ad_no",
        "class_department",
        "stream",
        "assigned_guide_username",
        "phone",
        "email",
        "guide_type",
        "institution",
        "academic_year",
        "semester",
        "off_course_subject",
        "exempted_subject",
        "credit",
        "timetable_slots",
        "proposal_app_no",
        "reference_books"
    )

    fun buildTemplateCsv(): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for clean Excel / Sheets opening
        sb.appendLine(EXPECTED_COLUMNS.joinToString(","))
        sb.appendLine("guide_kareem,Usthad Abdul Kareem,guide@2026,GUIDE,ACTIVE,,Arabic & Islamic Studies,,,9847000001,kareem@kkhm.edu,COLLEGE_FACULTY,KKHM Islamic & Arts College,,,,,,,,")
        sb.appendLine("ext_guide_salim,Dr. Salim Nadwi,guide@2026,GUIDE,ACTIVE,,External Research Mentor,,,9847000010,salim@external.org,EXTERNAL,Calicut Arabic Research Centre,,,,,,,,")
        sb.appendLine("prin_hussain,Dr. Hussain Maulavi,prin@2026,PRINCIPAL,ACTIVE,,Administration,,,9847000002,principal@kkhm.edu,,KKHM Islamic & Arts College,,,,,,,,")
        sb.appendLine("student_aysha,Aysha Nasrin,kkhm@2026,STUDENT,ACTIVE,CIC-2026-101,Final Wafiyya,Wafiyya,guide_kareem,9847000003,,,,2026 - 2027,Nov,Comparative Islamic Jurisprudence,Classical Usul II,4,Monday:2;Wednesday:5,OC-2026-01,Al-Muwafaqat")
        sb.appendLine("student_bilal,Muhammed Bilal,kkhm@2026,STUDENT,ACTIVE,CIC-2026-102,Second Wafy Arts,Wafy Arts,ext_guide_salim,9847000004,,,,2026 - 2027,Nov,Arabic Rhetoric & Journalism,Modern Prose Paper,4,Tuesday:3;Thursday:6,OC-2026-02,Al-Balagha al-Wadiha")
        return sb.toString()
    }

    fun writeTemplateToUri(context: Context, uri: Uri): Result<Unit> {
        return try {
            val content = buildTemplateCsv().toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content)
                out.flush()
            } ?: return Result.failure(IllegalStateException("Could not open destination file."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareTemplateCsv(context: Context): Result<Unit> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val templateFile = File(exportDir, TEMPLATE_FILE_NAME)
            FileOutputStream(templateFile).use { out ->
                out.write(buildTemplateCsv().toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                templateFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KKHM Off-Course Bulk Users & Courses CSV Template")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share KKHM Bulk Import Template (.csv)")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readAndParseCsvFromUri(
        context: Context,
        uri: Uri,
        existingUsernames: Set<String>
    ): Result<BulkCsvParseResult> {
        return try {
            var displayName = "uploaded_users.csv"
            var fileSize = -1L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
                }
            }
            if (fileSize > MAX_FILE_BYTES) {
                return Result.success(
                    BulkCsvParseResult(
                        fileName = displayName,
                        rows = emptyList(),
                        fatalError = "File is larger than 2 MB. Please split into batches of up to $MAX_ROWS users."
                    )
                )
            }
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size.toLong() > MAX_FILE_BYTES) {
                    return Result.success(
                        BulkCsvParseResult(
                            fileName = displayName,
                            rows = emptyList(),
                            fatalError = "File is larger than 2 MB. Please split into smaller CSV files."
                        )
                    )
                }
                bytes.toString(Charsets.UTF_8)
            } ?: return Result.failure(IllegalStateException("Unable to read selected file."))

            Result.success(parseCsvText(text, existingUsernames, displayName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseCsvText(
        csvContent: String,
        existingUsernames: Set<String>,
        fileName: String = "uploaded_users.csv"
    ): BulkCsvParseResult {
        val cleaned = csvContent.removePrefix("\uFEFF")
        val rawRecords = parseRfc4180Rows(cleaned)
            .filter { row -> row.any { it.isNotBlank() } }

        if (rawRecords.isEmpty()) {
            return BulkCsvParseResult(
                fileName = fileName,
                rows = emptyList(),
                fatalError = "CSV file is empty. Please fill in the downloaded template first."
            )
        }

        val headerRow = rawRecords.first().map { normalizeHeader(it) }
        val colIndex = mapHeaderIndices(headerRow)
        if (!colIndex.containsKey("username") || !colIndex.containsKey("display_name")) {
            return BulkCsvParseResult(
                fileName = fileName,
                rows = emptyList(),
                fatalError = "Missing required columns 'username' and 'display_name'. Please use the official KKHM CSV template."
            )
        }

        val dataRows = rawRecords.drop(1)
        if (dataRows.isEmpty()) {
            return BulkCsvParseResult(
                fileName = fileName,
                rows = emptyList(),
                fatalError = "Template contains only the header row and no user rows."
            )
        }
        if (dataRows.size > MAX_ROWS) {
            return BulkCsvParseResult(
                fileName = fileName,
                rows = emptyList(),
                fatalError = "Found ${dataRows.size} rows. Maximum allowed per upload is $MAX_ROWS users."
            )
        }

        val seenUsernames = mutableSetOf<String>()
        val existingSet = existingUsernames.map { it.lowercase() }.toSet()

        val parsed = dataRows.mapIndexed { index, cols ->
            val csvLineNumber = index + 2
            fun cell(key: String): String {
                val idx = colIndex[key] ?: return ""
                return cols.getOrNull(idx)?.trim().orEmpty()
            }

            val rawUsername = cell("username")
            val username = PasswordHasher.normalizeUsername(rawUsername)
            val displayName = cell("display_name").take(100)
            val rawPassword = cell("password")
            val rawRole = cell("role")
            val rawStatus = cell("account_status")
            val cicAdNo = cell("cic_ad_no").take(40)
            val classDept = cell("class_department").take(80)
            val rawStream = cell("stream")
            val guideUser = PasswordHasher.normalizeUsername(cell("assigned_guide_username")).take(40)
            val phone = cell("phone").take(30)
            val email = cell("email").lowercase().take(120)
            val rawGuideType = cell("guide_type")
            val institution = cell("institution").take(160)
            val academicYear = cell("academic_year").take(20)
            val semester = cell("semester").take(30)
            val offCourseSubject = cell("off_course_subject").take(160)
            val exemptedSubject = cell("exempted_subject").take(160)
            val rawCredit = cell("credit")
            val rawSlots = cell("timetable_slots")
            val proposalAppNo = cell("proposal_app_no").take(60)
            val referenceBooks = cell("reference_books").take(600)

            val isExisting = username in existingSet
            val errors = mutableListOf<String>()

            if (!PasswordHasher.isValidUsername(username)) {
                errors += "Invalid username '$rawUsername' (use 3-40 chars: a-z, 0-9, _, -)"
            } else if (username == "admin") {
                errors += "Primary 'admin' account cannot be overwritten via bulk CSV"
            } else if (!seenUsernames.add(username)) {
                errors += "Duplicate username '@$username' in CSV"
            }

            if (displayName.isBlank()) {
                errors += "Full name is required"
            }

            val resolvedRole = parseRole(rawRole)
            if (resolvedRole == null) {
                errors += "Invalid role '$rawRole' (use STUDENT, GUIDE, PRINCIPAL, or ADMIN)"
            }

            val resolvedStatus = parseStatus(rawStatus)
            if (resolvedStatus == null) {
                errors += "Invalid status '$rawStatus' (use ACTIVE or SUSPENDED)"
            }

            if (!isExisting && rawPassword.length < 4) {
                errors += "Password must be at least 4 characters for new user"
            } else if (isExisting && rawPassword.isNotBlank() && rawPassword.length < 4) {
                errors += "New password must be at least 4 characters"
            }

            val roleSafe = resolvedRole ?: UserRole.STUDENT
            val resolvedStream = if (roleSafe == UserRole.STUDENT) {
                if (rawStream.isBlank()) {
                    CicStream.WAFY.label
                } else {
                    val matchedStream = CicStream.entries.firstOrNull {
                        it.label.equals(rawStream, ignoreCase = true) ||
                            it.name.equals(rawStream.replace(" ", "_"), ignoreCase = true)
                    }
                    if (matchedStream == null) {
                        errors += "Invalid CIC stream '$rawStream'"
                        CicStream.WAFY.label
                    } else {
                        matchedStream.label
                    }
                }
            } else {
                ""
            }

            val resolvedGuideType = if (roleSafe == UserRole.GUIDE) {
                when (rawGuideType.trim().uppercase()) {
                    "", "COLLEGE", "COLLEGE_FACULTY", "FACULTY", "TEACHER", "COLLEGE TEACHER" -> GuideType.COLLEGE_FACULTY.name
                    "EXTERNAL", "NON_COLLEGE", "NON-COLLEGE", "GUEST" -> GuideType.EXTERNAL.name
                    else -> GuideType.COLLEGE_FACULTY.name
                }
            } else {
                ""
            }

            val parsedSlots = parseTimetableSlots(rawSlots)
            val parsedCredit = (rawCredit.toLongOrNull() ?: 4L).coerceIn(0L, 20L)

            BulkImportRow(
                rowNumber = csvLineNumber,
                username = username.ifBlank { rawUsername },
                displayName = displayName,
                passwordOrNull = rawPassword.ifBlank { null },
                role = roleSafe,
                accountStatus = resolvedStatus ?: AccountStatus.ACTIVE,
                cicAdNo = cicAdNo,
                classDepartment = classDept,
                stream = resolvedStream,
                assignedGuideUsername = if (roleSafe == UserRole.STUDENT) guideUser else "",
                phone = phone,
                email = email,
                guideType = resolvedGuideType,
                institution = institution,
                academicYear = academicYear,
                semester = semester,
                offCourseSubject = offCourseSubject,
                exemptedSubject = exemptedSubject,
                credit = parsedCredit,
                timetableSlots = parsedSlots,
                proposalAppNo = proposalAppNo,
                referenceBooks = referenceBooks,
                isExistingUpdate = isExisting,
                validationError = errors.firstOrNull()
            )
        }

        return BulkCsvParseResult(
            fileName = fileName,
            rows = parsed,
            fatalError = null
        )
    }

    fun parseTimetableSlots(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';', '|', '/')
            .mapNotNull { token ->
                val parts = token.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val dayRaw = parts[0].trim()
                val matchedDay = CIC_DAYS.firstOrNull {
                    it.equals(dayRaw, ignoreCase = true) ||
                        it.take(3).equals(dayRaw.take(3), ignoreCase = true)
                } ?: return@mapNotNull null
                val period = parts[1].trim().removePrefix("P").removePrefix("p").toIntOrNull()
                    ?: return@mapNotNull null
                if (period !in 1..8) return@mapNotNull null
                "$matchedDay:$period"
            }
            .distinct()
            .take(49)
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase().replace(Regex("[\\s-]+"), "_")

    private fun mapHeaderIndices(headers: List<String>): Map<String, Int> {
        val aliases = mapOf(
            "username" to listOf("username", "user_name", "user_id", "user"),
            "display_name" to listOf("display_name", "full_name", "name"),
            "password" to listOf("password", "pwd", "pass"),
            "role" to listOf("role", "user_role"),
            "account_status" to listOf("account_status", "status"),
            "cic_ad_no" to listOf("cic_ad_no", "cic_no", "cic_adno", "admission_no"),
            "class_department" to listOf("class_department", "department", "class", "class_dept"),
            "stream" to listOf("stream", "cic_stream"),
            "assigned_guide_username" to listOf("assigned_guide_username", "guide_username", "guide", "mentor_username"),
            "phone" to listOf("phone", "mobile", "whatsapp"),
            "email" to listOf("email", "mail", "email_id"),
            "guide_type" to listOf("guide_type", "mentor_type"),
            "institution" to listOf("institution", "college", "organization"),
            "academic_year" to listOf("academic_year", "year"),
            "semester" to listOf("semester", "sem"),
            "off_course_subject" to listOf("off_course_subject", "subject_title", "off_course", "subject"),
            "exempted_subject" to listOf("exempted_subject", "dropped_subject", "exempted", "skipped_subject"),
            "credit" to listOf("credit", "credits"),
            "timetable_slots" to listOf("timetable_slots", "timetable", "slots", "periods"),
            "proposal_app_no" to listOf("proposal_app_no", "proposal_no", "app_no"),
            "reference_books" to listOf("reference_books", "references", "books")
        )
        val result = mutableMapOf<String, Int>()
        aliases.forEach { (canonical, keys) ->
            val idx = headers.indexOfFirst { it in keys }
            if (idx >= 0) result[canonical] = idx
        }
        return result
    }

    private fun parseRole(raw: String): UserRole? {
        val clean = raw.trim().lowercase()
        return when (clean) {
            "", "student" -> if (clean.isEmpty()) null else UserRole.STUDENT
            "guide", "guide (mentor)", "mentor", "teacher", "external guide" -> UserRole.GUIDE
            "principal", "principal / dean", "dean" -> UserRole.PRINCIPAL
            "admin", "college admin" -> UserRole.ADMIN
            else -> null
        }
    }

    private fun parseStatus(raw: String): AccountStatus? {
        val clean = raw.trim().lowercase()
        return when (clean) {
            "", "active" -> AccountStatus.ACTIVE
            "suspended", "inactive", "disabled" -> AccountStatus.SUSPENDED
            else -> null
        }
    }

    private fun parseRfc4180Rows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.clear()
                    }
                    '\r' -> {
                        if (i + 1 < input.length && input[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }
        return rows
    }
}
