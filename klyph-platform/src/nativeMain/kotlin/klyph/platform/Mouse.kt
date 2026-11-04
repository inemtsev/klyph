package klyph.platform

/**
 * A mouse button, numbered from zero: [LEFT] is 0 and [X2] is 4.
 *
 * Every button [EventPump] reports has an [index] below [COUNT], so button state can live in a flat
 * array indexed by [index].
 */
public value class MouseButton(public val index: Int) {
    override fun toString(): String = "MouseButton($index)"

    /** Holds the mouse buttons. */
    public companion object {
        /** The primary button, usually the left one. */
        public val LEFT: MouseButton = MouseButton(0)
        /** The middle button, often a press on the wheel. */
        public val MIDDLE: MouseButton = MouseButton(1)
        /** The secondary button, usually the right one. */
        public val RIGHT: MouseButton = MouseButton(2)
        /** The first extra button, often "back" on the side of the mouse. */
        public val X1: MouseButton = MouseButton(3)
        /** The second extra button, often "forward" on the side of the mouse. */
        public val X2: MouseButton = MouseButton(4)

        /** The number of mouse buttons; every button [EventPump] reports has an index below it. */
        public const val COUNT: Int = 5
    }
}

/** Returns the [MouseButton] for SDL's one-based button number [button], or `null` past [MouseButton.X2]. */
internal fun mouseButtonOrNull(button: Int): MouseButton? =
    if (button in 1..MouseButton.COUNT) MouseButton(button - 1) else null
