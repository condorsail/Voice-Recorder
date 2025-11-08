package org.fossify.voicerecorder.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.ParcelFileDescriptor
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.voicerecorder.buffers.ImmediateBuffer
import org.fossify.voicerecorder.buffers.RawAudioRingBuffer
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.helpers.EXTENSION_MP3
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class Mp3Recorder(val context: Context) : Recorder {
    private var mp3buffer: ByteArray = ByteArray(0)
    private var isPaused = AtomicBoolean(false)
    private var isStopped = AtomicBoolean(false)
    private var amplitude = AtomicInteger(0)
    private var outputPath: String? = null
    private var androidLame: AndroidLame? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var outputStream: FileOutputStream? = null

    // Buffer system
    private var immediateBuffer: ImmediateBuffer? = null
    private var rawBuffer: RawAudioRingBuffer? = null
    private var useBufferSystem: Boolean = false
    private val minBufferSize = AudioRecord.getMinBufferSize(
        context.config.samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        context.config.microphoneMode,
        context.config.samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBufferSize * 2
    )

    override fun setOutputFile(path: String) {
        outputPath = path
    }

    override fun prepare() {
        // Initialize buffer system if enabled
        useBufferSystem = context.config.crashRecoveryEnabled || context.config.rawBufferEnabled

        if (useBufferSystem) {
            if (context.config.crashRecoveryEnabled) {
                immediateBuffer = ImmediateBuffer(context)
            }

            if (context.config.rawBufferEnabled) {
                rawBuffer = RawAudioRingBuffer(context)
                rawBuffer?.startNewSegment()
            }
        }
    }

    override fun start() {
        val rawData = ShortArray(minBufferSize)
        mp3buffer = ByteArray((7200 + rawData.size * 2 * 1.25).toInt())

        // Initialize immediate buffer if enabled
        if (immediateBuffer != null && outputPath != null) {
            val tempPath = immediateBuffer!!.init(
                format = "mp3",
                sampleRate = context.config.samplingRate,
                bitrate = context.config.bitrate
            )
            // Use temp file for crash recovery
            outputStream = FileOutputStream(File(tempPath))
        } else {
            outputStream = try {
                if (fileDescriptor != null) {
                    FileOutputStream(fileDescriptor!!.fileDescriptor)
                } else {
                    FileOutputStream(File(outputPath!!))
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return
            }
        }

        androidLame = LameBuilder()
            .setInSampleRate(context.config.samplingRate)
            .setOutBitrate(context.config.bitrate / 1000)
            .setOutSampleRate(context.config.samplingRate)
            .setOutChannels(1)
            .build()

        ensureBackgroundThread {
            try {
                audioRecord.startRecording()
            } catch (e: Exception) {
                context.showErrorToast(e)
                return@ensureBackgroundThread
            }

            while (!isStopped.get()) {
                if (!isPaused.get()) {
                    val count = audioRecord.read(rawData, 0, minBufferSize)
                    if (count > 0) {
                        // Write raw PCM16 to raw buffer if enabled
                        rawBuffer?.write(rawData, count)

                        // Encode to MP3
                        val encoded = androidLame!!.encode(rawData, rawData, count, mp3buffer)
                        if (encoded > 0) {
                            try {
                                updateAmplitude(rawData)

                                // Write to immediate buffer or output stream
                                if (immediateBuffer != null) {
                                    immediateBuffer!!.write(mp3buffer, 0, encoded)
                                } else {
                                    outputStream!!.write(mp3buffer, 0, encoded)
                                }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        isPaused.set(true)
        isStopped.set(true)
        audioRecord.stop()
    }

    override fun pause() {
        isPaused.set(true)
    }

    override fun resume() {
        isPaused.set(false)
    }

    override fun release() {
        androidLame?.flush(mp3buffer)

        // Finalize buffers
        if (immediateBuffer != null) {
            // Flush final MP3 data
            val flushed = androidLame?.flush(mp3buffer) ?: 0
            if (flushed > 0) {
                immediateBuffer!!.write(mp3buffer, 0, flushed)
            }

            // Finalize and rename to final path
            if (outputPath != null) {
                immediateBuffer!!.finalize(outputPath!!)
            } else {
                immediateBuffer!!.cancel()
            }

            immediateBuffer!!.cleanup()
            immediateBuffer = null
        } else {
            outputStream?.close()
        }

        // Close raw buffer segment
        rawBuffer?.closeCurrentSegment()
        rawBuffer?.cleanup()
        rawBuffer = null

        audioRecord.release()
    }

    override fun getMaxAmplitude(): Int {
        return amplitude.get()
    }

    override fun setOutputFile(parcelFileDescriptor: ParcelFileDescriptor) {
        this.fileDescriptor = ParcelFileDescriptor.dup(parcelFileDescriptor.fileDescriptor)
    }

    private fun updateAmplitude(data: ShortArray) {
        var sum = 0L
        for (i in 0 until minBufferSize step 2) {
            sum += abs(data[i].toInt())
        }
        amplitude.set((sum / (minBufferSize / 8)).toInt())
    }
}
