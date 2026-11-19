package dev.babysim.engine.random

import kotlinx.serialization.Serializable

private const val GOLDEN_GAMMA: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
private const val MIX_MUL_1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
private const val MIX_MUL_2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB
private const val MIX_SHIFT_1 = 30
private const val MIX_SHIFT_2 = 27
private const val MIX_SHIFT_3 = 31

private const val XOSHIRO_SHIFT = 17
private const val XOSHIRO_ROT_STATE = 45
private const val XOSHIRO_ROT_OUT = 7
private const val XOSHIRO_MUL_IN = 5L
private const val XOSHIRO_MUL_OUT = 9L

/** SplitMix64 finalizer: a strong 64-bit bit mixer. */
internal fun mix64(input: Long): Long {
    var z = input
    z = (z xor (z ushr MIX_SHIFT_1)) * MIX_MUL_1
    z = (z xor (z ushr MIX_SHIFT_2)) * MIX_MUL_2
    return z xor (z ushr MIX_SHIFT_3)
}

/** SplitMix64 (Steele, Lea, Flood). Used only to expand seeds into xoshiro state. */
class SplitMix64(
    private var state: Long,
) : RandomSource {
    override fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return mix64(state)
    }
}

/** Serializable xoshiro256** state. */
@Serializable
data class XoshiroState(
    val s0: Long,
    val s1: Long,
    val s2: Long,
    val s3: Long,
) {
    init {
        require(s0 != 0L || s1 != 0L || s2 != 0L || s3 != 0L) { "xoshiro state must not be all zero" }
    }
}

/**
 * xoshiro256** 1.0 (Blackman & Vigna). Portable and fast, and its state is four longs, so it serializes
 * exactly. `kotlin.random.Random`'s state is not serializable (docs/04 §7.2).
 */
class Xoshiro256StarStar(
    initial: XoshiroState,
) : RandomSource {
    private var s0 = initial.s0
    private var s1 = initial.s1
    private var s2 = initial.s2
    private var s3 = initial.s3

    override fun nextLong(): Long {
        val result = (s1 * XOSHIRO_MUL_IN).rotateLeft(XOSHIRO_ROT_OUT) * XOSHIRO_MUL_OUT
        val t = s1 shl XOSHIRO_SHIFT
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = s3.rotateLeft(XOSHIRO_ROT_STATE)
        return result
    }

    fun state(): XoshiroState = XoshiroState(s0, s1, s2, s3)

    companion object {
        /** Deterministically derives an independent stream from a baby seed and a stream code. */
        fun seeded(
            seed: Long,
            streamCode: Long,
        ): Xoshiro256StarStar {
            val expander = SplitMix64(seed xor mix64(streamCode))
            return Xoshiro256StarStar(
                XoshiroState(expander.nextLong(), expander.nextLong(), expander.nextLong(), expander.nextLong()),
            )
        }
    }
}
