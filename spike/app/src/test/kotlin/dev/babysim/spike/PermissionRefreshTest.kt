package dev.babysim.spike

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.babysim.spike.ui.SpikeTheme
import dev.babysim.spike.ui.TestApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Tester report (2026-11-20, both phones): a permission turned on in Settings only showed as allowed after
 * toggling it again. Coming back to the app (a resume) must show the new state at once, without the clock tick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionRefreshTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).edit().clear().commit()
        listOf(Steps.WELCOME, Steps.SETUP, Steps.SOUND).forEach { Prefs.setStep(context, it, "done") }
    }

    @Test
    fun allowingAlarmsInSettingsShowsAsSoonAsTheTesterComesBack() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val resumes = mutableIntStateOf(0)
        rule.setContent { SpikeTheme { TestApp(resumeTick = resumes.intValue, ticking = false) } }
        rule.onNodeWithText("Allow").assertExists()

        ShadowAlarmManager.setCanScheduleExactAlarms(true) // the tester allowed it in Settings…
        resumes.intValue++ // …and came back to the app
        rule.waitForIdle()

        rule.onNodeWithText("Allow").assertDoesNotExist()
    }
}
