package dev.babysim.spike

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.babysim.spike.ui.SpikeTheme
import dev.babysim.spike.ui.TestApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the guided test screens on the JVM at Galaxy A54 size (411 × 891 dp), so the UI can be checked
 * without an emulator. Images: app/build/outputs/roborazzi/. Each test seeds Prefs the way the app would.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).edit().clear().commit()
        SpikeLog.clear(context)
    }

    private fun done(vararg ids: String) = ids.forEach { Prefs.setStep(context, it, "done") }

    private fun shoot(name: String) =
        captureRoboImage("build/outputs/roborazzi/$name.png") { SpikeTheme { TestApp(resumeTick = 0, ticking = false) } }

    @Test
    fun welcome() = shoot("01-welcome")

    @Test
    fun setup() {
        done(Steps.WELCOME)
        shoot("02-setup")
    }

    @Test
    fun alertNotStarted() {
        done(Steps.WELCOME, Steps.SETUP, Steps.SOUND)
        shoot("03-alert-not-started")
    }

    @Test
    fun alertCountingDown() {
        done(Steps.WELCOME, Steps.SETUP, Steps.SOUND)
        val now = System.currentTimeMillis()
        Prefs.setStep(context, Steps.ALERT_MEDIA, "running:$now:${now + 102_000}")
        shoot("04-alert-counting-down")
    }

    @Test
    fun alertWaitingForAnswers() {
        done(Steps.WELCOME, Steps.SETUP, Steps.SOUND)
        val now = System.currentTimeMillis()
        Prefs.setStep(context, Steps.ALERT_MEDIA, "running:${now - 200_000}:${now - 80_000}")
        Questions.ask(context, "alertmedia~x-exact-t1", Steps.ALERT_MEDIA, "Did you hear the alert (a short cry)?")
        Questions.ask(context, "alertmedia~x exact-media-t2", Steps.ALERT_MEDIA, "Did the crying keep going for about a minute?")
        shoot("05-alert-questions")
    }

    @Test
    fun overnight() {
        done(Steps.WELCOME, Steps.SETUP, Steps.SOUND, Steps.ALERT_MEDIA, Steps.ALERT_ALARM, Steps.ALERT_LATE, Steps.RESTART, Steps.BATTERY_SAVER)
        shoot("06-overnight")
    }

    @Test
    fun results() {
        Steps.ALL.dropLast(1).forEach { Prefs.setStep(context, it.id, "done") }
        shoot("07-send-results")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkMode() {
        done(Steps.WELCOME, Steps.SETUP, Steps.SOUND)
        val now = System.currentTimeMillis()
        Prefs.setStep(context, Steps.ALERT_MEDIA, "running:${now - 200_000}:${now - 80_000}")
        Questions.ask(context, "alertmedia~x-exact-t1", Steps.ALERT_MEDIA, "Did you hear the alert (a short cry)?")
        shoot("08-dark-mode")
    }

    @Test
    fun appIcon() =
        captureRoboImage("build/outputs/roborazzi/09-app-icon.png") {
            Box(Modifier.background(Color.White)) {
                Box(Modifier.size(108.dp).background(colorResource(R.color.ic_launcher_background), CircleShape)) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
                }
            }
        }
}
