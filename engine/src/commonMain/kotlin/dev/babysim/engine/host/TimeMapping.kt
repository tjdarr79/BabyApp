package dev.babysim.engine.host

import dev.babysim.engine.model.SimInstant
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Maps wall-clock time to one baby's simulation time (docs/04 §11.5). At [speed] 1 they are equal (Normal
 * Care); Accelerated Mode runs faster. Per baby, so babies could run at different speeds.
 */
@Serializable
data class TimeMapping(
    val speed: Double,
    val simAnchor: SimInstant,
    val wallAnchorMillis: Long,
) {
    init {
        require(speed > 0.0) { "speed must be positive" }
    }

    /** Simulation time at wall time [wallMillis] (rounded down, so the simulation never runs ahead). */
    fun toSim(wallMillis: Long): SimInstant =
        SimInstant(simAnchor.epochMillis + floor((wallMillis - wallAnchorMillis) * speed).toLong())

    /** The earliest wall time at which simulation time has reached [sim] (rounded up). */
    fun toWall(sim: SimInstant): Long = wallAnchorMillis + ceil(sim.millisSince(simAnchor) / speed).toLong()

    /** Same simulation time at [wallMillis], continuing at [newSpeed] from there. */
    fun withSpeed(
        newSpeed: Double,
        wallMillis: Long,
    ): TimeMapping = TimeMapping(newSpeed, toSim(wallMillis), wallMillis)
}
