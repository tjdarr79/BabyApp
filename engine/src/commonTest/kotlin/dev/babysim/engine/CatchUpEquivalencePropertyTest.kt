package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.random.SplitMix64
import dev.babysim.engine.random.nextDouble
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * docs/04 §8: catching up in one call or in many calls, split anywhere, gives the identical state and events.
 * This is what lets the app sleep between alarms and still simulate exactly.
 */
class CatchUpEquivalencePropertyTest {
    private val engine = EngineFixtures.engine()
    private val hourMillis = 3_600_000L

    @Test
    fun splittingACatchUpAnywhereGivesIdenticalResults() {
        val gen = SplitMix64(20261119)
        repeat(CASES) { case ->
            val band = AgeBandId.entries[(gen.nextDouble() * AgeBandId.entries.size).toInt()]
            val temperament = TemperamentId.entries[(gen.nextDouble() * TemperamentId.entries.size).toInt()]
            val config = EngineFixtures.config(band, temperament, seed = gen.nextLong())
            val start = SimInstant(T0.epochMillis + (gen.nextDouble() * hourMillis).toLong())
            val end = SimInstant(start.epochMillis + (2 * hourMillis + gen.nextDouble() * 22 * hourMillis).toLong())
            val splits =
                List(1 + (gen.nextDouble() * 4).toInt()) {
                    SimInstant(start.epochMillis + (gen.nextDouble() * end.millisSince(start)).toLong())
                }.sorted()

            val initial = engine.initialize(config, start).state
            val whole = engine.advance(config, initial, end, predict = false)

            var state = initial
            val events = mutableListOf<BabyEvent>()
            for (point in splits + end) {
                val step = engine.advance(config, state, point, predict = false)
                state = step.state
                events += step.events
            }

            val label = "case $case: $band/$temperament seed=${config.seed} splits=$splits end=$end"
            assertEquals(whole.events, events, "events differ, $label")
            assertEquals(whole.state, state, "state differs, $label")
        }
    }

    @Test
    fun predictionsAgreeAcrossSplits() {
        val gen = SplitMix64(7)
        repeat(50) {
            val config = EngineFixtures.config(seed = gen.nextLong())
            val initial = engine.initialize(config, T0).state
            val end = SimInstant(T0.epochMillis + 12 * hourMillis)
            val mid = SimInstant(T0.epochMillis + (gen.nextDouble() * 12 * hourMillis).toLong())
            val whole = engine.advance(config, initial, end)
            val split = engine.advance(config, engine.advance(config, initial, mid, predict = false).state, end)
            assertEquals(whole.nextEventAt, split.nextEventAt)
            assertEquals(whole.nextWakeAt, split.nextWakeAt)
        }
    }

    private companion object {
        const val CASES = 1000
    }
}
