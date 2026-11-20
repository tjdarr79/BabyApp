---
name: android-build
description: Build, lint and screenshot-test the BabyApp Android code (the M3 spike in spike/ now, the :app module from M4). Use whenever you compile an APK, add an Android dependency, hit a Gradle/AGP/SDK error, or need to see what a screen looks like without an emulator.
---

# Building BabyApp's Android code

## Where things are
- `spike/` is a **standalone** Gradle project (M3 throwaway). It is not in the root build. Run Gradle from inside `spike/`.
- Root project (`:engine`, `:harness`) is Gradle 8.14.3. The spike uses **Gradle 9.9.2 + AGP 9.5.1** (AGP 9 needs Gradle 9). Decide the root upgrade at the start of M4 (`docs/spikes/M3-platform-findings.md` §6).
- Android SDK: `/opt/android-sdk`, with platforms 36, 37.0 and 37.2 and build-tools 36.0.0/36.1.0. `spike/local.properties` (not committed) holds `sdk.dir`.
  - The cloud environment "Claude dev (Android SDK access)" installs it in its setup script.
  - If it's missing, re-run that script's commands.

## Commands (quiet flags, from `spike/`)
| Purpose | Command |
|---|---|
| Both APKs | `./gradlew assembleT36Debug assembleT37Debug -q` |
| Lint (must be 0 errors) | `./gradlew lintT36Debug lintT37Debug -q`, then `grep -c 'Error:' app/build/reports/lint-results-t37Debug.txt` |
| Screenshot tests (render screens on the JVM) | `./gradlew testT37DebugUnitTest -q`. PNGs land in `app/build/outputs/roborazzi/` |
| APK facts (target, permissions, label) | `/opt/android-sdk/build-tools/36.1.0/aapt2 dump badging <apk>` |

Flavors: `t36` (targetSdk 36, app "Baby Sim extra test", short plan) and `t37` (targetSdk 37, app "Baby Sim test", full plan). They install side by side.

## Versions that work together (2026-11)
- AGP 9.5.1 has built-in Kotlin. Add only `org.jetbrains.kotlin.plugin.compose` at **2.4.21**, the same Kotlin as the engine.
- Compose BOM **2026.11.01** (Material 3 1.5) needs **compileSdk 37.1+**. Use the AGP 9 DSL:
  `compileSdk { version = release(37) { minorApiLevel = 2 } }`
- `material-icons-core:1.7.8` covers the icons we use. Avoid `-extended`; it's huge.
- Screenshots: Robolectric 4.18.1 + Roborazzi 1.79.0, with `@GraphicsMode(NATIVE)` and `@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")`, which is Galaxy A54 size. Record mode is set through `testOptions.unitTests.all { systemProperty("roborazzi.test.record", "true") }`.

## Known errors and fixes
| Symptom | Cause | Fix |
|---|---|---|
| `dl.google.com` 403 | Session runs on the Default environment | Use "Claude dev (Android SDK access)". Never route around a proxy block. |
| `gcp.mirrors.maven.org` 403 | Maven Central redirects large jars (Robolectric runtime) there | That host is allowed in the Android environment. Don't add other mirrors to dodge a block. |
| HTTP **429** from Maven Central or its mirror | Rate limit | Retry with backoff (45 s, 90 s…). It clears. |
| `checkAarMetadata … compile against 37.1` | New AndroidX needs a newer platform | `sdkmanager "platforms;android-37.2"`, then set `minorApiLevel`. |
| `Product Flavor … custom resource values … disabled` | AGP 9 default | `buildFeatures { resValues = true }` |
| Gradle 8 can't configure the AGP 9 project, even for `gradle wrapper` | Version mismatch | Generate the wrapper in an empty folder and copy it in. |
| Theme lint `NewApi` (DayNight needs 29) | minSdk is 26 | Use `@android:style/Theme.Material.Light.NoActionBar` and let Compose draw. |

## Rules
- Production permission set only: never `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or a `specialUse` FGS (`CLAUDE.md`).
- Before you hand over an APK: 0 lint errors on both flavors, screenshot tests pass, and you have **looked** at the PNGs (Read tool). Fix truncated text and cramped buttons before shipping.
- Bump `versionCode`/`versionName` when testers get a new build. Logs record `app_version`.
