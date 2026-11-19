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
        val label = "${planned.runId} ${planned.series}"
        Notifications.careAlert(context, "Care alert (T1 sound)", label)
        Questions.ask(context, "${planned.runId}-${planned.series}-t1", "T1 notification sound, $label")
        PlaybackService.startCry(context, planned, label)
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
        SpikeLog.log(context, "boot_completed", detail = "missed=${missed.size};rescheduled=${future.size}")
        Notifications.careAlert(context, "Boot catch-up (T1 only)", "missed=${missed.size} rescheduled=${future.size}")
        Questions.ask(context, "boot-$now", "T1 notification sound after reboot")
    }
}

object Notifications {
    const val CARE_CHANNEL = "care_alert_v1"
    const val PLAYBACK_CHANNEL = "playback_v1"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.cry_short}")
        val care =
            NotificationChannel(CARE_CHANNEL, "Care alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Baby needs care (spike: short synthetic cry)"
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
            NotificationChannel(PLAYBACK_CHANNEL, "Baby audio playing", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            }
        nm.createNotificationChannel(care)
        nm.createNotificationChannel(playback)
    }

    fun careAlert(
        context: Context,
        title: String,
        text: String,
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(CARE_CHANNEL)
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(context, CARE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
        val id = (System.currentTimeMillis() % 1_000_000).toInt() + 2000
        try {
            nm.notify(id, notification)
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

/** Questions for the tester when they next open the app: did you hear it? */
object Questions {
    fun ask(
        context: Context,
        id: String,
        label: String,
    ) = Prefs.setQuestions(context, Prefs.questions(context) + "$id|${SpikeLog.iso(System.currentTimeMillis())} $label")

    fun answer(
        context: Context,
        entry: String,
        answer: String,
    ) {
        Prefs.setQuestions(context, Prefs.questions(context) - entry)
        val (id, label) = entry.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        SpikeLog.log(context, "heard", runId = id, detail = "answer=$answer;question=$label")
    }
}
