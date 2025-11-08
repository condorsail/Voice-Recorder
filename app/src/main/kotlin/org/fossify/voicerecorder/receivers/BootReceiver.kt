package org.fossify.voicerecorder.receivers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.activities.MainActivity
import org.fossify.voicerecorder.extensions.config

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_RECORDING_ALARM = "org.fossify.voicerecorder.START_RECORDING_ALARM"
        private const val BOOT_NOTIFICATION_ID = 10001
        private const val ALARM_DELAY_MS = 3000L // 3 seconds after boot
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            ACTION_START_RECORDING_ALARM -> handleStartRecordingAlarm(context)
        }
    }

    private fun handleBootCompleted(context: Context) {
        if (!context.config.recordOnBoot) {
            return
        }

        // For Android 14+ (API 34+), microphone foreground services cannot be started
        // from BOOT_COMPLETED. Schedule an exact alarm to notify the user instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            scheduleRecordingAlarm(context)
        } else {
            // For Android 12-13, show immediate notification to open app
            showRecordingNotification(context)
        }
    }

    private fun scheduleRecordingAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Check if app has exact alarm permission (required for Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Can't schedule exact alarm, show notification immediately instead
                    showRecordingNotification(context)
                    return
                }
            }

            val alarmIntent = Intent(context, BootReceiver::class.java).apply {
                action = ACTION_START_RECORDING_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule exact alarm for a few seconds after boot
            val triggerTime = System.currentTimeMillis() + ALARM_DELAY_MS
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to immediate notification
            showRecordingNotification(context)
        }
    }

    private fun handleStartRecordingAlarm(context: Context) {
        // Show notification to prompt user to start recording
        showRecordingNotification(context)
    }

    private fun showRecordingNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "boot_recording_channel",
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Auto-start recording notifications"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app and start recording
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_start_recording", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "boot_recording_channel")
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Tap to start recording")
            .setSmallIcon(org.fossify.commons.R.drawable.ic_microphone_vector)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(BOOT_NOTIFICATION_ID, builder.build())
    }
}
