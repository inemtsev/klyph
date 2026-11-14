package klyph.app

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Reports frame timing to systems. Users never construct it; `App.time` provides the instance the
 * loop updates.
 *
 * [delta] depends on the phase that reads it. Inside a [Phase.FIXED_UPDATE] step it equals
 * [fixedDelta]. In every other phase it is the duration of the current frame. A system can
 * therefore integrate motion with `position += velocity * time.deltaSeconds` and get the right
 * result in either phase.
 *
 * A frame's duration is clamped to at most 250 milliseconds, so a breakpoint or a stalled window
 * shows up as one long frame instead of a jump in game time.
 */
public class Time internal constructor(fixedRate: Int) {
    private var frameDelta: Duration = Duration.ZERO
    private var inFixedStep: Boolean = false

    init {
        require(fixedRate > 0) { "Fixed rate $fixedRate is not positive." }
    }

    /**
     * Returns the duration of one [Phase.FIXED_UPDATE] step: one second divided by the fixed rate.
     *
     * @see fixedDeltaSeconds
     */
    public val fixedDelta: Duration = 1.seconds / fixedRate

    /**
     * Returns the time to advance by in the running phase: [fixedDelta] while a
     * [Phase.FIXED_UPDATE] step runs, and the duration of the current frame, at most 250
     * milliseconds, in every other phase.
     *
     * @see deltaSeconds
     */
    public val delta: Duration
        get() = if (inFixedStep) fixedDelta else frameDelta

    /**
     * Returns the sum of the clamped frame durations since the first frame, including the current
     * one. It runs whatever scene is on top. [klyph.scene.Scene.elapsed] is the clock of one scene:
     * it stands still while another scene covers it.
     *
     * @see elapsedSeconds
     */
    public var elapsed: Duration = Duration.ZERO
        private set

    /**
     * Returns how much of the next [Phase.FIXED_UPDATE] step has elapsed by the time the frame is
     * drawn, as a fraction of [fixedDelta], from 0 up to but not including 1.
     *
     * The loop sets it once per frame, after it has counted the frame's fixed steps and before it
     * runs them, so every phase of a frame, [Phase.FIXED_UPDATE] included, reads the same value.
     * Rendering uses it to draw entities marked `Interpolated` between their last two fixed steps:
     * 0 shows the state before the last step, and values near 1 come close to the current state.
     * It is 0 until the first frame's steps are counted. `App.drawnPositionOf` applies it to find
     * where an entity is drawn.
     */
    public var fixedStepAlpha: Float = 0f
        internal set(value) {
            require(value >= 0f && value < 1f) { "Fixed step alpha $value is not at least 0 and below 1." }
            field = value
        }

    /** Number of frames completed so far. It is 0 during the first frame. */
    public var frameCount: Long = 0
        private set

    /**
     * Number of [Phase.FIXED_UPDATE] steps completed so far. It is 0 during the first step and
     * goes up by one at the end of every step, so a frame can add zero, one or several.
     */
    public var fixedStepCount: Long = 0
        private set

    /** Starts a frame that lasted [frameDelta], clamped to between zero and [MAX_FRAME_DELTA]. */
    internal fun beginFrame(frameDelta: Duration) {
        check(!inFixedStep) { "Cannot begin a frame inside a fixed step." }
        val clamped = frameDelta.coerceIn(Duration.ZERO, MAX_FRAME_DELTA)
        this.frameDelta = clamped
        elapsed += clamped
    }

    /** Switches [delta] to [fixedDelta] until [endFixedStep]. */
    internal fun beginFixedStep() {
        check(!inFixedStep) { "Fixed step has already begun." }
        inFixedStep = true
    }

    /** Switches [delta] back to the frame duration and counts the step in [fixedStepCount]. */
    internal fun endFixedStep() {
        check(inFixedStep) { "No fixed step is running." }
        inFixedStep = false
        fixedStepCount++
    }

    /** Ends the current frame and counts it in [frameCount]. */
    internal fun endFrame() {
        check(!inFixedStep) { "Cannot end a frame inside a fixed step." }
        frameCount++
    }

    /** Returns the state as `Time(frameCount=12, elapsed=0.2s, delta=16.6ms)`. The format may change. */
    override fun toString(): String =
        "Time(frameCount=$frameCount, elapsed=${elapsed.toString(DurationUnit.SECONDS, 1)}, " +
            "delta=${delta.toString(DurationUnit.MILLISECONDS, 1)})"

    internal companion object {
        /** Longest frame duration [beginFrame] accepts; longer frames are clamped to it. */
        val MAX_FRAME_DELTA: Duration = 250.milliseconds
    }
}

/**
 * Returns [Time.delta] in seconds, the value to multiply per-second rates by. Like [Time.delta], it
 * equals the fixed step inside [Phase.FIXED_UPDATE] and the frame duration in every other phase.
 */
public val Time.deltaSeconds: Float
    get() = delta.toDouble(DurationUnit.SECONDS).toFloat()

/**
 * Returns [Time.fixedDelta] in seconds, the length of one [Phase.FIXED_UPDATE] step as a `Float`.
 * Unlike [deltaSeconds] it is the same in every phase, so code outside the fixed step, such as
 * a guess of where a body will be one step on, can use it too.
 *
 * Has no sample because a [Time] is made by the running app.
 */
public val Time.fixedDeltaSeconds: Float
    get() = fixedDelta.toDouble(DurationUnit.SECONDS).toFloat()

/**
 * Returns [Time.elapsed] in seconds, the value to feed to formulas that take seconds, such as a
 * wave or a blink. Like [Time.elapsed], it runs whatever scene is on top, where
 * [klyph.scene.Scene.elapsed] stands still while another scene covers its scene. It resolves a
 * millisecond for the first four hours of a run and less after that, so compare [Time.elapsed]
 * durations for a timer that must stay exact.
 *
 * Has no sample because a [Time] is made by the running app.
 */
public val Time.elapsedSeconds: Float
    get() = elapsed.toDouble(DurationUnit.SECONDS).toFloat()
