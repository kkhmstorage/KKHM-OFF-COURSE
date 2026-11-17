package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class PeriodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dayName = intent.getStringExtra(EXTRA_DAY_NAME) ?: "Today"
        val periodsText = intent.getStringExtra(EXTRA_PERIODS_TEXT) ?: "Scheduled Off-Course Period"
        val subjectTitle = intent.getStringExtra(EXTRA_SUBJECT_TITLE) ?: "KKHM Off-Course Study"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 4001)

        showPeriodNotification(
            context = context,
            title = "Off-Course Period Today ($dayName)",
            body = "$subjectTitle • $periodsText. Log your period utilization in your record book!",
            notificationId = notificationId
        )
    }

    companion object {
        const val CHANNEL_ID = "off_course_period_alerts"
        const val EXTRA_DAY_NAME = "extra_day_name"
        const val EXTRA_PERIODS_TEXT = "extra_periods_text"
        const val EXTRA_SUBJECT_TITLE = "extra_subject_title"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun showPeriodNotification(
            context: Context,
            title: String,
            body: String,
            notificationId: Int
        ): Boolean {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            return true
        }
    }
}
