# Whisper Models for Voice Recorder (Sherpa-ONNX)

This directory should contain Whisper models in **ONNX format** for speech-to-text transcription using Sherpa-ONNX.

## Quick Start

**Download the tiny.en model (recommended)**:

```bash
cd app/src/main/assets/
mkdir -p whisper-tiny.en
cd whisper-tiny.en

# Download the 3 required files
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt

# Rename to expected names
mv tiny.en-encoder.int8.onnx encoder.int8.onnx
mv tiny.en-decoder.int8.onnx decoder.int8.onnx
mv tiny.en-tokens.txt tokens.txt
```

After downloading, rebuild the app and enable Whisper in Settings.

## Directory Structure

Models must be placed in subdirectories with this structure:

```
app/src/main/assets/
├── whisper-tiny.en/
│   ├── encoder.int8.onnx
│   ├── decoder.int8.onnx
│   └── tokens.txt
├── whisper-base.en/    (optional)
│   ├── encoder.int8.onnx
│   ├── decoder.int8.onnx
│   └── tokens.txt
└── silero_vad.onnx     (already included)
```

## Available Models

### English-Only Models (Recommended)

#### 1. **tiny.en** (Recommended - Fast & Efficient)
- **Size**: ~40 MB (int8 quantized)
- **Language**: English only
- **Speed**: Very fast (~4x real-time on modern phones)
- **Accuracy**: Good for most use cases
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-tiny.en](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en)
- **Directory**: `whisper-tiny.en/`

#### 2. **base.en** (Better Accuracy)
- **Size**: ~75 MB (int8 quantized)
- **Language**: English only
- **Speed**: Fast (~2x real-time)
- **Accuracy**: Better than tiny
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-base.en](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en)
- **Directory**: `whisper-base.en/`

#### 3. **small.en** (Best Accuracy)
- **Size**: ~242 MB (int8 quantized)
- **Language**: English only
- **Speed**: Moderate (~1x real-time)
- **Accuracy**: Excellent
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-small.en](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en)
- **Directory**: `whisper-small.en/`

### Multilingual Models

#### 4. **tiny** (Multilingual)
- **Size**: ~40 MB (int8 quantized)
- **Languages**: 99 languages
- **Speed**: Very fast
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-tiny](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny)
- **Directory**: `whisper-tiny/`

#### 5. **base** (Multilingual)
- **Size**: ~75 MB (int8 quantized)
- **Languages**: 99 languages
- **Speed**: Fast
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-base](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base)
- **Directory**: `whisper-base/`

#### 6. **small** (Multilingual)
- **Size**: ~242 MB (int8 quantized)
- **Languages**: 99 languages
- **Speed**: Moderate
- **Hugging Face**: [csukuangfj/sherpa-onnx-whisper-small](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small)
- **Directory**: `whisper-small/`

## Download Instructions

### Method 1: Manual Download (Any OS)

1. Visit the Hugging Face model page (e.g., https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/tree/main)
2. Download these 3 files:
   - `tiny.en-encoder.int8.onnx` (or just `encoder.int8.onnx`)
   - `tiny.en-decoder.int8.onnx` (or just `decoder.int8.onnx`)
   - `tiny.en-tokens.txt` (or just `tokens.txt`)
3. Create directory: `app/src/main/assets/whisper-tiny.en/`
4. Place the files there and rename if needed:
   - `encoder.int8.onnx`
   - `decoder.int8.onnx`
   - `tokens.txt`
5. Rebuild the app

### Method 2: Using wget (Linux/Mac/WSL)

```bash
cd app/src/main/assets/
mkdir -p whisper-tiny.en
cd whisper-tiny.en

# Download files
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx
wget https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt

# Rename
mv tiny.en-encoder.int8.onnx encoder.int8.onnx
mv tiny.en-decoder.int8.onnx decoder.int8.onnx
mv tiny.en-tokens.txt tokens.txt
```

### Method 3: Using curl

```bash
cd app/src/main/assets/
mkdir -p whisper-tiny.en
cd whisper-tiny.en

# Download and rename in one step
curl -L -o encoder.int8.onnx \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx"

curl -L -o decoder.int8.onnx \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx"

curl -L -o tokens.txt \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt"
```

### Method 4: Using git-lfs (For Multiple Models)

```bash
cd app/src/main/assets/

# Install git-lfs if needed
# Ubuntu: sudo apt install git-lfs
# Mac: brew install git-lfs

# Clone the model repository
git lfs install
git clone https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en

# Rename directory
mv sherpa-onnx-whisper-tiny.en whisper-tiny.en

# Rename files inside
cd whisper-tiny.en
mv tiny.en-encoder.int8.onnx encoder.int8.onnx
mv tiny.en-decoder.int8.onnx decoder.int8.onnx
mv tiny.en-tokens.txt tokens.txt
```

## Model Comparison

