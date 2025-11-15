# Whisper ONNX Migration Status

## Summary

Successfully migrated from `whisper-jni` (which doesn't support Android) to **Sherpa-ONNX**, which uses ONNX Runtime for fast, optimized Whisper inference on Android.

## What's Been Done

### ✅ Dependencies Updated

**Removed:**
- `whisper-jni` (1.7.1) - Desktop-only library, incompatible with Android

**Added:**
- `sherpa-onnx` (6.25.12) - Android-native Whisper library using ONNX Runtime
- JNI packaging configuration for native library support

**Kept:**
- `onnxruntime-android` (1.23.2) - Already in your dependencies, powers Sherpa-ONNX
- `silero-vad` (via ONNX Runtime) - Already working, integrates with Sherpa-ONNX

### ✅ Build Configuration

Added to `app/build.gradle.kts`:
```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
}
```

### ✅ Version Catalog Cleaned

Removed whisper-jni references from `gradle/libs.versions.toml`

## What Needs to Be Done

### ✅ ~~Update WhisperProcessor.kt~~ COMPLETED

`WhisperProcessor.kt` has been rewritten to use Sherpa-ONNX API (commit 93fbe6d).

**whisper-jni (OLD - doesn't work):**
```kotlin
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperJNI

WhisperJNI.loadLibrary()
whisperJNI = WhisperJNI()
whisperContext = whisperJNI.init(modelPath)
```

**Sherpa-ONNX (NEW - Android compatible):**
```kotlin
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

val whisperConfig = OfflineWhisperModelConfig(
    encoder = "encoder.onnx",
    decoder = "decoder.onnx",
    language = "en",
    task = "transcribe"
)

val config = OfflineRecognizerConfig(
    whisper = whisperConfig,
    modelDir = modelDirectory
)

val recognizer = OfflineRecognizer(config)
```

### 🔧 Update Model Files

Sherpa-ONNX requires ONNX-format Whisper models instead of GGML:

**OLD (GGML format):**
- `ggml-tiny.en.bin` (single file)
- `ggml-base.en.bin`
- `ggml-small.en.bin`

**NEW (ONNX format - required):**
- Download from: https://github.com/k2-fsa/sherpa-onnx/releases
- Look for `sherpa-onnx-whisper-*` packages
- Typical structure:
  ```
  assets/
    ├── encoder.int8.onnx  (or encoder.onnx)
    ├── decoder.int8.onnx  (or decoder.onnx)
    └── tiny-tokens.txt    (tokenizer)
  ```

**Recommended models for Android:**
- **Tiny (English)**: `sherpa-onnx-whisper-tiny.en` (~40MB, fastest)
- **Base (English)**: `sherpa-onnx-whisper-base.en` (~75MB, better accuracy)
- **Small (Multilingual)**: `sherpa-onnx-whisper-small` (~242MB, best quality)

### 🔧 API Reference

Key Sherpa-ONNX classes to use:

```kotlin
// Import
import com.k2fsa.sherpa.onnx.*

// Configuration
OfflineRecognizerConfig(
    whisper: OfflineWhisperModelConfig,
    modelDir: String,
    numThreads: Int = 1,
    provider: String = "cpu"
)

// Recognition
val recognizer = OfflineRecognizer(config)
val stream = recognizer.createStream()
stream.acceptWaveform(samples, sampleRate)
recognizer.decode(stream)
val result = stream.result  // Get transcription text
```

## Benefits of Sherpa-ONNX

✨ **Fast**: Uses optimized ONNX Runtime
✨ **Efficient**: Smaller models, better battery life
✨ **Android-Native**: Proper JNI integration
✨ **Works with VAD**: Already compatible with Silero VAD
✨ **Active**: Latest release January 2025
✨ **Offline**: No internet required

## Testing Checklist

After updating WhisperProcessor:

- [ ] Download Sherpa-ONNX Whisper model (tiny.en recommended for testing)
- [ ] Extract model files to `app/src/main/assets/`
- [ ] Build the app
- [ ] Enable Whisper in Settings
- [ ] Make a test recording with speech
- [ ] Stop recording and check for transcription
- [ ] View transcription in the recording list

## Example Apps

Check these for reference implementations:
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android
- Look for `SherpaOnnx2Pass` or similar Android examples

## Notes

- Sherpa-ONNX supports **int8 quantized models** for even better mobile performance
- The library handles sample rate conversion automatically (16kHz for Whisper)
- Supports both streaming and offline recognition (we'll use offline)
- Compatible with your existing VAD pipeline for battery optimization

## Migration Priority

**HIGH**: Update `WhisperProcessor.kt` to use Sherpa-ONNX API
**HIGH**: Download and test tiny.en ONNX model
**MEDIUM**: Update `WhisperIntegrationHelper.kt` if needed
**LOW**: Consider int8 quantization for production
