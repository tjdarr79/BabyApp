package dev.babysim.engine.sim

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventType
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.random.RngBank
import dev.babysim.engine.random.RngStreamId
import dev.babysim.engine.random.nextBernoulli
import dev.babysim.engine.state.OngoingAction
import dev.babysim.engine.state.RecordedAction
import dev.babysim.engine.state.Unanswered
import kotlin.math.max
import kotlin.math.min

/** Recent caregiver actions kept in state (brief §7). Bookkeeping size, not a behavior value. */
private const val RECENT_ACTIONS_KEPT = 16

/**
 * Applies one caregiver action at one instant (docs/04 §6.4). Continuous actions change how the baby evolves
 * on later ticks ([TickRunner]); instant actions change state now. Every action is recorded, counts as a
 * response (resetting the unanswered-request count), and yields a non-punitive [ActionOutcome].
 */
@Suppress("TooManyFunctions") // One function per care action keeps each action's rules small and testable.
internal class ActionHandler(
    private val runner: TickRunner,
    private val rates: Rates,
) {
    private val model = rates.model
    private val age = rates.age
    private val temperament = rates.temperament

    fun apply(
        w: Work,
        bank: RngBank,
        action: CareAction,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val (type, outcome) =
            when {
                w.pause != null -> typeOf(action, w) to ActionOutcome.NOT_NOW
                action is CareAction.Start -> action.type to start(w, bank, action.type, t, out)
                action is CareAction.Stop -> stop(w, bank, t)
                action is CareAction.Do -> action.type to instant(w, bank, action.type, t, out)
                else -> error("unknown action $action")
            }
        if (w.pause == null) w.unanswered = Unanswered()
        w.recentActions = (w.recentActions + RecordedAction(type, t, outcome)).takeLast(RECENT_ACTIONS_KEPT)
        out += runner.event(w, t, EventType.ACTION_OUTCOME).copy(action = type, outcome = outcome)
    }

    private fun typeOf(
        action: CareAction,
        w: Work,
    ): CareActionType =
        when (action) {
            is CareAction.Start -> action.type
            is CareAction.Do -> action.type
            CareAction.Stop -> w.ongoingAction?.type ?: CareActionType.HOLD
        }

    // ---- continuous actions -------------------------------------------------------------------------------

    private fun start(
        w: Work,
        bank: RngBank,
        type: CareActionType,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome {
        if (type == CareActionType.PLAY && w.isAsleep) return ActionOutcome.NOT_NOW
        endOngoing(w, bank, t)
        return when (type) {
            CareActionType.FEED -> startFeed(w, bank, t, out)
            CareActionType.PLAY -> startPlay(w, t)
            else -> {
                w.ongoingAction = OngoingAction(type, t)
                w.lastInteractionAt = t
                ActionOutcome.STARTED
            }
        }
    }

    /** A feed is accepted only if the baby is hungry enough (docs/04 §5.2). A sleeping baby is woken to feed. */
    private fun startFeed(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome {
        if (w.isAsleep) runner.wake(w, bank, t, wantsContact = false, out)
        if (w.hunger < age.feedAcceptMin.value) {
            // Brief sucking comforts a little even when the baby is not hungry (non-punitive, brief §8).
            w.distress = max(0.0, w.distress - model.nonNutritiveComfort.value)
            return ActionOutcome.NOT_INTERESTED
        }
        val minutes = bank.stream(RngStreamId.FEEDING).sample(age.feedDurationMinutes.value)
        w.ongoingAction = OngoingAction(CareActionType.FEED, t, intakePerMinute = w.hunger / minutes)
        return ActionOutcome.FEEDING
    }

    private fun startPlay(
        w: Work,
        t: SimInstant,
    ): ActionOutcome {
        w.ongoingAction = OngoingAction(CareActionType.PLAY, t)
        w.lastInteractionAt = t
        w.fallAsleepAt = null
        val opposed =
            w.hunger >= w.thresholds.hungerCueAt ||
                w.sleepPressure >= w.thresholds.sleepReadyAt ||
                w.distress >= rates.fussLevel
        return if (opposed) ActionOutcome.NOT_IN_THE_MOOD else ActionOutcome.PLAYING
    }

    private fun stop(
        w: Work,
        bank: RngBank,
        t: SimInstant,
    ): Pair<CareActionType, ActionOutcome> {
        val ongoing = w.ongoingAction ?: return CareActionType.HOLD to ActionOutcome.NOT_NOW
        endOngoing(w, bank, t)
        return ongoing.type to ActionOutcome.STOPPED
    }

    private fun endOngoing(
        w: Work,
        bank: RngBank,
        t: SimInstant,
    ) {
        val ongoing = w.ongoingAction ?: return
        if (ongoing.type == CareActionType.FEED) runner.endFeed(w, bank, t)
        if (ongoing.type in SOCIAL) w.lastInteractionAt = t
        w.ongoingAction = null
    }

    // ---- instant actions ----------------------------------------------------------------------------------

    private fun instant(
        w: Work,
        bank: RngBank,
        type: CareActionType,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome =
        when (type) {
            CareActionType.BURP -> burp(w, bank, t, out)
            CareActionType.CHECK_DIAPER -> checkDiaper(w)
            CareActionType.CHANGE_DIAPER -> changeDiaper(w, bank, t, out)
            CareActionType.PUT_DOWN_TO_SLEEP -> putDown(w, bank, t, out)
            CareActionType.WAKE ->
                if (w.isAsleep) {
                    runner.wake(w, bank, t, wantsContact = false, out)
                    ActionOutcome.WOKE_UP
                } else {
                    ActionOutcome.ALREADY_AWAKE
                }
            else -> ActionOutcome.NOT_NOW // PACIFIER is reserved (docs/01 B6).
        }

    private fun burp(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome {
        val burped =
            w.gas >= model.burpableGas.value && bank.stream(RngStreamId.BURP).nextBernoulli(model.burpSuccessProb.value)
        if (!burped) return ActionOutcome.NO_BURP
        runner.burp(w, t, out)
        return ActionOutcome.BURPED
    }

    /** Information only: reveals the diaper, changes nothing (docs/01 B13). */
    private fun checkDiaper(w: Work): ActionOutcome =
        when {
            w.diaperSoiled -> ActionOutcome.DIAPER_SOILED
            w.diaperWetness > 0.0 -> ActionOutcome.DIAPER_WET
            else -> ActionOutcome.DIAPER_DRY
        }

    private fun changeDiaper(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome {
        val outcome =
            when {
                w.diaperSoiled -> ActionOutcome.CHANGED_SOILED
                w.diaperWetness > 0.0 -> ActionOutcome.CHANGED_WET
                else -> ActionOutcome.ALREADY_DRY
            }
        if (w.isAsleep) runner.wake(w, bank, t, wantsContact = false, out)
        w.diaperWetness = 0.0
        w.diaperSoiled = false
        w.diaperDiscomfort = 0.0
        w.timestamps = w.timestamps.copy(lastDiaperChangeAt = t)
        bump(w, model.changeTransitionDistress.value)
        return outcome
    }

    /**
     * Ends any holding or feeding. A sleeping baby usually stays asleep; a drowsy or tired one settles to sleep;
     * a baby who is not ready protests a little (docs/04 §6.4).
     */
    private fun putDown(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ): ActionOutcome {
        val wasRocking = w.ongoingAction?.type == CareActionType.ROCK
        endOngoing(w, bank, t)
        return when {
            w.isAsleep ->
                if (bank.stream(RngStreamId.SOOTHE).nextBernoulli(rates.transferStayAsleepProb)) {
                    ActionOutcome.STAYED_ASLEEP
                } else {
                    runner.wake(w, bank, t, wantsContact = true, out)
                    ActionOutcome.WOKE_ON_TRANSFER
                }
            w.fallAsleepAt != null -> ActionOutcome.SETTLING_TO_SLEEP
            readyToSettle(w, wasRocking) -> {
                w.fallAsleepAt = t + runner.sampleDrowsyMinutes(bank)
                ActionOutcome.SETTLING_TO_SLEEP
            }
            else -> {
                bump(w, model.putDownTransitionDistress.value)
                ActionOutcome.WANTS_TO_BE_HELD
            }
        }
    }

    private fun readyToSettle(
        w: Work,
        wasRocking: Boolean,
    ): Boolean {
        val threshold = w.thresholds.sleepReadyAt * if (wasRocking) model.rockDrowsyFactor.value else 1.0
        return w.sleepPressure >= threshold && w.distress < rates.cryLevel
    }

    /** A transition upset, scaled by the temperament's sensitivity to transitions. Awake babies only. */
    private fun bump(
        w: Work,
        amount: Double,
    ) {
        if (!w.isAsleep) w.distress = min(1.0, w.distress + amount * temperament.transitionDistress.value)
    }
}
