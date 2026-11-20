package dev.babysim.spike

import android.content.Context
import android.os.Build

/** What a step needs before it can start; the screen shows a fix-it button for each unmet one. */
enum class Need { NOTIFICATIONS, EXACT_ON, EXACT_OFF, POWER_SAVE_ON, CONFIRM }

enum class StepIcon { HELLO, SETUP, SOUND, ALERT, ALARM, LATE, RESTART, BATTERY, SAMSUNG, NIGHT, MONITOR, CLIPS, SEND }

/**
 * One guided test. [condition] is written on every log row the step schedules, so the tester never types a
 * label. [inShort] steps make up the 10-minute extra test in the target-36 app.
 */
data class Step(
    val id: String,
    val icon: StepIcon,
    val title: String,
    val time: String,
    val intro: String,
    val howTo: List<String>,
    val why: String,
    val startLabel: String,
    val condition: String = "normal",
    val needs: List<Need> = emptyList(),
    val confirmText: String = "",
    val inShort: Boolean = false,
    val samsungOnly: Boolean = false,
    val optional: Boolean = false,
    val afterText: String = "",
)

sealed interface StepState {
    data object NotStarted : StepState

    data class Running(
        val startMs: Long,
        val dueMs: Long,
    ) : StepState

    data object Done : StepState

    data object Skipped : StepState
}

