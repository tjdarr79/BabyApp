# 07 — Milestones

Rules: each milestone is **one commit** (kickoff). It's done only when its proving test passes locally with quiet flags and lint/type checks are clean. Mapping to brief §15 steps is shown per milestone. **Owner approval gates** are marked ⛔.

| ID | Brief step | Title | Gate |
|---|---|---|---|
| M1 | 1 | Phase 1 documents | ⛔ owner approval before any code |
| M2.1–M2.6 | 2 | Engine + harness + two-baby proof | ⛔ reviewer reads first harness report (M2.6) |
| M3 | (1/5) | Platform spike: alarms, notifications, background audio | Approved 2026-11-19 (`01` A3) |
| M4 | 3 | Persistence, SimulationHost on Android, profile & configuration | |
| M5 | 4 | Core care interactions (functional UI) | |
| M6.1–M6.3 | 5 | Scheduling, notifications, audio | ⛔ audio licence review |
| M7 | 6 | Modern UI | |
| M8 | 7 | Modes, history, pause/resume | |
| M9 | 8 | Testing, reviewer sign-off, beta | ⛔ reviewer sign-off on all parameters |
| M10 | 9 | Google Play release | ⛔ owner |

---

## M1 — Phase 1 documents (Step 1)
- **Deliverables:** `docs/01`–`07`, `CLAUDE.md`.
- **Acceptance:** owner approves stack, conflicts list, open questions.
- **Proof:** review only (no code).

## M2 — Behavior engine (Step 2). No UI, audio, or notifications.

### M2.1 Project skeleton
- Gradle (Kotlin DSL, version catalog). Modules `:engine` (KMP, `jvm` target), `:harness` (JVM CLI). **No `:app` yet.** ktlint + detekt configured. Warnings-as-errors in `:engine`.
- **Acceptance:** `./gradlew build -q` green on an empty engine with one smoke test. `:engine` has no Android or `java.*` imports (detekt forbidden-import rule).
- **Proof:** `EngineSmokeTest`, `NoForbiddenImportsTest`.

### M2.2 Core types, RNG, parameter loading
- `BabyId`, `SimInstant`, `Slot`, `BabyConfig`, `BabyState` (serializable), xoshiro256** + SplitMix64 streams, `Sourced<T>`, parameter JSON for 5 infant bands + 4 temperaments from `06`/`04` §6.3.
- **Acceptance:** loading fails on any value missing `source`/`reviewStatus`. RNG streams reproducible and independent.
- **Proof:** `ParamSchemaTest` (every leaf has source + status), `RngDeterminismTest`, `RngStreamIndependenceTest`, `SnapshotSerializationRoundTripTest`.

### M2.3 Drives, processes, sleep/feeding/diaper, catch-up
- Drive integration, hazard-budget processes, thresholds, sleep cycles with arousals, feeding, voids, audible-activity generation, `advance()` + `nextWakeAt`, pause semantics, unattended rule (`01` B1), backward-clock clamp.
- **Acceptance:** `advance(t0→t2) == advance(advance(t0→t1)→t2)` for arbitrary split points (catch-up equivalence). `nextWakeAt` is exactly when the next event fires.
- **Proof:** `CatchUpEquivalencePropertyTest` (random splits, 1000 cases), `NextWakePredictionTest`, `PauseFreezesNeedsTest`, `UnattendedAutoPauseTest`, `ClockBackwardClampTest`.

### M2.4 Care actions and effect matrix
- `apply()`. All `04` §6.4 actions. Hidden causes. Outcome events. Non-punitive rules.
- **Acceptance:** each matrix cell has a test. A miss never pushes distress above the no-action trajectory except `↑` cells.
- **Proof:** `EffectMatrixTest` (parameterized per cell), `NonPunitiveMissTest`, `PartialFeedShortensNextIntervalTest`, `ThreeMonthCryingScenarioTest` (replays `04` §12 qualitatively: feed refused, diaper dry, rock settles in sampled window, later wake hunger-driven).

### M2.5 SimulationHost, scheduler planner, audio arbiter (pure)
- `SimulationHost` (N instances), `EntitlementProvider` (v1 = 1), `SchedulerPlanner` (derived collision-free request codes, 60 s coalescing), `AudioArbiter` (priority), notification-ID mapper.
- **Acceptance:** kickoff rules 1–8 satisfied in the pure layer. Host refuses to exceed `maxActiveBabies`, but tests can construct a host with a test provider returning 2.
- **Proof:** `SchedulerPlannerPropertyTest` (uniqueness, merge), `AudioArbiterPriorityTest`, `EntitlementGateTest`, `NoGlobalBabyStateTest` (reflection/grep: no mutable `object`/companion state in `:engine`).

### M2.6 Harness + two-baby 72 h proof + first calibration report
- `:harness` CLI producing 24 h and 7 d Markdown/CSV reports per band × temperament with caregiver policies (`04` §10).
- **Two-baby test** (`04` §11.7): independence, no contamination, action isolation, deterministic replay, persistence round-trip (JSON snapshot level).
- **Acceptance:** two-baby test green. Report generated for all 5 × 4 combos. Every out-of-range metric listed with the responsible parameter. **No requirement that all metrics pass yet**; that's tuning with the reviewer.
- **Proof:** `TwoBabySeventyTwoHourTest`, `HarnessReportGoldenTest`, `CatchUpPerformanceTest` (< 250 ms per simulated day, JVM).
- ⛔ Reviewer reads `docs/reports/M2.6-baseline.md`.

