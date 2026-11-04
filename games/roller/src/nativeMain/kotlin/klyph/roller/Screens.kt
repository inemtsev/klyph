package klyph.roller

import klyph.app.App
import klyph.platform.Color
import klyph.text.TextAlign
import klyph.text.TextShadow
import kotlin.math.min
import kotlin.math.sin

// Everything drawn through app.canvas in screen space, in points from the window's top-left
// corner: the HUD and the text of each screen, with the engine's built-in font and a drop shadow
// so it reads on the sky and on the bricks.

/**
 * Draws the HUD: a heart for each of the hero's hearts in the top-left corner, empty ones dimmed,
 * the level's name in the top-right corner, the score at the bottom in the middle, and the rings
 * taken in the bottom-right corner next to a ring.
 */
fun Run.drawHud(app: App) {
    val canvas = app.canvas
    val art = game.art
    val margin = Tuning.HUD_MARGIN
    val windowWidth = app.window.width.toFloat()
    val windowHeight = app.window.height.toFloat()
    val heartSize = Tuning.HUD_HEART_SIZE
    for (index in 0 until Tuning.MAX_HEARTS) {
        canvas.drawSprite(
            art.heart,
            x = margin + heartSize / 2 + index * (heartSize + 6f),
            y = margin + heartSize / 2,
            width = heartSize,
            height = heartSize * art.heart.height / art.heart.width,
            color = if (index < hearts) Color.WHITE else Tuning.LOST_HEART_TINT,
            layer = 1,
        )
    }
    drawShadowed(app, RooftopRun.NAME, x = windowWidth - margin, y = margin + 6f, scale = Tuning.SMALL_TEXT_SCALE, align = TextAlign.RIGHT, color = Tuning.HINT_COLOR)

    val text = game.text
    val bottom = windowHeight - margin - 8f * Tuning.TEXT_SCALE
    text.clear()
    text.append(score)
    drawShadowed(app, text, x = windowWidth / 2, y = bottom, scale = Tuning.TEXT_SCALE, align = TextAlign.CENTER, color = Tuning.SCORE_COLOR)

    text.clear()
    text.append(rings).append(" x")
    val ringSize = Tuning.HUD_RING_SIZE
    val ringX = windowWidth - margin - ringSize / 2
    drawShadowed(app, text, x = ringX - ringSize / 2 - 8f, y = bottom, scale = Tuning.TEXT_SCALE, align = TextAlign.RIGHT)
    canvas.drawSprite(art.ring, x = ringX, y = bottom + 4f * Tuning.TEXT_SCALE, width = ringSize, height = ringSize, layer = 1)
}

