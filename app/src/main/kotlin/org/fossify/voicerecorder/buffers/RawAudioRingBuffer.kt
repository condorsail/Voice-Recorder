package org.fossify.voicerecorder.buffers

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.voicerecorder.extensions.config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Tier 1: Raw Audio Ring Buffer (24 hours default)
 *
 * Maintains a rolling buffer of raw PCM16 audio at 16kHz mono.
 * This format is compatible with Whisper and can be reprocessed.
 *
 * Features:
 * - Automatic segment rotation (1 hour segments)
 * - Ring buffer management (auto-delete old segments)
 * - Crash-resistant (each segment is independent)
 * - Metadata tracking for each segment
 */
class RawAudioRingBuffer(private val context: Context) {
    private var currentSegment: RawAudioSegment? = null
    private var currentOutputStream: FileOutputStream? = null
    private var currentBytesWritten = AtomicLong(0L)
    private var currentSegmentStartTime: Long = 0

    private val bufferDir: File
    private val json = Json { prettyPrint = true }

    companion object {
        private const val RAW_BUFFER_DIR = ".buffers/raw"
        private const val INDEX_FILE = "raw_buffer_index.json"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1
        private const val BYTES_PER_SAMPLE = 2 // PCM16
        private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE // 32000 bytes/sec
    }

    init {
        bufferDir = File(context.config.saveRecordingsFolder, RAW_BUFFER_DIR)
        if (!bufferDir.exists()) {
            bufferDir.mkdirs()
        }
    }

