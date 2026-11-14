package klyph.roller

import klyph.app.App
import klyph.entity.Commands
import klyph.entity.Transform
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.render.ParallaxLayer
import klyph.render.Sprite
import klyph.render.TextureRegion

// The sky behind the level: a gradient drawn in the background space, which lies under everything the
// world draws, then the far clouds, the distant city and the near clouds, each a sprite with a
// ParallaxLayer that follows the camera at its own share of its speed and repeats to fill the view.

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

/**
 * Returns the transform x that puts a layer with [factor] at [x] while the camera is at [startX],
 * where the level's left edge holds it at the start. A [ParallaxLayer] draws its sprite at its
 * transform plus `1 - factor` times the camera's x.
 */
private fun layerX(x: Float, factor: Float, startX: Float): Float = x - (1f - factor) * startX

/** App-level RENDER system: draws the sky across the whole window, under everything else. */
fun drawSky(app: App) {
    app.canvas.drawSprite(
        app.game.art.sky, x = 0f, y = 0f, width = app.window.width.toFloat(), height = app.window.height.toFloat(),
        space = DrawSpace.BACKGROUND, originX = 0f, originY = 0f,
    )
}

/**
 * Spawns the far clouds, the distant city and the near clouds, placed for a camera that starts at
 * [startX]. They belong to no scene when this runs at startup, so every screen shows them.
 */
fun Commands.spawnBackdrop(art: Art, startX: Float) {
    spawnClouds(art, FAR_CLOUDS, Tuning.FAR_CLOUD_PARALLAX, FAR_CLOUD_TINT, Tuning.LAYER_FAR_CLOUDS, startX)
    spawnSkyline(art.skyline, startX)
    spawnClouds(art, NEAR_CLOUDS, Tuning.NEAR_CLOUD_PARALLAX, Color.WHITE, Tuning.LAYER_NEAR_CLOUDS, startX)
}

/**
 * Spawns a layer of clouds that moves at [parallax] of the camera's speed along x and repeats every
 * [Tuning.CLOUD_PERIOD] units. A cloud stays at its height on the screen, which its spot counts from
 * the top of the view, so its y is that height less half the view's.
 */
private fun Commands.spawnClouds(art: Art, clouds: List<CloudSpot>, parallax: Float, color: Color, layer: Int, startX: Float) {
    for (cloud in clouds) {
        spawn {
            val y = cloud.y - Tuning.WORLD_HEIGHT / 2
            add(Transform, Transform(layerX(cloud.x, parallax, startX), y, scaleX = cloud.scale, scaleY = cloud.scale))
            add(Sprite, Sprite(art.clouds[cloud.variant], color = color, layer = layer, originX = 0f, originY = 0f))
            add(ParallaxLayer, ParallaxLayer(factorX = parallax, factorY = 0f, repeatX = Tuning.CLOUD_PERIOD))
        }
    }
}

/** Spawns the distant city, standing on the bottom of the world and repeating along x at its parallax. */
private fun Commands.spawnSkyline(skyline: TextureRegion, startX: Float) {
    val width = skyline.width * Tuning.SKYLINE_SCALE
    val height = skyline.height * Tuning.SKYLINE_SCALE
    spawn {
        add(Transform, Transform(layerX(0f, Tuning.SKYLINE_PARALLAX, startX), Tuning.WORLD_HEIGHT))
        add(Sprite, Sprite(skyline, width, height, layer = Tuning.LAYER_SKYLINE, originX = 0f, originY = 1f))
        add(ParallaxLayer, ParallaxLayer(factorX = Tuning.SKYLINE_PARALLAX, factorY = 1f, repeatX = width))
    }
}
