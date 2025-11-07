package org.fossify.voicerecorder.recorder

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sin
import kotlin.math.PI

/**
 * Unit tests and utilities for Silero VAD integration.
 *
 * Note: These tests require Android instrumentation to run since they need:
 * - Android Context for asset loading
 * - ONNX Runtime Android libraries
 *
 * For full integration testing, move to androidTest directory.
 */
class SileroVADTest {

    /**
     * Test that sample rate validation works correctly
     */
    @Test
    fun testSampleRateSupport() {
        assertTrue("8000 Hz should be supported", SileroVADProcessor.isSampleRateSupported(8000))
        assertTrue("16000 Hz should be supported", SileroVADProcessor.isSampleRateSupported(16000))
        assertFalse("48000 Hz should not be supported", SileroVADProcessor.isSampleRateSupported(48000))
        assertFalse("44100 Hz should not be supported", SileroVADProcessor.isSampleRateSupported(44100))
    }

    /**
     * Test chunk size calculation
     */
    @Test
    fun testChunkSizeCalculation() {
        assertEquals("8kHz chunk size should be 256", 256, SileroVADProcessor.getChunkSize(8000))
        assertEquals("16kHz chunk size should be 512", 512, SileroVADProcessor.getChunkSize(16000))
    }

    /**
     * Test that invalid sample rates throw exceptions
     */
    @Test(expected = IllegalArgumentException::class)
    fun testInvalidSampleRateThrowsException() {
        SileroVADProcessor.getChunkSize(48000)
    }

    /**
     * Test VAD result structure
     */
    @Test
    fun testVADResultStructure() {
        val result = SileroVADProcessor.VADResult(
            isVoiceActive = true,
            confidence = 0.85f,
            error = null
        )

        assertTrue("Voice should be active", result.isVoiceActive)
        assertEquals("Confidence should be 0.85", 0.85f, result.confidence, 0.001f)
        assertNull("Error should be null", result.error)
    }

    /**
     * Test error handling in VAD result
     */
    @Test
    fun testVADResultWithError() {
        val result = SileroVADProcessor.VADResult(
            isVoiceActive = false,
            confidence = 0f,
            error = "Test error"
        )

        assertFalse("Voice should not be active on error", result.isVoiceActive)
        assertEquals("Confidence should be 0", 0f, result.confidence, 0.001f)
        assertNotNull("Error should not be null", result.error)
    }

    // ========== Test Utilities ==========

    companion object {
        /**
         * Generate synthetic silence audio samples
         */
        fun generateSilence(sampleCount: Int): ShortArray {
            return ShortArray(sampleCount) { 0 }
        }

        /**
         * Generate synthetic sine wave audio (simulating tone)
         *
         * @param sampleRate Sample rate in Hz
         * @param frequency Frequency in Hz
         * @param durationMs Duration in milliseconds
         * @param amplitude Amplitude (0.0 to 1.0)
         */
        fun generateSineWave(
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
         * Generate synthetic speech-like audio (amplitude modulated noise)
         *
         * @param sampleRate Sample rate in Hz
         * @param durationMs Duration in milliseconds
         */
        fun generateSpeechLike(sampleRate: Int, durationMs: Int): ShortArray {
            val sampleCount = (sampleRate * durationMs) / 1000
            return ShortArray(sampleCount) { i ->
                val time = i.toDouble() / sampleRate
                // Amplitude modulation at speech-like rate (3-5 Hz)
                val envelope = (sin(2.0 * PI * 4.0 * time) + 1.0) / 2.0
                // Random noise modulated by envelope
                val noise = (Math.random() - 0.5) * 2.0
                (envelope * noise * Short.MAX_VALUE * 0.3).toInt().toShort()
            }
        }

        /**
         * Generate white noise
         */
        fun generateWhiteNoise(sampleCount: Int, amplitude: Float = 0.1f): ShortArray {
            return ShortArray(sampleCount) {
                ((Math.random() - 0.5) * 2.0 * amplitude * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /**
         * Downsample audio by taking every Nth sample
         */
        fun downsample(samples: ShortArray, factor: Int): ShortArray {
            if (factor <= 1) return samples
            val outputSize = samples.size / factor
            return ShortArray(outputSize) { i ->
                samples[i * factor]
            }
        }

        /**
         * Calculate RMS (Root Mean Square) amplitude of audio samples
         */
        fun calculateRMS(samples: ShortArray): Double {
            var sum = 0.0
            for (sample in samples) {
                sum += sample.toDouble() * sample.toDouble()
            }
            return kotlin.math.sqrt(sum / samples.size)
        }

        /**
         * Normalize audio samples to range [-1.0, 1.0]
         */
        fun normalizeToPCM16(samples: ShortArray): FloatArray {
            return FloatArray(samples.size) { i ->
                samples[i].toFloat() / Short.MAX_VALUE
            }
        }
    }
}

/**
 * Manual integration test example
 *
 * To run this test:
 * 1. Move this class to androidTest directory
 * 2. Add @RunWith(AndroidJUnit4::class)
 * 3. Run as Android instrumentation test
 */
/*
@RunWith(AndroidJUnit4::class)
class SileroVADIntegrationTest {
    private lateinit var context: Context
    private lateinit var vadProcessor: SileroVADProcessor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        vadProcessor = SileroVADProcessor(context, sampleRate = 16000, threshold = 0.5f)
    }

    @After
    fun teardown() {
        vadProcessor.release()
    }

    @Test
    fun testSilenceDetection() {
        val silence = SileroVADTest.generateSilence(512)
        val result = vadProcessor.process(silence)

        assertFalse("Silence should not be detected as voice", result.isVoiceActive)
        assertTrue("Confidence should be low", result.confidence < 0.3f)
    }

    @Test
    fun testSpeechLikeDetection() {
        val speechLike = SileroVADTest.generateSpeechLike(16000, 32)
        val result = vadProcessor.process(speechLike)

        // Note: This may or may not detect as speech depending on the noise pattern
        // Actual speech would be more reliable
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun testStateReset() {
        // Process some audio
        vadProcessor.process(SileroVADTest.generateSpeechLike(16000, 32))

        // Reset state
        vadProcessor.resetStates()

        // Process silence
        val result = vadProcessor.process(SileroVADTest.generateSilence(512))

        assertFalse("After reset, silence should not be detected", result.isVoiceActive)
    }

    @Test
    fun testMultipleChunks() {
        // Process multiple chunks in sequence
        for (i in 0 until 10) {
            val audio = if (i % 2 == 0) {
                SileroVADTest.generateSpeechLike(16000, 32)
            } else {
                SileroVADTest.generateSilence(512)
            }

            val result = vadProcessor.process(audio)
            assertNotNull("Result should not be null for chunk $i", result)
        }
    }
}
*/
