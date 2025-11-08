# Whisper Speech-to-Text Integration

This directory contains the Whisper speech-to-text transcription integration for the Voice Recorder app.

## Overview

Whisper is OpenAI's state-of-the-art speech recognition model that can transcribe and translate audio in 99 languages. This implementation uses whisper-jni (a JNI wrapper for whisper.cpp) to run Whisper models efficiently on Android devices.

## Components

### WhisperProcessor

Main class that handles speech-to-text transcription using whisper-jni.

**Location:** `WhisperProcessor.kt`

**Key Features:**
- Real-time and offline transcription
- Supports 99 languages
- Translation to English
- Multiple model sizes (tiny, base, small, medium, large)
- Automatic sample rate conversion
- Segmented transcription with timestamps

### WhisperIntegrationHelper

Helper class for easy integration with the recording pipeline.

**Location:** `WhisperIntegrationHelper.kt`

**Key Features:**
- Audio buffering and batching
- Asynchronous processing
- EventBus integration
- Lifecycle management
- Configuration integration

### Model Files

**Location:** `/app/src/main/assets/`

**Required:** At least one Whisper model file (see `WHISPER_MODELS_README.md` for download instructions)

## Usage

### Basic Usage

```kotlin
// Initialize the Whisper processor
val whisperProcessor = WhisperProcessor(
    context = context,
    modelName = "ggml-tiny.en.bin",  // Model file in assets
    language = "en",                  // Optional: null for auto-detect
    translate = false,                // Set true to translate to English
    useGpu = false                    // GPU support (experimental)
)

// Transcribe audio samples (PCM16 format)
val audioSamples: ShortArray = ... // Your audio data
val result = whisperProcessor.transcribe(audioSamples, sampleRate = 16000)

if (result.isSuccess) {
    println("Transcription: ${result.text}")
    println("Language: ${result.language}")
    println("Processing time: ${result.processingTimeMs}ms")

    // Access individual segments with timestamps
    result.segments.forEach { segment ->
        println("${segment.startTime}-${segment.endTime}: ${segment.text}")
    }
} else {
    println("Error: ${result.error}")
}

// Clean up when done
whisperProcessor.release()
```

### Integration with RecorderService

To integrate Whisper with the recording service:

```kotlin
class RecorderService : Service() {
    private var whisperHelper: WhisperIntegrationHelper? = null

    private fun startRecording() {
        // Initialize Whisper if enabled
        if (config.enableWhisper) {
            whisperHelper = WhisperIntegrationHelper.fromConfig(this, config)
        }
    }

    private fun processAudioChunk(audioData: ShortArray) {
        // Process audio for transcription
        whisperHelper?.processAudioChunk(audioData)
        // Transcription results posted automatically via EventBus
    }

    private fun stopRecording() {
        // Finalize any remaining audio
        whisperHelper?.finalizeTranscription()
        whisperHelper?.release()
        whisperHelper = null
    }
}
```

### Receiving Transcription Results

Subscribe to transcription events in your Activity/Fragment:

```kotlin
@Subscribe(threadMode = ThreadMode.MAIN)
fun onTranscriptionResult(event: Events.TranscriptionResult) {
    transcriptionText.text = event.text
    languageLabel.text = event.language

    if (event.isFinal) {
        // Final transcription for this segment
        saveTranscription(event.text)
    }
}

@Subscribe(threadMode = ThreadMode.MAIN)
fun onTranscriptionError(event: Events.TranscriptionError) {
    Toast.makeText(this, "Transcription error: ${event.error}", Toast.LENGTH_SHORT).show()
}
```

## Configuration

Whisper settings are stored in SharedPreferences via `Config.kt`:

- **`enableWhisper`**: Boolean flag to enable/disable transcription (default: false)
- **`whisperModel`**: Model filename (default: "ggml-tiny.en.bin")
- **`whisperLanguage`**: Language code or null for auto-detect (default: null)
- **`whisperTranslate`**: Translate to English (default: false)

Access configuration:

```kotlin
val config = Config.newInstance(context)

// Enable Whisper
config.enableWhisper = true

// Set model
config.whisperModel = "ggml-base.en.bin"

// Set language (or null for auto-detect)
config.whisperLanguage = "en"

// Enable translation to English
config.whisperTranslate = false
```

## Model Management

### Checking Available Models

```kotlin
// Get list of available models in assets
val models = WhisperIntegrationHelper.getAvailableModels(context)
if (models.isEmpty()) {
    showDownloadModelsDialog()
}
```

