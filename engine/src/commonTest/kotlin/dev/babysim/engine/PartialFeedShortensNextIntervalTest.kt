package dev.babysim.engine

import dev.babysim.engine.CauseFixtures.calm
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.state.BabyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** docs/04 §5.2: ending a feed early leaves hunger behind, so the baby is hungry again sooner. */
class PartialFeedShortensNextIntervalTest {
    private val engine = EngineFixtures.engine()

    @Test
    fun aPartialFeedBringsTheNextHungerCueForward() {
        for (seed in 1L..10L) {
            val config = EngineFixtures.config(AgeBandId.M2_3, seed = seed)
            val hungry = calm(config).let { it.copy(drives = it.drives.copy(hunger = 1.0)) }
            val feeding = engine.apply(config, hungry, CareAction.Start(CareActionType.FEED), T0, predict = false)
            assertEquals(ActionOutcome.FEEDING, feeding.events.last().outcome)

            // Full feed: the baby feeds until full and turns away by itself.
            val full = engine.advance(config, feeding.state, T0 + 40.minutes, predict = false)
            val finished = full.events.first { it.type == EventType.FEED_FINISHED_CUE }.at
            val fullRange =
                EngineFixtures.params
                    .age(AgeBandId.M2_3)
                    .feedDurationMinutes.value
            val feedMinutes = finished.millisSince(T0) / 60_000.0
            assertTrue(feedMinutes in fullRange.min - 1..fullRange.max + 1, "feed length $feedMinutes min")

            // Partial feed: stopped after 3 minutes.
            val partial = engine.apply(config, feeding.state, CareAction.Stop, T0 + 3.minutes, predict = false)
            assertTrue(partial.state.drives.hunger > full.state.drives.hunger)

            val afterFull =
                minutesUntilHungry(config, engine.advance(config, feeding.state, finished, predict = false).state)
            val afterPartial = minutesUntilHungry(config, partial.state)
            assertTrue(afterPartial < afterFull, "seed $seed: partial $afterPartial min vs full $afterFull min")
        }
    }

    private fun minutesUntilHungry(
        config: dev.babysim.engine.model.BabyConfig,
        fed: BabyState,
    ): Double {
        val start = fed.meta.lastAdvancedTo
        var state = fed
        for (minute in 1..600) {
            val at = SimInstant(start.epochMillis + minute * 60_000L)
            state = engine.advance(config, state, at, predict = false).state
            if (state.drives.hunger >= state.thresholds.hungerCueAt) return minute.toDouble()
        }
        return assertNotNull(null, "hunger cue within 10 h")
    }
}
