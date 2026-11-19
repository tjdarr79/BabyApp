package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.state.PauseReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** docs/01 B1 (owner decision): auto-pause after 3 unanswered care requests or 4 h without any response. */
class UnattendedAutoPauseTest {
    private val engine = EngineFixtures.engine()
    private val model = EngineFixtures.params.model

    @Test
    fun anUnattendedBabyAutoPausesForEveryProfile() {
        for ((band, temperament) in EngineFixtures.allProfiles()) {
            val config = EngineFixtures.config(band, temperament, seed = 7)
            val result = engine.advance(config, engine.initialize(config, T0).state, T0 + 48.hours)
            val label = "$band/$temperament"

            val pauses = result.events.filter { it.type == EventType.AUTO_PAUSED }
            assertEquals(1, pauses.size, "$label: exactly one auto-pause")
            val pausedAt = pauses.single().at
            assertTrue(result.events.none { it.at > pausedAt }, "$label: nothing happens after auto-pause")
            assertNull(result.nextWakeAt, "$label: no wake scheduled while paused")

            val pause = assertNotNull(result.state.pause, label)
            when (pause.reason) {
                PauseReason.AUTO_UNANSWERED_REQUESTS -> {
                    val expired = result.events.count { it.type == EventType.CARE_REQUEST_EXPIRED }
                    assertTrue(expired >= model.autoPauseUnansweredRequests.value, "$label: expired=$expired")
                }
                PauseReason.AUTO_NO_RESPONSE -> {
                    val firstRequest = result.events.first { it.type == EventType.CARE_REQUEST_OPENED }.at
                    val hours = pausedAt.millisSince(firstRequest) / 3_600_000.0
                    assertTrue(hours >= model.autoPauseHoursWithoutResponse.value, "$label: paused after $hours h")
                }
                PauseReason.USER -> error("$label: unexpected user pause")
            }
        }
    }

    @Test
    fun theAutoPauseIsAnAlert() {
        val config = EngineFixtures.config()
        var step = engine.initialize(config, T0)
        while (step.state.pause == null) {
            val wake = assertNotNull(step.nextWakeAt, "an unattended baby keeps alerting until it pauses")
            step = engine.advance(config, step.state, wake)
        }
        assertTrue(step.events.any { it.type == EventType.AUTO_PAUSED })
    }

    @Test
    fun noRequestIsLeftOpenWhenPaused() {
        val config = EngineFixtures.config()
        val result = engine.advance(config, engine.initialize(config, T0).state, T0 + 48.hours)
        assertNull(result.state.careRequest)
        val opened = result.events.count { it.type == EventType.CARE_REQUEST_OPENED }
        val closed =
            result.events.count {
                it.type == EventType.CARE_REQUEST_RESOLVED || it.type == EventType.CARE_REQUEST_EXPIRED
            }
        assertEquals(opened, closed, "every opened request is resolved or expired")
    }
}
