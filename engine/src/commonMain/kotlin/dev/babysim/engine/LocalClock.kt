package dev.babysim.engine

import dev.babysim.engine.model.SimInstant

/**
 * Maps simulation time to local minute-of-day, for day/night rhythm and evening fussiness (docs/01 B7–B8).
 * Injected so the engine stays pure and deterministic. The app supplies one backed by the device time zone.
 */
fun interface LocalClock {
    /** Minutes after local midnight, 0 until 1440. */
    fun minuteOfDay(at: SimInstant): Int

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_DAY = 1440L

        /** Local time at a fixed offset from UTC (tests, harness). */
        fun fixedOffset(offsetMinutes: Int): LocalClock =
            LocalClock { at ->
                (at.epochMillis.floorDiv(MILLIS_PER_MINUTE) + offsetMinutes).mod(MINUTES_PER_DAY).toInt()
            }

        val UTC: LocalClock = fixedOffset(0)
    }
}
