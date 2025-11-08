package org.fossify.voicerecorder.buffers

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.voicerecorder.extensions.config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.Timer
import java.util.TimerTask

/**
 * Tier 0: Immediate crash recovery buffer
 *
 * Handles:
 * - Write audio data to temporary file
 * - Periodic flush to disk (every 5 seconds)
 * - State persistence for crash recovery
 * - Atomic finalization on successful completion
 */
class ImmediateBuffer(private val context: Context) {
    private var tempFile: File? = null
    private var outputStream: FileOutputStream? = null
    private var bytesWritten = AtomicLong(0L)
    private var startTimestamp: Long = 0
    private var flushTimer: Timer? = null
    private val json = Json { prettyPrint = true }

    companion object {
        private const val TEMP_DIR = ".buffers/temp"
        private const val STATE_KEY = "active_recording_state"
        private const val DEFAULT_FLUSH_INTERVAL_MS = 5000L

        /**
         * Get active recording state from SharedPreferences
         */
        fun getActiveState(context: Context): RecordingState? {
            return try {
                val stateJson = context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
                    .getString(STATE_KEY, null)

                if (stateJson != null) {
                    Json.decodeFromString<RecordingState>(stateJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * Clear active recording state
         */
        fun clearActiveState(context: Context) {
            context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove(STATE_KEY)
                .apply()
        }

        /**
         * Get all temp files (for cleanup)
         */
        fun getAllTempFiles(context: Context): List<File> {
            val bufferDir = File(context.config.saveRecordingsFolder, TEMP_DIR)
            if (!bufferDir.exists()) return emptyList()

            return bufferDir.listFiles { file -> file.extension == "tmp" }?.toList() ?: emptyList()
        }

        /**
         * Clean up orphaned temp files
         */
        fun cleanupOrphanedTempFiles(context: Context) {
            val activeState = getActiveState(context)
            val tempFiles = getAllTempFiles(context)

            for (file in tempFiles) {
                // Don't delete if it's the active recording
                if (activeState?.tempFilePath == file.absolutePath) {
                    continue
                }

                // Delete old temp files (older than 1 hour)
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > 60 * 60 * 1000) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Initialize a new recording session
     */
    fun init(format: String, sampleRate: Int, bitrate: Int): String {
        cleanup()

        val bufferDir = File(context.config.saveRecordingsFolder, TEMP_DIR)
        if (!bufferDir.exists()) {
            bufferDir.mkdirs()
        }

        startTimestamp = System.currentTimeMillis()
        val fileName = "recording_${startTimestamp}.tmp"
        tempFile = File(bufferDir, fileName)

        outputStream = FileOutputStream(tempFile!!)
        bytesWritten.set(0L)

        // Save initial state
        saveState(format, sampleRate, bitrate)

        // Start periodic flush timer
        startFlushTimer()

        return tempFile!!.absolutePath
    }

    /**
     * Write audio data to the buffer
     */
    @Synchronized
    fun write(data: ByteArray, offset: Int, length: Int) {
        try {
            outputStream?.write(data, offset, length)
            bytesWritten.addAndGet(length.toLong())
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Write audio data to the buffer (full array)
     */
    fun write(data: ByteArray) {
        write(data, 0, data.size)
    }

    /**
     * Flush buffered data to disk
     */
    @Synchronized
    fun flush() {
        try {
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Finalize the recording and move to final location
     * Returns the final file path
     */
    fun finalize(finalPath: String): Boolean {
        stopFlushTimer()

        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            val finalFile = File(finalPath)
            if (tempFile != null && tempFile!!.exists()) {
                // Atomic rename
                val success = tempFile!!.renameTo(finalFile)
                if (success) {
                    clearState()
                    tempFile = null
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    /**
     * Cancel the recording and delete temp file
     */
    fun cancel() {
        stopFlushTimer()

        try {
            outputStream?.close()
            outputStream = null

            tempFile?.delete()
            tempFile = null

            clearState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopFlushTimer()

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get current bytes written
     */
    fun getBytesWritten(): Long = bytesWritten.get()

    /**
     * Get recording duration in seconds
     */
    fun getDurationSeconds(): Int {
        if (startTimestamp == 0L) return 0
        return ((System.currentTimeMillis() - startTimestamp) / 1000).toInt()
    }

    /**
     * Save recording state to SharedPreferences for crash recovery
     */
    private fun saveState(format: String, sampleRate: Int, bitrate: Int) {
        ensureBackgroundThread {
            try {
                val state = RecordingState(
                    tempFilePath = tempFile?.absolutePath ?: "",
                    startTimestamp = startTimestamp,
                    durationSeconds = getDurationSeconds(),
                    format = format,
                    bytesWritten = bytesWritten.get(),
                    sampleRate = sampleRate,
                    bitrate = bitrate
                )

                val stateJson = json.encodeToString(state)
                context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(STATE_KEY, stateJson)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Update state periodically
     */
    private fun updateState() {
        ensureBackgroundThread {
            try {
                val stateJson = context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
                    .getString(STATE_KEY, null)

                if (stateJson != null) {
                    val state = json.decodeFromString<RecordingState>(stateJson)
                    val updatedState = state.copy(
                        durationSeconds = getDurationSeconds(),
                        bytesWritten = bytesWritten.get()
                    )

                    val updatedJson = json.encodeToString(updatedState)
                    context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString(STATE_KEY, updatedJson)
                        .apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Clear recording state
     */
    private fun clearState() {
        context.getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove(STATE_KEY)
            .apply()
    }

    /**
     * Start periodic flush timer
     */
    private fun startFlushTimer() {
        val interval = context.config.bufferFlushInterval

        flushTimer = Timer()
        flushTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                flush()
                updateState()
            }
        }, interval, interval)
    }

    /**
     * Stop flush timer
     */
    private fun stopFlushTimer() {
        flushTimer?.cancel()
        flushTimer = null
    }
}
