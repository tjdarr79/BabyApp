package dev.babysim.engine.host

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.EventType
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CLAUDE.md rule 6: per-baby notification identity, never shared. */
class NotificationIdsTest {
    @Test
    fun idsAreUniqueAcrossSlotsAndKinds() {
        val ids =
            (0..Slot.MAX).flatMap { slot ->
                NotificationKind.entries.map { NotificationIds.identity(BabyId("b$slot"), Slot(slot), it).id }
            }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun tagAndGroupNameTheBaby() {
        val identity = NotificationIds.identity(BabyId("abc"), Slot(2), NotificationKind.CARE_ALERT)
        assertEquals("baby:abc", identity.tag)
        assertEquals("baby:abc", identity.group)
        assertEquals("care_alert_v1", identity.channelId)
        assertEquals(1000 + 2 * 16, identity.id)
    }

    @Test
    fun aCareAlertIsPostedThenWithdrawnWhenTheBabyCalms() {
        val baby = BabyId("a")

        fun event(type: EventType) = BabyEvent(baby, T0, type, careRequestId = 1)

        val opened = NotificationIds.plan(baby, Slot(0), listOf(event(EventType.CARE_REQUEST_OPENED)))
        assertEquals(listOf(NotificationKind.CARE_ALERT), opened.posts.map { it.kind })

        val calmed =
            NotificationIds.plan(
                baby,
                Slot(0),
                listOf(event(EventType.CARE_REQUEST_OPENED), event(EventType.CARE_REQUEST_RESOLVED)),
            )
        assertTrue(calmed.posts.isEmpty())
        assertEquals(1, calmed.cancels.size)

        val reopened =
            NotificationIds.plan(
                baby,
                Slot(0),
                listOf(event(EventType.CARE_REQUEST_EXPIRED), event(EventType.CARE_REQUEST_OPENED)),
            )
        assertEquals(1, reopened.posts.size)
        assertTrue(reopened.cancels.isEmpty(), "a later post replaces the cancel")

        val paused = NotificationIds.plan(baby, Slot(0), listOf(event(EventType.AUTO_PAUSED)))
        assertEquals(listOf(NotificationKind.SIM_SERVICE), paused.posts.map { it.kind })
    }
}
