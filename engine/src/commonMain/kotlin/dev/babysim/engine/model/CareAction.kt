package dev.babysim.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A caregiver command (docs/04 §6.4). Serializable so the host can keep an action log for exact replay
 * (docs/05 `action_log`).
 */
@Serializable
sealed interface CareAction {
    /** Begin a continuous action: feeding, holding, rocking or playing. Replaces any action in progress. */
    @Serializable
    @SerialName("start")
    data class Start(
        val type: CareActionType,
    ) : CareAction {
        init {
            require(type in CONTINUOUS) { "$type is not a continuous action" }
        }
    }

    /** End the continuous action in progress. */
    @Serializable
    @SerialName("stop")
    data object Stop : CareAction

    /** A one-off action: burp, check or change the diaper, put down to sleep, wake (or the reserved pacifier). */
    @Serializable
    @SerialName("do")
    data class Do(
        val type: CareActionType,
    ) : CareAction {
        init {
            require(type !in CONTINUOUS) { "$type is a continuous action; use Start" }
        }
    }

    companion object {
        val CONTINUOUS: Set<CareActionType> =
            setOf(CareActionType.FEED, CareActionType.HOLD, CareActionType.ROCK, CareActionType.PLAY)
    }
}
