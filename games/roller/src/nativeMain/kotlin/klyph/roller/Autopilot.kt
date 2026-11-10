package klyph.roller

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.entity.Transform
import kotlin.math.abs
import kotlin.math.sign

// The autopilot (KLYPH_AUTOPILOT=1) plays the level for demos and smoke runs: it leaves the title
// screen at once and the end screens after a moment (GameScenes does that), and in play it rolls
// right at full speed, jumps at the end of every roof, at walls and at the blobs ahead of it, and
// steers in the air to come down on a blob and never into a gap. It plans again every fixed step
// from where the hero is, asking the engine's sweeps what is solid ahead and below, and fills the
// same Controls the keyboard and gamepads fill.

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
    if (body.isGrounded) {
        controls.move = 1f
        controls.isJumpPressed = shouldJump(app, body, x, feet)
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
    if (!app.followArc(arc, x, feet, body.dx, body.dy, move).isLanded) {
        for (option in MOVES) {
            if (app.followArc(arc, x, feet, body.dx, body.dy, option).isLanded) {
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
private fun Run.shouldJump(app: App, body: KinematicBody, x: Float, feet: Float): Boolean {
    val collisions = app.collisions
    val reach = Tuning.AUTOPILOT_EDGE_MARGIN + abs(body.dx) * Tuning.AUTOPILOT_EDGE_SECONDS
    // A thin box at the feet that far ahead, swept a little down, finds the roof there or a gap.
    val floorAhead = collisions.sweepBox(x = x + reach, y = feet - 1f, width = 2f, height = 2f, dx = 0f, dy = FLOOR_PROBE, mask = Layers.LEVEL)
    if (floorAhead == null) return true
    // The hero's box rolled ahead as far: a wall in the way.
    val height = Tuning.PLAYER_HEIGHT
    val wall = collisions.sweepBox(x = x, y = feet - height / 2, width = Tuning.PLAYER_WIDTH, height = height, dx = reach, dy = 0f, mask = Layers.LEVEL)
    if (wall != null) return true
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
        app.followArc(arc, x, feet, body.dx, -Tuning.JUMP_SPEED, move = 1f, stopFeet = top)
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

/** Where an arc the autopilot followed ended. One is reused for every plan, so planning allocates only the sweeps' hits. */
class Arc {
    var x = 0f
    var feet = 0f
    var seconds = 0f

    /** Whether the arc ended on a roof or a ledge, rather than at its height or in a pit. */
    var isLanded = false

    override fun toString(): String = "Arc(x=$x, feet=$feet, seconds=$seconds, landed=$isLanded)"
}

/**
 * Follows the hero from ([x], [feet]) at speed ([dx], [dy]) one fixed step at a time, holding
 * [move] and the jump button, until he lands, his falling feet come down to [stopFeet] or he
 * falls out of the world, and leaves where it ended in [arc]. Each step takes the speeds the
 * hero's own system gives and moves his box with two sweeps, across and then down, as the engine
 * moves his body.
 */
private fun App.followArc(arc: Arc, x: Float, feet: Float, dx: Float, dy: Float, move: Float, stopFeet: Float = Float.POSITIVE_INFINITY): Arc {
    val dt = time.deltaSeconds
    val gap = Collisions.CONTACT_GAP
    val width = Tuning.PLAYER_WIDTH
    val height = Tuning.PLAYER_HEIGHT
    var stepX = x
    var stepFeet = feet
    var speedX = dx
    var speedY = dy
    var step = 0
    arc.isLanded = false
    while (step < ARC_STEPS && stepFeet <= Tuning.WORLD_HEIGHT + Tuning.PIT_MARGIN) {
        step++
        speedX = rollSpeed(speedX, move, isGrounded = false, dt = dt)
        speedY = fallSpeed(speedY, isHeld = true, dt = dt)
        val across = speedX * dt
        val wall = collisions.sweepBox(x = stepX, y = stepFeet - height / 2, width = width, height = height, dx = across, dy = 0f, mask = Layers.LEVEL)
        if (wall == null) {
            stepX += across
        } else {
            stepX += across * wall.fraction - sign(across) * gap
            speedX = 0f
        }
        val down = speedY * dt
        val floor = collisions.sweepBox(x = stepX, y = stepFeet - height / 2, width = width, height = height, dx = 0f, dy = down, mask = Layers.LEVEL)
        if (floor == null) {
            stepFeet += down
        } else {
            stepFeet += down * floor.fraction - sign(down) * gap
            if (floor.normalY < 0f) {
                arc.isLanded = true
                break
            }
            speedY = 0f
        }
        if (speedY > 0f && stepFeet >= stopFeet) break
    }
    arc.x = stepX
    arc.feet = stepFeet
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
