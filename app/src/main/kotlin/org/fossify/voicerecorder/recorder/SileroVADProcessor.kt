package org.fossify.voicerecorder.recorder

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Silero VAD (Voice Activity Detection) processor using ONNX Runtime.
 *
 * This class provides real-time voice activity detection using the Silero VAD model.
 * The model supports two sample rates: 8000 Hz and 16000 Hz.
 *
 * Usage:
 * ```
 * val vad = SileroVADProcessor(context, sampleRate = 16000)
 * val result = vad.process(audioSamples)
 * if (result.isVoiceActive) {
 *     // Voice detected
 * }
 * vad.release()
 * ```
 *
 * @param context Android context for accessing assets
 * @param sampleRate Sample rate of the input audio (8000 or 16000 Hz)
 * @param threshold Voice activity threshold (0.0 to 1.0, default 0.5)
 */
class SileroVADProcessor(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val threshold: Float = 0.5f
) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Model state tensors (maintained between inference calls)
    private var hTensor: FloatArray? = null
    private var cTensor: FloatArray? = null

    // Model configuration constants
    private val stateSize = 128  // Size of h and c state vectors
    private val batchSize = 1L

    // Chunk sizes for different sample rates
    private val chunkSize: Int = when (sampleRate) {
        8000 -> 256   // 32ms at 8kHz
        16000 -> 512  // 32ms at 16kHz
        else -> throw IllegalArgumentException("Sample rate must be 8000 or 16000 Hz")
    }

    init {
        require(sampleRate == 8000 || sampleRate == 16000) {
            "Sample rate must be 8000 or 16000 Hz"
        }
        require(threshold in 0.0f..1.0f) {
            "Threshold must be between 0.0 and 1.0"
        }

        initializeModel()
        resetStates()
    }

    /**
     * Initialize the ONNX Runtime and load the Silero VAD model
     */
    private fun initializeModel() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open("silero_vad.onnx").use { inputStream ->
                inputStream.readBytes()
            }

            ortSession = ortEnvironment?.createSession(modelBytes)
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Silero VAD model", e)
        }
    }

    /**
     * Reset the model's internal state tensors
     */
    fun resetStates() {
        hTensor = FloatArray(2 * batchSize.toInt() * stateSize) { 0f }
        cTensor = FloatArray(2 * batchSize.toInt() * stateSize) { 0f }
    }

    /**
     * Process an audio chunk and return voice activity detection result
     *
     * @param audioSamples Audio samples as ShortArray (PCM16)
     * @return VADResult containing voice activity status and confidence
     */
    fun process(audioSamples: ShortArray): VADResult {
        // Convert samples to the expected chunk size
        val processedSamples = prepareAudioChunk(audioSamples)

        return try {
            val result = runInference(processedSamples)
            result
        } catch (e: Exception) {
            // On error, return no voice detected
            VADResult(isVoiceActive = false, confidence = 0f, error = e.message)
        }
    }

    /**
     * Prepare audio chunk by resampling if needed and normalizing
     */
    private fun prepareAudioChunk(audioSamples: ShortArray): FloatArray {
        // Take only the first chunkSize samples, or pad if needed
        val samplesToProcess = if (audioSamples.size >= chunkSize) {
            audioSamples.copyOfRange(0, chunkSize)
        } else {
            // Pad with zeros if input is too short
            ShortArray(chunkSize).also {
                audioSamples.copyInto(it, 0, 0, audioSamples.size)
            }
        }

        // Normalize PCM16 to float [-1.0, 1.0]
        return FloatArray(samplesToProcess.size) { i ->
            samplesToProcess[i].toFloat() / 32768f
        }
    }

    /**
     * Run ONNX inference with the current audio chunk and state
     */
    private fun runInference(audioChunk: FloatArray): VADResult {
        val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")
        val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")

        // Create input tensors
        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(audioChunk),
            longArrayOf(batchSize, audioChunk.size.toLong())
        )

        val srTensor = OnnxTensor.createTensor(
            env,
            longArrayOf(sampleRate.toLong())
        )

        val hTensorInput = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(hTensor),
            longArrayOf(2, batchSize, stateSize.toLong())
        )

        val cTensorInput = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(cTensor),
            longArrayOf(2, batchSize, stateSize.toLong())
        )

        // Create input map
        val inputs = mapOf(
            "input" to inputTensor,
            "sr" to srTensor,
            "h" to hTensorInput,
            "c" to cTensorInput
        )

        // Run inference
        val outputs = session.run(inputs)

        // Extract output (voice probability)
        val output = outputs.get(0) as OnnxTensor
        val outputValue = output.floatBuffer.get(0)

        // Extract and update state tensors
        if (outputs.size() >= 3) {
            val hn = outputs.get(1) as OnnxTensor
            val cn = outputs.get(2) as OnnxTensor

            hTensor = FloatArray(2 * batchSize.toInt() * stateSize).also {
                hn.floatBuffer.get(it)
            }

            cTensor = FloatArray(2 * batchSize.toInt() * stateSize).also {
                cn.floatBuffer.get(it)
            }
        }

        // Clean up tensors
        inputTensor.close()
        srTensor.close()
        hTensorInput.close()
        cTensorInput.close()
        outputs.close()

        // Return result
        return VADResult(
            isVoiceActive = outputValue >= threshold,
            confidence = outputValue,
            error = null
        )
    }

    /**
     * Release ONNX Runtime resources
     */
    fun release() {
        try {
            ortSession?.close()
            ortSession = null
            ortEnvironment = null
            hTensor = null
            cTensor = null
        } catch (e: Exception) {
            // Log error but don't throw
            e.printStackTrace()
        }
    }

    /**
     * Data class representing VAD processing result
     */
    data class VADResult(
        val isVoiceActive: Boolean,
        val confidence: Float,
        val error: String? = null
    )

    companion object {
        /**
         * Check if the given sample rate is supported
         */
        fun isSampleRateSupported(sampleRate: Int): Boolean {
            return sampleRate == 8000 || sampleRate == 16000
        }

        /**
         * Get the recommended chunk size for a given sample rate
         */
        fun getChunkSize(sampleRate: Int): Int {
            return when (sampleRate) {
                8000 -> 256
                16000 -> 512
                else -> throw IllegalArgumentException("Sample rate must be 8000 or 16000 Hz")
            }
        }
    }
}
