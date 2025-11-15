package org.fossify.voicerecorder.helpers

import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.fossify.voicerecorder.extensions.config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper for background transcription with constraint checking
 *
 * Note: This is a simple helper class. For production use with WorkManager,
 * add androidx.work dependency and convert to CoroutineWorker.
 */
object TranscriptionHelper {

    /**
     * Check if constraints are met for transcription
     */
    fun checkConstraints(context: Context): Boolean {
        val config = context.config

        // Check battery constraint
        if (config.transcribeOnlyOnCharging) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val isCharging = batteryManager?.isCharging ?: false

            if (!isCharging) {
                return false
            }
        }

        // Check WiFi constraint
        if (config.transcribeOnlyOnWifi) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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
     * Transcribe a recording in the background with constraint checking
     */
    suspend fun transcribeWithConstraints(
        context: Context,
        recordingId: Int,
        recordingPath: String,
        duration: Int
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            // Check constraints
            if (!checkConstraints(context)) {
                return@withContext Result.failure(
                    Exception("Transcription constraints not met")
                )
            }

            val manager = TranscriptionManager.getInstance(context)
            manager.transcribeRecording(recordingId, recordingPath, duration)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Extension property for BatteryManager to check if device is charging
 */
private val BatteryManager.isCharging: Boolean
    get() = getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING
