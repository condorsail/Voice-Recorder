package org.fossify.voicerecorder.buffers

import kotlinx.serialization.Serializable

/**
 * Persistent state for crash recovery
 */
@Serializable
data class RecordingState(
    val tempFilePath: String,
    val startTimestamp: Long,
    val durationSeconds: Int,
    val format: String,
    val bytesWritten: Long,
    val sampleRate: Int,
    val bitrate: Int,
    val currentTier1Segment: String? = null
) {
    companion object {
        const val MIN_VALID_RECORDING_BYTES = 32000L // ~1 second at 128kbps
    }

    fun isValid(): Boolean {
        return bytesWritten >= MIN_VALID_RECORDING_BYTES
    }
}

/**
 * Represents a segment in the raw audio buffer (Tier 1)
 */
@Serializable
data class RawAudioSegment(
    val filePath: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val bytesWritten: Long,
    val isComplete: Boolean = false
) {
    fun getSizeBytes(): Long = bytesWritten

    fun getHourKey(): String {
        // Returns "YYYYMMDD_HH" for grouping
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = startTimestamp
        return "%04d%02d%02d_%02d".format(
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.HOUR_OF_DAY)
        )
    }
}

/**
 * Represents a processed segment with transcriptions (Tier 2)
 */
@Serializable
data class ProcessedSegment(
    val audioFilePath: String,
    val metadataFilePath: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,
    val bitrate: Int,
    val vadSegments: List<VADSegment> = emptyList(),
    val transcriptions: List<TranscriptionSegment> = emptyList(),
    val sourceRawSegments: List<String> = emptyList(),
    val processingTimestamp: Long = System.currentTimeMillis(),
    val uploadedToCloud: Boolean = false
) {
    fun getSizeBytes(): Long {
        return java.io.File(audioFilePath).length()
    }
}

/**
 * Voice Activity Detection segment
 */
@Serializable
data class VADSegment(
    val startMs: Long,
    val endMs: Long,
    val confidence: Float
)

/**
 * Whisper transcription segment
 */
@Serializable
data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val language: String,
    val confidence: Float? = null
)

/**
 * Index for managing buffer segments
 */
@Serializable
data class BufferIndex(
    val tier1Segments: MutableList<RawAudioSegment> = mutableListOf(),
    val tier2Segments: MutableList<ProcessedSegment> = mutableListOf(),
    val lastCleanupTimestamp: Long = System.currentTimeMillis(),
    val totalBytesWritten: Long = 0L
)
