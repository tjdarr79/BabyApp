package dev.babysim.engine

import dev.babysim.engine.CauseFixtures.distressAfter
import dev.babysim.engine.CauseFixtures.minutesToSleep
import dev.babysim.engine.CauseFixtures.withCause
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.Cause
import dev.babysim.engine.model.Effect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * docs/04 §6.4: every cell of the care-action → cause matrix, measured against an identical baby left alone.
 * Continuous actions are read from `engine/params/model.json`; instant actions are tested cell by cell below.
 */
class EffectMatrixTest {
    private val engine = EngineFixtures.engine()
    private val model = EngineFixtures.params.model
    private val matrix = model.actionEffects.value
    private val calm = model.calmThreshold.value

    @Test
    fun theMatrixCoversEveryContinuousActionAndCause() {
        assertEquals(CareAction.CONTINUOUS, matrix.keys)
        for ((action, row) in matrix) assertEquals(Cause.entries.toSet(), row.keys, "$action row is incomplete")
    }

    @Test
    fun theMatrixMatchesTheSpec() {
        // docs/04 §6.4, as approved: R = RESOLVES, P = PARTIAL, – = NONE, ↑ = WORSENS.
        val spec =
            mapOf(
                CareActionType.FEED to "R P P W N P P P P",
                CareActionType.HOLD to "P P P P N P R R P",
                CareActionType.ROCK to "P R P P N P R R R",
                CareActionType.PLAY to "W W W N N W R R P",
            )
        val letters = mapOf("R" to Effect.RESOLVES, "P" to Effect.PARTIAL, "N" to Effect.NONE, "W" to Effect.WORSENS)
        for ((action, row) in spec) {
            val expected = Cause.entries.zip(row.split(" ").map { letters.getValue(it) }).toMap()
            assertEquals(expected, matrix.getValue(action), "$action")
        }
    }

    @Test
    fun everyContinuousCellBehavesAsDeclared() {
        for ((action, row) in matrix) {
            for ((cause, effect) in row) {
                if (cause == Cause.TIRED) continue // measured by time to sleep, below
                val config = CauseFixtures.configFor(cause)
                val state = withCause(cause, config)
                val start = CareAction.Start(action)
                val label = "$action × $cause ($effect)"
                when (effect) {
                    Effect.RESOLVES -> {
                        val with = distressAfter(config, state, start, 15.minutes)
                        assertTrue(with <= calm, "$label: distress $with should settle to calm")
                        assertTrue(with < distressAfter(config, state, null, 15.minutes), label)
                    }
                    Effect.PARTIAL -> {
                        val with = distressAfter(config, state, start, 4.minutes)
                        val control = distressAfter(config, state, null, 4.minutes)
                        assertTrue(with < control - 0.05, "$label: $with should be below control $control")
                    }
                    Effect.NONE -> {
                        val with = distressAfter(config, state, start, 4.minutes)
                        val control = distressAfter(config, state, null, 4.minutes)
                        assertTrue(abs(with - control) < 1e-9, "$label: $with should equal control $control")
                    }
                    Effect.WORSENS -> {
                        val with = distressAfter(config, state, start, 4.minutes)
                        val control = distressAfter(config, state, null, 4.minutes)
                        assertTrue(with > control + 0.05, "$label: $with should be above control $control")
                    }
                }
            }
        }
    }

    @Test
    fun tiredColumnIsMeasuredByTimeToSleep() {
        val config = CauseFixtures.configFor(Cause.TIRED)
        val state = withCause(Cause.TIRED)
        val control =
            assertNotNull(minutesToSleep(config, state, null), "a tired baby left alone sleeps within the hour")
        for ((action, row) in matrix) {
            val with = minutesToSleep(config, state, CareAction.Start(action))
            val label = "$action × TIRED (${row.getValue(Cause.TIRED)})"
            when (row.getValue(Cause.TIRED)) {
                Effect.RESOLVES, Effect.PARTIAL ->
                    assertTrue(
                        with != null && with < control,
                        "$label: $with vs $control",
                    )
                Effect.NONE -> assertEquals(control, with, label)
                // Play keeps a tired baby up; at worst it cries itself into exhausted sleep later.
                Effect.WORSENS -> assertTrue(with == null || with > control + 10, "$label: $with vs $control")
            }
        }
        val rock = assertNotNull(minutesToSleep(config, state, CareAction.Start(CareActionType.ROCK)))
        val hold = assertNotNull(minutesToSleep(config, state, CareAction.Start(CareActionType.HOLD)))
        assertTrue(rock < hold, "rocking (resolves) beats holding (partial): $rock vs $hold")
    }

