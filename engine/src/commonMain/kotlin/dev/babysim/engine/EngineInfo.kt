package dev.babysim.engine

/**
 * Static facts about the engine build. Holds no mutable state (CLAUDE.md rule 3).
 */
object EngineInfo {
    /** Version of the serialized `BabyState` snapshot format. Bumped only with an upcaster (docs/05 §4). */
    const val ENGINE_SCHEMA_VERSION: Int = 1
}
