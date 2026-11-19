package dev.babysim.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineSmokeTest {
    @Test
    fun engineSchemaVersionStartsAtOne() {
        assertEquals(1, EngineInfo.ENGINE_SCHEMA_VERSION)
    }
}
