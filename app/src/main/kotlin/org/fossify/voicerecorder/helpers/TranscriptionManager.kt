package org.fossify.voicerecorder.helpers

import android.content.Context
import kotlinx.coroutines.*
import org.fossify.voicerecorder.buffers.RawAudioRingBuffer
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.models.Events
import org.fossify.voicerecorder.models.Transcription
import org.fossify.voicerecorder.models.TranscriptionSegment
import org.fossify.voicerecorder.models.VoiceSegment
import org.fossify.voicerecorder.recorder.WhisperProcessor
import org.greenrobot.eventbus.EventBus
import java.io.File
import kotlin.math.min

/**
 * Manager for handling audio transcription using Whisper
 * Supports both live transcription (from ring buffer) and archive transcription (from files)
 */
class TranscriptionManager(private val context: Context) {
    private val config = context.config
    private val storage = TranscriptionStorage(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var whisperProcessor: WhisperProcessor? = null
    private var vadFilter: VADAudioFilter? = null
    private var liveTranscriptionJob: Job? = null
    private var isLiveTranscribing = false

    companion object {
        private const val WHISPER_CHUNK_SIZE_SECONDS = 30
        private const val WHISPER_SAMPLE_RATE = 16000
        private const val SAMPLES_PER_CHUNK = WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE_SECONDS

        @Volatile
        private var instance: TranscriptionManager? = null

        fun getInstance(context: Context): TranscriptionManager {
            return instance ?: synchronized(this) {
                instance ?: TranscriptionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Start live transcription of active recording
     * Reads from the raw audio ring buffer and transcribes in real-time
     *
     * @param recordingId The ID of the recording being transcribed
     */
    fun startLiveTranscription(recordingId: Int) {
        if (!config.enableWhisper || !config.enableLiveTranscription) {
            return
        }

        if (isLiveTranscribing) {
            stopLiveTranscription()
        }

        liveTranscriptionJob = scope.launch {
            try {
                isLiveTranscribing = true
                EventBus.getDefault().post(Events.LiveTranscriptionStateChanged(true))
                EventBus.getDefault().post(Events.TranscriptionStarted(recordingId, isLive = true))

                initializeProcessors()

                val ringBuffer = RawAudioRingBuffer(context)
                val lookbackMs = config.liveTranscriptionLookbackMinutes * 60 * 1000L
                val startTime = System.currentTimeMillis() - lookbackMs

                // Get segments from the lookback period
                val segments = ringBuffer.getSegmentsInRange(startTime, System.currentTimeMillis())

                if (segments.isEmpty()) {
                    EventBus.getDefault().post(
                        Events.TranscriptionFailed(recordingId, "No audio data available")
                    )
                    return@launch
                }

                // Process historical segments first
                val allSegments = mutableListOf<TranscriptionSegment>()
                var processedDuration = 0L

                for (segment in segments) {
                    if (!isActive || !isLiveTranscribing) break

                    val audioData = VADAudioFilter.readPCM16FromFile(segment.filePath)
                    if (audioData.isEmpty()) continue

                    val transcribedSegments = transcribeAudioChunk(
                        audioData,
                        segment.startTimestamp
                    )

                    allSegments.addAll(transcribedSegments)
                    transcribedSegments.forEach { seg ->
                        EventBus.getDefault().post(Events.TranscriptionSegmentReady(recordingId, seg))
                    }

                    processedDuration += segment.durationMs
                }

                // Continue processing new segments as they arrive
                while (isActive && isLiveTranscribing) {
                    delay(5000) // Check for new data every 5 seconds

                    val newSegments = ringBuffer.getSegmentsInRange(
                        System.currentTimeMillis() - 10000, // Last 10 seconds
                        System.currentTimeMillis()
                    )

                    for (segment in newSegments) {
                        if (!isActive || !isLiveTranscribing) break

                        val audioData = VADAudioFilter.readPCM16FromFile(segment.filePath)
                        if (audioData.isEmpty()) continue

                        val transcribedSegments = transcribeAudioChunk(
                            audioData,
                            segment.startTimestamp
                        )

                        allSegments.addAll(transcribedSegments)
                        transcribedSegments.forEach { seg ->
                            EventBus.getDefault().post(Events.TranscriptionSegmentReady(recordingId, seg))
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                EventBus.getDefault().post(
                    Events.TranscriptionFailed(recordingId, e.message ?: "Unknown error")
                )
            } finally {
                isLiveTranscribing = false
                EventBus.getDefault().post(Events.LiveTranscriptionStateChanged(false))
                releaseProcessors()
            }
        }
    }

    /**
     * Stop live transcription
     */
    fun stopLiveTranscription() {
        liveTranscriptionJob?.cancel()
        liveTranscriptionJob = null
        isLiveTranscribing = false
        EventBus.getDefault().post(Events.LiveTranscriptionStateChanged(false))
    }

    /**
     * Transcribe a complete recording file
     * This is used when a recording is stopped and needs full transcription
     *
     * @param recordingId The ID of the recording
     * @param recordingPath Path to the recording file
     * @param duration Duration of the recording in seconds
     */
    fun transcribeRecording(
        recordingId: Int,
        recordingPath: String,
        duration: Int
    ) {
        if (!config.enableWhisper || !config.transcribeOnStop) {
            return
        }

        scope.launch {
            try {
                EventBus.getDefault().post(Events.TranscriptionStarted(recordingId, isLive = false))

                val startTime = System.currentTimeMillis()
                initializeProcessors()

                // Check if we should use the raw buffer or convert the file
                val audioData = loadAudioData(recordingPath)

                if (audioData.isEmpty()) {
                    EventBus.getDefault().post(
                        Events.TranscriptionFailed(recordingId, "No audio data found")
                    )
                    return@launch
                }

                // Apply VAD filtering if enabled
                val segments = if (config.useVADForTranscription) {
                    filterVoiceSegments(audioData)
                } else {
                    listOf(
                        VoiceSegment(
                            startTime = 0L,
                            endTime = (audioData.size * 1000L) / WHISPER_SAMPLE_RATE,
                            confidence = 1.0f,
                            audioData = audioData
                        )
                    )
                }

                val totalSegments = (audioData.size + SAMPLES_PER_CHUNK - 1) / SAMPLES_PER_CHUNK
                val transcriptionSegments = mutableListOf<TranscriptionSegment>()
                var currentSegment = 0

                // Process each voice segment
                for (voiceSegment in segments) {
                    if (!isActive) break

                    val segmentAudio = voiceSegment.audioData ?: continue
                    val segmentStartTime = voiceSegment.startTime

                    // Split into chunks for Whisper
                    var offset = 0
                    while (offset < segmentAudio.size && isActive) {
                        val chunkSize = min(SAMPLES_PER_CHUNK, segmentAudio.size - offset)
                        val chunk = segmentAudio.copyOfRange(offset, offset + chunkSize)

                        val chunkStartTime = segmentStartTime + (offset * 1000L) / WHISPER_SAMPLE_RATE
                        val transcribed = transcribeAudioChunk(chunk, chunkStartTime)

                        transcriptionSegments.addAll(transcribed)

                        // Post progress
                        currentSegment++
                        val progress = currentSegment.toFloat() / totalSegments.toFloat()
                        EventBus.getDefault().post(
                            Events.TranscriptionProgress(
                                recordingId,
                                progress,
                                currentSegment,
                                totalSegments
                            )
                        )

                        offset += chunkSize
                    }
                }

                if (!isActive) {
                    EventBus.getDefault().post(
                        Events.TranscriptionFailed(recordingId, "Transcription cancelled")
                    )
                    return@launch
                }

                // Create and save transcription
                val processingTime = System.currentTimeMillis() - startTime
                val transcription = Transcription(
                    recordingId = recordingId,
                    recordingPath = recordingPath,
                    segments = transcriptionSegments,
                    language = config.whisperLanguage ?: "auto",
                    duration = duration,
                    createdAt = System.currentTimeMillis(),
                    isComplete = true,
                    processingTimeMs = processingTime
                )

                storage.saveTranscription(transcription)

                EventBus.getDefault().post(Events.TranscriptionCompleted(recordingId, transcription))

            } catch (e: Exception) {
                e.printStackTrace()
                EventBus.getDefault().post(
                    Events.TranscriptionFailed(recordingId, e.message ?: "Unknown error")
                )
            } finally {
                releaseProcessors()
            }
        }
    }

    /**
     * Get transcription for a recording
     */
    fun getTranscription(recordingId: Int): Transcription? {
        return storage.loadTranscription(recordingId)
    }

    /**
     * Check if transcription exists for a recording
     */
    fun hasTranscription(recordingId: Int): Boolean {
        return storage.hasTranscription(recordingId)
    }

    /**
     * Delete transcription for a recording
     */
    fun deleteTranscription(recordingId: Int): Boolean {
        return storage.deleteTranscription(recordingId)
    }

    /**
     * Search transcriptions by text
     */
    fun searchTranscriptions(query: String): List<Pair<Transcription, List<TranscriptionSegment>>> {
        return storage.searchTranscriptions(query)
    }

    /**
     * Check if live transcription is active
     */
    fun isLiveTranscriptionActive(): Boolean {
        return isLiveTranscribing
    }

    /**
     * Release all resources
     */
    fun release() {
        stopLiveTranscription()
        scope.cancel()
        releaseProcessors()
    }

    // Private helper methods

    private fun initializeProcessors() {
        if (whisperProcessor == null) {
            whisperProcessor = WhisperProcessor(
                context = context,
                modelName = config.whisperModel,
                language = config.whisperLanguage,
                translate = config.whisperTranslate
            )
        }

        if (vadFilter == null && config.useVADForTranscription) {
            vadFilter = VADAudioFilter(
                context = context,
                sampleRate = WHISPER_SAMPLE_RATE,
                threshold = config.vadSilenceThreshold,
                minSpeechDurationMs = config.minSpeechDurationMs
            )
        }
    }

    private fun releaseProcessors() {
        whisperProcessor?.release()
        whisperProcessor = null
        vadFilter?.release()
        vadFilter = null
    }

    private fun transcribeAudioChunk(
        audioData: ShortArray,
        startTimeMs: Long
    ): List<TranscriptionSegment> {
        val processor = whisperProcessor ?: return emptyList()

        val result = processor.transcribe(audioData, WHISPER_SAMPLE_RATE)

        if (!result.isSuccess || result.text.trim().isEmpty()) {
            return emptyList()
        }

        // Convert WhisperProcessor segments to TranscriptionSegment with absolute timestamps
        return result.segments.map { segment ->
            TranscriptionSegment(
                text = segment.text.trim(),
                startTime = startTimeMs + segment.startTime,
                endTime = startTimeMs + segment.endTime,
                confidence = 1.0f, // Whisper doesn't provide confidence
                language = result.language
            )
        }.filter { it.text.isNotEmpty() }
    }

    private fun filterVoiceSegments(audioData: ShortArray): List<VoiceSegment> {
        val filter = vadFilter ?: return listOf(
            VoiceSegment(
                startTime = 0L,
                endTime = (audioData.size * 1000L) / WHISPER_SAMPLE_RATE,
                confidence = 1.0f,
                audioData = audioData
            )
        )

        filter.reset()
        filter.processChunk(audioData)
        val segments = filter.finalizeSegments()

        return if (segments.isEmpty()) {
            // If VAD found no voice, return the whole audio anyway
            listOf(
                VoiceSegment(
                    startTime = 0L,
                    endTime = (audioData.size * 1000L) / WHISPER_SAMPLE_RATE,
                    confidence = 1.0f,
                    audioData = audioData
                )
            )
        } else {
            segments
        }
    }

    private fun loadAudioData(recordingPath: String): ShortArray {
        // First check if we have it in the raw buffer
        val file = File(recordingPath)
        if (!file.exists()) {
            return ShortArray(0)
        }

        // For now, we'll try to read directly from the raw buffer if available
        // In a full implementation, you'd convert MP3/M4A/OGG to PCM16
        val rawBufferDir = RawAudioRingBuffer.getBufferDirectory(context)

        // Try to find corresponding raw buffer file based on timestamp
        // This is a simplified approach - in production you'd maintain better metadata
        val recordingName = file.nameWithoutExtension
        val possibleRawFiles = rawBufferDir.listFiles { f ->
            f.name.endsWith(".pcm") && f.name.contains(recordingName.substringAfter("recording_").take(8))
        }

        if (!possibleRawFiles.isNullOrEmpty()) {
            return VADAudioFilter.readPCM16FromFile(possibleRawFiles.first().absolutePath)
        }

        // TODO: If raw buffer not available, decode the MP3/M4A/OGG file to PCM16
        // This would require adding an audio decoder library
        // For now, return empty array
        return ShortArray(0)
    }
}
