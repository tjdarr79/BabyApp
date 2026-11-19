package dev.babysim.engine

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.model.Slot
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.params.ParamLoader
import dev.babysim.engine.params.ParamSet

/** Shared fixtures for engine tests. */
object EngineFixtures {
    val params: ParamSet by lazy { ParamLoader.bundled() }

    /** Deliberately not on the 30 s tick grid. */
    val T0 = SimInstant(1_790_000_012_345)

    fun engine(): BehaviorEngine = BehaviorEngine(params)

    fun config(
        band: AgeBandId = AgeBandId.M2_3,
        temperament: TemperamentId = TemperamentId.AVERAGE,
        seed: Long = 42,
        id: String = "baby-a",
        slot: Int = 0,
    ): BabyConfig = BabyConfig(BabyId(id), Slot(slot), band, temperament, seed)

    /** Every age band × temperament combination. */
    fun allProfiles(): List<Pair<AgeBandId, TemperamentId>> =
        AgeBandId.entries.flatMap { band -> TemperamentId.entries.map { band to it } }
}
