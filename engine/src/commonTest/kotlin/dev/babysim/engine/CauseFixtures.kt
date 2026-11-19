package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.Cause
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.ProcessId
import dev.babysim.engine.state.Thresholds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Builds awake babies with exactly one hidden cause active, at its equilibrium distress, with background
 * randomness (fussy spells, wetting) switched off, so an action's effect on that cause can be measured
 * against an identical baby left alone.
 */
object CauseFixtures {
    private val model = EngineFixtures.params.model

    /**
     * An engine where feeding swallows no air, so a feed's effect on one cause is not mixed up with the gas it
     * adds. Gas from feeding is a cell of its own (FEED × GAS) and is covered with the real parameters.
     */
    private val engine =
        BehaviorEngine(
            EngineFixtures.params.copy(model = model.copy(gasPerFeedMinute = model.gasPerFeedMinute.copy(value = 0.0))),
        )

    /** Hunger high enough that a feed is accepted, but below the hunger cue. */
    const val FEEDABLE_HUNGER = 0.6

    fun configFor(cause: Cause): BabyConfig =
        // Boredom exists from 4 months; everything else is measured on a 2–3 month old.
        EngineFixtures.config(if (cause == Cause.BORED) AgeBandId.M4_6 else AgeBandId.M2_3, seed = 5)

    /** A calm, awake baby with thresholds pinned at 1.0 and background processes disabled. */
    fun calm(config: BabyConfig): BabyState {
        val fresh = engine.initialize(config, T0).state
        val never = 1e12
        return fresh.copy(
            thresholds = Thresholds(hungerCueAt = 1.0, sleepReadyAt = 1.0, careSustainMinutes = 3.0),
            budgets = mapOf(ProcessId.VOID to never, ProcessId.STOOL to never, ProcessId.UNEXPLAINED_FUSS to never),
            activity = Activity.AWAKE_CALM,
        )
    }

    /** One cause active at its equilibrium distress. TIRED is set just below sleep-ready (see EffectMatrixTest). */
    fun withCause(
        cause: Cause,
        config: BabyConfig = configFor(cause),
    ): BabyState {
        val base = calm(config)
        val d = base.drives
        val later = T0 + 60.minutes
        return when (cause) {
            Cause.HUNGER -> base.copy(drives = d.copy(hunger = 1.1, distress = 0.7))
            Cause.TIRED -> base.copy(drives = d.copy(hunger = FEEDABLE_HUNGER, sleepPressure = 0.8))
            Cause.OVERTIRED ->
                base.copy(
                    drives =
                        d.copy(
                            hunger = FEEDABLE_HUNGER,
                            sleepPressure = 1.0 + model.overtiredMargin.value + 0.1,
                            distress = 0.7,
                        ),
                )
            Cause.GAS ->
                base.copy(
                    drives = d.copy(hunger = FEEDABLE_HUNGER, gas = 0.4, distress = model.gasCauseWeight.value),
                )
            Cause.DIAPER ->
                base.copy(
                    drives =
                        d.copy(
                            hunger = FEEDABLE_HUNGER,
                            diaperWetness = 1.0,
                            diaperDiscomfort = 0.6,
                            distress = 0.5,
                        ),
                )
            Cause.OVERSTIMULATED ->
                base.copy(
                    drives =
                        d.copy(
                            hunger = FEEDABLE_HUNGER,
                            stimulation = 0.8,
                            distress = model.overstimCauseWeight.value,
                        ),
                )
            Cause.BORED ->
                base.copy(
                    drives = d.copy(hunger = FEEDABLE_HUNGER, distress = model.boredCauseWeight.value),
                    lastInteractionAt = T0 - 120.minutes,
                    timestamps = base.timestamps.copy(lastWakeAt = T0 - 120.minutes),
                )
            Cause.WANTS_CONTACT ->
                base.copy(drives = d.copy(hunger = FEEDABLE_HUNGER, distress = 0.5), wantsContactUntil = later)
            Cause.UNEXPLAINED ->
                base.copy(drives = d.copy(hunger = FEEDABLE_HUNGER, distress = 0.6), fussSpellEndsAt = later)
        }
    }

    /** Distress after [after], with [action] applied at T0 (or nothing, for the control). */
    fun distressAfter(
        config: BabyConfig,
        state: BabyState,
        action: CareAction?,
        after: Duration,
    ): Double {
        val start = action?.let { engine.apply(config, state, it, T0, predict = false).state } ?: state
        return engine
            .advance(config, start, T0 + after, predict = false)
            .state.drives.distress
    }

    /** Minutes until the baby falls asleep within [limit], or null. */
    fun minutesToSleep(
        config: BabyConfig,
        state: BabyState,
        action: CareAction?,
        limit: Duration = 60.minutes,
    ): Double? {
        val start = action?.let { engine.apply(config, state, it, T0, predict = false).state } ?: state
        val asleep =
            engine
                .advance(config, start, T0 + limit, predict = false)
                .events
                .firstOrNull { it.type == EventType.FELL_ASLEEP } ?: return null
        return asleep.at.millisSince(T0) / 60_000.0
    }
}
