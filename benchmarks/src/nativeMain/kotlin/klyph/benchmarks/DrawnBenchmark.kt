package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.drawnPositionOf
import klyph.app.runApp
import klyph.entity.Interpolated
import klyph.render.asRegion
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Moves `KLYPH_SPRITES` sprites in `FIXED_UPDATE`, each marked `Interpolated`, and asks
 * `App.drawnPositionOf` for every one of them in `LATE_UPDATE` each frame, as a game that draws a
 * marker over each entity would. The report's `drawn` stage is those calls alone, and `extract`
 * beside it is the sprite pass writing the same number of sprites. Each call allocates one `Vec2`,
 * and the run's last line counts the garbage collections after startup, which show how much
 * garbage the calls make.
 */
internal val drawnBenchmark: Benchmark = Benchmark(
    name = "drawn",
    description = "Moves KLYPH_SPRITES interpolated sprites and calls App.drawnPositionOf for each one every frame; " +
        "measures the calls beside the sprite extract.",
) { settings ->
    var sprites = 0
    var calls = 0
    // The time of the last frame's calls; the report reads it in the next frame, as it reads the stats.
    var callTime = Duration.ZERO
    // Adds up every drawn position, so the calls cannot be dropped as unused.
    var checksum = 0f
    var collectionsBefore = 0L
    val report = StatsReport(settings.frameLimit, extraStages = listOf("drawn" to { _ -> callTime })) { "sprites=$sprites calls=$calls" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val region = app.assets.createTexture(circleImage(64)).asRegion()
            val size = SPRITE_PIXELS / app.window.pixelDensity
            spawnSprites(app.world, listOf(region), settings.spriteCount, size, app.window.width.toFloat(), app.window.height.toFloat())
            app.world.forEach(Velocity) { entity, _ -> app.world.add(entity, Interpolated, Interpolated) }
            println("interpolated=${app.world.count(Interpolated)}")
            collectionsBefore = collectionCount()
        }
        addSystem(Phase.FIXED_UPDATE) { app ->
            move(app.world, app.time.deltaSeconds.coerceAtMost(0.1f), app.window.width.toFloat(), app.window.height.toFloat())
        }
        addSystem(Phase.UPDATE) { app -> sprites = app.stats.spriteCount }
        addSystem(Phase.LATE_UPDATE) { app ->
            val start = TimeSource.Monotonic.markNow()
            var sum = 0f
            var count = 0
            app.world.forEach(Interpolated) { entity, _ ->
                val drawn = app.drawnPositionOf(entity)
                sum += drawn.x + drawn.y
                count++
            }
            callTime = start.elapsedNow()
            calls = count
            checksum = sum
        }
        addSystem(Phase.SHUTDOWN) { println("collections=${collectionCount() - collectionsBefore} checksum=$checksum") }
    }
}

/** Returns how many garbage collections the process has run so far. */
@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
private fun collectionCount(): Long = GC.lastGCInfo?.epoch ?: 0L
