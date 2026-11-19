package dev.babysim.engine.state

import dev.babysim.engine.EngineInfo
import dev.babysim.engine.model.ActionOutcome
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
    /** Per-cycle sampled thresholds (docs/04 §7.1). */
    val thresholds: Thresholds = Thresholds(),
    /** Remaining hazard budgets of armed stochastic processes (docs/04 §7.1). */
    val budgets: Map<ProcessId, Double> = emptyMap(),
    val sleep: SleepCycle? = null,
    /** When a drowsy baby falls asleep if nothing disturbs it. */
    val fallAsleepAt: SimInstant? = null,
    val fussSpellEndsAt: SimInstant? = null,
    val wantsContactUntil: SimInstant? = null,
    /** Start of the current run of distress at or above the crying level (care-request sustain). */
    val cryingLevelSince: SimInstant? = null,
    /** Start of the current crying segment (unattended cap, docs/01 B1). */
    val cryingSince: SimInstant? = null,
    val lastInteractionAt: SimInstant? = null,
    val unanswered: Unanswered = Unanswered(),
    val nextCareRequestId: Long = 1,
    val pause: PauseInfo? = null,
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

/** A continuous caregiver action in progress (feeding, holding, rocking, playing). */
@Serializable
data class OngoingAction(
    val type: CareActionType,
    val startedAt: SimInstant,
    /** Feeding only: hunger removed per minute, set so a full feed lasts the sampled feed length. */
    val intakePerMinute: Double = 0.0,
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

/** Thresholds sampled once per cycle within the band's researched ranges (docs/04 §5). */
@Serializable
data class Thresholds(
    /** Hunger level at which hunger becomes a cause of distress. 1.0 = the band's typical feed interval. */
    val hungerCueAt: Double = 1.0,
    /** Sleep pressure at which the baby becomes ready to sleep. 1.0 = the band's typical wake window. */
    val sleepReadyAt: Double = 1.0,
    /** Minutes of crying-level distress before a care request opens. */
    val careSustainMinutes: Double = 3.0,
)

/** Stochastic processes driven by hazard budgets. */
@Serializable
enum class ProcessId {
    VOID,
    STOOL,
    UNEXPLAINED_FUSS,
    VOCAL,

    /** A fussy spell or need for comfort settles while the baby is held or rocked. */
    SETTLE,

    /** A burp comes up on its own while the baby is held upright. */
    SPONTANEOUS_BURP,
}

@Serializable
data class SleepCycle(
    val cycleStartedAt: SimInstant,
    val cycleEndsAt: SimInstant,
)

/** Tracks care requests that went unanswered since the caregiver last responded (docs/01 B1). */
@Serializable
data class Unanswered(
    val expiredCount: Int = 0,
    val firstUnansweredAt: SimInstant? = null,
)

@Serializable
enum class PauseReason {
    USER,
    AUTO_UNANSWERED_REQUESTS,
    AUTO_NO_RESPONSE,
}

@Serializable
data class PauseInfo(
    val reason: PauseReason,
    val since: SimInstant,
)

/** Entry in the recent-caregiver-actions ring buffer (brief §7). */
@Serializable
data class RecordedAction(
    val action: CareActionType,
    val at: SimInstant,
    val outcome: ActionOutcome? = null,
)
