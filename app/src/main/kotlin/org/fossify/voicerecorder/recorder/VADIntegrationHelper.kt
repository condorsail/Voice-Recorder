package org.fossify.voicerecorder.recorder

import android.content.Context
import org.fossify.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus

/**
 * Helper class to integrate Silero VAD with the recording pipeline.
 *
 * This class simplifies VAD integration by handling:
 * - VAD processor lifecycle
 * - Sample rate conversion/downsampling
 * - EventBus integration
 * - State management
 *
 * Usage:
 * ```
 * val vadHelper = VADIntegrationHelper(context, recordingSampleRate, enabled, threshold)
 * vadHelper.processAudioChunk(audioSamples)
 * vadHelper.release()
 * ```
 */
class VADIntegrationHelper(
    private val context: Context,
    private val recordingSampleRate: Int,
    private val enabled: Boolean,
    private val threshold: Float
) {
    private var vadProcessor: SileroVADProcessor? = null
    private var downsampleFactor: Int = 1
    private var targetVADSampleRate: Int = 16000

    init {
        if (enabled) {
            initializeVAD()
        }
    }

    /**
     * Initialize the VAD processor with appropriate sample rate
     */
    private fun initializeVAD() {
        // Determine best VAD sample rate based on recording sample rate
        targetVADSampleRate = when {
            recordingSampleRate >= 16000 -> 16000
            recordingSampleRate >= 8000 -> 8000
            else -> {
                // If recording below 8kHz, use 8kHz and upsample (edge case)
                8000
            }
        }

        // Calculate downsample factor if needed
        downsampleFactor = when {
            recordingSampleRate == targetVADSampleRate -> 1
            recordingSampleRate % targetVADSampleRate == 0 -> recordingSampleRate / targetVADSampleRate
            else -> 1 // No downsampling if not evenly divisible
        }

        try {
            vadProcessor = SileroVADProcessor(
                context = context,
                sampleRate = targetVADSampleRate,
                threshold = threshold
            )
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
            vadProcessor = null
        }
    }

    /**
     * Process an audio chunk and post VAD results to EventBus
     *
     * @param audioSamples Audio samples in PCM16 format
     * @return VAD result or null if VAD is disabled/failed
     */
    fun processAudioChunk(audioSamples: ShortArray): SileroVADProcessor.VADResult? {
        val processor = vadProcessor ?: return null

        try {
            // Downsample if necessary
            val processedSamples = if (downsampleFactor > 1) {
                downsampleAudio(audioSamples, downsampleFactor)
            } else {
                audioSamples
            }

            // Process with VAD
            val result = processor.process(processedSamples)

            // Post event to EventBus
            EventBus.getDefault().post(
                Events.VoiceActivityDetected(
                    result.isVoiceActive,
                    result.confidence
                )
            )

            return result
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
            return null
        }
    }

    /**
     * Simple downsampling by taking every Nth sample
     *
     * @param samples Input audio samples
     * @param factor Downsample factor (e.g., 3 for 48kHz -> 16kHz)
     * @return Downsampled audio
     */
    private fun downsampleAudio(samples: ShortArray, factor: Int): ShortArray {
        if (factor <= 1) return samples

        val outputSize = samples.size / factor
        return ShortArray(outputSize) { i ->
            samples[i * factor]
        }
    }

    /**
     * Reset VAD internal state (call when starting a new recording)
     */
    fun resetStates() {
        vadProcessor?.resetStates()
    }

    /**
     * Check if VAD is enabled and initialized
     */
    fun isVADEnabled(): Boolean {
        return vadProcessor != null
    }

    /**
     * Get the VAD sample rate being used
     */
    fun getVADSampleRate(): Int {
        return targetVADSampleRate
    }

    /**
     * Get the downsample factor
     */
    fun getDownsampleFactor(): Int {
        return downsampleFactor
    }

    /**
     * Release VAD resources
     */
    fun release() {
        vadProcessor?.release()
        vadProcessor = null
    }

    companion object {
        /**
         * Create VAD helper from Config
         */
        fun fromConfig(context: Context, config: org.fossify.voicerecorder.helpers.Config): VADIntegrationHelper {
            return VADIntegrationHelper(
                context = context,
                recordingSampleRate = config.samplingRate,
                enabled = config.enableVAD,
                threshold = config.vadThreshold
            )
        }
    }
}
