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
 * are no slopes. [Collisions.slideBox] and [Collisions.slideCollider] make the same move without a
 * body, for planning ahead.
 *
 * A [Solid] that moved since the last step, by a `Velocity` or by a system that set its
 * `Transform`, acts on bodies before they move. A body standing on it is carried by the same
 * distance on both axes, stopping a contact gap short of any other solid on the way, and
 * [wasCarried] says so; [floorVelocityX] and [floorVelocityY] give the floor's speed, which a jump
 * adds to keep the platform's momentum, because the engine never adds it to [dx] or [dy]. A body
 * the solid moved into, which it did not stand on, is pushed along the solid's move until it is a
 * contact gap clear of it. When another solid stops that push, the body stays where the push
 * stopped, overlapping the moving solid, and [isCrushed] is `true`: the engine destroys nothing, so
 * the game decides what a crush does. A [Passable.FROM_BELOW] solid carries the bodies on it but
 * pushes none, so a body it rises through lands on it once its feet are above the top, and a body
 * it carries up into a ceiling is not crushed but left inside it, to fall through it. A solid only
 * pushes the bodies it moves into: a body beside a solid moving up or down, or under one moving
 * across, is left where it is. A body that jumps, setting [dy] upward, is neither carried nor
 * pushed by the floor it stood on in its next move, so it leaves by its own speed only. A solid
 * whose size changed since the last step, such as by its `Transform` scale, has not moved: it
 * carries nothing, and a body it grew into is pushed out as below.
 *
 * Before it moves, a body whose box overlaps a solid, because it was placed there or a solid was
 * moved onto it, is pushed out. Each push takes the shortest way out of one solid, up, down, left
 * or right, and ends [Collisions.CONTACT_GAP] units from its face, where a stop would have left the
 * body. A body that overlaps several solids is pushed out of them one after the other, at most four
 * pushes in all. A [Passable.FROM_BELOW] solid never pushes, and nor does a solid outside the body's
 * mask. [wasPushedOut] says the move began with a push. When four pushes leave the body overlapping
 * a solid, such as one squeezed between a wall and a solid moved against it, it is left where it
 * was and [isStuck] is `true`. The move then goes on from there, and the solids the body overlaps
 * do not stop it.
 *
 * After the move, [isOnFloor], [isOnWall], [isOnCeiling], [wallNormalX] and [floorOrNull] say what
 * the body touched, and [hasLanded] and [landingSpeed] say whether it has just come down onto a
 * floor. A body that stood on a floor before the move and is not moving up looks
 * [Collisions.FLOOR_SNAP_DISTANCE] units further down after it and lands on a floor it finds there, so a
 * body walking down a small step stays on the floor.
 *
 * The engine adds no gravity: a `FIXED_UPDATE` system adds it to [dy], as in
 * `body.dy += 1800f * time.deltaSeconds`, and sets [dx] from the input. A body stops only at solids
 * whose collider's layers are in its own collider's mask. Bodies do not stop each other. A body
 * owned by a scene frozen under a pushed scene stands still and keeps its state. A body whose
 * entity also has a `Velocity` or a [Solid], has no `Transform` or [Collider], has a collider that
 * is not a box, has a parent or has a speed that is not finite makes the next move throw
 * [IllegalStateException] naming the entity.
 *
 * Interpolated bodies are drawn blended between their last two steps, as with `Velocity`. A body
 * moved by setting its `Transform`, such as a respawn, is drawn sliding from the old place to the
 * new one during the next step unless [klyph.entity.snapInterpolation] follows the move, and a
 * camera that follows the body reads the place it is drawn at from [klyph.entity.drawnPositionOf].
 *
 * [toString] gives
 * `KinematicBody(dx=0.0, dy=0.0, isOnFloor=true, isOnWall=false, wallNormalX=0.0, isOnCeiling=false)`,
 * followed by `, hasLanded=true, landingSpeed=450.0` after a move that landed, `, wasPushedOut=true`
 * after one that began with a push, `, isStuck=true` after one that left the body stuck,
 * `, floorVelocityX=60.0, floorVelocityY=0.0` while the floor moves, `, wasCarried=true` after a
 * move that carried the body and `, isCrushed=true` after one that crushed it.
 *
 * @sample samples.collision.Bodies.kinematicBody
 */
