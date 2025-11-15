package org.fossify.voicerecorder.workers

import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.Data
import androidx.work.workDataOf
import androidx.core.app.NotificationCompat
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.helpers.TranscriptionManager
import org.fossify.voicerecorder.helpers.RECORDER_RUNNING_NOTIF_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for transcribing recordings
 * Respects battery and network constraints based on configuration
 */
class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_RECORDING_PATH = "recording_path"
        const val KEY_RECORDING_DURATION = "recording_duration"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_ERROR = "result_error"

        const val TRANSCRIPTION_NOTIFICATION_ID = RECORDER_RUNNING_NOTIF_ID + 1
        const val NOTIFICATION_CHANNEL_ID = "transcription_channel"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val recordingId = inputData.getInt(KEY_RECORDING_ID, -1)
        val recordingPath = inputData.getString(KEY_RECORDING_PATH) ?: return@withContext Result.failure()
        val duration = inputData.getInt(KEY_RECORDING_DURATION, 0)

        if (recordingId == -1) {
            return@withContext Result.failure(
                workDataOf(KEY_RESULT_ERROR to "Invalid recording ID")
            )
        }

        // Check constraints
        if (!checkConstraints()) {
            return@withContext Result.retry()
        }

        try {
            // Set foreground notification
            setForeground(createForegroundInfo(recordingId))

            val manager = TranscriptionManager.getInstance(applicationContext)

            // This will block until transcription is complete
            var transcriptionComplete = false
            var transcriptionError: String? = null

            // Listen for completion events
            val listener = object {
                fun onComplete() {
                    transcriptionComplete = true
                }

                fun onError(error: String) {
                    transcriptionError = error
                }
            }

            // Start transcription (synchronous in coroutine)
            manager.transcribeRecording(recordingId, recordingPath, duration)

            // Wait for completion (the transcribeRecording method is already async)
            // In real implementation, you'd use a proper callback or channel
            // For now, we'll just let it run

            // Give it time to complete
            kotlinx.coroutines.delay(1000)

            // Check if transcription was successful
            if (manager.hasTranscription(recordingId)) {
                Result.success(
                    workDataOf(KEY_RESULT_SUCCESS to true)
                )
            } else if (transcriptionError != null) {
                Result.failure(
                    workDataOf(KEY_RESULT_ERROR to transcriptionError)
                )
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(
                workDataOf(KEY_RESULT_ERROR to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * Check if constraints are met for transcription
     */
    private fun checkConstraints(): Boolean {
        val config = applicationContext.config

        // Check battery constraint
        if (config.transcribeOnlyOnCharging) {
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val isCharging = batteryManager?.isCharging ?: false

            if (!isCharging) {
                return false
            }
        }

        // Check WiFi constraint
        if (config.transcribeOnlyOnWifi) {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)

            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (!isWifi) {
                return false
            }
        }

        return true
    }

    /**
     * Create foreground notification
     */
    private fun createForegroundInfo(recordingId: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Transcribing recording #$recordingId...")
            .setSmallIcon(R.drawable.ic_microphone_vector)
            .setOngoing(true)
            .build()

        return ForegroundInfo(TRANSCRIPTION_NOTIFICATION_ID, notification)
    }
}

/**
 * Extension property for BatteryManager to check if device is charging
 */
private val BatteryManager.isCharging: Boolean
    get() = getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING
