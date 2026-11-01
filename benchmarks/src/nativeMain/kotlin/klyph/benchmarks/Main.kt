package klyph.benchmarks

import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.Sdl
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/** Every benchmark this executable can run, in the order they are listed. */
internal val benchmarks: List<Benchmark> = listOf(spriteBenchmark, hierarchyBenchmark)

/**
 * Runs the benchmark named by the first argument. Without an argument, prints the available
 * benchmarks and exits with status 0; with an unknown name, prints them and exits with status 1.
 *
 * Environment: `KLYPH_SPRITES` sprite count (default 10000), `KLYPH_CHILDREN` children per parent
 * in the hierarchy benchmark (default 8), `KLYPH_TEXTURES` textures in the sprites benchmark
 * (default 1), `KLYPH_INTERPOLATE=1` to move the sprites benchmark in fixed steps and draw it
 * interpolated, `KLYPH_FRAMES` frames before exit, `KLYPH_SECONDS` seconds of frame time before exit,
 * `KLYPH_VSYNC=1` to cap at the display rate, `KLYPH_VALIDATION=1` for the GPU validation layer,
 * `KLYPH_OFFSCREEN=1` to render to a texture and wait for the GPU each frame instead of presenting.
 */
fun main(args: Array<String>) {
    val name = args.firstOrNull()
    val benchmark = benchmarks.firstOrNull { it.name == name }
    if (benchmark == null) {
        if (name != null) println("Unknown benchmark '$name'.")
        printUsage()
        exitProcess(if (name == null) 0 else 1)
    }

    val settings = BenchmarkSettings(
        spriteCount = Sdl.environmentVariable("KLYPH_SPRITES")?.toIntOrNull() ?: 10_000,
        childCount = Sdl.environmentVariable("KLYPH_CHILDREN")?.toIntOrNull() ?: 8,
        textureCount = Sdl.environmentVariable("KLYPH_TEXTURES")?.toIntOrNull() ?: 1,
        interpolate = Sdl.environmentVariable("KLYPH_INTERPOLATE") == "1",
        frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull(),
        timeLimit = Sdl.environmentVariable("KLYPH_SECONDS")?.toDoubleOrNull()?.seconds,
        offscreen = Sdl.environmentVariable("KLYPH_OFFSCREEN") == "1",
        presentMode = if (Sdl.environmentVariable("KLYPH_VSYNC") == "1") PresentMode.VSYNC else PresentMode.IMMEDIATE,
        validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED,
    )
    benchmark.run(settings)
}

private fun printUsage() {
    println("Usage: benchmarks <name>")
    println("Available benchmarks:")
    val width = benchmarks.maxOf { it.name.length }
    for (benchmark in benchmarks) println("  ${benchmark.name.padEnd(width)}  ${benchmark.description}")
}
