package com.callscribe.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TranscriptDao(context: Context) {

    private val helper = CallScribeSqliteHelper(context.applicationContext)
    private val rowsFlow = MutableStateFlow<List<TranscriptEntity>>(queryAllSync())

    fun observeAll(): Flow<List<TranscriptEntity>> = rowsFlow

    fun search(query: String): Flow<List<TranscriptEntity>> {
        val q = query.lowercase()
        return rowsFlow.map { list ->
            list.filter { item ->
                item.title?.lowercase()?.contains(q) == true ||
                    item.fileName.lowercase().contains(q) ||
                    item.summaryMarkdown?.lowercase()?.contains(q) == true ||
                    item.transcriptText?.lowercase()?.contains(q) == true
            }
        }
    }

    fun observeById(id: String): Flow<TranscriptEntity?> =
        rowsFlow.map { list -> list.firstOrNull { it.id == id } }

    suspend fun getById(id: String): TranscriptEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM transcripts WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntity() else null
        }
    }

    suspend fun getByHash(hash: String): TranscriptEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM transcripts WHERE contentHash = ? LIMIT 1",
            arrayOf(hash)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntity() else null
        }
    }

    suspend fun insert(entity: TranscriptEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", entity.id)
            put("contentHash", entity.contentHash)
            put("sourceUri", entity.sourceUri)
            put("fileName", entity.fileName)
            put("mimeType", entity.mimeType)
            put("status", entity.status.name)
            put("errorMessage", entity.errorMessage)
            put("title", entity.title)
            put("summaryMarkdown", entity.summaryMarkdown)
            put("actionItemsMarkdown", entity.actionItemsMarkdown)
            put("transcriptText", entity.transcriptText)
            put("exportedUri", entity.exportedUri)
            put("exportedFileName", entity.exportedFileName)
            put("createdAt", entity.createdAt)
            put("updatedAt", entity.updatedAt)
        }
        helper.writableDatabase.insertOrThrow("transcripts", null, values)
        refreshFlow()
    }

    suspend fun updateStatus(
        id: String,
        status: JobStatus,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", status.name)
            put("errorMessage", errorMessage)
            put("updatedAt", updatedAt)
        }
        // Terminal state guard: never overwrite COMPLETED
        helper.writableDatabase.update(
            "transcripts",
            values,
            "id = ? AND status != ?",
            arrayOf(id, JobStatus.COMPLETED.name)
        )
        refreshFlow()
    }

    suspend fun markCompleted(
        id: String,
        title: String,
        summaryMarkdown: String,
        actionItemsMarkdown: String,
        transcriptText: String,
        exportedUri: String?,
        exportedFileName: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", JobStatus.COMPLETED.name)
            putNull("errorMessage")
            put("title", title)
            put("summaryMarkdown", summaryMarkdown)
            put("actionItemsMarkdown", actionItemsMarkdown)
            put("transcriptText", transcriptText)
            put("exportedUri", exportedUri)
            put("exportedFileName", exportedFileName)
            put("updatedAt", updatedAt)
        }
        helper.writableDatabase.update(
            "transcripts",
            values,
            "id = ? AND status != ?",
            arrayOf(id, JobStatus.COMPLETED.name)
        )
        refreshFlow()
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("transcripts", "id = ?", arrayOf(id))
        refreshFlow()
    }

    private fun refreshFlow() {
        rowsFlow.value = queryAllSync()
    }

    private fun queryAllSync(): List<TranscriptEntity> {
        val list = mutableListOf<TranscriptEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM transcripts ORDER BY createdAt DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toEntity())
            }
        }
        return list
    }

    private fun Cursor.toEntity(): TranscriptEntity = TranscriptEntity(
        id = getString(getColumnIndexOrThrow("id")),
        contentHash = getString(getColumnIndexOrThrow("contentHash")),
        sourceUri = getString(getColumnIndexOrThrow("sourceUri")),
        fileName = getString(getColumnIndexOrThrow("fileName")),
        mimeType = getString(getColumnIndexOrThrow("mimeType")),
        status = JobStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        errorMessage = getStringOrNull("errorMessage"),
        title = getStringOrNull("title"),
        summaryMarkdown = getStringOrNull("summaryMarkdown"),
        actionItemsMarkdown = getStringOrNull("actionItemsMarkdown"),
        transcriptText = getStringOrNull("transcriptText"),
        exportedUri = getStringOrNull("exportedUri"),
        exportedFileName = getStringOrNull("exportedFileName"),
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        updatedAt = getLong(getColumnIndexOrThrow("updatedAt"))
    )

    private fun Cursor.getStringOrNull(col: String): String? {
        val idx = getColumnIndexOrThrow(col)
        return if (isNull(idx)) null else getString(idx)
    }
}

private class CallScribeSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, "callscribe.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transcripts (
                id TEXT PRIMARY KEY NOT NULL,
                contentHash TEXT NOT NULL UNIQUE,
                sourceUri TEXT NOT NULL,
                fileName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                status TEXT NOT NULL,
                errorMessage TEXT,
                title TEXT,
                summaryMarkdown TEXT,
                actionItemsMarkdown TEXT,
                transcriptText TEXT,
                exportedUri TEXT,
                exportedFileName TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
