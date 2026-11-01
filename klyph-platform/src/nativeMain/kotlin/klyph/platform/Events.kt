package klyph.platform

import cnames.structs.SDL_Gamepad
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import sdl3.SDL_CloseGamepad
import sdl3.SDL_EVENT_GAMEPAD_ADDED
import sdl3.SDL_EVENT_GAMEPAD_AXIS_MOTION
import sdl3.SDL_EVENT_GAMEPAD_BUTTON_DOWN
import sdl3.SDL_EVENT_GAMEPAD_BUTTON_UP
import sdl3.SDL_EVENT_GAMEPAD_REMOVED
import sdl3.SDL_EVENT_KEY_DOWN
import sdl3.SDL_EVENT_KEY_UP
import sdl3.SDL_EVENT_MOUSE_BUTTON_DOWN
import sdl3.SDL_EVENT_MOUSE_BUTTON_UP
import sdl3.SDL_EVENT_MOUSE_MOTION
import sdl3.SDL_EVENT_MOUSE_WHEEL
import sdl3.SDL_EVENT_QUIT
import sdl3.SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED
import sdl3.SDL_EVENT_WINDOW_FOCUS_GAINED
import sdl3.SDL_EVENT_WINDOW_FOCUS_LOST
import sdl3.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED
import sdl3.SDL_Event
import sdl3.SDL_GetGamepadName
import sdl3.SDL_GetWindowFromID
import sdl3.SDL_GetWindowPixelDensity
import sdl3.SDL_MouseWheelDirection
import sdl3.SDL_OpenGamepad
import sdl3.SDL_PollEvent
import sdl3.SDL_WaitEventTimeout

/**
 * An event taken from the platform's event queue by [EventPump.pollOrNull].
 *
 * The set of events is closed, so a `when` over them is exhaustive. The pump skips platform
 * events that have no subclass here.
 *
 * Mouse positions are in window points with (0, 0) at the top-left corner of the client area, the
 * unit of [Window.width]; multiply by [Window.pixelDensity] for pixels.
 *
 * Samples live in the sandbox because they need a display.
 */
public sealed class PlatformEvent {
    /** Asks the application to quit, for example because the user closed the last window. */
    public object Quit : PlatformEvent() {
        override fun toString(): String = "Quit"
    }

    /**
     * Reports that a key was pressed: [key] is its label under the current layout and [scancode]
     * its physical position. [isRepeat] is `true` when the key is being held down and the event
     * comes from key repeat rather than a new press.
     */
    public class KeyDown(
        public val key: KeyCode,
        public val isRepeat: Boolean,
        public val scancode: Scancode,
    ) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is KeyDown && key == other.key && isRepeat == other.isRepeat && scancode == other.scancode

        override fun hashCode(): Int = 31 * (31 * key.hashCode() + isRepeat.hashCode()) + scancode.hashCode()

