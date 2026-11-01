package klyph.input

import klyph.math.Vec2
import klyph.platform.MouseButton
import klyph.render.Camera
import klyph.render.screenToWorld

/**
 * Holds the mouse's position, its movement and scrolling this frame, and which buttons are down,
 * read through [Input.mouse].
 *
 * Positions are in window points with (0, 0) at the top-left corner of the window's client area,
 * the unit of `Window.width`. The position is (0, 0) until the mouse first moves over the window.
 * "Just pressed" is `true` for one frame after a button goes down, and "just released" for one
 * frame after it goes up; a click within one frame reads as both. When the window loses focus
 * every held button is released.
 *
 * [toString] gives the position and the number of buttons down, for example
 * `Mouse(position=Vec2(10.0, 20.0), down=1)`.
 *
 * Samples live in the sandbox because they need a display.
 */
public class Mouse internal constructor() {
    private val buttons = ButtonStates(MouseButton.COUNT)
    private var x = 0f
    private var y = 0f
    private var deltaX = 0f
    private var deltaY = 0f
    private var wheelX = 0f
    private var wheelY = 0f

    /** The mouse's position in window points, as of the last event of this frame. */
    public var position: Vec2 = Vec2.ZERO
        private set

    /**
     * How far the mouse moved this frame in window points, the sum of every movement reported this
     * frame. Positive x is to the right and positive y is down. It is zero in a frame without
     * movement.
     */
    public var delta: Vec2 = Vec2.ZERO
        private set

    /**
     * How far the wheel or touchpad scrolled this frame, the sum of every scroll reported this frame.
     * Positive x scrolls right and positive y scrolls up, the wheel rolled away from the user, on
     * every system. One notch of a wheel is usually 1; touchpads report fractions. It is zero in a
     * frame without scrolling.
     */
    public var wheel: Vec2 = Vec2.ZERO
        private set

    /**
     * The world point under the mouse, through `App.camera` as the camera stood at the start of
     * this frame's `Phase.INPUT`.
     *
     * A system that moves the camera later in the frame, such as a camera that follows the player
     * in `Phase.LATE_UPDATE`, makes this value one frame behind the picture. A game that needs the
     * point under the camera as it is now calls `camera.screenToWorld` with [position] multiplied by
     * the window's pixel density.
     */
    public var worldPosition: Vec2 = Vec2.ZERO
        private set

    /** Returns `true` while [button] is held down. */
    public fun isDown(button: MouseButton): Boolean = buttons.isDown(button.index)

    /** Returns `true` if [button] went down this frame. */
    public fun isJustPressed(button: MouseButton): Boolean = buttons.isJustPressed(button.index)

    /** Returns `true` if [button] went up this frame. */
    public fun isJustReleased(button: MouseButton): Boolean = buttons.isJustReleased(button.index)

    internal fun beginFrame() {
        buttons.clearEdges()
        deltaX = 0f
        deltaY = 0f
        wheelX = 0f
        wheelY = 0f
    }

    internal fun moveTo(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    internal fun move(x: Float, y: Float, dx: Float, dy: Float) {
        moveTo(x, y)
        deltaX += dx
        deltaY += dy
    }

    internal fun scroll(dx: Float, dy: Float) {
        wheelX += dx
        wheelY += dy
    }

    internal fun press(button: MouseButton) = buttons.press(button.index)

    internal fun release(button: MouseButton) = buttons.release(button.index)

    internal val downCount: Int get() = buttons.downCount

    internal fun releaseAll() = buttons.releaseAll()

    /**
     * Publishes this frame's position, delta and wheel as [Vec2] values, reusing the previous ones
     * when they did not change, and computes [worldPosition] through [camera], whose screen
     * coordinates are pixels, [pixelDensity] pixels per point.
     */
    internal fun endFrame(camera: Camera, pixelDensity: Float) {
        if (position.x != x || position.y != y) position = Vec2(x, y)
        delta = vectorOf(delta, deltaX, deltaY)
        wheel = vectorOf(wheel, wheelX, wheelY)
        worldPosition = camera.screenToWorld(Vec2(x * pixelDensity, y * pixelDensity))
    }

    override fun toString(): String = "Mouse(position=$position, down=${buttons.downCount})"

    /** Returns [previous] if it already holds ([x], [y]), [Vec2.ZERO] for zero, or a new vector. */
    private fun vectorOf(previous: Vec2, x: Float, y: Float): Vec2 = when {
        previous.x == x && previous.y == y -> previous
        x == 0f && y == 0f -> Vec2.ZERO
        else -> Vec2(x, y)
    }
}
