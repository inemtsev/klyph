package klyph.roller

import klyph.app.App
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.render.TextureRegion
import klyph.render.visibleHeight
import klyph.render.visibleWidth

// The sky behind the level: a gradient, far clouds, the distant city and near clouds, each layer
// moving at its own share of the camera's speed. klyph has no parallax layers, and the canvas
// draws every WORLD item before every SCREEN item, so a backdrop drawn in screen space would
// cover the level. Everything here is drawn in world space instead, placed relative to the
// camera each frame, so it sorts by layer under the buildings.

/** A cloud: where it sits along its layer's repeating strip and how high, which shape, how big. */
private class CloudSpot(val x: Float, val y: Float, val variant: Int, val scale: Float)

private val FAR_CLOUDS = listOf(
    CloudSpot(x = 80f, y = 60f, variant = 0, scale = 3f),
    CloudSpot(x = 420f, y = 130f, variant = 1, scale = 2f),
    CloudSpot(x = 760f, y = 40f, variant = 2, scale = 3f),
    CloudSpot(x = 1120f, y = 150f, variant = 0, scale = 2f),
    CloudSpot(x = 1450f, y = 80f, variant = 1, scale = 3f),
    CloudSpot(x = 1820f, y = 120f, variant = 2, scale = 2f),
    CloudSpot(x = 2140f, y = 50f, variant = 1, scale = 3f),
)

private val NEAR_CLOUDS = listOf(
    CloudSpot(x = 200f, y = 100f, variant = 1, scale = 5f),
    CloudSpot(x = 900f, y = 30f, variant = 2, scale = 4f),
    CloudSpot(x = 1500f, y = 140f, variant = 0, scale = 5f),
    CloudSpot(x = 2000f, y = 60f, variant = 2, scale = 4f),
)

private val FAR_CLOUD_TINT = Color(0xF4F0FFC8u)

/** App-level RENDER system: draws the sky, the clouds and the distant city behind every screen. */
fun drawBackdrop(app: App) {
    val camera = app.camera
    val art = app.game.art
    val width = camera.visibleWidth
    val height = camera.visibleHeight
    val left = camera.x - width / 2
    val top = camera.y - height / 2
    // A little larger than the view, so no edge shows while the camera shakes.
    app.canvas.drawSprite(
        art.sky, x = camera.x, y = camera.y, width = width + 40f, height = height + 2f,
        layer = Tuning.LAYER_SKY, space = DrawSpace.WORLD,
    )
    drawClouds(app, FAR_CLOUDS, Tuning.FAR_CLOUD_PARALLAX, FAR_CLOUD_TINT, Tuning.LAYER_FAR_CLOUDS, left, top, width)
    drawSkyline(app, art.skyline, left, width)
    drawClouds(app, NEAR_CLOUDS, Tuning.NEAR_CLOUD_PARALLAX, Color.WHITE, Tuning.LAYER_NEAR_CLOUDS, left, top, width)
}

/**
 * Draws a layer of clouds that moves at [parallax] of the camera's speed and repeats every
 * [Tuning.CLOUD_PERIOD] units, with the view's left edge at [left].
 */
private fun drawClouds(app: App, clouds: List<CloudSpot>, parallax: Float, color: Color, layer: Int, left: Float, top: Float, width: Float) {
    val art = app.game.art
    val period = Tuning.CLOUD_PERIOD
    for (index in clouds.indices) {
        val cloud = clouds[index]
        val region = art.clouds[cloud.variant]
        val cloudWidth = region.width * cloud.scale
        // Where the cloud is across the screen, wrapped onto the repeating strip.
        val across = positiveRemainder(cloud.x - left * parallax, period)
        for (copy in 0..1) {
            val x = across - copy * period
            if (x > width || x + cloudWidth < 0f) continue
            app.canvas.drawSprite(
                region, x = left + x, y = top + cloud.y, width = cloudWidth, height = region.height * cloud.scale,
                color = color, layer = layer, space = DrawSpace.WORLD, originX = 0f, originY = 0f,
            )
        }
    }
}

/** Draws the distant city along the bottom of the view, repeated across it, at its parallax. */
private fun drawSkyline(app: App, skyline: TextureRegion, left: Float, width: Float) {
    val scale = Tuning.SKYLINE_SCALE
    val tileWidth = skyline.width * scale
    val tileHeight = skyline.height * scale
    var x = -positiveRemainder(left * Tuning.SKYLINE_PARALLAX, tileWidth)
    while (x < width) {
        app.canvas.drawSprite(
            skyline, x = left + x, y = Tuning.WORLD_HEIGHT - tileHeight, width = tileWidth, height = tileHeight,
            layer = Tuning.LAYER_SKYLINE, space = DrawSpace.WORLD, originX = 0f, originY = 0f,
        )
        x += tileWidth
    }
}

/** Returns [value] modulo [period], from 0 up to [period]. */
private fun positiveRemainder(value: Float, period: Float): Float {
    val remainder = value % period
    return if (remainder < 0f) remainder + period else remainder
}
