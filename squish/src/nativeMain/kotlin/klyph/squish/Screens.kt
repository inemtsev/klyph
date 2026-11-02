package klyph.squish

import klyph.app.App
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.text.TextAlign
import kotlin.math.ceil

// Everything drawn through app.canvas from RENDER systems: the blanket and the swatter in world
// space under every screen, and the text of each screen in screen space, in points, with the
// engine's built-in font and a drop shadow so it reads on the grass and on the blanket.

/** Draws the picnic blanket: a cream cloth with red gingham stripes and a soft shadow on the grass. */
fun drawBlanket(app: App) {
    val canvas = app.canvas
    val x = Tuning.BLANKET_X
    val y = Tuning.BLANKET_Y
    val width = Tuning.BLANKET_WIDTH
    val height = Tuning.BLANKET_HEIGHT
    val check = Tuning.BLANKET_CHECK
    val layer = Tuning.LAYER_BLANKET
    // One texture and one layer, so the rectangles keep this order: shadow, cloth, stripes.
    val offset = Tuning.BLANKET_SHADOW_OFFSET
    canvas.drawRect(x = x + offset, y = y + offset, width = width, height = height, color = Tuning.BLANKET_SHADOW_COLOR, layer = layer)
    canvas.drawRect(x = x, y = y, width = width, height = height, color = Tuning.BLANKET_COLOR, layer = layer)
    var column = 0f
    while (column < width) {
        canvas.drawRect(x = x + column, y = y, width = check, height = height, color = Tuning.BLANKET_STRIPE_COLOR, layer = layer)
        column += 2 * check
    }
    var row = 0f
    while (row < height) {
        canvas.drawRect(x = x, y = y + row, width = width, height = check, color = Tuning.BLANKET_STRIPE_COLOR, layer = layer)
        row += 2 * check
    }
}

/** Draws the fly swatter with its head on the cursor, squashed and tilted for a moment after each swat. */
fun drawSwatter(app: App) {
    val game = app.game
    val controls = game.controls
    val isSlapping = game.slapTimer > 0f
    val size = Tuning.CURSOR_SIZE * if (isSlapping) Tuning.SWAT_SLAP_SCALE else 1f
    app.canvas.drawSprite(
        game.art.swatter,
        x = controls.cursorX,
        y = controls.cursorY,
        width = size,
        height = size,
        rotation = if (isSlapping) Tuning.SWAT_SLAP_TILT else 0f,
        layer = Tuning.LAYER_CURSOR,
        originX = Tuning.CURSOR_ORIGIN_X,
        originY = Tuning.CURSOR_ORIGIN_Y,
    )
}

/**
 * Draws the round's score in the top-left corner, the time left as a bar across the top, and the
 * food that is left in the top-right corner, greyed out once eaten.
 */
fun Round.drawHud(app: App) {
    val text = game.text
    val margin = Tuning.HUD_MARGIN
    text.clear()
    text.append("Squished ").append(score)
    drawShadowed(app, text, x = margin, y = margin, scale = Tuning.TEXT_SCALE)

    val canvas = app.canvas
    val barX = (app.window.width - Tuning.TIME_BAR_WIDTH) / 2
    canvas.drawRect(x = barX, y = margin, width = Tuning.TIME_BAR_WIDTH, height = Tuning.TIME_BAR_HEIGHT, color = Tuning.TIME_BAR_BACK, layer = 1, space = DrawSpace.SCREEN)
    val share = secondsLeft / Tuning.ROUND_SECONDS
    canvas.drawRect(x = barX, y = margin, width = Tuning.TIME_BAR_WIDTH * share, height = Tuning.TIME_BAR_HEIGHT, color = Tuning.TIME_BAR_FILL, layer = 1, space = DrawSpace.SCREEN)
    text.clear()
    text.append(ceil(secondsLeft).toInt())
    drawShadowed(app, text, x = barX + Tuning.TIME_BAR_WIDTH + margin / 2, y = margin, scale = Tuning.TEXT_SCALE)

    val icon = Tuning.FOOD_ICON_SIZE
    var x = app.window.width - margin - icon / 2
    for (index in food.indices.reversed()) {
        val piece = app.world.getOrNull(food[index], Food) ?: continue
        canvas.drawSprite(
            game.art.food(piece.kind),
            x = x,
            y = margin + icon / 2,
            width = icon,
            height = icon,
            color = if (piece.bites == 0) Tuning.EATEN_FOOD_TINT else Color.WHITE,
            layer = 1,
            space = DrawSpace.SCREEN,
        )
        x -= icon
    }
}

