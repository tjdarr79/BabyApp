package dev.babysim.engine.sim

import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.random.RngBankState
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.Drives
import dev.babysim.engine.state.ProcessId
import dev.babysim.engine.state.StateMeta

/**
 * Mutable working copy of one baby's [BabyState] for the duration of one engine call. Built from an immutable
 * state, mutated tick by tick, then frozen back with [toState]. Never shared between babies or calls.
 */
internal class Work(
    from: BabyState,
) {
    val babyId = from.babyId
    private val meta: StateMeta = from.meta

    var hunger = from.drives.hunger
    var sleepPressure = from.drives.sleepPressure
    var gas = from.drives.gas
    var diaperWetness = from.drives.diaperWetness
    var diaperSoiled = from.drives.diaperSoiled
    var diaperDiscomfort = from.drives.diaperDiscomfort
    var stimulation = from.drives.stimulation
    var distress = from.drives.distress
    var mood = from.drives.mood

    var activity = from.activity
    var ongoingAction = from.ongoingAction
    var careRequest = from.careRequest
    var timestamps = from.timestamps
    var recentActions = from.recentActions
    var thresholds = from.thresholds
    val budgets: MutableMap<ProcessId, Double> = from.budgets.toMutableMap()
    var sleep = from.sleep
    var fallAsleepAt = from.fallAsleepAt
    var fussSpellEndsAt = from.fussSpellEndsAt
    var wantsContactUntil = from.wantsContactUntil
    var cryingLevelSince = from.cryingLevelSince
    var cryingSince = from.cryingSince
    var lastInteractionAt = from.lastInteractionAt
    var unanswered = from.unanswered
    var nextCareRequestId = from.nextCareRequestId
    var pause = from.pause

    val isAsleep: Boolean
        get() = activity in SLEEPING

    fun toState(
        rng: RngBankState,
        lastAdvancedTo: SimInstant,
    ): BabyState =
        BabyState(
            babyId = babyId,
            meta = meta.copy(lastAdvancedTo = lastAdvancedTo),
            drives =
                Drives(
                    hunger = hunger,
                    sleepPressure = sleepPressure,
                    gas = gas,
                    diaperWetness = diaperWetness,
                    diaperSoiled = diaperSoiled,
                    diaperDiscomfort = diaperDiscomfort,
                    stimulation = stimulation,
                    distress = distress,
                    mood = mood,
                ),
            activity = activity,
            ongoingAction = ongoingAction,
            careRequest = careRequest,
            timestamps = timestamps,
            recentActions = recentActions,
            rng = rng,
            thresholds = thresholds,
            budgets = budgets.toMap(),
            sleep = sleep,
            fallAsleepAt = fallAsleepAt,
            fussSpellEndsAt = fussSpellEndsAt,
            wantsContactUntil = wantsContactUntil,
            cryingLevelSince = cryingLevelSince,
            cryingSince = cryingSince,
            lastInteractionAt = lastInteractionAt,
            unanswered = unanswered,
            nextCareRequestId = nextCareRequestId,
            pause = pause,
        )

    companion object {
        val SLEEPING: Set<Activity> = setOf(Activity.ASLEEP_ACTIVE, Activity.ASLEEP_QUIET, Activity.EXHAUSTED_SLEEP)
    }
}
