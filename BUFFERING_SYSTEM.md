# Multi-Tier Audio Buffering System

## Overview

This document describes the multi-tier buffering system implemented for 24/7 continuous audio recording with crash recovery, processing, and cloud archival.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  TIER 0: Immediate Buffer (Crash Recovery)                       │
│  • Location: .buffers/temp/                                       │
│  • Format: .tmp files with periodic flush (5s default)           │
│  • Purpose: Protect against data loss during crashes              │
│  • Storage: ~10 MB rolling                                        │
└──────────────────────────────────────────────────────────────────┘
                              ↓ (Hourly rotation)
┌──────────────────────────────────────────────────────────────────┐
│  TIER 1: Raw Audio Ring Buffer (0-24 hours)                      │
│  • Location: .buffers/raw/                                        │
│  • Format: PCM16 @ 16kHz mono (Whisper-compatible)               │
│  • Segments: 1 hour each × 24 = 24 files                         │
│  • Storage: ~2.7 GB total                                         │
│  • Purpose: High-quality reprocessing, VAD, Whisper               │
└──────────────────────────────────────────────────────────────────┘
                    ↓ (Background processing: VAD + Whisper)
┌──────────────────────────────────────────────────────────────────┐
│  TIER 2: Processed Archive (24-72 hours) - TODO                  │
│  • Format: Opus @ 32kbps + JSON metadata                         │
│  • Storage: ~690 MB total                                         │
│  • Metadata: VAD timestamps, Whisper transcriptions              │
└──────────────────────────────────────────────────────────────────┘
                    ↓ (When: >72 hours old)
┌──────────────────────────────────────────────────────────────────┐
│  TIER 3: Cloud Archive (72+ hours) - TODO                        │
│  • Upload to: User's cloud (Drive/Dropbox/WebDAV)                │
│  • Local action: Delete after successful upload                   │
└──────────────────────────────────────────────────────────────────┘
```

## Implementation Status

### ✅ Completed (Phase 1)

#### 1. Core Data Structures
- `RecordingState` - Persistent state for crash recovery
- `RawAudioSegment` - Represents raw audio buffer segments
- `ProcessedSegment` - Processed audio with transcriptions (structure ready)
- `BufferIndex` - Index for managing all buffer segments

**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/buffers/RecordingState.kt`

#### 2. Tier 0: Immediate Crash Recovery Buffer
**Class:** `ImmediateBuffer`
**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/buffers/ImmediateBuffer.kt`

**Features:**
- Writes audio to temporary `.tmp` files
- Periodic flush to disk (configurable, default 5 seconds)
- Persistent state saved to SharedPreferences
- Automatic recovery on app restart
- Atomic rename on successful completion
- Cleanup of orphaned temp files

**Key Methods:**
```kotlin
fun init(format: String, sampleRate: Int, bitrate: Int): String
fun write(data: ByteArray, offset: Int, length: Int)
fun flush()
fun finalize(finalPath: String): Boolean
fun cancel()
companion object fun getActiveState(context: Context): RecordingState?
```

#### 3. Tier 1: Raw Audio Ring Buffer (24 hours)
**Class:** `RawAudioRingBuffer`
**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/buffers/RawAudioRingBuffer.kt`

**Features:**
- Rolling 24-hour buffer of raw PCM16 audio @ 16kHz mono
- Automatic segment rotation (1-hour segments)
- Ring buffer management (auto-delete old segments)
- Crash-resistant (independent segments)
- JSON metadata for each segment
- Whisper-compatible format

**Key Methods:**
```kotlin
fun startNewSegment(): Boolean
fun write(data: ShortArray, count: Int)
fun closeCurrentSegment()
fun getSegments(): List<RawAudioSegment>
fun getSegmentsInRange(startMs: Long, endMs: Long): List<RawAudioSegment>
fun getTotalSize(): Long
fun getTotalDuration(): Long
```

**Storage Format:**
- Audio: `raw_YYYYMMDD_HHmmss.pcm`
- Metadata: `raw_YYYYMMDD_HHmmss.json`
- Index: `raw_buffer_index.json`

#### 4. Configuration System
**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/helpers/Config.kt`

**New Configuration Options:**
```kotlin
// Tier 0
var bufferFlushInterval: Long (default: 5000ms)
var crashRecoveryEnabled: Boolean (default: true)

// Tier 1
var rawBufferEnabled: Boolean (default: true)
var rawBufferRetentionMs: Long (default: 24 hours)
var rawBufferSegmentDurationMs: Long (default: 1 hour)

