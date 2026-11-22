package klyph.benchmarks

import klyph.tiles.Cell
import klyph.tiles.DistanceField
import klyph.tiles.Neighborhood
import klyph.tiles.Tile
import klyph.tiles.TileGrid
import klyph.tiles.TileSet
import klyph.tiles.findPath
import kotlin.time.TimeSource

/**
 * Times pathfinding over a [SIZE] by [SIZE] grid with no window: recomputing a [DistanceField]
 * from one goal in the bottom-right corner, and a [findPath] from the top-left corner to it, each
 * with four and with eight neighbors. The grid is a maze of walls one cell thick every
 * [WALL_SPACING] columns, each open at the bottom or the top in turn, so the path winds through
 * every corridor and both searches reach almost every cell. Prints the average run, the 99th
 * percentile and the slowest, in milliseconds.
 */
internal val pathBenchmark = Benchmark(
    name = "paths",
    description = "Recomputes a distance field and finds a corner-to-corner path on a 256 by 256 maze, headless; " +
        "KLYPH_FRAMES sets the runs per workload (default 200).",
    run = ::runPathBenchmark,
)

private const val SIZE = 256
private const val WALL_SPACING = 4

/**
 * The maze, a [TileGrid] of its own: a [TileSet] needs a texture and so a GPU device, which a
 * headless benchmark has none of. Every search passes its own test for passable tiles, so
 * [tileSet] is never read.
 */
private class Maze : TileGrid {
    private val wall = Tile.of(0)
    private val walls = BooleanArray(SIZE * SIZE)

    init {
        var index = 0
        for (column in WALL_SPACING - 1 until SIZE - 1 step WALL_SPACING) {
            val gap = if (index++ % 2 == 0) SIZE - 1 else 0
            for (row in 0 until SIZE) if (row != gap) walls[row * SIZE + column] = true
        }
    }

    override val tileSet: TileSet get() = error("The maze has no tile set.")
    override val columns: Int get() = SIZE
    override val rows: Int get() = SIZE

    override fun get(column: Int, row: Int): Tile = if (walls[row * SIZE + column]) wall else Tile.EMPTY

    override fun toString(): String = "Maze(${SIZE}x$SIZE)"
}

private fun runPathBenchmark(settings: BenchmarkSettings) {
    val runs = settings.frameLimit ?: 200
    val maze = Maze()
    val start = Cell(0, 0)
    val goal = Cell(SIZE - 1, SIZE - 1)
    val goals = listOf(goal)
    val field = DistanceField()
    println("Pathfinding on a ${SIZE}x$SIZE maze, walls every $WALL_SPACING columns, $runs runs each:")
    for (neighborhood in Neighborhood.entries) {
        time("field ${neighborhood.name.lowercase()}", runs) {
            field.compute(maze, goals, neighborhood) { it.isEmpty }
            "${field[start.column, start.row]} steps from the far corner, $field"
        }
    }
    for (neighborhood in Neighborhood.entries) {
        time("path ${neighborhood.name.lowercase()}", runs) {
            "${maze.findPath(start, goal, neighborhood) { it.isEmpty }.size} cells"
        }
    }
    println("No GPU stats: this benchmark opens no window and no device.")
}

/** Runs [block] [runs] times and prints the times, with what the last run returned. */
private inline fun time(name: String, runs: Int, block: () -> String) {
    val millis = DoubleArray(runs)
    val clock = TimeSource.Monotonic
    var result = ""
    for (i in 0 until runs) {
        val start = clock.markNow()
        result = block()
        millis[i] = start.elapsedNow().inWholeNanoseconds / 1e6
    }
    val average = millis.sum() / runs
    millis.sort()
    val p99 = millis[(runs * 99 / 100).coerceAtMost(runs - 1)]
    println("  ${name.padEnd(11)} average=${fixed(average, 3)}ms p99=${fixed(p99, 3)}ms slowest=${fixed(millis[runs - 1], 3)}ms ($result)")
}
