package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.state.PauseReason
import dev.babysim.engine.state.Unanswered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PauseFreezesNeedsTest {
    private val engine = EngineFixtures.engine()
    private val config = EngineFixtures.config()

    @Test
    fun needsDoNotAccumulateWhilePaused() {
        val running = engine.advance(config, engine.initialize(config, T0).state, T0 + 50.minutes).state
        val paused = engine.pause(config, running, T0 + 50.minutes)
        assertEquals(PauseReason.USER, paused.state.pause?.reason)
        assertEquals(EventType.PAUSED, paused.events.last().type)

        val later = engine.advance(config, paused.state, T0 + 50.minutes + 10.hours)
        assertTrue(later.events.isEmpty(), "no events while paused")
        assertEquals(paused.state.drives, later.state.drives)
        assertEquals(paused.state.activity, later.state.activity)
        assertNull(later.nextWakeAt)
        assertNull(later.nextEventAt)
    }

    @Test
    fun resumingContinuesWhereTheBabyLeftOff() {
        val pauseAt = T0 + 50.minutes
        // A whole day, so local time of day (day/night rhythm) is the same before and after the pause.
        val pauseLength = 24.hours
        val running = engine.advance(config, engine.initialize(config, T0).state, pauseAt).state
        val paused = engine.pause(config, running, pauseAt).state

        val resumed = engine.resume(config, paused, pauseAt + pauseLength)
        assertNull(resumed.state.pause)
        assertEquals(EventType.RESUMED, resumed.events.last().type)
        assertEquals(paused.drives, resumed.state.drives)
        assertNotNull(resumed.nextWakeAt, "a resumed baby has needs again")

        // Engine time skips the pause, so the resumed baby replays exactly what the never-paused one did, a day later.
        val neverPaused = engine.advance(config, running, pauseAt + 12.hours, predict = false).events
        val afterResume =
            engine
                .advance(
                    config,
                    resumed.state,
                    pauseAt + pauseLength + 12.hours,
                    predict = false,
                ).events
        val shift = pauseLength.inWholeMilliseconds
        // Resuming deliberately restarts the unanswered-request count, so compare up to the never-paused baby's
        // auto-pause, which the resumed baby must not copy.
        val autoPauseIndex = neverPaused.indexOfFirst { it.type == EventType.AUTO_PAUSED }.takeIf { it >= 0 }
        val expected = neverPaused.take(autoPauseIndex ?: neverPaused.size)
        assertTrue(expected.size >= 10, "enough behavior to compare")
        assertEquals(
            expected.map { it.type to it.at.epochMillis },
            afterResume.take(expected.size).map { it.type to it.at.epochMillis - shift },
            "same behavior, at the same times shifted by the pause",
        )
        val unansweredAtPause = paused.copy(unanswered = Unanswered(expiredCount = 2, firstUnansweredAt = T0))
        assertEquals(
            Unanswered(),
            engine.resume(config, unansweredAtPause, pauseAt + pauseLength).state.unanswered,
            "resume restarts the unanswered count",
        )
    }

    @Test
    fun pausingTwiceIsANoOp() {
        val paused = engine.pause(config, engine.initialize(config, T0).state, T0 + 1.minutes).state
        val again = engine.pause(config, paused, T0 + 2.minutes)
        assertEquals(paused.pause, again.state.pause)
        assertTrue(again.events.none { it.type == EventType.PAUSED })
    }
}