// Tier 2 (ready for implementation)
var processedBufferEnabled: Boolean (default: true)
var processedBufferRetentionMs: Long (default: 48 hours)
var processedBufferSegmentDurationMs: Long (default: 6 hours)
var processedBufferFormat: String (default: "opus")
var processedBufferBitrate: Int (default: 32000)
var autoProcessRawBuffer: Boolean (default: true)
var deleteRawAfterProcessing: Boolean (default: false)

// Tier 3 (ready for implementation)
var cloudUploadEnabled: Boolean (default: false)
var cloudUploadAgeMs: Long (default: 72 hours)
var cloudProvider: String (default: "none")

// General
var continuousRecordingMode: Boolean (default: false)
```

#### 5. Integration with Mp3Recorder
**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/recorder/Mp3Recorder.kt`

**Changes:**
- Integrated `ImmediateBuffer` for crash recovery
- Integrated `RawAudioRingBuffer` for 24-hour raw buffer
- Modified `prepare()` to initialize buffer system
- Modified `start()` to write to temp file
- Modified recording loop to write to both buffers:
  - Raw PCM16 → `RawAudioRingBuffer`
  - Encoded MP3 → `ImmediateBuffer`
- Modified `release()` to finalize buffers properly

**Data Flow:**
```
AudioRecord (PCM16)
    ├─→ RawAudioRingBuffer.write(rawData)  // Tier 1
    └─→ Encode to MP3
        └─→ ImmediateBuffer.write(mp3Data)  // Tier 0
            └─→ On success: ImmediateBuffer.finalize(finalPath)
```

#### 6. Crash Recovery in RecorderService
**Location:** `app/src/main/kotlin/org/fossify/voicerecorder/services/RecorderService.kt`

**Changes:**
- Added `onCreate()` method to check for crashed recordings
- Added `recoverCrashedRecording()` method
- Automatic recovery of interrupted recordings
- Cleanup of orphaned temp files
- Recovery files prefixed with `recovered_`

**Recovery Logic:**
1. On service start, check for `RecordingState` in SharedPreferences
2. If found and valid (> 32KB), attempt recovery
3. Move temp file to final location with `recovered_` prefix
4. Scan file with MediaScanner
5. Clear recovery state
6. Continue normal operation

#### 7. Dependencies
**Added:** `kotlinx-serialization-json` for JSON serialization
**Location:** `gradle/libs.versions.toml` and `app/build.gradle.kts`

---

## Next Steps (Phase 2 & 3)

### TODO: Tier 2 - Processed Audio Buffer

**Planned Features:**
- Background worker for processing Tier 1 segments
- VAD integration (using existing `SileroVADProcessor`)
- Whisper transcription (using existing `WhisperProcessor`)
- Opus compression (32 kbps) for storage efficiency
- Metadata JSON with:
  - VAD segments (speech timestamps)
  - Transcriptions with timestamps
  - Source raw segments
- Ring buffer with 48-hour retention
- 6-hour segments

**Estimated Implementation:**
```kotlin
class ProcessedAudioRingBuffer(context: Context) {
    fun processRawSegment(rawSegment: RawAudioSegment)
    fun compressToOpus(pcmFile: File, opusFile: File, bitrate: Int)
    fun extractVADSegments(rawSegment: RawAudioSegment): List<VADSegment>
    fun transcribeSegments(rawSegment: RawAudioSegment, vadSegments: List<VADSegment>): List<TranscriptionSegment>
    fun saveProcessedSegment(segment: ProcessedSegment)
}
```

**Background Processing Service:**
```kotlin
class BufferProcessingService : Service() {
    // Process when:
    // - Device is charging
    // - Device is idle
    // - OR segment is > 24 hours old (forced processing)

    fun processOldestUnprocessedSegment()
    fun scheduleProcessing()
}
```

### TODO: Tier 3 - Cloud Upload

**Planned Features:**
- Support for multiple cloud providers:
  - Google Drive
  - Dropbox
  - WebDAV
  - Custom S3-compatible storage
- Upload segments older than 72 hours
- Retry logic with exponential backoff
- Offline queueing
- Delete local files after successful upload
- Upload verification

**Estimated Implementation:**
```kotlin
interface CloudProvider {
    suspend fun upload(file: File, remotePath: String): Boolean
    suspend fun verify(remotePath: String, expectedSize: Long): Boolean
    fun isAvailable(): Boolean
}

class CloudUploadService : Service() {
    fun uploadSegment(segment: ProcessedSegment)
    fun queueForUpload(segment: ProcessedSegment)
    fun processUploadQueue()
}
```

