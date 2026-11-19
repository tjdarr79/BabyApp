# 01 — Requirements Gaps, Ambiguities, and Brief Conflicts

**Status:** Draft for owner review · **Source of truth:** `docs/PROJECT_BRIEF.md`
**Convention:** Each item has a *Proposed default* (what we build unless told otherwise) and an *Open question*. Items marked **CONFLICT** are places where the brief, the kickoff (`docs/00-kickoff.md`), or verified platform facts disagree. They are flagged here, not silently resolved.

Priority: **P0** = blocks Milestone 2 (engine). **P1** = blocks Steps 3–5. **P2** = blocks release (Steps 6–9).

## Owner decisions (2026-11-19)

| Item | Decision |
|---|---|
| A1 age-band gap | **No 5–8 week band.** Bands stay exactly as in the brief. The "2–3 months" band is parameterized for ~8–13 weeks. Weeks 5–8 are not separately simulated |
| A3 platform spike | **Approved.** M3 runs after the engine milestone, before Step 3 |
| B1 unattended baby | **Approved as proposed:** auto-pause after 3 consecutive unanswered care requests or 4 h without response. No penalty |
| Q-06-1 crying amount (`06`) | **Target the lower half of real-infant norms.** Revisit during real-device testing (M9) and raise if testers want more |
| A4 audio budget | **Research sound libraries.** See `08-audio-sourcing.md` |
| Newborn band review | **Childcare reviewer, 17 of 20 values applied** (7 kept, 10 changed toward a doll-friendly simulation). Held: `sleepCycleMinutes` (reviewer meant nap length 2–3 h, which the cycle length doesn't set), `fussCryMinutesPer24h` (reviewer wrote "3": 3-minute spells 16:00–24:00, or 3 minutes per day?), `crySegmentMaxMinutes` 5 (requests would expire after 5 min, so an away caregiver auto-pauses on day 1) |

---

## A. Conflicts to resolve

| # | Pri | Conflict | Proposed default | Open question |
|---|---|---|---|---|
| A1 | P0 | **Age-band gap.** Brief §5 jumps from "Newborn, 0–4 weeks" to "2–3 months". Weeks 5–8 belong to no band. This is also the period where AAP/CDC feeding intervals and the crying data (Wolke 2017, see `06`) change most. | Treat the "2–3 months" band as covering **5–13 weeks**. Its label stays "2–3 months" until confirmed. | Should 5–8 weeks be its own band ("1 month"), be absorbed into newborn, or be absorbed into 2–3 months? |
| A2 | P0 | **Toddler in MVP?** Brief §5 lists Toddler 13–24 m as an "initial age profile". The same section and §15 Step 11 say to implement it only after infant simulation is reliable. | Toddler is **out of MVP UI**. The parameter schema reserves the band and `06` documents researched ranges, but nothing in the app is selectable. | Confirm toddler stays out of the first paid release. |
| A3 | P1 | **Background behavior "early" vs. build order.** Brief §9 says background execution "must be addressed early". Brief §15 places it in Step 5, and the kickoff forbids notifications and audio before the engine milestone. Platform research (`03`) found the biggest technical risk is here: Android 15/17 background-audio rules. | Insert a **throwaway platform spike (M3)** after the engine milestone and before Step 3. It is device testing only, with no production code merged, and it validates alarms, notifications and background audio. See `07`. | Approve inserting M3 ahead of Step 3? |
| A4 | P1 | **Brief audio sources vs. a paid app.** The brief's audio addendum names Freesound, Pixabay and ZapSplat as sources of "commercially licensed recordings". Freesound licenses are per-sound, and **CC BY-NC sounds cannot be used in a paid app** ([cinevva guide](https://app.cinevva.com/guides/free-sound-effects-music)). ZapSplat's free tier requires attribution, and secondary sources disagree on its commercial scope ([ZapSplat licence](https://www.zapsplat.com/license-agreement), [licenseorg](https://licenseorg.com/guide/music-audio/zapsplat)). Pixabay allows use inside an app but bars standalone redistribution ([Pixabay FAQ](https://pixabay.com/service/faq/)). | Accept only **CC0, Pixabay, or paid/commercially cleared licenses**. Accept CC BY only if the attribution is in-app. Reject NC, ND and "personal use". Record each file in the asset manifest with license evidence. | Is there a budget for a paid library or original recordings if CC0 infant audio of sufficient quality isn't available? |
| A5 | P1 | **Recordings of real infants.** Pixabay warns that identifiable people and audio samples can carry third-party rights ([Pixabay FAQ](https://pixabay.com/service/faq/)). Crying recordings are recordings of a real child. | Prefer recordings whose uploader states they made the recording themselves. Log that statement in the manifest. | Do you want a legal review of the audio set before launch? |
| A6 | P2 | **Training Mode vs. hidden cause.** Brief §7 says the user should not always know why the baby cries. Brief §11 says Training Mode "explains possible baby cues". | Training Mode shows **cue hints derived from observable state** (e.g. "rooting and hand-to-mouth are common hunger cues"). It never shows the engine's hidden cause directly. | Should Training Mode ever reveal the true cause after an episode resolves (a post-hoc "what was it")? |
| A7 | P2 | **Full-screen alerts.** Users may expect alarm-style takeover screens for crying. Play restricts default full-screen-intent permission to calling and alarm apps on Android 14+ ([Play FSI/FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en), [AOSP FSI limits](https://source.android.com/docs/core/permissions/fsi-limits?hl=en)). | Do **not** declare `USE_FULL_SCREEN_INTENT`. Use high-importance heads-up notifications. | — (informational) |

## B. Behavior engine gaps (P0 unless noted)

| # | Gap | Proposed default | Open question |
|---|---|---|---|
| B1 | **Unattended baby.** The brief doesn't say what happens when the simulation is running and the user doesn't respond for hours (asleep, at work, phone off). Brief §8 requires non-punitive feedback. | Needs saturate at a cap. A crying episode has a maximum length, after which the baby falls into **exhausted sleep**. Missed requests are logged neutrally ("Care request not answered, 02:10–02:40"). After **3 consecutive unanswered care requests or 4 h without any response**, the simulation **auto-pauses** and posts one gentle notification. No penalty score. | Is auto-pause desirable, or should the baby keep running indefinitely? What threshold? |
| B2 | **Night-time realism vs. user sleep.** Real newborns wake 1–2+ times a night (`06`). Some users will want that and some won't. | Normal Care simulates real night wakings. A **Quiet Hours** setting (brief §9) offers three behaviours: (a) alerts silenced and baby simulated as cared for by "another caregiver", (b) baby sleeps through, or (c) full realism. Default: (c), with Quiet Hours offered at onboarding. | Which Quiet Hours behaviours do you want? Is "another caregiver" acceptable wording? |
| B3 | **Feeding method.** Breast vs. bottle changes interval, duration and burping norms (AAP, see `06`). Reborn users typically use a toy bottle. | Single **"Feed"** action modelled on **bottle feeding** (CDC/AAP formula ranges). Duration is user-controlled with an engine-determined "finished" cue. | Add a "nursing" option later? |
| B4 | **Feed amount.** Should the user choose an amount (oz/mL)? | No amount entry. The engine decides how much the baby takes based on hunger. The user just starts and ends the feed. An early end means a partial feed and an earlier next hunger cue. | Confirm no amount entry. |
| B5 | **Solids (complementary feeding).** Brief §8 lists it as a "later stage" activity, but 7–12 m babies eat solids ([CDC solids](https://www.cdc.gov/infant-toddler-nutrition/foods-and-drinks/when-what-and-how-to-introduce-solid-foods.html)). | Out of MVP. For 7–12 m bands, "Feed" represents milk feeds only. Total feeds/day follow CDC's 5–6/24 h ([CDC](https://www.cdc.gov/infant-toddler-nutrition/formula-feeding/how-much-and-how-often.html)) minus an implicit solids share. | Is a "Meal" action needed for 7–12 m in the first release? |
| B6 | **Pacifier.** Brief lists it as later. | Out of MVP. The action exists in the engine's action enum but is not exposed. | — |
| B7 | **Day/night anchor.** The circadian modulation needs a local "night". | Night = 19:00–07:00 local by default, adjustable as "Baby's bedtime" in the profile. Circadian strength ramps from ~0 in newborns to strong by 4–6 m (the circadian rhythm emerges ~2–3 m per [BC Children's](https://www.bcchr.ca/healthysleepforkids/newborns)). | Do you want a user-set bedtime in v1? |
| B8 | **Time-zone change / clock change.** Not specified. | Simulation time = wall-clock epoch. The circadian rhythm follows *current* local time, like a travelling baby with instant jet-lag adaptation. A backward clock jump is clamped (the engine never runs backward). | — |
| B9 | **Changing age mid-simulation.** Brief Step 3 says age can change without losing the profile. It doesn't say what happens to live state. | History is preserved. Normalized needs (hunger, fatigue, etc.) carry over, and thresholds are re-sampled from the new band. The change applies immediately. | — |
| B10 | **Changing temperament mid-simulation.** Not specified. | Same as B9. | — |
| B11 | **Illness, teething, growth spurts, colic.** Brief §6 defers colic and reflux. | Out of MVP. Schema supports future "condition overlays" as multiplicative modifiers, like temperaments. | — |
| B12 | **"Missed/delayed care" definition.** Brief §12. | *Delayed* = first effective action > 10 min after care request. *Missed* = request expired (B1) with no effective action. Both are neutral log entries. | Are 10 min / expiry the right thresholds? |
| B13 | **Diaper "check" as an action.** Brief §7 scenario has the user "check the diaper". | `CHECK_DIAPER` is a free information action (dry / wet / soiled) with no state change. | — |
| B14 | **Engine realism review process.** Brief §8 says the childcare reviewer reviews behavior. | Every parameter carries `source` + `reviewStatus`. The harness generates per-band reports (`04` §10). The reviewer signs off per band. Status flows `UNREVIEWED → REVIEWED_OK / REVIEWED_CHANGED`. | How does the reviewer prefer to review: spreadsheet export, printed report, or in-app accelerated runs? |

## C. Product / UX gaps

| # | Pri | Gap | Proposed default | Open question |
|---|---|---|---|---|
| C1 | P1 | **Onboarding flow.** Not specified. | 3 screens: name → age band → temperament. Then a permissions explainer (notifications, exact alarms, battery). Then start. | — |
| C2 | P1 | **Simulation lifecycle.** Brief has start, stop, pause and resume but doesn't define "stop". | **Pause** freezes needs (no accumulation). **Stop/Reset** ends the session and keeps the profile and history. Pause has an optional auto-resume time. | Should pausing be time-limited for realism? |
| C3 | P1 | **Accelerated Mode speeds.** Brief §11 doesn't specify speeds. | 2×, 4×, 10× for users. A hidden 60×+ developer speed. Notifications still fire at scaled wall-clock times. | Which speeds should users see? |
| C4 | P2 | **History retention.** Not specified. | Keep 30 days of detailed events per baby, then roll up to daily summaries. | — |
| C5 | P2 | **Backup / device transfer.** Not specified. Brief §12 says no accounts. | Enable Android Auto Backup for the DB (no account needed). Paused-on-restore: a restored baby starts paused. | — |
| C6 | P2 | **Accessibility.** Brief §10 says "accessible contrast" only. | Material 3 contrast, TalkBack labels, haptics-only alert option for users who can't hear alerts. | — |
| C7 | P2 | **Localization.** Not specified. | English only in v1. Strings externalized from day one. | Target markets beyond the US? |
| C8 | P2 | **Privacy policy / Data safety.** Required by Play (brief Step 9). | No data collected or shared, no analytics SDKs (kickoff). The Data safety form declares none. | — |
| C9 | P2 | **Content rating / audience.** Brief §2 targets adults. A doll-care app may draw Play "Families" scrutiny. | Target audience 18+ in Play Console. No child-directed design. | Confirm adult audience. Families policy compliance is a significant extra burden. |
| C10 | P2 | **Competitor-complaint validation.** Brief Step 1 says to review BabySitMe complaints. **Not done this session.** Play review pages weren't fetched or verified. | Owner or reviewer to sample ~50 recent reviews before M7. | — |
| C11 | P2 | **Realityworks study.** Brief §4 says to study frequency and difficulty concepts. **Not done this session.** The PDFs weren't fetched. Per the kickoff, they are concept references only, never data. | Review once before calibration (M2.5) for *concepts only*: difficulty tiers and the existence of care-free windows. | — |

## D. Platform gaps (summary; detail in `03`)

| # | Pri | Gap | Proposed default |
|---|---|---|---|
| D1 | P1 | Exact-alarm permission is **denied by default** on Android 14+ for new installs ([Android 14 change](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)). `USE_EXACT_ALARM` is Play-restricted to alarm and calendar apps ([Play policy](https://support.google.com/googleplay/android-developer/answer/16558241)). | Request `SCHEDULE_EXACT_ALARM` with an explainer. Degrade gracefully to inexact `setAndAllowWhileIdle`. |
| D2 | P1 | Background audio playback is restricted on Android 17 (all apps) unless there's a visible activity or a non-`shortService` foreground service ([Android 17 bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)). | Tiered audio strategy (`03` §7). Validate in M3 spike. |
| D3 | P2 | Required target API: **new apps and updates must target API 36** as of Aug 31 2026 ([Play target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)). | `targetSdk = 36`. Plan for API 37 within ~12 months. |
