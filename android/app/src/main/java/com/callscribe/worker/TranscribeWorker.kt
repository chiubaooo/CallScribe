package com.callscribe.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callscribe.CallScribeApp
import com.callscribe.MainActivity
import com.callscribe.data.db.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranscribeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val app = applicationContext as CallScribeApp
        val dao = app.database.transcriptDao()
        val prefs = app.preferences
        val api = app.geminiClient

        val job = dao.getById(jobId) ?: return@withContext Result.failure()
        if (job.status == JobStatus.COMPLETED) return@withContext Result.success()

        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            dao.updateStatus(jobId, JobStatus.FAILED, "尚未設定 Gemini API Key，請到右上角設定頁填入")
            return@withContext Result.failure()
        }

        val audioFile = File(job.sourceUri)
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            dao.updateStatus(jobId, JobStatus.FAILED, "找不到本地暫存音檔，請重新匯入")
            return@withContext Result.failure()
        }

        var remoteFileName: String? = null
        try {
            dao.updateStatus(jobId, JobStatus.UPLOADING)
            val uploaded = api.uploadAudioFile(
                apiKey = apiKey,
                audioFile = audioFile,
                mimeType = job.mimeType,
                displayName = job.fileName
            )
            remoteFileName = uploaded.name

            dao.updateStatus(jobId, JobStatus.TRANSCRIBING)
            val rawTranscript = api.transcribeWithDiarization(
                apiKey = apiKey,
                fileUri = uploaded.uri,
                mimeType = uploaded.mimeType
            )

            dao.updateStatus(jobId, JobStatus.SUMMARIZING)
            val summary = api.summarizeTranscriptZhTw(
                apiKey = apiKey,
                rawTranscript = rawTranscript
            )

            val summaryMd = summary.summaryBullets.joinToString("\n") { "- $it" }
            val actionMd = summary.actionItems.joinToString("\n") { "- [ ] $it" }

            val exported = runCatching {
                app.exporter.exportToDocuments(
                    title = summary.title,
                    createdAt = job.createdAt,
                    sourceFileName = job.fileName,
                    summaryMarkdown = summaryMd,
                    actionItemsMarkdown = actionMd,
                    transcriptText = rawTranscript,
                    format = prefs.exportFormat
                )
            }.getOrNull()

            dao.markCompleted(
                id = jobId,
                title = summary.title,
                summaryMarkdown = summaryMd,
                actionItemsMarkdown = actionMd,
                transcriptText = rawTranscript,
                exportedUri = exported?.uriString,
                exportedFileName = exported?.fileName
            )

            // Clean up local cached copy only after completion
            if (audioFile.parentFile?.name == "pending_audio") {
                audioFile.delete()
            }

            postCompletionNotification(jobId, summary.title, exported?.fileName)
            Result.success()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "轉錄過程發生未知錯誤"
            dao.updateStatus(jobId, JobStatus.FAILED, msg)
            Result.failure()
        } finally {
            // Privacy invariant: always delete remote audio file immediately after transcription
            remoteFileName?.let { api.deleteRemoteFileSafely(apiKey, it) }
        }
    }

    private fun postCompletionNotification(jobId: String, title: String, fileName: String?) {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRANSCRIPT_ID, jobId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (fileName != null) {
            "已存檔至 Documents/CallTranscripts/$fileName"
        } else {
            "點擊查看逐字稿與重點摘要"
        }

        val notification = NotificationCompat.Builder(
            applicationContext,
            CallScribeApp.CHANNEL_TRANSCRIPTION
        )
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("轉錄完成：$title")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(jobId.hashCode(), notification)
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
    }
}
