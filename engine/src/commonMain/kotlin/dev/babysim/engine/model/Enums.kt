package dev.babysim.engine.model

import kotlinx.serialization.Serializable

/**
 * Developmental age bands (brief §5, docs/06). Toddler (13–24 m) is post-MVP and intentionally absent;
 * adding it later is an additive change (new enum value + new parameter file).
 */
@Serializable
enum class AgeBandId {
    /** 0–4 weeks. */
    NEWBORN,

    /** "2–3 months", parameterized for ~8–13 weeks (owner decision, docs/01). */
    M2_3,
    M4_6,
    M7_9,
    M10_12,
}

/** Temperament profiles (brief §6). Simulation profiles, not diagnoses. */
@Serializable
enum class TemperamentId {
    EASYGOING,
    AVERAGE,
    DEMANDING,
    HIGHLY_SENSITIVE,
}

/** Age-appropriate vocalizations and sounds (docs/06, CDC milestones). */
@Serializable
enum class VocalType {
    CRY,
    FUSS,
    GRUNT,
    SIGH,
    SLEEP_NOISE,
    HICCUP,
    COO,
    SQUEAL,
    GIGGLE,

    /** Consonant jabbering ("ba", "ma"), ~6 months. */
    BABBLE,

    /** Reduplicated babble ("mamama", "bababa"), ~9 months. */
    BABBLE_REDUPLICATED,
}

/** Discrete behavioral state (docs/04 §3.2). */
@Serializable
enum class Activity {
    ASLEEP_ACTIVE,
    ASLEEP_QUIET,
    DROWSY,
    AWAKE_CALM,
    AWAKE_ALERT,
    FUSSING,
    CRYING,
    CRYING_INTENSE,
    EXHAUSTED_SLEEP,
}

/** Caregiver actions (docs/04 §6.4). */
@Serializable
enum class CareActionType {
    FEED,
    BURP,
    CHECK_DIAPER,
    CHANGE_DIAPER,
    HOLD,
    ROCK,
    PLAY,
    PUT_DOWN_TO_SLEEP,
    WAKE,

    /** Reserved (docs/01 B6): exists in the engine, not exposed in the MVP UI. */
    PACIFIER,
}
