package dev.babysim.spike

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * mediaPlayback foreground service for two tiers (docs/03 §7):
 * - T2: started by an exact alarm while the app is in the background; plays a looping cry for up to 60 s.
 * - T3: a "baby monitor" session the tester starts from the foreground: looping ambient sound in a MediaSession,
 *   battery logged every 15 min; alert cries play inside the running session.
 * Every step that the platform might refuse (FGS start, audio focus, actual playback) is logged.
 */
class PlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var cry: MediaPlayer? = null
    private var cryFocus: AudioFocusRequest? = null
    private var ambient: MediaPlayer? = null
    private var ambientFocus: AudioFocusRequest? = null
    private var session: MediaSession? = null
    private var monitorStartMs = 0L
    private var monitorStartBattery = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CRY -> {
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                val usage = intent.getStringExtra(EXTRA_USAGE) ?: "media"
                val question = "$label-$usage-t2"
                if (goForeground("The test baby is crying", usage, question)) playCry(label, usage, question) else stopIfIdle()
            }
            ACTION_MONITOR -> if (goForeground("Baby monitor running (T3)")) startMonitor() else stopIfIdle()
            ACTION_STOP -> stopEverything("user")
            ACTION_STOP_CRY -> {
                stopCry("user_heard")
                stopIfIdle()
            }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(
        text: String,
        usage: String = "monitor",
        question: String? = null,
    ): Boolean {
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            Notification
                .Builder(this, Notifications.PLAYBACK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(text)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
        question?.let {
            builder.setContentText("Can you hear it? Tap the button if you can.")
            builder.addAction(
                Notification.Action.Builder(null, "I can hear it", Answers.intent(this, it, Answers.CLEAR, stopCry = true)).build(),
            )
        }
        session?.let { builder.setStyle(Notification.MediaStyle().setMediaSession(it.sessionToken)) }
        return try {
            Notifications.ensureChannels(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FGS_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(FGS_ID, builder.build())
            }
            SpikeLog.log(this, "fgs_started", detail = "text=$text")
            true
        } catch (e: Exception) {
            SpikeLog.log(this, "fgs_failed", detail = "usage=$usage;error=${e.javaClass.simpleName}:${e.message}")
            false
        }
    }

    // ---- T2: alarm-started cry ------------------------------------------------------------------------------

    private fun playCry(
        label: String,
        usage: String,
        question: String,
    ) {
        stopCry("replaced")
        val attrs = attributes(usage)
        val focus = focusRequest(attrs, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, "t2")
        cryFocus = focus
        val focusResult = audio.requestAudioFocus(focus)
        val stream = if (usage == "alarm") AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        val volume = "${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}"
        val player =
            try {
                Clips.player(this, Prefs.clip(this), attrs, loop = true)
            } catch (e: Exception) {
                SpikeLog.log(this, "t2_error", detail = "stage=prepare;error=${e.javaClass.simpleName}:${e.message}")
                stopIfIdle()
                return
            }
        player.setOnErrorListener { _, what, extra ->
            SpikeLog.log(this, "t2_error", detail = "what=$what;extra=$extra")
            true
        }
        player.start()
        cry = player
        SpikeLog.log(
            this,
            "t2_started",
            runId = label,
            detail =
                "usage=$usage;focus=${focusName(focusResult)};volume=$volume;monitor=${session != null};" +
                    "clip=${Clips.name(Prefs.clip(this))}",
        )
        handler.postDelayed({
            SpikeLog.log(
                this,
                "t2_check",
                runId = label,
                detail = "playing=${cry?.isPlaying};active_playbacks=${audio.activePlaybackConfigurations.size}",
            )
        }, CHECK_AFTER_MS)
        Questions.ask(this, question, Questions.stepOf(label), "Did the crying keep going for about a minute?")
        handler.postDelayed({
            stopCry("timeout")
            stopIfIdle()
        }, CRY_MS)
    }

    private fun stopCry(reason: String) {
        val player = cry ?: return
        runCatching { player.stop() }
        player.release()
        cry = null
        cryFocus?.let { audio.abandonAudioFocusRequest(it) }
        cryFocus = null
        SpikeLog.log(this, "t2_stopped", detail = "reason=$reason")
    }

    // ---- T3: user-started monitor session -------------------------------------------------------------------

    private fun startMonitor() {
        if (session != null) return
        val s =
            MediaSession(this, "baby-monitor").apply {
                setPlaybackState(
                    PlaybackState
                        .Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                        .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
                        .build(),
                )
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onStop() = stopEverything("media_session_stop")

                        override fun onPause() = stopEverything("media_session_pause")
                    },
                )
                isActive = true
            }
        session = s
        goForeground("Baby monitor running (T3)")
        val attrs = attributes("media")
        val focus = focusRequest(attrs, AudioManager.AUDIOFOCUS_GAIN, "t3")
        ambientFocus = focus
        val focusResult = audio.requestAudioFocus(focus)
        ambient = Clips.player(this, "raw:ambient_loop", attrs, loop = true).also { it.start() }
        Prefs.setMonitorRunning(this, true)
        monitorStartMs = System.currentTimeMillis()
        monitorStartBattery = DeviceState.snapshot(this).batteryPct
        SpikeLog.log(this, "monitor_started", detail = "focus=${focusName(focusResult)}")
        handler.postDelayed(batteryTick, BATTERY_EVERY_MS)
    }

    private val batteryTick =
        object : Runnable {
            override fun run() {
                val minutes = (System.currentTimeMillis() - monitorStartMs) / MINUTE_MS
                SpikeLog.log(
                    this@PlaybackService,
                    "monitor_battery",
                    detail = "minutes=$minutes;start_pct=$monitorStartBattery;playing=${ambient?.isPlaying}",
                )
                handler.postDelayed(this, BATTERY_EVERY_MS)
            }
        }

    private fun stopEverything(reason: String) {
        stopCry(reason)
        handler.removeCallbacksAndMessages(null)
        ambient?.let {
            runCatching { it.stop() }
            it.release()
        }
        ambient = null
        ambientFocus?.let { audio.abandonAudioFocusRequest(it) }
        ambientFocus = null
        if (session != null) {
            val minutes = (System.currentTimeMillis() - monitorStartMs) / MINUTE_MS
            SpikeLog.log(this, "monitor_stopped", detail = "reason=$reason;minutes=$minutes;start_pct=$monitorStartBattery")
        }
        session?.release()
        session = null
        Prefs.setMonitorRunning(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopIfIdle() {
        if (cry == null && session == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (session != null || cry != null) SpikeLog.log(this, "service_destroyed_while_playing")
        stopCry("destroyed")
        ambient?.release()
        session?.release()
        Prefs.setMonitorRunning(this, false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun attributes(usage: String): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(if (usage == "alarm") AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(
                if (usage == "alarm") AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_MUSIC,
            ).build()

    private fun focusRequest(
        attrs: AudioAttributes,
        gain: Int,
        tag: String,
    ): AudioFocusRequest =
        AudioFocusRequest
            .Builder(gain)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                SpikeLog.log(this, "focus_change", detail = "tier=$tag;change=$change")
            }.build()

    private fun focusName(result: Int): String =
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "failed"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
            else -> "result_$result"
        }

    companion object {
        private const val ACTION_CRY = "dev.babysim.spike.CRY"
        private const val ACTION_MONITOR = "dev.babysim.spike.MONITOR"
        private const val ACTION_STOP = "dev.babysim.spike.STOP"
        private const val ACTION_STOP_CRY = "dev.babysim.spike.STOP_CRY"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USAGE = "usage"
        private const val FGS_ID = 1001
        private const val CRY_MS = 60_000L
        private const val CHECK_AFTER_MS = 1_500L
        private const val MINUTE_MS = 60_000L
        private const val BATTERY_EVERY_MS = 15 * MINUTE_MS

        /** From the alarm receiver: the call the platform may refuse in the background. */
        fun startCry(
            context: Context,
            planned: Planned,
            label: String,
        ) {
            val intent =
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_CRY)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_USAGE, planned.usage)
            try {
                context.startForegroundService(intent)
                SpikeLog.log(
                    context,
                    "t2_start_requested",
                    planned.runId,
                    planned.series,
                    planned.index,
                    planned.atMs,
                    "usage=${planned.usage}",
                )
            } catch (e: Exception) {
                SpikeLog.log(
                    context,
                    "t2_start_failed",
                    planned.runId,
                    planned.series,
                    planned.index,
                    planned.atMs,
                    "usage=${planned.usage};error=${e.javaClass.simpleName}:${e.message}",
                )
            }
        }

        /** From the foreground only (T3 must be user-started, docs/03 §7). */
        fun startMonitor(context: Context) =
            context.startForegroundService(Intent(context, PlaybackService::class.java).setAction(ACTION_MONITOR))

        /** From a notification action: the tester heard the cry, so stop it (a running monitor keeps going). */
        fun stopCry(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP_CRY))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }
    }
}
