package dev.babysim.engine.audio

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventType
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.model.VocalType
import dev.babysim.engine.state.BabyState
import kotlinx.serialization.Serializable

/**
 * What a baby sounds like (docs/04 §9). [priority] orders competing sounds across babies (CLAUDE.md rule 7):
 * intense crying > crying > fussing > settling > content sounds > sleep sounds > silence.
 */
@Serializable
enum class AudioCategory(
    val priority: Int,
) {
    SILENT(0),
    SLEEP(1),
    CONTENT_COO(2),
    BABBLE(2),
    GIGGLE(2),
    BURP(2),
    SETTLING(3),
    FUSS(4),
    CRY(5),
    CRY_INTENSE(6),
}

/**
 * The continuous sound one baby wants right now. The engine decides what the baby's state sounds like; the
 * [AudioArbiter] decides what actually plays across babies. [variationSeed] lets the player pick clips
 * without repeating, reproducibly.
 */
@Serializable
data class AudioDirective(
    val babyId: BabyId,
    val category: AudioCategory,
    /** 0 silent or asleep, 1 fussing or settling, 2 crying, 3 intense crying. */
    val intensity: Int,
    val startedAt: SimInstant,
    val variationSeed: Long,
)

/** A one-shot sound from an audible event: a coo, a babble, a burp, a sleep noise, a brief fuss. */
@Serializable
data class AudioCue(
    val babyId: BabyId,
    val at: SimInstant,
    val category: AudioCategory,
    val vocal: VocalType? = null,
)

private val SOOTHING = setOf(CareActionType.HOLD, CareActionType.ROCK, CareActionType.FEED)
private const val INTENSITY_FUSS = 1
private const val INTENSITY_CRY = 2
private const val INTENSITY_INTENSE = 3

/** Derives one baby's continuous audio from its state. Pure. */
internal fun audioDirectiveOf(
    state: BabyState,
    seed: Long,
): AudioDirective {
    val now = state.meta.lastAdvancedTo
    val beingSoothed = state.ongoingAction?.type in SOOTHING
    val (category, intensity) =
        when {
            state.pause != null -> AudioCategory.SILENT to 0
            state.activity == Activity.CRYING_INTENSE -> AudioCategory.CRY_INTENSE to INTENSITY_INTENSE
            state.activity == Activity.CRYING -> AudioCategory.CRY to INTENSITY_CRY
            state.activity == Activity.FUSSING && beingSoothed -> AudioCategory.SETTLING to INTENSITY_FUSS
            state.activity == Activity.FUSSING -> AudioCategory.FUSS to INTENSITY_FUSS
            state.activity in ASLEEP -> AudioCategory.SLEEP to 0
            else -> AudioCategory.SILENT to 0
        }
    val startedAt =
        when (category) {
            AudioCategory.CRY, AudioCategory.CRY_INTENSE -> state.cryingSince ?: now
            AudioCategory.SLEEP -> state.timestamps.lastSleepStartAt ?: now
            else -> now
        }
    return AudioDirective(state.babyId, category, intensity, startedAt, seed xor startedAt.epochMillis)
}

private val ASLEEP = setOf(Activity.ASLEEP_ACTIVE, Activity.ASLEEP_QUIET, Activity.EXHAUSTED_SLEEP)

/** One-shot sounds carried by audible events. */
fun audioCuesOf(events: List<BabyEvent>): List<AudioCue> =
    events.mapNotNull { event ->
        val category =
            when (event.type) {
                EventType.VOCALIZATION ->
                    when (event.vocal) {
                        VocalType.BABBLE, VocalType.BABBLE_REDUPLICATED -> AudioCategory.BABBLE
                        VocalType.GIGGLE -> AudioCategory.GIGGLE
                        else -> AudioCategory.CONTENT_COO
                    }
                EventType.SLEEP_NOISE -> AudioCategory.SLEEP
                EventType.BRIEF_FUSS -> AudioCategory.FUSS
                EventType.BURPED -> AudioCategory.BURP
                else -> null
            }
        category?.let { AudioCue(event.babyId, event.at, it, event.vocal) }
    }

/** What to play: at most one continuous sound (v1 has one output source) and the one-shot cues allowed over it. */
data class PlaybackPlan(
    val loop: AudioDirective?,
    val cues: List<AudioCue>,
    /** Directives that lost to [loop]; logged, not played (v1). Step 10 may mix or alternate instead. */
    val suppressed: List<AudioDirective>,
)

/**
 * Chooses what plays when several babies want to make sound at once (CLAUDE.md rule 7). Highest priority
 * wins; ties go to the sound that started first, then to the lower baby id, so the choice is deterministic.
 * A one-shot cue plays only if it is at least as important as the continuous sound playing.
 */
object AudioArbiter {
    fun plan(
        directives: Collection<AudioDirective>,
        cues: Collection<AudioCue> = emptyList(),
    ): PlaybackPlan {
        val audible = directives.filter { it.category != AudioCategory.SILENT }
        val loop =
            audible.minWithOrNull(
                compareByDescending<AudioDirective> { it.category.priority }
                    .thenBy { it.startedAt }
                    .thenBy { it.babyId.value },
            )
        val floor = loop?.category?.priority ?: 0
        val playable =
            cues.filter { it.category.priority >= floor }.sortedWith(
                compareBy({ it.at }, { it.babyId.value }),
            )
        return PlaybackPlan(loop, playable, audible.filter { it != loop })
    }
}