## M3 — Platform spike (approved)
- Also: play 3–5 candidate clips from `08-audio-sourcing.md` through each audio tier, to judge quality after notification-channel compression and phone speakers.
- Throwaway `spike/` app (not merged into `:app`) exercising `03` §7 tiers T1–T3 and §12 matrix on real devices.
- **Acceptance:** `docs/spikes/M3-platform-findings.md` with measured alarm lateness per device/condition and go/no-go per audio tier for target 36 and 37.
- **Proof:** findings document with raw logs. No production code.

## M4 — Persistence, Android host, profile & configuration (Step 3)
- `:app` module. Room schema v1 (`05`), DAOs, transactional host step, DataStore prefs, `AppContainer` DI. Onboarding: name, age band, temperament. Profile edit (age/temperament change preserves history, `01` B9). Minimal Compose screens (unstyled).
- **Acceptance:** create, edit and restart the app with the profile and state restored exactly. Age change keeps history. Schema migration test harness in place.
- **Proof:** `RoomRoundTripTest` (snapshot via DB equals engine snapshot), `TwoBabiesNoMigrationTest` (instrumented: two babies on v1 schema, isolated queries), `AgeChangePreservesHistoryTest`, `MigrationTestHelper` scaffold, `SnapshotCorruptionRecoveryTest`.

## M5 — Core care interactions (Step 4)
- Dashboard action buttons wired to `host.apply`: feed (start/stop), burp, check/change diaper, hold, rock, play, put down. Outcome messages (non-punitive copy). Ongoing-action UI. Recent activity list.
- **Acceptance:** every action reachable in ≤ 2 taps. Actions change future events as the engine predicts.
- **Proof:** `CareActionViewModelTest` (each action → host call → state), Compose UI test `TwoTapActionsTest`, manual accelerated run of the 3-month scenario.

## M6 — Audio, notifications, background (Step 5)

### M6.1 Scheduling & recovery
- `AlarmScheduler` (exact/inexact per `03` §3), wake receiver, boot/package/time/permission receivers, catch-up on app open, alarm-latency self-check.
- **Proof:** Robolectric `AlarmSchedulerTest` (codes from planner, exact vs inexact fallback), `BootRecoveryTest`, `ExactPermissionRevokedFallbackTest`. Device checklist from M3 re-run.

### M6.2 Notifications
- Channels (`03` §5), per-baby id/tag/group, permission onboarding, quiet hours, volume/vibration controls.
- **Proof:** `NotificationMapperTest` (ids collision-free across slots), `QuietHoursTest`, `PostNotificationsDeniedDegradesTest`.

### M6.3 Audio library & playback
- Prerequisite: libraries purchased and licence confirmations on file per `08-audio-sourcing.md`.
- Asset manifest (`assets/audio/manifest.json`: filename, source URL, creator, licence, attribution, download date, age bands, vocal type, intensity, duration, states). Licence gate. Media3 playback, `AudioArbiter` integration, escalation/transition/stop-on-care, no immediate repeats. Tiers T0–T3 per M3 findings. Attribution screen.
- **Proof:** `AudioManifestLicenseGateTest` (rejects NC/ND/missing fields), `ClipSelectionNoRepeatTest`, `EscalationStopsOnCareTest`, device checklist (locked screen, battery saver).
- ⛔ Owner reviews audio licences.

## M7 — Modern UI (Step 6)
- Material 3 design system, light/dark, dashboard, profile, history, settings. Accessibility (TalkBack, contrast, haptics-only option).
- **Proof:** Compose screenshot tests (light/dark), accessibility checks (Espresso/Compose a11y), `DashboardShowsExactlyOneBabyTest` (kickoff rule 9).

## M8 — Modes and history (Step 7)
- Normal / Accelerated (2×/4×/10×) / Training hints overlay. Pause, resume, reset. History timeline + daily summaries + retention.
- **Proof:** `AcceleratedTimeMappingTest`, `PauseResumeNoAccumulationTest`, `TrainingHintsNeverRevealCauseTest`, `HistoryRetentionRollupTest`.

## M9 — Testing and refinement (Step 8)
- Harness re-tuned with the reviewer until all calibration gates pass. Parameters move to `REVIEWED_*`. 72 h on-device soak per device class. Battery measurement. Closed beta (Play internal/closed testing) with reborn-doll owners.
- **Acceptance:** zero `UNREVIEWED` parameters in shipped param set (`ParamsReviewedForReleaseTest` enabled in release build). Crash-free soak. Beta feedback triaged.
- **Proof:** `ParamsReviewedForReleaseTest`, harness report `docs/reports/M9-final.md`, soak logs.

## M10 — Google Play release (Step 9)
- Name/branding, listing, screenshots, pricing, privacy policy, Data safety (no data collected), FGS-type declarations (`mediaPlayback` only), exact-alarm justification, content rating (adult audience). Re-verify every Play policy citation in `03` on live pages.
- **Proof:** pre-launch report clean. Policy checklist signed off.

## Step 10 — Two-baby expansion (scope note)
Because the engine, host, scheduler, persistence, notification IDs and audio arbiter already support N babies (M2.5, M4, M6), **Step 10 is limited to:**
1. Multi-baby UI (dashboard switcher, "which baby needs attention" indicators, onboarding for baby #2).
2. Google Play Billing one-time in-app product → `entitlements` row → `EntitlementProvider.maxActiveBabies = 2`.
3. Distinguishable per-baby audio cue sets and per-baby notification channels.
No engine, scheduler, schema, or notification-ID changes are expected. Any such change found necessary is a defect against M2.5/M4.
