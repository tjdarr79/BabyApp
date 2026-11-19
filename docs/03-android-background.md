# 03 — Android Background Execution, Notifications, Audio, and Play Policy

**Researched:** 2026-11-19 · **Verification note:** developer.android.com pages were read directly or via search excerpts. **support.google.com (Play policy) pages could not be fetched from this environment (DNS failure); Play policy statements below come from search-result excerpts of those pages and must be re-read on the live page before Play submission (milestone M10).**

---

## 1. Strategy summary

The app **does not keep a long-running process alive** to simulate the baby. Instead:

1. The engine is a pure function: `state(t₁) = advance(state(t₀), t₁, actions)`. It also returns `nextWakeAt`, the earliest time anything user-perceivable can happen (`04` §8).
2. After every engine step, the **scheduler** registers OS alarm(s) for the merged next-due times across all active babies (`04` §11).
3. When an alarm fires, a `BroadcastReceiver` loads snapshots, catches each baby up to `now`, posts notifications, starts audio if allowed, persists, and reschedules.
4. On app open, boot, package update, time change, or exact-alarm-permission grant, the same catch-up runs.

Simulation correctness therefore never depends on alarm punctuality. **Alarm punctuality only affects when the user hears about it.** A late alarm produces a correct state ("crying for 9 min") and a delayed alert, never a corrupted simulation.

## 2. Target API level

