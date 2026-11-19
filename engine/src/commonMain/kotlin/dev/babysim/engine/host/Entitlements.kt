package dev.babysim.engine.host

/**
 * The single gate on how many babies can run at once (CLAUDE.md rule 8). The two-baby expansion (brief §13,
 * Step 10) changes only which provider the app supplies.
 */
interface EntitlementProvider {
    val maxActiveBabies: Int
}

/** v1: one baby. */
object V1Entitlements : EntitlementProvider {
    override val maxActiveBabies: Int = 1
}

/** Thrown when adding a baby would exceed [EntitlementProvider.maxActiveBabies]. */
class EntitlementExceededException(
    val max: Int,
) : IllegalStateException("At most $max active ${if (max == 1) "baby" else "babies"} allowed")
