package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.asRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Moves a tree of parents, orbiting children and grandchildren, so transform propagation does real work. */
internal val hierarchyBenchmark: Benchmark = Benchmark(
    name = "hierarchy",
    description = "A three-level tree of parents, orbiting children and grandchildren; measures transform propagation.",
) { settings ->
    var sprites = 0
    val report = StatsReport { "sprites=$sprites" }
    runApp(settings.appConfig("klyph hierarchy benchmark")) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val circle = app.assets.texture(circleImage(64))
            val shape = spawnTree(
                app.world,
                circle.asRegion(),
                settings.spriteCount,
                settings.childCount,
                // Sizes are in pixels; see spawnTree.
                1f / app.window.pixelDensity,
                app.window.width.toFloat(),
                app.window.height.toFloat(),
            )
            println(shape)
        }
        addSystem(Phase.UPDATE) { app ->
            sprites = app.stats.spriteCount
            val delta = app.time.deltaSeconds.coerceAtMost(0.1f)
            move(app.world, delta, app.window.width.toFloat(), app.window.height.toFloat())
            app.world.forEach(Transform, Spin) { _, transform, spin -> transform.rotation += spin.radiansPerSecond * delta }
        }
    }
}

/** Rotation in radians per second for entities that have a parent and so no [Velocity]. */
internal class Spin(var radiansPerSecond: Float) {
    companion object : ComponentType<Spin>("Spin")
}

/** The number of entities spawned at each level of the tree. */
private class TreeShape(val parents: Int, val children: Int, val grandchildren: Int, val childrenPerParent: Int) {
    val total: Int get() = parents + children + grandchildren

    override fun toString(): String =
        "parents=$parents children=$children (up to $childrenPerParent each) grandchildren=$grandchildren total=$total depth=3"
}

// Sprite sizes and ring radii in physical pixels.
private const val PARENT_SIZE = 32f
private const val CHILD_SIZE = 12f
private const val GRANDCHILD_SIZE = 6f
private const val CHILD_RING = 28f
private const val GRANDCHILD_RING = 12f

/**
 * Spawns about [total] sprites as a tree: parents of [childrenPerParent] children each, where
 * every fourth child (indices 0, 4, 8, ...) has two grandchildren. Each such family holds
 * 1 + c + 2 * ceil(c / 4) sprites for c = [childrenPerParent]. The number of full families is
 * `total / familySize`, rounded down; the leftover sprites (fewer than one family) become extra
 * parents without children, so the total is exact whenever [total] is at least one family.
 * Below one family, every sprite becomes a parent without children. Every sprite shows [region];
 * children are in layer 1 and grandchildren in layer 2, so they draw on top of their parents.
 *
 * Sizes and ring radii are the pixel constants above times [pointsPerPixel], so the GPU fills as
 * many pixels on every display as on a density-1 window.
 */
private fun spawnTree(
    world: World,
    region: TextureRegion,
    total: Int,
    childrenPerParent: Int,
    pointsPerPixel: Float,
    width: Float,
    height: Float,
): TreeShape {
    require(childrenPerParent >= 0) { "KLYPH_CHILDREN $childrenPerParent is less than zero." }
    val random = Random(42)
    val twoPi = 2f * PI.toFloat()
    val grandparentsPerFamily = (childrenPerParent + 3) / 4
    val familySize = 1 + childrenPerParent + 2 * grandparentsPerFamily
    val families = total / familySize
    val loose = total - families * familySize

    fun color() = Color(red = 0.5f + random.nextFloat() * 0.5f, green = 0.5f + random.nextFloat() * 0.5f, blue = 0.5f + random.nextFloat() * 0.5f, alpha = 0.9f)

    fun spawnParent(): Entity {
        val entity = world.create()
        world.add(entity, Transform, Transform(x = random.nextFloat() * width, y = random.nextFloat() * height, rotation = random.nextFloat() * twoPi))
        world.add(entity, Sprite, Sprite(region, width = PARENT_SIZE * pointsPerPixel, height = PARENT_SIZE * pointsPerPixel, color = color()))
        world.add(entity, Velocity, Velocity(dx = random.nextFloat() * 200f - 100f, dy = random.nextFloat() * 200f - 100f, spin = random.nextFloat() * 4f - 2f))
        return entity
    }

    fun spawnOnRing(parent: Entity, index: Int, count: Int, ringPixels: Float, sizePixels: Float, layer: Int): Entity {
        val angle = twoPi * index / count
        val ring = ringPixels * pointsPerPixel
        val size = sizePixels * pointsPerPixel
        val entity = world.create()
        world.add(entity, Transform, Transform(x = cos(angle) * ring, y = sin(angle) * ring))
        world.add(entity, Sprite, Sprite(region, width = size, height = size, color = color(), layer = layer))
        world.add(entity, Spin, Spin(random.nextFloat() * 1f - 0.5f))
        world.setParent(entity, parent)
        return entity
    }

    var children = 0
    var grandchildren = 0
    repeat(families) {
        val parent = spawnParent()
        for (c in 0 until childrenPerParent) {
            val child = spawnOnRing(parent, c, childrenPerParent, CHILD_RING, CHILD_SIZE, layer = 1)
            children++
            if (c % 4 == 0) {
                for (g in 0 until 2) {
                    spawnOnRing(child, g, 2, GRANDCHILD_RING, GRANDCHILD_SIZE, layer = 2)
                    grandchildren++
                }
            }
        }
    }
    repeat(loose) { spawnParent() }
    return TreeShape(parents = families + loose, children = children, grandchildren = grandchildren, childrenPerParent = childrenPerParent)
}
