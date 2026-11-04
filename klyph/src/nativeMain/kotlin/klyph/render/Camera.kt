package klyph.render

import klyph.math.Rect
import klyph.math.Vec2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * A 2D view onto the world, centered on ([x], [y]) in world units.
 *
 * At [zoom] 1 one world unit is one point, the unit the window size and the mouse position are
 * given in, on every display; at zoom 2 world units appear twice as large. Screen coordinates are
 * points with the origin at the top-left corner of the viewport and +y pointing down, the same
 * orientation the world uses, so they match `DrawSpace.SCREEN` and `Mouse.position`. The renderer
 * turns points into pixels with the window's pixel density, so a game never multiplies by it.
 *
 * [rotation] is in radians, measured the same way as [klyph.entity.Transform.rotation]: a
 * positive rotation turns the camera clockwise, because y points down. The camera turns like an
 * entity with that rotation, so the world turns the opposite way, counter-clockwise, on screen,
 * and a sprite whose rotation equals the camera's appears upright. With a rotation of a quarter
 * turn, a point to the right of the camera in the world appears above the viewport center.
 *
 * [viewportWidth] and [viewportHeight] are the size of the drawing area in points. The engine
 * sets them from the window whenever it changes size; pass them to the constructor only for a
 * camera the engine does not drive.
 *
 * [bounds] keeps the view inside a rectangle of the world, such as a level's edges; see
 * [clampToBounds].
 *
 * [toString] prints the position, zoom, rotation, viewport and bounds, for example
 * `Camera(x=0.0, y=0.0, zoom=1.0, rotation=0.0, viewport=800.0x600.0, bounds=null)`.
 *
 * @throws IllegalArgumentException if [zoom] is not a positive finite number, or if
 * [viewportWidth] or [viewportHeight] is not a positive finite number.
 *
 * @sample samples.render.Cameras.worldToScreen
 */
public class Camera(
    /** Horizontal world position of the viewport center. */
    public var x: Float = 0f,
    /** Vertical world position of the viewport center. */
    public var y: Float = 0f,
    zoom: Float = 1f,
    /**
     * Rotation of the camera in radians, as in [klyph.entity.Transform.rotation]: positive turns
     * the camera clockwise, because y points down, so the world turns counter-clockwise on screen.
     */
    public var rotation: Float = 0f,
    viewportWidth: Float = 1f,
    viewportHeight: Float = 1f,
) {
    /**
     * Scale from world units to points: at 1 one world unit is one point, at 2 world units
     * appear twice as large.
     *
     * @throws IllegalArgumentException if set to a value that is not a positive finite number.
     */
    public var zoom: Float = zoom
        set(value) {
            requireZoom(value)
            field = value
        }

    /** Width of the viewport in points. Set by the engine from the window. */
    public var viewportWidth: Float = viewportWidth
        internal set(value) {
            requireViewport(value, "width")
            field = value
        }

    /** Height of the viewport in points. Set by the engine from the window. */
    public var viewportHeight: Float = viewportHeight
        internal set(value) {
            requireViewport(value, "height")
            field = value
        }

    /**
     * The world rectangle the view must stay inside, or `null`, the default, for no limit.
     *
     * [clampToBounds] moves the camera so that the visible area, [visibleWidth] by
     * [visibleHeight] around ([x], [y]), lies inside it, and centers the camera on the rectangle
     * along an axis where the view is larger than the rectangle. [lookAt] and [follow] clamp right
     * away, and the engine clamps `App.camera` once more just before it draws each frame, so
     * writing [x], [y] or [zoom] directly is cheap and still never shows outside the bounds.
     * Rotation is ignored: a rotated view is clamped as if it were upright.
     */
    public var bounds: Rect? = null

    init {
        requireZoom(zoom)
        requireViewport(viewportWidth, "width")
        requireViewport(viewportHeight, "height")
    }

    override fun toString(): String =
        "Camera(x=$x, y=$y, zoom=$zoom, rotation=$rotation, viewport=${viewportWidth}x$viewportHeight, bounds=$bounds)"
}

private fun requireZoom(zoom: Float) {
    require(zoom > 0f && zoom.isFinite()) { "Camera zoom $zoom is not a positive finite number." }
}

private fun requireViewport(size: Float, axis: String) {
    require(size > 0f && size.isFinite()) { "Viewport $axis $size is not a positive finite number." }
}

/**
 * Returns the screen position in points, origin top-left and +y down, at which the [world]
 * point appears through this camera.
 *
 * [screenToWorld] is the inverse.
 *
 * @sample samples.render.Cameras.worldToScreen
 */
public fun Camera.worldToScreen(world: Vec2): Vec2 {
    val c = cos(rotation)
    val s = sin(rotation)
    val dx = world.x - x
    val dy = world.y - y
    return Vec2(
        viewportWidth * 0.5f + zoom * (c * dx + s * dy),
        viewportHeight * 0.5f + zoom * (c * dy - s * dx),
    )
}

/**
 * Returns the world point that appears at the [screen] position in points, origin top-left and
 * +y down, through this camera.
 *
 * [worldToScreen] is the inverse.
 *
 * @sample samples.render.Cameras.worldToScreen
 */
public fun Camera.screenToWorld(screen: Vec2): Vec2 {
    val c = cos(rotation)
    val s = sin(rotation)
    val vx = (screen.x - viewportWidth * 0.5f) / zoom
    val vy = (screen.y - viewportHeight * 0.5f) / zoom
    return Vec2(x + c * vx - s * vy, y + s * vx + c * vy)
}

