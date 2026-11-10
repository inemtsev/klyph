package klyph.roller

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.BoxSlide
import klyph.collision.KinematicBody
import klyph.entity.Transform
import kotlin.math.abs
import kotlin.math.sign

// The autopilot (KLYPH_AUTOPILOT=1) plays the level for demos and smoke runs: it leaves the title
// screen at once and the end screens after a moment (GameScenes does that), and in play it rolls
// right at full speed, jumps at the end of every roof, at walls and at the blobs ahead of it, and
// steers in the air to come down on a blob and never into a gap. It plans again every fixed step
// from where the hero is, asking the engine what is solid ahead and below and following his jumps
// with the move his body makes, and fills the same Controls the keyboard and gamepads fill.

/**
 * Play scene FIXED_UPDATE system, run before the hero's own system: decides this step's move and
 * whether to jump, so it decides once per step whatever the frame rate.
 */
fun Run.steerAutopilot(app: App) {
    val controls = game.controls
    controls.isJumpPressed = false
    val body = app.world.getOrNull(player, KinematicBody) ?: return
    if (state != RunState.PLAYING) {
        controls.move = 0f
        controls.isJumpHeld = false
        return
    }
    val transform = app.world.get(player, Transform)
    val x = transform.x
    val feet = transform.feet
    // Held, so every jump goes full height, unless a helicopter hangs overhead to bump into.
    controls.isJumpHeld = !isHelicopterAbove(app, x, feet)
    if (body.isOnFloor) {
        controls.move = 1f
        controls.isJumpPressed = shouldJump(app, body, transform)
        return
    }
    var move = 1f
    val targetX = blobBelowOrNull(app, x, feet)
    if (body.dy > 0f && targetX != null) {
        val dx = targetX - x
        move = if (abs(dx) < 6f) 0f else sign(dx)
    }
    // Never steer into a gap: keep the first move whose fall ends on a roof.
    val arc = game.arc
    if (!followArc(app, arc, x, transform.y, body.dx, body.dy, move).isLanded) {
        for (option in MOVES) {
            if (followArc(app, arc, x, transform.y, body.dx, body.dy, option).isLanded) {
                move = option
                break
            }
        }
    }
    controls.move = move
}

private val MOVES = floatArrayOf(1f, 0f, -1f)

/**
 * Returns `true` if the hero, rolling on a roof, should jump now: the roof ends or a wall stands
 * just ahead, a jump now would come down on a blob walking on it, or a blob is about to run into
 * him.
 */
private fun Run.shouldJump(app: App, body: KinematicBody, transform: Transform): Boolean {
    val x = transform.x
    val feet = transform.feet
    val reach = Tuning.AUTOPILOT_EDGE_MARGIN + abs(body.dx) * Tuning.AUTOPILOT_EDGE_SECONDS
    // The roof under the feet that far ahead, or a gap; then his box rolled ahead as far: a wall.
    if (app.collisions.floorBelowOrNull(x = x + reach, y = feet, width = 2f, depth = FLOOR_PROBE, mask = Layers.LEVEL) == null) return true
    if (app.collisions.sweepCollider(player, dx = reach, dy = 0f) != null) return true
    var shouldStomp = false
    var nearest = Float.POSITIVE_INFINITY
    val arc = game.arc
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind != EnemyKind.BLOB || enemy.isStomped || abs(enemy.patrol.y - feet) > 1f) return@forEach
        val dx = transform.x - x
        if (dx <= 0f || dx > AUTOPILOT_STOMP_REACH) return@forEach
        if (dx < nearest) nearest = dx
        // Where a jump now would come down to the blob's top, and where the blob will be by then.
        val top = enemy.patrol.y - Tuning.BLOB_HEIGHT * 0.8f
        followArc(app, arc, x, transform.y, body.dx, -Tuning.JUMP_SPEED, move = 1f, stopFeet = top)
        if (arc.isLanded || arc.feet < top) return@forEach
        val blobX = (transform.x + enemy.direction * Tuning.BLOB_SPEED * arc.seconds).coerceIn(enemy.patrol.minX, enemy.patrol.maxX)
        if (abs(arc.x - blobX) < AUTOPILOT_STOMP_AIM) shouldStomp = true
    }
    return shouldStomp || nearest < AUTOPILOT_TOO_CLOSE
}

