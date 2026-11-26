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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NoorForegroundService : Service() {

    private var wakeWord: String = "jarvis"
    private var wakeRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceGracefully()
            return START_NOT_STICKY
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD)?.trim()?.lowercase()?.ifBlank { "jarvis" }
            ?: "jarvis"

        createNotificationChannel()
        val notification = buildNotification(wakeWord)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isRunning.value = true
        isStopping = false
        startWakeWordLoop()
        return START_STICKY
    }

    private fun startWakeWordLoop() {
        if (isStopping || !SpeechRecognizer.isRecognitionAvailable(this)) return
        mainHandler.post {
            try {
                wakeRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                wakeRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (!isStopping) {
                            mainHandler.postDelayed({ startWakeWordLoop() }, 1200L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val heard = matches.firstOrNull()?.trim().orEmpty()
                        if (heard.isNotBlank()) {
                            val lower = heard.lowercase()
                            if (lower.contains(wakeWord)) {
                                val afterWake = lower.substringAfter(wakeWord).trim()
                                _wakeCommandEvents.tryEmit(afterWake)
                                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra(EXTRA_WAKE_COMMAND, afterWake)
                                    putExtra(EXTRA_WAKE_TRIGGERED, true)
                                }
                                startActivity(launchIntent)
                            }
                        }
                        if (!isStopping) {
                            mainHandler.postDelayed({ startWakeWordLoop() }, 900L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Wake word loop error", e)
            }
        }
    }

    private fun stopServiceGracefully() {
        isStopping = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            wakeRecognizer?.destroy()
        } catch (_: Exception) {
        }
        wakeRecognizer = null
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isStopping = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            wakeRecognizer?.destroy()
        } catch (_: Exception) {
        }
        _isRunning.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(activeWakeWord: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NoorForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Noor AI — مائیک آن ہے")
            .setContentText("Wake word active: \"$activeWakeWord\"")
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Stop Listening", stopPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "NoorForegroundService"
        private const val CHANNEL_ID = "noor_voice_fg_channel"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_STOP = "com.example.action.STOP_WAKE_LISTENING"
        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val EXTRA_WAKE_TRIGGERED = "extra_wake_triggered"
        const val EXTRA_WAKE_COMMAND = "extra_wake_command"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _wakeCommandEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val wakeCommandEvents: SharedFlow<String> = _wakeCommandEvents.asSharedFlow()

        fun start(context: Context, wakeWord: String) {
            val intent = Intent(context, NoorForegroundService::class.java).apply {
                putExtra(EXTRA_WAKE_WORD, wakeWord)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NoorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
