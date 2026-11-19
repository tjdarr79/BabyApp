package dev.babysim.engine.sim

import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.VocalType
import dev.babysim.engine.params.AgeProfile
import dev.babysim.engine.params.DoubleRange
import dev.babysim.engine.params.ModelProfile
import dev.babysim.engine.params.ParamSet
import dev.babysim.engine.params.TemperamentProfile
import dev.babysim.engine.random.RandomSource
import dev.babysim.engine.random.nextTruncatedNormal

/** Engine resolution (docs/04 §7.3): state changes only on ticks aligned to multiples of this in absolute time. */
internal const val TICK_MILLIS: Long = 30_000L
internal const val TICK_MINUTES: Double = TICK_MILLIS / 60_000.0
internal const val MILLIS_PER_MINUTE: Long = 60_000L
internal const val MINUTES_PER_HOUR: Double = 60.0
internal const val MINUTES_PER_DAY: Double = 1440.0
internal const val HOURS_PER_DAY: Double = 24.0

/** A range's spread is treated as ±2 standard deviations around its typical value. */
private const val RANGE_SD_DIVISOR = 4.0

private val CONTENT_VOCALS =
    setOf(
        VocalType.COO,
        VocalType.SQUEAL,
        VocalType.GIGGLE,
        VocalType.BABBLE,
        VocalType.BABBLE_REDUPLICATED,
        VocalType.SIGH,
        VocalType.GRUNT,
    )

/** Samples within a researched range, truncated to it (docs/04 §7.1). */
internal fun RandomSource.sample(
    range: DoubleRange,
    sdScale: Double = 1.0,
): Double =
    if (range.min == range.max) {
        range.min
    } else {
        nextTruncatedNormal(range.typical, (range.max - range.min) / RANGE_SD_DIVISOR * sdScale, range.min, range.max)
    }

internal fun minutesToMillis(minutes: Double): Long = (minutes * MILLIS_PER_MINUTE).toLong()

/** Per-baby rates derived once per engine call from the baby's age band and temperament. */
internal class Rates(
    config: BabyConfig,
    params: ParamSet,
) {
    val age: AgeProfile = params.age(config.ageBand)
    val temperament: TemperamentProfile = params.temperament(config.temperament)
    val model: ModelProfile = params.model

    /** Hunger reaches 1.0 after the band's typical feed interval. */
    val hungerPerMinute: Double = 1.0 / age.feedIntervalMinutes.value.typical

    /** Sleep pressure reaches 1.0 after the band's typical wake window. */
    val sleepRisePerMinute: Double = 1.0 / age.wakeWindowMinutes.value.typical

    /**
     * Sleep pressure clears over an average sleep bout sized so that the awake/asleep split matches the band's
     * typical total sleep: bout = wakeWindow × sleepFraction / awakeFraction.
     */
    val sleepFallPerMinute: Double =
        run {
            val sleepFraction = age.totalSleepHours24.value.typical / HOURS_PER_DAY
            val bout = age.wakeWindowMinutes.value.typical * sleepFraction / (1.0 - sleepFraction)
            1.0 / bout
        }

    val circadian: Double = age.circadianStrength.value.typical
    val wetPerMinute: Double = age.wetDiapersPerDay.value.typical / MINUTES_PER_DAY
    val soiledPerMinute: Double = age.soiledDiapersPerDay.value.typical / MINUTES_PER_DAY
    val vocalPerMinute: Double =
        age.vocalRatePerAwakeHour.value.typical / MINUTES_PER_HOUR * temperament.contentVocalRateScale.value
    val unexplainedFussPerMinute: Double =
        model.unexplainedFussPerAwakeHour.value / MINUTES_PER_HOUR * temperament.unexplainedFussHazardScale.value
    val boredAfterMinutes: Double? = age.boredAfterMinutes.value?.typical
    val selfSettleProbNight: Double =
        (age.selfSettleProbNight.value.typical * temperament.selfSettleScale.value).coerceIn(0.0, 1.0)
    val contentVocals: List<VocalType> = age.vocalRepertoire.value.filter { it in CONTENT_VOCALS }

    private val intensityShift: Double = temperament.cryIntensityBias.value * model.intensityBiasStep.value

    /** Activity thresholds, shifted by temperament (a louder temperament reaches each level sooner). */
    val fussLevel: Double = model.fussThreshold.value - intensityShift
    val cryLevel: Double = model.cryThreshold.value - intensityShift
    val intenseLevel: Double = model.intenseCryThreshold.value - intensityShift

    val crySegmentMaxMillis: Long = minutesToMillis(age.crySegmentMaxMinutes.value)
    val escalateAfterMillis: Long = minutesToMillis(model.escalateAfterMinutes.value)
    val postFeedVoidWindowMillis: Long = minutesToMillis(model.postFeedVoidWindowMinutes.value)
    val autoPauseNoResponseMillis: Long = minutesToMillis(model.autoPauseHoursWithoutResponse.value * MINUTES_PER_HOUR)
}
