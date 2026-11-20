package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.collision.Collider
import klyph.collision.KinematicBody
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Transform
import klyph.entity.Velocity as EngineVelocity
import klyph.entity.World
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.asRegion
import klyph.render.lookAt
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Moves `KLYPH_SPRITES` circle colliders with radii from 4 to 12 in random directions inside a
 * square area [AREA] world units across, bouncing at its edges, and lets the engine's own
 * detection after `FIXED_UPDATE` find the overlapping pairs every frame. `KLYPH_CELL` sets the
 * grid's cell size. The circles are also drawn, with the camera zoomed to fit the area, so the
 * windowed run shows them; the report's `collide` stage is the detection alone.
 *
 * With `KLYPH_MOVING=<share>` set, from 0 to 1, it also spawns a platformer level: a platform for
 * every ten circles, a `Solid` 64 by 16 on a jittered grid, with a `KinematicBody` dropped onto
 * each, and gives that share of the platforms a `Velocity` that a system turns around every two
 * seconds, so the riders are carried and the neighbours pushed. The `fixed` stage then holds the
 * bodies' move, and the `collide` stage the solids' refresh.
 */
internal val collisionBenchmark: Benchmark = Benchmark(
    name = "collisions",
    description = "Moves KLYPH_SPRITES circle colliders in a 4000x4000 area; measures overlap detection with cell size KLYPH_CELL, " +
        "and with KLYPH_MOVING=<share> moving platforms and the bodies riding them.",
) { settings ->
    var colliders = 0
    val share = settings.movingShare
    val platforms = if (share == null) 0 else settings.spriteCount / 10
    val level = if (share == null) "" else " solids=$platforms moving=$share"
    val report = StatsReport(settings.frameLimit) { "colliders=$colliders cell=${settings.cellSize}$level" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val region = app.assets.createTexture(circleImage(64)).asRegion()
            spawnCircles(app.world, region, settings.spriteCount)
            if (share != null) spawnPlatforms(app.world, region, platforms, share)
            val camera = app.camera
            camera.zoom = min(camera.viewportWidth, camera.viewportHeight) / AREA
            camera.lookAt(AREA / 2f, AREA / 2f)
        }
        addSystem(Phase.FIXED_UPDATE) { app -> move(app.world, app.time.deltaSeconds, AREA, AREA) }
        if (share != null) addSystem(Phase.FIXED_UPDATE) { app -> stepPlatforms(app.world, app.time.deltaSeconds, app.time.fixedStepCount) }
        addSystem(Phase.UPDATE) { app -> colliders = app.collisions.colliderCount }
    }
}

/** The speed of a moving platform, and the fixed steps between its turns. */
private const val PLATFORM_SPEED = 40f
private const val PLATFORM_TURN_STEPS = 120L

/**
 * Adds [count] platforms on a jittered grid over the area, each with a body dropped onto it, and
 * gives [share] of them a `Velocity`, with seed 7 so every run is the same.
 */
private fun spawnPlatforms(world: World, region: TextureRegion, count: Int, share: Float) {
    val random = Random(7)
    val columns = max(1, ceil(sqrt(count.toFloat())).toInt())
    val spacing = AREA / columns
    for (i in 0 until count) {
        val x = (i % columns + 0.5f) * spacing + (random.nextFloat() - 0.5f) * spacing * 0.3f
        val y = (i / columns + 0.5f) * spacing + (random.nextFloat() - 0.5f) * spacing * 0.3f
        val platform = world.create()
        world.add(platform, Transform, Transform(x = x, y = y))
        world.add(platform, Collider, Collider(Shape.Box(width = 64f, height = 16f)))
        world.add(platform, Solid, Solid())
        world.add(platform, Sprite, Sprite(region, width = 64f, height = 16f))
        if (random.nextFloat() < share) {
            world.add(platform, EngineVelocity, EngineVelocity(dx = if (random.nextBoolean()) PLATFORM_SPEED else -PLATFORM_SPEED))
        }
        val body = world.create()
        world.add(body, Transform, Transform(x = x, y = y - 40f))
        world.add(body, Collider, Collider(Shape.Box(width = 12f, height = 12f)))
        world.add(body, KinematicBody, KinematicBody())
        world.add(body, Sprite, Sprite(region, width = 12f, height = 12f))
    }
}

/**
 * Adds gravity to every body, wraps a body that fell out of the area back to its top, and turns
 * every moving platform around once every [PLATFORM_TURN_STEPS] fixed steps.
 */
private fun stepPlatforms(world: World, delta: Float, step: Long) {
    world.forEach(KinematicBody, Transform) { _, body, transform ->
        body.dy = min(body.dy + 1800f * delta, 900f)
        if (transform.y > AREA) transform.y = 0f
    }
    if (step % PLATFORM_TURN_STEPS == 0L) {
        world.forEach(EngineVelocity, Solid) { _, velocity, _ -> velocity.dx = -velocity.dx }
    }
}

/** The width and height of the square the circles move in, in world units. */
internal const val AREA = 4000f

/**
 * Adds [count] circles with radii from 4 to 12 at random positions in the area, each moving at 20
 * to 100 world units per second in a random direction, with seed 42 so every run is the same.
 */
internal fun spawnCircles(world: World, region: TextureRegion, count: Int) {
    val random = Random(42)
    repeat(count) {
        val entity = world.create()
        val radius = 4f + random.nextFloat() * 8f
        world.add(entity, Transform, Transform(x = random.nextFloat() * AREA, y = random.nextFloat() * AREA))
        world.add(entity, Collider, Collider(Shape.Circle(radius)))
        world.add(entity, Sprite, Sprite(region, width = radius * 2f, height = radius * 2f))
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val speed = 20f + random.nextFloat() * 80f
        world.add(entity, Velocity, Velocity(dx = cos(angle) * speed, dy = sin(angle) * speed, spin = 0f))
    }
}