    /**
     * Start a new segment
     */
    fun startNewSegment(): Boolean {
        try {
            // Close current segment if exists
            closeCurrentSegment()

            currentSegmentStartTime = System.currentTimeMillis()
            val fileName = getSegmentFileName(currentSegmentStartTime)
            val segmentFile = File(bufferDir, fileName)

            currentOutputStream = FileOutputStream(segmentFile)
            currentBytesWritten.set(0L)

            currentSegment = RawAudioSegment(
                filePath = segmentFile.absolutePath,
                startTimestamp = currentSegmentStartTime,
                endTimestamp = 0L,
                durationMs = 0L,
                sampleRate = SAMPLE_RATE,
                channels = CHANNELS,
                bytesWritten = 0L,
                isComplete = false
            )

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Write raw PCM16 audio data to current segment
     * @param data ShortArray of PCM16 samples
     */
    fun write(data: ShortArray, count: Int) {
        try {
            val stream = currentOutputStream ?: run {
                startNewSegment()
                currentOutputStream
            } ?: return

            // Convert ShortArray to ByteArray (little-endian PCM16)
            val byteArray = ByteArray(count * 2)
            for (i in 0 until count) {
                val sample = data[i]
                byteArray[i * 2] = (sample.toInt() and 0xFF).toByte()
                byteArray[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }

            stream.write(byteArray)
            currentBytesWritten.addAndGet(byteArray.size.toLong())

            // Check if segment duration exceeded
            checkSegmentRotation()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Flush current segment to disk
     */
    fun flush() {
        try {
            currentOutputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Close current segment and finalize metadata
     */
    fun closeCurrentSegment() {
        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
            currentOutputStream = null

            if (currentSegment != null) {
                val endTime = System.currentTimeMillis()
                val finalSegment = currentSegment!!.copy(
                    endTimestamp = endTime,
                    durationMs = endTime - currentSegment!!.startTimestamp,
                    bytesWritten = currentBytesWritten.get(),
                    isComplete = true
                )

                // Save segment metadata
                saveSegmentMetadata(finalSegment)

                // Add to index
                addSegmentToIndex(finalSegment)

                currentSegment = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if current segment should be rotated
     */
    private fun checkSegmentRotation() {
        val elapsed = System.currentTimeMillis() - currentSegmentStartTime
        val segmentDuration = context.config.rawBufferSegmentDurationMs

        if (elapsed >= segmentDuration) {
            closeCurrentSegment()
            startNewSegment()
            cleanupOldSegments()
        }
    }

    /**
     * Clean up segments older than retention period
     */
    private fun cleanupOldSegments() {
        ensureBackgroundThread {
            try {
                val index = loadIndex()
                val retentionMs = context.config.rawBufferRetentionMs
                val cutoffTime = System.currentTimeMillis() - retentionMs

                val segmentsToDelete = index.tier1Segments.filter {
                    it.startTimestamp < cutoffTime
                }

                for (segment in segmentsToDelete) {
                    // Delete audio file
                    File(segment.filePath).delete()

                    // Delete metadata file
                    val metadataPath = segment.filePath.replace(".pcm", ".json")
                    File(metadataPath).delete()

                    // Remove from index
                    index.tier1Segments.remove(segment)
                }

                saveIndex(index)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get segment file name based on timestamp
     */
    private fun getSegmentFileName(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        return "raw_${dateFormat.format(calendar.time)}.pcm"
    }

    /**
     * Save segment metadata to JSON file
     */
    private fun saveSegmentMetadata(segment: RawAudioSegment) {
        try {
            val metadataFile = File(segment.filePath.replace(".pcm", ".json"))
            val metadataJson = json.encodeToString(segment)
            metadataFile.writeText(metadataJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Load buffer index from disk
     */
    private fun loadIndex(): BufferIndex {
        return try {
            val indexFile = File(bufferDir, INDEX_FILE)
            if (indexFile.exists()) {
                val indexJson = indexFile.readText()
                json.decodeFromString<BufferIndex>(indexJson)
            } else {
                BufferIndex()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BufferIndex()
        }
    }

    /**
     * Save buffer index to disk
     */
    private fun saveIndex(index: BufferIndex) {
        try {
            val indexFile = File(bufferDir, INDEX_FILE)
            val indexJson = json.encodeToString(index)
            indexFile.writeText(indexJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Add segment to index
     */
    private fun addSegmentToIndex(segment: RawAudioSegment) {
        ensureBackgroundThread {
            try {
                val index = loadIndex()
                index.tier1Segments.add(segment)
                saveIndex(index)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get all segments in the buffer
     */
    fun getSegments(): List<RawAudioSegment> {
        return loadIndex().tier1Segments.toList()
    }

    /**
     * Get segments within a time range
     */
    fun getSegmentsInRange(startMs: Long, endMs: Long): List<RawAudioSegment> {
        return getSegments().filter { segment ->
            segment.startTimestamp < endMs && segment.endTimestamp > startMs
        }
    }

    /**
     * Get total buffer size in bytes
     */
    fun getTotalSize(): Long {
        return getSegments().sumOf { it.bytesWritten }
    }

    /**
     * Get buffer duration in milliseconds
     */
    fun getTotalDuration(): Long {
        val segments = getSegments()
        if (segments.isEmpty()) return 0L

        val oldest = segments.minByOrNull { it.startTimestamp }?.startTimestamp ?: 0L
        val newest = segments.maxByOrNull { it.endTimestamp }?.endTimestamp ?: 0L

        return newest - oldest
    }

    /**
     * Cleanup all resources
     */
    fun cleanup() {
        closeCurrentSegment()
    }

    /**
     * Delete all segments (complete cleanup)
     */
    fun deleteAll() {
        closeCurrentSegment()

        try {
            val segments = getSegments()
            for (segment in segments) {
                File(segment.filePath).delete()
                File(segment.filePath.replace(".pcm", ".json")).delete()
            }

            File(bufferDir, INDEX_FILE).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        /**
         * Get buffer directory
         */
        fun getBufferDirectory(context: Context): File {
            return File(context.config.saveRecordingsFolder, RAW_BUFFER_DIR)
        }

        /**
         * Check if buffer system is enabled
         */
        fun isEnabled(context: Context): Boolean {
            return context.config.rawBufferEnabled
        }

        /**
         * Calculate estimated size for given duration
         */
        fun estimateSize(durationMs: Long): Long {
            return (durationMs / 1000L) * BYTES_PER_SECOND
        }
    }
}
