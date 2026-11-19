package dev.babysim.engine

import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.state.BabyState

/**
 * Result of one engine call for one baby.
 *
 * [nextEventAt] is exactly when the next event of any kind fires if nobody acts, and [nextWakeAt] when the next
 * alert-worthy event fires ([EventType.alerts]). Both are null when the baby is paused, when look-ahead was
 * skipped, or when nothing happens within the look-ahead horizon (the host then schedules a safety re-check).
 * The audio directive (docs/04 §9) is added with the audio arbiter in M2.5.
 */
data class StepResult(
    val state: BabyState,
    val events: List<BabyEvent>,
    val nextEventAt: SimInstant?,
    val nextWakeAt: SimInstant?,
)
