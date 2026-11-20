package dev.babysim.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import dev.babysim.spike.ui.SpikeTheme
import dev.babysim.spike.ui.TestApp

/** Hosts the guided test (ui/TestApp.kt). Bumps a counter on every resume so permission changes show at once. */
class MainActivity : ComponentActivity() {
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        SpikeLog.log(this, "app_opened")
        setContent { SpikeTheme { TestApp(resumes.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }
}
