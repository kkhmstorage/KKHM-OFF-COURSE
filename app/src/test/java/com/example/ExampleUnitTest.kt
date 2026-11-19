package com.example

import com.example.data.model.CicStream
import com.example.data.model.OffCourseRecord
import com.example.data.model.PeriodLogEntry
import com.example.data.model.UserRole
import com.example.data.model.WeeklyScheduleHelper
import com.example.data.model.WeeklyStudyReport
import com.example.data.remote.AppUpdateHelper
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
        assertEquals(5, result.rows.size)
        assertEquals(5, result.validRows.size)
        assertTrue(result.invalidRows.isEmpty())
        assertEquals(UserRole.GUIDE, result.rows[0].role)
        assertEquals("COLLEGE_FACULTY", result.rows[0].guideType)
        assertEquals("EXTERNAL", result.rows[1].guideType)
        assertEquals(CicStream.WAFIYYA.label, result.rows[3].stream)
        assertEquals(2, result.offCourseEnrollCount)
        assertEquals("Classical Usul II", result.rows[3].exemptedSubject)
        assertEquals(listOf("Monday:2", "Wednesday:5"), result.rows[3].timetableSlots)
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

    @Test
    fun `nextPendingWeek detects first unfilled week and caps at totalWeeks`() {
        assertEquals(1, WeeklyScheduleHelper.nextPendingWeek(emptyList()))
        val logged123 = listOf(
            WeeklyStudyReport(weekNumber = 1L),
            WeeklyStudyReport(weekNumber = 2L),
            WeeklyStudyReport(weekNumber = 4L)
        )
        assertEquals(3, WeeklyScheduleHelper.nextPendingWeek(logged123))
        val all18 = (1..18).map { WeeklyStudyReport(weekNumber = it.toLong()) }
        assertEquals(18, WeeklyScheduleHelper.nextPendingWeek(all18))
    }

    @Test
    fun `sequential guide approval lock blocks week N+1 until week N is VERIFIED`() {
        val w1Pending = WeeklyStudyReport(weekNumber = 1L, verificationStatus = "PENDING")
        val lockPending = WeeklyScheduleHelper.resolveWeeklyLockState(listOf(w1Pending))
        assertFalse(lockPending.canAddNextWeek)
        assertEquals(1L, lockPending.blockingUnverifiedReport?.weekNumber)
        assertTrue(
            WeeklyScheduleHelper.canAccessWeek(2, listOf(w1Pending)) is com.example.data.model.WeekAccessDecision.LockedAwaitingGuideApproval
        )
        assertEquals(
            com.example.data.model.WeekAccessDecision.Allowed,
            WeeklyScheduleHelper.canAccessWeek(1, listOf(w1Pending))
        )

        val w1Revision = WeeklyStudyReport(weekNumber = 1L, verificationStatus = "NEEDS_REVISION")
        assertTrue(
            WeeklyScheduleHelper.canAccessWeek(2, listOf(w1Revision)) is com.example.data.model.WeekAccessDecision.LockedAwaitingGuideApproval
        )

        val w1Verified = WeeklyStudyReport(weekNumber = 1L, verificationStatus = "VERIFIED")
        val lockVerified = WeeklyScheduleHelper.resolveWeeklyLockState(listOf(w1Verified))
        assertTrue(lockVerified.canAddNextWeek)
        assertEquals(2, lockVerified.nextUnlockedWeek)
        assertEquals(
            com.example.data.model.WeekAccessDecision.Allowed,
            WeeklyScheduleHelper.canAccessWeek(2, listOf(w1Verified))
        )
        assertEquals(
            com.example.data.model.WeekAccessDecision.AlreadyVerifiedLocked,
            WeeklyScheduleHelper.canAccessWeek(1, listOf(w1Verified))
        )
    }

    @Test
    fun `authentic period report validation enforces meaningful notes, minutes, activity type, and confirmation`() {
        val weakEntry = PeriodLogEntry("Monday", 2, "EFFECTIVE", 45, "ok", "Reading / Textbook")
        assertNotNull(WeeklyScheduleHelper.validateAuthenticPeriodReport(listOf(weakEntry), true, ""))

        val shortMins = PeriodLogEntry("Monday", 2, "EFFECTIVE", 5, "Read Surah Al-Kahf commentary carefully", "Reading / Textbook")
        assertNotNull(WeeklyScheduleHelper.validateAuthenticPeriodReport(listOf(shortMins), true, ""))

        val goodEntry = PeriodLogEntry("Monday", 2, "EFFECTIVE", 45, "Read Surah Al-Kahf Tafsir pages 10-18", "Reading / Textbook")
        assertNotNull(WeeklyScheduleHelper.validateAuthenticPeriodReport(listOf(goodEntry), false, ""))
        assertNull(WeeklyScheduleHelper.validateAuthenticPeriodReport(listOf(goodEntry), true, ""))
    }

    @Test
    fun `period log entry decodes both legacy 5-part and new 6-part formats`() {
        val legacy = PeriodLogEntry.decode("Monday|P2|EFFECTIVE|45m|Legacy study note")
        assertNotNull(legacy)
        assertEquals("Monday", legacy!!.day)
        assertEquals(2, legacy.period)
        assertEquals(45, legacy.minutes)
        assertEquals("", legacy.activityType)
        assertEquals("Legacy study note", legacy.note)

        val modern = PeriodLogEntry("Wednesday", 5, "PARTIAL", 30, "Reviewed grammar exercises", "Note Making")
        val decoded = PeriodLogEntry.decode(modern.encode())
        assertEquals(modern, decoded)
    }

    @Test
    fun `suggestWeekDateRange advances sequentially from prior week dateTo`() {
        val prev = WeeklyStudyReport(
            weekNumber = 1L,
            dateFrom = "01/06/2026",
            dateTo = "07/06/2026"
        )
        val (fromW2, toW2) = WeeklyScheduleHelper.suggestWeekDateRange(2, listOf(prev))
        assertEquals("08/06/2026", fromW2)
        assertEquals("14/06/2026", toW2)
    }

    @Test
    fun `buildInitialPeriodEntries and composeWeeklyDescription preserve per-period activity notes`() {
        val record = OffCourseRecord(
            timetableSlots = listOf("Monday:3", "Wednesday:6"),
            periodsPerWeek = 2L
        )
        val entries = WeeklyScheduleHelper.buildInitialPeriodEntries(record, null)
        assertEquals(2, entries.size)
        assertEquals("Monday", entries[0].day)
        assertEquals(3, entries[0].period)

        val edited = listOf(
            entries[0].copy(note = "Studied Surah Al-Kahf commentary"),
            entries[1].copy(effectiveness = "PARTIAL", minutes = 25, note = "Prepared Arabic notes")
        )
        val composed = WeeklyScheduleHelper.composeWeeklyDescription("", edited)
        assertTrue(composed.contains("Mon P3:"))
        assertTrue(composed.contains("Studied Surah Al-Kahf commentary"))
        assertTrue(composed.contains("Wed P6:"))
        assertTrue(composed.contains("Prepared Arabic notes"))
        assertEquals("Custom overview", WeeklyScheduleHelper.composeWeeklyDescription("Custom overview", edited))
    }

    @Test
    fun `app update helper parses version json and compares versionCode accurately`() {
        val json = """
            {
              "versionCode": 9,
              "versionName": "1.1.7",
              "buildNumber": 7,
              "commitSha": "deadbee",
              "releaseNotes": "feat: add GitHub Releases updater",
              "publishedAt": "2026-11-18T14:00:00Z",
              "apkUrl": "https://github.com/kkhmstorage/KKHM-OFF-COURSE/releases/download/latest/KKHM-Off-Course-debug.apk",
              "releasePageUrl": "https://github.com/kkhmstorage/KKHM-OFF-COURSE/releases/tag/latest"
            }
        """.trimIndent()
        val parsed = AppUpdateHelper.parseVersionJson(json)
        assertNotNull(parsed)
        assertEquals(9, parsed!!.versionCode)
        assertEquals("1.1.7", parsed.versionName)
        assertEquals(7, parsed.buildNumber)
        assertEquals("deadbee", parsed.commitSha)
        assertEquals("feat: add GitHub Releases updater", parsed.releaseNotes)
        assertTrue(AppUpdateHelper.isUpdateAvailable(localVersionCode = 8, localBuildNumber = 6, localCommitSha = "1111111", remote = parsed))
        assertFalse(AppUpdateHelper.isUpdateAvailable(localVersionCode = 9, localBuildNumber = 7, localCommitSha = "deadbee", remote = parsed))
    }

    @Test
    fun `app update helper falls back to github release api json when version json absent`() {
        val apiJson = """
            {
              "html_url": "https://github.com/kkhmstorage/KKHM-OFF-COURSE/releases/tag/latest",
              "name": "KKHM Off-Course v1.1.8 (Build 8)",
              "body": "### KKHM Off-Course v1.1.8 (Build 8)\n- **Version Code:** `10`",
              "published_at": "2026-11-18T15:00:00Z",
              "assets": [
                {
                  "name": "KKHM-Off-Course-debug.apk",
                  "browser_download_url": "https://github.com/kkhmstorage/KKHM-OFF-COURSE/releases/download/latest/KKHM-Off-Course-debug.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = AppUpdateHelper.parseGitHubReleaseApiJson(apiJson)
        assertNotNull(parsed)
        assertEquals(10, parsed!!.versionCode)
        assertEquals("1.1.8", parsed.versionName)
        assertEquals(8, parsed.buildNumber)
        assertTrue(parsed.apkUrl.endsWith("KKHM-Off-Course-debug.apk"))
    }
}
