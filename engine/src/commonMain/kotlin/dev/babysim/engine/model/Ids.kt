package dev.babysim.engine.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/** Stable identity of one simulated baby (UUID string). Every baby-scoped object is keyed by it (CLAUDE.md rule 1). */
@Serializable
@JvmInline
value class BabyId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "BabyId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Small stable integer per baby, used only to derive collision-free OS alarm/notification ids
 * (`1000 + slot * 16 + kind`, docs/04 §11.2).
 */
@Serializable
@JvmInline
value class Slot(
    val value: Int,
) {
    init {
        require(value in 0..MAX) { "Slot must be in 0..$MAX, was $value" }
    }

    companion object {
        const val MAX: Int = 15
    }
}

/** A point in simulation time, as epoch milliseconds. Simulation time equals wall time at 1× speed. */
@Serializable
@JvmInline
value class SimInstant(
    val epochMillis: Long,
) : Comparable<SimInstant> {
    override fun compareTo(other: SimInstant): Int = epochMillis.compareTo(other.epochMillis)

    operator fun plus(duration: Duration): SimInstant = SimInstant(epochMillis + duration.inWholeMilliseconds)

    operator fun minus(duration: Duration): SimInstant = SimInstant(epochMillis - duration.inWholeMilliseconds)

    /** Elapsed milliseconds from [other] to this instant (negative if [other] is later). */
    fun millisSince(other: SimInstant): Long = epochMillis - other.epochMillis
}
