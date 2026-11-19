package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
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
    fun actionLogsReplayIdenticallyWhateverTheCatchUpSplits() {
        val gen = SplitMix64(424242)
        val actions =
            CareAction.CONTINUOUS.map { CareAction.Start(it) } + CareAction.Stop +
                (CareActionType.entries - CareAction.CONTINUOUS).map { CareAction.Do(it) }
        repeat(ACTION_CASES) { case ->
            val band = AgeBandId.entries[(gen.nextDouble() * AgeBandId.entries.size).toInt()]
            val config = EngineFixtures.config(band, seed = gen.nextLong())
            val end = SimInstant(T0.epochMillis + 12 * hourMillis)
            val log =
                List(1 + (gen.nextDouble() * 12).toInt()) {
                    SimInstant(T0.epochMillis + (gen.nextDouble() * 12 * hourMillis).toLong()) to
                        actions[(gen.nextDouble() * actions.size).toInt()]
                }.sortedBy { it.first }
            val extraSplits =
                List(1 + (gen.nextDouble() * 5).toInt()) {
                    SimInstant(T0.epochMillis + (gen.nextDouble() * 12 * hourMillis).toLong())
                }

            fun replay(splits: List<SimInstant>): Pair<List<BabyEvent>, dev.babysim.engine.state.BabyState> {
                var state = engine.initialize(config, T0).state
                val events = mutableListOf<BabyEvent>()
                val steps = (log.map { it.first to it.second } + splits.map { it to null }).sortedBy { it.first }
                for ((at, action) in steps) {
                    val step =
                        if (action == null) {
                            engine.advance(config, state, at, predict = false)
                        } else {
                            engine.apply(config, state, action, at, predict = false)
                        }
                    state = step.state
                    events += step.events
                }
                val last = engine.advance(config, state, end, predict = false)
                return (events + last.events) to last.state
            }

            val direct = replay(emptyList())
            val split = replay(extraSplits)
            assertEquals(direct.first, split.first, "case $case: events differ, log=$log splits=$extraSplits")
            assertEquals(direct.second, split.second, "case $case: state differs")
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
        const val ACTION_CASES = 300
    }
}
