package dev.babysim.engine.random

import kotlinx.serialization.Serializable

/**
 * One RNG stream per stochastic process, so adding a process later does not perturb the draws of
 * existing ones (docs/04 §7.2). [code] values feed seed derivation and must never change or be reused.
 */
@Serializable
enum class RngStreamId(
    val code: Long,
) {
    THRESHOLDS(1),
    FEEDING(2),
    SLEEP(3),
    AROUSAL(4),
    DIAPER_VOID(5),
    DIAPER_STOOL(6),
    UNEXPLAINED_FUSS(7),
    VOCAL(8),
    SOOTHE(9),
    BURP(10),
    CARE_REQUEST(11),
}

/** Persisted RNG state for one baby: the root seed plus every stream that has been used. */
@Serializable
data class RngBankState(
    val seed: Long,
    val streams: Map<RngStreamId, XoshiroState> = emptyMap(),
)

/**
 * Mutable working copy of one baby's RNG streams for the duration of one engine step. Streams are created
 * lazily from the seed. [snapshot] captures their exact positions back into [RngBankState].
 * Never shared between babies.
 */
class RngBank(
    private val persisted: RngBankState,
) {
    private val live = mutableMapOf<RngStreamId, Xoshiro256StarStar>()

    fun stream(id: RngStreamId): RandomSource =
        live.getOrPut(id) {
            persisted.streams[id]?.let(::Xoshiro256StarStar) ?: Xoshiro256StarStar.seeded(persisted.seed, id.code)
        }

    fun snapshot(): RngBankState = persisted.copy(streams = persisted.streams + live.mapValues { it.value.state() })
}
