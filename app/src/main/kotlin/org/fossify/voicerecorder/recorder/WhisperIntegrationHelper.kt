package org.fossify.voicerecorder.recorder

import android.content.Context
import org.fossify.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class to integrate Whisper transcription with the recording pipeline.
 *
 * This class simplifies Whisper integration by handling:
 * - Whisper processor lifecycle
 * - Audio buffering for transcription
 * - Asynchronous transcription processing
 * - EventBus integration
 * - Sample rate conversion
 *
 * Usage:
 * ```
 * val whisperHelper = WhisperIntegrationHelper(context, config)
 * whisperHelper.processAudioChunk(audioSamples)
 * whisperHelper.finalizeTranscription()  // Call when recording stops
 * whisperHelper.release()
 * ```
 */
class WhisperIntegrationHelper(
    private val context: Context,
    private val enabled: Boolean,
    private val modelName: String,
    private val language: String?,
    private val translate: Boolean,
    private val recordingSampleRate: Int
) {
    private var whisperProcessor: WhisperProcessor? = null
    private val audioBuffer = mutableListOf<Short>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // Buffer settings
    private val bufferSizeSeconds = 30  // Transcribe every 30 seconds
    private val bufferSizeInSamples = recordingSampleRate * bufferSizeSeconds

    init {
        if (enabled) {
            initializeWhisper()
        }
    }

    /**
     * Initialize the Whisper processor
     */
    private fun initializeWhisper() {
        try {
            // Check if model exists before initializing
            if (!WhisperProcessor.modelExists(context, modelName)) {
                postError("Whisper model not found: $modelName. Please download it to assets/")
                return
            }

            whisperProcessor = WhisperProcessor(
                context = context,
                modelName = modelName,
                language = language,
                translate = translate
            )
        } catch (e: Exception) {
            postError("Failed to initialize Whisper: ${e.message}")
            whisperProcessor = null
        }
    }

    /**
     * Process an audio chunk and accumulate for transcription
     *
     * @param audioSamples Audio samples in PCM16 format
     */
    fun processAudioChunk(audioSamples: ShortArray) {
        val processor = whisperProcessor ?: return

        // Add to buffer
        synchronized(audioBuffer) {
            audioBuffer.addAll(audioSamples.toList())

            // Transcribe when buffer is full
            if (audioBuffer.size >= bufferSizeInSamples) {
                val samplesToTranscribe = audioBuffer.toShortArray()
                audioBuffer.clear()

                // Run transcription in background
                transcribeAsync(processor, samplesToTranscribe, isFinal = false)
            }
        }
    }

    /**
     * Finalize and transcribe any remaining audio in buffer
     * Call this when recording stops
     */
    fun finalizeTranscription() {
        val processor = whisperProcessor ?: return

        synchronized(audioBuffer) {
            if (audioBuffer.isNotEmpty()) {
                val samplesToTranscribe = audioBuffer.toShortArray()
                audioBuffer.clear()

                // Run final transcription
                transcribeAsync(processor, samplesToTranscribe, isFinal = true)
            }
        }
    }

    /**
     * Transcribe audio asynchronously and post results to EventBus
     */
    private fun transcribeAsync(processor: WhisperProcessor, audioSamples: ShortArray, isFinal: Boolean) {
        scope.launch {
            try {
                // Perform transcription on IO thread
                val result = processor.transcribe(audioSamples, recordingSampleRate)

                // Post result to main thread via EventBus
                withContext(Dispatchers.Main) {
                    if (result.isSuccess && result.text.isNotEmpty()) {
                        EventBus.getDefault().post(
                            Events.TranscriptionResult(
                                text = result.text,
                                isFinal = isFinal,
                                language = result.language
                            )
                        )
                    } else if (result.error != null) {
                        postError("Transcription failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    postError("Transcription error: ${e.message}")
                }
            }
        }
    }

    /**
     * Post error event
     */
    private fun postError(error: String) {
        EventBus.getDefault().post(Events.TranscriptionError(error))
    }

    /**
     * Clear audio buffer without transcribing
     */
    fun clearBuffer() {
        synchronized(audioBuffer) {
            audioBuffer.clear()
        }
    }

    /**
     * Check if Whisper is enabled and initialized
     */
    fun isWhisperEnabled(): Boolean {
        return whisperProcessor != null
    }

    /**
     * Get model information
     */
    fun getModelInfo(): WhisperProcessor.ModelInfo? {
        return whisperProcessor?.getModelInfo()
    }

    /**
     * Release Whisper resources
     */
    fun release() {
        whisperProcessor?.release()
        whisperProcessor = null
        clearBuffer()
    }

    companion object {
        /**
         * Create Whisper helper from Config
         */
        fun fromConfig(context: Context, config: org.fossify.voicerecorder.helpers.Config): WhisperIntegrationHelper {
            return WhisperIntegrationHelper(
                context = context,
                enabled = config.enableWhisper,
                modelName = config.whisperModel,
                language = config.whisperLanguage,
                translate = config.whisperTranslate,
                recordingSampleRate = config.samplingRate
            )
        }

        /**
         * Get list of available Whisper models
         */
        fun getAvailableModels(context: Context): List<String> {
            val models = mutableListOf<String>()

            // Check common model names
            val commonModels = listOf(
                WhisperProcessor.MODEL_TINY_EN,
                WhisperProcessor.MODEL_BASE_EN,
                WhisperProcessor.MODEL_TINY,
                WhisperProcessor.MODEL_BASE,
                "ggml-small.en.bin",
                "ggml-small.bin"
            )

            for (model in commonModels) {
                if (WhisperProcessor.modelExists(context, model)) {
                    models.add(model)
                }
            }

            return models
        }
    }
}
