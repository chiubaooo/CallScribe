# CallScribe — Resume Anchor (Cross-Session Handoff)

> **Project Root**: `d:\antigravity project\CallScribe`
> **Primary Stack**: Android Native (Kotlin 2.2.10, AGP 9.0.1, Gradle 9.1.0, Compile/Target SDK 36, Min SDK 29, Jetpack Compose Material 3)
> **Test Device**: Google Pixel 10 Pro XL (`57131FDCQ003KL`)

## Decision Locks (Do NOT Re-Litigate Without User Request)
1. **Output Format**: Default to **`.txt`** (plain text). Do NOT force `.srt` subtitle output; users want clean text they can freely reuse.
2. **Audio Recording Invariant**: NEVER request `AudioFocus` or switch `AudioManager` to `MODE_IN_COMMUNICATION` / Bluetooth SCO. Always lock `MediaRecorder` to `AudioDeviceInfo.TYPE_BUILTIN_MIC` so Bluetooth music stays hi-res A2DP stereo.
3. **Database**: Pure `SQLiteOpenHelper` (`TranscriptDao.kt`) — do NOT re-add Room/KSP (avoids AGP 9.0 / Kotlin 2.2 KSP version conflicts and keeps builds under 30s).
4. **Gemini API**: Use `v1beta/interactions` with header `Api-Revision: 2026-05-20`, audio input object `{"type": "audio", "uri": ..., "mime_type": ...}`, and `DELETE /v1beta/{file_name}` in `finally`.
5. **Pricing Model (Planned Commercialization)**: `$2.99/mo` with `200 min/mo` cap (`~$1.00` max API cost -> `>=60%` margin); trial via 30-min free credit on Paid API (never shared Free Tier).

## Immediate Next Action
When the user plugs their Pixel back in via USB:
1. Run `gradle installDebug` from `d:\antigravity project\CallScribe\android` (now pure ASCII path!).
2. Test recording a 10-second spoken clip **while playing Bluetooth music** to verify zero audio distortion + clean `.txt` output in `Documents/CallTranscripts/`.
