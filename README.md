# CallScribe

**Record meetings & calls while listening to Bluetooth music without audio distortion — and get clean `.txt` transcripts in ~4 seconds.**

**戴藍牙耳機邊聽音樂也能零破音錄音，4 秒直出純淨 `.txt` 繁簡逐字稿的開源 Android App。**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
![Platform: Android](https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84.svg)
![Tech: Kotlin + Jetpack Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)

---

## Why CallScribe? / 為什麼做這個專案？

When you record audio with ChatGPT or typical voice apps while wearing Bluetooth earbuds, Android switches Bluetooth from hi-res stereo (**A2DP**) into 16 kHz mono call mode (**HFP/SCO**) — instantly turning your music into distorted garbage.

**CallScribe solves this cleanly:**
- **Zero Bluetooth Music Distortion（邊聽歌不破音）**: Locks `MediaRecorder` to the phone's built-in mic (`AudioDeviceInfo.TYPE_BUILTIN_MIC`) without hijacking `AudioFocus` or Bluetooth SCO. Your Spotify / YouTube Music stays crystal-clear stereo while meetings record silently in the background.
- **~4-Second Fast Path（極速轉錄）**: Sends audio `< 15 MB` inline directly to Gemini `v1beta/interactions` (`gemini-3.5-transcribe`), skipping slow upload/polling loops.
- **Universal `.txt` Output（純淨萬用文字檔）**: Saves directly to `Documents/CallTranscripts/` so you can paste freely into Premiere/CapCut, Notion, ChatGPT, or Obsidian. Optional `.md` + AI Summary toggle in Settings.
- **Traditional / Simplified Chinese Toggle（繁體／簡體自由切換）**: Instant zero-latency conversion via Android's built-in ICU `Transliterator`, plus native English/Chinese code-switching support.
- **3-Channel Ingestion（三軌輸入）**:
  1. **Pixel 1-Tap Share**: Share built-in Google Phone call recordings straight to CallScribe (`ACTION_SEND`).
  2. **SAF Folder Auto-Watcher**: Watch any recording folder with SHA-256 deduplication (never billed twice).
  3. **Built-in Foreground Recorder**: Screen-off reliable recording (`M4A/AAC`, 16 kHz mono).
- **Privacy First（隱私優先）**: Bring your own Gemini API key (encrypted in Android Keystore `EncryptedSharedPreferences`). Large uploaded files are explicitly deleted (`DELETE /v1beta/files/...`) immediately in a `finally` block.

---

## Quick Start / 快速開始

1. Clone this repo and open `android/` in **Android Studio** (or build via Gradle 9.1+ / JDK 17).
2. Connect your Android 10+ phone (USB Debugging enabled) and run:
   ```powershell
   ./gradlew installDebug
   ```
3. Open **CallScribe** → **Settings (⚙)** → paste your **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/apikey) → Save.
4. Tap **開始錄音 (Start Recording)** or share any call recording to CallScribe!

---

## Architecture & Roadmap / 架構與路線圖

- `android/` — Native Android app (Kotlin, Jetpack Compose Material 3, pure `SQLiteOpenHelper`, `WorkManager`, OkHttp)
- `docs/SPEC.md` — Technical architecture specification
- `web/` *(Planned)* — Browser drag-and-drop video/audio → `.txt` converter

---

## License / 授權條款

Licensed under **GNU AGPLv3** ([LICENSE](LICENSE)). Free to use, study, and contribute! If you want to embed CallScribe into a closed-source commercial product or hosted service, please reach out for a commercial license.
