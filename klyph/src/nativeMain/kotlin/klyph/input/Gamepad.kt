package klyph.input

import klyph.math.Vec2
import klyph.platform.GamepadAxis
import klyph.platform.GamepadButton
import klyph.platform.GamepadId
import kotlin.math.sqrt

/**
 * Holds the axes and buttons of one gamepad, read through [Input.gamepads] or
 * [Input.gamepadOrNull].
 *
 * Buttons and axes follow SDL's standard layout, named by position: [GamepadButton.SOUTH] is the
 * bottom face button on every pad. "Just pressed" is `true` for one frame after a button goes
 * down, and "just released" for one frame after it goes up; a press within one frame reads as
 * both. Inside `Phase.FIXED_UPDATE` they are `true` for one fixed step instead, as [isJustPressed]
 * describes. When the window loses focus every held button is released and every axis reads 0.
 *
 * A gamepad object lives for one connection. When the pad is disconnected it leaves
 * [Input.gamepads], [isConnected] turns `false` and every button and axis reads as released, but
 * the object stays safe to read. Plugged back in, the pad gets a new [id] and a new object.
 *
 * [toString] gives the id, the name and the connection state, for example
 * `Gamepad(id=GamepadId(3), name=Xbox Controller, isConnected=true)`.
 *
 * Samples live in the sandbox because they need a display.
 */
public class Gamepad internal constructor(
    /** Identifies this gamepad for as long as it stays connected. */
    public val id: GamepadId,
    /** The name SDL reports for the device, or an empty string when it knows none. */
    public val name: String,
    deadZone: Float,
    clock: StepClock,
) {
    private val buttons = ButtonStates(GamepadButton.COUNT, clock)
    private val axes = FloatArray(GamepadAxis.COUNT)
    private var stickCacheValid = false
    private var leftStickCache = Vec2.ZERO
    private var rightStickCache = Vec2.ZERO

    /** The radial dead zone [leftStick] and [rightStick] apply, kept equal to [Input.deadZone]. */
    internal var deadZone: Float = deadZone
        set(value) {
            if (field != value) stickCacheValid = false
            field = value
        }

    /** `true` until the gamepad is disconnected, `false` after. */
    public var isConnected: Boolean = true
        private set

    /**
     * Returns the raw value of [axis], with no dead zone: from -1 to 1 for a stick, negative being
     * left or up, and from 0 (released) to 1 (fully pressed) for a trigger.
     *
     * Sticks rarely rest at exactly 0, so read them through [leftStick] and [rightStick] unless the
     * game applies its own dead zone. An axis the pad has not reported yet, and an axis index
     * outside the standard layout, read 0.
     */
    public fun axis(axis: GamepadAxis): Float = if (axis.index in axes.indices) axes[axis.index] else 0f

    /**
     * The left stick's position with [Input.deadZone] applied: x from -1 (left) to 1 (right) and y
     * from -1 (up) to 1 (down).
     *
     * The dead zone is radial: a stick whose distance from the center is below it reads
     * [Vec2.ZERO]. Past it, the distance is rescaled so that it grows from 0 at the edge of the dead
     * zone to 1 at full tilt, keeping the direction, so there is no jump when the stick leaves the
     * dead zone. With the default dead zone of 0.15, a stick pushed to 0.5 reads about 0.41.
     */
    public val leftStick: Vec2
        get() {
            updateSticks()
            return leftStickCache
        }

    /**
     * The right stick's position with [Input.deadZone] applied, in the same range and with the same
     * rescaling as [leftStick].
     */
    public val rightStick: Vec2
        get() {
            updateSticks()
            return rightStickCache
        }

    /** Returns `true` while [button] is held down. */
    public fun isDown(button: GamepadButton): Boolean = buttons.isDown(button.index)

    /**
     * Returns `true` if [button] went down this frame.
     *
     * Inside `Phase.FIXED_UPDATE` it answers for fixed steps instead of frames: it is `true` in the
     * first step after the button went down, and in no other, even when frames ran no step in
     * between or a frame runs several steps. A button pressed and released before the step reads as
     * both just pressed and just released in it.
     */
    public fun isJustPressed(button: GamepadButton): Boolean = buttons.isJustPressed(button.index)

    /**
     * Returns `true` if [button] went up this frame.
     *
     * Inside `Phase.FIXED_UPDATE` it answers for fixed steps instead of frames: it is `true` in the
     * first step after the button went up, and in no other, even when frames ran no step in between
     * or a frame runs several steps. A button pressed and released before the step reads as both
     * just pressed and just released in it.
     */
    public fun isJustReleased(button: GamepadButton): Boolean = buttons.isJustReleased(button.index)

    internal fun beginFrame() = buttons.clearEdges()

    internal fun press(button: GamepadButton) = buttons.press(button.index)

    internal fun release(button: GamepadButton) = buttons.release(button.index)

    internal fun moveAxis(axis: GamepadAxis, value: Float) {
        if (axis.index !in axes.indices) return
        axes[axis.index] = value
        stickCacheValid = false
    }

    /** Releases every button, recording a release edge for each held one, and centers every axis. */
    internal fun releaseAll() {
        buttons.releaseAll()
        centerAxes()
    }

    /** Marks the gamepad disconnected and forgets all its state, edges included. */
    internal fun disconnect() {
        isConnected = false
        buttons.clear()
        centerAxes()
    }

    override fun toString(): String = "Gamepad(id=$id, name=$name, isConnected=$isConnected)"

    private fun centerAxes() {
        axes.fill(0f)
        stickCacheValid = false
    }

    private fun updateSticks() {
        if (stickCacheValid) return
        leftStickCache = stickOf(leftStickCache, axis(GamepadAxis.LEFT_X), axis(GamepadAxis.LEFT_Y))
        rightStickCache = stickOf(rightStickCache, axis(GamepadAxis.RIGHT_X), axis(GamepadAxis.RIGHT_Y))
        stickCacheValid = true
    }

    /** Returns the stick ([x], [y]) with the dead zone applied, reusing [previous] when it is equal. */
    private fun stickOf(previous: Vec2, x: Float, y: Float): Vec2 {
        val length = sqrt(x * x + y * y)
        if (length <= deadZone) return Vec2.ZERO
        val scale = ((length - deadZone) / (1f - deadZone)).coerceAtMost(1f) / length
        val sx = x * scale
        val sy = y * scale
        return if (previous.x == sx && previous.y == sy) previous else Vec2(sx, sy)
    }
}

