package klyph.squish

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.entity.Transform
import kotlin.math.sqrt

// The autopilot (KLYPH_AUTOPILOT=1) plays the game for demos and smoke runs: it leaves the title
// screen at once and the end screen after a moment (GameScenes does that), and in play it slides
// the swatter to the nearest bug on the blanket and swats it, every so often. It fills the same
// Controls the mouse, keyboard and gamepad fill.

/** Play scene INPUT system: moves the swatter toward the nearest bug and swats once it is on it. */
fun steerAutopilot(app: App) {
    val game = app.game
    val controls = game.controls
    val dt = app.time.deltaSeconds
    game.autopilotCooldown -= dt
    var targetX = Tuning.CAKE_X
    var targetY = Tuning.CAKE_Y
    var hasBug = false
    var nearest = Float.POSITIVE_INFINITY
    app.world.forEach(Bug, Transform) { _, bug, transform ->
        // Bugs still off the edge are out of the swatter's reach.
        val isOnScreen = transform.x in 0f..Tuning.WORLD_WIDTH && transform.y in 0f..Tuning.WORLD_HEIGHT
        if (bug.isSquished || !isOnScreen) return@forEach
        val dx = transform.x - controls.cursorX
        val dy = transform.y - controls.cursorY
        val distance = dx * dx + dy * dy
        if (distance < nearest) {
            nearest = distance
            targetX = transform.x
            targetY = transform.y
            hasBug = true
        }
    }
    val dx = targetX - controls.cursorX
    val dy = targetY - controls.cursorY
    val distance = sqrt(dx * dx + dy * dy)
    val step = Tuning.AUTOPILOT_CURSOR_SPEED * dt
    if (distance <= step) {
        controls.cursorX = targetX
        controls.cursorY = targetY
    } else {
        controls.cursorX += dx / distance * step
        controls.cursorY += dy / distance * step
    }
    if (hasBug && distance - step <= Tuning.AUTOPILOT_AIM_DISTANCE && game.autopilotCooldown <= 0f) {
        controls.isSwatPressed = true
        game.slapTimer = Tuning.SWAT_SLAP_SECONDS
        game.autopilotCooldown = Tuning.AUTOPILOT_SWAT_INTERVAL
    }
}
