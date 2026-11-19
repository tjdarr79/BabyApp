package dev.babysim.engine.caregiver

import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.CareAction
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.params.ParamSet
import dev.babysim.engine.random.RandomSource
import dev.babysim.engine.random.SplitMix64
import dev.babysim.engine.random.nextDouble
import dev.babysim.engine.random.nextUniform
import dev.babysim.engine.state.BabyState
import dev.babysim.engine.state.RecordedAction
import kotlin.time.Duration.Companion.minutes

/**
 * Simulated caregivers for the harness and the two-baby proof (docs/04 §10). Not used by the app.
 *
 * A caregiver sees only what a real one could: whether the baby is crying, asleep or calm, whether it needs
 * attention, what is in progress, when it last fed, slept and woke, and what recent actions achieved. Never the
 * hidden causes. Policies are stateless: every decision is a pure function of the baby's state, the time, and
 * a random source seeded from the baby's seed and that time, so a run can be saved and resumed exactly.
 */
data class CaregiverView(
    val now: SimInstant,
    val activity: Activity,
    val paused: Boolean,
    val careRequestOpenedAt: SimInstant?,
    val ongoingType: CareActionType?,
    val ongoingSince: SimInstant?,
    val lastFeedEndAt: SimInstant?,
    val lastSleepStartAt: SimInstant?,
    val lastWakeAt: SimInstant?,
    val lastInteractionAt: SimInstant?,
    val recentActions: List<RecordedAction>,
    /** Age guidance a caregiver would know (docs/06): typical feed interval and when play starts to matter. */
    val typicalFeedIntervalMinutes: Double,
    val boredAfterMinutes: Double?,
) {
    val asleep: Boolean get() = activity in ASLEEP
    val upset: Boolean get() = activity in UPSET

    fun minutesSince(t: SimInstant?): Double =
        t?.let { now.millisSince(it) / MILLIS_PER_MINUTE } ?: Double.POSITIVE_INFINITY

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000.0
        private val ASLEEP = setOf(Activity.ASLEEP_ACTIVE, Activity.ASLEEP_QUIET, Activity.EXHAUSTED_SLEEP)
        private val UPSET = setOf(Activity.FUSSING, Activity.CRYING, Activity.CRYING_INTENSE)

        fun of(
            state: BabyState,
            config: BabyConfig,
            params: ParamSet,
        ): CaregiverView {
            val age = params.age(config.ageBand)
            return CaregiverView(
                now = state.meta.lastAdvancedTo,
                activity = state.activity,
                paused = state.pause != null,
                careRequestOpenedAt = state.careRequest?.openedAt,
                ongoingType = state.ongoingAction?.type,
                ongoingSince = state.ongoingAction?.startedAt,
                lastFeedEndAt = state.timestamps.lastFeedEndAt,
                lastSleepStartAt = state.timestamps.lastSleepStartAt,
                lastWakeAt = state.timestamps.lastWakeAt,
                lastInteractionAt = state.lastInteractionAt,
                recentActions = state.recentActions,
                typicalFeedIntervalMinutes = age.feedIntervalMinutes.value.typical,
                boredAfterMinutes = age.boredAfterMinutes.value?.typical,
            )
        }
    }
}

/** How a simulated caregiver responds. */
interface CaregiverPolicy {
    val name: String

    /** Minutes between a care request opening and the caregiver starting to respond. */
    fun responseDelayMinutes(rng: RandomSource): Double

    /** The next action now, or null to wait. Called only once the caregiver is responding (see [CaregiverDriver]). */
    fun decide(
        view: CaregiverView,
        rng: RandomSource,
    ): CareAction?

    companion object {
        val ALL: List<CaregiverPolicy> = listOf(AttentiveCaregiver, DelayedCaregiver, AbsentCaregiver, RandomCaregiver)

        fun named(name: String): CaregiverPolicy =
            ALL.firstOrNull { it.name == name } ?: throw IllegalArgumentException("unknown policy '$name'")
    }
}

