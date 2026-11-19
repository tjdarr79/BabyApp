package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.caregiver.AttentiveCaregiver
import dev.babysim.engine.caregiver.CaregiverDriver
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.SimInstant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reviewer decision 2026-11-19 (docs/01): newborn day naps link sleep cycles up to a 2–3 h nap length, with an
 * occasional 15–25 min catnap. A need (hunger, a dirty diaper) still wakes the baby earlier.
 */
class NewbornDayNapsTest {
    private val engine = EngineFixtures.engine()
    private val nap =
        checkNotNull(
            EngineFixtures.params.ages
                .getValue(AgeBandId.NEWBORN)
                .dayNapMinutes.value,
        )
    private val catnap = EngineFixtures.params.model.catnapMinutes.value

    /** Lengths in minutes of naps that start between 07:00 and 16:00 (they end before night begins). */
    private fun dayNaps(seed: Long): List<Double> {
        val config = EngineFixtures.config(band = AgeBandId.NEWBORN, seed = seed)
        val driver = CaregiverDriver(AttentiveCaregiver, EngineFixtures.params)
        var state = engine.initialize(config, T0).state
        val events = mutableListOf<BabyEvent>()
        for (step in 1..DAYS * STEPS_PER_DAY) {
            val t = SimInstant(T0.epochMillis + step * STEP_MILLIS)
            val advanced = engine.advance(config, state, t, predict = false)
            events += advanced.events
            state = advanced.state
            val action = driver.decide(config, state) ?: continue
            val acted = engine.apply(config, state, action, t, predict = false)
            events += acted.events
            state = acted.state
        }
        return events
            .filter { it.type == EventType.FELL_ASLEEP && minuteOfDay(it.at) in DAY_START until LAST_NAP_START }
            .mapNotNull { start ->
                events
                    .firstOrNull { it.type == EventType.WOKE && it.at > start.at }
                    ?.let { it.at.millisSince(start.at) / MILLIS_PER_MINUTE.toDouble() }
            }
    }

    private fun minuteOfDay(t: SimInstant): Int = (t.epochMillis.mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()

    @Test
    fun dayNapsLinkCyclesButNeverPassTheNapLength() {
        val naps = SEEDS.flatMap { dayNaps(it) }
        assertTrue(naps.size >= SEEDS.size * DAYS, "enough day naps: ${naps.size}")
        assertTrue(naps.all { it <= nap.max + TICK_MINUTES }, "no nap is longer than ${nap.max} min: ${naps.max()}")
        assertTrue(naps.count { it >= nap.min } >= naps.size / 4, "many naps reach ${nap.min} min: $naps")
        assertTrue(naps.any { it <= catnap.max + TICK_MINUTES }, "some short naps")
    }

    private companion object {
        val SEEDS = (1L..8L).toList()
        const val DAYS = 3
        const val STEP_MILLIS = 120_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_DAY = 86_400_000L
        const val STEPS_PER_DAY = (MILLIS_PER_DAY / STEP_MILLIS).toInt()
        const val DAY_START = 7 * 60
        const val LAST_NAP_START = 16 * 60
        const val TICK_MINUTES = 1.0
    }
}