### Model Information

```kotlin
val whisper = WhisperProcessor(context, "ggml-tiny.en.bin")
val info = whisper.getModelInfo()

println("Model: ${info.modelName}")
println("Multilingual: ${info.isMultilingual}")
println("Loaded: ${info.isLoaded}")
println("Language: ${info.language}")
```

## Technical Details

### Audio Format Requirements

- **Input Format**: PCM16 (16-bit signed integer) or Float32 (normalized to -1.0 to 1.0)
- **Sample Rate**: Any (automatically resampled to 16kHz internally)
- **Channels**: Mono (if stereo, convert to mono first)

### Processing Modes

1. **Real-time Mode**: Process audio chunks as they arrive
   - Buffer size: 30 seconds (configurable in `WhisperIntegrationHelper`)
   - Transcription runs asynchronously
   - Results posted via EventBus

2. **Batch Mode**: Transcribe entire recording after completion
   - Load audio file
   - Process in one go
   - More accurate for long-form content

### Sample Rate Conversion

Whisper requires 16kHz audio. The processor automatically resamples:
- **48kHz → 16kHz**: Downsampled by factor of 3
- **44.1kHz → 16kHz**: Resampled using linear interpolation
- **8kHz → 16kHz**: Upsampled (rare, not recommended)

The current implementation uses simple linear interpolation. For production use, consider a proper resampling library for better quality.

### Performance Characteristics

| Model | Size | Speed (CPU) | Accuracy | Recommended Use |
|-------|------|-------------|----------|-----------------|
| tiny.en | 75 MB | Very Fast (~32x real-time) | Good | Real-time, mobile |
| base.en | 142 MB | Fast (~16x real-time) | Better | Mobile, good balance |
| small.en | 466 MB | Moderate (~6x real-time) | Very Good | Post-processing |
| medium.en | 1.5 GB | Slow (~2x real-time) | Excellent | High accuracy needed |
| large-v3 | 2.9 GB | Very Slow (1x real-time) | Best | Server-side only |

**Note**: Speeds are approximate and depend on device CPU. For mobile devices, **tiny.en** or **base.en** are recommended.

### Memory Usage

- **Model Loading**: Model size + ~200 MB overhead
- **Runtime**: ~300-500 MB for processing buffers
- **Recommendations**:
  - Use `tiny.en` on devices with < 4GB RAM
  - Use `base.en` on devices with 4GB+ RAM
  - Avoid `small` and larger models on mobile

## Supported Languages

Multilingual models (`.bin` files without `.en` suffix) support 99 languages:

- Afrikaans (af), Arabic (ar), Armenian (hy), Azerbaijani (az), Belarusian (be)
- Bosnian (bs), Bulgarian (bg), Catalan (ca), Chinese (zh), Croatian (hr)
- Czech (cs), Danish (da), Dutch (nl), English (en), Estonian (et)
- Finnish (fi), French (fr), Galician (gl), German (de), Greek (el)
- Hebrew (he), Hindi (hi), Hungarian (hu), Icelandic (is), Indonesian (id)
- Italian (it), Japanese (ja), Kannada (kn), Kazakh (kk), Korean (ko)
- Latvian (lv), Lithuanian (lt), Macedonian (mk), Malay (ms), Marathi (mr)
- Maori (mi), Nepali (ne), Norwegian (no), Persian (fa), Polish (pl)
- Portuguese (pt), Romanian (ro), Russian (ru), Serbian (sr), Slovak (sk)
- Slovenian (sl), Spanish (es), Swahili (sw), Swedish (sv), Tagalog (tl)
- Tamil (ta), Thai (th), Turkish (tr), Ukrainian (uk), Urdu (ur)
- Vietnamese (vi), Welsh (cy)
- And 50+ more languages

**For English-only**: Use `.en` models for 2x faster and more accurate transcription.

## Events

Whisper transcription results are published via EventBus:

### TranscriptionResult

```kotlin
class TranscriptionResult(
    val text: String,        // Transcribed text
    val isFinal: Boolean,    // True if this is the final segment
    val language: String     // Detected or specified language
)
```

### TranscriptionError

```kotlin
class TranscriptionError(
    val error: String        // Error message
)
```

## Advanced Features

### Translation Mode

Translate any language to English:

