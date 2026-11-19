package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.Unanswered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Owner decision 2026-11-19 (docs/01): unattended crying stops at the cap, but the alert stays open until a
 * caregiver responds. A quiet alert counts as one unanswered episode and sounds again if the baby cries again.
 */
class AlertStaysOpenUntilAnsweredTest {
    private val engine = EngineFixtures.engine()
    private val config = EngineFixtures.config(band = AgeBandId.NEWBORN)

    private data class Quiet(
        val state: BabyState,
        val events: List<BabyEvent>,
    )

    /** Leaves the baby alone, a minute at a time, until its first alert has gone quiet. */
    private fun untilQuietAlert(): Quiet {
        var state = engine.initialize(config, T0).state
        val events = mutableListOf<BabyEvent>()
        repeat(MAX_MINUTES) { minute ->
            val step = engine.advance(config, state, T0 + (minute + 1).minutes, predict = false)
            state = step.state
            events += step.events
            if (state.careRequest?.quietSince != null) return Quiet(state, events)
        }
        error("no alert went quiet within $MAX_MINUTES minutes")
    }

    @Test
    fun cryingStopsButTheAlertStaysOpen() {
        val quiet = untilQuietAlert()
        val closing = setOf(EventType.CARE_REQUEST_RESOLVED, EventType.CARE_REQUEST_EXPIRED)
        assertTrue(quiet.events.none { it.type in closing }, "nothing closed the alert")
        assertNotNull(quiet.state.careRequest)
        assertEquals(1, quiet.state.unanswered.expiredCount, "one unanswered episode")
        assertNotNull(quiet.state.unanswered.firstUnansweredAt)
    }

    @Test
    fun anyActionAnswersAQuietAlert() {
        val quiet = untilQuietAlert()
        val at = quiet.state.meta.lastAdvancedTo
        val checked = engine.apply(config, quiet.state, CareAction.Do(CareActionType.CHECK_DIAPER), at, predict = false)
        val next = engine.advance(config, checked.state, at + 1.minutes, predict = false)
        val resolved = next.events.single { it.type == EventType.CARE_REQUEST_RESOLVED }
        assertEquals(quiet.state.careRequest?.id, resolved.careRequestId)
        assertNull(next.state.careRequest)
        assertEquals(Unanswered(), next.state.unanswered, "the check counts as a response")
    }

    @Test
    fun aQuietAlertSoundsAgainWhenTheBabyCriesAgain() {
        val quiet = untilQuietAlert()
        val later = engine.advance(config, quiet.state, quiet.state.meta.lastAdvancedTo + 6.hours, predict = false)
        val next =
            later.events.first {
                it.type == EventType.CARE_REQUEST_ESCALATED || it.type == EventType.CARE_REQUEST_OPENED
            }
        assertEquals(EventType.CARE_REQUEST_ESCALATED, next.type, "the same alert sounds again")
        assertEquals(quiet.state.careRequest?.id, next.careRequestId)
    }

    @Test
    fun alertsNeverCloseOnTheirOwn() {
        for ((band, temperament) in EngineFixtures.allProfiles()) {
            val c = EngineFixtures.config(band, temperament, seed = 7)
            val result = engine.advance(c, engine.initialize(c, T0).state, T0 + 48.hours)
            assertTrue(result.events.none { it.type == EventType.CARE_REQUEST_RESOLVED }, "$band/$temperament")
        }
    }

    private companion object {
        const val MAX_MINUTES = 12 * 60
    }
}