/**
 * Moves the camera so that the world point ([x], [y]) is at the center of the viewport, or as
 * close to it as [Camera.bounds] allows.
 *
 * Zoom and rotation are unchanged.
 *
 * @sample samples.render.Cameras.lookAt
 */
public fun Camera.lookAt(x: Float, y: Float) {
    this.x = x
    this.y = y
    clampToBounds()
}

/**
 * Moves the camera part of the way toward the world point ([x], [y]), so that called every frame
 * it eases after a moving target: the further away the target, the faster the camera moves.
 *
 * Over [delta] the camera covers `1 - exp(-smoothing * delta)` of the distance left, where
 * [smoothing] is a rate per second, at least 0. The result depends only on the time that passed,
 * not on the frame rate: at a smoothing of 5 the camera covers about 99% of a still target's
 * distance within a second, at 0 it stays put, and at [Float.POSITIVE_INFINITY] it jumps there as
 * [lookAt] does. The camera then clamps to [Camera.bounds]. Pass `App.time.delta` as [delta] from
 * a `LATE_UPDATE` system, after the target has moved. Pass [smoothing] and [delta] by name; ([x],
 * [y]) may go by position, as in `follow(ship.x, ship.y, smoothing = 5f, delta = app.time.delta)`.
 *
 * @throws IllegalArgumentException if [smoothing] is negative or `NaN`, or [delta] is negative.
 *
 * @sample samples.render.Cameras.follow
 */
public fun Camera.follow(x: Float, y: Float, smoothing: Float, delta: Duration) {
    require(smoothing >= 0f) { "Smoothing $smoothing is not a rate of at least zero." }
    require(!delta.isNegative()) { "Delta $delta is negative." }
    val seconds = delta.toDouble(DurationUnit.SECONDS).toFloat()
    // An infinite rate over no time is a jump: the target is reached as soon as any time is asked for.
    val amount = if (smoothing == Float.POSITIVE_INFINITY) 1f else 1f - exp(-smoothing * seconds)
    this.x += (x - this.x) * amount
    this.y += (y - this.y) * amount
    clampToBounds()
}

/**
 * Moves the camera part of the way toward [target] as [follow] with two coordinates does. Pass
 * [smoothing] and [delta] by name.
 *
 * @throws IllegalArgumentException if [smoothing] is negative or `NaN`, or [delta] is negative.
 *
 * @sample samples.render.Cameras.follow
 */
public fun Camera.follow(target: Vec2, smoothing: Float, delta: Duration) {
    follow(target.x, target.y, smoothing, delta)
}

/**
 * Moves the camera the least distance that puts its visible area inside [Camera.bounds], and does
 * nothing while the bounds are `null`.
 *
 * Along each axis where the visible area, [visibleWidth] or [visibleHeight], is wider than the
 * bounds, the camera is centered on the bounds instead, so the view shows the whole of them with
 * the same margin on both sides. Rotation is ignored. [lookAt] and [follow] call it, and so does
 * the engine before it draws each frame; call it after writing [Camera.x], [Camera.y] or
 * [Camera.zoom] directly to read the clamped position in the same frame.
 *
 * @sample samples.render.Cameras.bounds
 */
public fun Camera.clampToBounds() {
    val bounds = bounds ?: return
    x = clampAxis(x, visibleWidth * 0.5f, bounds.x, bounds.width)
    y = clampAxis(y, visibleHeight * 0.5f, bounds.y, bounds.height)
}

/** Returns [position] moved so that [half] either side of it lies within [size] from [start], or the middle of that range when it is narrower. */
private fun clampAxis(position: Float, half: Float, start: Float, size: Float): Float {
    if (2f * half >= size) return start + size * 0.5f
    return position.coerceIn(start + half, start + size - half)
}

/**
 * Width of the visible world area in world units: [Camera.viewportWidth] divided by
 * [Camera.zoom].
 *
 * Rotation is ignored, so for a rotated camera this is the width of the view along the camera's
 * own axes, not of the world-aligned box around it.
 *
 * @sample samples.render.Cameras.visibleWidth
 */
public val Camera.visibleWidth: Float
    get() = viewportWidth / zoom

/**
 * Height of the visible world area in world units: [Camera.viewportHeight] divided by
 * [Camera.zoom].
 *
 * Rotation is ignored, so for a rotated camera this is the height of the view along the camera's
 * own axes, not of the world-aligned box around it.
 *
 * @sample samples.render.Cameras.visibleWidth
 */
public val Camera.visibleHeight: Float
    get() = viewportHeight / zoom

/**
 * Writes the camera's view-projection matrix into the first 16 elements of [destination],
 * column-major, mapping world positions to clip space.
 *
 * The matrix translates by the negated position, rotates by the negated rotation, scales by the
 * zoom and then applies the orthographic projection of the viewport in points, centered with +y
 * down, so it agrees with [worldToScreen]. Clip space has no unit, so the pixel density cancels:
 * the render pass's viewport covers the target in pixels and the same matrix fills it on every
 * display. Allocates nothing.
 */
internal fun Camera.writeProjection(destination: FloatArray) {
    require(destination.size >= 16) { "Projection destination size ${destination.size} is less than 16." }
    val c = cos(rotation)
    val s = sin(rotation)
    val a = 2f * zoom / viewportWidth
    val b = 2f * zoom / viewportHeight
    // clipX = a * (c * dx + s * dy), clipY = -b * (c * dy - s * dx), with d = world - position.
    destination.fill(0f, 0, 16)
    destination[0] = a * c
    destination[1] = b * s
    destination[4] = a * s
    destination[5] = -b * c
    destination[10] = 1f
    destination[12] = -a * (c * x + s * y)
    destination[13] = -b * (s * x - c * y)
    destination[15] = 1f
}
