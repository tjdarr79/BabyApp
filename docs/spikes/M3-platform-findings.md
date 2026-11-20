# M3 — Platform spike findings

**Status:** spike app built and lint-clean. **Waiting for runs on real phones.** No results yet, so no go/no-go yet.
**Milestone:** `07` M3 (approved 2026-11-19, `01` A3). **Questions it answers:** `03` §7 tiers T1–T3 and the §12 test matrix.
**Code:** `spike/`. This is a throwaway standalone Gradle project, not part of the main build. No production code.

## 1. What this spike decides

| # | Question (from `03`) | Measured by |
|---|---|---|
| Q1 | How late do exact and inexact (exact-denied fallback) alarms fire with the screen off, in Doze, overnight? | Alarm lateness runs (§3, tests B, D) |
| Q2 | Is the care-alert **notification sound** (T1) audible in the background under target 36 and 37? `03` §7 infers yes; unverified. | Tests A, C, F |
| Q3 | Can an exact alarm start **mediaPlayback playback** (T2) in the background? Does it need `USAGE_ALARM` under target 37 (Android 17 while-in-use rule)? | Test C, both usages, both targets |
| Q4 | After a reboot, do we catch up and post the notification (no FGS from boot)? | Test E |
| Q5 | What do battery saver and OEM killers (Samsung sleeping apps, Xiaomi autostart) do to Q1–Q3? | Tests F, G |
| Q6 | What does an 8 h **monitor session** (T3) cost in battery, and do cries play inside it? | Test H |
| Q7 | How do candidate clips (`08`) sound on a phone speaker? | Clip ratings |

## 2. Pass criteria (set before any results)

