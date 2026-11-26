# CallScribe — Development OS & Commands

## Environment (Windows)
- **JDK**: Android Studio bundled JBR (`C:\Program Files\Android\Android Studio\jbr`)
- **Android SDK**: `C:\Users\User\AppData\Local\Android\Sdk` (Platform `android-36`)
- **Gradle**: Cached `gradle-9.1.0` (`C:\Users\User\.gradle\wrapper\dists\gradle-9.1.0-bin\9agqghryom9wkf8r80qlhnts3\gradle-9.1.0\bin\gradle.bat`)
- **ADB**: `C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Build & Deploy Commands (PowerShell)

### Compile Debug APK (No phone needed)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location "d:\antigravity project\CallScribe\android"
& "C:\Users\User\.gradle\wrapper\dists\gradle-9.1.0-bin\9agqghryom9wkf8r80qlhnts3\gradle-9.1.0\bin\gradle.bat" assembleDebug --no-daemon
```

### Install & Launch on Connected Pixel
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location "d:\antigravity project\CallScribe\android"
& "C:\Users\User\.gradle\wrapper\dists\gradle-9.1.0-bin\9agqghryom9wkf8r80qlhnts3\gradle-9.1.0\bin\gradle.bat" installDebug --no-daemon
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.callscribe/.MainActivity
```

## Security Rules
- NEVER commit `local.properties`, `*.keystore`, or any Gemini API keys (`AQ.*` / `AIza*`).
- API keys on device live exclusively in `EncryptedSharedPreferences` (Android Keystore AES256_GCM).
