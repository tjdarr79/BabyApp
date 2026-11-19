# Claude Code Kickoff Prompt — Baby Care Simulator

## Setup before pasting
1. Create an empty repo folder (e.g. `baby-sim/`) and `git init`.
2. Save the full Project Brief as `docs/PROJECT_BRIEF.md` (Claude Code cannot see the claude.ai project description).
3. Start Claude Code in that folder. Plan mode (Shift+Tab) is recommended for Phase 1.
4. Paste the prompt below.

---

# Kickoff: Realistic Baby Care Simulator (Android-first)

## Context
You are the lead engineer on a new Android app: a realistic baby/toddler care simulator that runs alongside a physical reborn doll. The full requirements baseline is in `docs/PROJECT_BRIEF.md`. Read it completely before doing anything else. It is the source of truth. If this prompt and the brief conflict, flag the conflict and ask.

The differentiator is the behavior engine: realistic, state-driven, varied baby behavior. Visuals come later. Build and validate the engine before any UI.

## Hard architectural constraint: multi-baby from day one, single baby exposed
The two-baby paid expansion (brief Step 10) must NOT require rewriting the engine, scheduler, persistence, notifications, or audio. Design for N independent babies now. Build and expose only one.

Required:
1. Every baby-scoped object (state, events, history records, scheduled alarms, notifications, audio requests) is keyed by `BabyId`. No global "the baby" anywhere.
2. The engine is a pure function/stepper over a per-baby `BabyState`. A `SimulationHost` (or registry) owns N independent instances. Each instance has its own config (age, temperament), its own seeded RNG stream, its own pause/resume state, and its own persisted snapshot.
3. No singletons, static mutable state, or DI-scoped objects that hold a "current baby".
4. Persistence schema: babies table, per-baby state snapshot, history table with a `babyId` foreign key. Adding baby #2 must not require a schema migration.
5. Scheduling: one scheduler merges the next-due events across all active babies into the OS alarm(s). Alarm and notification IDs are derived from `BabyId` + event type, collision-free.
6. Notifications: per-baby notification identity (ID, tag, group) and per-baby identifiable content. Channel design must allow per-baby distinguishable alerts later.
7. Audio: an arbitration layer that accepts concurrent requests from multiple babies with priority (crying > fussing > content vocalizations). v1 has one source, but the API takes `BabyId`.
8. Entitlement gate: `maxActiveBabies` comes from a single `EntitlementProvider`. v1 returns 1. One place to change it later.
9. Exposure: the v1 UI, onboarding, and settings show exactly one baby. No disabled "Add baby" buttons, no hidden menus, no dead code paths in the UI.
10. Proof: the engine test suite must include a headless test running two babies (different ages, temperaments, seeds) for 72 simulated hours, asserting:
    - schedules are independent and do not mirror each other
    - no cross-state contamination
    - care actions on baby A do not affect baby B
    - deterministic replay from seeds
    - persistence round-trip restores both babies exactly

Do NOT build now: two-baby UI, multi-baby dashboard, Google Play Billing / in-app purchase, distinguishable per-baby audio cue sets. Those belong to Step 10.

## Behavior engine design principles
- Pure Kotlin module with zero Android dependencies (in `commonMain` if Kotlin Multiplatform is chosen).
- Injectable `Clock` and RNG. Seeded and reproducible.
- Event-driven, not a tick loop. The engine computes state as a function of elapsed time and returns the next scheduled event time. On wake/resume/reboot, it catches up from the last persisted timestamp. This is the battery and background-reliability strategy: do not depend on a long-running service to keep the baby alive.
- Internal state per brief §7 (hunger, fatigue, sleep state, diaper, comfort, stimulation, mood/fussiness, time-since counters, recent caregiver actions). Needs accumulate as continuous drives shaped by age and temperament curves. Events fire from thresholds plus a stochastic hazard, not fixed timers.
- Separate audible activity (coos, babble, light fussing) from care-required events.
- Hidden cause: the user does not see why the baby is crying. Actions that miss the actual need are ineffective or partially effective. Feedback is non-punitive.
- All age and temperament parameters live in data tables (JSON or Kotlin data), not logic. Each value carries a `source` field and a `reviewStatus` field (`UNREVIEWED` until the childcare reviewer signs off).
- Simulation harness: a CLI or test runner that produces 24-hour and 7-day reports per age × temperament: events per hour, longest gap with no audible activity, longest gap with no care-required event, care events per day, total sleep, night wakings. These reports are how behavior gets tuned and reviewed.

## Phase 1 deliverables (this session): documents only, no app code
Create in `docs/`:
1. `01-requirements-gaps.md`: missing or ambiguous functional requirements, each with a proposed default and the open question.
2. `02-tech-stack.md`: compare at minimum Kotlin + Jetpack Compose with a Kotlin Multiplatform engine module, Flutter, and native Kotlin only. Score each on background scheduling/alarms, foreground-service rules, notifications, audio, local persistence, testability of a pure engine, iOS path, and solo-developer maintenance cost. End with one recommendation.
3. `03-android-background.md`: current Android constraints, verified against official Android and Google Play documentation with URLs: exact alarm permissions and Play policy eligibility, foreground service types (Android 14+) and whether this app qualifies for any, POST_NOTIFICATIONS, Doze/App Standby buckets, OEM battery killers, full-screen intent policy, audio focus, boot/restart recovery. Flag every Play policy risk.
4. `04-behavior-engine-spec.md`: state model, drives, event types, age-profile schema, temperament modifiers, care-action → effect matrix, randomness strategy, catch-up algorithm, and the multi-instance architecture defined above. Include a worked sequence for the brief's 3-month-old crying scenario.
5. `05-data-model.md`: entities, keys, persistence schema, versioning and migration strategy.
6. `06-age-behavior-ranges.md`: research table per age band (feeding interval, feed duration, total sleep per 24h, nap count, wake windows, night waking, crying norms). Cite AAP, CDC, and peer-reviewed sources. Mark every value `UNREVIEWED`. Realityworks material is a reference for frequency/difficulty concepts only; do not copy its schedules as data.
7. `07-milestones.md`: small milestones mapped to brief Steps 2–9, each with acceptance criteria and the test that proves it. Note that Step 10 is limited to UI, billing, and audio distinction because the engine already supports multiple babies.
8. `CLAUDE.md` at the repo root: project conventions, build/test/lint commands, the multi-baby constraint, MVP scope guardrails.

Stop after Phase 1. Give me a short summary: recommended stack, top 5 open questions, top 5 risks. Wait for my approval before writing any application code.

## After approval (preview, do not start yet)
Milestone 2 = engine module + simulation harness + two-baby 72-hour test, before any UI, profile screens, audio, or notifications.

## Working rules
- Ask up to 3 clarifying questions only if blocked. Otherwise state the assumption and continue.
- Stay inside the MVP boundary. No cloud backend, accounts, analytics SDKs, ads, or AI APIs.
- Original behavior engine, content, and audio. Do not copy Realityworks or BabySitMe schedules, sounds, or branding.
- Once code starts: surgical edits, run tests with quiet flags (e.g. `./gradlew test -q`), run lint and type checks, and verify before reporting a milestone done.
- One commit per milestone with a clear message.
