package com.callscribe.data.db

enum class JobStatus {
    QUEUED,
    UPLOADING,
    TRANSCRIBING,
    SUMMARIZING,
    COMPLETED,
    FAILED
}

data class TranscriptEntity(
    val id: String,
    val contentHash: String,
    val sourceUri: String,
    val fileName: String,
    val mimeType: String,
    val status: JobStatus = JobStatus.QUEUED,
    val errorMessage: String? = null,
    val title: String? = null,
    val summaryMarkdown: String? = null,
    val actionItemsMarkdown: String? = null,
    val transcriptText: String? = null,
    val exportedUri: String? = null,
    val exportedFileName: String? = null,
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
