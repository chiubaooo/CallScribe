# CallScribe — Architecture & Product Blueprint

## 1. Product Vision (Side Project)
**CallScribe** is a lightweight, privacy-first audio/call/meeting transcription tool that turns voice recordings, phone calls, and video/audio files into clean **`.txt`** files users can freely use anywhere (Premiere/CapCut script matching, Notion, ChatGPT, archival).

### Core Differentiators (Validated Pain Points)
1. **Zero Bluetooth Audio Degradation ("邊聽歌不破音")**: Unlike ChatGPT / voice-call apps that hijack `AudioFocus` and force Bluetooth earbuds from A2DP stereo into 16kHz HFP mono garbage, CallScribe locks recording to the phone's built-in mic (`AudioDeviceInfo.TYPE_BUILTIN_MIC`) without requesting `AudioFocus` or Bluetooth SCO.
2. **Pure `.txt` Universal Output**: Gives users clean plain text (`.txt`, optional `.md` / AI summary) instead of brittle `.srt` timestamps, maximizing flexibility across editors, office workers, students, and salespeople.
3. **Code-Switching Friendly**: Powered by Gemini `v1beta/interactions` (`gemini-3.5-transcribe` + `gemini-3.6-flash`), handling mixed-language speech (e.g. Mandarin/Taiwanese/English and global code-switching).

---

## 2. Repository Structure
```text
d:\antigravity project\CallScribe\
├── ARCHITECTURE.md          # System architecture & unit economics
├── CHECKPOINT.md            # Live progress & milestones
├── RESUME_ANCHOR.md         # Zero-amnesia cross-session handoff
├── DEVELOPMENT_OS.md        # Build, deploy, and testing commands
├── README.md                # Quick start guide
├── .gitignore
├── docs/
│   └── SPEC.md              # Original Android Pixel technical specification
├── android/                 # Native Android App (Kotlin + Jetpack Compose + Material 3)
│   └── app/src/main/java/com/callscribe/
│       ├── data/            # SQLite store, GeminiApiClient, AudioIngestionRepository, TranscriptExporter, AppPreferences
│       ├── service/         # AudioRecordService (built-in mic lock, foreground service)
│       ├── worker/          # TranscribeWorker, FolderScanWorker (SAF)
│       └── ui/              # HomeScreen, DetailScreen, SettingsScreen, MainViewModel
└── web/                     # (Planned Phase 2) Browser drag-and-drop video/audio -> .txt MVP
```

---

## 3. Unit Economics & Monetization Guardrails
- **API Cost (`gemini-3.5-transcribe` Paid Tier)**: `~$0.005 / min` (`$0.30 / hour`).
- **Target Price**: `$2.99 / month` (~`$2.50` net after payment/store fees).
- **Recommended Monthly Quota**: `200 min / month` (max API cost `$1.00`, guaranteed `>= 60%` gross margin even at 100% utilization).
- **Trial Policy**: Avoid Google Free Tier shared pool (rate limits + training TOS); instead use Paid Tier with a **30-minute free trial credit** (`$0.15` max CAC per trial user).
- **Privacy Invariant**: Always delete uploaded audio from Gemini Files API (`DELETE /v1beta/files/{name}`) in a `finally` block immediately after transcription.
