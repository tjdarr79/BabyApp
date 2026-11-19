package dev.babysim.engine

import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.CareRequest
import dev.babysim.engine.state.ProcessId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The brief's §7 example, replayed as in docs/04 §12: a three-month-old cries; a feed is refused (fed
 * recently); the diaper is dry; rocking calms the baby after a variable settling period; the next waking is
 * driven by hunger, because the refused feed added nothing. The user never learns the cause.
 */
class ThreeMonthCryingScenarioTest {
    private val engine = EngineFixtures.engine()

    /** Fed about 1¾ h ago (hunger below the feed-accept level), overtired, a little trapped gas, crying. */
    private fun crying(seed: Long): Pair<dev.babysim.engine.model.BabyConfig, BabyState> {
        val config = EngineFixtures.config(AgeBandId.M2_3, seed = seed)
        val base = engine.initialize(config, T0).state
        val crying =
            base.copy(
                drives =
                    base.drives.copy(
                        hunger = 0.47,
                        sleepPressure = base.thresholds.sleepReadyAt + 0.3,
                        gas = 0.3,
                        distress = 0.7,
                    ),
                activity = Activity.CRYING,
                careRequest = CareRequest(id = 1, openedAt = T0, escalationLevel = 1, lastAlertAt = T0),
                nextCareRequestId = 2,
                cryingSince = T0,
                timestamps = base.timestamps.copy(lastFeedEndAt = T0 - 105.minutes),
                // The brief's diaper is dry when checked: no wetting in the first minutes (natural afterwards).
                budgets = base.budgets + (ProcessId.VOID to 1.0),
            )
        return config to crying
    }

    private data class SeedResult(
        val settleMinutes: Long,
        val stayedAsleep: Boolean,
        val wokeHungry: Boolean,
    )

    @Test
    fun theScenarioPlaysOutAsTheBriefDescribes() {
        val results = (1L..20L).map { playScenario(it) }
        val settleMinutes = results.map { it.settleMinutes }.toSet()
        val stayedAsleep = results.count { it.stayedAsleep }
        val wokeHungry = results.count { it.wokeHungry }
        assertTrue(settleMinutes.size >= 3, "settling time varies: $settleMinutes")
        assertTrue(stayedAsleep >= 10, "most transfers succeed: $stayedAsleep/20")
        assertTrue(wokeHungry * 2 >= stayedAsleep, "most wake hungry: $wokeHungry/$stayedAsleep")
    }

    private fun playScenario(seed: Long): SeedResult {
        val model = EngineFixtures.params.model
        val (config, crying) = crying(seed)

        val feed = engine.apply(config, crying, CareAction.Start(CareActionType.FEED), T0 + 1.minutes)
        assertEquals(ActionOutcome.NOT_INTERESTED, feed.events.last().outcome, "seed $seed: fed recently")
        assertTrue(feed.state.ongoingAction == null)

        val check = engine.apply(config, feed.state, CareAction.Do(CareActionType.CHECK_DIAPER), T0 + 2.minutes)
        assertEquals(ActionOutcome.DIAPER_DRY, check.events.last().outcome, "seed $seed")

        val rock = engine.apply(config, check.state, CareAction.Start(CareActionType.ROCK), T0 + 3.minutes)
        val rocked = engine.advance(config, rock.state, T0 + 33.minutes)
        val resolved =
            assertNotNull(
                rocked.events.firstOrNull { it.type == EventType.CARE_REQUEST_RESOLVED },
                "seed $seed: rocking calms the baby",
            )
        assertEquals(CareActionType.ROCK, resolved.action)
        assertTrue(rocked.events.none { it.type == EventType.CARE_REQUEST_ESCALATED }, "no repeat alerts while rocking")
        assertTrue(rocked.events.any { it.type == EventType.FELL_ASLEEP }, "seed $seed: asleep in arms")
        val settleMinutes = resolved.at.millisSince(T0 + 3.minutes) / 60_000

        val putDown =
            engine.apply(
                config,
                rocked.state,
                CareAction.Do(CareActionType.PUT_DOWN_TO_SLEEP),
                T0 + 34.minutes,
            )
        val outcome = putDown.events.last().outcome
        assertTrue(outcome == ActionOutcome.STAYED_ASLEEP || outcome == ActionOutcome.WOKE_ON_TRANSFER, "$outcome")
        if (outcome !=
            ActionOutcome.STAYED_ASLEEP
        ) {
            return SeedResult(settleMinutes, stayedAsleep = false, wokeHungry = false)
        }

        // Later behavior reflects the interaction: the refused feed added nothing, so the baby wakes for a need,
        // usually hunger (sometimes a dirty diaper gets there first).
        val until = engine.advance(config, putDown.state, T0 + (6 * 60).minutes, predict = false)
        val woke = assertNotNull(until.events.firstOrNull { it.type == EventType.WOKE }, "seed $seed wakes")
        val atWake = engine.advance(config, putDown.state, woke.at, predict = false).state
        val hungry = atWake.drives.hunger >= atWake.thresholds.hungerCueAt
        val dirty = atWake.drives.diaperDiscomfort >= model.diaperCueAt.value
        assertTrue(hungry || dirty, "seed $seed: woke for a need")
        if (hungry) {
            val feedNow = engine.apply(config, atWake, CareAction.Start(CareActionType.FEED), woke.at)
            assertEquals(ActionOutcome.FEEDING, feedNow.events.last().outcome, "seed $seed: now the feed is taken")
        }
        return SeedResult(settleMinutes, stayedAsleep = true, wokeHungry = hungry)
    }
}