/** Applies a policy to one baby at one instant. Stateless; safe to share between babies. */
class CaregiverDriver(
    private val policy: CaregiverPolicy,
    private val params: ParamSet,
) {
    fun decide(
        config: BabyConfig,
        state: BabyState,
    ): CareAction? {
        val view = CaregiverView.of(state, config, params)
        val waiting = view.paused || notYetResponding(config, view)
        return if (waiting) null else policy.decide(view, rngFor(config.seed, view.now.epochMillis, SALT_DECIDE))
    }

    /** A care request is open but the caregiver has not reached the baby yet. */
    private fun notYetResponding(
        config: BabyConfig,
        view: CaregiverView,
    ): Boolean {
        val openedAt = view.careRequestOpenedAt?.takeIf { view.ongoingType == null } ?: return false
        val delay = policy.responseDelayMinutes(rngFor(config.seed, openedAt.epochMillis, SALT_DELAY))
        return view.minutesSince(openedAt) < delay
    }

    private fun rngFor(
        seed: Long,
        at: Long,
        salt: Long,
    ): RandomSource = SplitMix64(seed xor (at * GOLDEN) xor salt)

    private companion object {
        const val GOLDEN = -0x61c8864680b583ebL
        const val SALT_DELAY = 0x5EED_DE1AL
        const val SALT_DECIDE = 0x5EED_DEC1L
    }
}

/**
 * A parent who is nearby: hears fussing and responds on the next check, and responds to a care request within
 * 1–3 minutes. Works through what a careful parent would try, in order: feed if it has been a while, check the
 * diaper and change it if needed, burp after a feed, play with an older baby who has been left alone, then
 * rock. Stops a feed the baby is crying through, burps partway through a feed and after it, puts a baby down
 * once asleep in arms, and plays with an older baby who is awake and calm.
 */
object AttentiveCaregiver : CaregiverPolicy {
    override val name = "attentive"

    private const val MIN_DELAY = 1.0
    private const val MAX_DELAY = 3.0
    private const val FEED_DUE_FRACTION = 0.6
    private const val REFEED_FRACTION = 0.4
    private const val BURP_WINDOW_MINUTES = 60.0
    private const val AFTER_FEED_BURP_MINUTES = 5.0
    private const val MAX_BURPS = 2
    private const val ROCK_BEFORE_REFEED_MINUTES = 25.0
    private const val PARTIAL_REFEED_ROCK_MINUTES = 6.0
    private const val ASLEEP_IN_ARMS_MINUTES = 5.0
    private const val SETTLED_HOLD_MINUTES = 10.0
    private const val PLAY_MINUTES = 15.0
    private const val PLAY_AT_BOREDOM_FRACTION = 0.7
    private const val MID_FEED_BURP_MINUTES = 7.0
    private const val CRYING_THROUGH_FEED_MINUTES = 8.0
    private const val EPISODE_MEMORY_MINUTES = 30.0

    override fun responseDelayMinutes(rng: RandomSource): Double = rng.nextUniform(MIN_DELAY, MAX_DELAY)

    override fun decide(
        view: CaregiverView,
        rng: RandomSource,
    ): CareAction? =
        when {
            view.asleep -> whileAsleep(view)
            view.ongoingType == CareActionType.FEED -> duringFeed(view)
            view.careRequestOpenedAt != null || view.upset -> soothe(view)
            else -> calmCare(view)
        }

    /**
     * Puts down a baby asleep in arms. If an alert is still open (the baby cried itself to sleep), wakes it for a
     * feed when one is due, otherwise checks on it without waking it, and changes a diaper that check found wet.
     */
    private fun whileAsleep(view: CaregiverView): CareAction? {
        val inArms = view.ongoingType == CareActionType.HOLD || view.ongoingType == CareActionType.ROCK
        val e = Episode(view)
        val changeNeeded = e.diaperNeedsChange && e.untried(CareActionType.CHANGE_DIAPER)
        return when {
            inArms && view.minutesSince(view.lastSleepStartAt) >= ASLEEP_IN_ARMS_MINUTES ->
                CareAction.Do(CareActionType.PUT_DOWN_TO_SLEEP)
            view.ongoingType != null -> null
            changeNeeded -> CareAction.Do(CareActionType.CHANGE_DIAPER)
            view.careRequestOpenedAt == null -> null
            e.feedDue -> CareAction.Start(CareActionType.FEED)
            else -> CareAction.Do(CareActionType.CHECK_DIAPER)
        }
    }

