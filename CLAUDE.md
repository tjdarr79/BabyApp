# CLAUDE.md — Baby Care Simulator (Android-first)

## Source of truth
- `docs/PROJECT_BRIEF.md` is the requirements baseline. `docs/00-kickoff.md` holds the engineering constraints.
- Design docs: `docs/01-requirements-gaps.md` … `docs/08-audio-sourcing.md`. Owner decisions are logged at the top of `docs/01-requirements-gaps.md`. If code and docs disagree, **flag it and ask**. Don't silently resolve it.
- Work proceeds milestone by milestone (`docs/07-milestones.md`). **One commit per milestone.** Don't start a milestone marked ⛔ without owner approval.

## Current phase
Phase 1 (documents) is complete. Owner decisions are recorded (2026-11-19). No application code exists yet. **Explicit go-ahead is still required before M2.1 (engine skeleton).** M3 platform spike is approved.

## Architecture in one paragraph
Pure-Kotlin behavior engine in `:engine` (Kotlin Multiplatform, `commonMain`, `jvm` target only for now) → `SimulationHost` owns N independent `BabyInstance`s → Android `:app` (Compose, Room, Media3, AlarmManager) persists snapshots, schedules OS alarms from the engine's `nextWakeAt`, posts notifications, and plays audio. There is no long-running service keeping the baby alive: every wake catches up from the last persisted timestamp. `:harness` is a JVM CLI that produces behavior reports.

## Non-negotiable: multi-baby from day one, single baby exposed
1. Every baby-scoped object (state, events, history, alarms, notifications, audio requests) is keyed by `BabyId`. There is no global "the baby" anywhere.
2. The engine is a stateless stepper over per-baby `BabyState`. `SimulationHost` owns N instances, each with its own config, seeded RNG streams, pause state and snapshot.
3. No singletons, `object`s with mutable state, companion caches, static mutable state, or DI scopes holding a "current baby".
4. Schema: `babies`, `baby_snapshots`, `history_events` (FK `baby_id`). Adding baby #2 = rows only, **no migration**.
5. One scheduler planner merges next-due events across babies. Alarm/notification IDs = `1000 + slot*16 + kind`, collision-free.
6. Notifications carry per-baby id/tag/group and name the baby.
7. Audio goes through `AudioArbiter` with `BabyId` and priority (`CRY_INTENSE > CRY > FUSS > SETTLING > content > SLEEP`).
8. `EntitlementProvider.maxActiveBabies` is the only gate (v1 returns 1).
9. The v1 UI shows exactly one baby. **No** disabled "Add baby" buttons, hidden menus, or dead UI paths.
10. `TwoBabySeventyTwoHourTest` must stay green.

## Engine rules
- `:engine` has zero Android dependencies and no `java.*` imports. Clock and RNG are injected. Same seed + action log ⇒ identical output.
- Event-driven: `advance(state, to)` / `apply(state, action, at)` return `StepResult(state, events, nextWakeAt, audio)`. No app-level tick loop.
- **All age/temperament numbers live in parameter JSON**, never in logic. Every value has `source` and `reviewStatus` (`UNREVIEWED` until the childcare reviewer signs off). Unsourced values use `evidence: DESIGN_ASSUMPTION`.
- Hidden cause: the UI never reads the engine's causes. Feedback is non-punitive. No scores.
- Separate audible activity from care-required events.

## Commands (valid once M2.1 lands)
| Purpose | Command |
|---|---|
| All tests | `./gradlew test -q` |
| Engine tests only | `./gradlew :engine:jvmTest -q` |
| Single test class | `./gradlew :engine:jvmTest -q --tests "*TwoBabySeventyTwoHourTest"` |
| App unit tests (from M4) | `./gradlew :app:testDebugUnitTest -q` |
| Lint / format | `./gradlew ktlintCheck detekt -q` (and `./gradlew :app:lintDebug -q` from M4) |
| Auto-format | `./gradlew ktlintFormat -q` |
| Type check / compile | `./gradlew compileKotlinJvm -q` (engine), `./gradlew :app:compileDebugKotlin -q` (app) |
| Harness | `./gradlew :harness:run -q --args="--band M2_3 --temperament AVERAGE --days 7 --seeds 50 --policy attentive"` |
| Full verification before reporting a milestone done | `./gradlew build -q` |

Always run tests, lint and type checks with quiet flags and report real results before declaring a milestone done.

## Conventions
- Kotlin official style (ktlint). detekt with the forbidden-imports rule on `:engine`. Warnings as errors in `:engine`.
- Surgical edits: change the targeted function/lines. Don't restructure unrelated code.
- Tests are named for the behavior they prove (`PartialFeedShortensNextIntervalTest`). Property tests for catch-up equivalence and the scheduler planner.
- Snapshots: additive fields use defaults. Breaking changes bump `engineSchemaVersion` and add an upcaster plus a golden-file test.
- Room: `exportSchema = true`, schemas committed, migration tests for every version. Never use destructive migration in release.
- Commit messages: `<area>: <imperative summary>`, one commit per milestone.

## MVP scope guardrails
**In:** one baby exposed; infant bands Newborn (0–4 wk), 2–3 m (~8–13 wk; no 5–8 wk band), 4–6 m, 7–9 m, 10–12 m; four temperaments; feed, burp, check/change diaper, hold, rock, play, put down; history; Normal/Accelerated/Training-hints modes; pause/resume; local notifications and audio; light/dark Material 3.

**Out (don't build):** two-baby UI, multi-baby dashboard, Play Billing/IAP, per-baby audio cue sets (all Step 10); toddler behaviors, solids, pacifier, bathing, colic/reflux/illness overlays (Step 11); cloud backend, accounts, analytics or crash SDKs that transmit data, ads, AI APIs; 3D/cartoon visuals; game currencies or points.

**Platform (see `docs/03-android-background.md`):** targetSdk 36, minSdk 26. Declare `SCHEDULE_EXACT_ALARM`. **Never** declare `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, or a `specialUse` FGS. Only `mediaPlayback` FGS, and only while audible audio plays.

## Content and IP
- Original behavior engine and content. Don't copy Realityworks or BabySitMe schedules, sounds, text or branding. Realityworks material is a concept reference only.
- Audio: CC0, Pixabay, or commercially cleared licences only. No NC/ND. Every clip is recorded in `assets/audio/manifest.json` with source URL, creator, licence, attribution and download date.
