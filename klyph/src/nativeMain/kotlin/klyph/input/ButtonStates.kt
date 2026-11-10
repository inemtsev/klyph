package klyph.input

/**
 * Holds the held state and the press and release edges of [count] buttons, indexed from zero, in
 * flat arrays.
 *
 * An edge is recorded only on a change: [press] of a held button and [release] of a released one
 * do nothing. Each edge is kept twice. The frame edge lasts until [clearEdges], which the engine
 * calls at the start of every frame, so a press and a release in the same frame leave both set and
 * a tap shorter than a frame still reads as just pressed and just released. The step edge is the
 * number of the fixed step that will see the change, `clock.step + 1` when it happens; it is never
 * cleared, because only that step's number matches it.
 *
 * Outside a fixed step [isJustPressed] and [isJustReleased] answer the frame edge. Inside one, as
 * [clock] tells, they answer whether the edge's step is the running step, so a change is seen by
 * exactly one step, the first after it, however many frames ran no step and however many steps a
 * frame runs.
 */
internal class ButtonStates(val count: Int, private val clock: StepClock) {
    private val down = ByteArray(count)
    private val pressed = ByteArray(count)
    private val released = ByteArray(count)
    private val pressedStep = LongArray(count)
    private val releasedStep = LongArray(count)
    private var hasEdges = false

    /** Number of buttons held now. */
    var downCount: Int = 0
        private set

    fun isDown(index: Int): Boolean = index in 0 until count && down[index] != NONE

    fun isJustPressed(index: Int): Boolean {
        if (index !in 0 until count) return false
        return if (clock.isInFixedStep) pressedStep[index] == clock.step else pressed[index] != NONE
    }

    fun isJustReleased(index: Int): Boolean {
        if (index !in 0 until count) return false
        return if (clock.isInFixedStep) releasedStep[index] == clock.step else released[index] != NONE
    }

    /** Marks the button [index] held and records a press edge if it was up. Ignores indices out of range. */
    fun press(index: Int) {
        if (index !in 0 until count || down[index] != NONE) return
        down[index] = SET
        pressed[index] = SET
        pressedStep[index] = clock.step + 1
        hasEdges = true
        downCount++
    }

    /** Marks the button [index] up and records a release edge if it was held. Ignores indices out of range. */
    fun release(index: Int) {
        if (index !in 0 until count || down[index] == NONE) return
        down[index] = NONE
        released[index] = SET
        releasedStep[index] = clock.step + 1
        hasEdges = true
        downCount--
    }

    /** Releases every held button, recording a release edge for each. */
    fun releaseAll() {
        if (downCount == 0) return
        for (i in 0 until count) release(i)
    }

    /** Forgets this frame's edges; held buttons stay held, and step edges stay for their step. */
    fun clearEdges() {
        if (!hasEdges) return
        pressed.fill(NONE)
        released.fill(NONE)
        hasEdges = false
    }

    /** Forgets everything: no button is held and no edge is recorded, for a frame or for a step. */
    fun clear() {
        down.fill(NONE)
        pressed.fill(NONE)
        released.fill(NONE)
        pressedStep.fill(0)
        releasedStep.fill(0)
        hasEdges = false
        downCount = 0
    }

    override fun toString(): String = "ButtonStates(count=$count, down=$downCount)"

    private companion object {
        const val NONE: Byte = 0
        const val SET: Byte = 1
    }
}
