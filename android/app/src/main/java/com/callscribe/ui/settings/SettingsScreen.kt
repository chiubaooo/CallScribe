package com.callscribe.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.callscribe.CallScribeApp
import com.callscribe.data.security.ExportFormat
import com.callscribe.worker.FolderScanWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CallScribeApp
    val prefs = app.preferences

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var watchedFolder by remember { mutableStateOf(prefs.watchedFolderUri) }
    var exportFormat by remember { mutableStateOf(prefs.exportFormat) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnlyUpload) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            prefs.watchedFolderUri = uri.toString()
            watchedFolder = uri.toString()
            FolderScanWorker.schedulePeriodicScan(context)
            FolderScanWorker.scanNow(context)
            scope.launch { snackbarHostState.showSnackbar("已啟用資料夾自動監控") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Gemini API 金鑰（加密儲存於本機 Keystore）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "顯示或隱藏"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    prefs.geminiApiKey = apiKey
                    scope.launch { snackbarHostState.showSnackbar("API Key 已加密儲存") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("儲存 API Key")
            }

            Text("匯出文字檔格式（Documents/CallTranscripts/）", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = exportFormat == ExportFormat.MARKDOWN,
                    onClick = {
                        exportFormat = ExportFormat.MARKDOWN
                        prefs.exportFormat = ExportFormat.MARKDOWN
                    },
                    label = { Text("Markdown (.md)") }
                )
                FilterChip(
                    selected = exportFormat == ExportFormat.PLAIN_TEXT,
                    onClick = {
                        exportFormat = ExportFormat.PLAIN_TEXT
                        prefs.exportFormat = ExportFormat.PLAIN_TEXT
                    },
                    label = { Text("純文字 (.txt)") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("僅在 Wi-Fi 下背景上傳", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "開啟後，行動網路下會先暫存排隊，連上 Wi-Fi 再自動轉錄",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        prefs.wifiOnlyUpload = it
                    }
                )
            }

            Text("自動監控錄音資料夾（選用）", style = MaterialTheme.typography.titleSmall)
            Text(
                text = watchedFolder ?: "尚未指定（目前可使用 Pixel 分享按鈕或 App 內建錄音）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Text("選擇資料夾")
                }
                if (watchedFolder != null) {
                    OutlinedButton(onClick = {
                        prefs.watchedFolderUri = null
                        watchedFolder = null
                        FolderScanWorker.cancelPeriodicScan(context)
                        scope.launch { snackbarHostState.showSnackbar("已停用資料夾監控") }
                    }) {
                        Text("取消監控")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Google Pixel 最佳使用流程", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. 通話時按 Pixel 內建「錄音」鈕（雙向人聲最清晰）。\n" +
                            "2. 掛斷後在「電話」App 通話紀錄點開錄音 → 按「分享」→ 選 CallScribe。\n" +
                            "3. 背景自動完成講者區分與摘要，存檔到 Documents/CallTranscripts/ 並立刻刪除雲端暫存音檔。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
