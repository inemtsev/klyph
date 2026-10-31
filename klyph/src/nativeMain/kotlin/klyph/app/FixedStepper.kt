package klyph.app

import kotlin.time.Duration

/**
 * Converts variable frame durations into a whole number of fixed steps of [fixedDelta].
 *
 * Frame time accumulates until it covers a step. At most [maxStepsPerFrame] steps run per frame;
 * whole steps beyond that are discarded so a stall does not make the next frames slower still.
 * The part of a step that does not yet add up to a whole one carries over to the next frame.
 */
internal class FixedStepper(private val fixedDelta: Duration, private val maxStepsPerFrame: Int) {
    private val stepNanos: Long
    private var accumulatedNanos: Long = 0

    init {
        require(fixedDelta.isPositive()) { "Fixed delta $fixedDelta is not positive." }
        require(maxStepsPerFrame >= 1) { "Max steps per frame $maxStepsPerFrame is less than one." }
        stepNanos = fixedDelta.inWholeNanoseconds
        require(stepNanos > 0) { "Fixed delta $fixedDelta is shorter than one nanosecond." }
    }

    /**
     * Adds [frameDelta] to the accumulator and returns how many fixed steps to run this frame,
     * between 0 and [maxStepsPerFrame].
     *
     * @throws IllegalArgumentException if [frameDelta] is negative.
     */
    fun accumulate(frameDelta: Duration): Int {
        require(!frameDelta.isNegative()) { "Frame delta $frameDelta is negative." }
        accumulatedNanos += frameDelta.inWholeNanoseconds
        val available = accumulatedNanos / stepNanos
        accumulatedNanos -= available * stepNanos
        return if (available > maxStepsPerFrame) maxStepsPerFrame else available.toInt()
    }

    override fun toString(): String =
        "FixedStepper(fixedDelta=$fixedDelta, maxStepsPerFrame=$maxStepsPerFrame)"
}
