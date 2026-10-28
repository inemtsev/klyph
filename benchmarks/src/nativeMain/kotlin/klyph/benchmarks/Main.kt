package klyph.benchmarks

import klyph.platform.Color
import klyph.platform.EventPump
import klyph.platform.GpuDevice
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.Sdl
import klyph.platform.ShaderFormat
import klyph.platform.Subsystem
import klyph.platform.TextureUsage
import klyph.platform.Window
import klyph.platform.WindowFlag
import klyph.platform.createTexture
import klyph.platform.release
import kotlin.system.exitProcess

/** Every benchmark this executable can run, in the order they are listed. */
internal val benchmarks: List<Benchmark> = listOf(spriteBenchmark)

/** The background every benchmark clears its frame to. */
internal val CLEAR_COLOR: Color = Color(red = 0.10f, green = 0.12f, blue = 0.18f)

/**
 * Runs the benchmark named by the first argument. Without an argument, prints the available
 * benchmarks and exits with status 0; with an unknown name, prints them and exits with status 1.
 *
 * Environment: `KLYPH_SPRITES` sprite count (default 10000), `KLYPH_FRAMES` frames before exit,
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

    Sdl.init(setOf(Subsystem.VIDEO))
    val window = Window("klyph ${benchmark.name} benchmark", 1280, 720, setOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY))
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val device = GpuDevice(setOf(ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL), validation)
    device.claim(window)
    val presentMode = if (Sdl.environmentVariable("KLYPH_VSYNC") == "1") PresentMode.VSYNC else PresentMode.IMMEDIATE
    val supported = PresentMode.entries.filter { device.supportsPresentMode(window, it) }
    device.setPresentMode(window, presentMode)
    println("GPU driver: ${device.driverName}, present mode: $presentMode (supported: $supported), validation: $validation")

    val spriteCount = Sdl.environmentVariable("KLYPH_SPRITES")?.toIntOrNull() ?: 10_000
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val offscreenTarget =
        if (Sdl.environmentVariable("KLYPH_OFFSCREEN") == "1") {
            device.createTexture(window.pixelWidth, window.pixelHeight, device.swapchainFormat(window), TextureUsage.RENDER_TARGET)
        } else {
            null
        }

    EventPump().use { events ->
        benchmark.run(BenchmarkContext(device, window, events, spriteCount, frameLimit, offscreenTarget))
    }

    offscreenTarget?.let { device.release(it) }
    device.release(window)
    device.close()
    window.close()
    Sdl.quit()
}

private fun printUsage() {
    println("Usage: benchmarks <name>")
    println("Available benchmarks:")
    val width = benchmarks.maxOf { it.name.length }
    for (benchmark in benchmarks) println("  ${benchmark.name.padEnd(width)}  ${benchmark.description}")
}
