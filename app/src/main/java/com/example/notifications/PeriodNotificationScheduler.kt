package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.CIC_DAYS
import com.example.data.model.OffCourseRecord
import java.util.Calendar

class PeriodNotificationScheduler(private val context: Context) {

    fun syncAlertsForRecords(
        records: List<OffCourseRecord>,
        enabled: Boolean,
        alertHour: Int,
        alertMinute: Int
    ) {
        cancelAllScheduledDayAlerts()
        if (!enabled) return

        PeriodReminderReceiver.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val slotsByDay = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        records.forEach { record ->
            record.timetableSlots.forEach { slot ->
                val parts = slot.split(":")
                if (parts.size == 2) {
                    val day = parts[0]
                    val period = parts[1].toIntOrNull()
                    if (day in CIC_DAYS && period != null) {
                        slotsByDay.getOrPut(day) { mutableListOf() }.add(record.subjectTitle to period)
                    }
                }
            }
        }

        slotsByDay.forEach { (dayName, entries) ->
            val dayOfWeek = mapDayNameToCalendarDay(dayName)
            val periodsLabel = entries
                .groupBy({ it.first }, { "P${it.second}" })
                .entries
                .joinToString(" | ") { (subject, periods) ->
                    "$subject (${periods.distinct().sorted().joinToString(", ")})"
                }
            val firstSubject = entries.firstOrNull()?.first ?: "Off-Course"

            val requestCode = 5000 + dayOfWeek
            val intent = Intent(context, PeriodReminderReceiver::class.java).apply {
                putExtra(PeriodReminderReceiver.EXTRA_DAY_NAME, dayName)
                putExtra(PeriodReminderReceiver.EXTRA_PERIODS_TEXT, periodsLabel)
                putExtra(PeriodReminderReceiver.EXTRA_SUBJECT_TITLE, firstSubject)
                putExtra(PeriodReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerCal = nextOccurrenceCalendar(dayOfWeek, alertHour, alertMinute)
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerCal.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7L,
                pendingIntent
            )
        }
    }

    fun sendTestNotification(records: List<OffCourseRecord>): Boolean {
        val todayCal = Calendar.getInstance()
        val todayName = calendarDayToName(todayCal.get(Calendar.DAY_OF_WEEK))
        val matchingSlots = records.flatMap { rec ->
            rec.timetableSlots.filter { it.startsWith("$todayName:") }
                .map { "${rec.subjectTitle} (Period ${it.substringAfter(":")})" }
        }
        val allScheduled = records.flatMap { rec ->
            rec.timetableSlots.map { "${rec.subjectTitle}: ${it.replace(":", " P")}" }
        }

        val title = if (matchingSlots.isNotEmpty()) {
            "Off-Course Periods Today ($todayName)"
        } else {
            "CIC Off-Course Alert Preview"
        }
        val body = when {
            matchingSlots.isNotEmpty() ->
                "Scheduled today: ${matchingSlots.joinToString(", ")}. Tap to log utilization."
            allScheduled.isNotEmpty() ->
                "Alerts active for: ${allScheduled.take(4).joinToString(" • ")}. You will be alerted on those days."
            else ->
                "Set your Timetable periods (Sun–Sat, P1–P8) in a record to get automatic day-of-period alerts."
        }
        return PeriodReminderReceiver.showPeriodNotification(
            context = context,
            title = title,
            body = body,
            notificationId = 4999
        )
    }

    fun cancelAllScheduledDayAlerts() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            val requestCode = 5000 + day
            val intent = Intent(context, PeriodReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun nextOccurrenceCalendar(dayOfWeek: Int, hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
    }

    private fun mapDayNameToCalendarDay(dayName: String): Int = when (dayName) {
        "Sunday" -> Calendar.SUNDAY
        "Monday" -> Calendar.MONDAY
        "Tuesday" -> Calendar.TUESDAY
        "Wednesday" -> Calendar.WEDNESDAY
        "Thursday" -> Calendar.THURSDAY
        "Friday" -> Calendar.FRIDAY
        "Saturday" -> Calendar.SATURDAY
        else -> Calendar.MONDAY
    }

    companion object {
        fun calendarDayToName(calDay: Int): String = when (calDay) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Monday"
        }
    }
}
