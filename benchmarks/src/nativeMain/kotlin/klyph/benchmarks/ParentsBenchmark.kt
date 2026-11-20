package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.Transform
import klyph.render.asRegion
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Moves the `hierarchy` tree of parents, orbiting children and grandchildren, and asks
 * `World.parentOrNull` for every child and grandchild in `LATE_UPDATE` each frame. The call returns a
 * nullable `Entity` and so may box it. The report's `parent` stage is those calls alone, and the run's
 * last line counts the garbage collections after startup, which show how much garbage the calls make.
 */
internal val parentsBenchmark: Benchmark = Benchmark(
    name = "parents",
    description = "The hierarchy tree with World.parentOrNull called for every child and grandchild every frame; " +
        "measures the calls beside transform propagation.",
) { settings ->
    var sprites = 0
    // The time of the last frame's parentOrNull calls; the report reads it in the next frame.
    var parentTime = Duration.ZERO
    // Adds up the index of every parent found, so the calls cannot be dropped as unused.
    var checksum = 0L
    var collectionsBefore = 0L
    val report = StatsReport(settings.frameLimit, extraStages = listOf("parent" to { _ -> parentTime })) { "sprites=$sprites" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val circle = app.assets.createTexture(circleImage(64))
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
            collectionsBefore = collectionCount()
        }
        addSystem(Phase.UPDATE) { app ->
            sprites = app.stats.spriteCount
            val delta = app.time.deltaSeconds.coerceAtMost(0.1f)
            move(app.world, delta, app.window.width.toFloat(), app.window.height.toFloat())
            app.world.forEach(Transform, Spin) { _, transform, spin -> transform.rotation += spin.radiansPerSecond * delta }
        }
        addSystem(Phase.LATE_UPDATE) { app ->
            val start = TimeSource.Monotonic.markNow()
            var sum = 0L
            app.world.forEach(Spin) { entity, _ ->
                val parent = app.world.parentOrNull(entity)
                if (parent != null) sum += parent.index
            }
            parentTime = start.elapsedNow()
            checksum += sum
        }
        addSystem(Phase.SHUTDOWN) { println("collections=${collectionCount() - collectionsBefore} checksum=$checksum") }
    }
}