- New apps and updates must target **Android 16 (API 36)** to be submitted to Google Play, and all apps must meet the latest requirements by Aug 31 2026 ([Play Console Help – target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [Policy announcement Jul 15 2026](https://support.google.com/googleplay/android-developer/answer/17134731?hl=en)).
- Plan: `targetSdk 36` at launch. **Budget for API 37 (Android 17) the following year**, which triggers the stricter background-audio rule in §7.

## 3. Exact alarms

| Fact | Source |
|---|---|
| `SCHEDULE_EXACT_ALARM` is **no longer pre-granted** to most newly installed apps targeting Android 13+ on Android 14+, so it starts **denied**. | [Android 14: exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) |
| Without it, `setExact()`, `setExactAndAllowWhileIdle()` and `setAlarmClock()` throw `SecurityException`. | same |
| Check with `canScheduleExactAlarms()`. If denied, fall back to `setAndAllowWhileIdle()` / `setWindow()`. | same; [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) |
| `SCHEDULE_EXACT_ALARM` is user-granted and can be revoked by the user or system. On revocation, **all exact alarms are deleted** and no broadcast is sent. On grant, `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is sent. | [AlarmManager reference](https://developer.android.com/reference/android/app/AlarmManager), [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) |
| `USE_EXACT_ALARM` is auto-granted but Play allows it **only for alarm/timer apps and calendar apps with event notifications**. Non-qualifying apps are disallowed from publishing. A Play Console declaration is required. | [Play – Permissions & APIs that access sensitive info](https://support.google.com/googleplay/android-developer/answer/16558241), [Developer Program Policy](https://support.google.com/googleplay/android-developer/answer/18265346?hl=en) |
| Exact alarms set via an `OnAlarmListener` don't need the permission, but only fire while the process is alive, so they're useless for a killed app. | [Android 14 change page](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) |

**Eligibility verdict:** This app is **not** an alarm clock or calendar. → **Do not declare `USE_EXACT_ALARM`.** Declare `SCHEDULE_EXACT_ALARM`, explain it in onboarding ("so your baby's cries arrive on time"), and degrade gracefully.

**Alarm API choice per event class:**

| Event class | API (permission granted) | API (permission denied) |
|---|---|---|
| Care-required onset (crying) | `setExactAndAllowWhileIdle` | `setAndAllowWhileIdle` (deferrable in Doze) |
| Audible-only activity (coo, babble, sleep sounds) | **No alarm.** Only rendered while the app is foreground or a playback session is active (§7) | same |
| Housekeeping (history rollup) | none / WorkManager | same |

`setAlarmClock()` is **not** used. It surfaces as a user-visible alarm clock in system UI, which misrepresents the app. Its FGS-start privilege is matched by the exact-alarm exemption (§6).

## 4. Doze and App Standby buckets

| Fact | Source |
|---|---|
| In Doze, standard alarms defer to maintenance windows. `setAndAllowWhileIdle()` / `setExactAndAllowWhileIdle()` can fire during Doze. | [Doze & App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) |
| Frequency of while-idle alarms is capped. Docs differ: "not more than once per nine minutes per app" (Doze guide) vs. "~every minute, up to ~15 min in low-power idle" ([AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)) vs. "7 per hour" in Doze ([Power limits](https://developer.android.com/topic/performance/power/power-details)). | as cited |
| Bucket limits: **Active / Working set: no alarm limits. Rare: 1 alarm/hour. Restricted: 1 alarm/day.** Bucket limits apply only on battery, except Restricted. | [Power limits](https://developer.android.com/topic/performance/power/power-details), [App Standby Buckets](https://developer.android.com/topic/performance/appstandby) |
| Android 16 ties job runtime quotas to standby bucket and to FGS co-running. | [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all) |

**Implications**
- A care-due baby typically needs ≤ 1 alert per 1–3 h. That is far below every documented cap. **Engine design rule: never require more than one care alert per baby per 10 minutes.** The scheduler coalesces alerts within a 60 s window across babies (`04` §11).
- An app the user opens several times a day stays in Active/Working Set. **Risk:** a user who leaves the baby unattended for days drifts toward Rare. B1 auto-pause in `01` covers this.
- Don't use WorkManager/JobScheduler for simulation timing. Job quotas don't fit second-level timing.

## 5. Notifications

| Fact | Source |
|---|---|
| `POST_NOTIFICATIONS` is a **runtime (dangerous) permission on Android 13+**. On fresh install notifications start **off**. If denied, all channels are blocked. | [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [Android 13 changes](https://developer.android.com/about/versions/13/behavior-changes-all) |
| Channels are mandatory on 8.0+. Importance and **sound are fixed at channel creation**. The user can change them, and the app can only lower importance. | [Channels](https://developer.android.com/develop/ui/compose/notifications/channels), [NotificationChannel.setSound](https://developer.android.com/reference/android/app/NotificationChannel) |
| An FGS doesn't need `POST_NOTIFICATIONS` to start, but its notification is hidden from the drawer if the permission is denied. | [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) |

**Channel design (v1)**: channel IDs are versioned so sound changes ship as new channels.

| Channel ID | Importance | Sound | Purpose |
|---|---|---|---|
| `care_alert_v1` | HIGH (heads-up) | short cry clip, `USAGE_NOTIFICATION` | Care-required onset / escalation |
| `baby_status_v1` | LOW | none | Woke, fell asleep, finished feeding (optional, user toggle) |
| `playback_v1` | LOW | none | Ongoing notification for the playback FGS (§7) |
| `sim_service_v1` | MIN | none | Auto-pause, permission problems |

**Per-baby identity (kickoff rule 6):** notification `id = 1000 + slot*16 + kind.ordinal`, `tag = "baby:<BabyId>"`, `group = "baby:<BabyId>"`. Content always names the baby. Step 10 can add per-baby channels `care_alert_b<slot>_v1` with distinct sounds without touching IDs.

## 6. Foreground services

| Fact | Source |
|---|---|
| Apps targeting 14+ must declare an FGS type, or `startForeground` throws. | [FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required) |
| `mediaPlayback`: needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, for background audio/video playback. Apps targeting 15+ **cannot start it from `BOOT_COMPLETED`**. | [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android 15 FGS changes](https://developer.android.com/about/versions/15/changes/foreground-service-types) |
| `shortService`: ~3 min limit, can't start other FGS. | [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types) |
| `specialUse`: catch-all. Requires a manifest `<property>` describing the use, **reviewed by Google Play**, and "may be restricted". | same; [Play FGS/FSI requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) |
| `systemExempted`: listed for apps holding exact-alarm permissions, narrowed by the A14 page to apps **continuing alarms** in the background. | [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required) |
| Apps targeting 12+ can't start an FGS from the background except for listed exemptions. These include: **"Your app invokes an exact alarm to complete an action that the user requests"**, user interaction with a notification/widget, `BOOT_COMPLETED`, and "the user turns off battery optimizations for your app". | [Background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) |
| **The exact-alarm exemption is not on the while-in-use (WIU) exemption list.** Notification interaction is. | same (WIU section) |
| Play FGS policy: Android 14+ apps must declare each FGS type in Play Console. Use must be user-beneficial, user-initiated or user-perceptible, and only as long as necessary. | [Play FGS/FSI requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) |

**Does this app qualify?**

| Type | Qualifies? | Use |
|---|---|---|
| `mediaPlayback` | **Yes, while audible baby audio is actually playing** (crying, fussing, ambient sleep sounds the user chose) | Background crying/fussing playback. Optional user-started "Baby monitor" ambient session (§7 tier 3) |
| `shortService` | Technically, but **useless for audio on Android 17** (excluded from the audio rule) | Not used |
| `specialUse` | Weak case ("keep virtual baby simulation running") and Play-reviewed | **Not used.** The architecture doesn't need it |
| `systemExempted` | Doubtful: we aren't an alarm app | Not used |
| `dataSync`, others | No | Not used |

## 7. Audio focus and background audio (highest technical risk)

| Fact | Source |
|---|---|
| **Android 15:** apps targeting API 35+ can request audio focus **only if top app or running an FGS**. Otherwise `AUDIOFOCUS_REQUEST_FAILED`. | [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15), [Audio focus](https://developer.android.com/media/optimize/audio-focus) |
| **Android 17, all apps regardless of target:** background playback, focus and volume changes require a **visible activity or an FGS that is not `shortService`**. | [Android 17 background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio) |
| **Android 17, apps targeting API 37:** in background, the FGS must have **while-in-use capability**. Waived only if the app holds the exact alarm permission **and** plays streams with `USAGE_ALARM`. | same |
| Failures are **silent** for playback (no exception). Focus returns `AUDIOFOCUS_REQUEST_FAILED`. A `BOOT_COMPLETED`-started service that touches audio "will be suppressed". Media3 `MediaSessionService` apps are "not likely to be impacted". | same |
| The page **does not mention notification sounds**; they aren't in the affected-API table. | same (absence noted. **Inference, verify in M3**) |
| The page has an apparent internal contradiction: its testing section says `set-enable-hardening enable` enforces WIU "even if… alarm streams and has the exact alarm permission". | same |

**Tiered audio strategy**

| Tier | When | Mechanism | Android 17 / target 37 status |
|---|---|---|---|
| **T0 Foreground** | App visible | In-app `AudioArbiter` → Media3 player. Full escalation, variation, transitions | Always allowed |
| **T1 Notification sound** | Any care onset in background | `care_alert_v1` channel sound (short cry clip). The system plays it, not our process | Believed unaffected (not in API table). **Verify in M3** |
| **T2 Alarm-started playback** | Care onset in background, exact-alarm permission granted | Exact alarm → receiver starts `mediaPlayback` FGS (exact-alarm FGS-start exemption) → plays escalating cry until care or cap → stops | A17 any-target: OK (non-short FGS). **Target 37: no WIU → suppressed unless `USAGE_ALARM` waiver applies.** Option: play cries with `USAGE_ALARM` (respects alarm volume). **Verify in M3** |
| **T3 User-started monitor session** | User enables "Baby monitor sounds" from the foreground (ambient breathing / white noise while baby sleeps) | `MediaSessionService` started from foreground → has WIU. Cries play inside the same session | Allowed (WIU from foreground start). Real media playback, so it fits `mediaPlayback` policy. Costs battery while running |
| **T4 Notification tap** | User taps the alert | Opens activity → T0 | Always allowed |

**Default v1 configuration:** T0 + T1 always. T2 when exact alarms are granted. T3 opt-in. The **M3 spike decides** whether T2 uses `USAGE_MEDIA` or `USAGE_ALARM` and whether it survives target 37.

## 8. Full-screen intents

Android 14+ limits the default `USE_FULL_SCREEN_INTENT` grant to **calling and alarm apps**. Play revokes it for others at install. Since Jan 22 2025, apps targeting 14+ without those functions must obtain user permission ([AOSP FSI limits](https://source.android.com/docs/core/permissions/fsi-limits?hl=en), [Play FGS/FSI requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)). → **Not used.** Heads-up notifications only.

## 9. OEM battery killers and battery-optimization exemption

- OEM layers (notably Samsung "sleeping/deep sleeping apps" and Xiaomi autostart/battery saver) defer alarms and kill services beyond AOSP behavior. Standard exemptions don't necessarily prevent this ([dontkillmyapp.com](https://dontkillmyapp.com), [Android Central on Samsung](https://www.androidcentral.com/samsung-aggressively-killing-background-apps-android-11-theres-easy-fix), [DEV: surviving battery killers](https://dev.to/szymonwalczak/surviving-android-battery-killers-in-capacitor-apps-4im7)).
- `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (direct exemption prompt) is reserved for apps meeting acceptable use cases. Generally that means Doze breaks *core function* and FCM can't be used ([Doze & App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)). **Our claim is arguable, which makes it a Play risk.** → v1 deep-links to the **general battery-optimization settings** screen with per-OEM instructions (sourced from dontkillmyapp.com) and doesn't declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Side benefit: if the user turns off battery optimization, that is itself an FGS background-start exemption (§6).
- **In-app reliability self-check:** the app compares each alarm's scheduled time with its actual fire time and stores the lateness. If p90 lateness exceeds 5 min, the app suggests battery settings. Local only, no analytics.

## 10. Boot, restart, and recovery

| Trigger | Action | Source |
|---|---|---|
| `BOOT_COMPLETED` (only after first user launch) | Catch-up + reschedule alarms. **Don't start `mediaPlayback` FGS** (forbidden for target 35+). Notification sound (T1) only | [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), [Android 15 FGS](https://developer.android.com/about/versions/15/changes/foreground-service-types) |
| `MY_PACKAGE_REPLACED` | Same as boot (alarms survive updates, but reschedule defensively) | [Background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) |
| `TIME_CHANGED`, `TIMEZONE_CHANGED` | Catch-up (clamp backward jumps), recompute circadian, reschedule | same |
| `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | Re-check `canScheduleExactAlarms()`, reschedule | [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager) |
| Permission revoked (no broadcast) | Detected at next app open / any receiver. Reschedule as inexact | same |
| Process death | Nothing lost. State lives in Room, and alarms are owned by the system | — |
| App open | Catch-up for all babies before first frame of dashboard data | — |

Reboot clears alarms ([AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)), so persistence plus the boot receiver is mandatory.

## 11. Play policy risk register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| P1 | Declaring `USE_EXACT_ALARM` → rejection | High if declared | Release blocked | **Don't declare.** Use `SCHEDULE_EXACT_ALARM` |
| P2 | `mediaPlayback` FGS judged not "media" or running "longer than necessary" | Medium | Rejection / policy strike | Only run while audible audio plays. T3 is user-started with a visible stop control. Clear declaration video |
| P3 | `specialUse` FGS | High if used | Rejection | **Not used** |
| P4 | `USE_FULL_SCREEN_INTENT` | High if declared | Permission revoked / rejection | **Not used** |
| P5 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Medium | Rejection | **Not declared.** Settings deep link only |
| P6 | Audio license violations (NC/ND clips) | Medium | Takedown | License gate in asset manifest (`01` A4) |
| P7 | Families / child-directed classification | Low–Medium | Extra compliance burden | Target audience 18+ (`01` C9) |
| P8 | Data safety form mismatch | Low | Rejection | No SDKs that collect data |
| P9 | Target API drift (37 required ~2027) triggers A17 WIU audio rule | **High (scheduled)** | Background crying may go silent | M3 spike validates `USAGE_ALARM` path and T3 fallback before launch |

## 12. M3 spike test matrix (validation, not production code)

Devices: Pixel (stock A16, A17), Samsung (One UI, current), Xiaomi/Redmi (HyperOS), one low-end Android 10–12 device.
Scenarios × {exact granted, exact denied} × {target 36, target 37 via compat flags / `adb shell cmd audio set-enable-hardening`}:
1. Screen off 30 min / 2 h / overnight (Doze): alarm lateness distribution.
2. App swiped away → alarm → notification sound → T2 playback audible?
3. Reboot → boot receiver catch-up → notification only.
4. Battery saver on. Samsung "sleeping apps". Xiaomi autostart off.
5. T3 session for 8 h: battery drain %.
Output: `docs/spikes/M3-platform-findings.md` with go/no-go per tier.
