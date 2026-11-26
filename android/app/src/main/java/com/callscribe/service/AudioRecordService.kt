package com.callscribe.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.callscribe.CallScribeApp
import com.callscribe.MainActivity
import com.callscribe.data.AudioIngestionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val lastError: String? = null
)

class AudioRecordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecordingAndEnqueue()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_state.value.isRecording) return

        val dir = File(filesDir, "pending_audio").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rec_$stamp.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            // Lock recording to built-in phone mic without requesting AudioFocus or Bluetooth SCO,
            // so Bluetooth A2DP music playback stays hi-res stereo and never degrades to HFP mono.
            val audioManager = getSystemService(AudioManager::class.java)
            val builtInMic = audioManager
                ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setAudioChannels(1)
                if (builtInMic != null) {
                    setPreferredDevice(builtInMic)
                }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            _state.value = RecordingState(isRecording = false, lastError = "錄音啟動失敗：${e.localizedMessage}")
            stopSelf()
            return
        }

        val notification = buildRecordingNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        _state.value = RecordingState(isRecording = true, elapsedSeconds = 0L)
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var sec = 0L
            while (isActive) {
                delay(1000)
                sec += 1
                _state.value = _state.value.copy(elapsedSeconds = sec)
            }
        }
    }

    private fun stopRecordingAndEnqueue() {
        timerJob?.cancel()
        val finishedFile = outputFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
            finishedFile?.delete()
        } finally {
            recorder?.release()
            recorder = null
        }

        _state.value = RecordingState(isRecording = false, elapsedSeconds = 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (finishedFile != null && finishedFile.exists() && finishedFile.length() > 0L) {
            val repo = AudioIngestionRepository(applicationContext)
            serviceScope.launch(Dispatchers.IO) {
                repo.enqueueLocalFile(finishedFile, finishedFile.name, "audio/mp4")
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun buildRecordingNotification() = NotificationCompat.Builder(
        this,
        CallScribeApp.CHANNEL_RECORDING
    )
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("CallScribe 錄音中")
        .setContentText("錄音結束後將自動轉錄為逐字稿與摘要")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        timerJob?.cancel()
        recorder?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.callscribe.action.START_RECORD"
        private const val ACTION_STOP = "com.callscribe.action.STOP_RECORD"
        private const val NOTIFICATION_ID = 4001

        private val _state = MutableStateFlow(RecordingState())
        val state: StateFlow<RecordingState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
