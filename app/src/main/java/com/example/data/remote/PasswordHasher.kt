package com.example.data.remote

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object PasswordHasher {
    private val secureRandom = SecureRandom()

    fun normalizeUsername(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "")

    fun isValidUsername(username: String): Boolean =
        username.length in 3..40 && username.matches(Regex("^[a-z0-9_\\-]+$"))

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$password".toByteArray(StandardCharsets.UTF_8)
        return digest.digest(input).toHex()
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPassword(password, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            expectedHash.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
