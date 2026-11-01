package klyph.input

import klyph.platform.KeyCode
import klyph.platform.Scancode
import klyph.platform.toScancode

/**
 * Holds which keys are down and which went down or up this frame, read through [Input.keyboard].
 *
 * Keys are identified either by [Scancode], the physical position, or by [KeyCode], the label
 * under the current layout. Use scancodes for controls that depend on where a key sits, such as
 * movement on W, A, S and D, so they work on every layout. The [KeyCode] overloads look up the
 * key that carries the label now, through [KeyCode.toScancode], so their answer follows the
 * user's layout: [KeyCode.A] is the key that types `a`, which sits where Q sits on a French
 * layout. A key code that no key on the layout produces is never down.
 *
 * "Just pressed" is `true` for one frame after a key goes down, and "just released" for one frame
 * after it goes up; a key pressed and released within one frame reads as both. Key repeat does
 * not count as a press. When the window loses focus every held key is released, so it reads as
 * just released that frame.
 *
 * [toString] gives the number of keys down, for example `Keyboard(down=2)`.
 *
 * Samples live in the sandbox because they need a display.
 */
public class Keyboard internal constructor() {
    private val keys = ButtonStates(Scancode.COUNT)

    /** Returns `true` while the key at [scancode] is held down. */
    public fun isDown(scancode: Scancode): Boolean = keys.isDown(indexOf(scancode))

    /** Returns `true` if the key at [scancode] went down this frame. */
    public fun isJustPressed(scancode: Scancode): Boolean = keys.isJustPressed(indexOf(scancode))

    /** Returns `true` if the key at [scancode] went up this frame. */
    public fun isJustReleased(scancode: Scancode): Boolean = keys.isJustReleased(indexOf(scancode))

    /**
     * Returns `true` while the key that types [key] under the current layout is held down.
     *
     * The key is found through [KeyCode.toScancode] on every call, so the answer follows a layout
     * switch at once.
     */
    public fun isDown(key: KeyCode): Boolean = isDown(key.toScancode())

    /** Returns `true` if the key that types [key] under the current layout went down this frame. */
    public fun isJustPressed(key: KeyCode): Boolean = isJustPressed(key.toScancode())

    /** Returns `true` if the key that types [key] under the current layout went up this frame. */
    public fun isJustReleased(key: KeyCode): Boolean = isJustReleased(key.toScancode())

    internal val downCount: Int get() = keys.downCount

    internal fun press(scancode: Scancode) = keys.press(indexOf(scancode))

    internal fun release(scancode: Scancode) = keys.release(indexOf(scancode))

    internal fun releaseAll() = keys.releaseAll()

    internal fun clearEdges() = keys.clearEdges()

    override fun toString(): String = "Keyboard(down=${keys.downCount})"

    /** Returns the array index of [scancode], or -1 for [Scancode.UNKNOWN], which is never tracked. */
    private fun indexOf(scancode: Scancode): Int = if (scancode == Scancode.UNKNOWN) -1 else scancode.code.toInt()
}
