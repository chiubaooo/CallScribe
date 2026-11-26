# CallScribe — Pixel 通話轉錄文字檔 App (Kotlin + Jetpack Compose)

## 功能總覽
- **Pixel 1-Tap 分享轉錄**：通話結束後在 Google 電話 App 點「分享 → CallScribe」，背景自動轉錄。
- **資料夾自動監控 (SAF)**：可選用監控任何公開錄音資料夾，自動偵測新音檔並以 SHA-256 防重複計費。
- **App 內建錄音**：前景錄音服務（M4A/AAC），錄完自動排入轉錄。
- **Gemini 雙模型管線**：
  - `gemini-3.5-transcribe`：逐字轉錄＋講者區分 (`diarization_mode: speaker`)
  - `gemini-3.6-flash`：台灣繁體中文標題、重點摘要與待辦事項
  - 轉錄結束後自動刪除雲端暫存音檔（`DELETE /v1beta/files/...`）。
- **自動匯出文字檔**：存至手機 `Documents/CallTranscripts/YYYY-MM-DD_HHmm_標題.md`（可於設定切換 `.txt`）。

## 安裝到 Google Pixel 步驟
1. 用 Android Studio 開啟此資料夾：`d:\antigravity project\第二大腦\apps\call-scribe-android`。
2. 等待 Gradle Sync 完成。
3. 在 Pixel 手機開啟「設定 → 關於手機 → 連點版本號碼 7 次」開啟開發人員選項 → 進入「系統 → 開發人員選項 → 開啟 **USB 偵錯**」。
4. 現在可以把 Pixel 用 USB 接到電腦，手機跳出授權視窗按「一律允許」。
5. 在 Android Studio 上方裝置列選你的 Pixel，按綠色 **Run (▶)** 直接安裝啟動。
6. 打開 App → 右上角「設定」→ 貼入你的 Gemini API Key（加密儲存於 Android Keystore）即可使用。
