package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.ComponentType
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.platform.Image
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.asRegion
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Moves `KLYPH_SPRITES` sprites around the window and draws them through the app's sprite pass.
 * The sprites take `KLYPH_TEXTURES` circle textures in turn, all in layer 0, so each texture is
 * one draw call. With `KLYPH_INTERPOLATE=1` they move in `FIXED_UPDATE` at the same speeds and are
 * marked [Interpolated], so each frame draws them blended between their last two fixed steps.
 */
internal val spriteBenchmark: Benchmark = Benchmark(
    name = "sprites",
    description = "Moves and spins KLYPH_SPRITES textured sprites over KLYPH_TEXTURES textures and draws them through the sprite pass; KLYPH_INTERPOLATE=1 moves them in fixed steps.",
) { settings ->
    require(settings.textureCount > 0) { "KLYPH_TEXTURES ${settings.textureCount} is not positive." }
    var sprites = 0
    val report = StatsReport(settings.frameLimit) { "sprites=$sprites textures=${settings.textureCount}" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            println("textures=${settings.textureCount} interpolate=${if (settings.interpolate) "on" else "off"}")
            val regions = List(settings.textureCount) { app.assets.createTexture(circleImage(64, TINTS[it % TINTS.size])).asRegion() }
            // At the camera's zoom of 1 a world unit is a point; sizing in points as the pixel size
            // divided by the density keeps each sprite SPRITE_PIXELS pixels across on every
            // display, so the GPU fills as many pixels as on a density-1 window.
            val size = SPRITE_PIXELS / app.window.pixelDensity
            spawnSprites(app.world, regions, settings.spriteCount, size, app.window.width.toFloat(), app.window.height.toFloat())
            if (settings.interpolate) app.world.forEach(Velocity) { entity, _ -> app.world.add(entity, Interpolated, Interpolated) }
        }
        addSystem(Phase.UPDATE) { app -> sprites = app.stats.spriteCount }
        // Time.deltaSeconds is the fixed step inside FIXED_UPDATE and the frame time in UPDATE, so
        // both move the sprites at the same speed in points per second.
        addSystem(if (settings.interpolate) Phase.FIXED_UPDATE else Phase.UPDATE) { app ->
            move(app.world, app.time.deltaSeconds.coerceAtMost(0.1f), app.window.width.toFloat(), app.window.height.toFloat())
        }
    }
}

/** Straight-line motion in world units (points) per second, plus spin in radians per second. */
internal class Velocity(var dx: Float, var dy: Float, var spin: Float) {
    companion object : ComponentType<Velocity>("Velocity")
}

/** Tints baked into the benchmark's textures, the first white so one texture looks as before. */
private val TINTS: List<Color> = listOf(
    Color.WHITE,
    Color(red = 1f, green = 0.55f, blue = 0.55f),
    Color(red = 0.55f, green = 1f, blue = 0.55f),
    Color(red = 0.55f, green = 0.65f, blue = 1f),
    Color(red = 1f, green = 0.95f, blue = 0.5f),
    Color(red = 0.5f, green = 1f, blue = 1f),
    Color(red = 1f, green = 0.55f, blue = 1f),
    Color(red = 1f, green = 0.75f, blue = 0.4f),
)

/** Width and height of every benchmark sprite in physical pixels. */
private const val SPRITE_PIXELS = 24f

/**
 * Adds [count] sprites of [size] by [size] world units at random positions in a [width] by
 * [height] area, with seed 42 so every run is the same. Sprite `i` shows `regions[i % regions.size]`.
 */
private fun spawnSprites(world: World, regions: List<TextureRegion>, count: Int, size: Float, width: Float, height: Float) {
    val random = Random(42)
    repeat(count) { i ->
        val entity = world.create()
        world.add(entity, Transform, Transform(x = random.nextFloat() * width, y = random.nextFloat() * height, rotation = random.nextFloat() * 2f * PI.toFloat()))
        world.add(
            entity,
            Sprite,
            Sprite(
                regions[i % regions.size],
                width = size,
                height = size,
                color = Color(red = 0.5f + random.nextFloat() * 0.5f, green = 0.5f + random.nextFloat() * 0.5f, blue = 0.5f + random.nextFloat() * 0.5f, alpha = 0.9f),
            ),
        )
        world.add(entity, Velocity, Velocity(dx = random.nextFloat() * 200f - 100f, dy = random.nextFloat() * 200f - 100f, spin = random.nextFloat() * 4f - 2f))
    }
}

/** Moves and spins every sprite by [delta] seconds of its velocity, bouncing off the edges of a [width] by [height] area. */
internal fun move(world: World, delta: Float, width: Float, height: Float) {
    world.forEach(Transform, Velocity) { _, transform, velocity ->
        transform.x += velocity.dx * delta
        transform.y += velocity.dy * delta
        transform.rotation += velocity.spin * delta
        if (transform.x < 0f) { transform.x = 0f; velocity.dx = -velocity.dx }
        if (transform.x > width) { transform.x = width; velocity.dx = -velocity.dx }
        if (transform.y < 0f) { transform.y = 0f; velocity.dy = -velocity.dy }
        if (transform.y > height) { transform.y = height; velocity.dy = -velocity.dy }
    }
}

/**
 * Draws a soft disc of [tint] into a [size] by [size] image, premultiplied by its coverage, so the
 * benchmarks need no image files.
 */
internal fun circleImage(size: Int, tint: Color = Color.WHITE): Image {
    val image = Image(size, size)
    val radius = size / 2f
    for (y in 0 until size) for (x in 0 until size) {
        val dx = x + 0.5f - radius
        val dy = y + 0.5f - radius
        val distance = sqrt(dx * dx + dy * dy)
        val coverage = (radius - distance).coerceIn(0f, 1f)
        image[x, y] = Color(red = coverage * tint.red, green = coverage * tint.green, blue = coverage * tint.blue, alpha = coverage)
    }
    return image
}