| Item | GO | PARTIAL | NO-GO |
|---|---|---|---|
| Exact alarm lateness (screen off, Doze) | p90 ≤ 60 s, none missed | p90 ≤ 5 min (the app's self-check threshold, `03` §9) | p90 > 5 min or any missed |
| Inexact fallback lateness | p90 ≤ 15 min | p90 ≤ 60 min | > 60 min or missed |
| T1 / T2 tier (per device × target × usage) | Every attempt started; ≥ 90% of answers "clear"/"heard-poor" | Some refusals, or 50–90% heard | Refused every time, or < 50% heard |
| T3 battery | ≤ 3 %/h unplugged | 3–6 %/h | > 6 %/h |

The tier rules are coded in `spike/tools/analyze.py`. Lateness thresholds are applied by hand from its table.

## 3. How to run it (per phone)

> **Since build m3-2 (2026-11-20) the app guides the tester through all of this itself.** It's a Compose + Material 3 screen, one step at a time in plain language. Permissions are fixed inline, condition labels are set automatically, "did you hear it?" can be answered from the notification, and a final step saves the results to Drive. The table below is the reference for what each step measures.
>
> | App step | Test below | Condition label |
> |---|---|---|
> | Sound check | A | `normal` |
> | Alert while the phone is locked / Same test, alarm volume | C (media / alarm) | `normal` |
> | Alert without the alarm permission | C2 | `exact-denied` |
> | Restart the phone | E | `reboot` |
> | Power saving mode | F | `battery-saver` |
> | Samsung: sleeping apps / unrestricted battery (Samsung only) | G | `samsung-sleeping` / `samsung-unrestricted` |
> | Overnight test (night 1) | D | `overnight` |
> | Baby monitor night (night 2) | H | `monitor` |
> | Real baby sounds (optional) | Clips | — |
>
> The **target 36 app** ("Baby Sim extra test") runs only the sound check and the two alerts, about 10 minutes.

**Install.** Install both APKs: `M3 spike · target 36` and `M3 spike · target 37`. They install side by side. Open each one:
1. Tap **Allow notifications**.
2. Tap **Exact alarm permission** and turn it on. It starts *denied* on Android 14+ (`03` §2).
3. Set the condition label to `normal`.

| Test | Steps | Time |
|---|---|---|
| **A. Sound check** | In each app: **Play the care alert sound now** (T1). Then **Quick check: 3 alarms**, lock the phone, wait 8 min, then reopen and answer the questions. | 10 min |
| **B. Lateness** | **One alarm in 30 min**, lock the phone, leave it. | 30 min |
| **C. Background alert** | For each app × each usage (`media`, `alarm`): pick the usage, tap **Alert in 2 min (exact)**, **swipe the app away from Recents**, lock the phone. When it cries, listen, then open the app and answer. That is 4 runs. | 30 min |
| **C2. Exact denied** | In the target-37 app, revoke exact alarms, tap **Alert in 15 min (inexact)**, swipe away and lock. Answer afterwards, then re-grant exact alarms. | 20 min |
| **D. Overnight lateness** | Unplugged, in both apps: **Overnight: every 20 min for 10 h**. Leave the phone face down. Answer nothing; just save the log in the morning. | 1 night |
| **E. Reboot** | **One alarm in 30 min**, then restart the phone right away. Unlock once. Open the app after 40 min and answer the T1 question. | 40 min |
| **F. Battery saver** | Label `battery-saver`, turn on battery saver, then repeat B and one C run (`alarm` usage, target 37). | 45 min |
| **G. OEM restrictions** (Samsung / Xiaomi only) | Label `samsung-sleeping` (put the app in Sleeping apps) or `xiaomi-autostart-off`, then repeat B and one C run. | 45 min |
| **H. Monitor (T3)** | Another night, in the target-37 app: **Start monitor**, then **Alert in 15 min (exact)**, unplugged. In the morning, **Stop monitor** and answer. | 1 night |
| **Clips** | Import 3–5 candidate clips (`08`). For each: **Play selected clip here**, then rate it 1–5. Select the best one before running C again. | 15 min |

**Send the logs.** In each app, tap **Save log to a file…**, choose Google Drive, and use a folder such as `Baby Sim/M3 logs`. Then tell Claude. The logs are committed under `docs/spikes/m3-logs/` and analysed with `python3 spike/tools/analyze.py docs/spikes/m3-logs/*.csv`.

### 3a. Emulator smoke test first (owner's PC, Android Studio)

The emulator checks that the app works: permission flows, alarms firing, the FGS refused or allowed under target 37, logging and export. It does **not** stand in for a phone. Its lateness numbers and OEM behavior don't count toward the verdicts; rows are labelled `emulator-…`.

1. **Device Manager:** create a phone with an **API 37 (Android 17)** image. Add an **API 36** one later if you have time.
2. **Install:** drag both APKs from the Drive folder onto the emulator window, or `adb install -r m3-spike-target37.apk` (same for 36).
3. **In each app:** Allow notifications, grant exact alarms, label `emulator-normal`. Run **test A**, then **test C** for both usages. Use the emulator's power button to lock it after swiping the app away.
4. **Force Doze** instead of waiting a night. Label `emulator-doze`, tap **Quick check** and **Alert in 2 min**, lock, then:
   `adb shell dumpsys battery unplug`
   `adb shell dumpsys deviceidle force-idle`
   Wait 8 min, then undo with `adb shell dumpsys deviceidle unforce` and `adb shell dumpsys battery reset`.
5. **Rare standby bucket:** label `emulator-bucket-rare`, then `adb shell am set-standby-bucket dev.babysim.spike.t37 rare` and repeat step 4's runs.
6. **Exact denied:** `adb shell appops set dev.babysim.spike.t37 SCHEDULE_EXACT_ALARM deny`, then test C2. Undo with `allow`.
7. **Reboot:** test E with `adb reboot`.
8. **Android 17 rules on the API 36 image:** `adb shell cmd audio set-enable-hardening enable`, label `emulator-hardening-on`, then repeat test C.
9. **Get the logs:** `adb exec-out run-as dev.babysim.spike.t37 cat files/spike-log.csv > emulator-t37.csv` (same for `t36`). Put the files in Drive `Baby Sim M3 spike/M3 logs`.

### 3b. Samsung notes (Galaxy A54 5G)

- Check the Android version first: Settings → About phone → Software information. The log records it too.
- Run the full list in §3 with the **default** battery settings first (label `normal`). That is what most users will have.
- **Test G on Samsung:** Settings → Battery → Background usage limits → add the app to **Sleeping apps** (label `samsung-sleeping`), then **Deep sleeping apps** (label `samsung-deep-sleeping`). Afterwards, set the app's battery to **Unrestricted** (app info → Battery) and repeat once (label `samsung-unrestricted`). That shows whether asking users to change it is worth it.
- Turn off "Put unused apps to sleep" only for the `samsung-unrestricted` run.

**Optional, with a computer and adb.** `adb shell cmd audio set-enable-hardening enable` makes an Android 16 phone enforce the Android 17 audio rules (`03` §7). Label such runs `hardening-on`.

## 4. Devices

`03` §12 asks for a Pixel (Android 16 and 17), a Samsung, a Xiaomi/Redmi and a low-end Android 10–12 phone. Any phone that isn't tested is reported as **not tested**, and its tier verdict stays open.

| Device | Android | Role | Tests run | Logs |
|---|---|---|---|---|
| Android Studio emulator (owner's PC) | API 37 | Smoke test only (§3a) | **Dropped 2026-11-20**: the PC lacks the RAM to run it. The app was installed, but nothing was heard and no log was pulled, so no result | — |
| Samsung Galaxy A54 5G (reviewer's phone) | to record | Samsung / One UI row of the matrix, and now also the first run of the app | — | — |
| Pixel (Android 16/17) | — | not available | — | — |
| Xiaomi/Redmi (HyperOS) | — | not available | — | — |
| Low-end Android 10–12 | — | not available | — | — |

## 5. Results

_Not yet run._ This section is replaced with the output of `spike/tools/analyze.py`, followed by the verdicts below.

| Decision | Verdict |
|---|---|
| T1 notification sound, target 36 / 37 | open |
| T2 usage for production: `USAGE_MEDIA` or `USAGE_ALARM` | open |
| T2 under target 37 | open |
| T3 opt-in monitor: offer in v1? | open |
| Exact-alarm prompt wording: how much worse is the inexact fallback? | open |

## 6. Build notes for M4

- The spike builds with **AGP 9.5.1 + Gradle 9.9.2**, compileSdk 37 (`platforms;android-37.0`), build-tools 36.0.0/36.1.0, minSdk 26.
- AGP 9 needs Gradle 9. The main build is on Gradle 8.14.3 (`CLAUDE.md`), so M4 must either upgrade the main wrapper to Gradle 9.x or stay on AGP 8.13.x, which only supports up to compileSdk 36. Recommendation: upgrade to Gradle 9 at the start of M4 and re-check the detekt and ktlint plugin versions.
- Google's Maven and SDK host (`dl.google.com`) is allowed in the cloud environment "Claude dev (Android SDK access)" since 2026-11-19.