object Steps {
    const val WELCOME = "welcome"
    const val SETUP = "setup"
    const val SOUND = "sound"
    const val ALERT_MEDIA = "alertmedia"
    const val ALERT_ALARM = "alertalarm"
    const val ALERT_LATE = "alertlate"
    const val RESTART = "restart"
    const val BATTERY_SAVER = "batterysaver"
    const val SAMSUNG_SLEEP = "samsungsleep"
    const val SAMSUNG_FREE = "samsungfree"
    const val OVERNIGHT = "overnight"
    const val MONITOR = "monitor"
    const val CLIPS = "clips"
    const val RESULTS = "results"

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    val ALL =
        listOf(
            Step(
                WELCOME, StepIcon.HELLO, "Welcome", "1 min",
                "Thank you for helping test Baby Sim on this phone. The app will make the phone cry like a baby at set times. Each test tells you exactly what to do.",
                listOf(
                    "Keep the phone's sound on (not silent, not Do Not Disturb).",
                    "Some tests take a few minutes, two of them run overnight.",
                    "You can skip any test.",
                ),
                "We need to know whether a real phone wakes up and plays sound on time when the app is closed. Only a real phone can tell us.",
                "Let's start", inShort = true,
            ),
            Step(
                SETUP, StepIcon.SETUP, "Allow alerts", "1 min",
                "The app needs two permissions to make the test baby cry on time.",
                listOf("Tap each button below and allow it.", "Come back here when both show a tick."),
                "Notifications let the baby alert you. 'Alarms & reminders' lets it wake the phone at the exact minute.",
                "Continue", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON), inShort = true,
            ),
            Step(
                SOUND, StepIcon.SOUND, "Sound check", "1 min",
                "Let's make sure you can hear the alert sound.",
                listOf("Turn the volume up.", "Tap Play. You should hear a short baby cry."),
                "This is the sound the app will use when the baby needs care.",
                "Play the alert sound", needs = listOf(Need.NOTIFICATIONS), inShort = true,
            ),
            Step(
                ALERT_MEDIA, StepIcon.ALERT, "Alert while the phone is locked", "3 min",
                "The phone will start crying 2 minutes after you tap Start, even with the app closed.",
                listOf(
                    "Tap Start.",
                    "Close the app: swipe it away from your recent apps.",
                    "Lock the phone and put it down.",
                    "When it cries, listen. Tap a button on the notification to say what you heard.",
                ),
                "This checks the alert sound and the longer crying when the app is not open (normal media volume).",
                "Start", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON), inShort = true,
            ),
            Step(
                ALERT_ALARM, StepIcon.ALARM, "Same test, alarm volume", "3 min",
                "Same as the last test, but the crying uses the phone's alarm volume.",
                listOf(
                    "Tap Start.",
                    "Close the app: swipe it away from your recent apps.",
                    "Lock the phone and put it down.",
                    "When it cries, tap a button on the notification.",
                ),
                "Newer Android versions treat alarm sounds differently from music. We need to know which one works.",
                "Start", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON), inShort = true,
            ),
            Step(
                ALERT_LATE, StepIcon.LATE, "Alert without the alarm permission", "20 min",
                "Some people will turn the 'Alarms & reminders' permission off. Let's see how late the baby cries then.",
                listOf(
                    "Tap the button below and turn 'Alarms & reminders' OFF for this app.",
                    "Tap Start, close the app, lock the phone.",
                    "The alert is due in 15 minutes but may come later. That's what we're measuring.",
                    "Afterwards, turn the permission back ON (we'll remind you).",
                ),
                "Without the permission Android delays alarms to save battery. This shows by how much.",
                "Start", condition = "exact-denied", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_OFF),
                afterText = "Now turn 'Alarms & reminders' back ON for this app.",
            ),
            Step(
                RESTART, StepIcon.RESTART, "Restart the phone", "35 min",
                "Phones sometimes forget alarms when they restart. Let's check this one doesn't.",
                listOf(
                    "Tap Start.",
                    "Restart the phone (hold the power button, then Restart).",
                    "Unlock it once, then leave it.",
                    "Come back after 35 minutes and answer the question.",
                ),
                "The real app must keep caring for the baby after a restart.",
                "Start", condition = "reboot", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON),
            ),
            Step(
                BATTERY_SAVER, StepIcon.BATTERY, "Power saving mode", "30 min",
                "Many people use power saving. Let's see whether the baby still cries on time.",
                listOf(
                    "Tap the button below and turn Power saving ON.",
                    "Tap Start, close the app, lock the phone.",
                    "The baby cries after about 15 minutes. Answer on the notification.",
                    "Come back after 30 minutes, then turn Power saving off.",
                ),
                "Power saving can delay alarms and stop sounds.",
                "Start", condition = "battery-saver", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON, Need.POWER_SAVE_ON),
                afterText = "You can turn Power saving off now.",
            ),
            Step(
                SAMSUNG_SLEEP, StepIcon.SAMSUNG, "Samsung: sleeping apps", "30 min",
                "Samsung phones can put apps 'to sleep'. Let's see what that does.",
                listOf(
                    "Open Settings › Battery › Background usage limits › Sleeping apps, and add this app (Baby Sim test).",
                    "Come back, tick the box below, tap Start, close the app, lock the phone.",
                    "Answer on the notification when it cries. Come back after 30 minutes.",
                ),
                "Samsung's sleeping apps can stop alarms. The real app needs to know whether to warn people.",
                "Start", condition = "samsung-sleeping", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON, Need.CONFIRM),
                confirmText = "I've added this app to Sleeping apps", samsungOnly = true,
                afterText = "Now remove this app from Sleeping apps again.",
            ),
            Step(
                SAMSUNG_FREE, StepIcon.SAMSUNG, "Samsung: unrestricted battery", "30 min",
                "Same check, with the app allowed to use battery freely.",
                listOf(
                    "Tap 'Open app settings' below › Battery › choose Unrestricted.",
                    "Come back, tick the box below, tap Start, close the app, lock the phone.",
                    "Answer on the notification when it cries. Come back after 30 minutes.",
                ),
                "This shows whether asking people to choose 'Unrestricted' is worth it.",
                "Start", condition = "samsung-unrestricted", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON, Need.CONFIRM),
                confirmText = "I've set this app's battery to Unrestricted", samsungOnly = true,
            ),
            Step(
                OVERNIGHT, StepIcon.NIGHT, "Overnight test (night 1)", "1 night",
                "Tonight the app checks, every 20 minutes, whether the phone wakes up on time. It stays silent.",
                listOf(
                    "At bedtime, unplug the phone.",
                    "Tap Start, close the app, lock the phone, put it face down.",
                    "Don't use the phone until morning if you can.",
                    "In the morning, open the app and tap Finish.",
                ),
                "Phones go into deep sleep at night. This measures how late alarms come then.",
                "Start tonight's test", condition = "overnight", needs = listOf(Need.EXACT_ON),
            ),
            Step(
                MONITOR, StepIcon.MONITOR, "Baby monitor night (night 2)", "1 night",
                "Tonight the phone plays soft background sound all night, like a baby monitor. The baby cries once, 15 minutes after you start.",
                listOf(
                    "At bedtime, unplug the phone and tap Start.",
                    "Lock the phone. After 15 minutes it cries: answer on the notification.",
                    "Leave it playing all night.",
                    "In the morning, open the app and tap Stop and finish.",
                ),
                "This measures how much battery a night of baby-monitor sound uses.",
                "Start the monitor", condition = "monitor", needs = listOf(Need.NOTIFICATIONS, Need.EXACT_ON),
            ),
            Step(
                CLIPS, StepIcon.CLIPS, "Real baby sounds (optional)", "10 min",
                "If you have baby cry sound files from the shortlist, add them and rate how real they sound.",
                listOf("Tap Add a sound file.", "Tap Play, listen, then give it 1–5 stars.", "Repeat for each file."),
                "Helps us choose the sounds for the real app.",
                "Done", optional = true,
            ),
            Step(
                RESULTS, StepIcon.SEND, "Send the results", "2 min",
                "All done. Thank you! Save the results to Google Drive so Claude can read them.",
                listOf(
                    "Tap Save results.",
                    "Choose Drive, open the folder Baby Sim M3 spike › M3 logs, tap Save.",
                ),
                "The results file lists what happened in each test. It contains no personal information.",
                "Save results", inShort = true,
            ),
        )

    fun shortPlan(context: Context): Boolean = context.applicationInfo.targetSdkVersion < 37

    fun plan(context: Context): List<Step> {
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        return ALL.filter { (!shortPlan(context) || it.inShort) && (!it.samsungOnly || samsung) }
    }

    fun state(
        context: Context,
        id: String,
    ): StepState {
        val raw = Prefs.step(context, id)
        return when {
            raw == "done" -> StepState.Done
            raw == "skipped" -> StepState.Skipped
            raw.startsWith("running:") ->
                raw.split(":").let { StepState.Running(it[1].toLong(), it[2].toLong()) }
            else -> StepState.NotStarted
        }
    }

    fun finished(state: StepState): Boolean = state == StepState.Done || state == StepState.Skipped

    /** The first step not yet done or skipped; the plan's last step when everything is finished. */
    fun current(context: Context): Step {
        val plan = plan(context)
        return plan.firstOrNull { !finished(state(context, it.id)) } ?: plan.last()
    }

    fun questions(
        context: Context,
        id: String,
    ): List<Question> = Questions.pending(context).filter { it.step == id }

    /** Can the tester move on? The due time has passed and nothing is left to answer. */
    fun canFinish(
        context: Context,
        step: Step,
        now: Long,
    ): Boolean {
        val s = state(context, step.id) as? StepState.Running ?: return false
        val answered = questions(context, step.id).isEmpty()
        return when (step.id) {
            OVERNIGHT, MONITOR -> answered
            RESTART -> answered && Prefs.lastBoot(context) > s.startMs && now >= s.dueMs
            else -> answered && now >= s.dueMs
        }
    }

    fun start(
        context: Context,
        step: Step,
    ) {
        val now = System.currentTimeMillis()
        Prefs.setCondition(context, step.condition)
        val due =
            when (step.id) {
                SOUND -> {
                    val q = "$SOUND~$now-t1"
                    Questions.ask(context, q, SOUND, "Did you hear the alert sound?")
                    Notifications.careAlert(context, "Sound check", "Did you hear this sound?", q)
                    now
                }
                ALERT_MEDIA -> alert(context, step.id, "media", 2 * MIN, Planned.EXACT)
                ALERT_ALARM -> alert(context, step.id, "alarm", 2 * MIN, Planned.EXACT)
                ALERT_LATE -> alert(context, step.id, "alarm", 15 * MIN, Planned.INEXACT)
                RESTART -> {
                    Alarms.scheduleRun(context, step.id, Planned.LATENCY, 30 * MIN, 1, 0)
                    now + 30 * MIN
                }
                BATTERY_SAVER, SAMSUNG_SLEEP, SAMSUNG_FREE -> {
                    Alarms.scheduleRun(context, step.id, Planned.LATENCY, 30 * MIN, 1, 0)
                    alert(context, step.id, "alarm", 15 * MIN, Planned.EXACT)
                    now + 30 * MIN
                }
                OVERNIGHT -> {
                    Alarms.scheduleRun(context, step.id, Planned.LATENCY, 20 * MIN, 30, 20 * MIN)
                    now + 10 * HOUR
                }
                MONITOR -> {
                    PlaybackService.startMonitor(context)
                    alert(context, step.id, "alarm", 15 * MIN, Planned.EXACT)
                }
                else -> now
            }
        Prefs.setStep(context, step.id, "running:$now:$due")
        SpikeLog.log(context, "step_started", detail = "step=${step.id};due_in_s=${(due - now) / 1000}")
    }

    private fun alert(
        context: Context,
        stepId: String,
        usage: String,
        inMs: Long,
        series: String,
    ): Long {
        Prefs.setUsage(context, usage)
        Alarms.scheduleRun(context, stepId, Planned.ALERT, inMs, 1, 0, listOf(series))
        return System.currentTimeMillis() + inMs
    }

    /** Marks a step done (or skipped). Skipping a running step cancels nothing already logged. */
    fun finish(
        context: Context,
        step: Step,
        skipped: Boolean = false,
    ) {
        if (step.id == MONITOR && Prefs.monitorRunning(context)) PlaybackService.stop(context)
        if (skipped) Questions.pending(context).filter { it.step == step.id }.forEach { Questions.drop(context, it.id) }
        Prefs.setStep(context, step.id, if (skipped) "skipped" else "done")
        SpikeLog.log(context, if (skipped) "step_skipped" else "step_done", detail = "step=${step.id}")
    }

    /** Back to "not started", e.g. to run a test again. */
    fun reset(
        context: Context,
        step: Step,
    ) {
        Prefs.setStep(context, step.id, "")
        SpikeLog.log(context, "step_reset", detail = "step=${step.id}")
    }
}