/** How far below the feet the probe ahead looks for the roof. */
private const val FLOOR_PROBE = 8f

/** How far ahead the autopilot looks for blobs to stomp, and how close its landing must come to one. */
private const val AUTOPILOT_STOMP_REACH = 420f
private const val AUTOPILOT_STOMP_AIM = 14f

/** A blob nearer than this is jumped at whatever the landing, or it would hurt. */
private const val AUTOPILOT_TOO_CLOSE = 70f

/** Where an arc the autopilot followed ended. One is reused for every plan, so planning allocates only the probes' hits. */
class Arc {
    /** Where the hero's Transform ended. */
    var x = 0f
    var y = 0f
    var seconds = 0f

    /** Whether the arc ended on a roof or a ledge, rather than at its height or in a pit. */
    var isLanded = false

    /** Each step's move, reused. */
    val slide = BoxSlide()

    val feet: Float get() = y + Tuning.PLAYER_SPRITE_HEIGHT / 2

    override fun toString(): String = "Arc(x=$x, feet=$feet, seconds=$seconds, landed=$isLanded)"
}

/**
 * Follows the hero from his Transform at ([x], [y]) at speed ([dx], [dy]) one fixed step at a
 * time, holding [move] and the jump button, until he lands, his falling feet come down to
 * [stopFeet] or he falls out of the world, and leaves where it ended in [arc]. Each step takes the
 * speeds the hero's own system gives and makes the move his body makes.
 */
private fun Run.followArc(app: App, arc: Arc, x: Float, y: Float, dx: Float, dy: Float, move: Float, stopFeet: Float = Float.POSITIVE_INFINITY): Arc {
    val dt = app.time.deltaSeconds
    val slide = arc.slide
    var speedX = dx
    var speedY = dy
    var step = 0
    arc.x = x
    arc.y = y
    arc.isLanded = false
    while (step < ARC_STEPS && arc.feet <= Tuning.WORLD_HEIGHT + Tuning.PIT_MARGIN) {
        step++
        speedX = rollSpeed(speedX, move, isGrounded = false, dt = dt)
        speedY = fallSpeed(speedY, isHeld = true, dt = dt)
        app.collisions.slideCollider(player, x = arc.x, y = arc.y, dx = speedX * dt, dy = speedY * dt, result = slide)
        arc.x = slide.x
        arc.y = slide.y
        arc.isLanded = slide.isOnFloor
        if (arc.isLanded) break
        if (slide.isOnWall) speedX = 0f
        if (slide.isOnCeiling) speedY = 0f
        if (speedY > 0f && arc.feet >= stopFeet) break
    }
    arc.seconds = step * dt
    return arc
}

/** Steps an arc follows at most: a second and a half at 60 steps a second. */
private const val ARC_STEPS = 90

/** Returns the x of the nearest blob ahead of or under the falling hero, within reach, or `null`. */
private fun blobBelowOrNull(app: App, x: Float, feet: Float): Float? {
    var best: Float? = null
    var bestDistance = Float.POSITIVE_INFINITY
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind != EnemyKind.BLOB || enemy.isStomped) return@forEach
        val dx = transform.x - x
        if (dx < -40f || dx > 160f || enemy.patrol.y < feet) return@forEach
        if (abs(dx) < bestDistance) {
            bestDistance = abs(dx)
            best = transform.x
        }
    }
    return best
}

/** Returns `true` if a helicopter flies above the hero, near enough across to hit on the way up. */
private fun isHelicopterAbove(app: App, x: Float, feet: Float): Boolean {
    var isAbove = false
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind == EnemyKind.HELI && !enemy.isStomped && abs(transform.x - x) < 110f && transform.y < feet) isAbove = true
    }
    return isAbove
}
