# 02 — Technology Stack Evaluation

**Decision needed:** stack for an Android-first app whose hardest problems are background scheduling, notifications and background audio (see `03`), with a possible iOS port only after commercial validation (brief §14).

## 1. Options evaluated

| ID | Option | Engine | Android app | iOS later |
|---|---|---|---|---|
| **A** | Kotlin + Jetpack Compose, engine as a **Kotlin Multiplatform (KMP) module** | `commonMain`, pure Kotlin | Native Kotlin/Compose | Add `iosArm64` target to engine. UI in SwiftUI or Compose Multiplatform |
| **B** | **Flutter** (Dart) | Dart package | Flutter UI + Kotlin platform code for alarms/FGS/audio | Same Dart codebase. Swift platform code for background |
| **C** | **Native Kotlin only** | Plain Kotlin/JVM module | Native Kotlin/Compose | Engine re-written in Swift, or migrated to KMP later |

## 2. Scoring

Scale 1 (poor) – 5 (excellent). **Weight** reflects the brief: Android reliability is non-negotiable (§14 "do not sacrifice Android reliability"), and iOS is a contingent Phase 3.

| Criterion | Weight | A: Kotlin+KMP engine | B: Flutter | C: Native only |
|---|---|---|---|---|
| Background scheduling / alarms | 3 | **5**: direct `AlarmManager`, receivers, boot handling | **3**: alarm callbacks run in a separate Dart isolate with no shared memory, and the plugin doesn't manage `SCHEDULE_EXACT_ALARM` itself ([freeCodeCamp](https://www.freecodecamp.org/news/flutter-alarmmanager-tutorial), [pub.dev docs](https://pub.dev/documentation/android_alarm_manager_plus/5.1.1/)) | **5** |
| Foreground-service rules (A14–A17) | 3 | **5**: typed FGS, `MediaSessionService` directly ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)) | **2**: FGS + audio from a background isolate means plugin chains, and every Android behavior change waits on plugin updates | **5** |
| Notifications | 2 | **5**: `NotificationCompat`, channels | **4**: mature plugins, but channel/sound edge cases still need native code (assessment, not verified) | **5** |
| Audio (arbitration, escalation, background) | 3 | **5**: Media3; Android 17 guidance says Media3 `MediaSessionService` apps are "not likely to be impacted" ([bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)) | **3**: background playback must bridge to a native media session | **5** |
| Local persistence | 2 | **5**: Room is KMP-capable (Android, iOS, JVM) from 2.7.0 ([Room KMP](https://developer.android.com/kotlin/multiplatform/room)) | **4**: drift/sqflite (assessment) | **5** |
| Testability of a pure engine | 3 | **5**: `commonTest` runs on JVM in seconds. Harness is a JVM CLI | **4**: Dart unit tests are good | **5** |
| iOS path | 1 | **4**: engine compiles to iOS unchanged. Compose Multiplatform for iOS is stable since 1.8.0 ([JetBrains](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)). iOS background code is still new native work | **5**: one UI codebase. iOS background code is still native | **2**: engine rewrite or later KMP migration |
| Solo-developer maintenance | 2 | **4**: one language. KMP Gradle setup is somewhat more complex | **3**: two languages (Dart + Kotlin) where it matters most | **5** |
| **Weighted total** (max 95) | | **92** | **63** | **92** |

### Why A over C when the totals tie
A wins on the iOS path (+2); C wins on maintenance (+2). The tie-break is strategic: the iOS path matters, and A's maintenance cost is small.

- A and C produce **identical Android apps**. The only difference is that the engine module is declared `kotlin("multiplatform")` with `commonMain` instead of `kotlin("jvm")`.
- The cost of A is small and paid once: KMP plugin configuration, no `java.*` imports in the engine, and `kotlinx` libraries instead of JVM ones. That restriction also enforces the kickoff's "zero Android dependencies" rule at compile time.
- The payoff is that the **behavior engine, which is the product's differentiator, is iOS-ready on day one** without any iOS work being done now.

### Why not Flutter
Every hard requirement in `03` is a native Android API with fast-moving rules: exact alarms, FGS types, the Android 15 audio-focus gate, and Android 17 background-audio hardening. In Flutter each of those is a Kotlin platform channel *plus* a Dart wrapper *plus* a plugin dependency that must track Android releases. Flutter's advantage is a shared UI, and this app's UI is deliberately simple (brief §10). Flutter optimises the cheap part and complicates the expensive part.

## 3. Recommendation

**Option A: Kotlin + Jetpack Compose (Material 3), with the behavior engine in a Kotlin Multiplatform module.**

Concrete choices (versions pinned at scaffold time in M2.1 to latest stable; not asserted here):

| Layer | Choice | Notes |
|---|---|---|
| Language / build | Kotlin 2.x, Gradle Kotlin DSL, version catalog | |
| Engine | `:engine` KMP module. Targets **`jvm` only in v1** (consumed by Android and harness). `iosArm64`/`iosSimulatorArm64` added at Step 11 | No Android, no `java.*`, no coroutines in core stepping. Pure functions |
| Serialization | `kotlinx.serialization` (JSON) | Snapshots, parameter tables |
| Harness | `:harness` JVM CLI module | 24 h / 7 d reports per age × temperament |
| App UI | Jetpack Compose + Material 3, light/dark | Step 6 |
| Persistence | Room (KMP-capable line), schema export committed | `05` |
| Settings | Jetpack DataStore | App-level prefs only. Per-baby prefs live in Room |
| Scheduling | `AlarmManager` (+ `AlarmManagerCompat`), boot/time-change receivers | No WorkManager for simulation timing. Optional for housekeeping |
| Audio | AndroidX Media3 (ExoPlayer + `MediaSessionService`) | `03` §7 |
| Notifications | `NotificationCompat`, channels | `03` §5 |
| DI | Manual constructor injection (`AppContainer`) | No framework needed at this size. Kickoff rule 3 forbids DI-held "current baby" |
| Tests | `kotlin.test` (engine), JUnit + Robolectric (app receivers/schedulers), instrumented tests on devices for alarms/audio | |
| Static checks | ktlint, detekt, Android Lint, compiler warnings as errors in `:engine` | `CLAUDE.md` |
| minSdk / targetSdk | **minSdk 26** (notification channels baseline). **targetSdk 36** (Play requirement for new apps since Aug 31 2026, [Play target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)) | minSdk is an open question: raising it to 29–31 shrinks the test matrix |

**Explicitly excluded** (kickoff / brief §16–18): cloud backend, accounts, analytics/crash SDKs that transmit data, ads, AI APIs, Play Billing (Step 10 only).
