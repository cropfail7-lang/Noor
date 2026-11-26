package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

data class RecordedAudioClip(
    val wavBytes: ByteArray,
    val durationSeconds: Int
)

/**
 * Foreground microphone service using Android AudioRecord (16 kHz, 16-bit PCM mono)
 * for real-time voice activity detection, audio level streaming, and audio clip capture.
 */
class AudioRecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var pcmBuffer = ByteArrayOutputStream()
    private var startTimeMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_AND_PROCESS -> {
                finishRecordingAndEmit()
                return START_NOT_STICKY
            }
            ACTION_CANCEL, ACTION_STOP_ACTIVATION -> {
                cancelCaptureAndStop()
                return START_NOT_STICKY
            }
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            _serviceError.value = "Microphone permission (RECORD_AUDIO) is required."
            stopSelf()
            return START_NOT_STICKY
        }

        val mode = intent?.action ?: ACTION_START_RECORDING
        createNotificationChannel()
        val isVadMode = mode == ACTION_START_VOICE_ACTIVATION
        val notification = buildNotification(isVadMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _serviceError.value = null
        if (isVadMode) {
            startVoiceActivationMonitor()
        } else {
            startPcmClipRecording()
        }

        return START_NOT_STICKY
    }

    private fun startPcmClipRecording() {
        captureJob?.cancel()
        releaseAudioRecord()
        pcmBuffer = ByteArrayOutputStream()
        startTimeMs = System.currentTimeMillis()
        _isVoiceActivationMonitoring.value = false
        _isRecording.value = true
        _recordingDurationSec.value = 0

        captureJob = serviceScope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE_HZ)

            val recorder = createRecorderSafely(minBuffer * 2)
            if (recorder == null) {
                _serviceError.value = "Unable to initialize device microphone."
                _isRecording.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            audioRecord = recorder
            val shortBuffer = ShortArray(minBuffer / 2)

            try {
                recorder.startRecording()
                while (isActive && _isRecording.value) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        val rms = computeNormalizedRms(shortBuffer, read)
                        _recordingRmsLevel.value = rms
                        _isSpeechDetected.value = rms >= VAD_RMS_THRESHOLD

                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val sample = shortBuffer[i].toInt()
                            bytes[i * 2] = (sample and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }
                        synchronized(pcmBuffer) {
                            pcmBuffer.write(bytes)
                        }

                        val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt()
                        _recordingDurationSec.value = elapsedSec
                        if (elapsedSec >= MAX_CLIP_DURATION_SEC) {
                            finishRecordingAndEmit()
                            break
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission revoked while recording", e)
                _serviceError.value = "Microphone access was denied."
                cancelCaptureAndStop()
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording error", e)
                _serviceError.value = e.localizedMessage ?: "Audio recording failed."
                cancelCaptureAndStop()
            } finally {
                releaseAudioRecord()
            }
        }
    }

    private fun startVoiceActivationMonitor() {
        captureJob?.cancel()
        releaseAudioRecord()
        _isRecording.value = false
        _isVoiceActivationMonitoring.value = true

        captureJob = serviceScope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE_HZ)

            val recorder = createRecorderSafely(minBuffer * 2)
            if (recorder == null) {
                _serviceError.value = "Unable to start microphone voice activation."
                _isVoiceActivationMonitoring.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            audioRecord = recorder
            val shortBuffer = ShortArray(minBuffer / 2)
            var consecutiveSpeechFrames = 0
            var lastTriggerMs = 0L

            try {
                recorder.startRecording()
                while (isActive && _isVoiceActivationMonitoring.value) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        val rms = computeNormalizedRms(shortBuffer, read)
                        _recordingRmsLevel.value = rms
                        val speechNow = rms >= VAD_RMS_THRESHOLD
                        _isSpeechDetected.value = speechNow

                        if (speechNow) {
                            consecutiveSpeechFrames++
                            val now = System.currentTimeMillis()
                            if (consecutiveSpeechFrames >= 3 && now - lastTriggerMs > 4000L) {
                                lastTriggerMs = now
                                consecutiveSpeechFrames = 0
                                _voiceActivationDetectedEvents.tryEmit(Unit)
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice activation monitor error", e)
                _serviceError.value = e.localizedMessage ?: "Voice activation stopped."
            } finally {
                releaseAudioRecord()
                _isVoiceActivationMonitoring.value = false
                _recordingRmsLevel.value = 0f
                _isSpeechDetected.value = false
            }
        }
    }

    private fun finishRecordingAndEmit() {
        _isRecording.value = false
        captureJob?.cancel()
        releaseAudioRecord()

        val rawPcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(1)
        _recordingRmsLevel.value = 0f
        _isSpeechDetected.value = false
        _recordingDurationSec.value = 0

        if (rawPcm.size >= MIN_VALID_PCM_BYTES) {
            val wav = buildWavBytes(rawPcm, SAMPLE_RATE_HZ, channels = 1, bitsPerSample = 16)
            _completedAudioEvents.tryEmit(RecordedAudioClip(wav, durationSec))
        } else {
            _serviceError.value = "Recording was too short. Please speak clearly for at least 1 second."
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelCaptureAndStop() {
        _isRecording.value = false
        _isVoiceActivationMonitoring.value = false
        _recordingRmsLevel.value = 0f
        _isSpeechDetected.value = false
        _recordingDurationSec.value = 0
        captureJob?.cancel()
        releaseAudioRecord()
        synchronized(pcmBuffer) { pcmBuffer.reset() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createRecorderSafely(bufferSizeBytes: Int): AudioRecord? {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
                rec.release()
                val fallback = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes
                )
                if (fallback.state == AudioRecord.STATE_INITIALIZED) fallback else {
                    fallback.release()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord creation failed", e)
            null
        }
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRecording.value = false
        _isVoiceActivationMonitoring.value = false
        _recordingRmsLevel.value = 0f
        captureJob?.cancel()
        releaseAudioRecord()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Noor Audio Recording & Voice Processing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isVadMode: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            21,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val stopPending = PendingIntent.getService(
            this,
            22,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isVadMode) {
            "Noor AI — Voice Activation Ready"
        } else {
            "Noor AI — Recording Voice Input"
        }
        val subtitle = if (isVadMode) {
            "Monitoring microphone for voice activity"
        } else {
            "آپ کی آواز ریکارڈ ہو رہی ہے"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val CHANNEL_ID = "noor_audio_recording_channel"
        private const val NOTIFICATION_ID = 4042

        const val SAMPLE_RATE_HZ = 16000
        private const val MAX_CLIP_DURATION_SEC = 30
        private const val MIN_VALID_PCM_BYTES = 6400 // ~0.2 seconds
        private const val VAD_RMS_THRESHOLD = 0.20f

        const val ACTION_START_RECORDING = "com.example.action.AUDIO_START_RECORDING"
        const val ACTION_STOP_AND_PROCESS = "com.example.action.AUDIO_STOP_AND_PROCESS"
        const val ACTION_CANCEL = "com.example.action.AUDIO_CANCEL"
        const val ACTION_START_VOICE_ACTIVATION = "com.example.action.AUDIO_START_VAD"
        const val ACTION_STOP_ACTIVATION = "com.example.action.AUDIO_STOP_VAD"

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _isVoiceActivationMonitoring = MutableStateFlow(false)
        val isVoiceActivationMonitoring: StateFlow<Boolean> = _isVoiceActivationMonitoring.asStateFlow()

        private val _recordingRmsLevel = MutableStateFlow(0f)
        val recordingRmsLevel: StateFlow<Float> = _recordingRmsLevel.asStateFlow()

        private val _isSpeechDetected = MutableStateFlow(false)
        val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

        private val _recordingDurationSec = MutableStateFlow(0)
        val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

        private val _serviceError = MutableStateFlow<String?>(null)
        val serviceError: StateFlow<String?> = _serviceError.asStateFlow()

        private val _completedAudioEvents = MutableSharedFlow<RecordedAudioClip>(extraBufferCapacity = 4)
        val completedAudioEvents: SharedFlow<RecordedAudioClip> = _completedAudioEvents.asSharedFlow()

        private val _voiceActivationDetectedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val voiceActivationDetectedEvents: SharedFlow<Unit> = _voiceActivationDetectedEvents.asSharedFlow()

        fun startRecording(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopAndProcess(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP_AND_PROCESS
            }
            context.startService(intent)
        }

        fun cancelRecording(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun startVoiceActivationMonitor(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START_VOICE_ACTIVATION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopVoiceActivationMonitor(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP_ACTIVATION
            }
            context.startService(intent)
        }

        fun clearError() {
            _serviceError.value = null
        }

        fun computeNormalizedRms(samples: ShortArray, count: Int): Float {
            if (count <= 0) return 0f
            var sumSq = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble() / Short.MAX_VALUE.toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / count.toDouble()).toFloat()
            return (rms * 4.5f).coerceIn(0f, 1f)
        }

        fun buildWavBytes(
            pcmData: ByteArray,
            sampleRate: Int = SAMPLE_RATE_HZ,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            val blockAlign = channels * (bitsPerSample / 8)
            val totalDataLen = pcmData.size + 36
            val out = ByteArray(44 + pcmData.size)

            // RIFF chunk descriptor
            out[0] = 'R'.code.toByte()
            out[1] = 'I'.code.toByte()
            out[2] = 'F'.code.toByte()
            out[3] = 'F'.code.toByte()
            writeIntLE(out, 4, totalDataLen)
            out[8] = 'W'.code.toByte()
            out[9] = 'A'.code.toByte()
            out[10] = 'V'.code.toByte()
            out[11] = 'E'.code.toByte()

            // fmt sub-chunk
            out[12] = 'f'.code.toByte()
            out[13] = 'm'.code.toByte()
            out[14] = 't'.code.toByte()
            out[15] = ' '.code.toByte()
            writeIntLE(out, 16, 16) // Subchunk1Size (16 for PCM)
            writeShortLE(out, 20, 1) // AudioFormat (1 = PCM)
            writeShortLE(out, 22, channels)
            writeIntLE(out, 24, sampleRate)
            writeIntLE(out, 28, byteRate)
            writeShortLE(out, 32, blockAlign)
            writeShortLE(out, 34, bitsPerSample)

            // data sub-chunk
            out[36] = 'd'.code.toByte()
            out[37] = 'a'.code.toByte()
            out[38] = 't'.code.toByte()
            out[39] = 'a'.code.toByte()
            writeIntLE(out, 40, pcmData.size)

            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }

        private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        private fun writeShortLE(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }
}
