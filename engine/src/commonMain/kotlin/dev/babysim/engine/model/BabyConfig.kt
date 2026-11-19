package dev.babysim.engine.model

import kotlinx.serialization.Serializable

/** Per-baby configuration. One instance per baby; nothing here is shared between babies. */
@Serializable
data class BabyConfig(
    val id: BabyId,
    val slot: Slot,
    val ageBand: AgeBandId,
    val temperament: TemperamentId,
    /** Root seed for this baby's RNG streams. Unique per baby (docs/05 `ux_babies_seed`). */
    val seed: Long,
    /** Local "night" start, minutes after midnight (docs/01 B7). */
    val nightStartLocalMinutes: Int = DEFAULT_NIGHT_START,
    /** Local "night" end, minutes after midnight. */
    val nightEndLocalMinutes: Int = DEFAULT_NIGHT_END,
) {
    init {
        require(nightStartLocalMinutes in 0 until MINUTES_PER_DAY) { "nightStartLocalMinutes out of range" }
        require(nightEndLocalMinutes in 0 until MINUTES_PER_DAY) { "nightEndLocalMinutes out of range" }
    }

    companion object {
        const val MINUTES_PER_DAY: Int = 24 * 60
        const val DEFAULT_NIGHT_START: Int = 19 * 60
        const val DEFAULT_NIGHT_END: Int = 7 * 60
    }
}
