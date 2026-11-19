package dev.babysim.harness

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.params.AgeProfile
import dev.babysim.engine.params.DoubleRange

/** Which seed statistic a target is checked against. */
enum class Stat { MEDIAN, P90 }

/**
 * A target for one metric in one band. [source] says where the range comes from; [levers] names the
 * parameters that move the metric, so an out-of-range result points the reviewer at what to change.
 */
data class Target(
    val metric: Metric,
    val min: Double,
    val max: Double,
    val stat: Stat,
    val source: String,
    val levers: List<String>,
) {
    fun accepts(value: Double): Boolean = !value.isNaN() && value in min..max
}

/**
 * Targets per band: real-infant ranges from the age profile (docs/06, as engine-ready ranges) and the
 * harness calibration gates in the docs/06 table "Harness calibration targets" (design assumptions).
 */
object Targets {
    private const val SOURCE_CALIBRATION = "docs/06 calibration targets (DESIGN_ASSUMPTION)"

    private data class Calibration(
        val careRequests: ClosedFloatingPointRange<Double>,
        val dayGapHours: Double,
        val quietGapMinutes: Double,
        val nightRequests: ClosedFloatingPointRange<Double>,
        val similarityBelow: Double,
    )

    /** Mirrors the docs/06 "Harness calibration targets" table; keep the two in step. */
    @Suppress("MagicNumber")
    private val calibration =
        mapOf(
            AgeBandId.NEWBORN to Calibration(10.0..16.0, 3.0, 20.0, 2.0..4.0, 0.6),
            AgeBandId.M2_3 to Calibration(8.0..13.0, 3.5, 15.0, 1.0..3.0, 0.6),
            AgeBandId.M4_6 to Calibration(6.0..10.0, 4.0, 15.0, 0.0..2.0, 0.6),
            AgeBandId.M7_9 to Calibration(5.0..9.0, 4.5, 12.0, 0.0..2.0, 0.65),
            AgeBandId.M10_12 to Calibration(4.0..8.0, 4.5, 12.0, 0.0..1.0, 0.65),
        )

    fun forBand(
        band: AgeBandId,
        age: AgeProfile,
    ): List<Target> {
        val c = calibration.getValue(band)
        val fromProfile =
            listOfNotNull(
                profile(Metric.TOTAL_SLEEP_H, age.totalSleepHours24.value, "totalSleepHours24", SLEEP_LEVERS),
                age.napsPerDay.value?.let { profile(Metric.NAPS_PER_DAY, it, "napsPerDay", SLEEP_LEVERS) },
                age.dayNapMinutes.value?.let { profile(Metric.DAY_NAP_MIN, it, "dayNapMinutes", NAP_LEVERS) },
                profile(Metric.NIGHT_WAKINGS, age.nightWakings.value, "nightWakings", NIGHT_LEVERS),
                profile(Metric.FUSS_CRY_MIN, age.fussCryMinutesPer24h.value, "fussCryMinutesPer24h", CRY_LEVERS),
                profile(Metric.FEEDS_PER_DAY, age.feedsPer24h.value, "feedsPer24h", FEED_LEVERS),
                profile(Metric.FEED_INTERVAL_MIN, age.feedIntervalMinutes.value, "feedIntervalMinutes", FEED_LEVERS),
                profile(Metric.WET_DIAPERS_PER_DAY, age.wetDiapersPerDay.value, "wetDiapersPerDay", DIAPER_LEVERS),
            )
        val calibrationTargets =
            listOf(
                Target(
                    Metric.CARE_REQUESTS_PER_DAY,
                    c.careRequests.start,
                    c.careRequests.endInclusive,
                    Stat.MEDIAN,
                    SOURCE_CALIBRATION,
                    CARE_LEVERS,
                ),
                Target(Metric.LONGEST_DAY_CARE_GAP_H, 0.0, c.dayGapHours, Stat.P90, SOURCE_CALIBRATION, CARE_LEVERS),
                Target(
                    Metric.LONGEST_AWAKE_QUIET_GAP_MIN,
                    0.0,
                    c.quietGapMinutes,
                    Stat.P90,
                    SOURCE_CALIBRATION,
                    VOCAL_LEVERS,
                ),
                Target(
                    Metric.NIGHT_CARE_REQUESTS,
                    c.nightRequests.start,
                    c.nightRequests.endInclusive,
                    Stat.MEDIAN,
                    SOURCE_CALIBRATION,
                    NIGHT_LEVERS,
                ),
                Target(
                    Metric.DAY_TO_DAY_SIMILARITY,
                    0.0,
                    c.similarityBelow,
                    Stat.MEDIAN,
                    SOURCE_CALIBRATION,
                    listOf("rhythmicitySigmaScale"),
                ),
                Target(
                    Metric.AUTO_PAUSES,
                    0.0,
                    0.0,
                    Stat.P90,
                    "docs/01 B1: an attentive caregiver never triggers auto-pause",
                    CARE_LEVERS,
                ),
            )
        return fromProfile + calibrationTargets
    }

    private fun profile(
        metric: Metric,
        range: DoubleRange,
        field: String,
        levers: List<String>,
    ) = Target(metric, range.min, range.max, Stat.MEDIAN, "age profile $field (docs/06)", levers)

    private val SLEEP_LEVERS =
        listOf(
            "wakeWindowMinutes",
            "totalSleepHours24",
            "sleepCycleMinutes",
            "circadianStrength",
            "model.restedSleepPressure",
        )
    private val NAP_LEVERS = listOf("dayNapMinutes", "catnapProb", "feedIntervalMinutes")
    private val NIGHT_LEVERS =
        listOf("feedIntervalMinutes", "selfSettleProbNight", "circadianStrength", "model.nightHungerRateScale")
    private val CRY_LEVERS =
        listOf(
            "model.unexplainedFussPerAwakeHour",
            "model.distressRisePerMinute",
            "model.distressFallPerMinute",
            "model.cryThreshold",
        )
    private val FEED_LEVERS = listOf("feedIntervalMinutes", "feedAcceptMin", "feedDurationMinutes")
    private val DIAPER_LEVERS = listOf("wetDiapersPerDay", "model.postFeedVoidMultiplier")
    private val CARE_LEVERS =
        listOf("careSustainMinutes", "model.cryThreshold", "feedIntervalMinutes", "wakeWindowMinutes")
    private val VOCAL_LEVERS = listOf("vocalRatePerAwakeHour", "model.moodVocalGain")
}
