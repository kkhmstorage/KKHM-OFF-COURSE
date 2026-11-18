package com.example

import com.example.data.model.CicStream
import com.example.data.model.UserRole
import com.example.data.remote.BulkUserCsvHelper
import com.example.data.remote.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `bulk csv template round trips cleanly with valid rows`() {
        val template = BulkUserCsvHelper.buildTemplateCsv()
        val result = BulkUserCsvHelper.parseCsvText(template, existingUsernames = emptySet())
        assertNull(result.fatalError)
        assertEquals(4, result.rows.size)
        assertEquals(4, result.validRows.size)
        assertTrue(result.invalidRows.isEmpty())
        assertEquals(UserRole.GUIDE, result.rows[0].role)
        assertEquals(CicStream.WAFIYYA.label, result.rows[2].stream)
    }

    @Test
    fun `bulk csv parser flags duplicates, short passwords, and admin override while keeping existing updates valid`() {
        val csv = """
            username,display_name,password,role,account_status,cic_ad_no,class_department,stream,assigned_guide_username,phone
            existing_student,"Nasrin, A",,STUDENT,ACTIVE,CIC-10,Final Wafiyya,Wafiyya,guide1,9847000010
            new_student,Rashid K,12,STUDENT,ACTIVE,CIC-11,First Wafy,Wafy,guide1,
            existing_student,Duplicate User,kkhm123,STUDENT,ACTIVE,CIC-12,Wafy,Wafy,guide1,
            admin,Blocked Admin,admin123,ADMIN,ACTIVE,,,,,
        """.trimIndent()
        val result = BulkUserCsvHelper.parseCsvText(csv, existingUsernames = setOf("existing_student"))
        assertNull(result.fatalError)
        assertEquals(4, result.rows.size)
        assertTrue(result.rows[0].isValid)
        assertTrue(result.rows[0].isExistingUpdate)
        assertEquals("Nasrin, A", result.rows[0].displayName)
        assertFalse(result.rows[1].isValid)
        assertNotNull(result.rows[1].validationError)
        assertFalse(result.rows[2].isValid)
        assertFalse(result.rows[3].isValid)
    }
}
