package dev.babysim.engine.host

import dev.babysim.engine.EngineFixtures
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.EventType
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.TemperamentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The host keeps babies independent (CLAUDE.md rules 1–2). A short version of the two-baby proof; the 72-hour
 * `TwoBabySeventyTwoHourTest` comes with the harness in M2.6.
 */
class HostIsolationTest {
    private val hourMillis = 3_600_000L
    private val start = T0.epochMillis
    private val a = EngineFixtures.config(AgeBandId.NEWBORN, TemperamentId.DEMANDING, seed = 0xA, id = "a", slot = 0)
    private val b = EngineFixtures.config(AgeBandId.M7_9, TemperamentId.EASYGOING, seed = 0xB, id = "b", slot = 1)

    private fun twoBabyHost() =
        SimulationHost(EngineFixtures.engine(), TwoBabies).apply {
            create(a, start)
            create(b, start)
        }

    private fun soloHost(config: BabyConfig) =
        SimulationHost(EngineFixtures.engine(), V1Entitlements).apply {
            create(config, start)
        }

    private object TwoBabies : EntitlementProvider {
        override val maxActiveBabies = 2
    }

    @Test
    fun actionsOnOneBabyDoNotTouchTheOther() {
        val shared = twoBabyHost()
        val alone = soloHost(b)
        var t = start
        repeat(12) { i ->
            t += hourMillis / 2
            val action =
                if (i % 2 ==
                    0
                ) {
                    CareAction.Start(CareActionType.ROCK)
                } else {
                    CareAction.Do(CareActionType.CHANGE_DIAPER)
                }
            val step = shared.apply(a.id, action, t)
            assertEquals(setOf(a.id), step.results.keys, "only baby A is stepped by an action on A")
            shared.advanceAll(t)
            alone.advanceAll(t)
            assertEquals(alone[b.id].state, shared[b.id].state, "baby B unchanged by actions on A, step $i")
        }
    }

    @Test
    fun everyResultEventAndAlarmIsKeyedByItsBaby() {
        val host = twoBabyHost()
        val step = host.advanceAll(start + 2 * hourMillis)
        for ((id, result) in step.results) {
            assertTrue(result.events.all { it.babyId == id })
            assertEquals(id, result.audio.babyId)
            assertEquals(id, result.state.babyId)
        }
        val alarms = host.plannedAlarms(start + 2 * hourMillis)
        assertEquals(setOf(a.id, b.id), alarms.flatMap { it.babies }.toSet())
        assertEquals(alarms.size, alarms.map { it.requestCode }.toSet().size)
    }

    @Test
    fun babiesKeepTheirOwnSchedules() {
        val host = twoBabyHost()
        val step = host.advanceAll(start + 24 * hourMillis)
        val onsets =
            step.results.mapValues { (_, r) ->
                r.events.filter { it.type == EventType.CARE_REQUEST_OPENED }.map { it.at }
            }
        assertTrue(onsets.getValue(a.id).isNotEmpty() && onsets.getValue(b.id).isNotEmpty())
        assertTrue(onsets.getValue(a.id) != onsets.getValue(b.id), "schedules do not mirror each other")
    }

    @Test
    fun pausingOneBabyLeavesTheOtherRunning() {
        val host = twoBabyHost()
        host.pause(a.id, start + hourMillis)
        host.advanceAll(start + 6 * hourMillis)
        assertNotNull(host[a.id].state.pause)
        assertTrue(host.plannedAlarms(start + 6 * hourMillis).none { a.id in it.babies }, "no alarms for a paused baby")
        val stillRunning = host[b.id].state
        assertTrue(stillRunning.pause != null || stillRunning.meta.lastAdvancedTo.epochMillis == start + 6 * hourMillis)
    }

    @Test
    fun acceleratedTimeRunsFasterAndStaysContinuous() {
        val host = soloHost(a)
        host.advanceAll(start + hourMillis)
        val before = host[a.id].state.meta.lastAdvancedTo
        host.setSpeed(a.id, 4.0, start + hourMillis)
        assertEquals(before, host[a.id].state.meta.lastAdvancedTo, "changing speed does not jump time")
        host.advanceAll(start + 2 * hourMillis)
        assertEquals(
            before.epochMillis + 4 * hourMillis,
            host[a.id]
                .state.meta.lastAdvancedTo.epochMillis,
        )
        val time = host[a.id].time
        val sim = host[a.id].state.meta.lastAdvancedTo
        assertTrue(time.toSim(time.toWall(sim)) >= sim, "an alarm at toWall(t) finds the baby at or after t")
    }

    @Test
    fun careAlertsBecomePerBabyNotifications() {
        val host = twoBabyHost()
        var step = host.advanceAll(start)
        while (step.notifications.values.none { plan -> plan.posts.any { it.kind == NotificationKind.CARE_ALERT } }) {
            step = host.advanceAll(step.alarms.first().triggerAtWallMillis)
        }
        for ((id, plan) in step.notifications) {
            for (post in plan.posts) {
                assertEquals(id, post.babyId)
                assertEquals("baby:${id.value}", post.identity.tag)
                assertEquals(NotificationIds.identity(id, host[id].config.slot, post.kind), post.identity)
            }
        }
    }
}
