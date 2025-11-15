package org.fossify.voicerecorder.helpers

import android.content.Context
import org.fossify.voicerecorder.models.VoiceSegment
import org.fossify.voicerecorder.recorder.SileroVADProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Filters audio using VAD to extract only voice segments
 * This dramatically reduces processing time for Whisper by skipping silence
 */
class VADAudioFilter(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val threshold: Float = 0.5f,
    private val minSpeechDurationMs: Int = 500
) {
    private val vadProcessor = SileroVADProcessor(context, sampleRate, threshold)
    private val chunkSize = SileroVADProcessor.getChunkSize(sampleRate)

    // State tracking
    private var currentSegment: VoiceSegmentBuilder? = null
    private val completedSegments = mutableListOf<VoiceSegment>()
    private var totalSamplesProcessed = 0

    /**
     * Process audio chunk and update voice segments
     * @param audioData PCM16 audio data as ShortArray
     * @return List of completed voice segments
     */
    fun processChunk(audioData: ShortArray): List<VoiceSegment> {
        val newSegments = mutableListOf<VoiceSegment>()

        var offset = 0
        while (offset < audioData.size) {
            val remainingSamples = audioData.size - offset
            val samplesToProcess = minOf(chunkSize, remainingSamples)

            if (samplesToProcess < chunkSize && remainingSamples > 0) {
                // Not enough samples for a full chunk, buffer them for next call
                break
            }

            val chunk = audioData.copyOfRange(offset, offset + samplesToProcess)
            val result = vadProcessor.process(chunk)

            val currentTimeMs = samplesToMillis(totalSamplesProcessed)

            if (result.isVoiceActive) {
                // Voice detected - start or continue segment
                if (currentSegment == null) {
                    currentSegment = VoiceSegmentBuilder(
                        startTime = currentTimeMs,
                        confidence = result.confidence
                    )
                }
                currentSegment?.addAudio(chunk)
                currentSegment?.updateConfidence(result.confidence)
            } else {
                // No voice - finalize current segment if exists
                if (currentSegment != null) {
                    val segment = currentSegment!!.build(currentTimeMs)

                    // Only keep segments longer than minimum duration
                    if (segment.endTime - segment.startTime >= minSpeechDurationMs) {
                        completedSegments.add(segment)
                        newSegments.add(segment)
                    }

                    currentSegment = null
                }
            }

            offset += samplesToProcess
            totalSamplesProcessed += samplesToProcess
        }

        return newSegments
    }

    /**
     * Process complete audio buffer and return all voice segments
     * This is useful for batch processing of recorded files
     */
    fun processCompleteAudio(audioData: ShortArray): List<VoiceSegment> {
        reset()
        processChunk(audioData)
        return finalizeSegments()
    }

    /**
     * Finalize any remaining segments and return them
     */
    fun finalizeSegments(): List<VoiceSegment> {
        val newSegments = mutableListOf<VoiceSegment>()

        currentSegment?.let { builder ->
            val currentTimeMs = samplesToMillis(totalSamplesProcessed)
            val segment = builder.build(currentTimeMs)

            if (segment.endTime - segment.startTime >= minSpeechDurationMs) {
                completedSegments.add(segment)
                newSegments.add(segment)
            }

            currentSegment = null
        }

        return newSegments
    }

    /**
     * Get all completed voice segments
     */
    fun getAllSegments(): List<VoiceSegment> {
        return completedSegments.toList()
    }

    /**
     * Get total duration of voice activity in milliseconds
     */
    fun getTotalVoiceDuration(): Long {
        return completedSegments.sumOf { it.endTime - it.startTime }
    }

    /**
     * Get percentage of audio that contains voice
     */
    fun getVoiceActivityPercentage(): Float {
        val totalDuration = samplesToMillis(totalSamplesProcessed)
        if (totalDuration == 0L) return 0f

        val voiceDuration = getTotalVoiceDuration()
        return (voiceDuration.toFloat() / totalDuration.toFloat()) * 100f
    }

    /**
     * Reset the filter state
     */
    fun reset() {
        vadProcessor.resetStates()
        currentSegment = null
        completedSegments.clear()
        totalSamplesProcessed = 0
    }

    /**
     * Release resources
     */
    fun release() {
        vadProcessor.release()
    }

    /**
     * Convert samples count to milliseconds
     */
    private fun samplesToMillis(samples: Int): Long {
        return (samples.toLong() * 1000L) / sampleRate.toLong()
    }

    /**
     * Helper class for building voice segments
     */
    private class VoiceSegmentBuilder(
        val startTime: Long,
        private var confidence: Float
    ) {
        private val audioBuffer = mutableListOf<Short>()
        private var confidenceCount = 1

        fun addAudio(samples: ShortArray) {
            audioBuffer.addAll(samples.toList())
        }

        fun updateConfidence(newConfidence: Float) {
            // Calculate running average of confidence
            confidence = (confidence * confidenceCount + newConfidence) / (confidenceCount + 1)
            confidenceCount++
        }

        fun build(endTime: Long): VoiceSegment {
            return VoiceSegment(
                startTime = startTime,
                endTime = endTime,
                confidence = confidence,
                audioData = audioBuffer.toShortArray()
            )
        }
    }

    companion object {
        /**
         * Read PCM16 audio from file
         */
        fun readPCM16FromFile(filePath: String): ShortArray {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                return ShortArray(0)
            }

            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val shorts = ShortArray(bytes.size / 2)

            for (i in shorts.indices) {
                shorts[i] = buffer.getShort()
            }

            return shorts
        }

        /**
         * Extract voice segments from PCM file
         */
        fun extractVoiceSegments(
            context: Context,
            filePath: String,
            sampleRate: Int = 16000,
            threshold: Float = 0.5f,
            minSpeechDurationMs: Int = 500
        ): List<VoiceSegment> {
            val filter = VADAudioFilter(context, sampleRate, threshold, minSpeechDurationMs)
            val audio = readPCM16FromFile(filePath)
            val segments = filter.processCompleteAudio(audio)
            filter.release()
            return segments
        }

        /**
         * Combine voice segments into a continuous audio buffer (silence removed)
         */
        fun combineVoiceSegments(segments: List<VoiceSegment>): ShortArray {
            val totalSamples = segments.sumOf { it.audioData?.size ?: 0 }
            val combined = ShortArray(totalSamples)

            var offset = 0
            for (segment in segments) {
                segment.audioData?.let { audio ->
                    audio.copyInto(combined, offset)
                    offset += audio.size
                }
            }

            return combined
        }
    }
}
