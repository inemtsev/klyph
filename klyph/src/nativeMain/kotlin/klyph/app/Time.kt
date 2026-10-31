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
     * one.
     */
    public var elapsed: Duration = Duration.ZERO
        private set

    /** Returns the number of frames completed so far. It is 0 during the first frame. */
    public var frame: Long = 0
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

    /** Switches [delta] back to the frame duration. */
    internal fun endFixedStep() {
        check(inFixedStep) { "No fixed step is running." }
        inFixedStep = false
    }

    /** Ends the current frame and counts it in [frame]. */
    internal fun endFrame() {
        check(!inFixedStep) { "Cannot end a frame inside a fixed step." }
        frame++
    }

    /** Returns the state as `Time(frame=12, elapsed=0.2s, delta=16.6ms)`. The format may change. */
    override fun toString(): String =
        "Time(frame=$frame, elapsed=${elapsed.toString(DurationUnit.SECONDS, 1)}, " +
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
