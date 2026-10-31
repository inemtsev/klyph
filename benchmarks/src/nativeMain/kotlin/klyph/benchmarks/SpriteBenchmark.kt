package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.ComponentType
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.beginCopyPass
import klyph.platform.createTexture
import klyph.platform.createTransferBuffer
import klyph.platform.release
import klyph.platform.uploadToTexture
import klyph.platform.write
import klyph.render.Sprite
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/** Moves `KLYPH_SPRITES` sprites around the window and draws them through the app's sprite pass. */
internal val spriteBenchmark: Benchmark = Benchmark(
    name = "sprites",
    description = "Moves and spins KLYPH_SPRITES textured sprites and draws them with SpriteRenderer.",
) { settings ->
    var sprites = 0
    val report = StatsReport { "sprites=$sprites" }
    runApp(settings.appConfig("klyph sprites benchmark")) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            app.spriteTexture = uploadCircleTexture(app.device, 64)
            spawnSprites(app.world, settings.spriteCount, app.window.pixelWidth.toFloat(), app.window.pixelHeight.toFloat())
        }
        addSystem(Phase.UPDATE) { app ->
            sprites = app.stats.spriteCount
            move(app.world, app.time.deltaSeconds.coerceAtMost(0.1f), app.window.pixelWidth.toFloat(), app.window.pixelHeight.toFloat())
        }
        addSystem(Phase.SHUTDOWN) { app ->
            app.spriteTexture?.let { app.device.release(it) }
            app.spriteTexture = null
        }
    }
}

/** Straight-line motion in pixels per second, plus spin in radians per second. */
internal class Velocity(var dx: Float, var dy: Float, var spin: Float) {
    companion object : ComponentType<Velocity>("Velocity")
}

/** Adds [count] sprites at random positions in a [width] by [height] area, with seed 42 so every run is the same. */
private fun spawnSprites(world: World, count: Int, width: Float, height: Float) {
    val random = Random(42)
    repeat(count) {
        val entity = world.create()
        world.add(entity, Transform, Transform(x = random.nextFloat() * width, y = random.nextFloat() * height, rotation = random.nextFloat() * 2f * PI.toFloat()))
        world.add(
            entity,
            Sprite,
            Sprite(
                width = 24f,
                height = 24f,
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

/** Builds a soft white disc as a sprite texture, so no image decoder is needed. */
internal fun uploadCircleTexture(device: GpuDevice, size: Int): GpuTexture {
    val pixels = ByteArray(size * size * 4)
    val radius = size / 2f
    for (y in 0 until size) for (x in 0 until size) {
        val dx = x + 0.5f - radius
        val dy = y + 0.5f - radius
        val distance = sqrt(dx * dx + dy * dy)
        val coverage = (radius - distance).coerceIn(0f, 1f)
        val i = (y * size + x) * 4
        val value = (255 * coverage).toInt().toByte()
        pixels[i] = value
        pixels[i + 1] = value
        pixels[i + 2] = value
        pixels[i + 3] = value
    }
    val texture = device.createTexture(size, size)
    val staging = device.createTransferBuffer(pixels.size)
    device.write(staging, pixels)
    val commands = device.acquireCommandBuffer()
    val copy = commands.beginCopyPass()
    copy.uploadToTexture(staging, texture, size, size)
    copy.end()
    commands.submit()
    device.release(staging)
    return texture
}
