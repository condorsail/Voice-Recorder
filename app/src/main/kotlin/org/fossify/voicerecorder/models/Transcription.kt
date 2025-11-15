package org.fossify.voicerecorder.models

import kotlinx.serialization.Serializable

/**
 * Represents a complete transcription for a recording
 */
@Serializable
data class Transcription(
    val recordingId: Int,
    val recordingPath: String,
    val segments: List<TranscriptionSegment>,
    val language: String,
    val duration: Int, // in seconds
    val createdAt: Long, // timestamp when transcription was created
    val isComplete: Boolean = false, // true if full transcription is done
    val processingTimeMs: Long = 0 // how long transcription took
)

/**
 * Represents a single transcribed segment with timestamp
 */
@Serializable
data class TranscriptionSegment(
    val text: String,
    val startTime: Long, // milliseconds from start of recording
    val endTime: Long, // milliseconds from start of recording
    val confidence: Float = 1.0f,
    val language: String = "en"
) {
    /**
     * Format the time range as a readable string (e.g., "00:12 - 00:45")
     */
    fun formatTimeRange(): String {
        return "${formatTime(startTime)} - ${formatTime(endTime)}"
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Get duration of this segment in milliseconds
     */
    fun getDuration(): Long = endTime - startTime
}

/**
 * Metadata for tracking transcription status
 */
@Serializable
data class TranscriptionStatus(
    val recordingId: Int,
    val isTranscribing: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val error: String? = null
)

/**
 * Voice activity segment detected by VAD
 */
data class VoiceSegment(
    val startTime: Long, // milliseconds
    val endTime: Long, // milliseconds
    val confidence: Float,
    val audioData: ShortArray? = null // Optional: the actual audio data for this segment
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoiceSegment

        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false
        if (confidence != other.confidence) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        return result
    }
}
