package org.fossify.voicerecorder.recorder

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sin
import kotlin.math.PI

/**
 * Unit tests and utilities for Whisper speech-to-text integration.
 *
 * Note: Full integration tests require Android instrumentation as they need:
 * - Android Context for asset loading
 * - Whisper model files in assets
 * - Native whisper-jni libraries
 *
 * For complete integration testing, move to androidTest directory.
 */
class WhisperTest {

    /**
     * Test model name constants
     */
    @Test
    fun testModelConstants() {
        assertEquals("ggml-tiny.en.bin", WhisperProcessor.MODEL_TINY_EN)
        assertEquals("ggml-base.en.bin", WhisperProcessor.MODEL_BASE_EN)
        assertEquals("ggml-tiny.bin", WhisperProcessor.MODEL_TINY)
        assertEquals("ggml-base.bin", WhisperProcessor.MODEL_BASE)
    }

    /**
     * Test required sample rate
     */
    @Test
    fun testRequiredSampleRate() {
        assertEquals(16000, WhisperProcessor.getRequiredSampleRate())
    }

    /**
     * Test transcription result structure
     */
    @Test
    fun testTranscriptionResult() {
        val result = WhisperProcessor.TranscriptionResult(
            text = "Hello, world!",
            segments = listOf(
                WhisperProcessor.TranscriptionSegment("Hello,", 0, 500),
                WhisperProcessor.TranscriptionSegment("world!", 500, 1000)
            ),
            language = "en",
            processingTimeMs = 123,
            error = null
        )

        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Hello, world!", result.text)
        assertEquals(2, result.segments.size)
        assertEquals("en", result.language)
        assertNull(result.error)
    }

    /**
     * Test transcription error handling
     */
    @Test
    fun testTranscriptionError() {
        val result = WhisperProcessor.TranscriptionResult(
            text = "",
            segments = emptyList(),
            language = "unknown",
            processingTimeMs = 0,
            error = "Model not found"
        )

        assertFalse("Result should not be successful", result.isSuccess)
        assertNotNull("Error should be present", result.error)
        assertTrue("Text should be empty on error", result.text.isEmpty())
    }

    /**
     * Test model info structure
     */
    @Test
    fun testModelInfo() {
        val info = WhisperProcessor.ModelInfo(
            modelName = "ggml-tiny.en.bin",
            isMultilingual = false,
            isLoaded = true,
            language = "en",
            useGpu = false
        )

        assertEquals("ggml-tiny.en.bin", info.modelName)
        assertFalse("English-only model should not be multilingual", info.isMultilingual)
        assertTrue("Model should be loaded", info.isLoaded)
    }

    /**
     * Test multilingual model detection
     */
    @Test
    fun testMultilingualDetection() {
        // English-only models
        assertFalse("tiny.en should not be multilingual", "ggml-tiny.en.bin".contains(".en."))
        assertFalse("base.en should not be multilingual", "ggml-base.en.bin".contains(".en."))

        // Multilingual models
        assertFalse("tiny should be multilingual", "ggml-tiny.bin".contains(".en."))
        assertFalse("base should be multilingual", "ggml-base.bin".contains(".en."))
    }

    /**
     * Test transcription segment timing
     */
    @Test
    fun testSegmentTiming() {
        val segment = WhisperProcessor.TranscriptionSegment(
            text = "Test phrase",
            startTime = 1000,  // 1 second
            endTime = 2500     // 2.5 seconds
        )

        assertEquals(1000L, segment.startTime)
        assertEquals(2500L, segment.endTime)
        assertEquals(1500L, segment.endTime - segment.startTime)  // Duration
    }

    // ========== Test Utilities ==========

