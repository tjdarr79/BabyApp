package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.SimInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `nextEventAt` and `nextWakeAt` are exact: nothing fires before them, and something fires at them. */
class NextWakePredictionTest {
    private val engine = EngineFixtures.engine()

    @Test
    fun nextEventAtIsExactlyWhenTheNextEventFires() {
        for ((band, temperament) in EngineFixtures.allProfiles()) {
            val config = EngineFixtures.config(band, temperament, seed = 1234)
            var step = engine.initialize(config, T0)
            repeat(STEPS) {
                val next = step.nextEventAt ?: return@repeat
                val justBefore = engine.advance(config, step.state, SimInstant(next.epochMillis - 1), predict = false)
                assertTrue(justBefore.events.isEmpty(), "$band/$temperament: events before predicted $next")
                step = engine.advance(config, step.state, next)
                assertTrue(step.events.isNotEmpty(), "$band/$temperament: no event at predicted $next")
                assertTrue(step.events.all { it.at == next }, "$band/$temperament: events not at $next")
            }
        }
    }

    @Test
    fun nextWakeAtIsExactlyWhenTheNextAlertFires() {
        for ((band, temperament) in EngineFixtures.allProfiles()) {
            val config = EngineFixtures.config(band, temperament, seed = 99)
            var step = engine.initialize(config, T0)
            var alertsSeen = 0
            while (alertsSeen < ALERTS) {
                val wake = step.nextWakeAt ?: break
                val before = engine.advance(config, step.state, SimInstant(wake.epochMillis - 1), predict = false)
                assertTrue(before.events.none { it.type.alerts }, "$band/$temperament: alert before $wake")
                step = engine.advance(config, before.state, wake)
                val alerts = step.events.filter { it.type.alerts }
                assertTrue(alerts.isNotEmpty(), "$band/$temperament: no alert at predicted $wake")
                assertTrue(alerts.all { it.at == wake })
                alertsSeen++
            }
            assertTrue(alertsSeen > 0, "$band/$temperament: an unattended baby must alert at least once")
        }
    }

    @Test
    fun anUnattendedBabyAlwaysHasAPredictedWakeUntilItPauses() {
        val config = EngineFixtures.config()
        val first = engine.initialize(config, T0)
        val wake = assertNotNull(first.nextWakeAt, "a fresh baby will need care within 24 h")
        assertTrue(wake > T0)
        val paused = engine.pause(config, first.state, T0)
        assertEquals(null, paused.nextWakeAt)
        assertEquals(null, paused.nextEventAt)
    }

    private companion object {
        const val STEPS = 40
        const val ALERTS = 6
    }
}
