package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** docs/01 B8: a wall clock moved backward never runs the engine backward. */
class ClockBackwardClampTest {
    private val engine = EngineFixtures.engine()
    private val config = EngineFixtures.config()

    @Test
    fun advancingToAnEarlierTimeChangesNothing() {
        val at3h = engine.advance(config, engine.initialize(config, T0).state, T0 + 3.hours).state
        val backward = engine.advance(config, at3h, T0 + 1.hours)
        assertEquals(at3h, backward.state)
        assertTrue(backward.events.isEmpty())
        assertEquals(T0 + 3.hours, backward.state.meta.lastAdvancedTo)
    }

    @Test
    fun afterABackwardJumpTimeResumesFromTheLatestPoint() {
        val initial = engine.initialize(config, T0).state
        val direct = engine.advance(config, initial, T0 + 6.hours, predict = false)

        val at3h = engine.advance(config, initial, T0 + 3.hours, predict = false)
        val backward = engine.advance(config, at3h.state, T0 + 1.hours, predict = false)
        val forward = engine.advance(config, backward.state, T0 + 6.hours, predict = false)

        assertEquals(direct.state, forward.state)
        assertEquals(direct.events, at3h.events + backward.events + forward.events)
    }
}
