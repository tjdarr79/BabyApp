package dev.babysim.spike

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only CSV log. Every row carries the device, build and conditions, so logs from several phones can be
 * merged and analysed by spike/tools/analyze.py without any other context.
 */
object SpikeLog {
    private const val FILE = "spike-log.csv"
    private val COLUMNS =
        listOf(
            "wall_iso", "wall_ms", "uptime_ms", "event", "run_id", "series", "index", "scheduled_ms", "lateness_ms",
            "manufacturer", "model", "sdk", "target_sdk", "app_version", "condition", "exact_granted",
            "notifications_on", "screen_on", "device_idle", "power_save", "standby_bucket", "battery_pct",
            "charging", "detail",
        )

    fun file(context: Context): File = File(context.filesDir, FILE)

    @Synchronized
    fun log(
        context: Context,
        event: String,
        runId: String = "",
        series: String = "",
        index: Int? = null,
        scheduledMs: Long? = null,
        detail: String = "",
    ) {
        val now = System.currentTimeMillis()
        val state = DeviceState.snapshot(context)
        val row =
            listOf(
                iso(now), now.toString(), SystemClock.elapsedRealtime().toString(), event, runId, series,
                index?.toString().orEmpty(), scheduledMs?.toString().orEmpty(),
                scheduledMs?.let { (now - it).toString() }.orEmpty(),
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT.toString(),
                context.applicationInfo.targetSdkVersion.toString(), BuildInfo.versionName(context),
                Prefs.condition(context), state.exactGranted.toString(), state.notificationsOn.toString(),
                state.screenOn.toString(), state.deviceIdle.toString(), state.powerSave.toString(),
                state.standbyBucket, state.batteryPct.toString(), state.charging.toString(), detail,
            )
        val f = file(context)
        if (!f.exists()) f.writeText(COLUMNS.joinToString(",") + "\n")
        f.appendText(row.joinToString(",") { csv(it) } + "\n")
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun text(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()

    fun rowCount(context: Context): Int = (text(context).lineSequence().count { it.isNotBlank() } - 1).coerceAtLeast(0)

    fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(ms))

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/** What the platform says about this app and device right now. */
data class DeviceState(
    val exactGranted: Boolean,
    val notificationsOn: Boolean,
    val screenOn: Boolean,
    val deviceIdle: Boolean,
    val powerSave: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val standbyBucket: String,
    val batteryPct: Int,
    val charging: Boolean,
) {
    companion object {
        fun snapshot(context: Context): DeviceState {
            val power = context.getSystemService(PowerManager::class.java)
            val battery =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            return DeviceState(
                exactGranted = exactGranted(context),
                notificationsOn = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
                screenOn = power.isInteractive,
                deviceIdle = power.isDeviceIdleMode,
                powerSave = power.isPowerSaveMode,
                ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
                standbyBucket = standbyBucket(context),
                batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                charging = plugged != 0,
            )
        }

        fun exactGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        private fun standbyBucket(context: Context): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a"
            return when (val b = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket) {
                UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
                UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
                UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
                UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
                EXEMPTED_BUCKET -> "exempted"
                else -> "bucket_$b"
            }
        }
    }
}

/** Hidden platform constant (UsageStatsManager.STANDBY_BUCKET_EXEMPTED); seen on Samsung with Unrestricted battery. */
private const val EXEMPTED_BUCKET = 5

object BuildInfo {
    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
