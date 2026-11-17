package com.example

import com.example.data.remote.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `password hasher verifies matching password and rejects wrong password`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword("kkhm@2026", salt)
        assertTrue(salt.length >= 16)
        assertEquals(64, hash.length)
        assertTrue(PasswordHasher.verifyPassword("kkhm@2026", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("wrong-pwd", salt, hash))
    }

    @Test
    fun `username normalization enforces lowercase safe id`() {
        assertEquals("student_1", PasswordHasher.normalizeUsername(" Student_1! "))
        assertTrue(PasswordHasher.isValidUsername("guide1"))
        assertFalse(PasswordHasher.isValidUsername("ab"))
    }
}
