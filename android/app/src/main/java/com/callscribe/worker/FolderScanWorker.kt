package com.callscribe.worker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.callscribe.CallScribeApp
import com.callscribe.data.AudioIngestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FolderScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as CallScribeApp
        val folderUriStr = app.preferences.watchedFolderUri ?: return@withContext Result.success()
        val treeUri = Uri.parse(folderUriStr)
        val rootDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
            ?: return@withContext Result.success()

        if (!rootDir.exists() || !rootDir.isDirectory) return@withContext Result.success()

        val repo = AudioIngestionRepository(applicationContext)
        val audioFiles = rootDir.listFiles().filter { file ->
            file.isFile && isSupportedAudio(file.name.orEmpty(), file.type)
        }

        for (file in audioFiles) {
            repo.enqueueFromUri(file.uri)
        }
        Result.success()
    }

    private fun isSupportedAudio(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_EXTENSIONS
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("m4a", "mp3", "wav", "ogg", "amr", "3gp", "aac", "flac")
        private const val PERIODIC_WORK_NAME = "callscribe_folder_watch"

        fun schedulePeriodicScan(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<FolderScanWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodicScan(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun scanNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FolderScanWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
