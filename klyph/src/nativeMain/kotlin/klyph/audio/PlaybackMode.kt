package klyph.audio

/**
 * How a sound or a music track moves on after its last frame.
 */
public enum class PlaybackMode {
    /** Plays once and stops at the end. The handle then reports that it is no longer playing. */
    ONCE,

    /** Wraps from the last frame back to the first and plays until it is stopped. */
    LOOP,
}
