# M3 platform spike (throwaway)

A standalone Android app that measures alarm lateness and background audio tiers on real phones (`docs/07` M3, `docs/03` §7 and §12). It is **not** part of the main Gradle build, and it is not production code. Results and the test protocol are in `docs/spikes/M3-platform-findings.md`.

## Build

```bash
cd spike
./gradlew assembleT36Debug assembleT37Debug lintT36Debug lintT37Debug -q
```

- Needs an Android SDK with `platforms;android-37.0` and build-tools 36. Point to it with `spike/local.properties` (`sdk.dir=…`, not committed) or `ANDROID_HOME`.
- Outputs:
  - `app/build/outputs/apk/t36/debug/app-t36-debug.apk`: targetSdk 36, id `dev.babysim.spike.t36`.
  - `app/build/outputs/apk/t37/debug/app-t37-debug.apk`: targetSdk 37, id `dev.babysim.spike.t37`.
- Debug-signed, for sideloading only.

## What it does

- **Permissions** are exactly the production set: notifications, `SCHEDULE_EXACT_ALARM`, boot, and a `mediaPlayback` FGS. Never `USE_EXACT_ALARM`, full-screen intents or battery-exemption prompts.
- **Lateness runs** schedule an exact series (`setExactAndAllowWhileIdle`, or the inexact fallback when exact alarms are denied) and an inexact series. Every fire is logged with its lateness and the device state.
- **Alerts** post the `care_alert_v1` notification (T1, the system plays a short synthetic cry). They then try to start a `mediaPlayback` FGS that plays a looping cry for 60 s (T2) with `USAGE_MEDIA` or `USAGE_ALARM`. Each refusal, focus result and playing check is logged.
- **Monitor (T3)** is started from the foreground. It plays ambient sound in a `MediaSession` and logs battery every 15 min. Alerts during a session play inside it.
- **Boot** catches up, reschedules, and posts a notification only (no FGS from boot).
- **"Did you hear it?"** questions record what the tester actually heard.
- The **log** is a CSV in app storage. *Save log to a file…* writes it anywhere, such as Drive.

`tools/make_clips.py` regenerates the synthetic sounds in `app/src/main/res/raw`. `tools/analyze.py` turns logs into the findings tables. Candidate clips imported on a phone stay on that phone and are never committed.
