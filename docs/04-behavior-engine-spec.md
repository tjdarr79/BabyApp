# 04 — Behavior Engine Specification

**Scope:** the pure-Kotlin `:engine` module (KMP `commonMain`, zero Android deps) plus the `SimulationHost` contract the app uses. All numeric values shown are **illustrative**. Real values live in parameter tables (§6), are sourced from `06`, and are `UNREVIEWED`.

---

## 1. Design principles (from brief §7 + kickoff)

1. **State-driven, not timer-driven.** Needs are continuous drives. Events fire from thresholds plus stochastic hazards.
2. **Event-driven stepping.** `advance(state, to)` jumps between events. No app-level tick loop. The engine always returns `nextWakeAt`.
3. **Pure and deterministic.** Same `(config, seed, action log)` → identical output. `Clock` and RNG are injected.
4. **Multi-instance by construction.** Every object is keyed by `BabyId`. The engine itself holds no global state (§11).
5. **Hidden cause, non-punitive feedback.** Distress has latent causes. Actions resolve causes, not symptoms. Misses produce informative, neutral outcomes.
6. **Data, not logic, for all age/temperament numbers.** Each value carries `source` and `reviewStatus`.
7. **Audible ≠ care-required.** Separate event families (§4).

## 2. Core types (sketch)

```kotlin
@JvmInline value class BabyId(val value: String)          // UUID string
@JvmInline value class SimInstant(val epochMillis: Long)  // simulation time
@JvmInline value class Slot(val value: Int)               // 0..15, stable per baby, for derived OS ids

data class BabyConfig(
  val id: BabyId, val slot: Slot, val ageBand: AgeBandId,
  val temperament: TemperamentId, val seed: Long,
  val nightStartLocalMinutes: Int = 19 * 60, val nightEndLocalMinutes: Int = 7 * 60,
  val zoneOffsetProvider: ZoneOffsetProvider,               // injected; pure fn(SimInstant) -> offset
)

data class BabyState( /* §3 — fully serializable, versioned */ )

interface Engine {
  fun initialize(config: BabyConfig, params: ParamSet, at: SimInstant): BabyState
  fun advance(state: BabyState, to: SimInstant): StepResult            // catch-up; no actions
  fun apply(state: BabyState, action: CareAction, at: SimInstant): StepResult  // advance to `at`, then act
}

data class StepResult(
  val state: BabyState,
  val events: List<BabyEvent>,      // ordered, all carry babyId + SimInstant
  val nextWakeAt: SimInstant?,      // null = nothing scheduled (e.g. paused)
  val audio: AudioDirective,        // what this baby wants to sound like *now* (§9)
)
```

## 3. State model (per baby)

### 3.1 Continuous drives (normalized 0.0–1.0 unless noted)

| Variable | Meaning | Dynamics between events |
|---|---|---|
| `hunger` | Need to feed | Rises linearly at `1 / meanFeedInterval(age)` × temperament × circadian (slower at night for ≥ 4 m). Reset by feeding in proportion to amount taken |
| `sleepPressure` | Fatigue / homeostatic drive | Rises while awake at `1 / meanWakeWindow(age)`, decays while asleep at `1 / meanSleepBoutToRecover(age)` |
| `gas` | Swallowed air / trapped wind | Increases during feeds (`gasPerFeedMinute`). Removed by successful burps. Slow spontaneous decay |
| `diaperWetness` | 0 dry … 1 saturated | Discrete jumps on void events (§5.3). `diaperSoiled: Boolean` separate |
| `diaperDiscomfort` | Irritation | Rises while wet/soiled, faster if soiled. Resets on change |
| `stimulation` | Recent sensory input | Rises during play/handling/awake-alert, decays when calm/asleep. Too high → overstimulated. Too low for too long while awake → bored (≥ 4 m) |
| `distress` | Acute arousal (the audible signal) | Driven by active causes (§3.3). Decays under effective soothing. Has inertia: no instant calm |
| `mood` | Slow baseline (−1 grumpy … +1 sunny) | EMA of distress, sleep quality and recent soothing success. Biases vocalization choice and fuss hazard |

### 3.2 Discrete state

