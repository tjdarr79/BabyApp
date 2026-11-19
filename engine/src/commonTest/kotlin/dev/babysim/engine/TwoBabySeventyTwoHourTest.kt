package dev.babysim.engine

import dev.babysim.engine.caregiver.AttentiveCaregiver
import dev.babysim.engine.caregiver.CaregiverDriver
import dev.babysim.engine.host.EntitlementProvider
import dev.babysim.engine.host.SimulationHost
import dev.babysim.engine.host.V1Entitlements
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.random.SplitMix64
import dev.babysim.engine.random.nextDouble
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.SnapshotCodec
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Kickoff rule 10 / docs/04 §11.7: two babies (different ages, temperaments and seeds) run in one host for 72
 * simulated hours, each looked after by an attentive caregiver. Proves the multi-baby architecture works before
 * any UI exists: independent schedules, no cross-contamination, action isolation, deterministic replay, and a
 * persistence round trip that restores both babies exactly.
 */
class TwoBabySeventyTwoHourTest {
    private val params = EngineFixtures.params
    private val a =
        EngineFixtures.config(
            AgeBandId.NEWBORN,
            TemperamentId.DEMANDING,
            seed = 0xA,
            id = "baby-a",
            slot = 0,
        )
    private val b = EngineFixtures.config(AgeBandId.M7_9, TemperamentId.EASYGOING, seed = 0xB, id = "baby-b", slot = 1)
    private val start = EngineFixtures.T0.epochMillis
    private val stepMillis = 2 * 60_000L
    private val hours72 = 72 * 3_600_000L
    private val driver = CaregiverDriver(AttentiveCaregiver, params)

    private object TwoBabies : EntitlementProvider {
        override val maxActiveBabies = 2
    }

    private data class Run(
        val events: Map<BabyId, List<BabyEvent>>,
        val finalStates: Map<BabyId, BabyState>,
    )

    /**
     * Drives a host every two minutes: catch every baby up, then let each baby's caregiver act. [extra] actions are
     * applied as well; at [persistAt] every baby is saved as a JSON snapshot and restored into a fresh host.
     */
    private fun run(
        babies: List<BabyConfig>,
        extra: Map<Long, Pair<BabyId, CareAction>> = emptyMap(),
        persistAt: Long? = null,
    ): Run {
        var host = SimulationHost(EngineFixtures.engine(), if (babies.size > 1) TwoBabies else V1Entitlements)
        babies.forEach { host.create(it, start) }
        val events = babies.associate { it.id to mutableListOf<BabyEvent>() }
        var t = start
        while (t < start + hours72) {
            t += stepMillis
            host.advanceAll(t, predict = false).results.forEach { (id, r) -> events.getValue(id) += r.events }
            for (baby in host.babies()) {
                val action = driver.decide(baby.config, baby.state) ?: continue
                events.getValue(baby.id) +=
                    host
                        .apply(baby.id, action, t, predict = false)
                        .results
                        .getValue(baby.id)
                        .events
            }
            extra[t]?.let { (id, action) ->
                events.getValue(id) +=
                    host
                        .apply(id, action, t, predict = false)
                        .results
                        .getValue(id)
                        .events
            }
            if (t == persistAt) host = persistAndRestore(host, babies.size)
        }
        return Run(events, host.babies().associate { it.id to it.state })
    }

    private fun persistAndRestore(
        host: SimulationHost,
        count: Int,
    ): SimulationHost {
        val saved = host.babies().map { Triple(it.config, SnapshotCodec.encode(it.state), it.time) }
        return SimulationHost(EngineFixtures.engine(), if (count > 1) TwoBabies else V1Entitlements).apply {
            saved.forEach { (config, json, time) -> restore(config, SnapshotCodec.decode(json), time) }
        }
    }

    private val together by lazy { run(listOf(a, b)) }

    @Test
    fun bothBabiesAreCaredForThroughoutAndNeitherAutoPauses() {
        for (id in listOf(a.id, b.id)) {
            val events = together.events.getValue(id)
            assertTrue(events.count { it.type == EventType.FELL_ASLEEP } >= 6, "$id sleeps repeatedly")
            assertTrue(events.count { it.outcome == dev.babysim.engine.model.ActionOutcome.FEEDING } >= 9, "$id is fed")
            assertTrue(events.none { it.type == EventType.AUTO_PAUSED }, "$id never auto-pauses with a caregiver")
            assertTrue(events.all { it.babyId == id }, "every event carries its own baby's id")
        }
    }