    /** Burps partway through a feed; stops a feed the baby keeps crying through (something else is wrong). */
    private fun duringFeed(view: CaregiverView): CareAction? {
        val feeding = view.minutesSince(view.ongoingSince)
        val burpedThisFeed =
            view.recentActions.any {
                it.action == CareActionType.BURP &&
                    it.at >= (view.ongoingSince ?: view.now)
            }
        val crying = view.activity == Activity.CRYING || view.activity == Activity.CRYING_INTENSE
        return when {
            crying && feeding >= CRYING_THROUGH_FEED_MINUTES -> CareAction.Stop
            feeding >= MID_FEED_BURP_MINUTES && !burpedThisFeed -> CareAction.Do(CareActionType.BURP)
            else -> null
        }
    }

    /**
     * Works down the list of things to try in this episode; each is tried once (burping up to twice). An
     * episode is the open care request, or the last half hour when the baby is fussing without one.
     */
    private fun soothe(view: CaregiverView): CareAction? {
        val e = Episode(view)
        return listOf(
            // The diaper check is instant and rules something out before a feed starts.
            e.untried(CareActionType.CHECK_DIAPER) to CareAction.Do(CareActionType.CHECK_DIAPER),
            (e.diaperNeedsChange && e.untried(CareActionType.CHANGE_DIAPER)) to
                CareAction.Do(CareActionType.CHANGE_DIAPER),
            (e.untried(CareActionType.FEED) && e.feedDue) to CareAction.Start(CareActionType.FEED),
            e.burpWorthTrying to CareAction.Do(CareActionType.BURP),
            (wantsPlay(view) && e.untried(CareActionType.PLAY)) to CareAction.Start(CareActionType.PLAY),
            !e.inArms to CareAction.Start(CareActionType.ROCK),
            e.refeed to CareAction.Start(CareActionType.FEED),
        ).firstOrNull { it.first }?.second
    }

    /** What the caregiver knows about the current episode: what was tried and how long since the last feed. */
    private class Episode(
        view: CaregiverView,
    ) {
        private val episodeStart =
            (view.now - EPISODE_MEMORY_MINUTES.minutes).let { recent ->
                view.careRequestOpenedAt?.let { minOf(it, recent) }
                    ?: recent
            }
        private val tried = view.recentActions.filter { it.at >= episodeStart }
        private val sinceFeed = view.minutesSince(view.lastFeedEndAt)
        private val lastCheck = tried.lastOrNull { it.action == CareActionType.CHECK_DIAPER }?.outcome

        // A feed the caregiver stopped early was partial: offering again soon is reasonable.
        private val lastFeedPartial =
            view.recentActions.lastOrNull { it.action == CareActionType.FEED }?.outcome == ActionOutcome.STOPPED
        private val rockingFor =
            if (view.ongoingType ==
                CareActionType.ROCK
            ) {
                view.minutesSince(view.ongoingSince)
            } else {
                0.0
            }
        private val refeedAfter = if (lastFeedPartial) PARTIAL_REFEED_ROCK_MINUTES else ROCK_BEFORE_REFEED_MINUTES

        val diaperNeedsChange = lastCheck == ActionOutcome.DIAPER_WET || lastCheck == ActionOutcome.DIAPER_SOILED
        val feedDue = sinceFeed >= FEED_DUE_FRACTION * view.typicalFeedIntervalMinutes
        val burpWorthTrying = sinceFeed <= BURP_WINDOW_MINUTES && count(CareActionType.BURP) < MAX_BURPS
        val inArms = view.ongoingType == CareActionType.ROCK || view.ongoingType == CareActionType.HOLD
        val refeed =
            rockingFor >= refeedAfter &&
                count(CareActionType.FEED) < 2 &&
                (lastFeedPartial || sinceFeed >= REFEED_FRACTION * view.typicalFeedIntervalMinutes)

        fun count(type: CareActionType): Int = tried.count { it.action == type }

        fun untried(type: CareActionType): Boolean = count(type) == 0
    }

    /** An older baby who has been without play or contact for its boredom time. */
    private fun wantsPlay(view: CaregiverView): Boolean {
        val boredAfter = view.boredAfterMinutes ?: return false
        val sinceContact = minOf(view.minutesSince(view.lastInteractionAt), view.minutesSince(view.lastWakeAt))
        return view.ongoingType == null && sinceContact >= boredAfter
    }

