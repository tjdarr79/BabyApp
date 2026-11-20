package dev.babysim.spike

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri

/**
 * Alarm fired. LATENCY alarms only log their lateness. ALERT alarms do what the production app will do when a
 * care request opens in the background: post the care notification (T1, the system plays its sound) and try to
 * start mediaPlayback playback (T2) under the exact-alarm FGS-start exemption (docs/03 §7).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val planned = Planned.fromIntent(intent) ?: return
        Alarms.done(context, planned)
        SpikeLog.log(context, "alarm_fired", planned.runId, planned.series, planned.index, planned.atMs, "kind=${planned.kind}")
        if (planned.kind != Planned.ALERT) return
        val question = "${planned.runId}-${planned.series}-t1"
        Questions.ask(context, question, Questions.stepOf(planned.runId), "Did you hear the alert (a short cry)?")
        Notifications.careAlert(context, "The test baby needs you", "Did you hear this alert?", question)
        PlaybackService.startCry(context, planned, "${planned.runId} ${planned.series}")
    }
}

/** Boot and exact-permission changes (docs/03 §8): catch up, reschedule, notification only (no FGS from boot). */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> onBoot(context)
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                SpikeLog.log(context, "exact_permission_changed", detail = "granted=${DeviceState.exactGranted(context)}")
        }
    }

    private fun onBoot(context: Context) {
        Prefs.setMonitorRunning(context, false)
        val now = System.currentTimeMillis()
        val pending = Prefs.pending(context).mapNotNull { Planned.decode(it) }
        val (missed, future) = pending.partition { it.atMs <= now }
        Prefs.setPending(context, emptySet())
        missed.forEach { SpikeLog.log(context, "alarm_missed_during_boot", it.runId, it.series, it.index, it.atMs) }
        future.forEach { Alarms.schedule(context, it) }
        Prefs.setLastBoot(context, now)
        SpikeLog.log(context, "boot_completed", detail = "missed=${missed.size};rescheduled=${future.size}")
        val question = "${Steps.RESTART}~boot$now-t1"
        Questions.ask(context, question, Steps.RESTART, "After the restart, did you hear an alert?")
        Notifications.careAlert(context, "Phone restarted: test alert", "Did you hear this alert?", question)
    }
}

/** Answer buttons on test notifications ("Yes, clearly" / "Faint" / "No"). */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val question = intent.getStringExtra(Answers.EXTRA_QUESTION) ?: return
        val answer = intent.getStringExtra(Answers.EXTRA_ANSWER) ?: return
        Questions.answer(context, question, answer, via = "notification")
        intent.getIntExtra(Answers.EXTRA_NOTIFICATION, 0).takeIf { it != 0 }?.let {
            context.getSystemService(NotificationManager::class.java).cancel(it)
        }
        if (intent.getBooleanExtra(Answers.EXTRA_STOP_CRY, false)) PlaybackService.stopCry(context)
    }
}

object Answers {
    /** Codes kept stable for spike/tools/analyze.py. */
    const val CLEAR = "clear"
    const val FAINT = "heard-poor"
    const val NOT_HEARD = "not-heard"
    /** Short labels fit three notification buttons; the app has room for plain sentences. */
    val ALL = listOf(CLEAR to "Yes, clearly", FAINT to "Faint", NOT_HEARD to "No")
    val IN_APP = listOf(CLEAR to "Yes, clearly", FAINT to "Yes, but it was faint", NOT_HEARD to "No, I didn't hear it")

    const val EXTRA_QUESTION = "question"
    const val EXTRA_ANSWER = "answer"
    const val EXTRA_NOTIFICATION = "notification"
    const val EXTRA_STOP_CRY = "stop_cry"

