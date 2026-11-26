package com.callscribe.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.callscribe.CallScribeApp
import com.callscribe.data.db.JobStatus
import com.callscribe.data.db.TranscriptEntity
import com.callscribe.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    transcriptId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CallScribeApp
    val item by viewModel.observeTranscript(transcriptId).collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val shareableText = remember(item) {
        item?.let { entity ->
            app.exporter.buildDocumentContent(
                title = entity.title ?: entity.fileName,
                createdAt = entity.createdAt,
                sourceFileName = entity.fileName,
                summaryMarkdown = entity.summaryMarkdown.orEmpty(),
                actionItemsMarkdown = entity.actionItemsMarkdown.orEmpty(),
                transcriptText = entity.transcriptText.orEmpty(),
                format = app.preferences.exportFormat
            )
        }.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "轉錄詳情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (item?.status == JobStatus.COMPLETED) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("CallScribe", shareableText))
                            scope.launch { snackbarHostState.showSnackbar("已複製完整摘要與逐字稿") }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "複製")
                        }
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, item?.title ?: "通話逐字稿")
                                putExtra(Intent.EXTRA_TEXT, shareableText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "分享逐字稿"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
                    IconButton(onClick = {
                        viewModel.deleteTranscript(transcriptId)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val current = item
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DetailStatusBanner(current = current, onRetry = { viewModel.retryJob(current.id) })

            if (current.status == JobStatus.COMPLETED) {
                if (!current.exportedFileName.isNullOrBlank()) {
                    Text(
                        text = "已存檔：Documents/CallTranscripts/${current.exportedFileName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!current.summaryMarkdown.isNullOrBlank()) {
                    SectionCard(
                        heading = "重點摘要",
                        body = current.summaryMarkdown
                    )
                }

                if (!current.actionItemsMarkdown.isNullOrBlank()) {
                    SectionCard(
                        heading = "待辦事項",
                        body = current.actionItemsMarkdown
                    )
                }

                SectionCard(
                    heading = "完整逐字稿",
                    body = current.transcriptText.orEmpty().ifBlank { "（無內容）" }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailStatusBanner(
    current: TranscriptEntity,
    onRetry: () -> Unit
) {
    when (current.status) {
        JobStatus.COMPLETED -> Unit
        JobStatus.FAILED -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("轉錄失敗", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = current.errorMessage ?: "未知錯誤",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(" 重新轉錄")
                    }
                }
            }
        }
        else -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (current.status) {
                            JobStatus.QUEUED -> "排隊等待處理中…"
                            JobStatus.UPLOADING -> "正在上傳音檔至 Gemini…"
                            JobStatus.TRANSCRIBING -> "正在語音轉錄與區分講者…"
                            JobStatus.SUMMARIZING -> "正在生成繁體中文摘要與待辦…"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(heading: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
