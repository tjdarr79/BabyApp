package dev.babysim.engine.random

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RngStreamIndependenceTest {
    @Test
    fun streamCodesAreUnique() {
        val codes = RngStreamId.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "RngStreamId codes must be unique")
    }

    @Test
    fun differentStreamsOfTheSameSeedDiffer() {
        val bank = RngBank(RngBankState(seed = 1))
        val sequences = RngStreamId.entries.map { id -> List(16) { bank.stream(id).nextLong() } }
        assertEquals(sequences.size, sequences.toSet().size, "every stream must produce a distinct sequence")
    }

    @Test
    fun drawingFromOneStreamDoesNotAffectAnother() {
        val control = RngBank(RngBankState(seed = 99))
        val expected = List(100) { control.stream(RngStreamId.DIAPER_VOID).nextLong() }

        val busy = RngBank(RngBankState(seed = 99))
        repeat(10_000) { busy.stream(RngStreamId.UNEXPLAINED_FUSS).nextLong() }
        val actual = List(100) { busy.stream(RngStreamId.DIAPER_VOID).nextLong() }

        assertEquals(expected, actual)
    }

    @Test
    fun differentSeedsDiffer() {
        val a = RngBank(RngBankState(seed = 1)).stream(RngStreamId.FEEDING)
        val b = RngBank(RngBankState(seed = 2)).stream(RngStreamId.FEEDING)
        assertNotEquals(List(16) { a.nextLong() }, List(16) { b.nextLong() })
    }

    @Test
    fun streamsAreNotShiftedCopiesOfEachOther() {
        // Guards against naive seed derivation where stream k is stream 0 advanced by k steps.
        val bank = RngBank(RngBankState(seed = 5))
        val a = List(256) { bank.stream(RngStreamId.THRESHOLDS).nextLong() }
        val b = List(256) { bank.stream(RngStreamId.FEEDING).nextLong() }
        val overlap = a.toSet().intersect(b.toSet())
        assertTrue(overlap.isEmpty(), "streams share outputs: $overlap")
    }

    @Test
    fun uniformAndExponentialMomentsAreSane() {
        val r = RngBank(RngBankState(seed = 2026)).stream(RngStreamId.VOCAL)
        val n = 100_000
        val uniformMean = (1..n).sumOf { r.nextDouble() } / n
        val expMean = (1..n).sumOf { r.nextExponential() } / n
        assertTrue(abs(uniformMean - 0.5) < 0.01, "uniform mean was $uniformMean")
        assertTrue(abs(expMean - 1.0) < 0.02, "exponential mean was $expMean")
    }

    @Test
    fun uniformDrawsStayInUnitInterval() {
        val r = RngBank(RngBankState(seed = -1)).stream(RngStreamId.SOOTHE)
        repeat(100_000) {
            val d = r.nextDouble()
            assertTrue(d >= 0.0 && d < 1.0, "draw out of [0,1): $d")
        }
    }

    @Test
    fun truncatedNormalStaysWithinBounds() {
        val r = RngBank(RngBankState(seed = 3)).stream(RngStreamId.THRESHOLDS)
        repeat(10_000) {
            val x = r.nextTruncatedNormal(mean = 180.0, sd = 60.0, min = 150.0, max = 240.0)
            assertTrue(x in 150.0..240.0, "out of bounds: $x")
        }
    }
}
