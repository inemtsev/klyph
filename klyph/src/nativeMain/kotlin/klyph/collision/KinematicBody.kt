package klyph.collision

import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.scene.SceneStack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A component that moves its entity by [dx] and [dy] world units per second and stops it at every
 * [Solid] in the way, such as a platformer's hero, a walking enemy or a crate that falls and lands.
 *
 * The entity needs a `Transform`, a [Collider] whose shape is a [Shape.Box], and no parent, because
 * the body moves its `Transform` in world space. Its box is placed as [Collider] describes, offset
 * and scaled unless [Collider.scalesWithTransform] is `false`. In every fixed step, after the
 * `FIXED_UPDATE` systems have run and after every `Velocity` has been applied, the engine moves each
 * body across by `dx` times the step length and stops it at the first solid it would enter, then
 * moves it down or up by `dy` times the step length in the same way. A stop leaves a gap of
 * [Collisions.CONTACT_GAP] units between the box and the solid, so the body never overlaps it, and zeroes
 * the velocity along that axis. Moving across and then up or down is exact for upright boxes; there
 * are no slopes and no moving platforms.
 *
 * After the move, [isOnFloor], [isOnWall], [isOnCeiling], [wallNormalX] and [floorOrNull] say what
 * the body touched. A body that stood on a floor before the move and is not moving up looks
 * [Collisions.FLOOR_SNAP_DISTANCE] units further down after it and lands on a floor it finds there, so a
 * body walking down a small step stays on the floor.
 *
 * The engine adds no gravity: a `FIXED_UPDATE` system adds it to [dy], as in
 * `body.dy += 1800f * time.deltaSeconds`, and sets [dx] from the input. A body stops only at solids
 * whose collider's layers are in its own collider's mask. Bodies do not stop each other. A body
 * that starts a move inside a solid, because it was placed there or the solid was moved onto it,
 * is not stopped by that solid and can move out of it. A body owned by a scene frozen under a
 * pushed scene stands still and keeps its state. A body whose entity also has a `Velocity` or a
 * [Solid], has no `Transform` or [Collider], has a collider that is not a box, has a parent or has a
 * speed that is not finite makes the next move throw [IllegalStateException] naming the entity.
 *
 * Interpolated bodies are drawn blended between their last two steps, as with `Velocity`.
 *
 * [toString] gives
 * `KinematicBody(dx=0.0, dy=0.0, isOnFloor=true, isOnWall=false, wallNormalX=0.0, isOnCeiling=false)`.
 *
 * @sample samples.collision.Bodies.kinematicBody
 */
public class KinematicBody(
    /** Horizontal speed in world units per second; positive moves right. A stop at a wall zeroes it. */
    public var dx: Float = 0f,
    /** Vertical speed in world units per second; positive moves down. A landing or a bump on a ceiling zeroes it. */
    public var dy: Float = 0f,
) {
    /** Whether the last move ended on a floor: it landed moving down, or the floor check below found one. */
    public var isOnFloor: Boolean = false
        internal set

    /** Whether the last move ran into a wall moving across. */
    public var isOnWall: Boolean = false
        internal set

    /** Whether the last move ran into a ceiling moving up. */
    public var isOnCeiling: Boolean = false
        internal set

    /**
     * The direction pointing away from the wall the last move ran into: -1 for a wall on the
     * body's right, 1 for one on its left, 0 when [isOnWall] is `false`. A wall jump pushes off with
     * `dx = wallNormalX * speed`.
     */
    public var wallNormalX: Float = 0f
        internal set

    /** The raw handle of the solid under the body after the last move, or [NO_FLOOR]. */
    internal var floorRaw: Long = NO_FLOOR

    /** Returns the [Solid] entity the body stands on after the last move, or `null` while [isOnFloor] is `false`. */
    public val floorOrNull: Entity?
        get() = if (floorRaw == NO_FLOOR) null else Entity(floorRaw)

    /** Whether [dropThrough] was called since the last move. */
    internal var isDropRequested: Boolean = false

    /**
     * The y below which the body keeps falling through one-way solids after [dropThrough]: one-way
     * solids whose top is at or above it are ignored. Negative infinity when the body is not dropping.
     */
    internal var dropLine: Float = Float.NEGATIVE_INFINITY

    /**
     * Makes the body fall through the [Passable.FROM_BELOW] solids it stands on, a drop down from a
     * one-way ledge. From the next move on, the body ignores every one-way solid whose top is at the
     * height of its feet, until it has fallen below that height, lands on something else or moves
     * up. Solids that are not one-way still stop it, and one-way solids further down still catch it.
     * Calling it in the air drops through ledges at the current height of the feet only.
     *
     * @sample samples.collision.Bodies.dropThrough
     */
    public fun dropThrough() {
        isDropRequested = true
    }

    /** Clears the contact state at the start of a move. */
    internal fun clearContacts() {
        isOnFloor = false
        isOnWall = false
        isOnCeiling = false
        wallNormalX = 0f
        floorRaw = NO_FLOOR
    }

    /** Returns `KinematicBody(dx=0.0, dy=0.0, isOnFloor=true, …)`. The format may change. */
    override fun toString(): String =
        "KinematicBody(dx=$dx, dy=$dy, isOnFloor=$isOnFloor, isOnWall=$isOnWall, wallNormalX=$wallNormalX, isOnCeiling=$isOnCeiling)"

    /** Component key for [KinematicBody]. */
    public companion object : ComponentType<KinematicBody>("KinematicBody") {
        /** The [floorRaw] of a body on no floor: no live entity has this raw handle. */
        internal const val NO_FLOOR: Long = -1L
    }
}

