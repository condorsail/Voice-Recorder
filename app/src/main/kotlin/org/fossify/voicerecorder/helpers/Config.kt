package org.fossify.voicerecorder.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import org.fossify.commons.helpers.BaseConfig
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.extensions.getDefaultRecordingsFolder
import androidx.core.content.edit

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var saveRecordingsFolder: String
        get() = prefs.getString(SAVE_RECORDINGS, context.getDefaultRecordingsFolder())!!
        set(saveRecordingsFolder) = prefs.edit().putString(SAVE_RECORDINGS, saveRecordingsFolder)
            .apply()

    var extension: Int
        get() = prefs.getInt(EXTENSION, EXTENSION_M4A)
        set(extension) = prefs.edit().putInt(EXTENSION, extension).apply()

    var microphoneMode: Int
        get() = prefs.getInt(MICROPHONE_MODE, MediaRecorder.AudioSource.DEFAULT)
        set(audioSource) = prefs.edit { putInt(MICROPHONE_MODE, audioSource) }

    fun getMicrophoneModeText(mode: Int) = context.getString(
        when (mode) {
            MediaRecorder.AudioSource.CAMCORDER -> R.string.microphone_mode_camcorder
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> R.string.microphone_mode_voice_communication
            MediaRecorder.AudioSource.VOICE_PERFORMANCE -> R.string.microphone_mode_voice_performance
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> R.string.microphone_mode_voice_recognition
            MediaRecorder.AudioSource.UNPROCESSED -> R.string.microphone_mode_unprocessed
            else -> org.fossify.commons.R.string.system_default
        }
    )

    var bitrate: Int
        get() = prefs.getInt(BITRATE, DEFAULT_BITRATE)
        set(bitrate) = prefs.edit().putInt(BITRATE, bitrate).apply()

    var samplingRate: Int
        get() = prefs.getInt(SAMPLING_RATE, DEFAULT_SAMPLING_RATE)
        set(samplingRate) = prefs.edit().putInt(SAMPLING_RATE, samplingRate).apply()

    var recordAfterLaunch: Boolean
        get() = prefs.getBoolean(RECORD_AFTER_LAUNCH, false)
        set(recordAfterLaunch) = prefs.edit().putBoolean(RECORD_AFTER_LAUNCH, recordAfterLaunch)
            .apply()

    var recordOnBoot: Boolean
        get() = prefs.getBoolean(RECORD_ON_BOOT, false)
        set(recordOnBoot) = prefs.edit().putBoolean(RECORD_ON_BOOT, recordOnBoot)
            .apply()

    fun getExtensionText() = context.getString(
        when (extension) {
            EXTENSION_M4A -> R.string.m4a
            EXTENSION_OGG -> R.string.ogg_opus
            else -> R.string.mp3_experimental
        }
    )

    fun getExtension() = context.getString(
        when (extension) {
            EXTENSION_M4A -> R.string.m4a
            EXTENSION_OGG -> R.string.ogg
            else -> R.string.mp3
        }
    )

    @SuppressLint("InlinedApi")
    fun getOutputFormat() = when (extension) {
        EXTENSION_OGG -> MediaRecorder.OutputFormat.OGG
        else -> MediaRecorder.OutputFormat.MPEG_4
    }

    @SuppressLint("InlinedApi")
    fun getAudioEncoder() = when (extension) {
        EXTENSION_OGG -> MediaRecorder.AudioEncoder.OPUS
        else -> MediaRecorder.AudioEncoder.AAC
    }

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON, true)
        set(keepScreenOn) = prefs.edit().putBoolean(KEEP_SCREEN_ON, keepScreenOn).apply()

    var wasMicModeWarningShown: Boolean
        get() = prefs.getBoolean(WAS_MIC_MODE_WARNING_SHOWN, false)
        set(wasMicModeWarningShown) = prefs.edit {
            putBoolean(WAS_MIC_MODE_WARNING_SHOWN, wasMicModeWarningShown)
        }

    var enableVAD: Boolean
        get() = prefs.getBoolean(ENABLE_VAD, false)
        set(enableVAD) = prefs.edit().putBoolean(ENABLE_VAD, enableVAD).apply()

    var vadThreshold: Float
        get() = prefs.getFloat(VAD_THRESHOLD, 0.5f)
        set(vadThreshold) = prefs.edit().putFloat(VAD_THRESHOLD, vadThreshold.coerceIn(0f, 1f)).apply()

    var enableWhisper: Boolean
        get() = prefs.getBoolean(ENABLE_WHISPER, false)
        set(enableWhisper) = prefs.edit().putBoolean(ENABLE_WHISPER, enableWhisper).apply()

    var whisperModel: String
        get() = prefs.getString(WHISPER_MODEL, "ggml-tiny.en.bin") ?: "ggml-tiny.en.bin"
        set(whisperModel) = prefs.edit().putString(WHISPER_MODEL, whisperModel).apply()

    var whisperLanguage: String?
        get() = prefs.getString(WHISPER_LANGUAGE, null)
        set(whisperLanguage) = prefs.edit().putString(WHISPER_LANGUAGE, whisperLanguage).apply()

    var whisperTranslate: Boolean
        get() = prefs.getBoolean(WHISPER_TRANSLATE, false)
        set(whisperTranslate) = prefs.edit().putBoolean(WHISPER_TRANSLATE, whisperTranslate).apply()
}
