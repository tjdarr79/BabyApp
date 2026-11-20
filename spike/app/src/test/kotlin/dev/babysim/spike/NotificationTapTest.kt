package dev.babysim.spike

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tester report (m3-4): tapping a test notification did not open the app. Both notifications must open it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationTapTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun assertOpensTheApp(
        tap: PendingIntent?,
        from: String,
    ) {
        assertNotNull("tapping the notification must do something", tap)
        val shadow = shadowOf(tap)
        assertTrue("it must start an activity", shadow.isActivityIntent)
        val intent = shadow.savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue("must start the app even when it is not running", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(from, intent.getStringExtra(Notifications.EXTRA_FROM))
    }

    @Test
    fun tappingTheAlertOpensTheApp() {
        Notifications.careAlert(context, "The test baby needs you", "Did you hear this alert?", "q-t1")
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertOpensTheApp(posted.contentIntent, "alert")
    }

    @Test
    fun tappingTheCryingNotificationOpensTheApp() {
        Notifications.ensureChannels(context)
        val stop = PendingIntent.getService(context, 1, Intent(context, PlaybackService::class.java), PendingIntent.FLAG_IMMUTABLE)
        val crying = Notifications.playback(context, "The test baby is crying", stop, "q-alarm-t2").build()
        assertOpensTheApp(crying.contentIntent, "playback")
    }
}
