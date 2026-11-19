package dev.babysim.engine

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * docs/04 §8: catching up after a long absence must be fast. Worst case: 7 simulated days in one call with the
 * auto-pause switched off (so the baby keeps living), plus the look-ahead. Budget: < 250 ms per simulated day on
 * the JVM. A phone is slower, but this budget leaves roughly a 10× margin for a week away.
 */
class CatchUpPerformanceTest {
    private val params =
        EngineFixtures.params.let { p ->
            p.copy(
                model =
                    p.model.copy(
                        autoPauseUnansweredRequests = p.model.autoPauseUnansweredRequests.copy(value = Int.MAX_VALUE),
                        autoPauseHoursWithoutResponse = p.model.autoPauseHoursWithoutResponse.copy(value = 1e9),
                    ),
            )
        }
    private val engine = BehaviorEngine(params)

    @Test
    fun sevenDaysCatchUpWithinBudget() {
        val configs = AgeBandId.entries.map { EngineFixtures.config(it, TemperamentId.DEMANDING, seed = 77) }
        // Warm up the JIT so the measurement reflects steady-state speed.
        configs.forEach {
            engine.advance(
                it,
                engine.initialize(it, EngineFixtures.T0).state,
                EngineFixtures.T0 + 2.days,
            )
        }

        for (config in configs) {
            val initial = engine.initialize(config, EngineFixtures.T0).state
            val started = System.nanoTime()
            val result = engine.advance(config, initial, EngineFixtures.T0 + 7.days)
            val msPerDay = (System.nanoTime() - started) / 1e6 / 7
            assertTrue(result.state.pause == null, "${config.ageBand}: auto-pause is off for this test")
            assertTrue(msPerDay < BUDGET_MS_PER_DAY, "${config.ageBand}: $msPerDay ms per simulated day")
        }
    }

    private companion object {
        const val BUDGET_MS_PER_DAY = 250.0
    }
}
