package dev.babysim.harness

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.BehaviorEngine
import dev.babysim.engine.caregiver.CaregiverDriver
import dev.babysim.engine.caregiver.CaregiverPolicy
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.params.ParamSet
import dev.babysim.engine.state.BabyState

/** One simulated baby looked after by one simulated caregiver for a number of days. */
data class RunResult(
    val config: BabyConfig,
    val policy: String,
    val start: SimInstant,
    val end: SimInstant,
    val events: List<BabyEvent>,
    val finalState: BabyState,
)

/** Runs simulated babies with simulated caregivers on one engine and parameter set. */
class Simulation(
    private val engine: BehaviorEngine,
    private val params: ParamSet,
) {
    /** The caregiver looks at the baby every [stepMinutes] and does at most one thing each time. */
    fun run(
        config: BabyConfig,
        policy: CaregiverPolicy,
        days: Int,
        stepMinutes: Int = 2,
    ): RunResult {
        val driver = CaregiverDriver(policy, params)
        val end = SimInstant(START.epochMillis + days * MILLIS_PER_DAY)
        val step = stepMinutes * MILLIS_PER_MINUTE
        var state = engine.initialize(config, START).state
        val events = mutableListOf<BabyEvent>()
        var t = START.epochMillis
        while (t < end.epochMillis) {
            t = minOf(t + step, end.epochMillis)
            val advanced = engine.advance(config, state, SimInstant(t), predict = false)
            state = advanced.state
            events += advanced.events
            val action = driver.decide(config, state) ?: continue
            val acted = engine.apply(config, state, action, SimInstant(t), predict = false)
            state = acted.state
            events += acted.events
        }
        return RunResult(config, policy.name, START, end, events, state)
    }

    companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_DAY = 86_400_000L
        private const val START_HOUR_LOCAL = 7L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val ANY_RECENT_DAY_MILLIS = 1_790_000_000_000L

        /** Runs start at 07:00 local (UTC in the harness) so day and night windows line up with whole days. */
        val START: SimInstant =
            SimInstant((ANY_RECENT_DAY_MILLIS / MILLIS_PER_DAY) * MILLIS_PER_DAY + START_HOUR_LOCAL * MILLIS_PER_HOUR)
    }
}
