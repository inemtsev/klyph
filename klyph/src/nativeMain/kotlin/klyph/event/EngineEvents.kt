package klyph.event

import klyph.platform.GamepadButton
import klyph.platform.GamepadId
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.Scancode

/**
 * Published when the user asks the application to quit: the window close button, the system quit
 * shortcut (Cmd+Q on macOS), or an interrupt signal.
 */
public object QuitRequested {
    override fun toString(): String = "QuitRequested"
}

/**
 * Published when the window's drawable area changes size. [pixelWidth] and [pixelHeight] are the
 * new size in physical pixels, which differ from window coordinates on high-density displays.
 */
public class WindowResized(
    /** The new drawable width in physical pixels. */
    public val pixelWidth: Int,
    /** The new drawable height in physical pixels. */
    public val pixelHeight: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is WindowResized && other.pixelWidth == pixelWidth && other.pixelHeight == pixelHeight

    override fun hashCode(): Int = 31 * pixelWidth + pixelHeight

    override fun toString(): String = "WindowResized(pixelWidth=$pixelWidth, pixelHeight=$pixelHeight)"
}

/**
 * Published when the window's pixel density changes, typically because it moved to a display
 * with a different density. [pixelDensity] is the new number of pixels per point, as in
 * [klyph.platform.Window.pixelDensity].
 *
 * `App.camera` works in points, so the view keeps its framing without a change to its zoom; the
 * renderer draws the same points with the new number of pixels.
 */
public class WindowDensityChanged(
    /** The new number of pixels per point: 2 on a Retina display, 1 on a standard one. */
    public val pixelDensity: Float,
) {
    override fun equals(other: Any?): Boolean = other is WindowDensityChanged && other.pixelDensity == pixelDensity

    override fun hashCode(): Int = pixelDensity.hashCode()

    override fun toString(): String = "WindowDensityChanged(pixelDensity=$pixelDensity)"
}

/**
 * Published when a key goes down: [key] is its label under the current layout and [scancode] its
 * physical position. Holding the key publishes further events with [isRepeat] set to `true` at the
 * system's key repeat rate.
 *
 * For controls that only need to know whether a key is held, read `App.input.keyboard` instead.
 */
public class KeyPressed(
    /** The label of the key that went down, under the current keyboard layout. */
    public val key: KeyCode,
    /** `true` when the event comes from the system's key repeat rather than a new press. */
    public val isRepeat: Boolean,
    /** The physical position of the key that went down. */
    public val scancode: Scancode,
) {
    override fun equals(other: Any?): Boolean =
        other is KeyPressed && other.key == key && other.isRepeat == isRepeat && other.scancode == scancode

    override fun hashCode(): Int = 31 * (31 * key.hashCode() + isRepeat.hashCode()) + scancode.hashCode()

    override fun toString(): String = "KeyPressed(key=$key, isRepeat=$isRepeat, scancode=$scancode)"
}

/** Published when a key goes up: [key] is its label under the current layout and [scancode] its physical position. */
public class KeyReleased(
    /** The label of the key that went up, under the current keyboard layout. */
    public val key: KeyCode,
    /** The physical position of the key that went up. */
    public val scancode: Scancode,
) {
    override fun equals(other: Any?): Boolean = other is KeyReleased && other.key == key && other.scancode == scancode

    override fun hashCode(): Int = 31 * key.hashCode() + scancode.hashCode()

    override fun toString(): String = "KeyReleased(key=$key, scancode=$scancode)"
}

/**
 * Published when the mouse moves to ([x], [y]) in window points, by [dx] and [dy] points since
 * the previous move. Positive [dx] is to the right and positive [dy] is down.
 *
 * Several moves can arrive in one frame; `App.input.mouse` holds the last position and the summed
 * movement.
 */
public class MouseMoved(
    /** The new horizontal position in window points. */
    public val x: Float,
    /** The new vertical position in window points. */
    public val y: Float,
    /** The horizontal movement since the previous move, in points. */
    public val dx: Float,
    /** The vertical movement since the previous move, in points. */
    public val dy: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is MouseMoved && x.equals(other.x) && y.equals(other.y) && dx.equals(other.dx) && dy.equals(other.dy)

    override fun hashCode(): Int = 31 * (31 * (31 * x.hashCode() + y.hashCode()) + dx.hashCode()) + dy.hashCode()

    override fun toString(): String = "MouseMoved(x=$x, y=$y, dx=$dx, dy=$dy)"
}