    @Test
    fun holdingAHungryBabyCalmsOnlyBriefly() {
        val config = CauseFixtures.configFor(Cause.HUNGER)
        val state = withCause(Cause.HUNGER)
        val hold = CareAction.Start(CareActionType.HOLD)
        assertTrue(distressAfter(config, state, hold, 4.minutes) < distressAfter(config, state, null, 4.minutes) - 0.05)
        val later = distressAfter(config, state, hold, 20.minutes)
        val controlLater = distressAfter(config, state, null, 20.minutes)
        assertTrue(abs(later - controlLater) < 0.05, "hunger reasserts: $later vs $controlLater")
    }

    // ---- instant actions --------------------------------------------------------------------------------

    @Test
    fun burpingResolvesGasOnlySometimes() {
        val outcomes =
            (1L..30L).map { seed ->
                val config = EngineFixtures.config(seed = seed)
                val state = withCause(Cause.GAS, config)
                val result = engine.apply(config, state, CareAction.Do(CareActionType.BURP), T0, predict = false)
                val outcome = result.events.last().outcome
                if (outcome == ActionOutcome.BURPED) {
                    assertTrue(result.state.drives.gas < model.gasDiscomfortAt.value, "a burp relieves the gas")
                    assertTrue(distressAfter(config, state, CareAction.Do(CareActionType.BURP), 15.minutes) <= calm)
                } else {
                    assertEquals(state.drives.gas, result.state.drives.gas)
                }
                outcome
            }
        assertTrue(ActionOutcome.BURPED in outcomes && ActionOutcome.NO_BURP in outcomes, "$outcomes")
    }

    @Test
    fun burpingAndCheckingDoNothingForOtherCauses() {
        for (cause in Cause.entries - Cause.GAS - Cause.TIRED) {
            val config = CauseFixtures.configFor(cause)
            val state = withCause(cause, config)
            for (action in listOf(CareActionType.BURP, CareActionType.CHECK_DIAPER)) {
                val with = distressAfter(config, state, CareAction.Do(action), 4.minutes)
                val control = distressAfter(config, state, null, 4.minutes)
                assertTrue(abs(with - control) < 1e-9, "$action × $cause: $with vs $control")
            }
        }
    }

    @Test
    fun checkingTheDiaperRevealsItWithoutChangingIt() {
        val config = CauseFixtures.configFor(Cause.DIAPER)
        val dirty = withCause(Cause.DIAPER).let { it.copy(drives = it.drives.copy(diaperSoiled = true)) }
        val cases =
            mapOf(
                CauseFixtures.calm(config) to ActionOutcome.DIAPER_DRY,
                withCause(Cause.DIAPER) to ActionOutcome.DIAPER_WET,
                dirty to ActionOutcome.DIAPER_SOILED,
            )
        for ((state, expected) in cases) {
            val result = engine.apply(config, state, CareAction.Do(CareActionType.CHECK_DIAPER), T0, predict = false)
            assertEquals(expected, result.events.last().outcome)
            assertEquals(state.drives, result.state.drives)
        }
    }