    fun intent(
        context: Context,
        question: String,
        answer: String,
        notificationId: Int = 0,
        stopCry: Boolean = false,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            "$question|$answer".hashCode(),
            Intent(context, AnswerReceiver::class.java)
                .putExtra(EXTRA_QUESTION, question)
                .putExtra(EXTRA_ANSWER, answer)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .putExtra(EXTRA_STOP_CRY, stopCry),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

object Notifications {
    const val CARE_CHANNEL = "care_alert_v1"
    const val PLAYBACK_CHANNEL = "playback_v1"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.cry_short}")
        val care =
            NotificationChannel(CARE_CHANNEL, "Care alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The test baby needs care (short synthetic cry)"
                setSound(
                    sound,
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            }
        val playback =
            NotificationChannel(PLAYBACK_CHANNEL, "Test baby sounds", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            }
        nm.createNotificationChannel(care)
        nm.createNotificationChannel(playback)
    }

    const val EXTRA_FROM = "from"

    /**
     * Tapping a test notification's text opens the app, whether it is running or not (NEW_TASK), reusing the
     * open screen if there is one. Tester report m3-4: the crying notification had no tap action at all.
     */
    fun openApp(
        context: Context,
        from: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            from.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_FROM, from),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The ongoing "baby is crying" / "monitor running" notification of the playback service (T2/T3). */
    fun playback(
        context: Context,
        text: String,
        stop: PendingIntent,
        question: String?,
    ): Notification.Builder {
        val builder =
            Notification
                .Builder(context, PLAYBACK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(text)
                .setContentIntent(openApp(context, "playback"))
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
        question?.let {
            builder.setContentText("Can you hear it? Tap the button if you can.")
            builder.addAction(
                Notification.Action.Builder(null, "I can hear it", Answers.intent(context, it, Answers.CLEAR, stopCry = true)).build(),
            )
        }
        return builder
    }

    /** The T1 alert. With a [question], it carries "Yes, clearly / Faint / No" buttons. */
    fun careAlert(
        context: Context,
        title: String,
        text: String,
        question: String? = null,
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(CARE_CHANNEL)
        val id = (System.currentTimeMillis() % 1_000_000).toInt() + 2000
        val builder =
            Notification
                .Builder(context, CARE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp(context, "alert"))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
        question?.let { q ->
            Answers.ALL.forEach { (code, label) ->
                builder.addAction(Notification.Action.Builder(null, label, Answers.intent(context, q, code, id)).build())
            }
        }
        try {
            nm.notify(id, builder.build())
            SpikeLog.log(
                context,
                "t1_posted",
                detail = "title=$title;channel_importance=${channel?.importance};channel_sound=${channel?.sound != null}",
            )
        } catch (e: SecurityException) {
            SpikeLog.log(context, "t1_failed", detail = "error=${e.javaClass.simpleName}:${e.message}")
        }
    }
}

/** One "did you hear it?" question, tied to the test step that caused it. */
data class Question(
    val id: String,
    val step: String,
    val text: String,
    val askedAt: Long,
)

/** Questions waiting for the tester, answered from a notification or in the app. */
object Questions {
    private const val SEP = "\u001F"

    /** Run ids are "<step id>~<time>", so every question and log row maps back to its test step. */
    fun stepOf(runIdOrLabel: String): String = runIdOrLabel.substringBefore("~")

    fun ask(
        context: Context,
        id: String,
        step: String,
        text: String,
    ) = Prefs.setQuestions(context, Prefs.questions(context) + listOf(id, step, text, System.currentTimeMillis()).joinToString(SEP))

    fun pending(context: Context): List<Question> =
        Prefs.questions(context).mapNotNull { raw ->
            raw.split(SEP).takeIf { it.size == 4 }?.let { Question(it[0], it[1], it[2], it[3].toLongOrNull() ?: 0) }
        }.sortedBy { it.askedAt }

    fun answer(
        context: Context,
        id: String,
        answer: String,
        via: String,
    ) {
        val entry = Prefs.questions(context).firstOrNull { it.startsWith(id + SEP) } ?: return
        Prefs.setQuestions(context, Prefs.questions(context) - entry)
        SpikeLog.log(context, "heard", runId = id, detail = "answer=$answer;via=$via;question=${entry.split(SEP).getOrNull(2)}")
    }

    /** Removes a question without an answer (its test was skipped); not counted as heard or not heard. */
    fun drop(
        context: Context,
        id: String,
    ) {
        val entry = Prefs.questions(context).firstOrNull { it.startsWith(id + SEP) } ?: return
        Prefs.setQuestions(context, Prefs.questions(context) - entry)
        SpikeLog.log(context, "question_dropped", runId = id)
    }
}
