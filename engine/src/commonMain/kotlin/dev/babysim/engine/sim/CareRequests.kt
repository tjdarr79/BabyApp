package dev.babysim.engine.sim

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventType
import dev.babysim.engine.model.Activity
import dev.babysim.engine.model.CareActionType
import dev.babysim.engine.model.SimInstant
import dev.babysim.engine.random.RngBank
import dev.babysim.engine.state.CareRequest
import kotlin.math.min

/**
 * The care-request lifecycle for one baby (docs/04 §4, docs/01 B1): an alert opens after sustained crying,
 * escalates while nobody responds, stays open when the baby goes quiet without a response (owner decision
 * 2026-11-19), sounds again if the baby cries again, and resolves once someone has responded.
 */
internal class CareRequests(
    private val rates: Rates,
    private val runner: TickRunner,
) {
    private val model = rates.model

    fun update(
        w: Work,
        t: SimInstant,
        bank: RngBank,
        out: MutableList<BabyEvent>,
    ) {
        val request = w.careRequest
        when {
            request == null -> if (cryingSustained(w, t)) openRequest(w, t, out)
            w.isAsleep || w.distress <= model.calmThreshold.value -> quietOrResolved(w, bank, request, t, out)
            request.quietSince != null -> if (cryingSustained(w, t)) alertAgain(w, request, t, out)
            shouldEscalate(w, request, t) -> alertAgain(w, request, t, out)
        }
    }

    /** True once crying has lasted the sampled care-sustain time; tracks when crying started. */
    private fun cryingSustained(
        w: Work,
        t: SimInstant,
    ): Boolean {
        val atCryLevel = w.activity == Activity.CRYING || w.activity == Activity.CRYING_INTENSE
        if (!atCryLevel) {
            w.cryingLevelSince = null
            return false
        }
        val since = w.cryingLevelSince ?: t.also { w.cryingLevelSince = it }
        return t.millisSince(since) >= minutesToMillis(w.thresholds.careSustainMinutes)
    }

    /**
     * The baby is asleep or calm. With a response since the alert, the request resolves. Without one, the alert
     * stays open and this quiet episode counts as unanswered (owner decision 2026-11-19, docs/01 B1).
     */
    private fun quietOrResolved(
        w: Work,
        bank: RngBank,
        request: CareRequest,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        when {
            responded(w, request) -> {
                out +=
                    runner
                        .event(
                            w,
                            t,
                            EventType.CARE_REQUEST_RESOLVED,
                        ).copy(careRequestId = request.id, action = caringAction(w))
                close(w, bank)
            }
            request.quietSince == null -> {
                w.careRequest = request.copy(quietSince = t)
                w.cryingLevelSince = null
                w.unanswered = w.unanswered.copy(expiredCount = w.unanswered.expiredCount + 1)
            }
        }
    }

    /** Repeat alert: escalation of an open request, or the baby crying again while its alert is still open. */
    private fun alertAgain(
        w: Work,
        request: CareRequest,
        t: SimInstant,
        out: MutableList<BabyEvent>,
    ) {
        val level = min(request.escalationLevel + 1, model.maxEscalationLevel.value)
        val escalated = request.copy(escalationLevel = level, lastAlertAt = t, quietSince = null)
        w.careRequest = escalated
        w.cryingLevelSince = null
        out +=
            runner
                .event(w, t, EventType.CARE_REQUEST_ESCALATED)
                .copy(careRequestId = escalated.id, escalationLevel = escalated.escalationLevel)
    }

    /** Any care action since the alert opened counts as a response (docs/04 §12). */
    fun responded(
        w: Work,
        request: CareRequest,
    ): Boolean = w.recentActions.lastOrNull()?.let { it.at >= request.openedAt } == true

    /** The action in progress, or else the most recent one: what calmed the baby, as far as the user knows. */
    fun caringAction(w: Work): CareActionType? = w.ongoingAction?.type ?: w.recentActions.lastOrNull()?.action

    private fun shouldEscalate(
        w: Work,
        request: CareRequest,
        t: SimInstant,
    ): Boolean {
        // No repeat alerts while someone is already caring for the baby (docs/04 §12).
        if (request.escalationLevel >= model.maxEscalationLevel.value || w.ongoingAction != null) return false
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
        out += runner.event(w, t, EventType.CARE_REQUEST_OPENED).copy(careRequestId = request.id, escalationLevel = 1)
    }

    fun close(
        w: Work,
        bank: RngBank,
    ) {
        w.careRequest = null
        w.cryingLevelSince = null
        w.thresholds = w.thresholds.copy(careSustainMinutes = runner.sampleCareSustain(bank))
    }
}
