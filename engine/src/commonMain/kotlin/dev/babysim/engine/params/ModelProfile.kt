package dev.babysim.engine.params

import kotlinx.serialization.Serializable

/**
 * Band-independent engine constants (docs/04 §3–§8): activity thresholds, cause weights, diaper rates,
 * circadian effects and the unattended-baby rule. Kept in `engine/params/model.json` so every tuning knob is
 * data with provenance, like the age and temperament values.
 */
@Serializable
data class ModelProfile(
    val distressRisePerMinute: Sourced<Double>,
    val distressFallPerMinute: Sourced<Double>,
    val fussThreshold: Sourced<Double>,
    val cryThreshold: Sourced<Double>,
    val intenseCryThreshold: Sourced<Double>,
    val calmThreshold: Sourced<Double>,
    val intensityBiasStep: Sourced<Double>,
    val escalateAfterMinutes: Sourced<Double>,
    val maxEscalationLevel: Sourced<Int>,
    val hungerCauseWeight: Sourced<Double>,
    val hungerOverdueGain: Sourced<Double>,
    val tiredCauseWeight: Sourced<Double>,
    val overtiredMargin: Sourced<Double>,
    val overtiredCauseWeight: Sourced<Double>,
    val diaperCauseWeight: Sourced<Double>,
    val diaperCueAt: Sourced<Double>,
    val diaperDiscomfortPerMinuteWet: Sourced<Double>,
    val diaperDiscomfortPerMinuteSoiled: Sourced<Double>,
    val wetnessPerVoid: Sourced<Double>,
    val postFeedVoidMultiplier: Sourced<Double>,
    val postFeedVoidWindowMinutes: Sourced<Double>,
    val unexplainedFussCauseWeight: Sourced<Double>,
    val unexplainedFussPerAwakeHour: Sourced<Double>,
    val eveningFussMultiplier: Sourced<Double>,
    val eveningStartLocalMinutes: Sourced<Int>,
    val eveningEndLocalMinutes: Sourced<Int>,
    val fussSpellMinutes: Sourced<DoubleRange>,
    val boredCauseWeight: Sourced<Double>,
    val wantsContactCauseWeight: Sourced<Double>,
    val causeStackBonus: Sourced<Double>,
    val drowsyToSleepMinutes: Sourced<DoubleRange>,
    val restedSleepPressure: Sourced<Double>,
    val nightHungerRateScale: Sourced<Double>,
    val nightSleepPressureDecayScale: Sourced<Double>,
    val nightSleepPressureRiseBoost: Sourced<Double>,
    val sleepNoiseProbPerArousal: Sourced<Double>,
    val wakeTransitionDistress: Sourced<Double>,
    val moodRatePerMinute: Sourced<Double>,
    val moodVocalGain: Sourced<Double>,
    val autoPauseUnansweredRequests: Sourced<Int>,
    val autoPauseHoursWithoutResponse: Sourced<Double>,
)

/** On-disk shape of `engine/params/model.json`. */
@Serializable
data class ModelProfileFile(
    val paramSetVersion: Int,
    val values: ModelProfile,
)