    @Test
    fun changingResolvesTheDiaperAndOnlyBrieflyUpsetsOtherwise() {
        val config = CauseFixtures.configFor(Cause.DIAPER)
        val change = CareAction.Do(CareActionType.CHANGE_DIAPER)
        val wet = withCause(Cause.DIAPER)
        val changed = engine.apply(config, wet, change, T0, predict = false)
        assertEquals(ActionOutcome.CHANGED_WET, changed.events.last().outcome)
        assertEquals(0.0, changed.state.drives.diaperDiscomfort)
        assertTrue(distressAfter(config, wet, change, 15.minutes) <= calm)

        for (cause in Cause.entries - Cause.DIAPER - Cause.TIRED) {
            val causeConfig = CauseFixtures.configFor(cause)
            val state = withCause(cause, causeConfig)
            assertEquals(
                ActionOutcome.ALREADY_DRY,
                engine
                    .apply(causeConfig, state, change, T0)
                    .events
                    .last()
                    .outcome,
            )
            val with = distressAfter(causeConfig, state, change, 10.minutes)
            val control = distressAfter(causeConfig, state, null, 10.minutes)
            assertTrue(abs(with - control) < 1e-9, "CHANGE × $cause settles back: $with vs $control")
        }
    }

    @Test
    fun puttingDownSettlesATiredBabyAndUpsetsOneWhoWantsContact() {
        val config = CauseFixtures.configFor(Cause.TIRED)
        val putDown = CareAction.Do(CareActionType.PUT_DOWN_TO_SLEEP)
        val tired = withCause(Cause.TIRED).let { it.copy(drives = it.drives.copy(sleepPressure = 1.05)) }
        assertEquals(
            ActionOutcome.SETTLING_TO_SLEEP,
            engine
                .apply(config, tired, putDown, T0)
                .events
                .last()
                .outcome,
        )

        for (cause in listOf(Cause.WANTS_CONTACT, Cause.BORED)) {
            val causeConfig = CauseFixtures.configFor(cause)
            val state = withCause(cause, causeConfig)
            val result = engine.apply(causeConfig, state, putDown, T0, predict = false)
            assertEquals(ActionOutcome.WANTS_TO_BE_HELD, result.events.last().outcome, "$cause")
            assertTrue(result.state.drives.distress > state.drives.distress, "PUT_DOWN × $cause worsens")
        }
    }

    @Test
    fun puttingDownASleepingBabyUsuallyKeepsItAsleep() {
        val outcomes =
            (1L..60L).map { seed ->
                val config = EngineFixtures.config(seed = seed)
                val asleep =
                    engine.advance(config, withCause(Cause.TIRED, config), T0 + 2.minutes, predict = false).state.let {
                        engine
                            .apply(
                                config,
                                it,
                                CareAction.Start(CareActionType.ROCK),
                                T0 + 2.minutes,
                                predict = false,
                            ).state
                    }
                val sleeping = engine.advance(config, asleep, T0 + 40.minutes, predict = false).state
                assertTrue(sleeping.activity.name.contains("ASLEEP"), "rocked to sleep, seed $seed")
                engine
                    .apply(config, sleeping, CareAction.Do(CareActionType.PUT_DOWN_TO_SLEEP), T0 + 41.minutes)
                    .events
                    .last()
                    .outcome
            }
        val stayed = outcomes.count { it == ActionOutcome.STAYED_ASLEEP }
        assertEquals(outcomes.size, stayed + outcomes.count { it == ActionOutcome.WOKE_ON_TRANSFER })
        assertTrue(stayed in 35..58, "average temperament stays asleep most of the time: $stayed/60")
    }

    @Test
    fun wakeOnlyWakesASleepingBaby() {
        val config = CauseFixtures.configFor(Cause.TIRED)
        val awake = CauseFixtures.calm(config)
        assertEquals(
            ActionOutcome.ALREADY_AWAKE,
            engine
                .apply(config, awake, CareAction.Do(CareActionType.WAKE), T0)
                .events
                .last()
                .outcome,
        )
        val asleep =
            engine
                .advance(
                    config,
                    withCause(Cause.TIRED).let { it.copy(drives = it.drives.copy(sleepPressure = 1.05)) },
                    T0 + 30.minutes,
                ).state
        val woken = engine.apply(config, asleep, CareAction.Do(CareActionType.WAKE), T0 + 31.minutes)
        assertEquals(ActionOutcome.WOKE_UP, woken.events.last().outcome)
        assertTrue(woken.events.any { it.type == EventType.WOKE })
    }
}
