package klyph.input

/**
 * Tells the button states of one [Input] which fixed step the frame loop is in, so that "just
 * pressed" can mean "since the previous fixed step" inside `Phase.FIXED_UPDATE`.
 *
 * [step] numbers the fixed steps from 1: inside a step it is that step's number, and between steps
 * it is the number of the last step that ran, 0 before the first. A button that changes between
 * steps is stamped with `step + 1`, the step that will see the change.
 */
internal class StepClock {
    /** Number of the running fixed step, or of the last one that ran when none is running. */
    var step: Long = 0
        private set

    /** `true` while a fixed step runs. */
    var isInFixedStep: Boolean = false
        private set

    /** Starts the next fixed step. */
    fun beginFixedStep() {
        check(!isInFixedStep) { "Fixed step has already begun." }
        step++
        isInFixedStep = true
    }

    /** Ends the running fixed step. */
    fun endFixedStep() {
        check(isInFixedStep) { "No fixed step is running." }
        isInFixedStep = false
    }

    override fun toString(): String = "StepClock(step=$step, isInFixedStep=$isInFixedStep)"
}