/** Draws the game's name, the hero, how to start, the best score so far and the controls. */
fun drawTitle(app: App) {
    val game = app.game
    val width = app.window.width.toFloat()
    val height = app.window.height.toFloat()
    shade(app, TITLE_SHADE)
    drawCentred(app, "ROOFTOP ROLLER", height * 0.12f, scale = Tuning.TITLE_TEXT_SCALE, color = Tuning.TITLE_COLOR)
    drawCentred(app, SUBTITLE, height * 0.12f + 2.1f * Tuning.LINE_SPACING, scale = Tuning.SMALL_TEXT_SCALE)
    // The hero rolls on the spot, bobbing, between the title and the prompt.
    val seconds = app.time.elapsed.inWholeMilliseconds / 1000f
    val frame = game.art.skate[((seconds * 4f).toInt()) % game.art.skate.size]
    val heroHeight = 128f
    app.canvas.drawSprite(
        frame, x = width / 2, y = height * 0.5f + 3f * sin(seconds * 8f),
        width = heroHeight * frame.width / frame.height, height = heroHeight, layer = 1,
    )
    drawCentred(app, "Press Space or A to roll", height * 0.72f, scale = Tuning.TEXT_SCALE)
    if (game.bestScore > 0) {
        game.text.clear()
        game.text.append("Best ").append(game.bestScore)
        drawCentred(app, game.text, height * 0.72f + Tuning.LINE_SPACING, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
    }
    drawCentred(
        app,
        "Arrows or A/D: roll   Space: jump   Esc: pause",
        height - Tuning.HUD_MARGIN - Tuning.LINE_SPACING / 2,
        scale = Tuning.SMALL_TEXT_SCALE,
        color = Tuning.HINT_COLOR,
    )
}

private const val SUBTITLE = "Level 1: " + RooftopRun.NAME

private val TITLE_SHADE = Color(0x1A103058u)

/** Darkens the frozen level and draws the HUD, "Paused" and how to go on. */
fun drawPause(app: App) {
    shade(app, Tuning.PAUSE_SHADE)
    app.resources.getOrNull<Run>()?.drawHud(app)
    val middle = app.window.height / 2f
    drawCentred(app, "PAUSED", middle - 2 * Tuning.LINE_SPACING, scale = Tuning.BIG_TEXT_SCALE)
    drawCentred(app, "Esc or Start: keep rolling", middle + Tuning.LINE_SPACING / 2, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
}

/**
 * Draws the level complete tally over the frozen level: the score, the heart and time bonuses
 * and the total, which counts up from the score, then the best score and the way on.
 */
fun drawComplete(app: App) {
    val game = app.game
    val run = app.run
    val text = game.text
    val middle = app.window.height / 2f
    val spacing = Tuning.LINE_SPACING
    shade(app, Tuning.END_SHADE)
    drawCentred(app, "LEVEL COMPLETE!", middle - 5f * spacing, scale = Tuning.BIG_TEXT_SCALE, color = Tuning.TITLE_COLOR)
    text.clear()
    text.append("Rings  ").append(run.rings).append("     Stomps  ").append(run.stomps)
    drawCentred(app, text, middle - 2.6f * spacing, scale = Tuning.TEXT_SCALE)
    text.clear()
    text.append("Score  ").append(run.score)
    drawCentred(app, text, middle - 1.4f * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    text.clear()
    text.append("Hearts  ").append(run.hearts).append(" x ").append(Tuning.HEART_BONUS).append(" = ").append(run.heartBonus)
    drawCentred(app, text, middle - 0.6f * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    text.clear()
    text.append("Time  ").append(run.elapsed.toInt()).append(" s   bonus ").append(run.timeBonus)
    drawCentred(app, text, middle + 0.2f * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    val waited = (app.time.elapsed - game.endedAt).inWholeMilliseconds / 1000f
    val shown = run.score + ((run.finalScore - run.score) * min(1f, waited / Tuning.TALLY_SECONDS)).toInt()
    text.clear()
    text.append("Total  ").append(shown)
    drawCentred(app, text, middle + 1.3f * spacing, scale = Tuning.TEXT_SCALE, color = Tuning.TITLE_COLOR)
    text.clear()
    text.append("Best ").append(game.bestScore)
    drawCentred(app, text, middle + 2.6f * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    if (app.time.elapsed - game.endedAt >= Tuning.END_INPUT_DELAY) {
        drawCentred(app, "Space or A: roll again    Esc: title", middle + 4f * spacing, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
    }
}

/** Draws the game over screen over the frozen level: the score, the best score and the way on. */
fun drawGameOver(app: App) {
    val game = app.game
    val run = app.run
    val text = game.text
    val middle = app.window.height / 2f
    val spacing = Tuning.LINE_SPACING
    shade(app, Tuning.END_SHADE)
    drawCentred(app, "GAME OVER", middle - 3f * spacing, scale = Tuning.BIG_TEXT_SCALE, color = GAME_OVER_COLOR)
    text.clear()
    text.append("Score  ").append(run.score).append("     Rings  ").append(run.rings)
    drawCentred(app, text, middle - 0.5f * spacing, scale = Tuning.TEXT_SCALE)
    text.clear()
    text.append("Best ").append(game.bestScore)
    drawCentred(app, text, middle + 0.8f * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    if (app.time.elapsed - game.endedAt >= Tuning.END_INPUT_DELAY) {
        drawCentred(app, "Space or A: try again    Esc: title", middle + 2.5f * spacing, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
    }
}

private val GAME_OVER_COLOR = Color(0xFF6A5AFFu)

/** Covers the whole window with [color], under the screen's text. */
private fun shade(app: App, color: Color) {
    app.canvas.drawRect(x = 0f, y = 0f, width = app.window.width.toFloat(), height = app.window.height.toFloat(), color = color, layer = 0)
}

/** Draws [text] centred across the window with its top at [y]. */
private fun drawCentred(app: App, text: CharSequence, y: Float, scale: Float, color: Color = Color.WHITE) {
    drawShadowed(app, text, x = app.window.width / 2f, y = y, scale = scale, color = color, align = TextAlign.CENTER)
}

/** Draws [text] with its anchor at ([x], [y]) over a dark copy a little down and to the right. */
private fun drawShadowed(app: App, text: CharSequence, x: Float, y: Float, scale: Float, color: Color = Color.WHITE, align: TextAlign = TextAlign.LEFT) {
    app.canvas.drawText(app.assets.defaultFont, text, x = x, y = y, color = color, scale = scale, align = align, layer = 2, shadow = TEXT_SHADOW)
}

/** The shadow under every line of screen text. */
private val TEXT_SHADOW = TextShadow(Tuning.TEXT_SHADOW_OFFSET, Tuning.TEXT_SHADOW_OFFSET, Tuning.TEXT_SHADOW_COLOR)
