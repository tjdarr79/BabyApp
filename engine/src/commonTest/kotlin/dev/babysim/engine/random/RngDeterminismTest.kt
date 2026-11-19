package dev.babysim.engine.random

import kotlin.test.Test
import kotlin.test.assertEquals

class RngDeterminismTest {
    @Test
    fun xoshiroMatchesReferenceVector() {
        // Reference: xoshiro256** 1.0 with state {1, 2, 3, 4} (computed from the published C reference algorithm).
        val rng = Xoshiro256StarStar(XoshiroState(1, 2, 3, 4))
        assertEquals(listOf(11520L, 0L, 1509978240L, 1215971899390074240L), List(4) { rng.nextLong() })
    }

    @Test
    fun splitMixMatchesReferenceVector() {
        // Reference: SplitMix64 seeded with 0 → 0xe220a8397b1dcdaf, 0x6e789e6aa1b965f4, 0x06c45d188009454f.
        val rng = SplitMix64(0)
        assertEquals(
            listOf(-2152535657050944081L, 7960286522194355700L, 487617019471545679L),
            List(3) { rng.nextLong() },
        )
    }

    @Test
    fun sameSeedAndStreamGiveIdenticalSequences() {
        val a = RngBank(RngBankState(seed = 42)).stream(RngStreamId.FEEDING)
        val b = RngBank(RngBankState(seed = 42)).stream(RngStreamId.FEEDING)
        assertEquals(List(1_000) { a.nextLong() }, List(1_000) { b.nextLong() })
    }

    @Test
    fun snapshotRestoreContinuesExactlyWhereItLeftOff() {
        val uninterrupted = RngBank(RngBankState(seed = 7))
        val expected = List(500) { uninterrupted.stream(RngStreamId.SLEEP).nextLong() }

        val first = RngBank(RngBankState(seed = 7))
        val head = List(200) { first.stream(RngStreamId.SLEEP).nextLong() }
        val resumed = RngBank(first.snapshot())
        val tail = List(300) { resumed.stream(RngStreamId.SLEEP).nextLong() }

        assertEquals(expected, head + tail)
    }

    @Test
    fun snapshotOnlyAdvancesStreamsThatWereUsed() {
        val bank = RngBank(RngBankState(seed = 9))
        bank.stream(RngStreamId.VOCAL).nextLong()
        assertEquals(setOf(RngStreamId.VOCAL), bank.snapshot().streams.keys)
    }

    @Test
    fun distributionHelpersAreDeterministic() {
        fun draws(seed: Long): List<Double> {
            val r = RngBank(RngBankState(seed)).stream(RngStreamId.THRESHOLDS)
            return List(50) { r.nextDouble() } +
                List(50) { r.nextExponential() } +
                List(50) { r.nextGaussian() } +
                List(50) { r.nextTruncatedNormal(10.0, 3.0, 5.0, 15.0) }
        }
        assertEquals(draws(123), draws(123))
    }
}
