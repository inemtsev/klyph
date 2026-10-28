package klyph.benchmarks

import klyph.entity.ComponentType
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.beginCopyPass
import klyph.platform.beginRenderPass
import klyph.platform.createTexture
import klyph.platform.createTransferBuffer
import klyph.platform.release
import klyph.platform.uploadToTexture
import klyph.platform.write
import klyph.render.Sprite
import klyph.render.SpriteRenderer
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/** Moves `KLYPH_SPRITES` sprites around the window and draws them through [SpriteRenderer]. */
internal val spriteBenchmark: Benchmark = Benchmark(
    name = "sprites",
    description = "Moves and spins KLYPH_SPRITES textured sprites and draws them with SpriteRenderer.",
) { context ->
    SpriteScene(context).use { scene ->
        context.runFrames(scene::frame)
        scene.timer.printSummary()
    }
}

/** Straight-line motion in pixels per second, plus spin in radians per second. */
internal class Velocity(var dx: Float, var dy: Float, var spin: Float) {
    companion object : ComponentType<Velocity>("Velocity")
}

/** The sprite benchmark's world, renderer and texture, and one timed frame of it. */
private class SpriteScene(private val context: BenchmarkContext) : AutoCloseable {
    private val device = context.device
    private val window = context.window
    private val world = World(context.spriteCount)
    private val renderer = SpriteRenderer(device, device.swapchainFormat(window), capacity = context.spriteCount)
    private val texture: GpuTexture = uploadCircleTexture(device, 64)
    val timer = StageTimer(listOf("update", "acquire", "extract", "record+submit", "gpuwait")) { "sprites=${renderer.spriteCount}" }

    init {
        renderer.texture = texture
        val random = Random(42)
        val width = window.pixelWidth.toFloat()
        val height = window.pixelHeight.toFloat()
        repeat(context.spriteCount) {
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

    fun frame() {
        val delta = timer.beginFrame().inWholeNanoseconds / 1e9f
        val width = window.pixelWidth.toFloat()
        val height = window.pixelHeight.toFloat()

        update(delta.coerceAtMost(0.1f), width, height)
        timer.endStage()

        val commands = device.acquireCommandBuffer()
        val target = context.acquireTarget(commands)
        timer.endStage()

        renderer.extract(world)
        timer.endStage()

        if (target != null) {
            renderer.upload(commands)
            val pass = commands.beginRenderPass(target, CLEAR_COLOR)
            renderer.draw(commands, pass, width, height)
            pass.end()
        }
        context.submit(commands, timer)
        timer.endFrame()
    }

    private fun update(delta: Float, width: Float, height: Float) {
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

    override fun close() {
        renderer.close()
        device.release(texture)
    }
}

/** Builds a soft white disc as a sprite texture, so no image decoder is needed. */
private fun uploadCircleTexture(device: GpuDevice, size: Int): GpuTexture {
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