/**
 * Moves every [KinematicBody] of [world] by its speed times [deltaSeconds] against the solids of
 * this grid, which the caller has refreshed, as the class description of [KinematicBody] explains.
 * When [scenes] is given, a body owned by a scene frozen below its top stands still. Allocates
 * nothing.
 *
 * @throws IllegalStateException if a body's entity also has a `Velocity` or a [Solid], has no
 *   `Transform` or [Collider], has a collider that is not a box, has a parent, or has a speed that is
 *   not finite.
 */
internal fun SolidGrid.moveBodies(world: World, deltaSeconds: Float, scenes: SceneStack?) {
    val bodies = world.storeOrNull(KinematicBody) ?: return
    if (bodies.size == 0) return
    val transforms = world.storeOrNull(Transform)
    val colliders = world.storeOrNull(Collider)
    val velocities = world.storeOrNull(Velocity)
    val solids = world.storeOrNull(Solid)
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    for (k in 0 until bodies.size) {
        val entity = Entity(bodies.entities[k])
        if (frozen != null && frozen.isFrozen(world.ownerAt(entity.index))) continue
        val body = bodies.items[k] as KinematicBody
        check(velocities?.getOrNull(entity) == null) {
            "Entity $entity has both a Velocity and a KinematicBody; give a body its speed through KinematicBody.dx and dy."
        }
        check(solids?.getOrNull(entity) == null) { "Entity $entity has both a Solid and a KinematicBody, and solids cannot move." }
        val transform = transforms?.getOrNull(entity)
        checkNotNull(transform) { "Entity $entity has a KinematicBody but no Transform." }
        val collider = colliders?.getOrNull(entity)
        checkNotNull(collider) { "Entity $entity has a KinematicBody but no Collider." }
        val shape = collider.shape
        check(shape is Shape.Box) { "Entity $entity has a KinematicBody whose collider shape $shape is not a Box." }
        check(world.parentOrNull(entity) == null) { "Entity $entity has a KinematicBody and a parent, but a body must be a root entity." }
        check(body.dx.isFinite() && body.dy.isFinite()) {
            "Entity $entity has a KinematicBody whose speed (${body.dx}, ${body.dy}) is not finite."
        }
        moveBody(body, transform, collider, shape, deltaSeconds)
    }
}

/** Moves [body] across, then up or down, then checks for a floor below, as [moveBodies] describes. */
private fun SolidGrid.moveBody(body: KinematicBody, transform: Transform, collider: Collider, shape: Shape.Box, deltaSeconds: Float) {
    val scaleX = if (collider.scalesWithTransform) transform.scaleX else 1f
    val scaleY = if (collider.scalesWithTransform) transform.scaleY else 1f
    var centerX = transform.x + collider.offsetX * scaleX
    var centerY = transform.y + collider.offsetY * scaleY
    val halfWidth = shape.width * abs(scaleX) * 0.5f
    val halfHeight = shape.height * abs(scaleY) * 0.5f
    val mask = collider.mask.bits
    val gap = Collisions.CONTACT_GAP
    val wasOnFloor = body.isOnFloor
    val isRising = body.dy < 0f
    body.clearContacts()
    if (body.isDropRequested) {
        body.isDropRequested = false
        // Covers a ledge the feet rest on, a contact gap below them.
        body.dropLine = centerY + halfHeight + 2 * gap
    }
    val dropLine = body.dropLine

    val moveX = body.dx * deltaSeconds
    if (moveX != 0f) {
        var stopX = centerX + moveX
        if (sweep(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, moveX, 0f, mask, skipsOverlaps = true, dropLine)) {
            val solid = hitSolid
            // Stops a contact gap short of the face, never behind the start or past the move.
            stopX = if (moveX > 0f) {
                max(centerX, min(stopX, leftOf(solid) - gap - halfWidth))
            } else {
                min(centerX, max(stopX, rightOf(solid) + gap + halfWidth))
            }
            body.dx = 0f
            body.isOnWall = true
            body.wallNormalX = hitNormalX
        }
        transform.x += stopX - centerX
        centerX = stopX
    }

    val moveY = body.dy * deltaSeconds
    if (moveY != 0f) {
        var stopY = centerY + moveY
        if (sweep(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, 0f, moveY, mask, skipsOverlaps = true, dropLine)) {
            val solid = hitSolid
            if (moveY > 0f) {
                stopY = max(centerY, min(stopY, topOf(solid) - gap - halfHeight))
                body.isOnFloor = true
                body.floorRaw = entityAt(solid)
            } else {
                stopY = min(centerY, max(stopY, bottomOf(solid) + gap + halfHeight))
                body.isOnCeiling = true
            }
            body.dy = 0f
        }
        transform.y += stopY - centerY
        centerY = stopY
    }

    // A body that walked off a small step down keeps its floor.
    if (wasOnFloor && !body.isOnFloor && !isRising) {
        val snap = Collisions.FLOOR_SNAP_DISTANCE
        if (sweep(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, 0f, snap, mask, skipsOverlaps = true, dropLine)) {
            val solid = hitSolid
            val stopY = max(centerY, min(centerY + snap, topOf(solid) - gap - halfHeight))
            transform.y += stopY - centerY
            centerY = stopY
            body.isOnFloor = true
            body.floorRaw = entityAt(solid)
            body.dy = 0f
        }
    }

    // The drop ends once the feet are below the line, on another floor, or rising.
    if (dropLine != Float.NEGATIVE_INFINITY && (centerY + halfHeight > dropLine || body.isOnFloor || isRising)) {
        body.dropLine = Float.NEGATIVE_INFINITY
    }
}
