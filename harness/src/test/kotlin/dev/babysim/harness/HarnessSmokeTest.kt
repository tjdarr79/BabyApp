package dev.babysim.harness

import kotlin.test.Test
import kotlin.test.assertTrue

class HarnessSmokeTest {
    @Test
    fun bannerReportsEngineSchemaVersion() {
        assertTrue(banner().contains("engine schema v1"))
    }
}