public class KinematicBody(
    /** Horizontal speed in world units per second; positive moves right. A stop at a wall zeroes it. */
    public var dx: Float = 0f,
    /** Vertical speed in world units per second; positive moves down. A landing or a bump on a ceiling zeroes it. */
    public var dy: Float = 0f,
) {
    /** Whether the last move ended on a floor, whatever [dy] has been set to since. */
    internal var isTouchingFloor: Boolean = false

    /**
     * Whether the body stands on a floor: the last move ended on one, landing moving down or found
     * by the floor check below, and [dy] has not been set upward since. Setting `dy` below 0 for a
     * jump turns it `false` at once, before the next move, so a system that reads it after its own
     * jump, or a stomp that bounces the body up, sees the body leaving the floor.
     *
     * @sample samples.collision.Bodies.isOnFloor
     */
    public val isOnFloor: Boolean
        get() = isTouchingFloor && dy >= 0f

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

    /**
     * Whether the last move came down onto a floor and the move before it did not end on one, or
     * had been sent up since: `true` for exactly one move per landing, the step to play a landing
     * sound or raise dust. A body walking down a small step keeps its floor through the floor
     * check, so it does not land. The first move of a body placed on a floor counts as a landing.
     *
     * While the body's scene is frozen under a pushed scene nothing moves the body, so the flag
     * holds, as the scene's clock does. A body that landed in the last step before the freeze still
     * reads `true` when the scene is on top again, and the scene's systems see the landing in their
     * first step back, which is the next step the scene runs.
     *
     * @sample samples.collision.Bodies.hasLanded
     */
    public var hasLanded: Boolean = false
        internal set

    /**
     * The downward speed in world units per second that the last move came down onto the floor
     * with, the [dy] the move started with, or 0 when [hasLanded] is `false`. A landing zeroes `dy`,
     * so this is where the speed of the fall is kept, such as for dust only after a long fall.
     */
    public var landingSpeed: Float = 0f
        internal set

    /**
     * Whether the last move began by pushing the body out of a solid it overlapped, such as after
     * it was placed inside a floor or a solid was moved onto it: `true` for each move that did,
     * `false` again after the next one that did not. The class description says how a push works.
     *
     * @sample samples.collision.Bodies.wasPushedOut
     */
    public var wasPushedOut: Boolean = false
        internal set

    /**
     * Whether the last move found the body overlapping a solid that pushing could not take it out
     * of, such as a body squeezed between a wall and a solid moved against it, and left it where it
     * was: `true` for each move that did, `false` again after the next one that did not. The move
     * went on from there, and a solid the body overlaps does not stop it, so a stuck body can still
     * leave by its own speed. A game can react by moving, crushing or respawning it.
     *
     * @sample samples.collision.Bodies.isStuck
     */
    public var isStuck: Boolean = false
        internal set

    /**
     * Whether the last move began by carrying the body along with the solid it stood on, which had
     * moved since the move before: `true` for each move that did, `false` again after the next one
     * that did not. The body moves with its floor before its own move, by the floor's whole
     * displacement on both axes, unless another solid stops it first.
     *
     * @sample samples.collision.Bodies.wasCarried
     */
    public var wasCarried: Boolean = false
        internal set

    /**
     * The speed across, in world units per second, at which the floor under the body moved during
     * the last move: the floor's displacement over the fixed step divided by the step's length, 0
     * when the body ended the move on no floor or on one that did not move. A jump from a moving
     * platform keeps its momentum by adding it, as in `body.dx += body.floorVelocityX`; the engine
     * never adds it itself. Unlike [floorOrNull], it keeps its value when [dy] is set upward, so a
     * jump can read it after setting `dy`.
     *
     * @sample samples.collision.Bodies.floorVelocity
     */
    public var floorVelocityX: Float = 0f
        internal set

    /**
     * The speed up or down, in world units per second, at which the floor under the body moved
     * during the last move, negative for a floor going up; 0 on no floor or a still one. A jump
     * from a rising lift goes higher by adding it, as in `body.dy = -jumpSpeed + body.floorVelocityY`.
     * [floorVelocityX] describes both.
     *
     * @sample samples.collision.Bodies.floorVelocity
     */
    public var floorVelocityY: Float = 0f
        internal set

    /**
     * Whether a moving [Solid] pushed the body in the last move and another solid stopped the push
     * before the body was clear of it, such as a lift pressing the body against a ceiling: `true` for
     * each move that did, `false` again after the next one that did not. The body stays where the
     * push stopped, overlapping the moving solid, which then does not stop it. The engine destroys
     * nothing, so a game reacts as it likes, such as by taking a heart and respawning the body.
     *
     * @sample samples.collision.Bodies.isCrushed
     */
    public var isCrushed: Boolean = false
        internal set

    /** The raw handle of the solid under the body after the last move, or [NO_FLOOR]. */
    internal var floorRaw: Long = NO_FLOOR

    /** Returns the [Solid] entity the body stands on, or `null` while [isOnFloor] is `false`. */
    public val floorOrNull: Entity?
        get() = if (floorRaw == NO_FLOOR || !isOnFloor) null else Entity(floorRaw)

    /** Whether [dropThrough] was called since the last move. */
    internal var isDropRequested: Boolean = false

    /**
     * The y below which the body keeps falling through one-way solids after [dropThrough]: one-way
     * solids whose top is at or above it, a y less than or equal to it, are ignored. Negative
     * infinity when the body is not dropping.
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

    /** Returns `KinematicBody(dx=0.0, dy=0.0, isOnFloor=true, …)`. The format may change. */
    override fun toString(): String {
        val contacts = "KinematicBody(dx=$dx, dy=$dy, isOnFloor=$isOnFloor, isOnWall=$isOnWall, wallNormalX=$wallNormalX, isOnCeiling=$isOnCeiling"
        val landing = if (hasLanded) ", hasLanded=true, landingSpeed=$landingSpeed" else ""
        val pushed = if (wasPushedOut) ", wasPushedOut=true" else ""
        val stuck = if (isStuck) ", isStuck=true" else ""
        val isFloorMoving = floorVelocityX != 0f || floorVelocityY != 0f
        val floor = if (isFloorMoving) ", floorVelocityX=$floorVelocityX, floorVelocityY=$floorVelocityY" else ""
        val carried = if (wasCarried) ", wasCarried=true" else ""
        val crushed = if (isCrushed) ", isCrushed=true" else ""
        return "$contacts$landing$pushed$stuck$floor$carried$crushed)"
    }

    /** Component key for [KinematicBody]. */
    public companion object : ComponentType<KinematicBody>("KinematicBody") {
        /** The [floorRaw] of a body on no floor: no live entity has this raw handle. */
        internal const val NO_FLOOR: Long = -1L
    }
}

