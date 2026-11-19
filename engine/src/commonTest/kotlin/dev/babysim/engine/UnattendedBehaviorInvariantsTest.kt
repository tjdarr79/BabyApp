package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.VocalType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Structural invariants of the event stream for every profile. Behavior calibration is M2.6. */
class UnattendedBehaviorInvariantsTest {
    private val engine = EngineFixtures.engine()

    @Test
    fun eventStreamsAreWellFormed() {
        for ((band, temperament) in EngineFixtures.allProfiles()) {
            val config = EngineFixtures.config(band, temperament, seed = 31)
            val events = engine.advance(config, engine.initialize(config, T0).state, T0 + 24.hours).events
            val label = "$band/$temperament"
            assertTrue(events.all { it.babyId == config.id }, "$label: every event carries the baby's id")
            assertEquals(events.sortedBy { it.at }, events, "$label: events are in time order")
            assertTrue(events.all { it.at.epochMillis % 30_000L == 0L }, "$label: events fall on the tick grid")
            assertTrue(
                events.any { it.type == EventType.CARE_REQUEST_OPENED },
                "$label: needs eventually call for care",
            )
            assertTrue(events.any { it.type == EventType.FELL_ASLEEP }, "$label: the baby sleeps")
        }
    }

    @Test
    fun vocalizationsMatchTheAgeRepertoire() {
        for (band in AgeBandId.entries) {
            val repertoire =
                EngineFixtures.params
                    .age(band)
                    .vocalRepertoire.value
                    .toSet()
            for (seed in 1L..5L) {
                val config = EngineFixtures.config(band, seed = seed)
                val vocals =
                    engine
                        .advance(config, engine.initialize(config, T0).state, T0 + 24.hours)
                        .events
                        .mapNotNull { it.vocal }
                assertTrue(
                    vocals.all { it in repertoire },
                    "$band: vocal outside repertoire: ${vocals.toSet() - repertoire}",
                )
            }
        }
        val newbornVocals =
            EngineFixtures.params
                .age(AgeBandId.NEWBORN)
                .vocalRepertoire.value
        assertTrue(VocalType.COO !in newbornVocals, "newborns do not coo yet (docs/06)")
    }

    @Test
    fun sameSeedReplaysIdenticallyAndDifferentSeedsDiffer() {
        val a = EngineFixtures.config(seed = 1)
        val b = EngineFixtures.config(seed = 2)

        fun run(config: dev.babysim.engine.model.BabyConfig) =
            engine.advance(config, engine.initialize(config, T0).state, T0 + 24.hours, predict = false)
        assertEquals(run(a), run(a))
        assertNotEquals(run(a).events.map { it.at }, run(b).events.map { it.at })
    }
}
