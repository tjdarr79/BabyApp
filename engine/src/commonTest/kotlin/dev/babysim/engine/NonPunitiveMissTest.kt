package dev.babysim.engine

import dev.babysim.engine.CauseFixtures.withCause
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.Cause
import dev.babysim.engine.state.BabyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Brief §8 and docs/04 §6.4 rule 1: an action that misses the baby's actual need never makes the baby more
 * upset than leaving it alone would (except where real babies object to handling, the ↑ cells). Misses get
 * an informative outcome, never a score.
 */
class NonPunitiveMissTest {
    private val engine = EngineFixtures.engine()

    private data class Miss(
        val name: String,
        val state: BabyState,
        val action: CareAction,
        val expected: ActionOutcome,
    )

    @Test
    fun missesNeverRaiseDistressAboveLeavingTheBabyAlone() {
        val notHungry = withCause(Cause.UNEXPLAINED).let { it.copy(drives = it.drives.copy(hunger = 0.2)) }
        val misses =
            listOf(
                Miss(
                    "feed a baby who is not hungry",
                    notHungry,
                    CareAction.Start(CareActionType.FEED),
                    ActionOutcome.NOT_INTERESTED,
                ),
                Miss(
                    "check a hungry baby's diaper",
                    withCause(Cause.HUNGER),
                    CareAction.Do(CareActionType.CHECK_DIAPER),
                    ActionOutcome.DIAPER_DRY,
                ),
                Miss(
                    "burp a hungry baby",
                    withCause(Cause.HUNGER),
                    CareAction.Do(CareActionType.BURP),
                    ActionOutcome.NO_BURP,
                ),
                Miss(
                    "hold a hungry baby",
                    withCause(Cause.HUNGER),
                    CareAction.Start(CareActionType.HOLD),
                    ActionOutcome.STARTED,
                ),
                Miss(
                    "rock a baby with a wet diaper",
                    withCause(Cause.DIAPER),
                    CareAction.Start(CareActionType.ROCK),
                    ActionOutcome.STARTED,
                ),
                Miss("stop when nothing is happening", withCause(Cause.HUNGER), CareAction.Stop, ActionOutcome.NOT_NOW),
            )
        for (miss in misses) {
            val config = CauseFixtures.configFor(Cause.HUNGER)
            val acted = engine.apply(config, miss.state, miss.action, T0, predict = false)
            assertEquals(miss.expected, acted.events.last().outcome, miss.name)
            var withState = acted.state
            var controlState = miss.state
            for (minute in 1..30) {
                val at = T0 + minute.minutes
                withState = engine.advance(config, withState, at, predict = false).state
                controlState = engine.advance(config, controlState, at, predict = false).state
                assertTrue(
                    withState.drives.distress <= controlState.drives.distress + 1e-9,
                    "${miss.name}: minute $minute, ${withState.drives.distress} > ${controlState.drives.distress}",
                )
            }
        }
    }

    @Test
    fun anyActionCountsAsAResponse() {
        val config = EngineFixtures.config()
        // Leave the baby alone until its first care request opens.
        var step = engine.initialize(config, T0)
        while (step.events.none { it.type == EventType.CARE_REQUEST_OPENED }) {
            step = engine.advance(config, step.state, assertNotNull(step.nextWakeAt))
        }
        assertNotNull(step.state.unanswered.firstUnansweredAt, "an open request is unanswered")
        val at = step.state.meta.lastAdvancedTo
        val checked = engine.apply(config, step.state, CareAction.Do(CareActionType.CHECK_DIAPER), at)
        assertEquals(0, checked.state.unanswered.expiredCount)
        assertEquals(null, checked.state.unanswered.firstUnansweredAt, "even a check counts as a response")
    }

    @Test
    fun cryingThatRunsOutWhileBeingCaredForIsNotUnanswered() {
        // A hungry baby rocked for an hour: rocking never fixes hunger, the crying cap is reached, but the
        // caregiver was there, so the request resolves instead of expiring and nothing counts against them.
        val config = CauseFixtures.configFor(Cause.HUNGER)
        val hungry = withCause(Cause.HUNGER).let { it.copy(drives = it.drives.copy(hunger = 1.6, distress = 0.9)) }
        val opened = engine.advance(config, hungry, T0 + 10.minutes, predict = false)
        assertTrue(opened.events.any { it.type == EventType.CARE_REQUEST_OPENED })
        val rocking =
            engine.apply(
                config,
                opened.state,
                CareAction.Start(CareActionType.ROCK),
                T0 + 10.minutes,
                predict = false,
            )
        val hour = engine.advance(config, rocking.state, T0 + 70.minutes, predict = false)
        assertTrue(hour.events.none { it.type == EventType.CARE_REQUEST_EXPIRED })
        assertEquals(0, hour.state.unanswered.expiredCount)
    }
}