    @Test
    fun schedulesAreIndependentAndDoNotMirrorEachOther() {
        fun onsets(id: BabyId) =
            together.events
                .getValue(id)
                .filter { it.type == EventType.CARE_REQUEST_OPENED }
                .map { it.at.epochMillis }

        fun feeds(id: BabyId) =
            together.events
                .getValue(id)
                .filter {
                    it.outcome == dev.babysim.engine.model.ActionOutcome.FEEDING
                }.map { it.at.epochMillis }
        assertNotEquals(onsets(a.id), onsets(b.id))
        assertNotEquals(feeds(a.id), feeds(b.id))
        // Correlation of 10-minute activity series (care requests and feeds) stays low at small lags.
        for (lag in -1..1) {
            val r = correlation(binned(onsets(a.id) + feeds(a.id)), binned(onsets(b.id) + feeds(b.id)), lag)
            assertTrue(abs(r) < 0.5, "lag $lag: correlation $r")
        }
    }

    @Test
    fun eachBabyBehavesExactlyAsIfAlone() {
        for (config in listOf(a, b)) {
            val alone = run(listOf(config))
            assertEquals(alone.events.getValue(config.id), together.events.getValue(config.id), "${config.id} events")
            assertEquals(
                alone.finalStates.getValue(config.id),
                together.finalStates.getValue(config.id),
                "${config.id} state",
            )
        }
    }

    @Test
    fun extraActionsOnBabyADoNotAffectBabyB() {
        val gen = SplitMix64(50)
        val actions =
            CareAction.CONTINUOUS.map { CareAction.Start(it) } + CareAction.Stop +
                listOf(
                    CareActionType.BURP,
                    CareActionType.CHECK_DIAPER,
                    CareActionType.CHANGE_DIAPER,
                    CareActionType.PUT_DOWN_TO_SLEEP,
                    CareActionType.WAKE,
                ).map { CareAction.Do(it) }
        val extra =
            (1..50).associate {
                val step = 1 + (gen.nextDouble() * (hours72 / stepMillis - 1)).toLong()
                (start + step * stepMillis) to (a.id to actions[(gen.nextDouble() * actions.size).toInt()])
            }
        val disturbed = run(listOf(a, b), extra = extra)
        assertNotEquals(
            together.events.getValue(a.id),
            disturbed.events.getValue(a.id),
            "the extra actions changed baby A",
        )
        assertEquals(together.events.getValue(b.id), disturbed.events.getValue(b.id), "baby B's events")
        assertEquals(together.finalStates.getValue(b.id), disturbed.finalStates.getValue(b.id), "baby B's state")
    }

    @Test
    fun replayFromSeedsIsDeterministic() {
        val again = run(listOf(a, b))
        assertEquals(together.events, again.events)
        assertEquals(together.finalStates, again.finalStates)
    }

    @Test
    fun aPersistenceRoundTripAt36HoursRestoresBothBabiesExactly() {
        val restored = run(listOf(a, b), persistAt = start + 36 * 3_600_000L)
        assertEquals(together.events, restored.events)
        assertEquals(together.finalStates, restored.finalStates)
    }

    private fun binned(times: List<Long>): DoubleArray {
        val bins = DoubleArray((hours72 / BIN_MILLIS).toInt())
        times.forEach { bins[((it - start) / BIN_MILLIS).toInt().coerceIn(0, bins.size - 1)] = 1.0 }
        return bins
    }

    private fun correlation(
        x: DoubleArray,
        y: DoubleArray,
        lag: Int,
    ): Double {
        val pairs = x.indices.mapNotNull { i -> (i + lag).takeIf { it in y.indices }?.let { x[i] to y[it] } }
        val mx = pairs.map { it.first }.average()
        val my = pairs.map { it.second }.average()
        val cov = pairs.sumOf { (p, q) -> (p - mx) * (q - my) }
        val vx = pairs.sumOf { (p, _) -> (p - mx) * (p - mx) }
        val vy = pairs.sumOf { (_, q) -> (q - my) * (q - my) }
        return if (vx == 0.0 || vy == 0.0) 0.0 else cov / sqrt(vx * vy)
    }

    private companion object {
        const val BIN_MILLIS = 10 * 60_000L
    }
}
