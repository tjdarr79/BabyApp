package dev.babysim.engine.host

import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.Slot
import dev.babysim.engine.random.SplitMix64
import dev.babysim.engine.random.nextDouble
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CLAUDE.md rule 5: one planner merges every baby's next wake; alarm ids never collide. */
class SchedulerPlannerPropertyTest {
    private val window = SchedulerPlanner.DEFAULT_COALESCE_WINDOW_MILLIS

    @Test
    fun requestCodesAreUniqueForEverySlotAndKind() {
        val codes =
            (0..Slot.MAX).flatMap { slot ->
                AlarmKind.entries.map { SchedulerPlanner.requestCode(Slot(slot), it) }
            }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(1000, SchedulerPlanner.requestCode(Slot(0), AlarmKind.CARE_ALERT))
        assertEquals(1000 + 3 * 16 + 1, SchedulerPlanner.requestCode(Slot(3), AlarmKind.SAFETY_RECHECK))
    }

    @Test
    fun plansCoverEveryBabyOnceWithUniqueCodesAndBoundedDelay() {
        val gen = SplitMix64(55)
        repeat(2000) { case ->
            val slots = (0..Slot.MAX).shuffledBy(gen).take(1 + (gen.nextDouble() * 16).toInt())
            val due =
                slots.map { slot ->
                    DueWake(
                        babyId = BabyId("baby-$slot"),
                        slot = Slot(slot),
                        atWallMillis = (gen.nextDouble() * 10 * 60_000).toLong(),
                        kind = if (gen.nextDouble() < 0.8) AlarmKind.CARE_ALERT else AlarmKind.SAFETY_RECHECK,
                    )
                }
            val alarms = SchedulerPlanner.plan(due)
            val label = "case $case"

            assertEquals(alarms.size, alarms.map { it.requestCode }.toSet().size, "$label: codes collide")
            assertEquals(
                due.map { it.babyId }.sortedBy { it.value },
                alarms.flatMap { it.babies }.sortedBy { it.value },
                label,
            )
            assertEquals(alarms.sortedBy { it.triggerAtWallMillis }, alarms, "$label: alarms in time order")
            for (alarm in alarms) {
                val members = due.filter { it.babyId in alarm.babies }
                assertTrue(members.all { alarm.triggerAtWallMillis >= it.atWallMillis }, "$label: nobody woken early")
                assertTrue(
                    members.all { alarm.triggerAtWallMillis - it.atWallMillis <= window },
                    "$label: delay ≤ window",
                )
                val leader = members.minWith(compareBy({ it.atWallMillis }, { it.slot.value }))
                assertEquals(SchedulerPlanner.requestCode(leader.slot, leader.kind), alarm.requestCode, label)
                assertEquals(members.any { it.kind == AlarmKind.CARE_ALERT }, alarm.exact, label)
            }
        }
    }

    @Test
    fun wakesFarApartGetSeparateAlarmsAndCloseOnesMerge() {
        fun due(
            slot: Int,
            at: Long,
        ) = DueWake(BabyId("b$slot"), Slot(slot), at, AlarmKind.CARE_ALERT)
        assertEquals(1, SchedulerPlanner.plan(listOf(due(0, 0), due(1, 59_000))).size)
        assertEquals(2, SchedulerPlanner.plan(listOf(due(0, 0), due(1, 61_000))).size)
        val merged = SchedulerPlanner.plan(listOf(due(0, 10_000), due(1, 40_000))).single()
        assertEquals(40_000, merged.triggerAtWallMillis)
        assertEquals(setOf(BabyId("b0"), BabyId("b1")), merged.babies)
    }

    @Test
    fun oneDueWakePerBaby() {
        val twice = listOf(0L, 5_000L).map { DueWake(BabyId("a"), Slot(0), it, AlarmKind.CARE_ALERT) }
        assertFailsWith<IllegalArgumentException> { SchedulerPlanner.plan(twice) }
    }

    private fun <T> List<T>.shuffledBy(gen: SplitMix64): List<T> =
        map {
            it to gen.nextLong()
        }.sortedBy { it.second }.map { it.first }

    private fun IntRange.shuffledBy(gen: SplitMix64): List<Int> = toList().shuffledBy(gen)
}
