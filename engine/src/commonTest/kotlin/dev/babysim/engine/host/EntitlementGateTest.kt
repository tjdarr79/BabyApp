package dev.babysim.engine.host

import dev.babysim.engine.EngineFixtures
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.AgeBandId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** CLAUDE.md rule 8: `maxActiveBabies` is the only gate; v1 allows one. */
class EntitlementGateTest {
    private val now = T0.epochMillis
    private val first = EngineFixtures.config(AgeBandId.NEWBORN, seed = 1, id = "a", slot = 0)
    private val second = EngineFixtures.config(AgeBandId.M7_9, seed = 2, id = "b", slot = 1)
    private val third = EngineFixtures.config(AgeBandId.M4_6, seed = 3, id = "c", slot = 2)

    private class TestEntitlements(
        override val maxActiveBabies: Int,
    ) : EntitlementProvider

    @Test
    fun v1AllowsExactlyOneBaby() {
        assertEquals(1, V1Entitlements.maxActiveBabies)
        val host = SimulationHost(EngineFixtures.engine(), V1Entitlements)
        host.create(first, now)
        val e = assertFailsWith<EntitlementExceededException> { host.create(second, now) }
        assertEquals(1, e.max)
        assertEquals(listOf(first.id), host.babies().map { it.id })
    }

    @Test
    fun theSameHostRunsTwoBabiesWhenTheEntitlementAllows() {
        val host = SimulationHost(EngineFixtures.engine(), TestEntitlements(2))
        host.create(first, now)
        host.create(second, now)
        assertFailsWith<EntitlementExceededException> { host.create(third, now) }
        assertEquals(listOf(first.id, second.id), host.babies().map { it.id })
    }

    @Test
    fun removingABabyFreesItsPlace() {
        val host = SimulationHost(EngineFixtures.engine(), V1Entitlements)
        host.create(first, now)
        host.remove(first.id)
        host.create(second, now)
        assertEquals(listOf(second.id), host.babies().map { it.id })
    }

    @Test
    fun idsSlotsAndSeedsMustBeUnique() {
        val host = SimulationHost(EngineFixtures.engine(), TestEntitlements(4))
        host.create(first, now)
        assertFailsWith<IllegalArgumentException> { host.create(first, now) }
        assertFailsWith<IllegalArgumentException> { host.create(second.copy(slot = first.slot), now) }
        assertFailsWith<IllegalArgumentException> { host.create(second.copy(seed = first.seed), now) }
    }

    @Test
    fun restoringRespectsTheGateToo() {
        val source = SimulationHost(EngineFixtures.engine(), TestEntitlements(2))
        source.create(first, now)
        source.create(second, now)
        val restored = SimulationHost(EngineFixtures.engine(), V1Entitlements)
        val (a, b) = source.babies()
        restored.restore(a.config, a.state, a.time)
        assertFailsWith<EntitlementExceededException> { restored.restore(b.config, b.state, b.time) }
    }
}
