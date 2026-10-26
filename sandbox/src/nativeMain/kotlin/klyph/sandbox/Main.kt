package klyph.sandbox

import klyph.platform.Color
import klyph.platform.EventPump
import klyph.platform.GpuDevice
import klyph.platform.GpuValidation
import klyph.platform.KeyCode
import klyph.platform.PresentMode
import klyph.platform.Sdl
import klyph.platform.ShaderFormat
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag

/**
 * Sandbox entry point. Runs the sprite benchmark.
 *
 * Environment: `KLYPH_SPRITES` sprite count (default 10000), `KLYPH_FRAMES` frames before exit,
 * `KLYPH_VSYNC=1` to cap at the display rate, `KLYPH_VALIDATION=1` for the GPU validation layer,
 * `KLYPH_OFFSCREEN=1` to render to a texture and wait for the GPU each frame instead of presenting.
 */
fun main() {
    Sdl.init(setOf(Subsystem.VIDEO))
    val window = Window("klyph sprite benchmark", 1280, 720, setOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY))
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val device = GpuDevice(setOf(ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL), validation)
    device.claim(window)
    val presentMode = if (Sdl.environmentVariable("KLYPH_VSYNC") == "1") PresentMode.VSYNC else PresentMode.IMMEDIATE
    val supported = PresentMode.entries.filter { device.supportsPresentMode(window, it) }
    device.setPresentMode(window, presentMode)
    println("GPU driver: ${device.driverName}, present mode: $presentMode (supported: $supported), validation: $validation")

    val spriteCount = Sdl.environmentVariable("KLYPH_SPRITES")?.toIntOrNull() ?: 10_000
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val offscreen = Sdl.environmentVariable("KLYPH_OFFSCREEN") == "1"

    EventPump().use { events ->
        SpriteBenchmark(device, window, spriteCount, offscreen).use { benchmark ->
            var running = true
            while (running) {
                while (events.poll()) {
                    if (events.isQuit || (events.isKeyDown && events.keyCode == KeyCode.ESCAPE)) running = false
                }
                benchmark.frame()
                if (frameLimit != null && benchmark.frames >= frameLimit) running = false
            }
            benchmark.printSummary()
        }
    }

    device.release(window)
    device.close()
    window.close()
    Sdl.quit()
}

internal val CLEAR_COLOR: Color = Color(red = 0.10f, green = 0.12f, blue = 0.18f)
