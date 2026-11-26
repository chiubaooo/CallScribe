package com.callscribe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.callscribe.data.api.GeminiApiClient
import com.callscribe.data.db.CallScribeDatabase
import com.callscribe.data.export.TranscriptExporter
import com.callscribe.data.security.AppPreferences

class CallScribeApp : Application() {

    lateinit var database: CallScribeDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var geminiClient: GeminiApiClient
        private set
    lateinit var exporter: TranscriptExporter
        private set

    override fun onCreate() {
        super.onCreate()
        database = CallScribeDatabase.getInstance(this)
        preferences = AppPreferences(this)
        geminiClient = GeminiApiClient()
        exporter = TranscriptExporter(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            "通話與會議錄音",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "顯示正在進行中的 App 內建錄音狀態"
        }

        val transcriptionChannel = NotificationChannel(
            CHANNEL_TRANSCRIPTION,
            "轉錄完成通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "通話語音轉文字與摘要完成時通知"
        }

        manager.createNotificationChannel(recordingChannel)
        manager.createNotificationChannel(transcriptionChannel)
    }

    companion object {
        const val CHANNEL_RECORDING = "callscribe_recording"
        const val CHANNEL_TRANSCRIPTION = "callscribe_transcription"
    }
}
