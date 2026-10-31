package klyph.app

/**
 * Names the stage of the frame loop in which a [System] runs.
 *
 * The loop runs [STARTUP] once, then repeats [INPUT], [FIXED_UPDATE], [UPDATE], [LATE_UPDATE] and
 * [RENDER] once per frame in that order, and finally runs [SHUTDOWN] once. Systems of one phase
 * run in the order they were added.
 */
public enum class Phase {
    /** Runs once before the first frame. */
    STARTUP,

    /** Runs once per frame, first. This is where the engine translates platform events. */
    INPUT,

    /**
     * Runs zero or more times per frame at a fixed rate, for gameplay and physics. While it runs,
     * [Time.delta] equals [Time.fixedDelta].
     */
    FIXED_UPDATE,

    /** Runs once per frame, after every [FIXED_UPDATE] step of that frame. */
    UPDATE,

    /**
     * Runs once per frame after every [UPDATE] system, for work that must see final positions,
     * such as a camera that follows an entity.
     */
    LATE_UPDATE,

    /** Runs once per frame, last. */
    RENDER,

    /** Runs once after the last frame. */
    SHUTDOWN,
}
