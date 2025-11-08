package org.fossify.voicerecorder.recorder

import android.content.Context
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperSamplingStrategy
import java.io.File
import java.io.FileOutputStream

/**
 * Whisper speech-to-text processor using whisper-jni.
 *
 * This class provides real-time and offline speech transcription using OpenAI's Whisper model.
 * Supports multiple languages and various model sizes.
 *
 * Usage:
 * ```
 * val whisper = WhisperProcessor(context, modelName = "ggml-tiny.en.bin")
 * val result = whisper.transcribe(audioSamples, sampleRate = 16000)
 * println("Transcription: ${result.text}")
 * whisper.release()
 * ```
 *
 * @param context Android context for accessing assets
 * @param modelName Name of the GGML model file in assets (e.g., "ggml-tiny.en.bin")
 * @param language Optional language code (e.g., "en", "es", "fr"). Auto-detect if null.
 * @param translate Set to true to translate to English
 * @param useGpu Enable GPU acceleration if available (experimental)
 */
class WhisperProcessor(
    private val context: Context,
    private val modelName: String = "ggml-tiny.en.bin",
    private val language: String? = null,
    private val translate: Boolean = false,
    private val useGpu: Boolean = false
) {
    private var whisperContext: WhisperContext? = null
    private var modelFile: File? = null

    // Whisper requires 16kHz sample rate
    private val requiredSampleRate = 16000

    init {
        initializeWhisper()
    }

    /**
     * Initialize Whisper context and load the model
     */
    private fun initializeWhisper() {
        try {
            // Extract model from assets to cache directory
            modelFile = File(context.cacheDir, modelName)

            if (!modelFile!!.exists()) {
                context.assets.open(modelName).use { inputStream ->
                    FileOutputStream(modelFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // Create Whisper context
            whisperContext = WhisperContext.createContextFromFile(modelFile!!.absolutePath)

            // Enable GPU if requested and available
            if (useGpu) {
                try {
                    whisperContext?.setUseGpu(true)
                } catch (e: Exception) {
                    // GPU not available, continue with CPU
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Whisper model: ${e.message}", e)
        }
    }

    /**
     * Transcribe audio samples to text
     *
     * @param audioSamples Audio samples as FloatArray (normalized to -1.0 to 1.0)
     * @param sampleRate Sample rate of the audio (will be resampled to 16kHz if different)
     * @return TranscriptionResult containing transcribed text and metadata
     */
    fun transcribe(audioSamples: FloatArray, sampleRate: Int = 16000): TranscriptionResult {
        val ctx = whisperContext ?: throw IllegalStateException("Whisper context not initialized")

        try {
            // Resample if needed
            val processedSamples = if (sampleRate != requiredSampleRate) {
                resampleAudio(audioSamples, sampleRate, requiredSampleRate)
            } else {
                audioSamples
            }

            // Create parameters for full transcription
            val params = WhisperFullParams()
            params.strategy = WhisperSamplingStrategy.GREEDY
            params.printProgress = false
            params.printRealtime = false
            params.printTimestamps = false

            // Set language if specified
            language?.let {
                params.language = it
            }

            // Set translation mode
            params.translate = translate

            // Run transcription
            val startTime = System.currentTimeMillis()
            val result = ctx.transcribeData(processedSamples, params)
            val processingTime = System.currentTimeMillis() - startTime

            // Extract full text
            val fullText = ctx.getTextSegments().joinToString(" ") { it.text }

            return TranscriptionResult(
                text = fullText.trim(),
                segments = ctx.getTextSegments().map { segment ->
                    TranscriptionSegment(
                        text = segment.text,
                        startTime = segment.startTimestamp,
                        endTime = segment.endTimestamp
                    )
                },
                language = language ?: "auto",
                processingTimeMs = processingTime,
                error = null
            )
        } catch (e: Exception) {
            return TranscriptionResult(
                text = "",
                segments = emptyList(),
                language = language ?: "unknown",
                processingTimeMs = 0,
                error = e.message
            )
        }
    }

    /**
     * Transcribe audio samples from PCM16 format
     *
     * @param audioSamples Audio samples as ShortArray (PCM16)
     * @param sampleRate Sample rate of the audio
     * @return TranscriptionResult
     */
    fun transcribe(audioSamples: ShortArray, sampleRate: Int = 16000): TranscriptionResult {
        // Convert PCM16 to float
        val floatSamples = FloatArray(audioSamples.size) { i ->
            audioSamples[i].toFloat() / 32768f
        }
        return transcribe(floatSamples, sampleRate)
    }

    /**
     * Simple resampling by linear interpolation
     * Note: For production use, consider using a proper resampling library
     */
    private fun resampleAudio(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples

        val ratio = fromRate.toFloat() / toRate.toFloat()
        val newLength = (samples.size / ratio).toInt()
        val resampled = FloatArray(newLength)

        for (i in resampled.indices) {
            val srcIndex = i * ratio
            val srcIndexInt = srcIndex.toInt()

            if (srcIndexInt + 1 < samples.size) {
                // Linear interpolation
                val frac = srcIndex - srcIndexInt
                resampled[i] = samples[srcIndexInt] * (1 - frac) + samples[srcIndexInt + 1] * frac
            } else if (srcIndexInt < samples.size) {
                resampled[i] = samples[srcIndexInt]
            }
        }

        return resampled
    }

    /**
     * Get information about the loaded model
     */
    fun getModelInfo(): ModelInfo {
        val ctx = whisperContext
        return if (ctx != null) {
            ModelInfo(
                modelName = modelName,
                isMultilingual = !modelName.contains(".en."),
                isLoaded = true,
                language = language,
                useGpu = useGpu
            )
        } else {
            ModelInfo(
                modelName = modelName,
                isMultilingual = false,
                isLoaded = false,
                language = null,
                useGpu = false
            )
        }
    }

    /**
     * Release Whisper resources
     */
    fun release() {
        try {
            whisperContext?.release()
            whisperContext = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Data class representing transcription result
     */
    data class TranscriptionResult(
        val text: String,
        val segments: List<TranscriptionSegment>,
        val language: String,
        val processingTimeMs: Long,
        val error: String? = null
    ) {
        val isSuccess: Boolean
            get() = error == null && text.isNotEmpty()
    }

    /**
     * Data class representing a transcription segment with timing
     */
    data class TranscriptionSegment(
        val text: String,
        val startTime: Long,
        val endTime: Long
    )

    /**
     * Data class representing model information
     */
    data class ModelInfo(
        val modelName: String,
        val isMultilingual: Boolean,
        val isLoaded: Boolean,
        val language: String?,
        val useGpu: Boolean
    )

    companion object {
        /**
         * Check if a model file exists in assets
         */
        fun modelExists(context: Context, modelName: String): Boolean {
            return try {
                context.assets.open(modelName).use { true }
            } catch (e: Exception) {
                // Also check in cache
                File(context.cacheDir, modelName).exists()
            }
        }

        /**
         * Get the required sample rate for Whisper
         */
        fun getRequiredSampleRate(): Int = 16000

        /**
         * Recommended model for English-only use
         */
        const val MODEL_TINY_EN = "ggml-tiny.en.bin"

        /**
         * Recommended model for better accuracy (English-only)
         */
        const val MODEL_BASE_EN = "ggml-base.en.bin"

        /**
         * Recommended model for multilingual use
         */
        const val MODEL_TINY = "ggml-tiny.bin"

        /**
         * Better accuracy multilingual model
         */
        const val MODEL_BASE = "ggml-base.bin"
    }
}
