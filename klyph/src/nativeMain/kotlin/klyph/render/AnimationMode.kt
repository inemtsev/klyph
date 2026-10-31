package klyph.render

/**
 * How an [Animation] moves on after it shows its last frame.
 */
public enum class AnimationMode {
    /**
     * Plays the frames once. When the last frame's duration elapses, the playhead stays on the last
     * frame and [Animation.isFinished] becomes `true`.
     *
     * @sample samples.render.Animations.once
     */
    ONCE,

    /** Plays the frames in order and wraps from the last frame back to the first. */
    LOOP,

    /**
     * Plays the frames forward, then backward, then forward again, without showing the first and
     * last frames twice in a row. Four frames play as 0, 1, 2, 3, 2, 1, 0, 1, and so on.
     */
    PING_PONG,
}
