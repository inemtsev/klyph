package klyph.shooter

import klyph.app.App
import klyph.entity.Transform
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.DurationUnit

// The autopilot (KLYPH_AUTOPILOT=1) plays the game for demos and smoke runs: it faces up and fires
// all the time, lines up under the nearest enemy above it and patrols up and down so the camera
// moves. It fills the same Controls the keyboard, mouse and gamepad fill.

private const val LINE_UP_DISTANCE = 40f
private const val PATROL_HEIGHT = 300f
private const val PATROL_RATE = 0.6f
private const val SWEEP_RATE = 0.5f
/** An enemy closer above the player than this is too late to line up under; the next one counts. */
private const val MIN_TARGET_GAP = 80f

/** Fills [Game.controls] as if a player were steering, in place of the player's input. */
fun Game.steerAutopilot(app: App) {
    val ship = app.world.get(player, Transform)
    val seconds = app.time.elapsed.toDouble(DurationUnit.SECONDS).toFloat()
    val enemyX = nextEnemyXOrNaN(app, ship)
    val targetX = if (enemyX.isNaN()) Tuning.ARENA_WIDTH * (0.5f + 0.35f * sin(seconds * SWEEP_RATE)) else enemyX
    val targetY = Tuning.ARENA_HEIGHT * 0.5f + PATROL_HEIGHT * sin(seconds * PATROL_RATE)
    controls.setMove(
        ((targetX - ship.x) / LINE_UP_DISTANCE).coerceIn(-1f, 1f),
        ((targetY - ship.y) / LINE_UP_DISTANCE).coerceIn(-1f, 1f),
    )
    controls.heading = 0f
    controls.isFiring = true
}

/** Returns where to stand to hit the enemy above [ship] that is closest sideways, or NaN when there is none. */
private fun Game.nextEnemyXOrNaN(app: App, ship: Transform): Float {
    var bestX = Float.NaN
    var bestDistance = Float.POSITIVE_INFINITY
    app.world.forEach(Enemy, Transform, Velocity) { _, enemy, transform, velocity ->
        if (transform.y > ship.y - MIN_TARGET_GAP) return@forEach
        // Lead the target: aim where its sway will have taken it when a bullet fired now arrives.
        val flightTime = (ship.y - transform.y) / (Tuning.BULLET_SPEED + velocity.y)
        val x = enemy.centerX + Tuning.ENEMY_SWAY_AMPLITUDE * sin(enemy.swayPhase + enemy.swayRate * flightTime)
        val distance = abs(x - ship.x)
        if (distance < bestDistance) {
            bestDistance = distance
            bestX = x
        }
    }
    return bestX
}
