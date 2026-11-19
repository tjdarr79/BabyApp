package dev.babysim.spike

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/** Small persisted settings and queues. Everything else lives in the CSV log. */
object Prefs {
    private const val NAME = "spike"
    private const val CONDITION = "condition"
    private const val CLIP = "clip"
    private const val USAGE = "usage"
    private const val PENDING = "pending_alarms"
    private const val QUESTIONS = "questions"
    private const val MONITOR = "monitor_running"

    private fun p(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun condition(context: Context): String = p(context).getString(CONDITION, "normal").orEmpty()

    fun setCondition(
        context: Context,
        value: String,
    ) = p(context).edit().putString(CONDITION, value.trim().ifEmpty { "normal" }).apply()

    /** "raw:cry_loop" for the bundled clip, or an absolute path to an imported clip. */
    fun clip(context: Context): String = p(context).getString(CLIP, "raw:cry_loop").orEmpty()

    fun setClip(
        context: Context,
        value: String,
    ) = p(context).edit().putString(CLIP, value).apply()

    /** "media" or "alarm": the AudioAttributes usage for T2 playback (docs/03 §7). */
    fun usage(context: Context): String = p(context).getString(USAGE, "media").orEmpty()

    fun setUsage(
        context: Context,
        value: String,
    ) = p(context).edit().putString(USAGE, value).apply()

    fun monitorRunning(context: Context): Boolean = p(context).getBoolean(MONITOR, false)

    fun setMonitorRunning(
        context: Context,
        value: Boolean,
    ) = p(context).edit().putBoolean(MONITOR, value).apply()

    fun pending(context: Context): Set<String> = p(context).getStringSet(PENDING, emptySet()).orEmpty()

    fun setPending(
        context: Context,
        value: Set<String>,
    ) = p(context).edit().putStringSet(PENDING, value).apply()

    /** "Did you hear it?" questions waiting for the tester: "id|label". */
    fun questions(context: Context): Set<String> = p(context).getStringSet(QUESTIONS, emptySet()).orEmpty()

    fun setQuestions(
        context: Context,
        value: Set<String>,
    ) = p(context).edit().putStringSet(QUESTIONS, value).apply()
}

/** One planned alarm. [kind]: LATENCY (log only) or ALERT (T1 notification sound + T2 playback). */
data class Planned(
    val runId: String,
    val series: String,
    val index: Int,
    val atMs: Long,
    val kind: String,
    val usage: String,
) {
    fun encode(): String = listOf(runId, series, index, atMs, kind, usage).joinToString("|")

    val requestCode: Int get() = encode().hashCode()

    companion object {
        const val EXACT = "exact"
        const val INEXACT = "inexact"
        const val LATENCY = "latency"
        const val ALERT = "alert"

        fun decode(s: String): Planned? =
            s.split("|").takeIf { it.size == 6 }?.let {
                Planned(it[0], it[1], it[2].toInt(), it[3].toLong(), it[4], it[5])
            }

        fun fromIntent(intent: Intent): Planned? = intent.getStringExtra("planned")?.let { decode(it) }
    }
}

/**
 * Schedules alarms the way the production app will (docs/03 §4): exact-and-allow-while-idle when the exact
 * permission is granted, otherwise the inexact allow-while-idle fallback. The "inexact" series always uses the
 * fallback, so one run measures both.
 */
object Alarms {
    fun schedule(
        context: Context,
        planned: Planned,
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, planned)
        val exactAllowed = DeviceState.exactGranted(context)
        val method =
            when {
                planned.series == Planned.EXACT && exactAllowed -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, planned.atMs, pi)
                    "setExactAndAllowWhileIdle"
                }
                else -> {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, planned.atMs, pi)
                    if (planned.series == Planned.EXACT) "setAndAllowWhileIdle(exact denied)" else "setAndAllowWhileIdle"
                }
            }
        Prefs.setPending(context, Prefs.pending(context) + planned.encode())
        SpikeLog.log(
            context,
            "alarm_scheduled",
            planned.runId,
            planned.series,
            planned.index,
            planned.atMs,
            "kind=${planned.kind};usage=${planned.usage};method=$method",
        )
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = Prefs.pending(context).mapNotNull { Planned.decode(it) }
        pending.forEach { am.cancel(pendingIntent(context, it)) }
        Prefs.setPending(context, emptySet())
        SpikeLog.log(context, "alarms_cancelled", detail = "count=${pending.size}")
    }

    fun done(
        context: Context,
        planned: Planned,
    ) = Prefs.setPending(context, Prefs.pending(context) - planned.encode())

    /** A run: [count] alarms [spacingMs] apart in both series, the first [firstInMs] from now. */
    fun scheduleRun(
        context: Context,
        label: String,
        kind: String,
        firstInMs: Long,
        count: Int,
        spacingMs: Long,
        seriesList: List<String> = listOf(Planned.EXACT, Planned.INEXACT),
    ): String {
        val runId = "$label-" + SpikeLog.iso(System.currentTimeMillis()).substring(5, 16).replace(":", "")
        val start = System.currentTimeMillis() + firstInMs
        for (series in seriesList) {
            for (i in 0 until count) {
                schedule(context, Planned(runId, series, i, start + i * spacingMs, kind, Prefs.usage(context)))
            }
        }
        return runId
    }

    private fun pendingIntent(
        context: Context,
        planned: Planned,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            planned.requestCode,
            Intent(context, AlarmReceiver::class.java).putExtra("planned", planned.encode()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
