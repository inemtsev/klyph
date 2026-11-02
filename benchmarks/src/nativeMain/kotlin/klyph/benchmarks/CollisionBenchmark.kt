package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.collision.Collider
import klyph.collision.Shape
import klyph.entity.Transform
import klyph.entity.World
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.asRegion
import klyph.render.lookAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Moves `KLYPH_SPRITES` circle colliders with radii from 4 to 12 in random directions inside a
 * square area [AREA] world units across, bouncing at its edges, and lets the engine's own
 * detection after `FIXED_UPDATE` find the overlapping pairs every frame. `KLYPH_CELL` sets the
 * grid's cell size. The circles are also drawn, with the camera zoomed to fit the area, so the
 * windowed run shows them; the report's `collide` stage is the detection alone.
 */
internal val collisionBenchmark: Benchmark = Benchmark(
    name = "collisions",
    description = "Moves KLYPH_SPRITES circle colliders in a 4000x4000 area; measures overlap detection with cell size KLYPH_CELL.",
) { settings ->
    var colliders = 0
    val report = StatsReport(settings.frameLimit) { "colliders=$colliders cell=${settings.cellSize}" }
    runApp(settings.appConfig("klyph collisions benchmark")) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            spawnCircles(app.world, app.assets.texture(circleImage(64)).asRegion(), settings.spriteCount)
            val camera = app.camera
            camera.zoom = min(camera.viewportWidth, camera.viewportHeight) / AREA
            camera.lookAt(AREA / 2f, AREA / 2f)
        }
        addSystem(Phase.FIXED_UPDATE) { app -> move(app.world, app.time.deltaSeconds, AREA, AREA) }
        addSystem(Phase.UPDATE) { app -> colliders = app.collisions.colliderCount }
    }
}

/** The width and height of the square the circles move in, in world units. */
private const val AREA = 4000f

/**
 * Adds [count] circles with radii from 4 to 12 at random positions in the area, each moving at 20
 * to 100 world units per second in a random direction, with seed 42 so every run is the same.
 */
private fun spawnCircles(world: World, region: TextureRegion, count: Int) {
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
