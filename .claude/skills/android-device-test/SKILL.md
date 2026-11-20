---
name: android-device-test
description: Get a BabyApp test build onto a real phone, run the M3 platform tests (alarms, notification sound, background playback, reboot, battery saver, Samsung sleeping apps, overnight monitor), collect the CSV logs and analyse them. Use when someone is about to install or test on a phone or emulator, sends test logs, or asks how to use adb for these tests.
---

# Testing on devices

## Who has what (2026-11)
- **Owner:** Windows PC with Android Studio "Rabbit 2". The PC lacks the RAM to run the emulator, so don't plan on emulator runs.
- **Reviewer:** Samsung Galaxy A54 5G. This is the real device for M3 (Samsung / One UI row).
- **This cloud session cannot reach either.** It has no PC folder shared, and Desktop Commander runs on this same Linux VM, not on the PC. Don't claim to run adb or PowerShell on their machine. Give exact commands for them to run instead.

## Getting the APK to the phone
1. Build (see the `android-build` skill), then copy to the scratchpad as `m3-spike-target37.apk` / `…36.apk`.
2. Upload to the owner's Drive folder **Baby Sim M3 spike** (find its id with `mcp__Google_Drive__search_files` by name; the repo is public, so ids stay out of it) with `mcp__Google_Drive__create_file`. Use `disableConversionToGoogleType: true` and `base64Content` as a `sha256:<hex>:<path>` reference. Replace the old files rather than piling up versions.
3. On the phone: Drive app → tap the APK → allow "install unknown apps" for Drive → if Play Protect warns, **More details → Install anyway**.
4. A drag-and-drop install error "could not parse error string" usually means a `.zip` was dragged, not the `.apk`. Real errors show with `adb install -r <apk>`.

## What the tester does
The app guides them step by step (`spike/.../Steps.kt`). They need no labels or settings knowledge.
- **target 37 app = main test:**
  - welcome, setup;
  - sound check;
  - alert at media volume, then at alarm volume;
  - alert with exact alarms off;
  - restart;
  - power saving;
  - Samsung sleeping apps, Samsung unrestricted;
  - overnight (night 1);
  - monitor (night 2);
  - clips (optional);
  - send results.
- **target 36 app = 10-minute extra test:** welcome, setup, sound check, both alerts, send results.
- They answer "did you hear it?" from the notification buttons or in the app, then **Save results** to Drive → `Baby Sim M3 spike/M3 logs`.

## Reading results
1. Find new CSVs in the M3 logs folder (`mcp__Google_Drive__search_files`), download with `mcp__Google_Drive__download_file_content`, and copy them to `docs/spikes/m3-logs/`.
2. `python3 spike/tools/analyze.py docs/spikes/m3-logs/*.csv` prints the tables.
   - Verdicts per tier come from the rules in `docs/spikes/M3-platform-findings.md` §2. Those were set before any results; don't move them after seeing data.
   - Run ids are `<step>~<time>`, so every row maps back to a test step. Condition labels are set by the step (`normal`, `exact-denied`, `reboot`, `battery-saver`, `samsung-sleeping`, `samsung-unrestricted`, `overnight`, `monitor`).
3. Fill in §4 (devices) and §5 (results) of the findings document, then commit the CSVs together with it.

## adb, for when a PC session or a USB-connected phone is available
| Purpose | Command |
|---|---|
| Force Doze | `adb shell dumpsys battery unplug` then `adb shell dumpsys deviceidle force-idle` (undo: `deviceidle unforce`, `battery reset`) |
| Rare standby bucket | `adb shell am set-standby-bucket dev.babysim.spike.t37 rare` |
| Exact alarms off | `adb shell appops set dev.babysim.spike.t37 SCHEDULE_EXACT_ALARM deny` (undo: `allow`) |
| Android 17 audio rules on Android 16 | `adb shell cmd audio set-enable-hardening enable` |
| Pull the log without the UI | `adb exec-out run-as dev.babysim.spike.t37 cat files/spike-log.csv > t37.csv` |
| Why an app closed | `adb logcat -d -s AndroidRuntime` |

On Windows, adb lives at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb`.

## Interpreting silence
When the tester hears nothing, check the log before blaming the app:
- `t2_check playing=true` with "not heard" points to the device: volume, DND, emulator audio.
- `t2_start_failed`, `fgs_failed` or `focus=failed` points to the platform refusing, which is a real finding.
