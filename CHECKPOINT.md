# CallScribe — Project Checkpoint

> **Last Updated**: 2026-11-26
> **Current Phase**: Phase 1 — Android MVP Built & Verified on Pixel 10 Pro XL

## Completed Milestones
- [x] **Android Native MVP (`android/`)**: Kotlin + Jetpack Compose (Material You dynamic color), zero KSP/Room overhead (pure `SQLiteOpenHelper` + reactive `StateFlow`), `EncryptedSharedPreferences` for Gemini API key.
- [x] **3-Channel Audio Ingestion**:
  - Pixel 1-Tap Share Target (`ACTION_SEND` `audio/*`)
  - SAF Folder Auto-Watcher (`FolderScanWorker` + SHA-256 deduplication)
  - Built-in Foreground Recorder (`AudioRecordService`)
- [x] **Bluetooth A2DP Protection**: `setPreferredDevice(TYPE_BUILTIN_MIC)` without `AudioFocus` hijack — prevents Bluetooth music from degrading to HFP mono during recording.
- [x] **Gemini `v1beta/interactions` Pipeline**: Resumable Files API upload -> `gemini-3.5-transcribe` (`type: "audio"`, `diarization_mode: "speaker"`) -> `gemini-3.6-flash` zh-TW summary -> guaranteed `finally` remote file deletion.
- [x] **Graceful Empty-Speech Handling**: Detects `total_output_tokens == 0` and displays friendly message instead of raw JSON.
- [x] **Real-Device Verification**: Compiled (`BUILD SUCCESSFUL`) and installed on `Pixel 10 Pro XL - 17` (`57131FDCQ003KL`).
- [x] **Spun Off as Standalone Side Project**: Moved to pure-ASCII path `d:\antigravity project\CallScribe` (eliminates Gradle non-ASCII path warning).

## Pending / Next Up
- [ ] Re-install latest APK (`setPreferredDevice(TYPE_BUILTIN_MIC)` + default `.txt` export) onto Pixel and test recording while playing Bluetooth music.
- [ ] Add optional "Pure `.txt` only (skip AI summary)" toggle in Settings to cut latency & cost in half.
- [ ] Add video file import support (extract audio track locally on Android / Web before upload).
- [ ] Phase 2: Build minimal Web MVP (`web/`) for desktop users (drag video/audio -> extract audio in browser -> download `.txt`).
- [ ] Phase 3: Cross-brand Android presets (Samsung `Recordings/Call/`, Xiaomi `MIUI/sound_recorder/call_rec/`) + monetization backend (Cloudflare Worker proxy + Stripe/Play Billing).
