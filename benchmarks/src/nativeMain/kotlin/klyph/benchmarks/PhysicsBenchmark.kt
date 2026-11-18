package klyph.benchmarks

import klyph.physics.ParticleCollision
import klyph.physics.PhysicsConfig
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import klyph.physics.SoftBodyShape
import klyph.physics.SolidGrid
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Steps the physics stress world with no window and prints, for each workload, the average step,
 * the 99th percentile and the slowest, in milliseconds. The world is a room 40 tiles of 16 wide:
 * [BOXES] rigid boxes in a block on the left, [GRAINS] grains of sand heaped on the right and
 * [BLOBS] soft balls dropped on the sand, at [SUBSTEPS] substeps of [ITERATIONS] passes, 60 steps a
 * second. Each kind is timed alone and then all together, every one from its first step and with
 * sleeping off, so nothing gets cheaper by falling asleep. Uses only klyph-physics' public API.
 */
internal val physicsBenchmark = Benchmark(
    name = "physics",
    description = "Steps the physics stress world headless: 1000 boxes, 5000 grains and 50 soft bodies, " +
        "alone and together; KLYPH_FRAMES sets the steps per workload (default 600).",
    run = ::runPhysicsBenchmark,
)

private const val BOXES = 1000
private const val GRAINS = 5000
private const val BLOBS = 50
private const val SUBSTEPS = 8
private const val ITERATIONS = 2
private const val TILE = 16f
private const val COLUMNS = 40
private const val ROWS = 23
private const val BOX = 8f
private const val BOX_GAP = 0.5f
private const val BOX_COLUMNS = 40
private const val GRAIN_RADIUS = 1.25f
private const val BLOB_RADIUS = 8f
private const val BLOB_TOP = 30f
private val STEP: Duration = 1.seconds / 60

private fun runPhysicsBenchmark(settings: BenchmarkSettings) {
    val steps = settings.frameLimit ?: 600
    println("Physics stress world, $SUBSTEPS substeps of $ITERATIONS passes, never asleep, $steps steps from the first:")
    time("boxes", steps, boxes = BOXES, grains = 0, blobs = 0)
    time("sand", steps, boxes = 0, grains = GRAINS, blobs = 0)
    time("soft bodies", steps, boxes = 0, grains = 0, blobs = BLOBS)
    time("everything", steps, boxes = BOXES, grains = GRAINS, blobs = BLOBS)
    println("No GPU stats: this benchmark opens no window and no device.")
}

/** Builds the stress world with [boxes], [grains] and [blobs], steps it [steps] times and prints the times. */
private fun time(name: String, steps: Int, boxes: Int, grains: Int, blobs: Int) {
    val world = stressWorld(boxes, grains, blobs)
    val millis = DoubleArray(steps)
    val clock = TimeSource.Monotonic
    for (i in 0 until steps) {
        val start = clock.markNow()
        world.step(STEP)
        millis[i] = start.elapsedNow().inWholeNanoseconds / 1e6
    }
    val average = millis.sum() / steps
    millis.sort()
    val p99 = millis[(steps * 99 / 100).coerceAtMost(steps - 1)]
    println(
        "  ${name.padEnd(12)} average=${fixed(average, 2)}ms p99=${fixed(p99, 2)}ms slowest=${fixed(millis[steps - 1], 2)}ms " +
            "(${world.particles.size} particles, ${world.rigidBodies.size} rigid bodies, ${world.softBodies.size} soft bodies, " +
            "${world.contactCount} rigid-body contacts at the end)",
    )
}

/** The stress world: a room with [boxes] boxes on the left, [grains] grains of sand on the right and [blobs] soft balls above them. */
private fun stressWorld(boxes: Int, grains: Int, blobs: Int): PhysicsWorld {
    val config = PhysicsConfig(substeps = SUBSTEPS, iterations = ITERATIONS, timeToSleep = Duration.INFINITE)
    val world = PhysicsWorld(config)
    world.solids = SolidGrid(COLUMNS, ROWS, TILE).apply {
        for (column in 0 until COLUMNS) for (row in ROWS - 2 until ROWS) fill(column, row)
        for (row in 0 until ROWS) {
            fill(0, row)
            fill(COLUMNS - 1, row)
        }
    }
    val floor = (ROWS - 2) * TILE
    val step = BOX + BOX_GAP
    for (i in 0 until boxes) {
        val column = i % BOX_COLUMNS
        val row = i / BOX_COLUMNS
        world.rigidBody(Shape.box(BOX, BOX), TILE + step * column + step / 2f, floor - step * row - step / 2f)
    }
    // The sand: a hex heap from the right wall, as wide as the room right of the block.
    val left = TILE + step * BOX_COLUMNS + GRAIN_RADIUS + 2f
    val right = (COLUMNS - 1) * TILE - GRAIN_RADIUS
    val gap = 2f * GRAIN_RADIUS + 0.1f
    var row = 0
    var y = floor - GRAIN_RADIUS
    while (world.particles.size < grains) {
        var x = left + if (row % 2 == 1) gap / 2f else 0f
        while (x <= right && world.particles.size < grains) {
            world.particles.add(x, y, radius = GRAIN_RADIUS, collision = ParticleCollision.EVERYTHING)
            x += gap
        }
        y -= gap * 0.87f
        row++
    }
    // The soft bodies: rows of balls above the sand, dropped on it.
    val ball = SoftBodyShape.circle(radius = BLOB_RADIUS, segments = 12, coreMass = 8f)
    val across = 10
    val spacing = (right - left) / across
    for (i in 0 until blobs) {
        world.softBody(ball, left + spacing * (i % across + 0.5f), BLOB_TOP + (i / across) * (2f * BLOB_RADIUS + 4f))
    }
    return world
}
