package klyph.render

import klyph.math.Vec2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 2D view onto the world, centered on ([x], [y]) in world units.
 *
 * At [zoom] 1 one world unit is one screen pixel; at zoom 2 world units appear twice as large.
 * The engine's own camera, `App.camera`, starts at the window's pixel density, so there one world
 * unit is one logical point.
 * Screen coordinates are pixels with the origin at the top-left corner of the viewport and +y
 * pointing down, the same orientation the world uses.
 *
 * [rotation] is in radians, measured the same way as [klyph.entity.Transform.rotation]: a
 * positive rotation turns the camera clockwise, because y points down. The camera turns like an
 * entity with that rotation, so the world turns the opposite way, counter-clockwise, on screen,
 * and a sprite whose rotation equals the camera's appears upright. With a rotation of a quarter
 * turn, a point to the right of the camera in the world appears above the viewport center.
 *
 * [viewportWidth] and [viewportHeight] are the size of the drawing area in pixels. The engine
 * sets them from the window every frame; pass them to the constructor only for a camera the
 * engine does not drive.
 *
 * [toString] prints the position, zoom, rotation and viewport, for example
 * `Camera(x=0.0, y=0.0, zoom=1.0, rotation=0.0, viewport=800 x 600)`.
 *
 * @throws IllegalArgumentException if [zoom] is not a positive finite number, or if
 * [viewportWidth] or [viewportHeight] is not positive.
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
    viewportWidth: Int = 1,
    viewportHeight: Int = 1,
) {
    /**
     * Scale from world units to screen pixels. Setting a value that is not a positive finite
     * number throws [IllegalArgumentException].
     */
    public var zoom: Float = zoom
        set(value) {
            requireZoom(value)
            field = value
        }

    /** Width of the viewport in pixels. Set by the engine from the window. */
    public var viewportWidth: Int = viewportWidth
        internal set(value) {
            require(value > 0) { "Viewport width $value is not positive." }
            field = value
        }

    /** Height of the viewport in pixels. Set by the engine from the window. */
    public var viewportHeight: Int = viewportHeight
        internal set(value) {
            require(value > 0) { "Viewport height $value is not positive." }
            field = value
        }

    init {
        requireZoom(zoom)
        require(viewportWidth > 0) { "Viewport width $viewportWidth is not positive." }
        require(viewportHeight > 0) { "Viewport height $viewportHeight is not positive." }
    }

    override fun toString(): String =
        "Camera(x=$x, y=$y, zoom=$zoom, rotation=$rotation, viewport=$viewportWidth x $viewportHeight)"
}

private fun requireZoom(zoom: Float) {
    require(zoom > 0f && zoom.isFinite()) { "Camera zoom $zoom is not a positive finite number." }
}

/**
 * Returns the screen position in pixels, origin top-left and +y down, at which the [world]
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
 * Returns the world point that appears at the [screen] position in pixels, origin top-left and
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
 * Moves the camera so that the world point ([x], [y]) is at the center of the viewport.
 *
 * Zoom and rotation are unchanged.
 *
 * @sample samples.render.Cameras.lookAt
 */
public fun Camera.lookAt(x: Float, y: Float) {
    this.x = x
    this.y = y
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
 * zoom and then applies the pixel orthographic projection centered on the viewport with +y down,
 * so it agrees with [worldToScreen]. Allocates nothing.
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