/**
 * Published when [button] goes down with the mouse at ([x], [y]) in window points. [clicks]
 * counts presses in quick succession: 1 for a single click, 2 for a double click, and so on.
 */
public class MouseButtonPressed(
    /** The button that went down. */
    public val button: MouseButton,
    /** The horizontal position in window points. */
    public val x: Float,
    /** The vertical position in window points. */
    public val y: Float,
    /** The number of presses in quick succession, 1 for a single click. */
    public val clicks: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is MouseButtonPressed && button == other.button && x.equals(other.x) && y.equals(other.y) &&
            clicks == other.clicks

    override fun hashCode(): Int = 31 * (31 * (31 * button.hashCode() + x.hashCode()) + y.hashCode()) + clicks

    override fun toString(): String = "MouseButtonPressed(button=$button, x=$x, y=$y, clicks=$clicks)"
}

/** Published when [button] goes up with the mouse at ([x], [y]) in window points. */
public class MouseButtonReleased(
    /** The button that went up. */
    public val button: MouseButton,
    /** The horizontal position in window points. */
    public val x: Float,
    /** The vertical position in window points. */
    public val y: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is MouseButtonReleased && button == other.button && x.equals(other.x) && y.equals(other.y)

    override fun hashCode(): Int = 31 * (31 * button.hashCode() + x.hashCode()) + y.hashCode()

    override fun toString(): String = "MouseButtonReleased(button=$button, x=$x, y=$y)"
}

/**
 * Published when the mouse wheel or a touchpad scrolls by [dx] and [dy]. Positive [dx] scrolls
 * right and positive [dy] scrolls up, the wheel rolled away from the user, on every system. One
 * notch of a wheel is usually 1; touchpads report fractions.
 */
public class MouseWheelScrolled(
    /** The horizontal scroll amount. */
    public val dx: Float,
    /** The vertical scroll amount. */
    public val dy: Float,
) {
    override fun equals(other: Any?): Boolean = other is MouseWheelScrolled && dx.equals(other.dx) && dy.equals(other.dy)

    override fun hashCode(): Int = 31 * dx.hashCode() + dy.hashCode()

    override fun toString(): String = "MouseWheelScrolled(dx=$dx, dy=$dy)"
}

/**
 * Published when a gamepad named [name] is connected as [id]. Gamepads plugged in before the app
 * started are announced this way during the first frames. The pad is in `App.input.gamepads` by
 * the time the event is read.
 */
public class GamepadConnected(
    /** The id of the new gamepad, valid until it is disconnected. */
    public val id: GamepadId,
    /** The name SDL reports for the device, or an empty string when it knows none. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is GamepadConnected && id == other.id && name == other.name

    override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()

    override fun toString(): String = "GamepadConnected(id=$id, name=$name)"
}

/** Published when the gamepad [id] is disconnected. The id is not used again. */
public class GamepadDisconnected(
    /** The id of the gamepad that was disconnected. */
    public val id: GamepadId,
) {
    override fun equals(other: Any?): Boolean = other is GamepadDisconnected && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "GamepadDisconnected(id=$id)"
}

/** Published when [button] goes down on the gamepad [id]. */
public class GamepadButtonPressed(
    /** The gamepad the button belongs to. */
    public val id: GamepadId,
    /** The button that went down. */
    public val button: GamepadButton,
) {
    override fun equals(other: Any?): Boolean = other is GamepadButtonPressed && id == other.id && button == other.button

    override fun hashCode(): Int = 31 * id.hashCode() + button.hashCode()

    override fun toString(): String = "GamepadButtonPressed(id=$id, button=$button)"
}

/** Published when [button] goes up on the gamepad [id]. */
public class GamepadButtonReleased(
    /** The gamepad the button belongs to. */
    public val id: GamepadId,
    /** The button that went up. */
    public val button: GamepadButton,
) {
    override fun equals(other: Any?): Boolean = other is GamepadButtonReleased && id == other.id && button == other.button

    override fun hashCode(): Int = 31 * id.hashCode() + button.hashCode()

    override fun toString(): String = "GamepadButtonReleased(id=$id, button=$button)"
}

/** Published when the window gains keyboard focus. */
public object WindowFocusGained {
    override fun toString(): String = "WindowFocusGained"
}

/**
 * Published when the window loses keyboard focus, for example because the user switched to another
 * application. By then `App.input` has released every held key and button; a game typically
 * pauses here.
 */
public object WindowFocusLost {
    override fun toString(): String = "WindowFocusLost"
}