```kotlin
val whisper = WhisperProcessor(
    context = context,
    modelName = "ggml-base.bin",  // Use multilingual model
    language = null,               // Auto-detect source language
    translate = true               // Translate to English
)

// French audio → English text
val result = whisper.transcribe(frenchAudioSamples)
println(result.text)  // Output in English
```

### Language Detection

Let Whisper automatically detect the language:

```kotlin
val whisper = WhisperProcessor(
    context = context,
    modelName = "ggml-base.bin",
    language = null  // Auto-detect
)

val result = whisper.transcribe(audioSamples)
println("Detected language: ${result.language}")
```

### Timestamp Segments

Access word-level or phrase-level timestamps:

```kotlin
val result = whisper.transcribe(audioSamples)

result.segments.forEach { segment ->
    val startSec = segment.startTime / 1000.0
    val endSec = segment.endTime / 1000.0
    println("[$startSec - $endSec]: ${segment.text}")
}
```

## Future Enhancements

Potential improvements:

1. **Live Captions**: Real-time on-screen transcription during recording
2. **Transcript Export**: Save transcriptions as .txt, .srt, .vtt files
3. **Search**: Full-text search across all recordings
4. **Speaker Diarization**: Identify different speakers
5. **Punctuation Restoration**: Add proper punctuation to transcripts
6. **Noise Reduction**: Combine with VAD for better accuracy
7. **Cloud Sync**: Backup transcriptions to cloud storage
8. **Batch Processing**: Transcribe multiple files in background

## Troubleshooting

### Model not found error

**Problem**: "Whisper model not found" error

**Solutions**:
- Download model from Hugging Face (see `WHISPER_MODELS_README.md`)
- Place `.bin` file in `app/src/main/assets/`
- Rebuild the app
- Check filename matches exactly (case-sensitive)

### Out of memory errors

**Problem**: App crashes with OutOfMemoryError

**Solutions**:
- Use smaller model (switch to `tiny.en`)
- Reduce buffer size in `WhisperIntegrationHelper`
- Close background apps
- Test on device with more RAM

### Slow transcription

**Problem**: Transcription is too slow for real-time use

**Solutions**:
- Use `tiny.en` instead of larger models
- Reduce buffer size for more frequent updates
- Use English-only models for English audio
- Consider GPU acceleration (experimental)

### Poor transcription quality

**Problem**: Incorrect or garbled transcription

**Solutions**:
- Use larger model (`base.en` instead of `tiny.en`)
- Improve audio quality (reduce background noise)
- Speak clearly and at moderate pace
- Ensure correct language is set
- Check microphone is working properly

### Language not detected correctly

**Problem**: Wrong language detected or used

**Solutions**:
- Manually specify language: `whisperLanguage = "en"`
- Use multilingual model (`.bin` not `.en.bin`)
- Ensure sufficient audio length (3+ seconds)
- Check audio quality

## Dependencies

| Dependency | Version | License | Purpose |
|------------|---------|---------|---------|
| whisper-jni | 1.7.1 | MIT | Whisper.cpp JNI bindings |
| EventBus | 3.3.1 | Apache 2.0 | Event communication |
| Kotlin Coroutines | (via Kotlin) | Apache 2.0 | Async processing |

## References

- [OpenAI Whisper](https://github.com/openai/whisper)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
- [whisper-jni](https://github.com/GiviMAD/whisper-jni)
- [Whisper Models on Hugging Face](https://huggingface.co/ggerganov/whisper.cpp)

## License

- **Whisper Model**: MIT License (OpenAI)
- **whisper.cpp**: MIT License
- **whisper-jni**: MIT License

This integration code follows the project's existing license.

## Best Practices

1. **Choose the right model**:
   - Mobile: `tiny.en` or `base.en`
   - Desktop: `small.en` or `medium.en`
   - Server: any model

2. **Enable only when needed**:
   ```kotlin
   config.enableWhisper = userWantsTranscription
   ```

3. **Handle errors gracefully**:
   ```kotlin
   if (!result.isSuccess) {
       showError(result.error)
   }
   ```

4. **Release resources**:
   ```kotlin
   override fun onDestroy() {
       whisperHelper?.release()
       super.onDestroy()
   }
   ```

5. **Inform users**:
   - Show transcription is in progress
   - Display language being used
   - Allow manual language selection
   - Provide option to disable

## Conclusion

The Whisper integration provides powerful speech-to-text capabilities for the Voice Recorder app. It's designed to be modular, efficient, and easy to integrate with the existing recording pipeline.

For questions or issues, refer to the troubleshooting section or consult the whisper-jni documentation.
