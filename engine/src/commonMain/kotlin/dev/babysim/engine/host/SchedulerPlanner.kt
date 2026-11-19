package dev.babysim.engine.host

import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.Slot
import kotlinx.serialization.Serializable

/** Why the device must wake. Codes are part of the derived alarm id and must stay below [ID_KINDS_PER_SLOT]. */
@Serializable
enum class AlarmKind(
    val code: Int,
) {
    /** A care request opens or escalates, or the baby auto-pauses. */
    CARE_ALERT(0),

    /** Nothing is predicted within the look-ahead horizon: wake later and look again (docs/04 §7.3). */
    SAFETY_RECHECK(1),
}

/** One baby's next reason to wake the device, in wall-clock time. */
data class DueWake(
    val babyId: BabyId,
    val slot: Slot,
    val atWallMillis: Long,
    val kind: AlarmKind,
)

/**
 * One OS alarm (docs/03 §3). [requestCode] is collision-free across babies and kinds. [babies] are all woken
 * by it: when it fires the host advances every baby (docs/04 §11.2).
 */
data class AlarmRequest(
    val requestCode: Int,
    val triggerAtWallMillis: Long,
    /** Whether an exact alarm is wanted; the app falls back to inexact without the permission (docs/03 §3). */
    val exact: Boolean,
    val kind: AlarmKind,
    val babies: Set<BabyId>,
)

internal const val ID_BASE = 1000
internal const val ID_KINDS_PER_SLOT = 16

/**
 * Merges every baby's next wake into OS alarm requests (CLAUDE.md rule 5). Pure.
 *
 * Wakes due within [coalesceWindowMillis] of each other share one alarm, which fires at the latest of them, so
 * no member is woken before it is due and none waits longer than the window. The shared alarm uses the request
 * code of its earliest member.
 */
object SchedulerPlanner {
    const val DEFAULT_COALESCE_WINDOW_MILLIS: Long = 60_000L

    /** `1000 + slot × 16 + kind`: unique for every slot (0–15) and kind (< 16). */
    fun requestCode(
        slot: Slot,
        kind: AlarmKind,
    ): Int = ID_BASE + slot.value * ID_KINDS_PER_SLOT + kind.code

    fun plan(
        due: List<DueWake>,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    ): List<AlarmRequest> {
        require(due.map { it.babyId }.toSet().size == due.size) { "at most one due wake per baby" }
        val sorted = due.sortedWith(compareBy({ it.atWallMillis }, { it.slot.value }))
        val alarms = mutableListOf<AlarmRequest>()
        var i = 0
        while (i < sorted.size) {
            val leader = sorted[i]
            val group = sorted.drop(i).takeWhile { it.atWallMillis - leader.atWallMillis <= coalesceWindowMillis }
            alarms +=
                AlarmRequest(
                    requestCode = requestCode(leader.slot, leader.kind),
                    triggerAtWallMillis = group.maxOf { it.atWallMillis },
                    exact = group.any { it.kind == AlarmKind.CARE_ALERT },
                    kind = if (group.any { it.kind == AlarmKind.CARE_ALERT }) AlarmKind.CARE_ALERT else leader.kind,
                    babies = group.map { it.babyId }.toSet(),
                )
            i += group.size
        }
        return alarms
    }
}
