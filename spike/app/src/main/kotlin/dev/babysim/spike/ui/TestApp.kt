package dev.babysim.spike.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.babysim.spike.Answers
import dev.babysim.spike.Clips
import dev.babysim.spike.DeviceState
import dev.babysim.spike.Need
import dev.babysim.spike.Prefs
import dev.babysim.spike.Question
import dev.babysim.spike.SpikeLog
import dev.babysim.spike.Step
import dev.babysim.spike.StepIcon
import dev.babysim.spike.StepState
import dev.babysim.spike.Steps
import kotlinx.coroutines.delay
import java.io.File

/**
 * The guided tester screen: one step at a time, plain language, one big button. State lives in Prefs and the
 * CSV log, so the screen just re-reads it every second and after every action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestApp(
    resumeTick: Int,
    ticking: Boolean = true,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var version by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showList by remember { mutableStateOf(false) }
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val refresh = { version++ }
    // Compose skips children whose arguments look unchanged, but permissions and saved progress live outside
    // Compose. A new value on every recomposition (clock tick, resume, action) makes those children re-read them.
    // Without it a permission allowed in Settings only showed after the app restarted (tester report, m3-3).
    @Suppress("UNUSED_EXPRESSION")
    run { now; version; resumeTick }
    val redraw = System.nanoTime()

    val plan = Steps.plan(context)
    val step = selected?.let { id -> plan.firstOrNull { it.id == id } } ?: Steps.current(context)
    val index = plan.indexOf(step)
    val doneCount = plan.count { Steps.finished(Steps.state(context, it.id)) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (Steps.shortPlan(context)) "Baby Sim · extra test" else "Baby Sim · phone test") },
                actions = {
                    IconButton(onClick = { showList = true }) { Icon(Icons.Filled.Menu, contentDescription = "All tests") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressHeader(index + 1, plan.size, doneCount)
            StepCard(
                step = step,
                state = Steps.state(context, step.id),
                now = now,
                refresh = redraw,
                onChanged = {
                    selected = null
                    refresh()
                },
            )
            Footer(context, redraw)
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            AllTests(
                plan = plan,
                onPick = { id ->
                    selected = id
                    showList = false
                },
            )
        }
    }
}

/**
 * The ☰ sheet: "Save results now" first (testers can save at any point), then every test. Scrolls, because
 * with the Samsung-only steps the list is taller than a phone screen.
 */
@Composable
fun AllTests(
    plan: List<Step>,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save your results", style = MaterialTheme.typography.titleLarge)
            Text(
                "Saves everything recorded so far. You can save as often as you like and keep testing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SaveResults(onSaved = {})
            HorizontalDivider()
            Text("All tests", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.size(8.dp))
        plan.forEach { s ->
            val st = Steps.state(context, s.id)
            ListItem(
                headlineContent = { Text(s.title) },
                supportingContent = { Text(statusText(st) + " · " + s.time) },
                leadingContent = { StepBadge(s.icon, done = Steps.finished(st), small = true) },
                modifier = Modifier.clickable { onPick(s.id) },
            )
        }
        Spacer(Modifier.size(32.dp))
    }
}

private fun statusText(state: StepState): String =
    when (state) {
        StepState.NotStarted -> "Not started"
        is StepState.Running -> "In progress"
        StepState.Done -> "Done"
        StepState.Skipped -> "Skipped"
    }

@Composable
private fun ProgressHeader(
    position: Int,
    total: Int,
    done: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Step $position of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("$done done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
    }
}

private fun iconFor(icon: StepIcon): ImageVector =
    when (icon) {
        StepIcon.HELLO -> Icons.Filled.Favorite
        StepIcon.SETUP -> Icons.Filled.Settings
        StepIcon.SOUND -> Icons.Filled.PlayArrow
        StepIcon.ALERT, StepIcon.ALARM -> Icons.Filled.Notifications
        StepIcon.LATE -> Icons.Filled.DateRange
        StepIcon.RESTART -> Icons.Filled.Refresh
        StepIcon.BATTERY -> Icons.Filled.Build
        StepIcon.SAMSUNG -> Icons.Filled.Phone
        StepIcon.NIGHT -> Icons.Filled.Lock
        StepIcon.MONITOR -> Icons.Filled.Face
        StepIcon.CLIPS -> Icons.Filled.Star
        StepIcon.SEND -> Icons.AutoMirrored.Filled.Send
    }

