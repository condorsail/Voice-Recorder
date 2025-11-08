# Whisper Models for Voice Recorder

This directory should contain Whisper models in GGML format for speech-to-text transcription.

## Required Model Files

The Voice Recorder app supports Whisper models for real-time and offline transcription. You need to download at least one model and place it in this directory (`app/src/main/assets/`).

## Recommended Models

### 1. **tiny.en** (Recommended for most users)
- **File**: `ggml-tiny.en.bin`
- **Size**: ~75 MB
- **Language**: English only
- **Speed**: Very fast
- **Accuracy**: Good for most use cases
- **Download**: [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin)

### 2. **base.en** (Better accuracy)
- **File**: `ggml-base.en.bin`
- **Size**: ~142 MB
- **Language**: English only
- **Speed**: Fast
- **Accuracy**: Better than tiny
- **Download**: [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin)

### 3. **tiny** (Multilingual)
- **File**: `ggml-tiny.bin`
- **Size**: ~75 MB
- **Languages**: 99 languages
- **Speed**: Very fast
- **Accuracy**: Good
- **Download**: [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin)

### 4. **base** (Multilingual, better accuracy)
- **File**: `ggml-base.bin`
- **Size**: ~142 MB
- **Languages**: 99 languages
- **Speed**: Fast
- **Accuracy**: Better than tiny
- **Download**: [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin)

## Quantized Models (Smaller size, slightly lower accuracy)

For devices with limited storage, quantized models are available:

- **tiny.en-q5_1**: `ggml-tiny.en-q5_1.bin` (~31 MB)
- **base.en-q5_1**: `ggml-base.en-q5_1.bin` (~57 MB)

## Download Instructions

### Method 1: Direct Download
1. Click on the download link for your chosen model above
2. Save the `.bin` file
3. Copy it to `app/src/main/assets/` in your project
4. Rebuild the app

### Method 2: Using wget (Linux/Mac)
```bash
cd app/src/main/assets/

# Download tiny.en model (recommended)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin

# Or download base.en for better accuracy
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin
```

### Method 3: Using curl
```bash
cd app/src/main/assets/

# Download tiny.en model
curl -L -o ggml-tiny.en.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
```

### Method 4: Using whisper.cpp download script
```bash
# Clone whisper.cpp repository
git clone https://github.com/ggml-org/whisper.cpp.git
cd whisper.cpp

# Download tiny.en model
./models/download-ggml-model.sh tiny.en

# Copy to your project
cp models/ggml-tiny.en.bin /path/to/Voice-Recorder/app/src/main/assets/
```

## All Available Models

| Model | Size | English-only | Multilingual | Required VRAM | Relative Speed |
|-------|------|--------------|--------------|---------------|----------------|
| tiny.en | 75 MB | ✓ | | ~390 MB | ~32x |
| tiny | 75 MB | | ✓ | ~390 MB | ~32x |
| base.en | 142 MB | ✓ | | ~500 MB | ~16x |
| base | 142 MB | | ✓ | ~500 MB | ~16x |
| small.en | 466 MB | ✓ | | ~1.0 GB | ~6x |
| small | 466 MB | | ✓ | ~1.0 GB | ~6x |
| medium.en | 1.5 GB | ✓ | | ~2.6 GB | ~2x |
| medium | 1.5 GB | | ✓ | ~2.6 GB | ~2x |
| large-v1 | 2.9 GB | | ✓ | ~4.7 GB | 1x |
| large-v2 | 2.9 GB | | ✓ | ~4.7 GB | 1x |
| large-v3 | 2.9 GB | | ✓ | ~4.7 GB | 1x |

**Note**: For mobile devices, we recommend **tiny.en** or **base.en** models only. Larger models require too much memory and are too slow for real-time use.

## Model Configuration

Once you've downloaded a model, configure it in the app:

```kotlin
// In your configuration
config.whisperModel = "ggml-tiny.en.bin"  // Use the filename you downloaded
config.enableWhisper = true
```

## Supported Languages (Multilingual models)

Multilingual models (.bin files without .en suffix) support 99 languages including:

Afrikaans, Arabic, Armenian, Azerbaijani, Belarusian, Bosnian, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, English, Estonian, Finnish, French, Galician, German, Greek, Hebrew, Hindi, Hungarian, Icelandic, Indonesian, Italian, Japanese, Kannada, Kazakh, Korean, Latvian, Lithuanian, Macedonian, Malay, Marathi, Maori, Nepali, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Serbian, Slovak, Slovenian, Spanish, Swahili, Swedish, Tagalog, Tamil, Thai, Turkish, Ukrainian, Urdu, Vietnamese, Welsh

## Model Format

All models use the GGML format, which is optimized for CPU inference. The models are compatible with whisper.cpp and whisper-jni.

## Performance Tips

1. **For English-only recordings**: Use `.en` models for faster and more accurate results
2. **For real-time transcription**: Use `tiny.en` or `tiny` models
3. **For batch transcription**: You can use `base` or `small` models for better accuracy
4. **For low-memory devices**: Use quantized models (q5_1 or q8_0 variants)

## Troubleshooting

### Model not found error
- Verify the model file is in `app/src/main/assets/`
- Check the filename matches exactly (case-sensitive)
- Ensure you rebuilt the app after adding the model

### Out of memory errors
- Use a smaller model (tiny instead of base)
- Use quantized models
- Close other apps to free memory

### Slow transcription
- Use a smaller model
- Ensure you're using the English-only variant for English audio
- Consider using quantized models

## Additional Resources

- [Whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp)
- [Whisper Models on Hugging Face](https://huggingface.co/ggerganov/whisper.cpp)
- [OpenAI Whisper Documentation](https://github.com/openai/whisper)
- [Whisper-JNI Library](https://github.com/GiviMAD/whisper-jni)

## License

The Whisper models are licensed under MIT License by OpenAI.
