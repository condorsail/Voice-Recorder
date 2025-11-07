# Silero VAD Integration

This directory contains the Silero VAD (Voice Activity Detection) integration for the Voice Recorder app.

## Overview

Silero VAD is a pre-trained enterprise-grade Voice Activity Detector that can distinguish speech from silence and background noise. This implementation uses ONNX Runtime to run the model efficiently on Android devices.

## Components

### SileroVADProcessor

Main class that handles VAD processing using the ONNX Runtime.

**Location:** `SileroVADProcessor.kt`

**Key Features:**
- Real-time voice activity detection
- Supports 8kHz and 16kHz sample rates
- Maintains internal state between audio chunks
- Configurable detection threshold
- Thread-safe operation

### Model File

**Location:** `/app/src/main/assets/silero_vad.onnx`

**Model Information:**
- Size: ~292 KB
- Version: Silero VAD v5+
- Source: [Silero VAD GitHub Repository](https://github.com/snakers4/silero-vad)
- License: MIT

## Usage

### Basic Usage

```kotlin
// Initialize the VAD processor
val vadProcessor = SileroVADProcessor(
    context = context,
    sampleRate = 16000,  // 16kHz or 8kHz
    threshold = 0.5f      // Detection threshold (0.0 - 1.0)
)

// Process audio samples (PCM16 format)
val audioSamples: ShortArray = ... // Your audio data
val result = vadProcessor.process(audioSamples)

if (result.isVoiceActive) {
    // Voice detected!
    Log.d("VAD", "Voice confidence: ${result.confidence}")
} else {
    // Silence or background noise
}

// Reset state when starting new recording
vadProcessor.resetStates()

// Clean up when done
vadProcessor.release()
```

### Integration with RecorderService

To integrate VAD with the recording service, you can:

1. Initialize VAD processor when starting recording
2. Process audio chunks in real-time
3. Post VAD events via EventBus
4. Use VAD results for features like:
   - Auto-pause on silence
   - Smart segmentation
   - Noise filtering
   - Recording quality indicators

Example integration:

```kotlin
class RecorderService : Service() {
    private var vadProcessor: SileroVADProcessor? = null

    private fun startRecording() {
        // Initialize VAD if enabled
        if (config.enableVAD) {
            val vadSampleRate = if (config.samplingRate >= 16000) 16000 else 8000
            vadProcessor = SileroVADProcessor(
                context = this,
                sampleRate = vadSampleRate,
                threshold = config.vadThreshold
            )
        }
    }

    private fun processAudioChunk(audioData: ShortArray) {
        vadProcessor?.let { vad ->
            val result = vad.process(audioData)
            EventBus.getDefault().post(
                Events.VoiceActivityDetected(
                    result.isVoiceActive,
                    result.confidence
                )
            )
        }
    }

    private fun stopRecording() {
        vadProcessor?.release()
        vadProcessor = null
    }
}
```

## Configuration

VAD settings are stored in SharedPreferences via `Config.kt`:

- **`enableVAD`**: Boolean flag to enable/disable VAD (default: false)
- **`vadThreshold`**: Float value for detection threshold (default: 0.5, range: 0.0-1.0)

Access configuration:

```kotlin
val config = Config.newInstance(context)

// Enable VAD
config.enableVAD = true

// Adjust threshold
config.vadThreshold = 0.6f  // Higher = more strict (less false positives)
```

## Technical Details

### Audio Format Requirements

- **Input Format**: PCM16 (16-bit signed integer)
- **Sample Rates**: 8000 Hz or 16000 Hz only
- **Chunk Sizes**:
  - 8kHz: 256 samples (32ms)
  - 16kHz: 512 samples (32ms)

### Model Architecture

Silero VAD uses an LSTM-based architecture with internal state:
- **h tensor**: Hidden state (2 × batch × 128)
- **c tensor**: Cell state (2 × batch × 128)

These states are automatically managed by `SileroVADProcessor` and persist between audio chunks for continuity.

### Performance

- Processing time: < 1ms per chunk on modern mobile CPUs
- Memory footprint: ~2-3 MB (model + runtime)
- Accuracy: Trained on 6000+ languages

### Sample Rate Conversion

If your recording sample rate doesn't match VAD requirements (8kHz or 16kHz):

- **For 48kHz → 16kHz**: Downsample by factor of 3
- **For 44.1kHz → 16kHz**: Use resampling library
- **For 8kHz-16kHz**: Use native rate directly

The current implementation processes audio at 16kHz when recording sample rate is ≥ 16kHz, otherwise uses 8kHz.

## Events

VAD detection results are published via EventBus:

```kotlin
class VoiceActivityDetected(
    val isVoiceActive: Boolean,  // True if voice detected
    val confidence: Float         // Confidence score (0.0-1.0)
)
```

Subscribe to events in your Activity/Fragment:

```kotlin
@Subscribe(threadMode = ThreadMode.MAIN)
fun onVoiceActivityDetected(event: Events.VoiceActivityDetected) {
    if (event.isVoiceActive) {
        // Update UI to show voice activity
        confidenceText.text = "Voice: ${(event.confidence * 100).toInt()}%"
    }
}
```

## Future Enhancements

Potential improvements for 24/7 recording scenarios:

1. **Auto-pause on silence**: Automatically pause recording after N seconds of silence
2. **Smart segmentation**: Split recordings at silence boundaries
3. **Noise gate**: Filter out background noise below threshold
4. **Statistics**: Track voice activity percentage over time
5. **Battery optimization**: Reduce processing during long silence periods
6. **Adaptive threshold**: Auto-adjust based on environment

## Troubleshooting

### VAD not working

1. Check that ONNX Runtime dependency is included in `build.gradle.kts`
2. Verify model file exists in `/assets/silero_vad.onnx`
3. Ensure sample rate is 8000 or 16000 Hz
4. Check that audio data is in PCM16 format

### High false positive rate

- Increase `vadThreshold` (try 0.6-0.8)
- Check for microphone noise/interference
- Verify audio normalization is correct

### High false negative rate

- Decrease `vadThreshold` (try 0.3-0.4)
- Check audio gain/volume levels
- Ensure proper sample rate conversion

## Dependencies

- **ONNX Runtime Android**: 1.23.2
  - Maven: `com.microsoft.onnxruntime:onnxruntime-android:1.23.2`
  - License: MIT

## References

- [Silero VAD GitHub](https://github.com/snakers4/silero-vad)
- [ONNX Runtime Android](https://onnxruntime.ai/docs/install/)
- [EventBus Documentation](https://greenrobot.org/eventbus/documentation/)

## License

The Silero VAD model and this integration code follow the MIT License.