@Composable
private fun StepBadge(
    icon: StepIcon,
    done: Boolean = false,
    small: Boolean = false,
) {
    val size = if (small) 40.dp else 64.dp
    Box(
        Modifier
            .size(size)
            .background(
                if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else iconFor(icon),
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}

@Composable
private fun StepCard(
    step: Step,
    state: StepState,
    now: Long,
    refresh: Long,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var whyOpen by remember(step.id) { mutableStateOf(false) }
    var confirmed by remember(step.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(step.icon, done = Steps.finished(state))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "About ${step.time}" + if (step.optional) " · optional" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(step.intro, style = MaterialTheme.typography.bodyLarge)

            when (state) {
                StepState.NotStarted -> NotStarted(step, confirmed, { confirmed = it }, refresh, onChanged)
                is StepState.Running -> Running(step, state, now, refresh, onChanged)
                StepState.Done, StepState.Skipped -> Finished(step, state, refresh, onChanged)
            }

            if (step.id == Steps.WELCOME || step.id == Steps.RESULTS) return@Column
            HorizontalDivider()
            TextButton(onClick = { whyOpen = !whyOpen }) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (whyOpen) "Hide" else "Why this test?")
            }
            AnimatedVisibility(whyOpen) {
                Text(step.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Instructions(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun NotStarted(
    step: Step,
    confirmed: Boolean,
    onConfirm: (Boolean) -> Unit,
    refresh: Long,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    Instructions(step.howTo)
    val unmet = Needs(step, confirmed, onConfirm, refresh, onChanged)
    when (step.id) {
        Steps.CLIPS -> ClipTools(refresh, onChanged)
        Steps.RESULTS -> {
            SaveResults(onSaved = {
                Steps.finish(context, step)
                onChanged()
            })
            return
        }
    }
    BigButton(step.startLabel, enabled = unmet.isEmpty()) {
        when (step.id) {
            Steps.WELCOME, Steps.SETUP, Steps.CLIPS -> Steps.finish(context, step)
            else -> Steps.start(context, step)
        }
        onChanged()
    }
    if (unmet.isNotEmpty()) {
        Text(
            "Finish the items above first, or skip this test.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (step.id != Steps.WELCOME) SkipButton(step, onChanged)
}

@Composable
private fun SkipButton(
    step: Step,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    TextButton(onClick = {
        Steps.finish(context, step, skipped = true)
        onChanged()
    }) { Text("Skip this test") }
}

/** Shows each requirement with a tick or a fix-it button. Returns the unmet ones. */
@Composable
@Suppress("UNUSED_PARAMETER") // refresh: forces a re-read of the permissions (see TestApp)
private fun Needs(
    step: Step,
    confirmed: Boolean,
    onConfirm: (Boolean) -> Unit,
    refresh: Long,
    onChanged: () -> Unit,
): List<Need> {
    if (step.needs.isEmpty()) return emptyList()
    val context = LocalContext.current
    val device = DeviceState.snapshot(context)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    val unmet = mutableListOf<Need>()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        step.needs.forEach { need ->
            when (need) {
                Need.NOTIFICATIONS ->
                    NeedRow("Notifications allowed", device.notificationsOn, "Allow") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }.also { if (!it) unmet += need }
                Need.EXACT_ON ->
                    NeedRow("'Alarms & reminders' allowed", device.exactGranted, "Allow") { openExact(context) }
                        .also { if (!it) unmet += need }
                Need.EXACT_OFF ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Text("Not possible on this phone's Android version. Please skip this test.")
                        unmet += need
                    } else {
                        NeedRow("'Alarms & reminders' turned OFF", !device.exactGranted, "Turn off") { openExact(context) }
                            .also { if (!it) unmet += need }
                    }
                Need.POWER_SAVE_ON ->
                    NeedRow("Power saving ON", device.powerSave, "Open settings") {
                        open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    }.also { if (!it) unmet += need }
                Need.CONFIRM -> {
                    OutlinedButton(onClick = {
                        if (step.id == Steps.SAMSUNG_FREE) {
                            open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        } else {
                            open(context, Intent(Settings.ACTION_SETTINGS))
                        }
                    }) { Text(if (step.id == Steps.SAMSUNG_FREE) "Open app settings" else "Open Settings") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onConfirm(!confirmed) },
                    ) {
                        Checkbox(checked = confirmed, onCheckedChange = onConfirm)
                        Text(step.confirmText, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (!confirmed) unmet += need
                }
            }
        }
    }
    return unmet
}

@Composable
private fun NeedRow(
    label: String,
    ok: Boolean,
    action: String,
    onFix: () -> Unit,
): Boolean {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (ok) "Done" else "Needed",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok) FilledTonalButton(onClick = onFix) { Text(action) }
    }
    return ok
}

private fun open(
    context: Context,
    intent: Intent,
) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openExact(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // refresh: answers given on a notification show at once
private fun Running(
    step: Step,
    state: StepState.Running,
    now: Long,
    refresh: Long,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val left = state.dueMs - now
    val status =
        when (step.id) {
            Steps.SOUND -> "Listen for a short baby cry now."
            Steps.OVERNIGHT -> {
                val remaining = Prefs.pending(context).count { it.startsWith(Steps.OVERNIGHT + "~") } / 2
                "Running until morning. ${30 - remaining} of 30 checks done."
            }
            Steps.MONITOR ->
                if (left > 0) "Monitor playing. The baby cries in ${clock(left)}." else "Monitor playing. Leave it until morning."
            Steps.RESTART ->
                if (Prefs.lastBoot(context) > state.startMs) {
                    if (left > 0) "Restart detected. Wait ${clock(left)} more." else "Restart detected. Almost done."
                } else {
                    "Now restart the phone (hold the power button, then Restart)."
                }
            else -> if (left > 0) "The baby will cry in about ${clock(left)}." else "The baby should have cried by now."
        }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (left > 0 && step.id !in setOf(Steps.SOUND, Steps.OVERNIGHT, Steps.MONITOR, Steps.RESTART)) {
                Text(
                    "Close the app and lock the phone now.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    Steps.questions(context, step.id).forEach { q -> QuestionCard(q, onChanged) }
    if (step.afterText.isNotEmpty() && now >= state.dueMs) {
        AssistChip(onClick = {}, label = { Text(step.afterText) }, leadingIcon = { Icon(Icons.Filled.Info, null) })
    }
    val ready = Steps.canFinish(context, step, now)
    val label =
        when (step.id) {
            Steps.OVERNIGHT -> if (now >= state.dueMs) "Finish" else "Finish early"
            Steps.MONITOR -> "Stop and finish"
            else -> "Next test"
        }
    BigButton(label, enabled = ready) {
        Steps.finish(context, step)
        onChanged()
    }
    if (!ready && Steps.questions(context, step.id).isNotEmpty()) {
        Text("Answer the question above to continue.", style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            Steps.reset(context, step)
            onChanged()
        }) { Text("Start again") }
        SkipButton(step, onChanged)
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600} h ${(s % 3600) / 60} min" else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun QuestionCard(
    q: Question,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(q.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Answers.IN_APP.forEach { (code, label) ->
                OutlinedButton(
                    onClick = {
                        dev.babysim.spike.Questions.answer(context, q.id, code, via = "app")
                        onChanged()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                ) { Text(label, style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun Finished(
    step: Step,
    state: StepState,
    refresh: Long,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        if (state == StepState.Done) "This test is done. Thank you!" else "You skipped this test.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    if (step.id == Steps.RESULTS) SaveResults(onSaved = onChanged)
    OutlinedButton(onClick = {
        Steps.reset(context, step)
        onChanged()
    }) { Text("Do this test again") }
}

@Composable
private fun SaveResults(onSaved: () -> Unit) {
    val context = LocalContext.current
    val name =
        "baby-sim-test-${Build.MANUFACTURER}-${Build.MODEL}-t${context.applicationInfo.targetSdkVersion}-" +
            SpikeLog.iso(System.currentTimeMillis()).substring(0, 10) + ".csv"
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(SpikeLog.text(context).toByteArray()) }
                SpikeLog.log(context, "log_saved")
                onSaved()
            }
        }
    BigButton("Save results") { save.launch(name.replace(Regex("[^A-Za-z0-9._-]"), "_")) }
    OutlinedButton(onClick = {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Baby Sim phone test results")
                    .putExtra(Intent.EXTRA_TEXT, SpikeLog.text(context)),
                "Send results",
            ),
        )
    }) { Text("Send another way (email, message…)") }
}

@Composable
@Suppress("UNUSED_PARAMETER") // refresh: the selected clip shows at once
private fun ClipTools(
    refresh: Long,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val path = Clips.import(context, uri)
                Prefs.setClip(context, path)
                SpikeLog.log(context, "clip_imported", detail = "clip=${File(path).name}")
                onChanged()
            }
        }
    OutlinedButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Text("Add a sound file") }
    val selected = Prefs.clip(context)
    Clips.all(context).forEach { clip ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        Prefs.setClip(context, clip)
                        onChanged()
                    },
        ) {
            RadioButton(selected = clip == selected, onClick = {
                Prefs.setClip(context, clip)
                onChanged()
            })
            Text(Clips.name(clip), style = MaterialTheme.typography.bodyLarge)
        }
    }
    FilledTonalButton(onClick = {
        player?.release()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        player = runCatching { Clips.player(context, Prefs.clip(context), attrs, loop = false).also { it.start() } }.getOrNull()
        SpikeLog.log(context, "t0_preview", detail = "clip=${Clips.name(Prefs.clip(context))}")
    }) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Play")
    }
    Text("How real does it sound?", style = MaterialTheme.typography.titleMedium)
    Row {
        (1..5).forEach { stars ->
            IconButton(onClick = {
                SpikeLog.log(context, "rating", detail = "tier=T0;stars=$stars;clip=${Clips.name(Prefs.clip(context))}")
                android.widget.Toast.makeText(context, "Saved: $stars stars", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.Star, contentDescription = "$stars stars", tint = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun Footer(
    context: Context,
    refresh: Long,
) {
    val state = DeviceState.snapshot(context)
    val parts =
        listOfNotNull(
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            "Android ${Build.VERSION.RELEASE}",
            if (Steps.shortPlan(context)) "extra test" else "main test",
            state.batteryPct.takeIf { it >= 0 }?.let { "battery $it%" },
            "monitor on".takeIf { Prefs.monitorRunning(context) },
        )
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
    )
}
