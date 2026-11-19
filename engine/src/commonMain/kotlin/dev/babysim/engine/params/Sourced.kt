package dev.babysim.engine.params

import kotlinx.serialization.Serializable

/** Strength of the basis for a parameter value (docs/06 grades). */
@Serializable
enum class Evidence {
    /** Peer-reviewed study or consensus statement. */
    PEER_REVIEWED,

    /** Official guidance (AAP, CDC, AASM, NSF, children's hospital). */
    GUIDANCE,

    /** Secondary or press summary of the above, or a non-peer-reviewed dataset. */
    SECONDARY,

    /** No adequate source found; chosen by design and must be decided by the reviewer. */
    DESIGN_ASSUMPTION,
}

/** Childcare reviewer sign-off state (kickoff; brief §8). Everything ships `UNREVIEWED` until reviewed. */
@Serializable
enum class ReviewStatus {
    UNREVIEWED,
    REVIEWED_OK,
    REVIEWED_CHANGED,
}

/**
 * A parameter value with provenance. Every leaf in a parameter file is a `Sourced` value:
 * [source], [evidence] and [reviewStatus] have no defaults, so a file missing any of them fails to load.
 */
@Serializable
data class Sourced<T>(
    val value: T,
    val source: String,
    val sourceUrls: List<String> = emptyList(),
    val evidence: Evidence,
    val reviewStatus: ReviewStatus,
    /** Free-text context, e.g. "owner decision 2026-11-19: lower half of norm". */
    val note: String? = null,
    /** Reviewer's comment when signing off. */
    val reviewNote: String? = null,
)

/** Inclusive range with a typical value. Units are carried by the field name (e.g. `...Minutes`). */
@Serializable
data class DoubleRange(
    val min: Double,
    val typical: Double,
    val max: Double,
) {
    init {
        require(min <= typical && typical <= max) { "range must satisfy min <= typical <= max: $this" }
        require(min >= 0.0) { "range values must be non-negative: $this" }
    }
}
