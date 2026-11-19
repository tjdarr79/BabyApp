package dev.babysim.spike

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Tester screen for the M3 matrix (docs/03 §12). Plain platform views: this is throwaway spike code. Every button
 * that changes something writes a log row; the tester saves the CSV and sends it back for spike/tools/analyze.py.
 */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var questions: LinearLayout
    private lateinit var clips: RadioGroup
    private var preview: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 96) }
        setContentView(ScrollView(this).apply { addView(root) })
        build()
        SpikeLog.log(this, "app_opened")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    private fun build() {
        title("M3 platform spike")
        note("${Build.MANUFACTURER} ${Build.MODEL} · Android SDK ${Build.VERSION.SDK_INT} · this build targets ${applicationInfo.targetSdkVersion}")
        status = note("")

        section("1. Set up")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            button("Allow notifications") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            button("Exact alarm permission (grant or revoke)") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        button("Battery optimisation settings") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("App settings (Samsung sleeping apps, Xiaomi autostart…)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button("Play the care alert sound now (T1 test)") {
            Notifications.careAlert(this, "Care alert test (T1 sound)", "Tap to open")
        }

        section("2. Condition label")
        note("Describe what is special about this run, e.g. normal, battery-saver, samsung-sleeping, xiaomi-autostart-off, exact-denied. It is written on every log row.")
        val condition = EditText(this).apply { setText(Prefs.condition(this@MainActivity)); inputType = InputType.TYPE_CLASS_TEXT }
        root.addView(condition)
        button("Save label") {
            Prefs.setCondition(this, condition.text.toString())
            SpikeLog.log(this, "condition_set")
            refresh()
        }

        section("3. Alarm lateness (screen off, Doze)")
        note("Start a run, then lock the phone and leave it alone. Each run schedules an exact and an inexact series.")
        button("Quick check: 3 alarms, 2 min apart") { run("quick", Planned.LATENCY, 2 * MIN, 3, 2 * MIN) }
        button("One alarm in 30 min") { run("30min", Planned.LATENCY, 30 * MIN, 1, 0) }
        button("One alarm in 2 h") { run("2h", Planned.LATENCY, 120 * MIN, 1, 0) }
        button("Overnight: every 20 min for 10 h") { run("overnight", Planned.LATENCY, 20 * MIN, 30, 20 * MIN) }

        section("4. Background alert: notification sound (T1) + playback (T2)")
        note("Choose how T2 plays, start the alert, then swipe the app away or lock the phone. When it fires, listen.")
        val usage =
            RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
                listOf("media", "alarm").forEach { u ->
                    addView(
                        RadioButton(this@MainActivity).apply {
                            text = "usage=$u"
                            id = View.generateViewId()
                            isChecked = Prefs.usage(this@MainActivity) == u
                            setOnClickListener { Prefs.setUsage(this@MainActivity, u) }
                        },
                    )
                }
            }
        root.addView(usage)
        button("Alert in 2 min (exact)") { run("alert2", Planned.ALERT, 2 * MIN, 1, 0, listOf(Planned.EXACT)) }
        button("Alert in 15 min (exact)") { run("alert15", Planned.ALERT, 15 * MIN, 1, 0, listOf(Planned.EXACT)) }
        button("Alert in 15 min (inexact: the exact-denied path)") {
            run("alert15i", Planned.ALERT, 15 * MIN, 1, 0, listOf(Planned.INEXACT))
        }

        section("5. Baby monitor session (T3)")
        note("Starts ambient sound from the foreground. Battery is logged every 15 min. Alerts during a session play inside it. Run it overnight for the drain figure.")
        button("Start monitor") { PlaybackService.startMonitor(this); later { refresh() } }
        button("Stop monitor") { PlaybackService.stop(this); later { refresh() } }

        section("6. Clips")
        note("Import candidate clips from docs/08 (they stay on this phone). The selected clip is used for T2 and in-monitor cries; the notification sound is always the synthetic one.")
        clips = RadioGroup(this)
        root.addView(clips)
        button("Import clip…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), PICK_CLIP)
        }
        button("Play selected clip here (T0, 15 s)") { playPreview() }
        val ratings = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (1..5).forEach { r ->
            ratings.addView(
                Button(this).apply {
                    text = "$r"
                    setOnClickListener {
                        SpikeLog.log(this@MainActivity, "rating", detail = "tier=T0;stars=$r;clip=${Clips.name(Prefs.clip(this@MainActivity))}")
                        toast("Rated $r")
                    }
                },
            )
        }
        note("Rate how real the selected clip sounds on this phone's speaker (1–5):")
        root.addView(ratings)

        section("7. Did you hear it?")
        questions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(questions)

        section("8. Log")
        button("Save log to a file… (Drive, Downloads)") {
            val name = "m3-${Build.MANUFACTURER}-${Build.MODEL}-t${applicationInfo.targetSdkVersion}-${SpikeLog.iso(System.currentTimeMillis()).substring(0, 10)}.csv"
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
                    .putExtra(Intent.EXTRA_TITLE, name.replace(Regex("[^A-Za-z0-9._-]"), "_")),
                SAVE_LOG,
            )
        }
        button("Share log as text") {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "M3 spike log ${Build.MODEL}")
                        .putExtra(Intent.EXTRA_TEXT, SpikeLog.text(this)),
                    "Share log",
                ),
            )
        }
        button("Cancel all scheduled alarms") { Alarms.cancelAll(this); refresh() }
        button("Clear log") {
            AlertDialog.Builder(this).setMessage("Delete all log rows on this phone?")
                .setPositiveButton("Delete") { _, _ -> SpikeLog.clear(this); refresh() }
                .setNegativeButton("Keep", null).show()
        }
    }

    private fun run(
        label: String,
        kind: String,
        firstIn: Long,
        count: Int,
        spacing: Long,
        series: List<String> = listOf(Planned.EXACT, Planned.INEXACT),
    ) {
        val runId = Alarms.scheduleRun(this, label, kind, firstIn, count, spacing, series)
        toast("Scheduled $runId")
        refresh()
    }

    private fun refresh() {
        val s = DeviceState.snapshot(this)
        status.text =
            listOf(
                "Exact alarms: ${if (s.exactGranted) "granted" else "DENIED"}",
                "Notifications: ${if (s.notificationsOn) "on" else "OFF"}",
                "Battery saver: ${if (s.powerSave) "ON" else "off"} · battery ${s.batteryPct}%${if (s.charging) " (charging)" else ""}",
                "Unrestricted battery: ${if (s.ignoringBatteryOptimizations) "yes" else "no"} · standby bucket: ${s.standbyBucket}",
                "Condition label: ${Prefs.condition(this)}",
                "Alarms waiting: ${Prefs.pending(this).size} · monitor: ${if (Prefs.monitorRunning(this)) "running" else "off"} · log rows: ${SpikeLog.rowCount(this)}",
            ).joinToString("\n")
        refreshClips()
        refreshQuestions()
    }

    private fun refreshClips() {
        clips.removeAllViews()
        val selected = Prefs.clip(this)
        Clips.all(this).forEach { clip ->
            clips.addView(
                RadioButton(this).apply {
                    text = Clips.name(clip)
                    id = View.generateViewId()
                    isChecked = clip == selected
                    setOnClickListener { Prefs.setClip(this@MainActivity, clip) }
                },
            )
        }
    }

    private fun refreshQuestions() {
        questions.removeAllViews()
        val pending = Prefs.questions(this).sorted()
        if (pending.isEmpty()) questions.addView(TextView(this).apply { text = "Nothing to answer." })
        pending.forEach { entry ->
            questions.addView(TextView(this).apply { text = entry.substringAfter("|"); setPadding(0, 16, 0, 0) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("clear", "heard-poor", "not-heard").forEach { answer ->
                row.addView(
                    Button(this).apply {
                        text = answer
                        setOnClickListener { Questions.answer(this@MainActivity, entry, answer); refresh() }
                    },
                )
            }
            questions.addView(row)
        }
    }

    private fun playPreview() {
        stopPreview()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        preview = Clips.player(this, Prefs.clip(this), attrs, loop = true).also { it.start() }
        SpikeLog.log(this, "t0_preview", detail = "clip=${Clips.name(Prefs.clip(this))}")
        handler.postDelayed({ stopPreview() }, PREVIEW_MS)
    }

    private fun stopPreview() {
        handler.removeCallbacksAndMessages(null)
        preview?.release()
        preview = null
    }

    @Deprecated("Platform Activity result API; fine for a throwaway spike.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            PICK_CLIP -> {
                val path = Clips.import(this, uri)
                Prefs.setClip(this, path)
                SpikeLog.log(this, "clip_imported", detail = "clip=${File(path).name}")
            }
            SAVE_LOG -> {
                contentResolver.openOutputStream(uri)?.use { it.write(SpikeLog.text(this).toByteArray()) }
                SpikeLog.log(this, "log_saved")
                toast("Log saved")
            }
        }
        refresh()
    }

    private fun title(text: String) = root.addView(TextView(this).apply { this.text = text; textSize = 24f })

    private fun section(text: String) =
        root.addView(TextView(this).apply { this.text = text; textSize = 18f; setPadding(0, 48, 0, 8) })

    private fun note(text: String): TextView = TextView(this).apply { this.text = text }.also { root.addView(it) }

    private fun button(
        text: String,
        onClick: () -> Unit,
    ) = root.addView(Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { onClick() } })

    private fun toast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    private fun later(block: () -> Unit) = handler.postDelayed(block, 800)

    private companion object {
        const val MIN = 60_000L
        const val PREVIEW_MS = 15_000L
        const val PICK_CLIP = 10
        const val SAVE_LOG = 11
    }
}
