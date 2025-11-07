package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.services.RecorderService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if auto-record on boot is enabled in settings
            if (context.config.recordOnBoot) {
                try {
                    val serviceIntent = Intent(context, RecorderService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Handle potential security exceptions on newer Android versions
                    e.printStackTrace()
                }
            }
        }
    }
}