| Model | Size | Languages | Speed | Accuracy | Use Case |
|-------|------|-----------|-------|----------|----------|
| tiny.en | ~40 MB | English | ★★★★★ | ★★★ | Real-time, Mobile |
| base.en | ~75 MB | English | ★★★★ | ★★★★ | Balanced |
| small.en | ~242 MB | English | ★★★ | ★★★★★ | Best quality |
| tiny | ~40 MB | 99 langs | ★★★★★ | ★★★ | Multilingual, Mobile |
| base | ~75 MB | 99 langs | ★★★★ | ★★★★ | Multilingual, Balanced |
| small | ~242 MB | 99 langs | ★★★ | ★★★★★ | Multilingual, Quality |

## Configuration in App

1. Open Voice Recorder app
2. Go to **Settings** → **Transcription**
3. Enable "Enable Whisper transcription"
4. Select your model from the dropdown
5. (Optional) Enable "Use VAD to skip silence" for faster processing

## Model Format Details

### File Types
- **encoder.int8.onnx**: ONNX encoder model (int8 quantized for mobile)
- **decoder.int8.onnx**: ONNX decoder model (int8 quantized for mobile)
- **tokens.txt**: Tokenizer vocabulary file

### Why ONNX Format?
- **Faster**: ONNX Runtime is optimized for mobile devices
- **Smaller**: int8 quantization reduces size by ~4x vs float32
- **Efficient**: Lower memory usage and battery consumption
- **Cross-platform**: Works on Android, iOS, desktop

### int8 vs float32
We use **int8 quantized** models by default for mobile:
- **Size**: 4x smaller than float32
- **Speed**: 2-3x faster on mobile CPUs
- **Accuracy**: ~99% of float32 accuracy
- **Memory**: Uses less RAM

## Supported Languages (Multilingual Models)

Afrikaans, Arabic, Armenian, Azerbaijani, Belarusian, Bosnian, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, English, Estonian, Finnish, French, Galician, German, Greek, Hebrew, Hindi, Hungarian, Icelandic, Indonesian, Italian, Japanese, Kannada, Kazakh, Korean, Latvian, Lithuanian, Macedonian, Malay, Marathi, Maori, Nepali, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Serbian, Slovak, Slovenian, Spanish, Swahili, Swedish, Tagalog, Tamil, Thai, Turkish, Ukrainian, Urdu, Vietnamese, Welsh

## Performance Tips

1. **For English recordings**: Use `.en` models for 2x faster and better results
2. **For 24/7 recording**: Use `tiny.en` to minimize battery usage
3. **For best quality**: Use `small.en` or `base.en`
4. **Enable VAD**: "Use VAD to skip silence" reduces processing time by 40-70%
5. **WiFi only**: Enable "Transcribe only on WiFi" to save mobile data (if applicable)
6. **Charging only**: Enable "Transcribe only on charging" to preserve battery

## Troubleshooting

### "No transcription available"
**Causes**:
- Model files not downloaded
- Files in wrong directory
- Incorrect file names
- App not rebuilt after adding models

**Solution**:
1. Check `app/src/main/assets/whisper-{modelname}/` exists
2. Verify files: `encoder.int8.onnx`, `decoder.int8.onnx`, `tokens.txt`
3. Rebuild the app (clean build recommended)
4. Enable Whisper in Settings

### "Model not found" error
- Ensure directory name matches model name (e.g., `whisper-tiny.en` for tiny.en model)
- Check file names are exactly: `encoder.int8.onnx`, `decoder.int8.onnx`, `tokens.txt`
- Rebuild the app completely: `./gradlew clean build`

### Out of memory errors
- Use `tiny.en` instead of larger models
- Enable "Use VAD to skip silence"
- Close other apps
- Restart device

### Slow transcription
- Use smaller model (tiny instead of base)
- Use English-only model for English audio
- Ensure VAD is enabled
- Check if phone is in power-saving mode

### Empty transcriptions
- Check audio has actual speech (not just silence)
- Try disabling VAD temporarily
- Test with a known speech sample
- Check logcat for errors: `adb logcat | grep Whisper`

## Technical Details

### How It Works
1. **VAD (Voice Activity Detection)**: Silero VAD removes silence before processing
2. **Preprocessing**: Audio resampled to 16kHz mono
3. **Encoding**: Whisper encoder processes audio features
4. **Decoding**: Whisper decoder generates text tokens
5. **Postprocessing**: Tokens converted to text

### Memory Requirements
- **tiny**: ~200 MB RAM
- **base**: ~350 MB RAM
- **small**: ~800 MB RAM

### Processing Speed (on modern Android phone)
- **tiny.en**: ~4x real-time (10min audio → 2.5min processing)
- **base.en**: ~2x real-time (10min audio → 5min processing)
- **small.en**: ~1x real-time (10min audio → 10min processing)

*Speed varies by device CPU. Snapdragon 8 Gen 2+ recommended for real-time.*

## Additional Resources

- **Sherpa-ONNX**: https://github.com/k2-fsa/sherpa-onnx
- **Pre-trained Models**: https://github.com/k2-fsa/sherpa-onnx/releases
- **Hugging Face Models**: https://huggingface.co/csukuangfj
- **ONNX Runtime**: https://onnxruntime.ai/
- **OpenAI Whisper**: https://github.com/openai/whisper

## License

Whisper models are licensed under MIT License by OpenAI.
Sherpa-ONNX is licensed under Apache 2.0 License.
