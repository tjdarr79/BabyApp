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

/**
 * Hidden causes of distress (docs/04 §3.3). Engine-internal: the UI never sees which are active.
 * Listed here because the care-action effect matrix in `engine/params/model.json` is keyed by them.
 */
@Serializable
enum class Cause {
    HUNGER,
    TIRED,
    OVERTIRED,
    GAS,
    DIAPER,
    OVERSTIMULATED,
    BORED,
    WANTS_CONTACT,
    UNEXPLAINED,
}

/** What an ongoing care action does to one cause's distress (docs/04 §6.4). */
@Serializable
enum class Effect {
    RESOLVES,
    PARTIAL,
    NONE,
    WORSENS,
}

/** User-visible result of a care action. Informative and non-punitive (brief §8): never a score. */
@Serializable
enum class ActionOutcome {
    STARTED,
    STOPPED,
    FEEDING,
    NOT_INTERESTED,
    BURPED,
    NO_BURP,
    DIAPER_DRY,
    DIAPER_WET,
    DIAPER_SOILED,
    CHANGED_WET,
    CHANGED_SOILED,
    ALREADY_DRY,
    PLAYING,
    NOT_IN_THE_MOOD,
    SETTLING_TO_SLEEP,
    STAYED_ASLEEP,
    WOKE_ON_TRANSFER,
    WANTS_TO_BE_HELD,
    WOKE_UP,
    ALREADY_AWAKE,

    /** The action does not apply right now (paused, nothing to stop, playing with a sleeping baby, reserved). */
    NOT_NOW,
}
