package dev.babysim.engine.sim

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventType
import dev.babysim.engine.LocalClock
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.random.RngBank
import dev.babysim.engine.random.RngStreamId
import dev.babysim.engine.random.nextBernoulli
import dev.babysim.engine.random.nextDouble
import dev.babysim.engine.random.nextExponential
import dev.babysim.engine.state.CareRequest
import dev.babysim.engine.state.PauseInfo
import dev.babysim.engine.state.PauseReason
import dev.babysim.engine.state.ProcessId
import dev.babysim.engine.state.SleepCycle
import kotlin.math.max
import kotlin.math.min

/** Safety caps so an unattended baby's drives stay finite. Not behavior values. */
private const val DRIVE_CAP = 3.0

/**
 * Advances one baby by exactly one tick (docs/04 §5, §8). Holds only immutable per-baby configuration.
 * Every random draw comes from the baby's own [RngBank], in a fixed order, so runs are reproducible.
 */
@Suppress("TooManyFunctions") // One function per behavior rule keeps each rule small and testable.
internal class TickRunner(
    private val config: BabyConfig,
    private val rates: Rates,
    private val localClock: LocalClock,
) {
    private val model = rates.model
    private val age = rates.age
    private val temperament = rates.temperament

    fun tick(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        if (w.pause != null) return
        val nightWeight = if (isNight(t)) rates.circadian else 0.0
        integrateDrives(w, t, nightWeight)
        runProcesses(w, bank, t, out)
        if (w.isAsleep) sleepStep(w, bank, t, nightWeight, out) else drowsiness(w, bank, t, out)
        updateDistress(w, t)
        updateActivity(w, t, out)
        updateMood(w)
        careRequests(w, t, bank, out)
        cryCap(w, bank, t, out)
        autoPause(w, t, out)
    }

    // ---- drives -------------------------------------------------------------------------------------------

    private fun integrateDrives(
        w: Work,
        t: SimInstant,
        nightWeight: Double,
    ) {
        val nightHunger = 1.0 - (1.0 - model.nightHungerRateScale.value) * nightWeight
        w.hunger = min(DRIVE_CAP, w.hunger + rates.hungerPerMinute * nightHunger * TICK_MINUTES)
        w.sleepPressure =
            if (w.isAsleep) {
                val nightDecay = 1.0 - (1.0 - model.nightSleepPressureDecayScale.value) * nightWeight
                max(0.0, w.sleepPressure - rates.sleepFallPerMinute * nightDecay * TICK_MINUTES)
            } else {
                val nightRise = 1.0 + model.nightSleepPressureRiseBoost.value * nightWeight
                min(DRIVE_CAP, w.sleepPressure + rates.sleepRisePerMinute * nightRise * TICK_MINUTES)
            }
        val soiledRate = if (w.diaperSoiled) model.diaperDiscomfortPerMinuteSoiled.value else 0.0
        val discomfortRate = w.diaperWetness * model.diaperDiscomfortPerMinuteWet.value + soiledRate
        w.diaperDiscomfort = min(1.0, w.diaperDiscomfort + discomfortRate * TICK_MINUTES)
        if (w.fussSpellEndsAt?.let { t >= it } == true) w.fussSpellEndsAt = null
        if (w.wantsContactUntil?.let { t >= it } == true) w.wantsContactUntil = null
    }

    // ---- stochastic processes (hazard budgets, docs/04 §7.1) ---------------------------------------------

    private fun runProcesses(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        for (process in ProcessId.entries) {
            val ratePerMinute = hazard(process, w, t)
            if (ratePerMinute <= 0.0) continue
            val stream = bank.stream(streamFor(process))
            var budget = (w.budgets[process] ?: stream.nextExponential()) - ratePerMinute * TICK_MINUTES
            if (budget <= 0.0) {
                fire(process, w, bank, t, out)
                budget = stream.nextExponential()
            }
            w.budgets[process] = budget
        }
    }

    private fun hazard(
        process: ProcessId,
        w: Work,
        t: SimInstant,
    ): Double {
        val awakeAndCalm = !w.isAsleep && w.fallAsleepAt == null && w.distress < rates.fussLevel
        return when (process) {
            ProcessId.VOID -> rates.wetPerMinute * postFeedVoidBoost(w, t)
            ProcessId.STOOL -> rates.soiledPerMinute
            ProcessId.UNEXPLAINED_FUSS ->
                if (!w.isAsleep && w.fussSpellEndsAt == null) rates.unexplainedFussPerMinute * eveningBoost(t) else 0.0
            ProcessId.VOCAL ->
                if (awakeAndCalm && rates.contentVocals.isNotEmpty()) {
                    rates.vocalPerMinute * max(0.0, 1.0 + model.moodVocalGain.value * w.mood)
                } else {
                    0.0
                }
        }
    }

    private fun fire(
        process: ProcessId,
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        when (process) {
            ProcessId.VOID -> {
                w.diaperWetness = min(1.0, w.diaperWetness + model.wetnessPerVoid.value)
                out += event(w, t, EventType.DIAPER_WET)
            }
            ProcessId.STOOL -> {
                w.diaperSoiled = true
                out += event(w, t, EventType.DIAPER_SOILED)
            }
            ProcessId.UNEXPLAINED_FUSS -> {
                val minutes = bank.stream(RngStreamId.UNEXPLAINED_FUSS).sample(model.fussSpellMinutes.value)
                w.fussSpellEndsAt = t + minutes
            }
            ProcessId.VOCAL -> {
                val vocals = rates.contentVocals
                val index =
                    (bank.stream(RngStreamId.VOCAL).nextDouble() * vocals.size).toInt().coerceAtMost(
                        vocals.size - 1,
                    )
                out += event(w, t, EventType.VOCALIZATION).copy(vocal = vocals[index])
            }
        }
    }

    private fun streamFor(process: ProcessId): RngStreamId =
        when (process) {
            ProcessId.VOID -> RngStreamId.DIAPER_VOID
            ProcessId.STOOL -> RngStreamId.DIAPER_STOOL
            ProcessId.UNEXPLAINED_FUSS -> RngStreamId.UNEXPLAINED_FUSS
            ProcessId.VOCAL -> RngStreamId.VOCAL
        }

    private fun postFeedVoidBoost(
        w: Work,
        t: SimInstant,
    ): Double {
        val fedAt = w.timestamps.lastFeedEndAt ?: return 1.0
        return if (t.millisSince(fedAt) <= rates.postFeedVoidWindowMillis) model.postFeedVoidMultiplier.value else 1.0
    }

    private fun eveningBoost(t: SimInstant): Double {
        if (!age.eveningFussClustering.value) return 1.0
        val inEvening =
            inWindow(
                localClock.minuteOfDay(t),
                model.eveningStartLocalMinutes.value,
                model.eveningEndLocalMinutes.value,
            )
        return if (inEvening) model.eveningFussMultiplier.value else 1.0
    }

    // ---- sleep --------------------------------------------------------------------------------------------

    private fun sleepStep(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        nightWeight: Double,
        out: MutableList<BabyEvent>,
    ) {
        val cycle = w.sleep ?: startCycle(w, bank, t)
        if (t >= cycle.cycleEndsAt) {
            arousal(w, bank, t, nightWeight, out)
        } else if (w.activity != Activity.EXHAUSTED_SLEEP) {
            val midpoint = cycle.cycleStartedAt.epochMillis + (cycle.cycleEndsAt.millisSince(cycle.cycleStartedAt) / 2)
            w.activity = if (t.epochMillis < midpoint) Activity.ASLEEP_ACTIVE else Activity.ASLEEP_QUIET
        }
    }

    /** Between sleep cycles the baby partly rouses (docs/04 §5.1). */
    private fun arousal(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        nightWeight: Double,
        out: MutableList<BabyEvent>,
    ) {
        val stream = bank.stream(RngStreamId.AROUSAL)
        val needsCare = w.hunger >= w.thresholds.hungerCueAt || w.diaperDiscomfort >= model.diaperCueAt.value
        val rested = w.sleepPressure <= model.restedSleepPressure.value
        when {
            needsCare -> wake(w, bank, t, wantsContact = false, out)
            rested && stream.nextDouble() >= nightWeight -> wake(w, bank, t, wantsContact = nightWeight > 0.0, out)
            rested -> {
                if (!stream.nextBernoulli(rates.selfSettleProbNight)) out += event(w, t, EventType.BRIEF_FUSS)
                startCycle(w, bank, t)
            }
            else -> {
                if (stream.nextBernoulli(model.sleepNoiseProbPerArousal.value)) {
                    out +=
                        event(w, t, EventType.SLEEP_NOISE)
                }
                startCycle(w, bank, t)
            }
        }
    }

    private fun startCycle(
        w: Work,
        bank: RngBank,
        t: SimInstant,
    ): SleepCycle {
        val minutes = bank.stream(RngStreamId.SLEEP).sample(age.sleepCycleMinutes.value)
        return SleepCycle(t, t + minutes).also { w.sleep = it }
    }

    private fun wake(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        wantsContact: Boolean,
        out: MutableList<BabyEvent>,
    ) {
        w.sleep = null
        w.activity = Activity.AWAKE_CALM
        w.timestamps = w.timestamps.copy(lastWakeAt = t)
        w.thresholds = w.thresholds.copy(sleepReadyAt = sampleSleepReady(bank))
        w.distress =
            min(1.0, w.distress + model.wakeTransitionDistress.value * temperament.transitionDistress.value)
        if (wantsContact) {
            w.wantsContactUntil = t + bank.stream(RngStreamId.AROUSAL).sample(model.fussSpellMinutes.value)
        }
        out += event(w, t, EventType.WOKE)
    }

    /** An awake baby that is sleepy and not crying grows drowsy, then falls asleep unless disturbed. */
    private fun drowsiness(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val fallAsleepAt = w.fallAsleepAt
        when {
            fallAsleepAt != null && w.distress >= rates.cryLevel -> w.fallAsleepAt = null
            fallAsleepAt != null && t >= fallAsleepAt -> fallAsleep(w, bank, t, exhausted = false, out)
            fallAsleepAt == null && w.sleepPressure >= w.thresholds.sleepReadyAt && w.distress < rates.cryLevel -> {
                w.fallAsleepAt = t + bank.stream(RngStreamId.SLEEP).sample(model.drowsyToSleepMinutes.value)
            }
        }
    }

    private fun fallAsleep(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        exhausted: Boolean,
        out: MutableList<BabyEvent>,
    ) {
        w.fallAsleepAt = null
        w.fussSpellEndsAt = null
        w.wantsContactUntil = null
        w.cryingSince = null
        w.cryingLevelSince = null
        w.distress = 0.0
        w.activity = if (exhausted) Activity.EXHAUSTED_SLEEP else Activity.ASLEEP_ACTIVE
        w.timestamps = w.timestamps.copy(lastSleepStartAt = t)
        startCycle(w, bank, t)
        out += event(w, t, EventType.FELL_ASLEEP).copy(activity = w.activity)
    }

    // ---- needs → distress → activity (docs/04 §3.3) ------------------------------------------------------

    private fun updateDistress(
        w: Work,
        t: SimInstant,
    ) {
        if (w.isAsleep) {
            w.distress = 0.0
            return
        }
        val target = distressTarget(w, t)
        w.distress =
            if (target > w.distress) {
                min(target, w.distress + model.distressRisePerMinute.value * TICK_MINUTES)
            } else {
                max(target, w.distress - model.distressFallPerMinute.value * TICK_MINUTES)
            }
    }

    /** The hidden causes. The UI never sees which are active (CLAUDE.md engine rules). */
    private fun distressTarget(
        w: Work,
        t: SimInstant,
    ): Double {
        val weights = mutableListOf<Double>()
        val hungerCue = w.thresholds.hungerCueAt
        if (w.hunger >= hungerCue) {
            weights += model.hungerCauseWeight.value + model.hungerOverdueGain.value * (w.hunger - hungerCue)
        }
        val ready = w.thresholds.sleepReadyAt
        if (w.fallAsleepAt == null && w.sleepPressure >= ready + model.overtiredMargin.value) {
            weights += model.overtiredCauseWeight.value
        } else if (w.fallAsleepAt == null && w.sleepPressure >= ready) {
            weights += model.tiredCauseWeight.value
        }
        if (w.diaperDiscomfort >= model.diaperCueAt.value) weights += model.diaperCauseWeight.value
        if (w.fussSpellEndsAt != null) weights += model.unexplainedFussCauseWeight.value
        if (w.wantsContactUntil != null) weights += model.wantsContactCauseWeight.value
        if (isBored(w, t)) weights += model.boredCauseWeight.value
        if (weights.isEmpty()) return 0.0
        return (weights.max() + model.causeStackBonus.value * (weights.size - 1)).coerceIn(0.0, 1.0)
    }

    private fun isBored(
        w: Work,
        t: SimInstant,
    ): Boolean {
        val boredAfter = rates.boredAfterMinutes
        // Boredom builds from the later of the last interaction and waking up: sleep resets it.
        val since = listOfNotNull(w.lastInteractionAt, w.timestamps.lastWakeAt).maxOrNull()
        return boredAfter != null && since != null && t.millisSince(since) >= minutesToMillis(boredAfter)
    }

    private fun updateActivity(
        w: Work,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        if (w.isAsleep) return
        val next =
            when {
                w.distress >= rates.intenseLevel -> Activity.CRYING_INTENSE
                w.distress >= rates.cryLevel -> Activity.CRYING
                w.distress >= rates.fussLevel -> Activity.FUSSING
                w.fallAsleepAt != null -> Activity.DROWSY
                else -> Activity.AWAKE_CALM
            }
        if (next != w.activity) {
            w.activity = next
            out += event(w, t, EventType.ACTIVITY_CHANGED).copy(activity = next)
        }
        val crying = next == Activity.CRYING || next == Activity.CRYING_INTENSE
        if (crying && w.cryingSince == null) w.cryingSince = t
        if (w.distress < model.calmThreshold.value) w.cryingSince = null
    }

    private fun updateMood(w: Work) {
        val target = (1.0 - 2.0 * w.distress).coerceIn(-1.0, 1.0)
        w.mood += (target - w.mood) * model.moodRatePerMinute.value * TICK_MINUTES
    }

    // ---- care requests, unattended rule (docs/04 §4, docs/01 B1) -------------------------------------------

    private fun careRequests(
        w: Work,
        t: SimInstant,
        bank: RngBank,
        out: MutableList<BabyEvent>,
    ) {
        val request = w.careRequest
        val atCryLevel = w.activity == Activity.CRYING || w.activity == Activity.CRYING_INTENSE
        when {
            request == null && atCryLevel -> {
                val since = w.cryingLevelSince ?: t.also { w.cryingLevelSince = it }
                if (t.millisSince(since) >= minutesToMillis(w.thresholds.careSustainMinutes)) openRequest(w, t, out)
            }
            request == null -> w.cryingLevelSince = null
            w.isAsleep || w.distress <= model.calmThreshold.value -> {
                out += event(w, t, EventType.CARE_REQUEST_RESOLVED).copy(careRequestId = request.id)
                closeRequest(w, bank)
            }
            shouldEscalate(w, request, t) -> {
                val escalated = request.copy(escalationLevel = request.escalationLevel + 1, lastAlertAt = t)
                w.careRequest = escalated
                out +=
                    event(w, t, EventType.CARE_REQUEST_ESCALATED)
                        .copy(careRequestId = escalated.id, escalationLevel = escalated.escalationLevel)
            }
        }
    }

    private fun shouldEscalate(
        w: Work,
        request: CareRequest,
        t: SimInstant,
    ): Boolean {
        if (request.escalationLevel >= model.maxEscalationLevel.value) return false
        val intenseFirstAlert = request.escalationLevel == 1 && w.activity == Activity.CRYING_INTENSE
        return intenseFirstAlert || t.millisSince(request.lastAlertAt) >= rates.escalateAfterMillis
    }

    private fun openRequest(
        w: Work,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val request = CareRequest(id = w.nextCareRequestId, openedAt = t, escalationLevel = 1, lastAlertAt = t)
        w.careRequest = request
        w.nextCareRequestId += 1
        w.unanswered = w.unanswered.copy(firstUnansweredAt = w.unanswered.firstUnansweredAt ?: t)
        out += event(w, t, EventType.CARE_REQUEST_OPENED).copy(careRequestId = request.id, escalationLevel = 1)
    }

    private fun closeRequest(
        w: Work,
        bank: RngBank,
    ) {
        w.careRequest = null
        w.cryingLevelSince = null
        w.thresholds = w.thresholds.copy(careSustainMinutes = sampleCareSustain(bank))
    }

    /** Unattended crying stops after a cap: the request expires and the baby falls into exhausted sleep. */
    private fun cryCap(
        w: Work,
        bank: RngBank,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val since = w.cryingSince ?: return
        if (t.millisSince(since) < rates.crySegmentMaxMillis) return
        w.careRequest?.let { request ->
            out += event(w, t, EventType.CARE_REQUEST_EXPIRED).copy(careRequestId = request.id)
            w.unanswered = w.unanswered.copy(expiredCount = w.unanswered.expiredCount + 1)
            closeRequest(w, bank)
        }
        fallAsleep(w, bank, t, exhausted = true, out)
    }

    private fun autoPause(
        w: Work,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val firstUnanswered = w.unanswered.firstUnansweredAt
        val reason =
            when {
                w.unanswered.expiredCount >= model.autoPauseUnansweredRequests.value ->
                    PauseReason.AUTO_UNANSWERED_REQUESTS
                firstUnanswered != null && t.millisSince(firstUnanswered) >= rates.autoPauseNoResponseMillis ->
                    PauseReason.AUTO_NO_RESPONSE
                else -> return
            }
        w.careRequest?.let { out += event(w, t, EventType.CARE_REQUEST_EXPIRED).copy(careRequestId = it.id) }
        w.careRequest = null
        w.pause = PauseInfo(reason, t)
        out += event(w, t, EventType.AUTO_PAUSED).copy(pauseReason = reason)
    }

    // ---- thresholds -------------------------------------------------------------------------------------

    fun sampleHungerCue(bank: RngBank): Double {
        val interval = age.feedIntervalMinutes.value
        val sampled = bank.stream(RngStreamId.THRESHOLDS).sample(interval, temperament.rhythmicitySigmaScale.value)
        return sampled / interval.typical
    }

    fun sampleSleepReady(bank: RngBank): Double {
        val window = age.wakeWindowMinutes.value
        val sampled = bank.stream(RngStreamId.THRESHOLDS).sample(window, temperament.rhythmicitySigmaScale.value)
        return sampled / window.typical
    }

    fun sampleCareSustain(bank: RngBank): Double =
        bank.stream(RngStreamId.CARE_REQUEST).sample(age.careSustainMinutes.value) * temperament.careSustainScale.value

    // ---- time helpers ---------------------------------------------------------------------------------------

    private fun isNight(t: SimInstant): Boolean =
        inWindow(localClock.minuteOfDay(t), config.nightStartLocalMinutes, config.nightEndLocalMinutes)

    private fun event(
        w: Work,
        t: SimInstant,
        type: EventType,
    ): BabyEvent = BabyEvent(babyId = w.babyId, at = t, type = type)
}

/** True if [minute] lies in [start, end), wrapping past midnight when start > end. */
internal fun inWindow(
    minute: Int,
    start: Int,
    end: Int,
): Boolean = if (start <= end) minute in start until end else minute >= start || minute < end

/** Adds fractional minutes to an instant, at millisecond resolution. */
internal operator fun SimInstant.plus(minutes: Double): SimInstant = SimInstant(epochMillis + minutesToMillis(minutes))
