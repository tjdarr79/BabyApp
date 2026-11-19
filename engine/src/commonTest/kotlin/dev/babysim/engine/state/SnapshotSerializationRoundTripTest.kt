package dev.babysim.engine.state

import dev.babysim.engine.EngineInfo
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.model.Slot
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.random.RngBank
import dev.babysim.engine.random.RngStreamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SnapshotSerializationRoundTripTest {
    private val t0 = SimInstant(1_790_000_000_000)

    private val config =
        BabyConfig(
            id = BabyId("6f1d2c3a-0000-4000-8000-000000000001"),
            slot = Slot(0),
            ageBand = AgeBandId.M2_3,
            temperament = TemperamentId.AVERAGE,
            seed = 0x5EED,
        )

    private fun populatedState(): BabyState {
        val bank = RngBank(BabyState.fresh(config, paramSetVersion = 1, at = t0).rng)
        repeat(37) { bank.stream(RngStreamId.FEEDING).nextLong() }
        repeat(5) { bank.stream(RngStreamId.SOOTHE).nextLong() }
        return BabyState(
            babyId = config.id,
            meta = StateMeta(EngineInfo.ENGINE_SCHEMA_VERSION, 1, t0 + 95.minutes),
            drives =
                Drives(
                    hunger = 0.47,
                    sleepPressure = 0.81,
                    gas = 0.3,
                    diaperWetness = 0.4,
                    diaperSoiled = true,
                    diaperDiscomfort = 0.12,
                    stimulation = 0.2,
                    distress = 0.66,
                    mood = -0.25,
                ),
            activity = Activity.CRYING,
            ongoingAction = OngoingAction(CareActionType.ROCK, t0 + 91.minutes),
            careRequest =
                CareRequest(
                    id = 3,
                    openedAt = t0 + 87.minutes,
                    escalationLevel = 1,
                    lastAlertAt =
                        t0 + 87.minutes,
                ),
            timestamps = CareTimestamps(lastFeedEndAt = t0, lastWakeAt = t0 + 10.minutes),
            recentActions =
                listOf(
                    RecordedAction(CareActionType.FEED, t0 + 88.minutes),
                    RecordedAction(CareActionType.CHECK_DIAPER, t0 + 90.minutes),
                ),
            rng = bank.snapshot(),
        )
    }

    @Test
    fun populatedStateRoundTripsExactly() {
        val state = populatedState()
        assertEquals(state, SnapshotCodec.decode(SnapshotCodec.encode(state)))
    }

    @Test
    fun freshStateRoundTripsExactly() {
        val state = BabyState.fresh(config, paramSetVersion = 1, at = t0)
        assertEquals(state, SnapshotCodec.decode(SnapshotCodec.encode(state)))
    }

    @Test
    fun engineStatesRoundTripAndContinueIdentically() {
        val engine =
            dev.babysim.engine.EngineFixtures
                .engine()
        for (hours in listOf(1, 5, 9, 17)) {
            val mid = engine.advance(config, engine.initialize(config, t0).state, t0 + (hours * 60).minutes).state
            val restored = SnapshotCodec.decode(SnapshotCodec.encode(mid))
            assertEquals(mid, restored, "round trip at $hours h")
            assertEquals(
                engine.advance(config, mid, t0 + 30.minutes * 48),
                engine.advance(config, restored, t0 + 30.minutes * 48),
                "continues identically after a round trip at $hours h",
            )
        }
    }

    @Test
    fun rngContinuesIdenticallyAfterRoundTrip() {
        val state = populatedState()
        val restored = SnapshotCodec.decode(SnapshotCodec.encode(state))
        val direct = RngBank(state.rng)
        val viaSnapshot = RngBank(restored.rng)
        for (id in RngStreamId.entries) {
            assertEquals(
                List(20) { direct.stream(id).nextLong() },
                List(20) { viaSnapshot.stream(id).nextLong() },
                "stream $id",
            )
        }
    }

    @Test
    fun encodingIsStable() {
        val state = populatedState()
        assertEquals(
            SnapshotCodec.encode(state),
            SnapshotCodec.encode(SnapshotCodec.decode(SnapshotCodec.encode(state))),
        )
    }

    @Test
    fun newerSchemaVersionIsRejected() {
        val future =
            populatedState().let {
                it.copy(
                    meta =
                        it.meta.copy(
                            engineSchemaVersion =
                                EngineInfo.ENGINE_SCHEMA_VERSION + 1,
                        ),
                )
            }
        val e = assertFailsWith<SnapshotDecodeException> { SnapshotCodec.decode(SnapshotCodec.encode(future)) }
        assertTrue("newer than this engine" in e.message.orEmpty())
    }

    @Test
    fun garbageIsRejectedWithDecodeException() {
        assertFailsWith<SnapshotDecodeException> { SnapshotCodec.decode("not json") }
        assertFailsWith<SnapshotDecodeException> { SnapshotCodec.decode("""{"babyId":"x"}""") }
        assertFailsWith<SnapshotDecodeException> {
            SnapshotCodec.decode(
                SnapshotCodec.encode(populatedState()).replace("\"hunger\":0.47", "\"hunger\":\"high\""),
            )
        }
    }
}
