package klyph.render

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A playhead that steps through [frameCount] frames, showing each one for [frameDuration], in the
 * order [mode] sets.
 *
 * The animation only tracks a frame index. [advance] moves it by the time that passed; what the
 * index selects, such as a texture region, is up to the caller. Time is kept in whole nanoseconds,
 * so many small deltas add up exactly and the frame changes do not drift.
 *
 * [frameCount] must be at least one and [frameDuration] must be positive and finite.
 *
 * @throws IllegalArgumentException if [frameCount] is less than one, or [frameDuration] is zero,
 * negative or infinite.
 *
 * @sample samples.render.Animations.advance
 */
public class Animation(
    /** Number of frames the playhead steps through, at least one. */
    public val frameCount: Int,
    /** How long each frame is shown, positive and finite. */
    public val frameDuration: Duration,
    /** The order the frames play in and what happens after the last one. */
    public val mode: AnimationMode = AnimationMode.LOOP,
) {
    private val frameNanos: Long

    // Number of positions in one cycle. For PING_PONG this counts the way back, so the position
    // runs past the last frame and frameIndex folds it back.
    private val cycleLength: Long

    // Position within the cycle, 0 until cycleLength.
    private var position: Long = 0

    private var elapsedNanos: Long = 0

    init {
        require(frameCount >= 1) { "Frame count $frameCount is less than one." }
        require(frameDuration.isPositive() && frameDuration.isFinite()) {
            "Frame duration $frameDuration is not positive and finite."
        }
        frameNanos = frameDuration.inWholeNanoseconds
        cycleLength = when (mode) {
            AnimationMode.PING_PONG -> maxOf(1L, 2L * (frameCount - 1))
            AnimationMode.ONCE, AnimationMode.LOOP -> frameCount.toLong()
        }
    }

    /** Returns the index of the frame to show, from 0 until [frameCount]. */
    public val frameIndex: Int
        get() = (if (position < frameCount) position else cycleLength - position).toInt()

    /**
     * Returns `true` once an [AnimationMode.ONCE] animation has shown its last frame for the full
     * [frameDuration]. Always `false` for the other modes.
     *
     * @sample samples.render.Animations.once
     */
    public var isFinished: Boolean = false
        private set

    /** The number of times [advance] has finished this animation, so a reader can tell each finish apart, a restart included. */
    internal var finishCount: Int = 0
        private set

    /** Stops [advance] from moving the playhead while `true`. Set it to pause and resume the animation. */
    public var isPaused: Boolean = false

    /**
     * Returns how long the current frame has been shown, from zero until [frameDuration].
     *
     * A finished [AnimationMode.ONCE] animation reports exactly [frameDuration], because its last
     * frame was shown for the full time.
     */
    public val frameElapsed: Duration
        get() = elapsedNanos.nanoseconds

    /**
     * Moves the playhead forward by [delta].
     *
     * A delta longer than one frame skips over the frames it covers, and a delta longer than the
     * whole animation wraps around or, for [AnimationMode.ONCE], finishes it. Time that does not
     * fill a frame carries over in [frameElapsed]. Does nothing while [isPaused] or [isFinished].
     *
     * @throws IllegalArgumentException if [delta] is negative.
     *
     * @sample samples.render.Animations.advance
     */
    public fun advance(delta: Duration) {
        require(!delta.isNegative()) { "Delta $delta is negative." }
        if (isPaused || isFinished) return
        // An infinite delta saturates to Long.MAX_VALUE nanoseconds, which still finishes or wraps.
        val deltaNanos = delta.inWholeNanoseconds
        var frames = deltaNanos / frameNanos
        val remainder = deltaNanos % frameNanos
        // Compared as a difference so that elapsedNanos + remainder cannot overflow.
        if (elapsedNanos >= frameNanos - remainder) {
            elapsedNanos -= frameNanos - remainder
            frames++
        } else {
            elapsedNanos += remainder
        }
        if (frames == 0L) return
        if (mode == AnimationMode.ONCE) {
            if (frames >= frameCount - position) {
                position = frameCount - 1L
                elapsedNanos = frameNanos
                isFinished = true
                finishCount++
            } else {
                position += frames
            }
        } else {
            position = (position + frames % cycleLength) % cycleLength
        }
    }

    /**
     * Returns to frame 0 with no elapsed time, and clears [isFinished] and [isPaused].
     *
     * @sample samples.render.Animations.restart
     */
    public fun restart() {
        position = 0
        elapsedNanos = 0
        isFinished = false
        isPaused = false
    }

    /**
     * Jumps to [frameIndex] with no elapsed time on it and clears [isFinished]. [isPaused] is kept.
     *
     * [frameIndex] must be from 0 until [frameCount]. A [AnimationMode.PING_PONG] animation plays
     * forward from the new frame.
     *
     * @throws IndexOutOfBoundsException if [frameIndex] is outside 0 until [frameCount].
     *
     * @sample samples.render.Animations.seek
     */
    public fun seek(frameIndex: Int) {
        if (frameIndex !in 0..<frameCount) {
            throw IndexOutOfBoundsException("Frame index $frameIndex is out of bounds for frame count $frameCount.")
        }
        position = frameIndex.toLong()
        elapsedNanos = 0
        isFinished = false
    }

    override fun toString(): String = "Animation(frame=$frameIndex/$frameCount, mode=$mode)"
}

/**
 * Returns the length of one cycle of the animation.
 *
 * For [AnimationMode.ONCE] and [AnimationMode.LOOP] that is [Animation.frameCount] times
 * [Animation.frameDuration]. For [AnimationMode.PING_PONG] it is the round trip back to the first
 * frame, `2 * (frameCount - 1)` frames, because only then does the sequence repeat. A ping-pong
 * of a single frame lasts one frame.
 *
 * @sample samples.render.Animations.duration
 */
public val Animation.duration: Duration
    get() = when (mode) {
        AnimationMode.PING_PONG -> if (frameCount == 1) frameDuration else frameDuration * (frameCount - 1) * 2
        AnimationMode.ONCE, AnimationMode.LOOP -> frameDuration * frameCount
    }
