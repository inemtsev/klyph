package klyph.roller

import klyph.app.App
import klyph.entity.Transform
import kotlin.math.abs
import kotlin.time.DurationUnit

// The autopilot (KLYPH_AUTOPILOT=1) plays the level for demos and smoke runs: it leaves the title
// screen at once and the end screens after a moment (GameScenes does that), and in play it rolls
// right at full speed, jumps at the end of every roof and at the blobs ahead of it, and steers in
// the air to come down on a blob and never into a gap. It reads the level data and runs the
// hero's own physics ahead to see where a jump would land, and fills the same Controls the
// keyboard and gamepads fill.

/** Play scene INPUT system: decides this frame's move and whether to jump. */
fun Run.steerAutopilot(app: App) {
    val controls = game.controls
    val hero = app.world.getOrNull(player, Player) ?: return
    val body = hero.body
    controls.isJumpPressed = false
    if (state != RunState.PLAYING) {
        controls.move = 0f
        controls.isJumpHeld = false
        return
    }
    val dt = app.time.fixedDelta.toDouble(DurationUnit.SECONDS).toFloat()
    // Held, so every jump goes full height, unless a helicopter hangs overhead to bump into.
    controls.isJumpHeld = !isHelicopterAbove(app, body)
    if (body.isGrounded) {
        controls.move = 1f
        if (shouldJump(app, body, dt)) controls.isJumpQueued = true
        return
    }
    var move = 1f
    val targetX = blobBelowOrNull(app, body)
    if (body.vy > 0f && targetX != null) {
        val dx = targetX - body.x
        move = if (abs(dx) < 6f) 0f else if (dx > 0f) 1f else -1f
    }
    // Never steer into a gap: keep the first move whose landing is on something.
    if (body.predictLanding(game.scratch, RooftopRun.solids, move, jump = false, dt = dt) < 0) {
        for (option in MOVES) {
            if (body.predictLanding(game.scratch, RooftopRun.solids, option, jump = false, dt = dt) >= 0) {
                move = option
                break
            }
        }
    }
    controls.move = move
}

private val MOVES = floatArrayOf(1f, 0f, -1f)

/**
 * Returns `true` if the hero should jump now: the roof ends just ahead, or a jump now would come
 * down on a blob walking on it, or a blob is about to run into him.
 */
private fun Run.shouldJump(app: App, body: Body, dt: Float): Boolean {
    val solids = RooftopRun.solids
    val surface = solids[body.groundIndex]
    val isLastRoof = body.groundIndex == RooftopRun.buildings.size - 1
    val edge = surface.right - body.x
    if (!isLastRoof && edge < Tuning.AUTOPILOT_EDGE_MARGIN + abs(body.vx) * Tuning.AUTOPILOT_EDGE_SECONDS) return true
    var shouldStomp = false
    var nearest = Float.POSITIVE_INFINITY
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind != EnemyKind.BLOB || enemy.isStomped || enemy.patrol.y != surface.top) return@forEach
        val dx = transform.x - body.x
        if (dx <= 0f || dx > AUTOPILOT_STOMP_REACH) return@forEach
        if (dx < nearest) nearest = dx
        // Where a jump now would come down to the blob's top, and where the blob will be by then.
        val steps = body.predictDescent(game.scratch, solids, height = enemy.patrol.y - Tuning.BLOB_HEIGHT * 0.8f, move = 1f, dt = dt)
        if (steps < 0) return@forEach
        val blobX = (transform.x + enemy.direction * Tuning.BLOB_SPEED * steps * dt).coerceIn(enemy.patrol.minX, enemy.patrol.maxX)
        if (abs(game.scratch.x - blobX) < AUTOPILOT_STOMP_AIM) shouldStomp = true
    }
    return shouldStomp || nearest < AUTOPILOT_TOO_CLOSE
}

/** How far ahead the autopilot looks for blobs to stomp, and how close its landing must come to one. */
private const val AUTOPILOT_STOMP_REACH = 420f
private const val AUTOPILOT_STOMP_AIM = 14f

/** A blob nearer than this is jumped at whatever the landing, or it would hurt. */
private const val AUTOPILOT_TOO_CLOSE = 70f

/** Returns the x of the nearest blob ahead of or under the falling hero, within reach, or `null`. */
private fun blobBelowOrNull(app: App, body: Body): Float? {
    var best: Float? = null
    var bestDistance = Float.POSITIVE_INFINITY
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind != EnemyKind.BLOB || enemy.isStomped) return@forEach
        val dx = transform.x - body.x
        if (dx < -40f || dx > 160f || enemy.patrol.y < body.feet) return@forEach
        if (abs(dx) < bestDistance) {
            bestDistance = abs(dx)
            best = transform.x
        }
    }
    return best
}

/** Returns `true` if a helicopter flies above the hero, near enough across to hit on the way up. */
private fun isHelicopterAbove(app: App, body: Body): Boolean {
    var isAbove = false
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        if (enemy.kind == EnemyKind.HELI && !enemy.isStomped && abs(transform.x - body.x) < 110f && transform.y < body.feet) isAbove = true
    }
    return isAbove
}
