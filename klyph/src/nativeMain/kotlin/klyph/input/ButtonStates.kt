package klyph.input

/**
 * Holds the held state and this frame's press and release edges of [count] buttons, indexed from
 * zero, in flat arrays.
 *
 * An edge is recorded only on a change: [press] of a held button and [release] of a released one
 * do nothing. A press and a release in the same frame therefore leave both edges set, so a tap
 * shorter than a frame still reads as just pressed and just released.
 */
internal class ButtonStates(val count: Int) {
    private val down = ByteArray(count)
    private val pressed = ByteArray(count)
    private val released = ByteArray(count)
    private var hasEdges = false

    /** Number of buttons held now. */
    var downCount: Int = 0
        private set

    fun isDown(index: Int): Boolean = index in 0 until count && down[index] != NONE

    fun isJustPressed(index: Int): Boolean = index in 0 until count && pressed[index] != NONE

    fun isJustReleased(index: Int): Boolean = index in 0 until count && released[index] != NONE

    /** Marks the button [index] held and records a press edge if it was up. Ignores indices out of range. */
    fun press(index: Int) {
        if (index !in 0 until count || down[index] != NONE) return
        down[index] = SET
        pressed[index] = SET
        hasEdges = true
        downCount++
    }

    /** Marks the button [index] up and records a release edge if it was held. Ignores indices out of range. */
    fun release(index: Int) {
        if (index !in 0 until count || down[index] == NONE) return
        down[index] = NONE
        released[index] = SET
        hasEdges = true
        downCount--
    }

    /** Releases every held button, recording a release edge for each. */
    fun releaseAll() {
        if (downCount == 0) return
        for (i in 0 until count) release(i)
    }

    /** Forgets this frame's edges; held buttons stay held. */
    fun clearEdges() {
        if (!hasEdges) return
        pressed.fill(NONE)
        released.fill(NONE)
        hasEdges = false
    }

    /** Forgets everything: no button is held and no edge is recorded. */
    fun clear() {
        down.fill(NONE)
        pressed.fill(NONE)
        released.fill(NONE)
        hasEdges = false
        downCount = 0
    }

    override fun toString(): String = "ButtonStates(count=$count, down=$downCount)"

    private companion object {
        const val NONE: Byte = 0
        const val SET: Byte = 1
    }
}
