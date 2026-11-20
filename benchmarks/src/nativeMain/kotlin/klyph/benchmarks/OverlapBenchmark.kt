package klyph.benchmarks

import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.collision.Overlap
import klyph.collision.contains
import klyph.collision.otherOrNull
import klyph.collision.otherThan
import klyph.event.Events
import klyph.render.asRegion
import klyph.render.lookAt
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Runs the `collisions` world and, in `LATE_UPDATE`, visits every `Overlap` of the frame twice: once
 * asking `otherOrNull(entity)`, which returns a nullable `Entity` and so may box it, and once asking
 * `entity in overlap` and then `otherThan(entity)`, which returns a plain `Entity`. The entity
 * passed is the pair's `a` and `b` in turn, so every call finds the other one. The report's
 * `ornull` and `control` stages are the two loops; the frames alternate which loop runs first, so
 * neither always finds the events already in the cache. Both loops add up the index of what they
 * get, so the calls cannot be dropped as unused. The run's last line gives the time of one call of
 * each loop over every frame, warm-up included, and counts the garbage collections after startup.
 */
internal val overlapBenchmark: Benchmark = Benchmark(
    name = "overlaps",
    description = "The collisions world; calls Overlap.otherOrNull for every overlap of the frame, " +
        "against contains plus otherThan.",
) { settings ->
    var colliders = 0
    var calls = 0
    // The times of the last frame's loops; the report reads them in the next frame, as it reads the stats.
    var orNullTime = Duration.ZERO
    var controlTime = Duration.ZERO
    var checksum = 0L
    // Every frame's calls and loop times, warm-up included, for the cost of one call.
    var totalCalls = 0L
    var totalOrNull = Duration.ZERO
    var totalControl = Duration.ZERO
    var collectionsBefore = 0L
    val report = StatsReport(
        settings.frameLimit,
        extraStages = listOf("ornull" to { _ -> orNullTime }, "control" to { _ -> controlTime }),
    ) { "colliders=$colliders calls=$calls" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            spawnCircles(app.world, app.assets.createTexture(circleImage(64)).asRegion(), settings.spriteCount)
            val camera = app.camera
            camera.zoom = min(camera.viewportWidth, camera.viewportHeight) / AREA
            camera.lookAt(AREA / 2f, AREA / 2f)
            collectionsBefore = collectionCount()
        }
        addSystem(Phase.FIXED_UPDATE) { app -> move(app.world, app.time.deltaSeconds, AREA, AREA) }
        addSystem(Phase.UPDATE) { app -> colliders = app.collisions.colliderCount }
        addSystem(Phase.LATE_UPDATE) { app ->
            val clock = TimeSource.Monotonic
            if (app.time.frameCount % 2L == 0L) {
                val orNullStart = clock.markNow()
                checksum += sumOrNull(app.events)
                orNullTime = orNullStart.elapsedNow()
                val controlStart = clock.markNow()
                checksum -= sumControl(app.events)
                controlTime = controlStart.elapsedNow()
            } else {
                val controlStart = clock.markNow()
                checksum -= sumControl(app.events)
                controlTime = controlStart.elapsedNow()
                val orNullStart = clock.markNow()
                checksum += sumOrNull(app.events)
                orNullTime = orNullStart.elapsedNow()
            }
            var count = 0
            app.events.forEach<Overlap> { count++ }
            calls = count
            totalCalls += count
            totalOrNull += orNullTime
            totalControl += controlTime
        }
        // Both loops find the same entities, so the checksum is 0 unless one of them is wrong.
        addSystem(Phase.SHUTDOWN) {
            val callCount = totalCalls.coerceAtLeast(1L)
            println(
                "ornull=${fixed(totalOrNull.inWholeNanoseconds.toDouble() / callCount, 2)}ns/call " +
                    "control=${fixed(totalControl.inWholeNanoseconds.toDouble() / callCount, 2)}ns/call " +
                    "collections=${collectionCount() - collectionsBefore} checksum=$checksum",
            )
        }
    }
}

/** Returns the sum of the indices `otherOrNull` gives for every overlap, passing `a` and `b` in turn. */
private fun sumOrNull(events: Events): Long {
    var sum = 0L
    var useB = false
    events.forEach<Overlap> { overlap ->
        val entity = if (useB) overlap.b else overlap.a
        useB = !useB
        val other = overlap.otherOrNull(entity)
        if (other != null) sum += other.index
    }
    return sum
}

/** Returns the sum of the indices `contains` and `otherThan` give for every overlap, passing `a` and `b` in turn. */
private fun sumControl(events: Events): Long {
    var sum = 0L
    var useB = false
    events.forEach<Overlap> { overlap ->
        val entity = if (useB) overlap.b else overlap.a
        useB = !useB
        if (entity in overlap) sum += overlap.otherThan(entity).index
    }
    return sum
}
