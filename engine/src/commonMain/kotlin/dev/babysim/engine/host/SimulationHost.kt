package dev.babysim.engine.host

import dev.babysim.engine.BehaviorEngine
import dev.babysim.engine.StepResult
import dev.babysim.engine.audio.AudioArbiter
import dev.babysim.engine.audio.AudioDirective
import dev.babysim.engine.audio.PlaybackPlan
import dev.babysim.engine.audio.audioCuesOf
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.state.BabyState
import kotlin.time.Duration.Companion.hours

/** One simulated baby as the host holds it: its own config, state, time mapping and predictions (rule 2). */
data class BabyInstance(
    val config: BabyConfig,
    val state: BabyState,
    val time: TimeMapping,
    val nextWakeAt: SimInstant?,
    val nextEventAt: SimInstant?,
    val audio: AudioDirective,
) {
    val id: BabyId get() = config.id
}

/** Everything one host call produced: per-baby results, and the plans for alarms, notifications and audio. */
data class HostStep(
    val results: Map<BabyId, StepResult>,
    val alarms: List<AlarmRequest>,
    val notifications: Map<BabyId, NotificationPlan>,
    val playback: PlaybackPlan,
)

/**
 * Owns N independent babies (docs/04 §11.1, CLAUDE.md rules 1–8). Every call takes wall-clock time explicitly,
 * so the host is deterministic and testable. There is no "current baby": callers name the baby they mean.
 *
 * One host per app process (owned by the app's container, M4). Not thread-safe: the app serializes calls.
 */
@Suppress("TooManyFunctions") // The public API is one method per host operation (create, advance, apply, …).
class SimulationHost(
    private val engine: BehaviorEngine,
    private val entitlements: EntitlementProvider,
) {
    private val instances = mutableMapOf<BabyId, BabyInstance>()

    /** All babies, ordered by slot. */
    fun babies(): List<BabyInstance> = instances.values.sortedBy { it.config.slot.value }

    operator fun get(id: BabyId): BabyInstance = instances[id] ?: throw NoSuchElementException("no baby $id")

    /** Creates a new baby at wall time [nowWall]. Fails if the entitlement or a unique key would be violated. */
    fun create(
        config: BabyConfig,
        nowWall: Long,
        speed: Double = 1.0,
    ): HostStep {
        val time = TimeMapping(speed, SimInstant(nowWall), nowWall)
        checkCanAdd(config)
        val result = engine.initialize(config, time.toSim(nowWall))
        instances[config.id] = instanceOf(config, time, result)
        return step(nowWall, mapOf(config.id to result))
    }

    /** Re-adds a persisted baby (process restart, docs/05 §3). Same checks as [create]. */
    fun restore(
        config: BabyConfig,
        state: BabyState,
        time: TimeMapping,
    ) {
        require(state.babyId == config.id) { "state belongs to ${state.babyId}, config to ${config.id}" }
        checkCanAdd(config)
        val result = engine.advance(config, state, state.meta.lastAdvancedTo)
        instances[config.id] = instanceOf(config, time, result)
    }

    fun remove(id: BabyId) {
        instances.remove(id)
    }

    /**
     * Catches every baby up to [nowWall] (an alarm fired, the app opened, the device rebooted). [predict] = false
     * skips the look-ahead (bulk simulation); the alarm plan then falls back to safety re-checks.
     */
    fun advanceAll(
        nowWall: Long,
        predict: Boolean = true,
    ): HostStep {
        val results =
            babies().associate { baby ->
                baby.id to engine.advance(baby.config, baby.state, baby.time.toSim(nowWall), predict)
            }
        results.forEach { (id, result) -> instances[id] = instanceOf(this[id].config, this[id].time, result) }
        return step(nowWall, results)
    }

    /** Applies a care action to one baby. Other babies are untouched (CLAUDE.md rule 1). */
    fun apply(
        id: BabyId,
        action: CareAction,
        nowWall: Long,
        predict: Boolean = true,
    ): HostStep = update(id, nowWall) { engine.apply(it.config, it.state, action, it.time.toSim(nowWall), predict) }

    fun pause(
        id: BabyId,
        nowWall: Long,
    ): HostStep = update(id, nowWall) { engine.pause(it.config, it.state, it.time.toSim(nowWall)) }

    fun resume(
        id: BabyId,
        nowWall: Long,
    ): HostStep = update(id, nowWall) { engine.resume(it.config, it.state, it.time.toSim(nowWall)) }

    /** Changes one baby's speed (Accelerated Mode), keeping its simulation time continuous. */
    fun setSpeed(
        id: BabyId,
        speed: Double,
        nowWall: Long,
    ): HostStep {
        val baby = this[id]
        val caughtUp = engine.advance(baby.config, baby.state, baby.time.toSim(nowWall))
        instances[id] = instanceOf(baby.config, baby.time.withSpeed(speed, nowWall), caughtUp)
        return step(nowWall, mapOf(id to caughtUp))
    }

    /** The alarms the device should hold now, for every baby. */
    fun plannedAlarms(nowWall: Long): List<AlarmRequest> =
        SchedulerPlanner.plan(
            babies().mapNotNull { baby ->
                when {
                    baby.state.pause != null -> null
                    baby.nextWakeAt != null ->
                        DueWake(baby.id, baby.config.slot, baby.time.toWall(baby.nextWakeAt), AlarmKind.CARE_ALERT)
                    else ->
                        DueWake(
                            baby.id,
                            baby.config.slot,
                            nowWall + SAFETY_RECHECK_MILLIS,
                            AlarmKind.SAFETY_RECHECK,
                        )
                }
            },
        )

    private fun update(
        id: BabyId,
        nowWall: Long,
        call: (BabyInstance) -> StepResult,
    ): HostStep {
        val baby = this[id]
        val result = call(baby)
        instances[id] = instanceOf(baby.config, baby.time, result)
        return step(nowWall, mapOf(id to result))
    }

    private fun step(
        nowWall: Long,
        results: Map<BabyId, StepResult>,
    ): HostStep =
        HostStep(
            results = results,
            alarms = plannedAlarms(nowWall),
            notifications = results.mapValues { (id, r) -> NotificationIds.plan(id, this[id].config.slot, r.events) },
            playback = AudioArbiter.plan(babies().map { it.audio }, results.values.flatMap { audioCuesOf(it.events) }),
        )

    private fun checkCanAdd(config: BabyConfig) {
        require(config.id !in instances) { "baby ${config.id} already exists" }
        require(instances.values.none { it.config.slot == config.slot }) { "slot ${config.slot.value} is taken" }
        require(instances.values.none { it.config.seed == config.seed }) { "seed is already used by another baby" }
        if (instances.size >=
            entitlements.maxActiveBabies
        ) {
            throw EntitlementExceededException(entitlements.maxActiveBabies)
        }
    }

    private fun instanceOf(
        config: BabyConfig,
        time: TimeMapping,
        result: StepResult,
    ) = BabyInstance(config, result.state, time, result.nextWakeAt, result.nextEventAt, result.audio)

    private companion object {
        val SAFETY_RECHECK_MILLIS = 6.hours.inWholeMilliseconds
    }
}
