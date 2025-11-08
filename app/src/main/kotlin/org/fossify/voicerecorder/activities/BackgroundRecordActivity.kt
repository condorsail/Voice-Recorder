package org.fossify.voicerecorder.activities

import android.content.Intent
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.voicerecorder.services.RecorderService

class BackgroundRecordActivity : SimpleActivity() {
    companion object {
        const val RECORD_INTENT_ACTION = "RECORD_ACTION"
        const val RECORD_INTENT_ACTION_BOOT = "RECORD_ACTION_BOOT"
    }

    override fun onResume() {
        super.onResume()
        when (intent.action) {
            RECORD_INTENT_ACTION -> handleRecordToggle()
            RECORD_INTENT_ACTION_BOOT -> handleBootRecord()
        }
        moveTaskToBack(true)
        finish()
    }

    private fun handleRecordToggle() {
        handleNotificationPermission { granted ->
            if (granted) {
                Intent(this@BackgroundRecordActivity, RecorderService::class.java).apply {
                    try {
                        if (RecorderService.isRunning) {
                            stopService(this)
                        } else {
                            startService(this)
                        }
                    } catch (ignored: Exception) {
                    }
                }
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = org.fossify.commons.R.string.allow_notifications_voice_recorder,
                    positiveActionCallback = { openNotificationSettings() }
                )
            }
        }
    }

    private fun handleBootRecord() {
        handleNotificationPermission { granted ->
            if (granted) {
                // Always start recording on boot (don't toggle)
                if (!RecorderService.isRunning) {
                    Intent(this@BackgroundRecordActivity, RecorderService::class.java).apply {
                        try {
                            startService(this)
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
    }
}
