package com.callscribe.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.callscribe.data.security.ExportFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedFileResult(
    val uriString: String,
    val fileName: String
)

class TranscriptExporter(private val context: Context) {

    fun buildDocumentContent(
        title: String,
        createdAt: Long,
        sourceFileName: String,
        summaryMarkdown: String,
        actionItemsMarkdown: String,
        transcriptText: String,
        format: ExportFormat
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(Date(createdAt))
        return when (format) {
            ExportFormat.MARKDOWN -> buildString {
                appendLine("# $timestamp — $title")
                appendLine()
                appendLine("> 來源音檔：`$sourceFileName`")
                appendLine()
                appendLine("## 重點摘要")
                appendLine(summaryMarkdown.ifBlank { "- （無摘要）" })
                appendLine()
                appendLine("## 待辦事項")
                appendLine(actionItemsMarkdown.ifBlank { "- [ ] 無明確待辦事項" })
                appendLine()
                appendLine("## 逐字稿")
                appendLine(transcriptText)
            }

            ExportFormat.PLAIN_TEXT -> buildString {
                if (summaryMarkdown.isBlank() && actionItemsMarkdown.isBlank()) {
                    appendLine(transcriptText)
                } else {
                    appendLine("標題：$title")
                    appendLine("時間：$timestamp")
                    appendLine("來源：$sourceFileName")
                    appendLine("=".repeat(36))
                    if (summaryMarkdown.isNotBlank()) {
                        appendLine("[重點摘要]")
                        appendLine(summaryMarkdown)
                        appendLine()
                    }
                    if (actionItemsMarkdown.isNotBlank()) {
                        appendLine("[待辦事項]")
                        appendLine(actionItemsMarkdown)
                        appendLine()
                    }
                    appendLine("[逐字稿]")
                    appendLine(transcriptText)
                }
            }
        }
    }

    fun exportToDocuments(
        title: String,
        createdAt: Long,
        sourceFileName: String,
        summaryMarkdown: String,
        actionItemsMarkdown: String,
        transcriptText: String,
        format: ExportFormat
    ): ExportedFileResult {
        val datePrefix = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(createdAt))
        val safeTitle = sanitizeFileName(title).ifBlank { "通話紀錄" }.take(32)
        val fileName = "${datePrefix}_${safeTitle}.${format.extension}"
        val content = buildDocumentContent(
            title = title,
            createdAt = createdAt,
            sourceFileName = sourceFileName,
            summaryMarkdown = summaryMarkdown,
            actionItemsMarkdown = actionItemsMarkdown,
            transcriptText = transcriptText,
            format = format
        )

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOCUMENTS}/CallTranscripts"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("Unable to create export file in Documents/CallTranscripts")

        resolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for $uri")

        val doneValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, doneValues, null, null)

        return ExportedFileResult(uriString = uri.toString(), fileName = fileName)
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
}