/**
 * Returns `true` while [button] is held on any connected gamepad of the list, such as
 * `App.input.gamepads`, for a game that lets whichever pad is at hand play. An empty list gives
 * `false`.
 *
 * Samples live in the sandbox because they need a display.
 */
public fun List<Gamepad>.isDown(button: GamepadButton): Boolean {
    for (i in indices) {
        val gamepad = this[i]
        if (gamepad.isConnected && gamepad.isDown(button)) return true
    }
    return false
}

/**
 * Returns `true` if [button] went down this frame on any connected gamepad of the list. An empty
 * list gives `false`. Inside `Phase.FIXED_UPDATE` it answers for fixed steps, as
 * [Gamepad.isJustPressed] does.
 *
 * Samples live in the sandbox because they need a display.
 */
public fun List<Gamepad>.isJustPressed(button: GamepadButton): Boolean {
    for (i in indices) {
        val gamepad = this[i]
        if (gamepad.isConnected && gamepad.isJustPressed(button)) return true
    }
    return false
}

/**
 * Returns `true` if [button] went up this frame on any connected gamepad of the list. An empty
 * list gives `false`. Inside `Phase.FIXED_UPDATE` it answers for fixed steps, as
 * [Gamepad.isJustReleased] does.
 *
 * Samples live in the sandbox because they need a display.
 */
public fun List<Gamepad>.isJustReleased(button: GamepadButton): Boolean {
    for (i in indices) {
        val gamepad = this[i]
        if (gamepad.isConnected && gamepad.isJustReleased(button)) return true
    }
    return false
}
