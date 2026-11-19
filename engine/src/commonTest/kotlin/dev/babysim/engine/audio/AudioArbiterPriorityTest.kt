package dev.babysim.engine.audio

import dev.babysim.engine.EngineFixtures
import dev.babysim.engine.EngineFixtures.T0
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CLAUDE.md rule 7: competing sounds from several babies resolve by priority, deterministically. */
class AudioArbiterPriorityTest {
    private val a = BabyId("a")
    private val b = BabyId("b")

    private fun directive(
        baby: BabyId,
        category: AudioCategory,
        startedAt: Long = 0,
    ) = AudioDirective(baby, category, intensity = 0, startedAt = SimInstant(startedAt), variationSeed = 1)

    @Test
    fun priorityOrderMatchesTheRule() {
        val order =
            listOf(
                AudioCategory.CRY_INTENSE,
                AudioCategory.CRY,
                AudioCategory.FUSS,
                AudioCategory.SETTLING,
                AudioCategory.CONTENT_COO,
                AudioCategory.SLEEP,
                AudioCategory.SILENT,
            )
        assertEquals(order, order.sortedByDescending { it.priority })
        assertTrue(order.zipWithNext().all { (louder, quieter) -> louder.priority > quieter.priority })
    }

    @Test
    fun theHighestPriorityBabyIsHeard() {
        for ((louder, quieter) in AudioCategory.entries.flatMap { x -> AudioCategory.entries.map { x to it } }) {
            if (louder.priority <= quieter.priority || louder == AudioCategory.SILENT) continue
            for ((first, second) in listOf(a to b, b to a)) {
                val plan = AudioArbiter.plan(listOf(directive(first, louder), directive(second, quieter)))
                assertEquals(first, plan.loop?.babyId, "$louder beats $quieter")
                assertEquals(louder, plan.loop?.category)
            }
        }
    }

    @Test
    fun tiesGoToTheEarlierSoundThenTheLowerId() {
        val earlierB =
            AudioArbiter.plan(
                listOf(directive(a, AudioCategory.CRY, 200), directive(b, AudioCategory.CRY, 100)),
            )
        assertEquals(b, earlierB.loop?.babyId)
        val sameTime =
            AudioArbiter.plan(
                listOf(directive(b, AudioCategory.CRY, 100), directive(a, AudioCategory.CRY, 100)),
            )
        assertEquals(a, sameTime.loop?.babyId)
        assertEquals(listOf(b), sameTime.suppressed.map { it.babyId })
    }

    @Test
    fun silenceIsNotPlayed() {
        val plan = AudioArbiter.plan(listOf(directive(a, AudioCategory.SILENT), directive(b, AudioCategory.SILENT)))
        assertNull(plan.loop)
        assertTrue(plan.suppressed.isEmpty())
    }

    @Test
    fun oneShotsPlayOnlyOverQuieterSounds() {
        val coo = AudioCue(b, SimInstant(5), AudioCategory.CONTENT_COO)
        val overSleep = AudioArbiter.plan(listOf(directive(a, AudioCategory.SLEEP)), listOf(coo))
        assertEquals(listOf(coo), overSleep.cues)
        val overCrying = AudioArbiter.plan(listOf(directive(a, AudioCategory.CRY)), listOf(coo))
        assertTrue(overCrying.cues.isEmpty(), "a coo is not heard over crying")
    }

    @Test
    fun theEngineDescribesEachStateAsASound() {
        val engine = EngineFixtures.engine()
        val config = EngineFixtures.config()
        val base = engine.initialize(config, T0)
        assertEquals(AudioCategory.SILENT, base.audio.category)

        fun soundOf(
            activity: Activity,
            soothing: Boolean = false,
        ): AudioCategory {
            var state = base.state.copy(activity = activity)
            if (soothing) {
                state =
                    engine.apply(config, state, CareAction.Start(CareActionType.ROCK), T0, predict = false).state
            }
            return audioDirectiveOf(state.copy(activity = activity), config.seed).category
        }
        assertEquals(AudioCategory.CRY_INTENSE, soundOf(Activity.CRYING_INTENSE))
        assertEquals(AudioCategory.CRY, soundOf(Activity.CRYING))
        assertEquals(AudioCategory.FUSS, soundOf(Activity.FUSSING))
        assertEquals(AudioCategory.SETTLING, soundOf(Activity.FUSSING, soothing = true))
        assertEquals(AudioCategory.SLEEP, soundOf(Activity.ASLEEP_QUIET))
        assertEquals(AudioCategory.SILENT, soundOf(Activity.AWAKE_CALM))
        assertEquals(config.id, base.audio.babyId)
    }
}
