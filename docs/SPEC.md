# CallScribe Android — Technical Architecture Specification

> **Target Device**: Google Pixel / Stock Android (API 33+ / Android 13–16)
> **Tech Stack**: Native Android — Kotlin, Jetpack Compose (Material 3), Room, WorkManager, OkHttp, Kotlinx Serialization
> **AI Engine**: Gemini API (`v1beta/interactions` — `gemini-3.5-transcribe` + `gemini-3.6-flash`)
> **Project Path**: `apps/call-scribe-android/`

---

## 1. Platform Reality & Ingestion Strategy (Pixel Constraints)

Android 9+ restricts `MediaRecorder.AudioSource.VOICE_CALL` to system-privileged apps. On **Google Pixel**, the built-in Google Phone app (`com.google.android.dialer`) records both sides clearly, but stores recordings inside its **app-private internal storage**, which third-party apps cannot read directly without root.

To solve this cleanly without fragile Accessibility hacks, CallScribe uses a **3-Channel Ingestion Architecture**:

| Channel | Mechanism | Use Case | Permission Needed |
| :--- | :--- | :--- | :--- |
| **1. Pixel 1-Tap Share (Primary)** | `Intent.ACTION_SEND` (`audio/*`) | Right after a Pixel call, tap **Share → CallScribe** in Google Phone history. Immediately enqueues background job. | None |
| **2. Folder Auto-Watcher (SAF)** | `ACTION_OPEN_DOCUMENT_TREE` + `WorkManager` | Auto-detects new audio files in a user-selected folder (e.g. `Recordings/`, exported Pixel Recorder, synced audio). | Scoped SAF persistent URI permission |
| **3. Built-in Recorder (Fallback)** | `ForegroundService` (`microphone`) + `MediaRecorder` (`M4A/AAC`, 16kHz, 64kbps mono) | Speakerphone calls, in-person meetings, quick voice memos. | `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MICROPHONE` |

---

## 2. System Architecture & Modules (Single-Module YAGNI)

```text
apps/call-scribe-android/
└── app/src/main/java/com/callscribe/
    ├── ui/
    │   ├── home/          # Transcript list, search bar, FAB (Record / Import)
    │   ├── detail/        # Summary, Action Items, Speaker Transcript, Share/Copy
    │   ├── recorder/      # Live waveform/timer recording sheet
    │   └── settings/      # API Key (encrypted), watched folder picker, output format (.md/.txt)
    ├── data/
    │   ├── db/            # Room DB: TranscriptEntity, TranscriptDao
    │   ├── api/           # GeminiFilesApi (resumable upload + delete), GeminiInteractionsApi
    │   ├── security/      # EncryptedSharedPreferences (Android Keystore) for Gemini API Key
    │   └── export/        # MediaStore writer -> Documents/CallTranscripts/
    ├── worker/
    │   ├── TranscribeWorker.kt       # CoroutineWorker: upload -> transcribe -> summarize -> export -> cleanup
    │   └── FolderScanWorker.kt       # PeriodicWorkRequest (15m) + on-app-open SAF folder scan
    └── service/
        └── AudioRecordService.kt     # Foreground service for reliable screen-off recording
```

---

## 3. Data Model & State Machine

### Room Entity: `TranscriptEntity`
- `id: String` (UUID)
- `contentHash: String` (Unique index — SHA-256 of audio bytes for strict deduplication)
- `sourceUri: String` (Original file URI or local recorded path)
- `fileName: String`
- `durationMs: Long`
- `status: JobStatus`
- `errorMessage: String?`
- `title: String?` (AI-generated concise zh-TW title)
- `summaryMarkdown: String?` (Bullet points + action items)
- `transcriptMarkdown: String?` (Timestamped speaker-diarized verbatim text)
- `exportedUri: String?` (`MediaStore` URI in `Documents/CallTranscripts/`)
- `createdAt: Long`

### Job State Machine (Strict Transitions)
```text
QUEUED -> UPLOADING -> TRANSCRIBING -> SUMMARIZING -> COMPLETED
   \          \             \              \
    +----------+-------------+--------------+---> FAILED (user-retryable)
```
- **Terminal Guard**: `COMPLETED` jobs are immutable and never re-processed.
- **Deduplication**: Before enqueueing, compute `SHA-256` of the audio stream. If `contentHash` already exists in Room DB (not `FAILED`), skip silently to prevent duplicate API billing.

---

## 4. Gemini API Pipeline (`v1beta`)

1. **Upload Audio (Files API)**:
   - `POST https://generativelanguage.googleapis.com/upload/v1beta/files` (resumable protocol).
   - Supports `.m4a`, `.mp3`, `.wav`, `.ogg`, `.amr`, `.3gp` (covers all Android/Pixel call formats).
   - Poll `GET /v1beta/{file_name}` until `state == "ACTIVE"`.
2. **Step 1 — Verbatim Transcription + Speaker Diarization**:
   - `POST https://generativelanguage.googleapis.com/v1beta/interactions`
   - `model`: `"models/gemini-3.5-transcribe"`
   - `generation_config.transcription_config.mode`: `{ "type": "verbatim", "diarization_mode": "speaker" }`
3. **Step 2 — Traditional Chinese Summary & Action Items**:
   - `POST https://generativelanguage.googleapis.com/v1beta/interactions`
   - `model`: `"gemini-3.6-flash"`
   - Prompt instructs Taiwan Traditional Chinese (`繁體中文（台灣用語）`), structured output:
     - 1-line title
     - 3–5 bullet summary
     - Action items checklist (`- [ ] ...`)
     - Cleaned speaker-labeled transcript
4. **Guaranteed Privacy Cleanup**:
   - `finally` block executes `DELETE https://generativelanguage.googleapis.com/v1beta/{file_name}` immediately after transcription finishes (success or failure), so private call audio does not linger on cloud storage.

---

## 5. Export File Format (`Documents/CallTranscripts/`)

Filename: `YYYY-MM-DD_HHmm_<AI標題>.md` (or `.txt` configurable in Settings)

```markdown
# 2026-11-26 14:30 — 客戶報價確認通話

## 重點摘要
- 確認第一階段開發報價與交付時程
- 對方同意週五前提供素材

## 待辦事項
- [ ] 週四中午寄出正式報價單
- [ ] 確認對方聯繫窗口 Email

## 逐字稿
**[00:00] 講者 1**：喂你好，請問是陳先生嗎？
**[00:03] 講者 2**：對，你好，關於昨天寄的報價單...
```

---

## 6. Security, Privacy & Cost Controls

- **Key Storage**: Gemini API Key stored exclusively in `EncryptedSharedPreferences` (`AES256_GCM` master key in Android Keystore). Never logged, never committed.
- **No Unbounded Background Polling**: `FolderScanWorker` uses WorkManager constraints (`BatteryNotLow`) and only runs when the user explicitly selects a watched folder.
- **Cost Guard**:
  - Rejects files > 100 MB (configurable) with a clear confirmation dialog.
  - Optional "Wi-Fi Only" toggle for background uploads.
- **Local Privacy**: All audio stays on-device except during transient API upload; remote file deleted immediately in `finally`.

---

## 7. Open Decisions Before Implementation

1. **Build Environment**: Will you compile the APK via **Android Studio** on this Windows PC, or do you want a **GitHub Actions workflow** that automatically builds & attaches the installable `.apk` on every push (zero local Android SDK setup needed)?
2. **UI Visual Style**: Standard clean Material You (Pixel native dynamic color) vs. Dark Minimalist Studio theme?