| Field | Values |
|---|---|
| `activity` | `ASLEEP_ACTIVE`, `ASLEEP_QUIET`, `DROWSY`, `AWAKE_CALM`, `AWAKE_ALERT`, `FUSSING`, `CRYING`, `CRYING_INTENSE`, `EXHAUSTED_SLEEP` |
| `ongoingAction` | `null` or `{type: FEED/ROCK/HOLD/PLAY, startedAt}` |
| `careRequest` | `null` or `{id, openedAt, escalationLevel, lastAlertAt}` |
| `timestamps` | `lastFeedEndAt`, `lastDiaperChangeAt`, `lastSleepStartAt`, `lastWakeAt`, `lastSoothedAt`, `lastBurpAt` (time-since counters are derived, never stored) |
| `recentActions` | ring buffer (size 16) of `{action, at, outcome}` |
| `pendingProcesses` | armed stochastic processes with their pre-drawn hazard budgets (§7) |
| `thresholds` | per-cycle sampled thresholds (`hungerCueAt`, `sleepReadyAt`, `selfSettleProb`, …) |
| `rng` | per-stream counters (§7.2) |
| `meta` | `engineSchemaVersion`, `paramSetVersion`, `lastAdvancedTo` |

### 3.3 Hidden causes

Distress is driven by a **weighted set of active causes**, each computed from drives:

