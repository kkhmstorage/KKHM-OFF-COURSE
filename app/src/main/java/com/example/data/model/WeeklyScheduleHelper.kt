package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface WeekAccessDecision {
    data object Allowed : WeekAccessDecision
    data class LockedAwaitingGuideApproval(
        val blockingWeekNumber: Int,
        val blockingStatus: WeekVerificationStatus,
        val missingWeekOnly: Boolean = false
    ) : WeekAccessDecision
    data object AlreadyVerifiedLocked : WeekAccessDecision
}

data class WeeklyLockState(
    val blockingUnverifiedReport: WeeklyStudyReport?,
    val nextUnlockedWeek: Int?,
    val isAllWeeksCompletedAndVerified: Boolean
) {
    val canAddNextWeek: Boolean
        get() = blockingUnverifiedReport == null && nextUnlockedWeek != null
}

object WeeklyScheduleHelper {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val parseFormatters = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd/MM/yy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US)
    ).onEach { it.isLenient = false }

    /**
     * Returns the smallest unlogged week number in 1..totalWeeks, or totalWeeks if all are filled.
     */
    fun nextPendingWeek(reports: List<WeeklyStudyReport>, totalWeeks: Int = 18): Int {
        val logged = reports.map { it.weekNumber.toInt() }.toSet()
        return (1..totalWeeks).firstOrNull { it !in logged } ?: totalWeeks
    }

    /**
     * Strict sequential Guide-approval gating:
     * A student cannot log Week N+1 until Week N exists AND is approved (`VERIFIED`) by their Guide.
     */
    fun resolveWeeklyLockState(
        reports: List<WeeklyStudyReport>,
        totalWeeks: Int = 18
    ): WeeklyLockState {
        val byWeek = reports.associateBy { it.weekNumber.toInt() }
        val earliestUnverified = reports
            .filter { it.verificationStatus != WeekVerificationStatus.VERIFIED.name }
            .minByOrNull { it.weekNumber }

        if (earliestUnverified != null) {
            return WeeklyLockState(
                blockingUnverifiedReport = earliestUnverified,
                nextUnlockedWeek = null,
                isAllWeeksCompletedAndVerified = false
            )
        }

        val nextUnlogged = (1..totalWeeks).firstOrNull { it !in byWeek.keys }
        return WeeklyLockState(
            blockingUnverifiedReport = null,
            nextUnlockedWeek = nextUnlogged,
            isAllWeeksCompletedAndVerified = nextUnlogged == null && reports.isNotEmpty()
        )
    }

    fun canAccessWeek(
        weekNumber: Int,
        reports: List<WeeklyStudyReport>,
        totalWeeks: Int = 18
    ): WeekAccessDecision {
        val safeWeek = weekNumber.coerceIn(1, totalWeeks)
        val byWeek = reports.associateBy { it.weekNumber.toInt() }
        val existing = byWeek[safeWeek]

        if (existing?.verificationStatus == WeekVerificationStatus.VERIFIED.name) {
            return WeekAccessDecision.AlreadyVerifiedLocked
        }

        for (prior in 1 until safeWeek) {
            val priorReport = byWeek[prior]
            if (priorReport == null) {
                return WeekAccessDecision.LockedAwaitingGuideApproval(
                    blockingWeekNumber = prior,
                    blockingStatus = WeekVerificationStatus.PENDING,
                    missingWeekOnly = true
                )
            }
            if (priorReport.verificationStatus != WeekVerificationStatus.VERIFIED.name) {
                return WeekAccessDecision.LockedAwaitingGuideApproval(
                    blockingWeekNumber = prior,
                    blockingStatus = WeekVerificationStatus.fromRaw(priorReport.verificationStatus),
                    missingWeekOnly = false
                )
            }
        }
        return WeekAccessDecision.Allowed
    }

    fun validateAuthenticPeriodReport(
        periodEntries: List<PeriodLogEntry>,
        selfConfirmed: Boolean,
        manualSummary: String
    ): String? {
        if (periodEntries.isEmpty()) {
            return "Add at least one scheduled period activity entry."
        }
        val activeEntries = periodEntries.filter { it.effectiveness != "MISSED" }
        for ((idx, entry) in periodEntries.withIndex()) {
            val trimmedNote = entry.note.trim()
            val alnumCount = trimmedNote.count { it.isLetterOrDigit() }
            if (entry.effectiveness != "MISSED") {
                if (entry.activityType.isBlank()) {
                    return "Select an activity type for ${entry.day} P${entry.period} (Entry ${idx + 1})."
                }
                if (trimmedNote.length < 12 || alnumCount < 8) {
                    return "Describe what was genuinely studied in ${entry.day} P${entry.period} (at least 12 characters)."
                }
                if (entry.minutes < 10) {
                    return "Enter realistic study duration (minimum 10 minutes) for ${entry.day} P${entry.period}."
                }
            } else if (trimmedNote.length < 5) {
                return "Mention a short reason for missing ${entry.day} P${entry.period}."
            }
        }
        if (activeEntries.isEmpty() && manualSummary.trim().length < 12) {
            return "When all periods are marked Missed, provide a clear weekly note explaining what happened."
        }
        if (!selfConfirmed) {
            return "Confirm that the entered period study activities are true and accurate."
        }
        return null
    }

    /**
     * Suggests a clean 7-day date range (dd/MM/yyyy) for [weekNumber] based on previous reports
     * or the current week's Monday–Sunday.
     */
    fun suggestWeekDateRange(
        weekNumber: Int,
        existingReports: List<WeeklyStudyReport>,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<String, String> {
        val existing = existingReports.firstOrNull { it.weekNumber.toInt() == weekNumber }
        if (existing != null && existing.dateFrom.isNotBlank() && existing.dateTo.isNotBlank()) {
            return existing.dateFrom to existing.dateTo
        }

        val prev = existingReports
            .filter { it.weekNumber.toInt() < weekNumber && it.dateTo.isNotBlank() }
            .maxByOrNull { it.weekNumber }

        val cal = Calendar.getInstance()
        if (prev != null) {
            val prevEnd = parseDateMillis(prev.dateTo)
            if (prevEnd != null) {
                val weekGap = (weekNumber - prev.weekNumber.toInt()).coerceAtLeast(1)
                cal.timeInMillis = prevEnd
                cal.add(Calendar.DAY_OF_YEAR, 1 + (weekGap - 1) * 7)
                val from = dateFormatter.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val to = dateFormatter.format(cal.time)
                return from to to
            }
        }

        cal.timeInMillis = nowMillis
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val from = dateFormatter.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val to = dateFormatter.format(cal.time)
        return from to to
    }

    private fun parseDateMillis(raw: String): Long? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        for (fmt in parseFormatters) {
            try {
                val d = fmt.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Pre-populates per-period cards from existing report logs and/or the record's timetable slots
     * so every scheduled period has an editable activity type and "What was done in this period?" note.
     */
    fun buildInitialPeriodEntries(
        record: OffCourseRecord,
        existingReport: WeeklyStudyReport?
    ): List<PeriodLogEntry> {
        val decodedExisting = existingReport?.periodLogs
            ?.mapNotNull { PeriodLogEntry.decode(it) }
            .orEmpty()

        val sortedSlots = record.timetableSlots.sortedWith(
            compareBy<String>(
                { slot ->
                    val idx = CIC_DAYS.indexOf(slot.substringBefore(":"))
                    if (idx >= 0) idx else 99
                },
                { slot -> slot.substringAfter(":").toIntOrNull() ?: 99 }
            )
        )

        val defaultActivity = STUDY_ACTIVITY_TYPES.first()

        if (decodedExisting.isEmpty()) {
            return if (sortedSlots.isNotEmpty()) {
                sortedSlots.map { slot ->
                    PeriodLogEntry(
                        day = slot.substringBefore(":"),
                        period = slot.substringAfter(":").toIntOrNull() ?: 1,
                        effectiveness = "EFFECTIVE",
                        minutes = 45,
                        note = "",
                        activityType = defaultActivity
                    )
                }
            } else {
                val count = record.periodsPerWeek.coerceIn(1L, 7L).toInt()
                (1..count).map { idx ->
                    PeriodLogEntry(
                        day = "Period $idx",
                        period = idx,
                        effectiveness = "EFFECTIVE",
                        minutes = 45,
                        note = "",
                        activityType = defaultActivity
                    )
                }
            }
        }

        // Merge any newly added timetable slots not yet in decodedExisting
        val merged = decodedExisting.map { entry ->
            if (entry.activityType.isBlank() && entry.effectiveness != "MISSED") {
                entry.copy(activityType = defaultActivity)
            } else entry
        }.toMutableList()
        sortedSlots.forEach { slot ->
            val day = slot.substringBefore(":")
            val period = slot.substringAfter(":").toIntOrNull() ?: 1
            if (merged.none { it.day == day && it.period == period }) {
                merged.add(PeriodLogEntry(day, period, "EFFECTIVE", 45, "", defaultActivity))
            }
        }
        return merged
    }

    /**
     * Auto-composes a concise weekly description from per-period activity notes when the student
     * leaves the overall summary empty, avoiding double typing while preserving manual summaries.
     */
    fun composeWeeklyDescription(
        manualSummary: String,
        periodEntries: List<PeriodLogEntry>
    ): String {
        val trimmed = manualSummary.trim()
        if (trimmed.isNotEmpty()) return trimmed

        val noteLines = periodEntries.mapNotNull { entry ->
            val cleanNote = entry.note.trim()
            if (cleanNote.isEmpty()) null
            else {
                val prefix = if (entry.activityType.isNotBlank()) "[${entry.activityType}] " else ""
                "${entry.day.take(3)} P${entry.period}: $prefix$cleanNote"
            }
        }
        if (noteLines.isNotEmpty()) {
            return noteLines.joinToString("\n").take(1450)
        }

        val activeCount = periodEntries.count { it.effectiveness != "MISSED" }
        return if (activeCount > 0) {
            "Completed $activeCount off-course study period(s)."
        } else {
            ""
        }
    }
}
