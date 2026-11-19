package dev.babysim.engine.state

import dev.babysim.engine.EngineInfo
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.random.RngBankState
import kotlinx.serialization.Serializable

/**
 * Complete persisted state of one baby (docs/04 §3). Fully serializable and versioned. Nothing outside it is
 * needed to resume the simulation except the [BabyConfig] and the parameter set.
 *
 * Time-since counters (time since feeding, etc.) are derived from [timestamps] and never stored.
 * Dynamics are added in M2.3. New fields must have defaults so older snapshots still decode (docs/05 §4).
 */
@Serializable
data class BabyState(
    val babyId: BabyId,
    val meta: StateMeta,
    val drives: Drives = Drives(),
    val activity: Activity = Activity.AWAKE_CALM,
    val ongoingAction: OngoingAction? = null,
    val careRequest: CareRequest? = null,
    val timestamps: CareTimestamps = CareTimestamps(),
    val recentActions: List<RecordedAction> = emptyList(),
    val rng: RngBankState,
) {
    companion object {
        /** Fresh state for a newly configured baby. M2.3 replaces default drives with sampled starting values. */
        fun fresh(
            config: BabyConfig,
            paramSetVersion: Int,
            at: SimInstant,
        ): BabyState =
            BabyState(
                babyId = config.id,
                meta = StateMeta(EngineInfo.ENGINE_SCHEMA_VERSION, paramSetVersion, at),
                rng = RngBankState(seed = config.seed),
            )
    }
}

@Serializable
data class StateMeta(
    val engineSchemaVersion: Int,
    val paramSetVersion: Int,
    /** Simulation time this state is valid at. `advance` never moves it backward (docs/04 §8). */
    val lastAdvancedTo: SimInstant,
)

/** Continuous drives (docs/04 §3.1). Normalized 0.0–1.0 unless noted. */
@Serializable
data class Drives(
    val hunger: Double = 0.0,
    val sleepPressure: Double = 0.0,
    val gas: Double = 0.0,
    val diaperWetness: Double = 0.0,
    val diaperSoiled: Boolean = false,
    val diaperDiscomfort: Double = 0.0,
    val stimulation: Double = 0.0,
    val distress: Double = 0.0,
    /** −1 grumpy … +1 sunny. */
    val mood: Double = 0.0,
)

/** A continuous caregiver action in progress (feeding, rocking, …). */
@Serializable
data class OngoingAction(
    val type: CareActionType,
    val startedAt: SimInstant,
)

/** An open care-required episode (docs/04 §4). */
@Serializable
data class CareRequest(
    val id: Long,
    val openedAt: SimInstant,
    val escalationLevel: Int,
    val lastAlertAt: SimInstant,
)

@Serializable
data class CareTimestamps(
    val lastFeedEndAt: SimInstant? = null,
    val lastDiaperChangeAt: SimInstant? = null,
    val lastSleepStartAt: SimInstant? = null,
    val lastWakeAt: SimInstant? = null,
    val lastSoothedAt: SimInstant? = null,
    val lastBurpAt: SimInstant? = null,
)

/** Entry in the recent-caregiver-actions ring buffer (brief §7). Outcome is added in M2.4. */
@Serializable
data class RecordedAction(
    val action: CareActionType,
    val at: SimInstant,
)