    private fun calmCare(view: CaregiverView): CareAction? {
        val sinceFeed = view.minutesSince(view.lastFeedEndAt)
        val burpedSinceFeed =
            view.recentActions.any {
                it.action == CareActionType.BURP &&
                    it.at >= (view.lastFeedEndAt ?: view.now)
            }
        return when {
            sinceFeed <= AFTER_FEED_BURP_MINUTES && !burpedSinceFeed -> CareAction.Do(CareActionType.BURP)
            view.ongoingType == CareActionType.HOLD || view.ongoingType == CareActionType.ROCK ->
                stopHoldingIfSettled(
                    view,
                )
            view.ongoingType == CareActionType.PLAY -> stopPlayingIfDone(view)
            readyToPlay(view) -> CareAction.Start(CareActionType.PLAY)
            else -> null
        }
    }

    private fun stopHoldingIfSettled(view: CaregiverView): CareAction? {
        val settled = view.activity != Activity.DROWSY && view.minutesSince(view.ongoingSince) >= SETTLED_HOLD_MINUTES
        return if (settled) CareAction.Stop else null
    }

    private fun stopPlayingIfDone(view: CaregiverView): CareAction? {
        val done = view.minutesSince(view.ongoingSince) >= PLAY_MINUTES || view.activity == Activity.DROWSY
        return if (done) CareAction.Stop else null
    }

    private fun readyToPlay(view: CaregiverView): Boolean {
        val boredAfter = view.boredAfterMinutes ?: return false
        val sinceContact = minOf(view.minutesSince(view.lastInteractionAt), view.minutesSince(view.lastWakeAt))
        return view.activity == Activity.AWAKE_CALM && sinceContact >= PLAY_AT_BOREDOM_FRACTION * boredAfter
    }
}

/** The attentive caregiver, but away: responds only to care requests, after 10–20 minutes. */
object DelayedCaregiver : CaregiverPolicy {
    override val name = "delayed"

    private const val MIN_DELAY = 10.0
    private const val MAX_DELAY = 20.0

    override fun responseDelayMinutes(rng: RandomSource): Double = rng.nextUniform(MIN_DELAY, MAX_DELAY)

    override fun decide(
        view: CaregiverView,
        rng: RandomSource,
    ): CareAction? {
        // Away from the baby: hears nothing until a care request reaches them.
        val away = view.careRequestOpenedAt == null && view.ongoingType == null && view.upset
        return if (away) null else AttentiveCaregiver.decide(view, rng)
    }
}

/** Never acts: tests the unattended path (auto-pause, docs/01 B1). */
object AbsentCaregiver : CaregiverPolicy {
    override val name = "absent"

    override fun responseDelayMinutes(rng: RandomSource): Double = Double.POSITIVE_INFINITY

    override fun decide(
        view: CaregiverView,
        rng: RandomSource,
    ): CareAction? = null
}

/** Responds within 1–10 minutes with a random action half the time: tests non-punitive dynamics. */
object RandomCaregiver : CaregiverPolicy {
    override val name = "random"

    private const val MIN_DELAY = 1.0
    private const val MAX_DELAY = 10.0
    private const val ACT_PROBABILITY = 0.5
    private const val CALM_ACT_PROBABILITY = 0.02
    private val actions: List<CareAction> =
        CareAction.CONTINUOUS.map { CareAction.Start(it) } + CareAction.Stop +
            listOf(
                CareActionType.BURP,
                CareActionType.CHECK_DIAPER,
                CareActionType.CHANGE_DIAPER,
                CareActionType.PUT_DOWN_TO_SLEEP,
                CareActionType.WAKE,
            ).map { CareAction.Do(it) }

    override fun responseDelayMinutes(rng: RandomSource): Double = rng.nextUniform(MIN_DELAY, MAX_DELAY)

    override fun decide(
        view: CaregiverView,
        rng: RandomSource,
    ): CareAction? {
        val needsCare = view.careRequestOpenedAt != null || view.upset
        val p = if (needsCare) ACT_PROBABILITY else CALM_ACT_PROBABILITY
        if (rng.nextDouble() >= p) return null
        return actions[(rng.nextDouble() * actions.size).toInt().coerceAtMost(actions.size - 1)]
    }
}
