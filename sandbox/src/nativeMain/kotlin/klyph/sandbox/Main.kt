package klyph.sandbox

import klyph.platform.Color
import klyph.platform.EventPump
import klyph.platform.GpuDevice
import klyph.platform.GpuValidation
import klyph.platform.KeyCode
import klyph.platform.Sdl
import klyph.platform.ShaderFormat
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag
import klyph.platform.acquireSwapchainTexture
import klyph.platform.beginRenderPass

/**
 * Sandbox entry point: a smoke test that opens a window and clears it every frame until the
 * window closes, escape is pressed, or `KLYPH_FRAMES` frames have run.
 *
 * Environment: `KLYPH_FRAMES` frames before exit, `KLYPH_VALIDATION=1` for the GPU validation layer.
 */
fun main() {
    Sdl.init(setOf(Subsystem.VIDEO))
    val window = Window("klyph sandbox", 1280, 720, setOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY))
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val device = GpuDevice(setOf(ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL), validation)
    device.claim(window)
    println("GPU driver: ${device.driverName}, validation: $validation")
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()

    EventPump().use { events ->
        var frames = 0
        var running = true
        while (running) {
            while (events.poll()) {
                if (events.isQuit || (events.isKeyDown && events.keyCode == KeyCode.ESCAPE)) running = false
            }
            val commands = device.acquireCommandBuffer()
            val target = commands.acquireSwapchainTexture(window)
            if (target != null) commands.beginRenderPass(target, CLEAR_COLOR).end()
            commands.submit()
            frames++
            if (frameLimit != null && frames >= frameLimit) running = false
        }
        println("Ran $frames frames.")
    }

    device.release(window)
    device.close()
    window.close()
    Sdl.quit()
}

private val CLEAR_COLOR: Color = Color(red = 0.10f, green = 0.12f, blue = 0.18f)
