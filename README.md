# CallScribe

輕量、隱私優先的語音 / 通話 / 會議轉 `.txt` 逐字稿工具。

## 核心特色
- **邊聽音樂不破音**：強制鎖定手機本體麥克風錄音（`TYPE_BUILTIN_MIC`），不搶音訊焦點、不觸發藍牙 HFP 降質，戴藍牙耳機聽歌也能背景安靜錄音。
- **純淨 `.txt` 萬用輸出**：自動存檔至 `Documents/CallTranscripts/`，愛貼去剪輯軟體、Notion、ChatGPT 都隨你用。
- **三軌輸入**：Pixel 電話錄音一鍵分享、資料夾自動監控（SAF + SHA-256 防重複計費）、App 內建前景錄音。
- **隱私優先**：Gemini API Key 加密存於本機 Android Keystore，轉錄完成立刻刪除雲端暫存音檔。

## 快速開始
用 Android Studio 開啟 `android/` 資料夾即可編譯安裝；詳細指令見 [DEVELOPMENT_OS.md](DEVELOPMENT_OS.md)。