| Cause | Active when (illustrative) |
|---|---|
| `HUNGER` | `hunger ≥ thresholds.hungerCueAt` |
| `TIRED` / `OVERTIRED` | `sleepPressure ≥ sleepReadyAt` / `≥ sleepReadyAt + overtiredMargin` |
| `GAS` | `gas ≥ gasDiscomfortAt` |
| `DIAPER` | `diaperDiscomfort ≥ diaperCueAt(temperament)` |
| `OVERSTIMULATED` | `stimulation ≥ overstimAt(temperament)` |
| `BORED` / `WANTS_CONTACT` | awake, `stimulation` low for > `boredAfter(age)` (≥ 4 m), or contact-seeking hazard (all ages) |
| `UNEXPLAINED` | Stochastic "fussy spell" process. Hazard higher in late afternoon/evening for < 4 m ([Barr crying curve, evening clustering](https://www.melvinkonner.com/images/PDFs/articles/Barr%20Crying%20Cult%20Specificity.pdf)). Self-limiting duration |
| `SEPARATION` | 7 m+: hazard on caregiver "leaving" (post-MVP; reserved) |

The UI **never** reads causes. Only `activity`, care-request status and action outcomes are user-visible. Training Mode reads *observable cues* derived from drives (`01` A6).

## 4. Events

All events: `{babyId, at: SimInstant, type, payload}`.

| Family | Types | Notification? | History? |
|---|---|---|---|
| **Audible activity** | `COO`, `BABBLE`, `GIGGLE`, `SIGH`, `SLEEP_NOISE`, `BRIEF_FUSS` (self-resolving), `HICCUPS` | No | Optional (filtered) |
| **Care-required** | `CARE_REQUEST_OPENED(severity)`, `CARE_REQUEST_ESCALATED(level)`, `CARE_REQUEST_RESOLVED(byAction)`, `CARE_REQUEST_EXPIRED` | Opened/escalated: yes | Yes |
| **Transitions** | `FELL_ASLEEP`, `WOKE`, `SETTLED`, `FEED_FINISHED_CUE`, `BURPED`, `DIAPER_WET`*, `DIAPER_SOILED`* | Optional status channel | Yes (*internal only until checked) |
| **Action outcomes** | `ACTION_OUTCOME(action, outcome)` (§6.4) | No | Yes |
| **Lifecycle** | `PAUSED`, `RESUMED`, `AUTO_PAUSED(reason)`, `CONFIG_CHANGED` | Auto-pause: yes | Yes |

**Care-request opening rule:** `distress ≥ careThreshold` sustained for `careSustainMin(age, temperament)` (e.g. 2–4 min). Below that it's audible fussing only. This prevents alerting on every fuss and is the main lever against "excessive activity".

## 5. Processes and dynamics

### 5.1 Sleep/wake
- Sleep proceeds in **cycles** of `sleepCycleLength(age)`. Newborn cycles are ~50–60 min, with active and quiet sleep ([BC Children's](https://www.bcchr.ca/healthysleepforkids/newborns)).
- At each cycle boundary a **partial arousal** occurs. Wake probability = f(hunger, gas, diaperDiscomfort, circadian(night/day), age, temperament). If aroused: `selfSettleProb(age, temperament, mood)` → `BRIEF_FUSS` then back to sleep (audible only), else `WOKE` (+ maybe care request).
- Sleep onset requires `DROWSY` + `sleepPressure ≥ sleepReadyAt` + low distress. Latency is sampled. `PUT_DOWN` while drowsy succeeds with `transferSuccess(temperament)`. Sensitive babies more often rouse on transfer (brief §6).
- **Circadian weight** `c(age, localTime)`: ~0 for newborns, ramping to strong by 4–6 m (circadian rhythm emerges ~2–3 m, [BC Children's](https://www.bcchr.ca/healthysleepforkids/newborns)). At night: longer cycles chained, lower arousal→wake probability, slower hunger rise (≥ 4 m).

### 5.2 Feeding
- After each feed, sample `hungerCueAt ~ TruncNormal(μ, σ)` such that the implied interval falls inside the band's feeding-interval range (`06`). Temperament scales σ (rhythmicity).
- During `FEED`: if `hunger < feedAcceptMin` → baby refuses after 0.5–2 min (`ACTION_OUTCOME: NOT_INTERESTED`). Brief non-nutritive comfort gives a small distress reduction for < 4 m. Otherwise intake rate reduces hunger and gas accumulates. When satiated, `FEED_FINISHED_CUE` fires (turns away / slows). Feed duration is sampled within the band range.
- **Partial feeds matter:** ending early leaves residual hunger → earlier next cue. This is how history shapes future behavior.

### 5.3 Diapers
- Void events are a Poisson process with rate `wetPerDay(age)/24h`, elevated for 20–60 min post-feed (multiplier, not fixed coupling). "Diaper events are not always tied to feeding" (brief §7).
- Stool events: separate process, rate `soiledPerDay(age)`.
- A wet diaper doesn't automatically open a care request. Discomfort accumulates and contributes the `DIAPER` cause once over threshold. Many wet diapers are discovered only by `CHECK_DIAPER`.

### 5.4 Soothing dynamics
- Under an ongoing soothing action, distress decays at `rate = baseSoothe(action) × match(action, causes) × temperament.sootheFactor`.
- **Settle time is sampled**, not computed. At action start the engine draws a settle-hazard budget (§7). Some attempts take 2 min, others 15 (brief §7 "some soothing attempts take longer").
- Unresolved underlying causes **reassert**: rocking a hungry baby calms briefly, then distress climbs again after `reassertDelay`.

### 5.5 Audible activity generation
- While awake and calm/alert: vocalization process with age-appropriate repertoire (CDC: sounds other than crying ~2 m, cooing ~4 m, consonant babble ~6 m, "mamama/bababa" ~9 m, `06`), rate scaled by mood and stimulation.
- While asleep: low-rate `SLEEP_NOISE`, higher in active sleep.
- Target: no awake period ≥ X min without some audible event (harness metric, `06` targets, reviewer-tuned). **This is the lever against the "3–4 h of nothing" complaint without inventing care events.**

## 6. Parameters (data, not logic)

### 6.1 Schema

```jsonc
// engine/params/age/M2_3.json  (illustrative; files are embedded into the engine at build time)
{
  "paramSetVersion": 1,
  "ageBand": "M2_3",
  "values": {
    "feedIntervalMinutes": {
      "min": 150, "typical": 180, "max": 240,
      "source": "CDC formula feeding; AAP Amount & Schedule of Formula Feedings",
      "sourceUrls": ["https://www.cdc.gov/infant-toddler-nutrition/formula-feeding/how-much-and-how-often.html",
                     "https://healthychildren.org/English/ages-stages/baby/formula-feeding/Pages/Amount-and-Schedule-of-Formula-Feedings.aspx"],
      "evidence": "GUIDANCE",          // PEER_REVIEWED | GUIDANCE | SECONDARY | DESIGN_ASSUMPTION
      "reviewStatus": "UNREVIEWED",    // UNREVIEWED | REVIEWED_OK | REVIEWED_CHANGED
      "reviewNote": null
    }
    // ...
  }
}
```

Every leaf value is a `Sourced<T>`. **Loading fails if any value lacks `source` or `reviewStatus`** (unit test). `DESIGN_ASSUMPTION` is allowed but listed in harness reports so the reviewer sees what is invented vs. researched.

### 6.2 Age-profile parameters (per band)

`feedIntervalMinutes`, `feedDurationMinutes`, `feedAcceptMin`, `totalSleepHours24`, `napCount`, `wakeWindowMinutes`, `sleepCycleMinutes`, `nightWakingsPerNight`, `selfSettleProbNight`, `fussCryMinutesPer24h`, `eveningFussClustering`, `wetDiapersPerDay`, `soiledDiapersPerDay`, `circadianStrength`, `vocalRepertoire` (set of `VocalType`), `vocalRatePerAwakeHour`, `boredAfterMinutes` (null < 4 m), `careSustainMinutes`, `crySegmentMaxMinutes`.

### 6.3 Temperament modifiers

Temperaments are **multiplicative/additive modifiers over the age profile**, loosely mapped to Thomas & Chess temperament dimensions (rhythmicity, adaptability, threshold of responsiveness, intensity, mood; see their NYLS easy / difficult / slow-to-warm-up constellations, [LibreTexts summary](https://socialsci.libretexts.org/Bookshelves/Early_Childhood_Education/Child_Growth_and_Development_(Paris_Ricardo_Rymond_and_Johnson)/06%3A_Social_and_Emotional_Development_in_Infancy_and_Toddlerhood/6.01%3A_Temperament)). These are simulation profiles, not diagnoses (brief §6). All values `DESIGN_ASSUMPTION`, `UNREVIEWED`.

| Modifier | Easygoing | Average | Demanding | Highly Sensitive |
|---|---|---|---|---|
| `rhythmicitySigmaScale` (threshold variance) | 0.7 | 1.0 | 1.2 | 1.1 |
| `sootheTimeScale` | 0.7 | 1.0 | 1.4 | 1.6 |
| `unexplainedFussHazardScale` | 0.6 | 1.0 | 1.6 | 1.3 |
| `careSustainScale` (higher = waits longer before demanding) | 1.4 | 1.0 | 0.7 | 0.9 |
| `overstimThresholdScale` | 1.3 | 1.0 | 1.0 | 0.6 |
| `transitionDistress` (sleep transfer, diaper change, wake) | 0.5 | 1.0 | 1.2 | 1.8 |
| `selfSettleScale` | 1.3 | 1.0 | 0.7 | 0.8 |
| `contentVocalRateScale` | 1.3 | 1.0 | 0.9 | 0.8 |
| `cryIntensityBias` | −1 level | 0 | +1 level | 0 (longer, not louder) |

### 6.4 Care-action → effect matrix

Effect on each hidden cause while the action is applied. **R** = resolves, **P** = partial / temporary relief (cause reasserts), **–** = no effect, **↑** = worsens. Outcome text is what the user sees (non-punitive).

| Action ↓ / Cause → | HUNGER | TIRED | OVERTIRED | GAS | DIAPER | OVERSTIM | BORED/CONTACT | UNEXPLAINED | User-visible outcome on miss |
|---|---|---|---|---|---|---|---|---|---|
| `FEED` (continuous) | **R** (∝ amount) | P (feed-to-drowsy) | P | ↑ (adds gas) | – | P | P | P (< 4 m sucking comfort) | "Not interested in feeding right now" |
| `BURP` (instant attempt, p_success per try) | – | – | – | **R** (prob.) | – | – | – | – | "No burp this time" |
| `CHECK_DIAPER` (info) | – | – | – | – | – (reveals dry/wet/soiled) | – | – | – | "Diaper is dry" |
| `CHANGE_DIAPER` | – | – | – | – | **R** | ↑ (transition, temperament) | P | – | "Diaper was already dry" |
| `HOLD` (continuous) | P (short) | P | P | P (upright: spontaneous-burp chance) | – | P | **R** | P | — |
| `ROCK` (continuous) | P (short) | **R** (→ drowsy) | P (slower) | P | – | P (gentle) / ↑ (sensitive, vigorous) | R | **R** (sampled settle time) | — |
| `PLAY` (continuous, awake) | ↑ | ↑ | ↑↑ | – | – | ↑ | **R** | P | "Not in the mood to play" |
| `PUT_DOWN_TO_SLEEP` | – | **R** if drowsy & calm | ↑ if not settled | – | – | P (reduces input) | ↑ | – | "Wants to be held a little longer" |
| `WAKE` / `GET_UP` | – | ↑ | ↑ | – | – | – | – | – | — |

Rules: (1) A miss never increases distress beyond its natural trajectory, except where real babies object to handling (`↑` cells), and then scaled by `transitionDistress`. (2) Every action is logged with outcome. (3) There is no score.

## 7. Randomness strategy

### 7.1 Hazard-budget (time-to-event) method
Each stochastic process `k` (arousal-wake, unexplained fuss, void, stool, vocalization, settle, spontaneous burp…) has a state-dependent hazard `λ_k(t)`. When armed, the engine draws `E_k ~ Exp(1)` and stores it. The event fires at the first `t` where `∫ λ_k dt ≥ E_k`. Because `E_k` is pre-drawn and stored:
- **`nextWakeAt` is computable** without advancing time (integrate forward).
- **Catch-up equals live execution** bit-for-bit.
- State changes (an action) alter `λ_k` going forward without re-drawing `E_k`, which gives the "previous events influence future" property naturally.

Thresholds (`hungerCueAt`, `sleepReadyAt`) are drawn once per cycle from truncated distributions bounded by the band's `min/max`. This guarantees intervals stay inside researched ranges while varying day to day.

### 7.2 RNG
- Own **SplitMix64-seeded xoshiro256\*\*** implementation in `commonMain` (portable, serializable state; `kotlin.random.Random` state isn't serializable).
- **Per-baby, per-stream:** `streamSeed = mix(baby.seed, streamId)`. Each process has its own stream and counter. Adding a new process later doesn't perturb existing streams, so old seeds keep reproducing old schedules except where the new process interacts.
- Baby seeds are drawn at creation from a secure source and stored. Two babies never share a seed (unique constraint).

### 7.3 Numerical resolution
Drives are piecewise-linear between events. Hazard integration uses a **fixed 30 s internal grid** (deterministic, KMP-portable) inside `advance()`/look-ahead. This is internal resolution only. The OS isn't woken per grid step. Look-ahead horizon: 24 h. If nothing is predicted, `nextWakeAt = now + 6 h` as a safety re-check.

## 8. Catch-up algorithm

```
advance(state, to):
  require(to >= state.meta.lastAdvancedTo)          // backward clock jump → clamp: to = lastAdvancedTo
  t = state.meta.lastAdvancedTo
  events = []
  while true:
    tNext = min(predictedTime(p) for p in armed processes ∪ threshold crossings ∪ ongoing-action milestones)
    if tNext > to: break
    state = integrateDrives(state, t, tNext)         // deterministic, piecewise-linear
    (state, newEvents) = fire(state, eventAt(tNext)) // may arm/disarm processes, draw from per-stream RNG
    events += newEvents; t = tNext
    if events.size > MAX_EVENTS_PER_CALL: compact/rollup (very long absences)
  state = integrateDrives(state, t, to)
  state.meta.lastAdvancedTo = to
  return StepResult(state, events, nextWakeAt = predictNext(state), audio = directive(state))
```

- **Long absences** (days): the loop is O(events). A 7-day catch-up is ~1–3k events, sub-second on JVM (harness benchmark asserts < 250 ms per simulated day on a reference device class).
- **Unattended rule** (`01` B1) is applied inside the loop: crying segments cap at `crySegmentMaxMinutes` → `EXHAUSTED_SLEEP`. N unanswered requests → `AUTO_PAUSED` event, after which `advance` is a no-op until resumed.
- **Pause:** `advance` while paused moves `lastAdvancedTo` without integrating drives (needs frozen).
- **Accelerated mode** is a host concern. `SimInstant = simAnchor + (wall − wallAnchor) × speed`. The engine sees only sim time.

## 9. Audio directive (engine → arbiter)

`AudioDirective(babyId, category, intensity 0..3, priority, variationSeed, startedAt)`. Category ∈ {`SILENT`, `SLEEP`, `CONTENT_COO`, `BABBLE`, `GIGGLE`, `FUSS`, `CRY`, `CRY_INTENSE`, `SETTLING`, `BURP`}. The engine decides *what state sounds like*. The app's `AudioArbiter` decides *what actually plays* across babies (§11.6), picking clips from the manifest by age/type/intensity and avoiding immediate repeats via `variationSeed`. Escalation and stop-on-care-success come from directive changes.

## 10. Simulation harness (CLI)

`./gradlew :harness:run --args="--band M2_3 --temperament AVERAGE --days 7 --seeds 50 --policy attentive"`

**Caregiver policies** (scripted responders): `attentive` (responds in 1–3 min with the best-guess action order), `delayed` (10–20 min), `absent` (never; tests B1), `random` (random action choice; tests non-punitive dynamics).

**Report per band × temperament (24 h and 7 d, across seeds: median / p10 / p90):**
events per hour (by family) · longest gap with no audible activity (awake / overall) · longest gap with no care-required event (day / night) · care requests per day · feeds per day and interval distribution · total sleep per 24 h · naps per day · night wakings · fuss/cry minutes per 24 h · diaper changes needed per day · day-to-day schedule similarity (must stay below threshold) · **list of `DESIGN_ASSUMPTION` and `UNREVIEWED` parameters in use**.

Each metric is compared against the `06` target range and marked PASS / OUT-OF-RANGE. Output: Markdown + CSV in `reports/` (git-ignored; snapshots of reviewed reports committed under `docs/reports/`).

## 11. Multi-instance architecture (kickoff rules 1–10)

### 11.1 Ownership
```
SimulationHost(engine, params, clock, entitlements, store)
  ├── instances: Map<BabyId, BabyInstance>      // no "current baby" anywhere
  └── BabyInstance(config, state, runState{RUNNING|PAUSED|STOPPED}, timeMapping{speed, anchors})

host.advanceAll(nowWall): Map<BabyId, StepResult>
host.apply(babyId, action, nowWall): StepResult
host.create(config) // fails if activeCount >= entitlements.maxActiveBabies
```
- **Rule 1:** every event, history record, alarm request, notification request, and audio directive carries `BabyId`.
- **Rule 2:** `Engine` is stateless. `BabyInstance` owns config, seeded RNG state (inside `BabyState`), pause state and snapshot.
- **Rule 3:** no `object` with mutable state, no companion-object caches of babies, no DI scope that holds a baby. Enforced by a detekt rule/grep test on `:engine` and code review.
- **Rule 8:** `EntitlementProvider { val maxActiveBabies: Int }`. `V1EntitlementProvider` returns `1`. It is the only place to change.
- **Rule 9:** the UI layer asks the host for "the active babies" and v1 renders `first()`. There is no add-baby affordance. Onboarding creates exactly one.

### 11.2 Scheduler planner (pure, in `:engine`, rule 5)
```
plan(next: List<(BabyId, Slot, SimInstant→wall, AlarmKind, urgency)>, coalesceWindow = 60s): List<AlarmRequest>
AlarmRequest(requestCode = 1000 + slot*16 + kind.ordinal, triggerAtWall, exact: Boolean, babies: Set<BabyId>)
```
Earliest due per baby. Requests within the coalescing window are merged into the earliest one, whose wake runs `advanceAll`. Request codes are **collision-free by construction** (slot 0–15 × kind < 16). Test: property test over random inputs asserts uniqueness and the merge correctness.

### 11.3 Persistence (rule 4)
See `05`. `babies`, `baby_snapshots` (1:1), `history_events` (N:1 FK). Baby #2 = new rows only.

### 11.4 Notifications (rule 6)
`NotificationRequest(babyId, kind, id = 1000 + slot*16 + kind, tag = "baby:$id", group = "baby:$id", title includes baby name)`. Built by a pure mapper in the app's notification module.

### 11.5 Time mapping
Per-baby speed and anchors. Babies may run at different speeds (useful for tests). v1 UI exposes one.

### 11.6 Audio arbitration (rule 7)
`AudioArbiter.submit(directives: Map<BabyId, AudioDirective>) → PlaybackPlan`. Priority `CRY_INTENSE > CRY > FUSS > SETTLING > content/babble > SLEEP`. Ties go to the earliest `startedAt`. v1 has **one output source**: highest priority wins, others are dropped (logged). Step 10 can mix or alternate without API change.

### 11.7 Two-baby proof test (rule 10), `:engine` `commonTest`
Baby A: `NEWBORN`, `DEMANDING`, seed 0xA. Baby B: `M7_9`, `EASYGOING`, seed 0xB. 72 simulated hours, `attentive` policy for both, plus extra actions applied only to A.
1. **Independence:** cross-correlation of A/B care-request onset series at lags 0 ± 10 min stays below threshold, and the onset time sets are not equal.
2. **No contamination:** run B alone with the same seed → B's event stream identical to B-in-host.
3. **Action isolation:** apply 50 random actions to A → B's state and events byte-identical to the control.
4. **Deterministic replay:** re-run the whole host from seeds + action log → identical event streams and final snapshots.
5. **Persistence round-trip:** serialize both snapshots at t = 36 h → deserialize → continue to 72 h → identical to uninterrupted run. (Room round-trip repeated in M4 with the real DB.)

## 12. Worked sequence: 3-month-old crying (brief §7)

Baby "Ava", band `M2_3`, `AVERAGE`. Values illustrative.

| Time | Engine internals (hidden) | User sees / does | Event(s) |
|---|---|---|---|
| 13:55 | Feed ends: hunger 0.85 → 0.10. `hungerCueAt` drawn = 0.90 (≈ 3 h 10 m interval). Gas +0.35. User burped once, failed: gas stays 0.35 | — | `FEED_FINISHED_CUE`, `ACTION_OUTCOME(BURP, NO_BURP)` |
| 14:05–15:20 | Awake, alert. Vocal process fires coos every ~6–12 min. Sleep pressure rising | Hears coos (foreground) | `COO`×9 (audible) |
| 15:20 | `sleepPressure` crosses `sleepReadyAt` → cause TIRED. Gas 0.30 still ≥ `gasDiscomfortAt` (0.25) → cause GAS. Distress rising | — | — |
| 15:24 | distress > fuss level | Light fussing audio | `BRIEF_FUSS`-like → `activity=FUSSING` |
| 15:27 | distress ≥ careThreshold sustained 3 min (`careSustainMin`) | **Notification: "Ava is crying"** | `CARE_REQUEST_OPENED(sev=1)` |
| 15:28 | hunger = 0.47 < `feedAcceptMin` 0.6 | Taps **Feed** | — |
| 15:29 | Baby sucks briefly, turns away. Small non-nutritive comfort (< 4 m): distress −0.05, reasserts. Gas +0.02 | "Ava isn't interested in feeding right now" | `ACTION_OUTCOME(FEED, NOT_INTERESTED)` |
| 15:30 | — | Taps **Check diaper** | `ACTION_OUTCOME(CHECK_DIAPER, DRY)` ("Diaper is dry") |
| 15:31 | ROCK starts. match(ROCK, TIRED)=R, match(ROCK, GAS)=P. Settle process armed: E drawn → predicted settle 15:41 (sampled; could be 4–16 min). Upright handling arms spontaneous-burp process | Rocks the doll; app shows "Rocking…" with calming audio transition | `CARE_REQUEST_ESCALATED` suppressed while effective action ongoing |
| 15:36 | Spontaneous burp fires: gas 0.32 → 0.05. GAS cause clears, settle hazard increases → predicted settle moves earlier to 15:39 | Burp sound | `BURPED` |
| 15:39 | distress below calm threshold. `lastSoothedAt = 15:39` | Crying fades to settling sounds | `SETTLED`, `CARE_REQUEST_RESOLVED(ROCK)` |
| 15:46 | DROWSY → asleep (latency sampled) | Quiet | `FELL_ASLEEP` |
| 15:50 | User taps **Put down**. `transferSuccess(AVERAGE)` roll succeeds | Baby sleeps | `ACTION_OUTCOME(PUT_DOWN, OK)` |
| **Later behavior reflects the interaction** | Because the feed attempt added no satiety, hunger reaches 0.90 at ~17:05. The nap started late (overtired margin almost reached), so `mood` dipped slightly. The first arousal at the 16:40 cycle boundary self-settles (prob 0.55). The next arousal at ~17:05 coincides with HUNGER → wakes hungry. If the user had fed at 15:28 *anyway* and prolonged it, residual hunger would be lower and the next cue later | ~17:05 "Ava is crying" (now genuinely hungry). Feeding works | `WOKE`, `CARE_REQUEST_OPENED` |

The user never learned the cause was "tired + gas". They inferred it by elimination, exactly as in the brief.

## 13. Out of scope for the engine v1
Toddler behaviors, separation anxiety process, solids, pacifier, illness/teething/colic overlays, two-baby UI. Enum slots and schema extension points exist. Behavior is not implemented.

## 14. As built (M2.3)

Where the implementation refines this spec:

- **Tick grid.** State changes only on ticks at absolute multiples of 30 s (`TickRunner`). `advance` processes every tick in (`lastAdvancedTo`, `to`]. That makes catch-up split anywhere bit-identical (`CatchUpEquivalencePropertyTest`, 1,000 random cases).
- **Look-ahead.** `StepResult` carries `nextEventAt` (next event of any kind) and `nextWakeAt` (next alert-worthy event: care request opened or escalated, auto-pause). Both come from simulating forward up to 24 h on a copy, so they are exact (`NextWakePredictionTest`). Null when paused or nothing happens within 24 h; the host then schedules a safety re-check (M2.5). The audio directive (§9) arrives with the audio arbiter in M2.5.
- **Model constants.** Band-independent knobs (activity thresholds, cause weights, diaper rates, circadian effects, the auto-pause rule) live in `engine/params/model.json`, sourced and `UNREVIEWED` like every other value.
- **Engine API** takes the `BabyConfig` with each call: `initialize(config, at)`, `advance(config, state, to)`, `pause`, `resume`. Local time of day comes from an injected `LocalClock`.
- **Pause/resume.** While paused nothing integrates. On resume, running timers and time-since timestamps shift by the paused duration (engine time skips the pause; history keeps real times), and the unanswered-request count restarts.
- **Sleepiness.** A tired baby that is not crying grows drowsy and falls asleep on its own; crying cancels drowsiness. Tiredness becomes a distress cause only when something keeps the baby from drowsing.
- **Care actions (M2.4).** `apply(config, state, action, at)` with `CareAction.Start(FEED|HOLD|ROCK|PLAY)`, `CareAction.Stop`, `CareAction.Do(BURP|CHECK_DIAPER|CHANGE_DIAPER|PUT_DOWN_TO_SLEEP|WAKE)`. Each yields an `ACTION_OUTCOME` event with a non-punitive `ActionOutcome`.
  - The §6.4 matrix for continuous actions lives in `model.json` (`actionEffects`). While an action runs, each cause's distress is scaled: RESOLVES ×0, PARTIAL ×`partialReliefFactor`, WORSENS ×`worsenFactor`. Holding or rocking a hungry baby comforts it only for `hungerComfortMinutes`.
  - Feeding: accepted only at hunger ≥ the band's `feedAcceptMin`. Intake is sized so a full feed lasts a length sampled from the band's range. It adds gas and **ends by itself when the baby is full** (`FEED_FINISHED_CUE`). Stopping early leaves hunger behind, so the next cue comes sooner. A refused feed gives a small sucking comfort (applied at every age, a simplification of "< 4 m").
  - Rocking or holding speeds calming (`sootheFallMultiplier`, slower for harder-to-soothe temperaments). A fussy spell or need for contact settles after a sampled time (`SETTLED`). Holding upright can bring up a burp on its own. Rocking lets drowsiness start at `rockDrowsyFactor` of the usual tiredness; play prevents it.
  - Instant actions: BURP succeeds with `burpSuccessProb` when there is gas. CHECK_DIAPER only reveals. CHANGE_DIAPER clears the diaper with a small transition upset. PUT_DOWN keeps a sleeping baby asleep with probability `transferStayAsleepProb` raised to the temperament's transition sensitivity; it settles a drowsy or tired baby; otherwise the baby protests (`WANTS_TO_BE_HELD`). WAKE wakes a sleeping baby.
  - Any action counts as a response (the unanswered count resets). No repeat alerts while a continuous action runs. If the crying cap is reached while someone is caring for the baby, the request is resolved, not expired.
- **Not yet modeled:** separation anxiety, pacifier, solids (post-MVP).