### TODO: 24/7 Continuous Recording Mode

**Planned Features:**
- Auto-start recording on app launch
- Auto-start recording on device boot
- Foreground service with permanent notification
- Wake locks for reliability
- Battery optimization handling
- Auto-restart on crash with `START_STICKY`

**Integration:**
```kotlin
// In RecorderService.onCreate()
if (config.continuousRecordingMode) {
    startRecording()
}

// In RecorderService.onDestroy()
if (config.continuousRecordingMode) {
    // Schedule restart via AlarmManager
    scheduleServiceRestart()
}
```

---

## Storage Requirements

### Current Implementation (Tier 0 + Tier 1)

| Tier | Duration | Format | Storage |
|------|----------|--------|---------|
| Tier 0 | Current recording | MP3/M4A/OGG | ~10 MB |
| Tier 1 | 24 hours | PCM16 @ 16kHz | ~2.7 GB |
| **Total** | | | **~2.7 GB** |

### Full System (All Tiers)

| Tier | Duration | Format | Storage |
|------|----------|--------|---------|
| Tier 0 | Current recording | MP3/M4A/OGG | ~10 MB |
| Tier 1 | 24 hours | PCM16 @ 16kHz | ~2.7 GB |
| Tier 2 | 48 hours | Opus @ 32kbps | ~690 MB |
| Tier 3 | Unlimited | Cloud | 0 (local) |
| **Total** | 72 hours local | | **~3.4 GB** |

### Optimization Options

1. **Reduce Tier 1 to 12 hours:** Saves ~1.35 GB
2. **Reduce Tier 2 bitrate to 24kbps:** Saves ~170 MB
3. **Reduce Tier 2 retention to 24 hours:** Saves ~345 MB
4. **Disable Tier 1 after processing:** Saves ~2.7 GB (not recommended)

---

## File Structure

```
{Recordings Folder}/
├── .buffers/                          # Hidden from user (unless show hidden files)
│   ├── temp/                          # Tier 0: Immediate buffer
│   │   ├── recording_1699123456789.tmp
│   │   └── ...
│   ├── raw/                           # Tier 1: Raw audio buffer
│   │   ├── raw_20241108_100000.pcm
│   │   ├── raw_20241108_100000.json
│   │   ├── raw_20241108_110000.pcm
│   │   ├── raw_20241108_110000.json
│   │   ├── ...
│   │   └── raw_buffer_index.json
│   └── processed/                     # Tier 2: Processed buffer (TODO)
│       ├── processed_20241108_100000.opus
│       ├── processed_20241108_100000.json
│       ├── ...
│       └── processed_buffer_index.json
└── recording_20241108_101530.mp3      # Final user recordings
```

---

## API Reference

### ImmediateBuffer

```kotlin
class ImmediateBuffer(context: Context) {
    // Initialize new recording session
    fun init(format: String, sampleRate: Int, bitrate: Int): String

    // Write audio data
    fun write(data: ByteArray, offset: Int, length: Int)
    fun write(data: ByteArray)

    // Flush to disk
    fun flush()

    // Finalize recording (atomic rename)
    fun finalize(finalPath: String): Boolean

    // Cancel recording
    fun cancel()

    // Cleanup resources
    fun cleanup()

    // Get statistics
    fun getBytesWritten(): Long
    fun getDurationSeconds(): Int

    companion object {
        // Get crashed recording state
        fun getActiveState(context: Context): RecordingState?

        // Clear recovery state
        fun clearActiveState(context: Context)

        // Get all temp files
        fun getAllTempFiles(context: Context): List<File>

        // Cleanup orphaned files
        fun cleanupOrphanedTempFiles(context: Context)
    }
}
```

### RawAudioRingBuffer

```kotlin
class RawAudioRingBuffer(context: Context) {
    // Start new segment
    fun startNewSegment(): Boolean

    // Write raw PCM16 data
    fun write(data: ShortArray, count: Int)

    // Flush current segment
    fun flush()

    // Close current segment
    fun closeCurrentSegment()

    // Get segments
    fun getSegments(): List<RawAudioSegment>
    fun getSegmentsInRange(startMs: Long, endMs: Long): List<RawAudioSegment>

    // Get statistics
    fun getTotalSize(): Long
    fun getTotalDuration(): Long

    // Cleanup
    fun cleanup()
    fun deleteAll()

    companion object {
        fun getBufferDirectory(context: Context): File
        fun isEnabled(context: Context): Boolean
        fun estimateSize(durationMs: Long): Long
    }
}
```

