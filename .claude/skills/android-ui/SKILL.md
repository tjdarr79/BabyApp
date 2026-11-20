---
name: android-ui
description: Design and review BabyApp Android screens (Jetpack Compose + Material 3) for non-technical users, reborn doll owners and testers. Use when creating or changing any screen, wording, button or notification in the spike or the :app module, and before shipping a build to a tester.
---

# Screens for non-technical users

The people using these screens are not developers. The owner's rule: **"clear and easy to use"**. A screen that needs explaining has failed.

## Wording
- **Plain words only.** Never show internal names: T1/T2, "usage=media", "exact alarm", "FGS", targetSdk, run ids.
  - Say "alert sound", "alarm volume", and "'Alarms & reminders' permission", which is the name Android shows in Settings.
- **Tell people what to do, in order.** Use numbered, one-action steps: "Tap Start.", "Lock the phone and put it down."
- **Give times as a person would.** "About 3 min", "1 night", "The baby will cry in about 1:41".
- **Keep the "why" one tap away** ("Why this test?"). Never put it in the way.
- **Product rules from `CLAUDE.md`:** feedback is non-punitive, with no scores, points or blame. Never reveal the baby's hidden cause.

## Layout
- **One task per screen.** Each screen has one big primary button: full width, ≥ 56 dp tall.
  - Secondary actions are text buttons, such as "Skip this test" and "Start again".
- **Choices stack vertically, full width, ≥ 48 dp.** Don't put 3+ buttons side by side: "Yes, clearly" truncated to "Yes," on a 411 dp phone.
- **Handle requirements inline.** Show each one as a row with a tick, or a fix-it button that opens the exact Settings page. Disable the main button until they're met, and say why beneath it.
- **Show progress.** "Step 4 of 12", a progress bar, and a list of all steps one tap away (top-bar menu).
- **Running state:** a large status panel with a live countdown and the next physical action ("Close the app and lock the phone now").
- **Notifications:** short title, plus at most three short action labels ("Yes, clearly" / "Faint" / "No"). The app can use longer sentences.

## Look
- Material 3, with our fixed calm palette (`spike/.../ui/Theme.kt`: nursery teal and peach), so every phone looks the same as the screenshots.
- Both light and dark mode must work.
- Use Material icons (`material-icons-core`) in a tonal circle as each step's badge. The app has a launcher icon (white heart on teal); never ship without one.
- Body text is `bodyLarge`. Don't go below `bodyMedium` except the footer.

## Accessibility
- Every icon-only button has a `contentDescription`. Decorative icons use `null`.
- Touch targets are ≥ 48 dp. Text wraps rather than truncates (no `maxLines = 1` on labels that can grow).
- Re-read state on resume, so a permission granted in Settings shows as a tick when the user comes back.
  - **Compose strong skipping (Kotlin 2.x default) skips a child whose arguments are the same instances.** A composable that reads state from *outside* Compose (permissions, SharedPreferences, the log) must take a parameter that changes on every refresh. The spike passes `refresh` (a fresh `System.nanoTime()` per `TestApp` recomposition).
  - Without it, a permission allowed in Settings only showed after the app restarted. Testers had to toggle every setting twice (m3-3, fixed in m3-4).
  - Keep `PermissionRefreshTest` green: it fails if this regresses.

## Verify before shipping (required)
1. Add or adjust a Roborazzi test in `spike/app/src/test/.../ScreenshotTest.kt` for each changed screen, seeding Prefs to the state shown. Include one dark-mode shot.
2. `./gradlew testT37DebugUnitTest -q`, then **open every PNG** with the Read tool and check for:
   - truncated or overlapping text;
   - cramped rows;
   - jargon;
   - a missing primary action;
   - contrast in dark mode.
3. Fix what you find and re-render. Only then build and upload the APK.