/** Draws the game's name, what to do, how to start, the best score so far and the small print. */
fun drawTitle(app: App) {
    val game = app.game
    val height = app.window.height.toFloat()
    drawCentred(app, "BUG SQUISH!", height * 0.08f, scale = Tuning.TITLE_TEXT_SCALE, color = Tuning.TITLE_COLOR)
    drawCentred(app, "Squish the bugs before they eat the picnic!", height * 0.08f + 2.2f * Tuning.LINE_SPACING, scale = Tuning.SMALL_TEXT_SCALE)
    drawCentred(app, "Click or press A to play", height * 0.8f, scale = Tuning.TEXT_SCALE)
    if (game.bestScore > 0) {
        game.text.clear()
        game.text.append("Best ").append(game.bestScore)
        drawCentred(app, game.text, height * 0.8f + Tuning.LINE_SPACING, scale = Tuning.SMALL_TEXT_SCALE)
    }
    drawCentred(
        app,
        "Mouse, arrows and Space, or a gamepad    Esc: pause and quit    F3: debug",
        height - Tuning.HUD_MARGIN - Tuning.LINE_SPACING / 2,
        scale = Tuning.SMALL_TEXT_SCALE,
        color = Tuning.HINT_COLOR,
    )
}

/** Darkens the frozen round and draws the score, "Paused" and how to go on. */
fun drawPause(app: App) {
    shade(app, Tuning.PAUSE_SHADE)
    app.resources.getOrNull<Round>()?.drawHud(app)
    val middle = app.window.height / 2f
    drawCentred(app, "Paused", middle - 2 * Tuning.LINE_SPACING, scale = Tuning.BIG_TEXT_SCALE)
    drawCentred(app, "Click or press Esc to keep playing", middle + Tuning.LINE_SPACING / 2, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
}

/**
 * Draws over the frozen round why it ended, how many bugs were squished, up to three medals, the
 * best score and the way on. The way on shows once the screen takes presses.
 */
fun drawEnd(app: App) {
    val game = app.game
    val round = app.round
    val text = game.text
    val middle = app.window.height / 2f
    val spacing = Tuning.LINE_SPACING
    shade(app, Tuning.END_SHADE)
    val headline = if (round.endOrNull == RoundEnd.FOOD_EATEN) "The bugs ate the picnic!" else "Time's up!"
    drawCentred(app, headline, middle - 4 * spacing, scale = Tuning.BIG_TEXT_SCALE)
    text.clear()
    text.append("You squished ").append(round.score).append(if (round.score == 1) " bug!" else " bugs!")
    drawCentred(app, text, middle - 2 * spacing, scale = Tuning.TEXT_SCALE)
    drawMedals(app, medalsFor(round.score), middle - 0.5f * spacing)
    text.clear()
    text.append("Best ").append(game.bestScore)
    drawCentred(app, text, middle + 2 * spacing, scale = Tuning.SMALL_TEXT_SCALE)
    if (app.time.elapsed - game.endedAt >= Tuning.END_INPUT_DELAY) {
        drawCentred(app, "Click or press A to play again", middle + 3.5f * spacing, scale = Tuning.TEXT_SCALE, color = Tuning.HINT_COLOR)
        drawCentred(app, "Esc: title", middle + 5f * spacing, scale = Tuning.SMALL_TEXT_SCALE, color = Tuning.HINT_COLOR)
    }
}

/** Returns how many medals, from 0 to 3, a round of [score] squished bugs earns. */
private fun medalsFor(score: Int): Int = when {
    score >= Tuning.THREE_MEDALS -> 3
    score >= Tuning.TWO_MEDALS -> 2
    score >= 1 -> 1
    else -> 0
}

/**
 * Draws three ladybugs as medals centred across the window with their tops at [y]: the first
 * [earned] in color, the others greyed out.
 */
private fun drawMedals(app: App, earned: Int, y: Float) {
    val size = Tuning.MEDAL_SIZE
    val middle = app.window.width / 2f
    for (medal in 0 until 3) {
        app.canvas.drawSprite(
            app.game.art.ladybug,
            x = middle + (medal - 1) * size * 1.3f,
            y = y + size / 2,
            width = size,
            height = size,
            color = if (medal < earned) Color.WHITE else Tuning.EATEN_FOOD_TINT,
            layer = 1,
            space = DrawSpace.SCREEN,
        )
    }
}

/** Covers the whole window with [color], under the screen's text. */
private fun shade(app: App, color: Color) {
    app.canvas.drawRect(x = 0f, y = 0f, width = app.window.width.toFloat(), height = app.window.height.toFloat(), color = color, layer = 0, space = DrawSpace.SCREEN)
}

/** Draws [text] centred across the window with its top at [y]. */
private fun drawCentred(app: App, text: CharSequence, y: Float, scale: Float, color: Color = Color.WHITE) {
    drawShadowed(app, text, x = app.window.width / 2f, y = y, scale = scale, color = color, align = TextAlign.CENTER)
}

/** Draws [text] with its anchor at ([x], [y]) over a dark copy a little down and to the right. */
private fun drawShadowed(
    app: App,
    text: CharSequence,
    x: Float,
    y: Float,
    scale: Float,
    color: Color = Color.WHITE,
    align: TextAlign = TextAlign.LEFT,
) {
    val font = app.assets.defaultFont
    val offset = Tuning.TEXT_SHADOW_OFFSET
    app.canvas.drawText(font, text, x = x + offset, y = y + offset, color = Tuning.TEXT_SHADOW_COLOR, scale = scale, align = align, layer = 2)
    app.canvas.drawText(font, text, x = x, y = y, color = color, scale = scale, align = align, layer = 3)
}