        override fun toString(): String = "KeyDown(key=$key, isRepeat=$isRepeat, scancode=$scancode)"
    }

    /**
     * Reports that a key was released: [key] is its label under the current layout and [scancode]
     * its physical position.
     */
    public class KeyUp(public val key: KeyCode, public val scancode: Scancode) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is KeyUp && key == other.key && scancode == other.scancode

        override fun hashCode(): Int = 31 * key.hashCode() + scancode.hashCode()

        override fun toString(): String = "KeyUp(key=$key, scancode=$scancode)"
    }

    /**
     * Reports that the mouse moved to ([x], [y]) in window points, by [dx] and [dy] points since
     * the previous [MouseMoved].
     *
     * Positive [dx] is to the right and positive [dy] is down, like the window's coordinates.
     */
    public class MouseMoved(
        public val x: Float,
        public val y: Float,
        public val dx: Float,
        public val dy: Float,
    ) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is MouseMoved && x.equals(other.x) && y.equals(other.y) && dx.equals(other.dx) && dy.equals(other.dy)

        override fun hashCode(): Int = 31 * (31 * (31 * x.hashCode() + y.hashCode()) + dx.hashCode()) + dy.hashCode()

        override fun toString(): String = "MouseMoved(x=$x, y=$y, dx=$dx, dy=$dy)"
    }

    /**
     * Reports that [button] was pressed with the mouse at ([x], [y]) in window points.
     *
     * [clicks] counts presses in quick succession: 1 for a single click, 2 for a double click, and
     * so on. Buttons past [MouseButton.X2] are not reported.
     */
    public class MouseButtonDown(
        public val button: MouseButton,
        public val x: Float,
        public val y: Float,
        public val clicks: Int,
    ) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is MouseButtonDown && button == other.button && x.equals(other.x) && y.equals(other.y) &&
                clicks == other.clicks

        override fun hashCode(): Int = 31 * (31 * (31 * button.hashCode() + x.hashCode()) + y.hashCode()) + clicks

        override fun toString(): String = "MouseButtonDown(button=$button, x=$x, y=$y, clicks=$clicks)"
    }

    /**
     * Reports that [button] was released with the mouse at ([x], [y]) in window points. Buttons
     * past [MouseButton.X2] are not reported.
     */
    public class MouseButtonUp(public val button: MouseButton, public val x: Float, public val y: Float) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is MouseButtonUp && button == other.button && x.equals(other.x) && y.equals(other.y)

        override fun hashCode(): Int = 31 * (31 * button.hashCode() + x.hashCode()) + y.hashCode()

        override fun toString(): String = "MouseButtonUp(button=$button, x=$x, y=$y)"
    }

    /**
     * Reports that the mouse wheel or a touchpad scrolled by [dx] and [dy].
     *
     * Positive [dx] scrolls right and positive [dy] scrolls up, the wheel rolled away from the
     * user. SDL reports the opposite signs when the system uses "natural" scrolling; the pump flips
     * them back, so the sign means the same on every system. One notch of a wheel is usually 1;
     * touchpads report fractions.
     */
    public class MouseWheel(public val dx: Float, public val dy: Float) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is MouseWheel && dx.equals(other.dx) && dy.equals(other.dy)

        override fun hashCode(): Int = 31 * dx.hashCode() + dy.hashCode()

        override fun toString(): String = "MouseWheel(dx=$dx, dy=$dy)"
    }

    /**
     * Reports that a gamepad named [name] was connected and is now known as [id].
     *
     * Gamepads that are already plugged in when [Subsystem.GAMEPAD] starts are reported this way
     * too, by the first polls after [Sdl.init], so no separate enumeration is needed. Some systems
     * (macOS, Windows with Windows.Gaming.Input) announce them a little later, after event
     * processing has begun. [name] is empty when SDL knows no name for the device.
     */
    public class GamepadConnected(public val id: GamepadId, public val name: String) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is GamepadConnected && id == other.id && name == other.name

        override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()

        override fun toString(): String = "GamepadConnected(id=$id, name=$name)"
    }

    /** Reports that the gamepad [id] was disconnected. The id is not used again. */
    public class GamepadDisconnected(public val id: GamepadId) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is GamepadDisconnected && id == other.id

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "GamepadDisconnected(id=$id)"
    }

    /** Reports that [button] was pressed on the gamepad [id]. */
    public class GamepadButtonDown(public val id: GamepadId, public val button: GamepadButton) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is GamepadButtonDown && id == other.id && button == other.button

        override fun hashCode(): Int = 31 * id.hashCode() + button.hashCode()

        override fun toString(): String = "GamepadButtonDown(id=$id, button=$button)"
    }

    /** Reports that [button] was released on the gamepad [id]. */
    public class GamepadButtonUp(public val id: GamepadId, public val button: GamepadButton) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is GamepadButtonUp && id == other.id && button == other.button

        override fun hashCode(): Int = 31 * id.hashCode() + button.hashCode()

        override fun toString(): String = "GamepadButtonUp(id=$id, button=$button)"
    }

    /**
     * Reports that [axis] on the gamepad [id] moved to [value].
     *
     * Stick values run from -1 to 1, negative being left or up; trigger values run from 0
     * (released) to 1 (fully pressed). Sticks rarely rest at exactly 0, so apply a dead zone.
     */
    public class GamepadAxisMoved(
        public val id: GamepadId,
        public val axis: GamepadAxis,
        public val value: Float,
    ) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is GamepadAxisMoved && id == other.id && axis == other.axis && value.equals(other.value)

        override fun hashCode(): Int = 31 * (31 * id.hashCode() + axis.hashCode()) + value.hashCode()

        override fun toString(): String = "GamepadAxisMoved(id=$id, axis=$axis, value=$value)"
    }

    /**
     * Reports that a window's drawable size changed to [pixelWidth] by [pixelHeight] pixels.
     *
     * The size is in pixels, not window coordinates, so it also changes when the window moves to a
     * display with a different pixel density.
     */
    public class WindowResized(public val pixelWidth: Int, public val pixelHeight: Int) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is WindowResized && pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight

        override fun hashCode(): Int = 31 * pixelWidth + pixelHeight

        override fun toString(): String = "WindowResized(pixelWidth=$pixelWidth, pixelHeight=$pixelHeight)"
    }

    /**
     * Reports that a window's display scale changed, typically because it moved to a display with
     * a different pixel density. [pixelDensity] is the window's [Window.pixelDensity] now.
     *
     * The display scale also covers the system's content scale, so the density can equal the one
     * reported before; compare it with the last known value before acting on it.
     */
    public class WindowDensityChanged(public val pixelDensity: Float) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is WindowDensityChanged && pixelDensity == other.pixelDensity

        override fun hashCode(): Int = pixelDensity.hashCode()

        override fun toString(): String = "WindowDensityChanged(pixelDensity=$pixelDensity)"
    }

    /** Reports that a window gained keyboard focus, so key events go to it. */
    public object WindowFocusGained : PlatformEvent() {
        override fun toString(): String = "WindowFocusGained"
    }

    /** Reports that a window lost keyboard focus, so key events stop going to it. */
    public object WindowFocusLost : PlatformEvent() {
        override fun toString(): String = "WindowFocusLost"
    }
}

