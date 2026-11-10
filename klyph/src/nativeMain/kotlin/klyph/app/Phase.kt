package klyph.app

/**
 * Names the stage of the frame loop in which a [System] runs.
 *
 * The loop runs [STARTUP] once, then repeats [INPUT], [FIXED_UPDATE], [UPDATE], [LATE_UPDATE] and
 * [RENDER] once per frame in that order, and finally runs [SHUTDOWN] once. Systems of one phase
 * run in the order they were added: first those added with [App.addSystem], then those of the
 * scene on top of [App.scenes]. Scenes have no STARTUP or SHUTDOWN systems.
 */
public enum class Phase {
    /** Runs once before the first frame. */
    STARTUP,

    /** Runs once per frame, first. This is where the engine translates platform events. */
    INPUT,

    /**
     * Runs zero or more times per frame at a fixed rate, for gameplay and physics. While it runs,
     * [Time.delta] equals [Time.fixedDelta]. After the systems of each step, the engine moves every
     * entity with a [klyph.entity.Velocity] by its velocity times [Time.fixedDelta].
     */
    FIXED_UPDATE,

    /** Runs once per frame, after every [FIXED_UPDATE] step of that frame. */
    UPDATE,

    /**
     * Runs once per frame after every [UPDATE] system, for work that must see final positions,
     * such as a camera that follows an entity. Before it, the engine advances every
     * [klyph.particle.ParticleEmitter], then every [klyph.render.SpriteAnimation], showing its
     * current frame.
     */
    LATE_UPDATE,

    /**
     * Runs once per frame, last. The only phase in which [App.canvas] accepts calls; after the last
     * RENDER system the engine draws the frame.
     */
    RENDER,

    /** Runs once after the last frame. */
    SHUTDOWN,
}
