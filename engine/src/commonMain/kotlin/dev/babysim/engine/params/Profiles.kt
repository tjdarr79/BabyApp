package dev.babysim.engine.params

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.model.VocalType
import kotlinx.serialization.Serializable

/**
 * Age-band parameters (docs/04 §6.2, values from docs/06). Units are in the field names.
 * Every field is [Sourced]; there are no defaults, so a missing field fails loading.
 */
@Serializable
data class AgeProfile(
    val feedIntervalMinutes: Sourced<DoubleRange>,
    val feedDurationMinutes: Sourced<DoubleRange>,
    val feedsPer24h: Sourced<DoubleRange>,
    /** Hunger level (0–1) below which a feed is refused. */
    val feedAcceptMin: Sourced<Double>,
    val totalSleepHours24: Sourced<DoubleRange>,
    /** Null for newborns: no distinct naps; sleep is spread across day and night. */
    val napsPerDay: Sourced<DoubleRange?>,
    val wakeWindowMinutes: Sourced<DoubleRange>,
    val sleepCycleMinutes: Sourced<DoubleRange>,
    val nightWakings: Sourced<DoubleRange>,
    /** Engine target, not the raw norm (owner decision: lower half of norms). */
    val fussCryMinutesPer24h: Sourced<DoubleRange>,
    val eveningFussClustering: Sourced<Boolean>,
    /** 0 = no day/night difference, 1 = strong circadian rhythm. */
    val circadianStrength: Sourced<DoubleRange>,
    val wetDiapersPerDay: Sourced<DoubleRange>,
    val soiledDiapersPerDay: Sourced<DoubleRange>,
    val vocalRepertoire: Sourced<List<VocalType>>,
    val vocalRatePerAwakeHour: Sourced<DoubleRange>,
    /** Null below ~4 months: boredom is not modeled for young infants. */
    val boredAfterMinutes: Sourced<DoubleRange?>,
    /** Distress must be sustained this long before a care request opens (docs/04 §4). */
    val careSustainMinutes: Sourced<DoubleRange>,
    /** Unattended crying segment cap before exhausted sleep (docs/01 B1). */
    val crySegmentMaxMinutes: Sourced<Double>,
    /** Probability that a night arousal resolves without care. */
    val selfSettleProbNight: Sourced<DoubleRange>,
)

/** Temperament modifiers over the age profile (docs/04 §6.3). Multipliers unless noted. */
@Serializable
data class TemperamentProfile(
    val rhythmicitySigmaScale: Sourced<Double>,
    val sootheTimeScale: Sourced<Double>,
    val unexplainedFussHazardScale: Sourced<Double>,
    val careSustainScale: Sourced<Double>,
    val overstimThresholdScale: Sourced<Double>,
    val transitionDistress: Sourced<Double>,
    val selfSettleScale: Sourced<Double>,
    val contentVocalRateScale: Sourced<Double>,
    /** Additive shift in cry intensity level (−1, 0, +1). */
    val cryIntensityBias: Sourced<Int>,
)

/** On-disk shape of `engine/params/age/<BAND>.json`. */
@Serializable
data class AgeProfileFile(
    val paramSetVersion: Int,
    val ageBand: AgeBandId,
    val values: AgeProfile,
)

/** On-disk shape of `engine/params/temperament/<TEMPERAMENT>.json`. */
@Serializable
data class TemperamentProfileFile(
    val paramSetVersion: Int,
    val temperament: TemperamentId,
    val values: TemperamentProfile,
)

/** The complete, validated parameter set the engine runs on. */
data class ParamSet(
    val version: Int,
    val ages: Map<AgeBandId, AgeProfile>,
    val temperaments: Map<TemperamentId, TemperamentProfile>,
    val model: ModelProfile,
) {
    fun age(id: AgeBandId): AgeProfile = ages.getValue(id)

    fun temperament(id: TemperamentId): TemperamentProfile = temperaments.getValue(id)
}
