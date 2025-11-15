package org.fossify.voicerecorder.models

import android.net.Uri

class Events {
    class RecordingDuration internal constructor(val duration: Int)
    class RecordingStatus internal constructor(val status: Int)
    class RecordingAmplitude internal constructor(val amplitude: Int)
    class RecordingCompleted internal constructor()
    class RecordingTrashUpdated internal constructor()
    class RecordingSaved internal constructor(val uri: Uri?)
    class VoiceActivityDetected internal constructor(val isVoiceActive: Boolean, val confidence: Float)
    class TranscriptionResult internal constructor(val text: String, val isFinal: Boolean, val language: String)
    class TranscriptionError internal constructor(val error: String)

    // New transcription management events
    class TranscriptionSegmentReady internal constructor(val recordingId: Int, val segment: TranscriptionSegment)
    class TranscriptionProgress internal constructor(val recordingId: Int, val progress: Float, val currentSegment: Int, val totalSegments: Int)
    class TranscriptionStarted internal constructor(val recordingId: Int, val isLive: Boolean)
    class TranscriptionCompleted internal constructor(val recordingId: Int, val transcription: Transcription)
    class TranscriptionFailed internal constructor(val recordingId: Int, val error: String)
    class LiveTranscriptionStateChanged internal constructor(val isActive: Boolean)
}
