package com.anubhav.placemyorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioCaptureHelper(
    private val sampleRate: Int = 16000,
    private val silenceThreshold: Double = 2000.0,
    private val silenceDurationMillis: Long = 1500,
    private val volumeCheckInterval: Long = 100L,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit,
    private val onSegmentUpload: (delta: ByteArray) -> Unit = {},
) {
    private val isRecording = AtomicBoolean(false)
    private val isUserSpeaking = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var uploadJob: Job? = null
    private var lastVoiceTime = 0L

    private val tempBuffer = ByteArrayOutputStream()
    private val speechQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (isRecording.get()) return
        isRecording.set(true)

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder?.startRecording()

        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            lastVoiceTime = System.currentTimeMillis()

            while (isRecording.get()) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val amplitude = calculateRMS(buffer, read)
                    val now = System.currentTimeMillis()

                    if (amplitude > silenceThreshold) {
                        lastVoiceTime = now

                        if (!isUserSpeaking.get()) {
                            isUserSpeaking.set(true)
                            withContext(Dispatchers.Main) { onSpeechStart() }
                        }

                        synchronized(tempBuffer) {
                            tempBuffer.write(buffer, 0, read)
                        }
                    } else {
                        if (isUserSpeaking.get() && now - lastVoiceTime > silenceDurationMillis) {
                            isUserSpeaking.set(false)

                            val speechSegment: ByteArray?
                            synchronized(tempBuffer) {
                                speechSegment = tempBuffer.toByteArray()
                                tempBuffer.reset()
                            }

                            if (speechSegment != null) {
                                speechQueue.add(speechSegment)
                                withContext(Dispatchers.Main) {
                                    onSpeechEnd()
                                }
                            }
                        }
                    }

                    delay(volumeCheckInterval)
                }
            }

            recorder?.stop()
            recorder?.release()
            recorder = null
        }


        if (uploadJob == null || uploadJob?.isCompleted == true) {
            uploadJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    val segment = speechQueue.poll()
                    if (segment != null) {
                        onSegmentUpload(segment)
                    } else {
                        delay(200)
                    }
                    if (!isRecording.get() && speechQueue.isEmpty()) break
                }

                withContext(Dispatchers.Main) {
                    println("✅ Upload job finished.")
                }
            }
        }

    }

    fun stopListening() {
        if (!isRecording.get()) return
        isRecording.set(false)
        job?.cancel()
        uploadJob?.cancel()
        job = null
        uploadJob = null
    }

    private fun calculateRMS(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length step 2) {
            val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
            sum += sample * sample
        }
        return sqrt(sum / (length / 2))
    }
}