/**
 * Moves every [KinematicBody] of [world] by its speed times [deltaSeconds] against the solids of
 * this grid, which the caller has refreshed, as the class description of [KinematicBody] explains:
 * first carried by its floor if that moved, then pushed by every solid that moved into it, then
 * pushed out of the solids it overlaps, slid and checked for a floor. When [scenes] is given, a body
 * owned by a scene frozen below its top stands still. The solids' shifts are cleared afterwards,
 * so each move of a solid acts once. Allocates nothing.
 *
 * @throws IllegalStateException if a body's entity also has a `Velocity` or a [Solid], has no
 *   `Transform` or [Collider], has a collider that is not a box, has a parent, or has a speed that is
 *   not finite.
 */
internal fun SolidGrid.moveBodies(world: World, deltaSeconds: Float, scenes: SceneStack?) {
    try {
        moveEachBody(world, deltaSeconds, scenes)
    } finally {
        clearShifts()
    }
}

private fun SolidGrid.moveEachBody(world: World, deltaSeconds: Float, scenes: SceneStack?) {
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
        check(solids?.getOrNull(entity) == null) {
            "Entity $entity has both a Solid and a KinematicBody; give a solid a Velocity or set its Transform to move it."
        }
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

/**
 * Carries [body] with its floor if the floor moved, lets every moved solid push it, pushes it out
 * of the solids it overlaps, moves it across, then up or down, then checks for a floor below, as
 * [moveBodies] describes.
 */
private fun SolidGrid.moveBody(body: KinematicBody, transform: Transform, collider: Collider, shape: Shape.Box, deltaSeconds: Float) {
    val scaleX = if (collider.scalesWithTransform) transform.scaleX else 1f
    val scaleY = if (collider.scalesWithTransform) transform.scaleY else 1f
    val offsetX = collider.offsetX * scaleX
    val offsetY = collider.offsetY * scaleY
    val halfWidth = shape.width * abs(scaleX) * 0.5f
    val halfHeight = shape.height * abs(scaleY) * 0.5f
    val mask = collider.mask.bits
    val box = bodyBox.set(transform, offsetX, offsetY, halfWidth, halfHeight, mask, body.dropLine)
    // A moved floor the body has jumped off is left to the body's own move, which goes through it.
    val leftFloor = if (body.isOnFloor) -1 else movedFloorOf(body)
    // The solids that moved act first: the body's floor carries it, and the others push it.
    val crushed = carryAndPush(body, box)
    // The sweeps below skip the solids a box starts inside, so the body is first pushed out of them,
    // unless a crush left it inside a moving solid on purpose.
    val pushes = if (crushed) 0 else pushOut(transform, offsetX, offsetY, halfWidth, halfHeight, mask, ignored = leftFloor)
    body.wasPushedOut = !crushed && (body.wasPushedOut || pushes > 0)
    body.isStuck = pushes == STUCK
    body.isCrushed = crushed
    // Standing means touching a floor and not sent up since, so a standing body is not rising.
    val wasOnFloor = body.isOnFloor
    val startSpeedY = body.dy
    if (body.isDropRequested) {
        body.isDropRequested = false
        // Covers a ledge the feet rest on, a contact gap below them.
        body.dropLine = transform.y + offsetY + halfHeight + 2 * Collisions.CONTACT_GAP
    }
    val dropLine = body.dropLine
    val moved = bodySlide
    slide(
        transform.x, transform.y, offsetX, offsetY, halfWidth, halfHeight,
        body.dx * deltaSeconds, startSpeedY * deltaSeconds, collider.mask.bits, dropLine,
        // A body that walked off a small step down keeps its floor.
        floorSnap = if (wasOnFloor) Collisions.FLOOR_SNAP_DISTANCE else 0f,
        result = moved,
    )
    transform.x = moved.x
    transform.y = moved.y
    recordContacts(body, moved, wasOnFloor, startSpeedY, deltaSeconds)

    // The drop ends once the feet are below the line, on another floor, or rising.
    if (dropLine != Float.NEGATIVE_INFINITY && (moved.y + offsetY + halfHeight > dropLine || moved.isOnFloor || startSpeedY < 0f)) {
        body.dropLine = Float.NEGATIVE_INFINITY
    }
}

/**
 * Sets the contacts of [body] from the slide [moved] it just made, which started standing when
 * [wasOnFloor] and moving up or down at [startSpeedY], and the speed of the floor it ended on over
 * a step of [deltaSeconds].
 */
private fun SolidGrid.recordContacts(body: KinematicBody, moved: BoxSlide, wasOnFloor: Boolean, startSpeedY: Float, deltaSeconds: Float) {
    body.isTouchingFloor = moved.isOnFloor
    body.isOnWall = moved.isOnWall
    body.isOnCeiling = moved.isOnCeiling
    body.wallNormalX = moved.wallNormalX
    body.floorRaw = moved.floorRaw
    if (moved.isOnWall) body.dx = 0f
    if (moved.isOnFloor || moved.isOnCeiling) body.dy = 0f
    body.hasLanded = moved.isOnFloor && !wasOnFloor
    body.landingSpeed = if (body.hasLanded) startSpeedY else 0f
    val floor = solidOf(moved.floorRaw)
    val isTimed = floor >= 0 && deltaSeconds > 0f
    body.floorVelocityX = if (isTimed) shiftXOf(floor) / deltaSeconds else 0f
    body.floorVelocityY = if (isTimed) shiftYOf(floor) / deltaSeconds else 0f
}

/** The most pushes [pushOut] makes before it gives up on a body. */
private const val MAX_PUSHES: Int = 4

/** What [pushOut] returns for a body that [MAX_PUSHES] pushes did not take out of the solids it overlaps. */
private const val STUCK: Int = -1

/**
 * Pushes the box centered on [transform]'s position plus ([offsetX], [offsetY]), [halfWidth] and
 * [halfHeight] from its center to its edges, out of the solids on a layer in [mask] that it overlaps,
 * as the class description of [KinematicBody] explains, and moves [transform] by each push. Each push
 * takes the box the shortest way, up, down, left or right, clear of the first solid it overlaps,
 * ties going to up, down, left and right in that order, and ends a contact gap from the solid, where
 * a sweep would have stopped it. One-way solids never overlap here: a sweep without a move only meets
 * them when the box moves down. Solid [ignored], such as a moved floor the body jumped off, or -1,
 * is never pushed out of.
 *
 * Returns how many pushes it took, 0 when the box overlapped nothing, or [STUCK] when [MAX_PUSHES]
 * pushes still left it overlapping a solid, after putting [transform] back where it was. Allocates
 * nothing.
 */
private fun SolidGrid.pushOut(
    transform: Transform,
    offsetX: Float,
    offsetY: Float,
    halfWidth: Float,
    halfHeight: Float,
    mask: Int,
    ignored: Int,
): Int {
    val gap = Collisions.CONTACT_GAP
    val startX = transform.x
    val startY = transform.y
    var pushes = 0
    while (true) {
        val centerX = transform.x + offsetX
        val centerY = transform.y + offsetY
        val minX = centerX - halfWidth
        val minY = centerY - halfHeight
        val maxX = centerX + halfWidth
        val maxY = centerY + halfHeight
        if (!sweep(minX, minY, maxX, maxY, 0f, 0f, mask, skipsOverlaps = false, ignores = ignored)) return pushes
        if (pushes == MAX_PUSHES) {
            transform.x = startX
            transform.y = startY
            return STUCK
        }
        val solid = hitSolid
        // How far the box must move each way to end clear of the solid, and the shortest of them.
        val up = maxY - topOf(solid)
        val down = bottomOf(solid) - minY
        val left = maxX - leftOf(solid)
        val right = rightOf(solid) - minX
        var least = up
        var pushX = 0f
        var pushY = -up - gap
        if (down < least) {
            least = down
            pushY = down + gap
        }
        if (left < least) {
            least = left
            pushX = -left - gap
            pushY = 0f
        }
        if (right < least) {
            pushX = right + gap
            pushY = 0f
        }
        transform.x += pushX
        transform.y += pushY
        pushes++
    }
}

/**
 * Moves a box whose center is ([originX] + [offsetX], [originY] + [offsetY]) across by [moveX], then
 * up or down by [moveY], stopping a contact gap short of the first solid on a layer in [mask] each
 * way, and writes where its origin ends and what it touched into [result]. When the box did not
 * end on a floor and is not moving up, it then looks [floorSnap] units further down and lands on a
 * floor it finds there. One-way solids whose top is at or above [dropLine] are ignored.
 *
 * Every [KinematicBody] move and every [Collisions.slideBox] and [Collisions.slideCollider] runs
 * through this one function, so a plan made with a slide is the move a body makes. Allocates nothing.
 */
internal fun SolidGrid.slide(
    originX: Float,
    originY: Float,
    offsetX: Float,
    offsetY: Float,
    halfWidth: Float,
    halfHeight: Float,
    moveX: Float,
    moveY: Float,
    mask: Int,
    dropLine: Float,
    floorSnap: Float,
    result: BoxSlide,
) {
    val gap = Collisions.CONTACT_GAP
    var x = originX
    var y = originY
    var centerX = originX + offsetX
    var centerY = originY + offsetY
    var movedX = 0f
    var movedY = 0f
    var isOnFloor = false
    var isOnWall = false
    var isOnCeiling = false
    var wallNormalX = 0f
    var floorRaw = KinematicBody.NO_FLOOR

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
            isOnWall = true
            wallNormalX = hitNormalX
        }
        x += stopX - centerX
        movedX += stopX - centerX
        centerX = stopX
    }

    if (moveY != 0f) {
        var stopY = centerY + moveY
        if (sweep(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, 0f, moveY, mask, skipsOverlaps = true, dropLine)) {
            val solid = hitSolid
            if (moveY > 0f) {
                stopY = max(centerY, min(stopY, topOf(solid) - gap - halfHeight))
                isOnFloor = true
                floorRaw = entityAt(solid)
            } else {
                stopY = min(centerY, max(stopY, bottomOf(solid) + gap + halfHeight))
                isOnCeiling = true
            }
        }
        y += stopY - centerY
        movedY += stopY - centerY
        centerY = stopY
    }

    if (floorSnap > 0f && !isOnFloor && moveY >= 0f) {
        if (sweep(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, 0f, floorSnap, mask, skipsOverlaps = true, dropLine)) {
            val solid = hitSolid
            val stopY = max(centerY, min(centerY + floorSnap, topOf(solid) - gap - halfHeight))
            y += stopY - centerY
            movedY += stopY - centerY
            isOnFloor = true
            floorRaw = entityAt(solid)
        }
    }

    result.x = x
    result.y = y
    result.movedX = movedX
    result.movedY = movedY
    result.isOnFloor = isOnFloor
    result.isOnWall = isOnWall
    result.isOnCeiling = isOnCeiling
    result.wallNormalX = wallNormalX
    result.floorRaw = floorRaw
}
