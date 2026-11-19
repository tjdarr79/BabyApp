package dev.babysim.engine

import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.model.VocalType
import dev.babysim.engine.state.PauseReason
import kotlinx.serialization.Serializable

/** Event families (docs/04 §4). Audible activity is kept separate from care-required events. */
@Serializable
enum class EventFamily {
    AUDIBLE,
    CARE,
    TRANSITION,

    /** Hidden from the user until discovered (e.g. a wet diaper before it is checked). */
    INTERNAL,
    LIFECYCLE,

    /** The result of a caregiver action. */
    ACTION,
}

@Serializable
enum class EventType(
    val family: EventFamily,
    /** True when the host should wake the device for it (OS alarm + notification, docs/03 §3). */
    val alerts: Boolean = false,
) {
    VOCALIZATION(EventFamily.AUDIBLE),
    SLEEP_NOISE(EventFamily.AUDIBLE),
    BRIEF_FUSS(EventFamily.AUDIBLE),
    BURPED(EventFamily.AUDIBLE),
    CARE_REQUEST_OPENED(EventFamily.CARE, alerts = true),
    CARE_REQUEST_ESCALATED(EventFamily.CARE, alerts = true),
    CARE_REQUEST_RESOLVED(EventFamily.CARE),
    CARE_REQUEST_EXPIRED(EventFamily.CARE),
    ACTIVITY_CHANGED(EventFamily.TRANSITION),
    FELL_ASLEEP(EventFamily.TRANSITION),
    WOKE(EventFamily.TRANSITION),

    /** A feeding baby is full and turns away; the feed ends. */
    FEED_FINISHED_CUE(EventFamily.TRANSITION),

    /** A fussy spell or need for comfort settled under holding or rocking. */
    SETTLED(EventFamily.TRANSITION),
    ACTION_OUTCOME(EventFamily.ACTION),
    DIAPER_WET(EventFamily.INTERNAL),
    DIAPER_SOILED(EventFamily.INTERNAL),
    PAUSED(EventFamily.LIFECYCLE),
    RESUMED(EventFamily.LIFECYCLE),
    AUTO_PAUSED(EventFamily.LIFECYCLE, alerts = true),
}

/** Something that happened to one baby. Every event carries its [babyId] (CLAUDE.md rule 1). */
@Serializable
data class BabyEvent(
    val babyId: BabyId,
    val at: SimInstant,
    val type: EventType,
    val activity: Activity? = null,
    val vocal: VocalType? = null,
    val careRequestId: Long? = null,
    val escalationLevel: Int? = null,
    val pauseReason: PauseReason? = null,
    /** The action this event answers: the action itself for ACTION_OUTCOME, the soothing action for RESOLVED. */
    val action: CareActionType? = null,
    val outcome: ActionOutcome? = null,
)
