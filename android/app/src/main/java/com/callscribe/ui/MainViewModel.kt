package com.callscribe.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callscribe.CallScribeApp
import com.callscribe.data.AudioIngestionRepository
import com.callscribe.data.EnqueueResult
import com.callscribe.data.db.TranscriptEntity
import com.callscribe.worker.FolderScanWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CallScribeApp
    private val dao = app.database.transcriptDao()
    private val repo = AudioIngestionRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val transcripts: StateFlow<List<TranscriptEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) dao.observeAll() else dao.search(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.resumeStuckPendingJobs()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun observeTranscript(id: String) = dao.observeById(id)

    fun hasApiKey(): Boolean = app.preferences.geminiApiKey.isNotBlank()

    fun hasWatchedFolder(): Boolean = !app.preferences.watchedFolderUri.isNullOrBlank()

    fun ingestAudioUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = repo.enqueueFromUri(uri)) {
                is EnqueueResult.Enqueued ->
                    _snackbarMessage.tryEmit("已加入轉錄佇列：${res.fileName}")
                is EnqueueResult.Duplicate ->
                    _snackbarMessage.tryEmit("此音檔已轉錄過：${res.fileName}")
                is EnqueueResult.Error ->
                    _snackbarMessage.tryEmit(res.message)
            }
        }
    }

    fun retryJob(id: String) {
        viewModelScope.launch {
            repo.retryJob(id)
            _snackbarMessage.tryEmit("已重新開始轉錄")
        }
    }

    fun deleteTranscript(id: String) {
        viewModelScope.launch {
            dao.deleteById(id)
            _snackbarMessage.tryEmit("已刪除紀錄")
        }
    }

    fun triggerFolderScan() {
        if (!hasWatchedFolder()) {
            _snackbarMessage.tryEmit("尚未選擇監控資料夾，請先到設定頁指定")
            return
        }
        FolderScanWorker.scanNow(app)
        _snackbarMessage.tryEmit("正在掃描監控資料夾中的新音檔…")
    }
}
