package dev.babysim.engine.host

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventType
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.Slot
import kotlinx.serialization.Serializable

/** Notification kinds and their channels (docs/03 §5). Codes are part of the derived notification id. */
@Serializable
enum class NotificationKind(
    val code: Int,
    val channelId: String,
) {
    CARE_ALERT(0, "care_alert_v1"),
    STATUS(1, "baby_status_v1"),
    PLAYBACK(2, "playback_v1"),
    SIM_SERVICE(3, "sim_service_v1"),
}

/** Per-baby notification identity (CLAUDE.md rule 6): never shared between babies. */
data class NotificationIdentity(
    val id: Int,
    val tag: String,
    val group: String,
    val channelId: String,
)

/** Post or replace a notification. The app composes the text, naming the baby (docs/03 §5). */
data class NotificationPost(
    val identity: NotificationIdentity,
    val babyId: BabyId,
    val kind: NotificationKind,
    val event: BabyEvent,
)

/** Remove a notification that no longer applies (the baby calmed down). */
data class NotificationCancel(
    val identity: NotificationIdentity,
    val babyId: BabyId,
)

data class NotificationPlan(
    val posts: List<NotificationPost>,
    val cancels: List<NotificationCancel>,
)

object NotificationIds {
    /** `1000 + slot × 16 + kind`, tag and group `baby:<id>`. */
    fun identity(
        babyId: BabyId,
        slot: Slot,
        kind: NotificationKind,
    ): NotificationIdentity {
        val key = "baby:${babyId.value}"
        return NotificationIdentity(ID_BASE + slot.value * ID_KINDS_PER_SLOT + kind.code, key, key, kind.channelId)
    }

    /**
     * Maps one baby's events to notification changes: a care alert is posted when a request opens or escalates
     * and removed when it resolves or expires; an auto-pause posts a notice. Pure.
     */
    fun plan(
        babyId: BabyId,
        slot: Slot,
        events: List<BabyEvent>,
    ): NotificationPlan {
        val posts = mutableListOf<NotificationPost>()
        val cancels = mutableListOf<NotificationCancel>()
        val care = identity(babyId, slot, NotificationKind.CARE_ALERT)
        for (event in events) {
            when (event.type) {
                EventType.CARE_REQUEST_OPENED, EventType.CARE_REQUEST_ESCALATED ->
                    posts += NotificationPost(care, babyId, NotificationKind.CARE_ALERT, event)
                EventType.CARE_REQUEST_RESOLVED, EventType.CARE_REQUEST_EXPIRED -> {
                    posts.removeAll { it.identity == care }
                    cancels += NotificationCancel(care, babyId)
                }
                EventType.AUTO_PAUSED ->
                    posts +=
                        NotificationPost(
                            identity(babyId, slot, NotificationKind.SIM_SERVICE),
                            babyId,
                            NotificationKind.SIM_SERVICE,
                            event,
                        )
                else -> Unit
            }
        }
        // Only the latest post per identity matters; a later post replaces an earlier one.
        val latest = posts.associateBy { it.identity }.values.toList()
        return NotificationPlan(latest, cancels.filter { cancel -> latest.none { it.identity == cancel.identity } })
    }
}
