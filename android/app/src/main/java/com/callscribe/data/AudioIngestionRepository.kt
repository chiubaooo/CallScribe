package com.callscribe.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.callscribe.CallScribeApp
import com.callscribe.data.db.JobStatus
import com.callscribe.data.db.TranscriptEntity
import com.callscribe.worker.TranscribeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

sealed class EnqueueResult {
    data class Enqueued(val id: String, val fileName: String) : EnqueueResult()
    data class Duplicate(val existingId: String, val fileName: String) : EnqueueResult()
    data class Error(val message: String) : EnqueueResult()
}

class AudioIngestionRepository(private val context: Context) {

    private val app = context.applicationContext as CallScribeApp
    private val dao = app.database.transcriptDao()
    private val prefs = app.preferences

    suspend fun enqueueFromUri(uri: Uri): EnqueueResult = withContext(Dispatchers.IO) {
        val (displayName, sizeBytes) = resolveUriMetadata(uri)
        if (sizeBytes > MAX_AUDIO_BYTES) {
            return@withContext EnqueueResult.Error("音檔超過 100MB 上限，無法自動上傳")
        }

        val mimeType = resolveMimeType(uri, displayName)
        val jobId = UUID.randomUUID().toString()
        val cacheDir = File(context.filesDir, "pending_audio").apply { mkdirs() }
        val ext = displayName.substringAfterLast('.', "m4a").lowercase()
        val localCopy = File(cacheDir, "$jobId.$ext")

        val hash = try {
            copyAndHashUri(uri, localCopy)
        } catch (e: Exception) {
            localCopy.delete()
            return@withContext EnqueueResult.Error("無法讀取音檔：${e.localizedMessage ?: "權限不足"}")
        }

        enqueuePreparedFile(
            jobId = jobId,
            localFile = localCopy,
            fileName = displayName,
            mimeType = mimeType,
            contentHash = hash
        )
    }

    suspend fun enqueueLocalFile(file: File, displayName: String, mimeType: String = "audio/mp4"): EnqueueResult =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext EnqueueResult.Error("錄音檔無效或長度為 0")
            }
            val hash = sha256OfFile(file)
            enqueuePreparedFile(
                jobId = UUID.randomUUID().toString(),
                localFile = file,
                fileName = displayName,
                mimeType = mimeType,
                contentHash = hash
            )
        }

    suspend fun retryJob(jobId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(jobId) ?: return@withContext
        if (existing.status == JobStatus.COMPLETED) return@withContext
        dao.updateStatus(jobId, JobStatus.QUEUED, errorMessage = null)
        scheduleWorker(jobId, forceReplace = true)
    }

    suspend fun resumeStuckPendingJobs() = withContext(Dispatchers.IO) {
        val pending = dao.getPendingJobs()
        for (job in pending) {
            dao.updateStatus(job.id, JobStatus.QUEUED, errorMessage = null)
            scheduleWorker(job.id, forceReplace = true)
        }
    }

    private suspend fun enqueuePreparedFile(
        jobId: String,
        localFile: File,
        fileName: String,
        mimeType: String,
        contentHash: String
    ): EnqueueResult {
        val existing = dao.getByHash(contentHash)
        if (existing != null) {
            // Avoid duplicate billing; clean up redundant local copy
            if (localFile.absolutePath != existing.sourceUri) {
                localFile.delete()
            }
            if (existing.status == JobStatus.FAILED) {
                dao.updateStatus(existing.id, JobStatus.QUEUED, errorMessage = null)
                scheduleWorker(existing.id)
                return EnqueueResult.Enqueued(existing.id, existing.fileName)
            }
            return EnqueueResult.Duplicate(existing.id, existing.fileName)
        }

        val entity = TranscriptEntity(
            id = jobId,
            contentHash = contentHash,
            sourceUri = localFile.absolutePath,
            fileName = fileName,
            mimeType = mimeType,
            status = JobStatus.QUEUED
        )
        dao.insert(entity)
        scheduleWorker(jobId)
        return EnqueueResult.Enqueued(jobId, fileName)
    }

    private fun scheduleWorker(jobId: String, forceReplace: Boolean = false) {
        val networkType = if (prefs.wifiOnlyUpload) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(TranscribeWorker.KEY_JOB_ID to jobId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "transcribe_$jobId",
            if (forceReplace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun copyAndHashUri(uri: Uri, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("ContentResolver returned null stream")
        input.use { src ->
            FileOutputStream(target).use { dst ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = src.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_AUDIO_BYTES) {
                        throw IOException("音檔超過 100MB 上限")
                    }
                    digest.update(buffer, 0, read)
                    dst.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun resolveUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "call_${System.currentTimeMillis()}.m4a"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun resolveMimeType(uri: Uri, fileName: String): String {
        val resolved = context.contentResolver.getType(uri)
        if (!resolved.isNullOrBlank() && resolved.startsWith("audio/")) return resolved
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "amr" -> "audio/amr"
            "3gp", "3gpp" -> "audio/3gpp"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            else -> "audio/mp4"
        }
    }

    companion object {
        private const val MAX_AUDIO_BYTES = 100L * 1024L * 1024L
    }
}
