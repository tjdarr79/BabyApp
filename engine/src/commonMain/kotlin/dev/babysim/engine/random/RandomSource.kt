package dev.babysim.engine.random

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/** Minimal deterministic 64-bit source. All engine randomness flows through this (CLAUDE.md engine rules). */
interface RandomSource {
    fun nextLong(): Long
}

private const val DOUBLE_MANTISSA_SHIFT = 11
private const val DOUBLE_UNIT = 1.0 / (1L shl 53)
private const val MAX_TRUNCATION_TRIES = 64
private const val BOX_MULLER_LOG_FACTOR = -2.0
private const val TWO_PI = 2.0 * PI

/** Uniform in [0, 1). */
fun RandomSource.nextDouble(): Double = (nextLong() ushr DOUBLE_MANTISSA_SHIFT) * DOUBLE_UNIT

/** Exponential with rate 1: the "hazard budget" draw (docs/04 §7.1). Always > 0. */
fun RandomSource.nextExponential(): Double = -ln(1.0 - nextDouble())

/** Standard normal via Box–Muller. Uses exactly two draws and caches no spare, so state stays serializable. */
fun RandomSource.nextGaussian(): Double {
    val u1 = 1.0 - nextDouble() // (0, 1]
    val u2 = nextDouble()
    return sqrt(BOX_MULLER_LOG_FACTOR * ln(u1)) * cos(TWO_PI * u2)
}

/**
 * Normal(mean, sd) truncated to [min, max] by rejection. After [MAX_TRUNCATION_TRIES] rejections the last
 * draw is clamped, so the call always terminates after a bounded number of draws.
 */
fun RandomSource.nextTruncatedNormal(
    mean: Double,
    sd: Double,
    min: Double,
    max: Double,
): Double {
    require(min <= max) { "min must be <= max" }
    require(sd >= 0.0) { "sd must be >= 0" }
    var candidate = mean
    repeat(MAX_TRUNCATION_TRIES) {
        candidate = mean + sd * nextGaussian()
        if (candidate in min..max) return candidate
    }
    return candidate.coerceIn(min, max)
}

/** Uniform in [min, max). */
fun RandomSource.nextUniform(
    min: Double,
    max: Double,
): Double {
    require(min <= max) { "min must be <= max" }
    return min + (max - min) * nextDouble()
}

/** True with probability [p] (clamped to [0, 1]). */
fun RandomSource.nextBernoulli(p: Double): Boolean = nextDouble() < p.coerceIn(0.0, 1.0)