---

## Configuration Examples

### Enable Full Buffering System
```kotlin
config.crashRecoveryEnabled = true        // Tier 0
config.rawBufferEnabled = true            // Tier 1
config.processedBufferEnabled = true      // Tier 2 (when implemented)
config.cloudUploadEnabled = true          // Tier 3 (when implemented)
```

### Minimal Crash Recovery Only
```kotlin
config.crashRecoveryEnabled = true
config.rawBufferEnabled = false
config.processedBufferEnabled = false
config.cloudUploadEnabled = false
```

### 24/7 Continuous Recording
```kotlin
config.continuousRecordingMode = true
config.crashRecoveryEnabled = true
config.rawBufferEnabled = true
config.autoProcessRawBuffer = true
config.cloudUploadEnabled = true
config.cloudProvider = "google_drive"
```

### Custom Buffer Durations
```kotlin
// 12-hour raw buffer instead of 24
config.rawBufferRetentionMs = 12 * 60 * 60 * 1000L

// 30-minute segments instead of 1 hour
config.rawBufferSegmentDurationMs = 30 * 60 * 1000L

// 24-hour processed buffer instead of 48
config.processedBufferRetentionMs = 24 * 60 * 60 * 1000L

// 3-hour processed segments instead of 6
config.processedBufferSegmentDurationMs = 3 * 60 * 60 * 1000L

// Upload after 48 hours instead of 72
config.cloudUploadAgeMs = 48 * 60 * 60 * 1000L
```

---

## Testing

### Test Crash Recovery

1. Start a recording
2. Record for at least 10 seconds
3. Force kill the app: `adb shell am force-stop org.fossify.voicerecorder`
4. Restart the app
5. Check recordings folder for `recovered_*.mp3` file

### Test Raw Buffer

1. Enable raw buffer: `config.rawBufferEnabled = true`
2. Record for 5 minutes
3. Check `.buffers/raw/` directory for PCM files
4. Verify JSON metadata exists
5. Record for another 60 minutes (trigger rotation)
6. Verify new segment created

### Verify Buffer Cleanup

1. Record for 25 hours continuously
2. Verify oldest segments are deleted
3. Check total size ≈ 2.7 GB (not growing indefinitely)

---

## Performance Considerations

### CPU Impact
- **Tier 0:** Low (just writing MP3 data + periodic flush)
- **Tier 1:** Low (writing raw PCM, no encoding)
- **Tier 2:** Medium-High (VAD + Whisper + Opus encoding)
- **Tier 3:** Low (network I/O, async)

### Battery Impact
- Continuous recording: ~5-10% per hour (varies by device)
- With VAD/Whisper processing: +2-5% per hour
- Optimize by processing only when charging

### Disk I/O
- Tier 1 write rate: 32 KB/sec (sustainable on all devices)
- Periodic flush: every 5 seconds (configurable)
- Segment rotation: every 1 hour (atomic rename, fast)

---

## Known Limitations

1. **Network required for Gradle builds** - Initial setup requires internet
2. **MediaRecorderWrapper integration incomplete** - Only Mp3Recorder has buffering
3. **No UI for buffer management** - Configuration via code only
4. **No Tier 2 implementation yet** - Opus encoding pending
5. **No Tier 3 implementation yet** - Cloud upload pending
6. **No background processing service** - Manual processing required

---

## Future Enhancements

1. **Settings UI** - User-friendly configuration screen
2. **Buffer viewer** - Browse and playback buffer segments
3. **Transcription search** - Full-text search across all transcriptions
4. **Smart processing** - Process only segments with speech (VAD-guided)
5. **Adaptive bitrate** - Adjust Opus bitrate based on content complexity
6. **Multiple cloud providers** - Simultaneous backup to multiple clouds
7. **Encryption** - End-to-end encryption for cloud uploads
8. **Compression options** - Additional codecs (FLAC, AAC, Vorbis)
9. **Buffer statistics** - Dashboard showing storage usage, processing status
10. **Export tools** - Batch export of buffer segments

---

## License

This implementation follows the same license as the Voice Recorder project.

## Contributors

- Implementation: Claude (Anthropic)
- Architecture design: Based on requirements for 24/7 continuous recording with crash recovery

---

**Last Updated:** 2025-11-08
**Version:** 1.0.0 (Phase 1 Complete)
