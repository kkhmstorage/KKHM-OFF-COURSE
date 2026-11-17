package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class UserRole(val title: String, val malayalamHint: String) {
    STUDENT("Student", "വിദ്യാർത്ഥി"),
    GUIDE("Guide (Mentor)", "അധ്യാപകൻ / ഗൈഡ്"),
    PRINCIPAL("Principal / Dean", "പ്രിൻസിപ്പാൾ / ഡീൻ"),
    ADMIN("College Admin", "അഡ്മിൻ");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STUDENT
    }
}

enum class AccountStatus(val label: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended");

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class DarkModePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): DarkModePreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

enum class CicStream(val label: String) {
    WAFY("Wafy"),
    WAFY_ARTS("Wafy Arts"),
    WAFIYYA("Wafiyya"),
    WAFIYYA_DAY("Wafiyya Day"),
    WAFIYYA_ARTS("Wafiyya Arts"),
    WAFIYYA_DISTANCE("Wafiyya Distance");

    companion object {
        fun fromLabel(raw: String?): CicStream =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: WAFY
    }
}

enum class TermStatus(val label: String) {
    ACTIVE("Active"),
    UPCOMING("Upcoming"),
    ARCHIVED("Archived");

    companion object {
        fun fromRaw(raw: String?): TermStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class SignatureStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REVISION_REQUIRED("Revision Needed");

    companion object {
        fun fromRaw(raw: String?): SignatureStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class RecordStatus(val label: String) {
    ACTIVE("Active"),
    SUBMITTED("Submitted"),
    COMPLETED("Completed"),
    ARCHIVED("Archived");

    companion object {
        fun fromRaw(raw: String?): RecordStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class WeekVerificationStatus(val label: String) {
    PENDING("Pending Review"),
    VERIFIED("Verified"),
    NEEDS_REVISION("Needs Revision");

    companion object {
        fun fromRaw(raw: String?): WeekVerificationStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

val CIC_DAYS = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
)

// Exact periods from Page 2 of the CIC Off-Course Record PDF
val CIC_PERIODS = listOf(1, 2, 3, 5, 6, 7, 8)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.name,
    val cicAdNo: String = "",
    val clgAdNo: String = "",
    val classDepartment: String = "",
    val stream: String = CicStream.WAFY.label,
    val collegeAfflName: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val darkModePreference: String = DarkModePreference.SYSTEM.name,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CollegeAccount(
    @DocumentId val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = UserRole.STUDENT.name,
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val accountStatus: String = AccountStatus.ACTIVE.name,
    val cicAdNo: String = "",
    val classDepartment: String = "",
    val stream: String = "",
    val assignedGuideUsername: String = "",
    val phone: String = "",
    val boundUid: String = "",
    val createdByUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AcademicTerm(
    @DocumentId val id: String = "",
    val visibility: String = "campus",
    val academicYear: String = "",
    val semester: String = "",
    val status: String = TermStatus.ACTIVE.name,
    val totalWeeks: Long = 18L,
    val defaultPeriodsPerWeek: Long = 4L,
    val notes: String = "",
    val createdByUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OffCourseRecord(
    @DocumentId val id: String = "",
    val studentUid: String = "",
    val studentUsername: String = "",
    val studentEmail: String = "",
    val guideUsername: String = "",
    val guideEmail: String = "",
    val principalEmail: String = "",
    val studentName: String = "",
    val cicAdNo: String = "",
    val clgAdNo: String = "",
    val classDepartment: String = "",
    val academicYear: String = "",
    val semester: String = "",
    val stream: String = CicStream.WAFY.label,
    val collegeAfflName: String = "",
    val subjectTitle: String = "",
    val proposalAppNo: String = "",
    val credit: Long = 4L,
    val guideName: String = "",
    val guidePhone: String = "",
    val guideWhatsapp: String = "",
    val referenceBooks: String = "",
    val timetableSlots: List<String> = emptyList(),
    val periodsPerWeek: Long = 0L,
    val guideSignatureStatus: String = SignatureStatus.PENDING.name,
    val guideSignatureName: String = "",
    val guideRemarks: String = "",
    val principalSignatureStatus: String = SignatureStatus.PENDING.name,
    val principalSignatureName: String = "",
    val principalRemarks: String = "",
    val recordStatus: String = RecordStatus.ACTIVE.name,
    val completedWeeksCount: Long = 0L,
    val verifiedWeeksCount: Long = 0L,
    val totalAllocatedPeriods: Long = 0L,
    val totalUtilizedPeriods: Long = 0L,
    val averageFocusRating: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val utilizationPercent: Int
        get() = if (totalAllocatedPeriods > 0L) {
            ((totalUtilizedPeriods.toDouble() / totalAllocatedPeriods.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        } else 0
}

@Keep
@IgnoreExtraProperties
data class WeeklyStudyReport(
    @DocumentId val id: String = "",
    val recordId: String = "",
    val studentUid: String = "",
    val studentUsername: String = "",
    val guideUsername: String = "",
    val guideEmail: String = "",
    val principalEmail: String = "",
    val weekNumber: Long = 1L,
    val dateFrom: String = "",
    val dateTo: String = "",
    val description: String = "",
    val allocatedPeriods: Long = 0L,
    val utilizedPeriods: Long = 0L,
    val focusRating: Long = 4L,
    val utilizationNotes: String = "",
    val periodLogs: List<String> = emptyList(),
    val verificationStatus: String = WeekVerificationStatus.PENDING.name,
    val authorizedSignature: String = "",
    val mentorFeedback: String = "",
    val verifiedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val utilizationPercent: Int
        get() = if (allocatedPeriods > 0L) {
            ((utilizedPeriods.toDouble() / allocatedPeriods.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        } else 0
}

data class PeriodLogEntry(
    val day: String,
    val period: Int,
    val effectiveness: String, // EFFECTIVE, PARTIAL, MISSED
    val minutes: Int,
    val note: String
) {
    fun encode(): String =
        listOf(day, "P$period", effectiveness, "${minutes}m", note.replace("|", "/").trim())
            .joinToString("|")

    companion object {
        fun decode(raw: String): PeriodLogEntry? {
            val parts = raw.split("|", limit = 5)
            if (parts.size < 4) return null
            val day = parts[0]
            val period = parts[1].removePrefix("P").toIntOrNull() ?: 1
            val effectiveness = parts[2]
            val minutes = parts[3].removeSuffix("m").toIntOrNull() ?: 45
            val note = parts.getOrElse(4) { "" }
            return PeriodLogEntry(day, period, effectiveness, minutes, note)
        }
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
