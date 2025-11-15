package org.fossify.voicerecorder.helpers

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.models.Transcription
import org.fossify.voicerecorder.models.TranscriptionSegment
import java.io.File

/**
 * Helper class for storing and loading transcriptions
 */
class TranscriptionStorage(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    companion object {
        private const val TRANSCRIPTION_DIR = ".transcriptions"
        private const val TRANSCRIPTION_FILE_SUFFIX = ".transcription.json"

        /**
         * Get transcription directory
         */
        fun getTranscriptionDirectory(context: Context): File {
            val dir = File(context.config.saveRecordingsFolder, TRANSCRIPTION_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }

    /**
     * Save transcription to disk
     */
    fun saveTranscription(transcription: Transcription): Boolean {
        return try {
            val dir = getTranscriptionDirectory(context)
            val fileName = getTranscriptionFileName(transcription.recordingId)
            val file = File(dir, fileName)

            val jsonString = json.encodeToString(transcription)
            file.writeText(jsonString)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Load transcription for a recording
     */
    fun loadTranscription(recordingId: Int): Transcription? {
        return try {
            val dir = getTranscriptionDirectory(context)
            val fileName = getTranscriptionFileName(recordingId)
            val file = File(dir, fileName)

            if (!file.exists()) {
                return null
            }

            val jsonString = file.readText()
            json.decodeFromString<Transcription>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Check if transcription exists for a recording
     */
    fun hasTranscription(recordingId: Int): Boolean {
        val dir = getTranscriptionDirectory(context)
        val fileName = getTranscriptionFileName(recordingId)
        return File(dir, fileName).exists()
    }

    /**
     * Delete transcription for a recording
     */
    fun deleteTranscription(recordingId: Int): Boolean {
        return try {
            val dir = getTranscriptionDirectory(context)
            val fileName = getTranscriptionFileName(recordingId)
            File(dir, fileName).delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get all transcriptions
     */
    fun getAllTranscriptions(): List<Transcription> {
        return try {
            val dir = getTranscriptionDirectory(context)
            if (!dir.exists()) {
                return emptyList()
            }

            dir.listFiles { file ->
                file.name.endsWith(TRANSCRIPTION_FILE_SUFFIX)
            }?.mapNotNull { file ->
                try {
                    val jsonString = file.readText()
                    json.decodeFromString<Transcription>(jsonString)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Search transcriptions by text content
     */
    fun searchTranscriptions(query: String): List<Pair<Transcription, List<TranscriptionSegment>>> {
        val results = mutableListOf<Pair<Transcription, List<TranscriptionSegment>>>()

        try {
            val allTranscriptions = getAllTranscriptions()
            val queryLower = query.lowercase()

            for (transcription in allTranscriptions) {
                val matchingSegments = transcription.segments.filter { segment ->
                    segment.text.lowercase().contains(queryLower)
                }

                if (matchingSegments.isNotEmpty()) {
                    results.add(Pair(transcription, matchingSegments))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    /**
     * Get transcription file size
     */
    fun getTranscriptionSize(recordingId: Int): Long {
        return try {
            val dir = getTranscriptionDirectory(context)
            val fileName = getTranscriptionFileName(recordingId)
            val file = File(dir, fileName)
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get total size of all transcriptions
     */
    fun getTotalTranscriptionsSize(): Long {
        return try {
            val dir = getTranscriptionDirectory(context)
            if (!dir.exists()) return 0L

            dir.listFiles { file ->
                file.name.endsWith(TRANSCRIPTION_FILE_SUFFIX)
            }?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clean up all transcriptions
     */
    fun deleteAllTranscriptions(): Boolean {
        return try {
            val dir = getTranscriptionDirectory(context)
            if (!dir.exists()) return true

            dir.listFiles { file ->
                file.name.endsWith(TRANSCRIPTION_FILE_SUFFIX)
            }?.forEach { it.delete() }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getTranscriptionFileName(recordingId: Int): String {
        return "recording_${recordingId}${TRANSCRIPTION_FILE_SUFFIX}"
    }
}