    companion object {
        /**
         * Generate synthetic silence for testing
         */
        fun generateSilence(sampleCount: Int): ShortArray {
            return ShortArray(sampleCount) { 0 }
        }

        /**
         * Generate synthetic speech-like audio for testing
         *
         * @param sampleRate Sample rate in Hz
         * @param durationMs Duration in milliseconds
         * @param frequency Base frequency for modulation
         */
        fun generateSpeechLike(
            sampleRate: Int,
            durationMs: Int,
            frequency: Int = 200
        ): ShortArray {
            val sampleCount = (sampleRate * durationMs) / 1000
            return ShortArray(sampleCount) { i ->
                val time = i.toDouble() / sampleRate
                // Amplitude modulation mimicking speech
                val envelope = (sin(2.0 * PI * 4.0 * time) + 1.0) / 2.0
                val carrier = sin(2.0 * PI * frequency * time)
                (envelope * carrier * Short.MAX_VALUE * 0.5).toInt().toShort()
            }
        }

        /**
         * Generate a sine wave tone
         *
         * @param sampleRate Sample rate in Hz
         * @param frequency Frequency in Hz
         * @param durationMs Duration in milliseconds
         * @param amplitude Amplitude (0.0 to 1.0)
         */
        fun generateTone(
            sampleRate: Int,
            frequency: Int,
            durationMs: Int,
            amplitude: Float = 0.5f
        ): ShortArray {
            val sampleCount = (sampleRate * durationMs) / 1000
            return ShortArray(sampleCount) { i ->
                val time = i.toDouble() / sampleRate
                val value = amplitude * sin(2.0 * PI * frequency * time)
                (value * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /**
         * Generate white noise
         */
        fun generateNoise(sampleCount: Int, amplitude: Float = 0.1f): ShortArray {
            return ShortArray(sampleCount) {
                ((Math.random() - 0.5) * 2.0 * amplitude * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /**
         * Convert PCM16 to Float32 (normalized)
         */
        fun convertToFloat(samples: ShortArray): FloatArray {
            return FloatArray(samples.size) { i ->
                samples[i].toFloat() / Short.MAX_VALUE
            }
        }

        /**
         * Convert Float32 to PCM16
         */
        fun convertToShort(samples: FloatArray): ShortArray {
            return ShortArray(samples.size) { i ->
                (samples[i] * Short.MAX_VALUE).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()
            }
        }

        /**
         * Simple downsampling by decimation
         */
        fun downsample(samples: ShortArray, factor: Int): ShortArray {
            if (factor <= 1) return samples
            val outputSize = samples.size / factor
            return ShortArray(outputSize) { i ->
                samples[i * factor]
            }
        }

        /**
         * Convert stereo to mono
         */
        fun stereoToMono(stereoSamples: ShortArray): ShortArray {
            val monoSize = stereoSamples.size / 2
            return ShortArray(monoSize) { i ->
                val left = stereoSamples[i * 2].toInt()
                val right = stereoSamples[i * 2 + 1].toInt()
                ((left + right) / 2).toShort()
            }
        }

        /**
         * Apply simple gain to audio samples
         */
        fun applyGain(samples: ShortArray, gain: Float): ShortArray {
            return ShortArray(samples.size) { i ->
                (samples[i] * gain).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()
            }
        }

        /**
         * Calculate RMS (Root Mean Square) amplitude
         */
        fun calculateRMS(samples: ShortArray): Double {
            var sum = 0.0
            for (sample in samples) {
                sum += sample.toDouble() * sample.toDouble()
            }
            return kotlin.math.sqrt(sum / samples.size)
        }

        /**
         * Detect if audio is mostly silence
         */
        fun isSilence(samples: ShortArray, threshold: Int = 500): Boolean {
            val rms = calculateRMS(samples)
            return rms < threshold
        }
    }
}

/**
 * Manual integration test examples
 *
 * To run these tests:
 * 1. Move this class to androidTest directory
 * 2. Add @RunWith(AndroidJUnit4::class)
 * 3. Ensure a Whisper model is in assets
 * 4. Run as Android instrumentation test
 */
/*
@RunWith(AndroidJUnit4::class)
class WhisperIntegrationTest {
    private lateinit var context: Context
    private lateinit var whisperProcessor: WhisperProcessor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Check if model exists
        assumeTrue(
            "Whisper model not found",
            WhisperProcessor.modelExists(context, WhisperProcessor.MODEL_TINY_EN)
        )

        whisperProcessor = WhisperProcessor(
            context = context,
            modelName = WhisperProcessor.MODEL_TINY_EN,
            language = "en"
        )
    }

    @After
    fun teardown() {
        whisperProcessor.release()
    }

    @Test
    fun testSilenceTranscription() {
        val silence = WhisperTest.generateSilence(16000)  // 1 second of silence
        val result = whisperProcessor.transcribe(silence, 16000)

        // Silence should produce empty or minimal output
        assertTrue(
            "Silence should produce minimal text",
            result.text.length < 20
        )
    }

    @Test
    fun testModelInfo() {
        val info = whisperProcessor.getModelInfo()

        assertTrue("Model should be loaded", info.isLoaded)
        assertEquals("Model name should match", WhisperProcessor.MODEL_TINY_EN, info.modelName)
        assertFalse("English-only model should not be multilingual", info.isMultilingual)
    }

    @Test
    fun testSampleRateConversion() {
        // Generate audio at 48kHz
        val audio48k = WhisperTest.generateTone(48000, 440, 1000)

        // Should automatically resample to 16kHz
        val result = whisperProcessor.transcribe(audio48k, 48000)

        assertNotNull("Result should not be null", result)
        assertTrue("Should handle sample rate conversion", result.error == null || result.isSuccess)
    }

    @Test
    fun testRealAudioFile() {
        // Load a real audio file from test resources
        val audioFile = context.resources.openRawResource(R.raw.test_audio)
        val audioData = audioFile.readBytes()

        // Convert to PCM16 samples (adjust based on actual format)
        val samples = // ... convert audio data to ShortArray

        val result = whisperProcessor.transcribe(samples, 16000)

        assertTrue("Real audio should transcribe successfully", result.isSuccess)
        assertFalse("Transcription should not be empty", result.text.isEmpty())
    }

    @Test
    fun testWhisperHelper() {
        val helper = WhisperIntegrationHelper(
            context = context,
            enabled = true,
            modelName = WhisperProcessor.MODEL_TINY_EN,
            language = "en",
            translate = false,
            recordingSampleRate = 16000
        )

        assertTrue("Helper should be enabled", helper.isWhisperEnabled())

        val modelInfo = helper.getModelInfo()
        assertNotNull("Model info should be available", modelInfo)

        helper.release()
    }
}
*/