/**
 * Drains SDL's event queue. Owns one native event record, so polling allocates nothing beyond the
 * returned [PlatformEvent]. [close] it when the pump is no longer needed, before [Sdl.quit].
 *
 * The pump opens every gamepad SDL announces, because SDL only delivers button and axis events for
 * open gamepads, and closes each one when it is disconnected or the pump is closed. Start
 * [Subsystem.GAMEPAD] to get gamepad events at all.
 *
 * Samples live in the sandbox because they need a display.
 */
public class EventPump : AutoCloseable {
    private val event = nativeHeap.alloc<SDL_Event>()
    private val gamepads = HashMap<UInt, CPointer<SDL_Gamepad>>()

    /**
     * Returns the next event from the queue, or `null` when the queue is empty.
     *
     * Events that [PlatformEvent] does not model are discarded, so `null` means no event of
     * interest is waiting. Call it in a loop until it returns `null`, once per frame.
     */
    public fun pollOrNull(): PlatformEvent? {
        while (SDL_PollEvent(event.ptr)) {
            translateOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Returns the next event from the queue, waiting up to [timeout] for one to arrive, or `null`
     * when none arrives in time. [Duration.INFINITE] waits until an event arrives.
     *
     * Use it instead of [pollOrNull] in a loop that has nothing to do between events. The wait can
     * run past [timeout] by the system's scheduling delay. [timeout] must not be negative.
     *
     * @throws IllegalArgumentException if [timeout] is negative.
     */
    public fun waitOrNull(timeout: Duration): PlatformEvent? {
        require(!timeout.isNegative()) { "Timeout $timeout is negative." }
        pollOrNull()?.let { return it }
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            val remaining = -deadline.elapsedNow()
            if (remaining <= Duration.ZERO) return null
            // SDL waits forever on -1; a finite wait of less than a millisecond still waits one.
            val milliseconds = if (timeout.isInfinite()) -1 else remaining.inWholeMilliseconds.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            if (!SDL_WaitEventTimeout(event.ptr, milliseconds)) return null
            translateOrNull()?.let { return it }
        }
    }

    /** Returns the [PlatformEvent] for the native event record, or `null` when it is not modelled. */
    private fun translateOrNull(): PlatformEvent? = when (event.type) {
        SDL_EVENT_QUIT -> PlatformEvent.Quit
        SDL_EVENT_KEY_DOWN ->
            PlatformEvent.KeyDown(KeyCode(event.key.key), isRepeat = event.key.repeat, scancode = Scancode(event.key.scancode))
        SDL_EVENT_KEY_UP -> PlatformEvent.KeyUp(KeyCode(event.key.key), scancode = Scancode(event.key.scancode))
        SDL_EVENT_MOUSE_MOTION -> PlatformEvent.MouseMoved(
            x = event.motion.x,
            y = event.motion.y,
            dx = event.motion.xrel,
            dy = event.motion.yrel,
        )
        SDL_EVENT_MOUSE_BUTTON_DOWN -> mouseButtonOrNull(event.button.button.toInt())?.let {
            PlatformEvent.MouseButtonDown(it, x = event.button.x, y = event.button.y, clicks = event.button.clicks.toInt())
        }
        SDL_EVENT_MOUSE_BUTTON_UP -> mouseButtonOrNull(event.button.button.toInt())?.let {
            PlatformEvent.MouseButtonUp(it, x = event.button.x, y = event.button.y)
        }
        SDL_EVENT_MOUSE_WHEEL -> {
            val wheel = event.wheel
            if (wheel.direction == SDL_MouseWheelDirection.SDL_MOUSEWHEEL_FLIPPED) {
                // Subtracting from zero flips the sign without turning 0 into -0.
                PlatformEvent.MouseWheel(dx = 0f - wheel.x, dy = 0f - wheel.y)
            } else {
                PlatformEvent.MouseWheel(dx = wheel.x, dy = wheel.y)
            }
        }
        SDL_EVENT_GAMEPAD_ADDED -> connectGamepadOrNull(event.gdevice.which)
        SDL_EVENT_GAMEPAD_REMOVED -> disconnectGamepadOrNull(event.gdevice.which)
        SDL_EVENT_GAMEPAD_BUTTON_DOWN ->
            PlatformEvent.GamepadButtonDown(GamepadId(event.gbutton.which), GamepadButton(event.gbutton.button.toInt()))
        SDL_EVENT_GAMEPAD_BUTTON_UP ->
            PlatformEvent.GamepadButtonUp(GamepadId(event.gbutton.which), GamepadButton(event.gbutton.button.toInt()))
        SDL_EVENT_GAMEPAD_AXIS_MOTION -> PlatformEvent.GamepadAxisMoved(
            GamepadId(event.gaxis.which),
            GamepadAxis(event.gaxis.axis.toInt()),
            normalizedAxisValue(event.gaxis.value),
        )
        SDL_EVENT_WINDOW_FOCUS_GAINED -> PlatformEvent.WindowFocusGained
        SDL_EVENT_WINDOW_FOCUS_LOST -> PlatformEvent.WindowFocusLost
        SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED ->
            PlatformEvent.WindowResized(pixelWidth = event.window.data1, pixelHeight = event.window.data2)
        SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> {
            // The event carries no value; a window destroyed since it was queued is skipped.
            val window = SDL_GetWindowFromID(event.window.windowID)
            val density = if (window == null) 0f else SDL_GetWindowPixelDensity(window)
            if (density > 0f) PlatformEvent.WindowDensityChanged(density) else null
        }
        else -> null
    }

    /**
     * Opens the gamepad [id] and returns its connection event, or `null` when it is already open or
     * SDL cannot open it. SDL sends a second ADDED event when a joystick gains a mapping.
     */
    private fun connectGamepadOrNull(id: UInt): PlatformEvent? {
        if (id in gamepads) return null
        val gamepad = SDL_OpenGamepad(id) ?: return null
        gamepads[id] = gamepad
        val name = SDL_GetGamepadName(gamepad)?.toKString().orEmpty()
        return PlatformEvent.GamepadConnected(GamepadId(id), name)
    }

    /** Closes the gamepad [id] and returns its disconnection event, or `null` when it was not open. */
    private fun disconnectGamepadOrNull(id: UInt): PlatformEvent? {
        val gamepad = gamepads.remove(id) ?: return null
        SDL_CloseGamepad(gamepad)
        return PlatformEvent.GamepadDisconnected(GamepadId(id))
    }

    override fun close() {
        for (gamepad in gamepads.values) SDL_CloseGamepad(gamepad)
        gamepads.clear()
        nativeHeap.free(event)
    }

    override fun toString(): String = "EventPump(gamepads=${gamepads.size})"
}
