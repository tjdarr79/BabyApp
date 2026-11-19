package dev.babysim.engine

import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.params.ParamSet
import dev.babysim.engine.random.RngBank
import dev.babysim.engine.sim.Rates
import dev.babysim.engine.sim.TICK_MILLIS
import dev.babysim.engine.sim.TickRunner
import dev.babysim.engine.sim.Work
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.PauseInfo
import dev.babysim.engine.state.PauseReason
import dev.babysim.engine.state.Unanswered
import kotlin.time.Duration.Companion.hours

/**
 * The behavior engine (docs/04): a pure, stateless stepper over one baby's [BabyState].
 *
 * State changes only on ticks aligned to absolute multiples of 30 s, so catching up in one call or in many
 * calls gives the identical state and events (docs/04 §8). The OS never needs to wake per tick: the host
 * schedules the next wake from [StepResult.nextWakeAt]. Holds only immutable parameters; safe to share
 * between babies (CLAUDE.md rule 2).
 */
class BehaviorEngine(
    private val params: ParamSet,
    private val localClock: LocalClock = LocalClock.UTC,
) {
    /** A newly configured baby: just fed, awake and calm, with a caregiver present at [at]. */
    fun initialize(
        config: BabyConfig,
        at: SimInstant,
    ): StepResult {
        val runner = runner(config)
        val fresh = BabyState.fresh(config, params.version, at)
        val bank = RngBank(fresh.rng)
        val w = Work(fresh)
        w.activity = Activity.AWAKE_CALM
        w.timestamps = w.timestamps.copy(lastFeedEndAt = at, lastWakeAt = at)
        w.lastInteractionAt = at
        w.thresholds =
            w.thresholds.copy(
                hungerCueAt = runner.sampleHungerCue(bank),
                sleepReadyAt = runner.sampleSleepReady(bank),
                careSustainMinutes = runner.sampleCareSustain(bank),
            )
        return withPrediction(config, w.toState(bank.snapshot(), at), emptyList(), predict = true)
    }

    /**
     * Catches the baby up to [to] with no caregiver actions. A [to] earlier than the state's time is clamped
     * (the engine never runs backward, docs/01 B8). [predict] = false skips the look-ahead for bulk replay.
     */
    fun advance(
        config: BabyConfig,
        state: BabyState,
        to: SimInstant,
        predict: Boolean = true,
    ): StepResult {
        require(state.babyId == config.id) { "state belongs to ${state.babyId}, config to ${config.id}" }
        val target = maxOf(to, state.meta.lastAdvancedTo)
        val runner = runner(config)
        val w = Work(state)
        val bank = RngBank(state.rng)
        val events = mutableListOf<BabyEvent>()
        var tick = firstTickAfter(state.meta.lastAdvancedTo)
        while (tick <= target.epochMillis) {
            runner.tick(w, bank, SimInstant(tick), events)
            tick += TICK_MILLIS
        }
        return withPrediction(config, w.toState(bank.snapshot(), target), events, predict)
    }

    /** Pauses the simulation at [at]: needs stop accumulating until [resume]. */
    fun pause(
        config: BabyConfig,
        state: BabyState,
        at: SimInstant,
    ): StepResult {
        val caughtUp = advance(config, state, at, predict = false)
        if (caughtUp.state.pause != null) return caughtUp
        val paused = caughtUp.state.copy(pause = PauseInfo(PauseReason.USER, caughtUp.state.meta.lastAdvancedTo))
        val event = BabyEvent(config.id, paused.meta.lastAdvancedTo, EventType.PAUSED)
        return StepResult(paused, caughtUp.events + event, nextEventAt = null, nextWakeAt = null)
    }

    /**
     * Resumes a paused baby at [at]. Timers that were running (sleep cycle, drowsiness, fussy spells, crying
     * durations, boredom) are shifted by the paused duration, so the baby picks up where it left off.
     * The unanswered-request count restarts.
     */
    fun resume(
        config: BabyConfig,
        state: BabyState,
        at: SimInstant,
    ): StepResult {
        val pause = state.pause ?: return advance(config, state, at)
        val caughtUp = advance(config, state, at, predict = false)
        val now = caughtUp.state.meta.lastAdvancedTo
        val resumed =
            shiftTimers(
                caughtUp.state,
                now.millisSince(pause.since),
            ).copy(pause = null, unanswered = Unanswered())
        val event = BabyEvent(config.id, now, EventType.RESUMED)
        return withPrediction(config, resumed, caughtUp.events + event, predict = true)
    }

    private fun runner(config: BabyConfig) = TickRunner(config, Rates(config, params), localClock)

    private fun withPrediction(
        config: BabyConfig,
        state: BabyState,
        events: List<BabyEvent>,
        predict: Boolean,
    ): StepResult {
        if (!predict || state.pause != null) return StepResult(state, events, nextEventAt = null, nextWakeAt = null)
        val (nextEvent, nextWake) = lookAhead(config, state)
        return StepResult(state, events, nextEvent, nextWake)
    }

    /**
     * Runs the deterministic simulation forward on a copy (no caregiver actions) to find exactly when the next
     * event and the next alert-worthy event fire. Never changes [state].
     */
    private fun lookAhead(
        config: BabyConfig,
        state: BabyState,
    ): Pair<SimInstant?, SimInstant?> {
        val runner = runner(config)
        val w = Work(state)
        val bank = RngBank(state.rng)
        val events = mutableListOf<BabyEvent>()
        val horizon = (state.meta.lastAdvancedTo + LOOK_AHEAD).epochMillis
        var nextEvent: SimInstant? = null
        var tick = firstTickAfter(state.meta.lastAdvancedTo)
        while (tick <= horizon) {
            events.clear()
            val at = SimInstant(tick)
            runner.tick(w, bank, at, events)
            if (events.isNotEmpty() && nextEvent == null) nextEvent = at
            if (events.any { it.type.alerts }) return nextEvent to at
            tick += TICK_MILLIS
        }
        return nextEvent to null
    }

    private fun shiftTimers(
        state: BabyState,
        byMillis: Long,
    ): BabyState {
        fun SimInstant.shifted() = SimInstant(epochMillis + byMillis)
        return state.copy(
            sleep =
                state.sleep?.let {
                    it.copy(cycleStartedAt = it.cycleStartedAt.shifted(), cycleEndsAt = it.cycleEndsAt.shifted())
                },
            fallAsleepAt = state.fallAsleepAt?.shifted(),
            fussSpellEndsAt = state.fussSpellEndsAt?.shifted(),
            wantsContactUntil = state.wantsContactUntil?.shifted(),
            cryingLevelSince = state.cryingLevelSince?.shifted(),
            cryingSince = state.cryingSince?.shifted(),
            lastInteractionAt = state.lastInteractionAt?.shifted(),
            careRequest =
                state.careRequest?.let {
                    it.copy(
                        openedAt = it.openedAt.shifted(),
                        lastAlertAt = it.lastAlertAt.shifted(),
                    )
                },
            // Time-since counters exclude paused time. History keeps the real times; this is engine time only.
            timestamps =
                state.timestamps.let {
                    it.copy(
                        lastFeedEndAt = it.lastFeedEndAt?.shifted(),
                        lastDiaperChangeAt = it.lastDiaperChangeAt?.shifted(),
                        lastSleepStartAt = it.lastSleepStartAt?.shifted(),
                        lastWakeAt = it.lastWakeAt?.shifted(),
                        lastSoothedAt = it.lastSoothedAt?.shifted(),
                        lastBurpAt = it.lastBurpAt?.shifted(),
                    )
                },
            recentActions = state.recentActions.map { it.copy(at = it.at.shifted()) },
        )
    }

    private companion object {
        val LOOK_AHEAD = 24.hours

        /** The first tick strictly after [t] on the absolute 30 s grid. */
        fun firstTickAfter(t: SimInstant): Long = (t.epochMillis.floorDiv(TICK_MILLIS) + 1) * TICK_MILLIS
    }
}
