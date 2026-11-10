package klyph.input

import klyph.platform.GamepadId
import klyph.platform.PlatformEvent
import klyph.render.Camera

/**
 * Holds the state of the keyboard, the mouse and every connected gamepad as of this frame, so a
 * system asks "is this key down?" instead of following key events. Read it through `App.input`.
 *
 * The engine updates it at the start of each frame's `Phase.INPUT`, from the platform events that
 * arrived since the previous frame, before any system runs; it stays the same for the rest of the
 * frame. Inside `Phase.FIXED_UPDATE`, "just pressed" and "just released" follow fixed steps
 * instead of frames: a change is seen by the first step after it, once, as
 * [Keyboard.isJustPressed] describes. The matching `klyph.event` events are published in the same
 * step, for code that needs every event in order. When the window loses focus, every held key and
 * button is released and reads as just released that frame, and every gamepad axis reads 0, so
 * nothing stays stuck while the window is in the background.
 *
 * [toString] gives the number of keys and mouse buttons down and the number of gamepads, for
 * example `Input(keysDown=2, mouseButtonsDown=0, gamepads=1)`.
 *
 * Not thread-safe. Samples live in the sandbox because they need a display.
 */
public class Input internal constructor() {
    private val connected = ArrayList<Gamepad>()
    private val clock = StepClock()

    /** The keyboard's keys. */
    public val keyboard: Keyboard = Keyboard(clock)

    /** The mouse's position, movement, wheel and buttons. */
    public val mouse: Mouse = Mouse(clock)

    /**
     * The connected gamepads, in the order they were connected. The list is a read-only view that
     * the engine updates at the start of a frame as pads come and go, so a reference to it stays
     * current.
     */
    public val gamepads: List<Gamepad> = connected

    /**
     * The radial dead zone [Gamepad.leftStick] and [Gamepad.rightStick] apply to every gamepad, as
     * a distance from the stick's center from 0 (none) up to but excluding 1. Starts at 0.15, which
     * hides the drift of most worn sticks.
     *
     * Setting it outside that range throws.
     *
     * @throws IllegalArgumentException if set to a value below 0 or not below 1.
     */
    public var deadZone: Float = DEFAULT_DEAD_ZONE
        set(value) {
            require(value >= 0f && value < 1f) { "Dead zone $value is outside 0 until 1." }
            field = value
            for (gamepad in connected) gamepad.deadZone = value
        }

    /** Returns the connected gamepad [id], or `null` when no gamepad with that id is connected. */
    public fun gamepadOrNull(id: GamepadId): Gamepad? {
        for (gamepad in connected) if (gamepad.id == id) return gamepad
        return null
    }

    /** Forgets the previous frame's just-pressed and just-released edges, mouse movement and scrolling. */
    internal fun beginFrame() {
        keyboard.clearEdges()
        mouse.beginFrame()
        for (gamepad in connected) gamepad.beginFrame()
    }

    /** Applies [event] to the state. Events that carry no input state are ignored. */
    internal fun handle(event: PlatformEvent) {
        when (event) {
            is PlatformEvent.KeyDown -> if (!event.isRepeat) keyboard.press(event.scancode)
            is PlatformEvent.KeyUp -> keyboard.release(event.scancode)
            is PlatformEvent.MouseMoved -> mouse.move(event.x, event.y, event.dx, event.dy)
            is PlatformEvent.MouseButtonDown -> {
                mouse.moveTo(event.x, event.y)
                mouse.press(event.button)
            }
            is PlatformEvent.MouseButtonUp -> {
                mouse.moveTo(event.x, event.y)
                mouse.release(event.button)
            }
            is PlatformEvent.MouseWheelScrolled -> mouse.scroll(event.dx, event.dy)
            is PlatformEvent.GamepadConnected ->
                if (gamepadOrNull(event.id) == null) connected.add(Gamepad(event.id, event.name, deadZone, clock))
            is PlatformEvent.GamepadDisconnected -> {
                val gamepad = gamepadOrNull(event.id) ?: return
                connected.remove(gamepad)
                gamepad.disconnect()
            }
            is PlatformEvent.GamepadButtonDown -> gamepadOrNull(event.id)?.press(event.button)
            is PlatformEvent.GamepadButtonUp -> gamepadOrNull(event.id)?.release(event.button)
            is PlatformEvent.GamepadAxisMoved -> gamepadOrNull(event.id)?.moveAxis(event.axis, event.value)
            PlatformEvent.WindowFocusLost -> {
                keyboard.releaseAll()
                mouse.releaseAll()
                for (gamepad in connected) gamepad.releaseAll()
            }
            PlatformEvent.Quit,
            PlatformEvent.WindowFocusGained,
            is PlatformEvent.WindowResized,
            is PlatformEvent.WindowDensityChanged -> {}
        }
    }

    /**
     * Starts a fixed step: until [endFixedStep], "just pressed" and "just released" answer for the
     * changes made since the previous step instead of this frame's. The frame loop calls it before
     * each `Phase.FIXED_UPDATE` step's systems run.
     */
    internal fun beginFixedStep() = clock.beginFixedStep()

    /** Ends the fixed step [beginFixedStep] started, so the frame's edges answer again. */
    internal fun endFixedStep() = clock.endFixedStep()

    /**
     * Finishes the frame's update: publishes the mouse's vectors and computes its world position
     * through [camera], whose screen coordinates are points.
     */
    internal fun endFrame(camera: Camera) {
        mouse.endFrame(camera)
    }

    override fun toString(): String =
        "Input(keysDown=${keyboard.downCount}, mouseButtonsDown=${mouse.downCount}, gamepads=${connected.size})"

    private companion object {
        const val DEFAULT_DEAD_ZONE = 0.15f
    }
}
