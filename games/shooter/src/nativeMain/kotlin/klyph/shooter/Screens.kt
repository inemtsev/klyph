package klyph.shooter

import klyph.app.App
import klyph.entity.Transform
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.text.TextAlign

// The text of every screen, drawn with the engine's built-in font in screen space from RENDER
// systems, and the boss's health bar in world space. Lines are centred on the window and laid out
// from its middle, in points.

/**
 * Draws the score, the lives and the wave in the window's top-left corner, and while a wave starts
 * its number, and "Boss" on a boss wave, in the middle of the window.
 */
fun Round.drawHud(app: App) {
    val text = game.text
    val wave = app.wave
    text.clear()
    text.append("Score ").append(score).append('\n').append("Lives ").append(lives).append('\n').append("Wave ").append(wave.number)
    app.canvas.drawText(app.assets.defaultFont, text, x = Tuning.HUD_MARGIN, y = Tuning.HUD_MARGIN, scale = Tuning.TEXT_SCALE, layer = 1)
    if (wave.bannerTimer <= 0f) return
    text.clear()
    text.append("Wave ").append(wave.number)
    val top = app.window.height / 3f
    drawCentred(app, text, top, scale = Tuning.TITLE_TEXT_SCALE)
    if (wave.isBoss) drawCentred(app, "Boss", top + 2 * Tuning.LINE_SPACING, color = Tuning.BOSS_BAR_FILL)
}

/** Draws the boss's hit points as a bar above it, in the world, so it moves with the boss. */
fun drawBossBar(app: App) {
    val canvas = app.canvas
    app.world.forEach(Boss, Enemy, Transform) { _, _, enemy, transform ->
        val x = transform.x - Tuning.BOSS_BAR_WIDTH / 2
        val y = transform.y - Tuning.BOSS_SIZE / 2 - Tuning.BOSS_BAR_GAP - Tuning.BOSS_BAR_HEIGHT
        val share = enemy.hitPoints.toFloat() / enemy.kind.hitPoints
        canvas.drawRect(x, y, Tuning.BOSS_BAR_WIDTH, Tuning.BOSS_BAR_HEIGHT, Tuning.BOSS_BAR_BACK, layer = Tuning.LAYER_EFFECTS, space = DrawSpace.WORLD)
        canvas.drawRect(x, y, Tuning.BOSS_BAR_WIDTH * share, Tuning.BOSS_BAR_HEIGHT, Tuning.BOSS_BAR_FILL, layer = Tuning.LAYER_EFFECTS, space = DrawSpace.WORLD)
    }
}

/** Draws the game's name, how to start, the best score so far and the small print. */
fun drawTitle(app: App) {
    val game = app.game
    val middle = app.window.height / 2f
    drawCentred(app, "KLYPH SHOOTER", middle - 3 * Tuning.LINE_SPACING, scale = Tuning.TITLE_TEXT_SCALE)
    drawCentred(app, "Press Space or Start", middle)
    game.text.clear()
    game.text.append("Best ").append(game.bestScore)
    drawCentred(app, game.text, middle + Tuning.LINE_SPACING)
    drawCentred(
        app,
        "F3: debug overlay    Escape: quit",
        app.window.height - Tuning.HUD_MARGIN - Tuning.LINE_SPACING,
        color = Tuning.HINT_COLOR,
    )
}

/** Darkens the frozen game and draws the score, "Paused" and how to resume. */
fun drawPause(app: App) {
    app.canvas.drawRect(
        x = 0f,
        y = 0f,
        width = app.window.width.toFloat(),
        height = app.window.height.toFloat(),
        color = Tuning.PAUSE_SHADE,
    )
    app.resources.getOrNull<Round>()?.drawHud(app)
    val middle = app.window.height / 2f
    drawCentred(app, "Paused", middle - 2 * Tuning.LINE_SPACING, scale = Tuning.TITLE_TEXT_SCALE)
    drawCentred(app, "Escape or Start to resume", middle, color = Tuning.HINT_COLOR)
}

/** Draws the final score, the best score and the way on. */
fun drawGameOver(app: App) {
    val game = app.game
    val text = game.text
    val middle = app.window.height / 2f
    drawCentred(app, "GAME OVER", middle - 3 * Tuning.LINE_SPACING, scale = Tuning.TITLE_TEXT_SCALE)
    text.clear()
    text.append("Score ").append(game.lastScore)
    drawCentred(app, text, middle)
    text.clear()
    text.append("Best ").append(game.bestScore)
    drawCentred(app, text, middle + Tuning.LINE_SPACING)
    drawCentred(app, "Space to retry, Escape for title", middle + 3 * Tuning.LINE_SPACING, color = Tuning.HINT_COLOR)
}

/** Draws [text] centred across the window with its top at [y], above the pause shade. */
private fun drawCentred(app: App, text: CharSequence, y: Float, scale: Float = Tuning.TEXT_SCALE, color: Color = Color.WHITE) {
    app.canvas.drawText(app.assets.defaultFont, text, x = app.window.width / 2f, y = y, color = color, scale = scale, align = TextAlign.CENTER, layer = 1)
}
