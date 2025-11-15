package org.fossify.voicerecorder.recorder

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getOfflineRecognizerConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Whisper speech-to-text processor using Sherpa-ONNX.
 *
 * This class provides offline speech transcription using OpenAI's Whisper model
 * via ONNX Runtime for optimal Android performance.
 * Supports multiple languages and various model sizes.
 *
 * Usage:
 * ```
 * val whisper = WhisperProcessor(context, modelName = "tiny.en")
 * val result = whisper.transcribe(audioSamples, sampleRate = 16000)
 * println("Transcription: ${result.text}")
 * whisper.release()
 * ```
 *
 * @param context Android context for accessing assets
 * @param modelName Name of the model directory in assets (e.g., "tiny.en", "base.en")
 * @param language Optional language code (e.g., "en", "es", "fr"). Auto-detect if null.
 * @param translate Set to true to translate to English
 */
class WhisperProcessor(
    private val context: Context,
    private val modelName: String = "tiny.en",
    private val language: String? = null,
    private val translate: Boolean = false
) {
    private var recognizer: OfflineRecognizer? = null
    private var modelDir: File? = null

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
            // Extract model files from assets to cache directory
            modelDir = File(context.cacheDir, "whisper-$modelName")
            if (!modelDir!!.exists()) {
                modelDir!!.mkdirs()
                extractModelFromAssets(modelName, modelDir!!)
            }

            // Create Sherpa-ONNX config for Whisper
            val whisperConfig = OfflineWhisperModelConfig(
                encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                language = language ?: "en",
                task = if (translate) "translate" else "transcribe"
            )

            val config = getOfflineRecognizerConfig(
                whisper = whisperConfig,
                modelDir = modelDir!!.absolutePath,
                numThreads = 2,
                provider = "cpu",
                enableEndpoint = true
            )

            // Create recognizer
            recognizer = OfflineRecognizer(config)

        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Whisper model: ${e.message}", e)
        }
    }

    /**
     * Extract model files from assets to file system
     */
    private fun extractModelFromAssets(modelName: String, targetDir: File) {
        // Expected files in assets/whisper-{modelName}/
        val modelFiles = listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "tokens.txt"
        )

        val assetPrefix = "whisper-$modelName"

        for (fileName in modelFiles) {
            try {
                val assetPath = "$assetPrefix/$fileName"
                context.assets.open(assetPath).use { inputStream ->
                    val targetFile = File(targetDir, fileName)
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                // Try without int8 suffix for encoder/decoder
                if (fileName.contains("int8")) {
                    val fallbackName = fileName.replace(".int8", "")
                    try {
                        val assetPath = "$assetPrefix/$fallbackName"
                        context.assets.open(assetPath).use { inputStream ->
                            val targetFile = File(targetDir, fileName)
                            FileOutputStream(targetFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } catch (e2: Exception) {
                        throw RuntimeException("Model file not found: $fileName or $fallbackName", e)
                    }
                } else {
                    throw RuntimeException("Model file not found: $fileName", e)
                }
            }
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
        val rec = recognizer ?: throw IllegalStateException("Whisper recognizer not initialized")

        try {
            // Resample if needed
            val processedSamples = if (sampleRate != requiredSampleRate) {
                resampleAudio(audioSamples, sampleRate, requiredSampleRate)
            } else {
                audioSamples
            }

            // Create stream and feed audio
            val stream = rec.createStream()
            stream.acceptWaveform(processedSamples, requiredSampleRate)

            // Decode
            val startTime = System.currentTimeMillis()
            rec.decode(stream)
            val processingTime = System.currentTimeMillis() - startTime

            // Get result
            val result = stream.result

            // Extract segments with timestamps
            val segments = mutableListOf<TranscriptionSegment>()
            val tokens = result.tokens ?: emptyArray()
            val timestamps = result.timestamps ?: FloatArray(0)

            // Group tokens into segments (simplified - Sherpa may provide better segmentation)
            if (tokens.isNotEmpty() && timestamps.isNotEmpty()) {
                var currentText = StringBuilder()
                var startTime = 0L

                for (i in tokens.indices) {
                    currentText.append(tokens[i]).append(" ")

                    // Create segment every ~5 seconds or at sentence boundaries
                    val currentTimestamp = (timestamps.getOrNull(i) ?: 0f).toLong() * 1000
                    if (i == tokens.lastIndex || currentTimestamp - startTime > 5000) {
                        segments.add(
                            TranscriptionSegment(
                                text = currentText.toString().trim(),
                                startTime = startTime,
                                endTime = currentTimestamp
                            )
                        )
                        currentText = StringBuilder()
                        startTime = currentTimestamp
                    }
                }
            }

            // If no segments created from tokens, create one from full text
            if (segments.isEmpty() && result.text.isNotEmpty()) {
                segments.add(
                    TranscriptionSegment(
                        text = result.text,
                        startTime = 0,
                        endTime = (processedSamples.size * 1000L / requiredSampleRate)
                    )
                )
            }

            stream.release()

            return TranscriptionResult(
                text = result.text,
                segments = segments,
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
        return ModelInfo(
            modelName = modelName,
            isMultilingual = !modelName.contains(".en"),
            isLoaded = recognizer != null,
            language = language,
            useGpu = false  // Sherpa-ONNX uses CPU by default
        )
    }

    /**
     * Release Whisper resources
     */
    fun release() {
        try {
            recognizer?.release()
            recognizer = null
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
                // Check for model directory in assets
                val assetPrefix = "whisper-$modelName"
                context.assets.list(assetPrefix)?.isNotEmpty() ?: false
            } catch (e: Exception) {
                // Also check in cache
                File(context.cacheDir, "whisper-$modelName").exists()
            }
        }

        /**
         * Get the required sample rate for Whisper
         */
        fun getRequiredSampleRate(): Int = 16000

        /**
         * Recommended model for English-only use (ONNX format)
         * Model directory name in assets: whisper-tiny.en/
         */
        const val MODEL_TINY_EN = "tiny.en"

        /**
         * Recommended model for better accuracy (English-only, ONNX format)
         * Model directory name in assets: whisper-base.en/
         */
        const val MODEL_BASE_EN = "base.en"

        /**
         * Recommended model for multilingual use (ONNX format)
         * Model directory name in assets: whisper-tiny/
         */
        const val MODEL_TINY = "tiny"

        /**
         * Better accuracy multilingual model (ONNX format)
         * Model directory name in assets: whisper-base/
         */
        const val MODEL_BASE = "base"
    }
}
