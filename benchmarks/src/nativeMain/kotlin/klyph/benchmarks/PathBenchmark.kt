package klyph.benchmarks

import klyph.tiles.Cell
import klyph.tiles.DistanceField
import klyph.tiles.Neighbors
import klyph.tiles.Tile
import klyph.tiles.TileGrid
import klyph.tiles.TileSet
import klyph.tiles.shortestPath
import kotlin.time.TimeSource

/**
 * Times the searches of `klyph.tiles` on a grid of [SIZE] by [SIZE] cells with no window: a
 * [DistanceField] recomputed from two goals with four and with eight neighbors, and a shortest
 * path from one corner to the other, and prints the mean and slowest time of each in microseconds.
 * Every fourth column is a wall with one gap at a different row, so the searches wind through the
 * whole grid. The grid is an `object : TileGrid` without a tile set, so no GPU device is needed,
 * and the searches pass their own passability test.
 */
internal val pathBenchmark = Benchmark(
    name = "paths",
    description = "Recomputes a distance field and finds a corner-to-corner path on a 256 by 256 grid headless; " +
        "KLYPH_FRAMES sets the runs of each (default 200).",
    run = ::runPathBenchmark,
)

private const val SIZE = 256
private const val DEFAULT_RUNS = 200
private const val WARM_UP_RUNS = 5

private fun runPathBenchmark(settings: BenchmarkSettings) {
    val walls = BooleanArray(SIZE * SIZE)
    for (column in 1 until SIZE step 4) {
        for (row in 0 until SIZE) walls[row * SIZE + column] = row != column * 7 % SIZE
    }
    val wall = Tile.of(0)
    val grid = object : TileGrid {
        override val tileSet: TileSet get() = error("The benchmark's grid has no tile set.")
        override val columns: Int = SIZE
        override val rows: Int = SIZE
        override fun get(column: Int, row: Int): Tile = if (walls[row * SIZE + column]) wall else Tile.EMPTY
    }
    val isPassable: (Tile) -> Boolean = { it.isEmpty }
    val start = Cell(column = 0, row = 0)
    val end = Cell(column = SIZE - 1, row = SIZE - 1)
    val goals = listOf(start, end)
    val field = DistanceField()
    val runs = settings.frameLimit ?: DEFAULT_RUNS

    println("Paths: a ${SIZE}x$SIZE grid, a wall on every fourth column with one gap; $runs runs of each search.")
    time("field, four neighbors", runs) { field.compute(grid, goals = goals, neighbors = Neighbors.FOUR, isPassable = isPassable) }
    println("  $field, middle cell ${field[SIZE / 2, SIZE / 2]} steps from a goal")
    time("field, eight neighbors", runs) { field.compute(grid, goals = goals, neighbors = Neighbors.EIGHT, isPassable = isPassable) }
    var steps = 0
    time("path, four neighbors", runs) { steps = grid.shortestPath(start = start, end = end, isPassable = isPassable).size - 1 }
    println("  $steps steps from $start to $end")
    time("path, eight neighbors", runs) {
        steps = grid.shortestPath(start = start, end = end, neighbors = Neighbors.EIGHT, isPassable = isPassable).size - 1
    }
    println("  $steps steps from $start to $end")
    println("No GPU stats: this benchmark opens no window and no device.")
}

/** Runs [block] a few times to warm up, then [runs] times, and prints the mean and slowest run under [name]. */
private fun time(name: String, runs: Int, block: () -> Unit) {
    repeat(WARM_UP_RUNS) { block() }
    val clock = TimeSource.Monotonic
    var totalNanos = 0L
    var maxNanos = 0L
    repeat(runs) {
        val start = clock.markNow()
        block()
        val nanos = start.elapsedNow().inWholeNanoseconds
        totalNanos += nanos
        if (nanos > maxNanos) maxNanos = nanos
    }
    println("$name: mean=${fixed(totalNanos / 1000.0 / runs, 1)}us max=${fixed(maxNanos / 1000.0, 1)}us")
}
