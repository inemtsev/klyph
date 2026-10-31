package klyph.app

import klyph.entity.propagateTransforms
import klyph.event.KeyPressed
import klyph.event.KeyReleased
import klyph.event.QuitRequested
import klyph.event.WindowResized
import klyph.platform.EventPump
import klyph.platform.GpuDevice
import klyph.platform.PlatformEvent
import klyph.platform.PresentMode
import klyph.platform.Sdl
import klyph.platform.ShaderFormat
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Opens a window, calls [setup] on a new [App], runs the frame loop until [App.quit] is called,
 * and releases everything before it returns.
 *
 * [setup] registers systems with [App.addSystem]. Then [Phase.STARTUP] runs once, the loop runs
 * [Phase.INPUT], [Phase.FIXED_UPDATE] (zero or more steps), [Phase.UPDATE], [Phase.LATE_UPDATE],
 * the transform propagation and [Phase.RENDER] once per frame until a frame ends with a quit
 * request, and finally [Phase.SHUTDOWN] runs once. STARTUP and SHUTDOWN run even when no frame
 * does. The engine translates platform events into `klyph.event` events at the start of INPUT,
 * and draws every entity with a `WorldTransform` and a `Sprite` at the end of RENDER. A
 * `QuitRequested` event also calls [App.quit], so closing the window ends the app.
 *
 * The SDL video subsystem, the window, the GPU device and the renderer exist only while this
 * call runs, and only one app may run at a time. Exceptions thrown by [setup] or a system end
 * the loop without running SHUTDOWN; everything is still released, and the exception propagates.
 * Initialization fails with an exception if SDL, the window or the GPU device cannot be created.
 *
 * Samples live in the sandbox because they need a display.
 *
 * @throws klyph.platform.SdlException if SDL, the window or the GPU device cannot be initialized.
 */
public fun runApp(config: AppConfig = AppConfig(), setup: App.() -> Unit) {
    Sdl.init(setOf(Subsystem.VIDEO))
    try {
        Window(config.title, config.width, config.height, setOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY)).use { window ->
            GpuDevice(setOf(ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL), config.validation).use { device ->
                device.claim(window)
                try {
                    val presentMode = if (device.supportsPresentMode(window, config.presentMode)) {
                        device.setPresentMode(window, config.presentMode)
                        config.presentMode
                    } else {
                        PresentMode.VSYNC
                    }
                    EventPump().use { pump ->
                        SpritePass(device, window, config.target, config.gpuSync).use { pass ->
                            val app = App(config, window, device, presentMode)
                            app.setup()
                            FrameLoop(app, pump, pass).run()
                        }
                    }
                } finally {
                    device.release(window)
                }
            }
        }
    } finally {
        Sdl.quit()
    }
}

/** Runs [app]'s phases: startup, frames until a quit request, shutdown. */
private class FrameLoop(private val app: App, private val pump: EventPump, private val pass: SpritePass) {
    private val clock = TimeSource.Monotonic
    private val stepper = FixedStepper(app.time.fixedDelta, app.config.maxFixedStepsPerFrame)

    fun run() {
        val startup = clock.markNow()
        app.run(Phase.STARTUP)
        app.stats.recordStartup(startup.elapsedNow().inWholeNanoseconds)

        var frameStart = clock.markNow()
        while (!app.isQuitRequested) {
            val now = clock.markNow()
            val delta = now - frameStart
            frameStart = now
            frame(delta, now)
        }
        app.run(Phase.SHUTDOWN)
    }

    private fun frame(delta: Duration, start: TimeSource.Monotonic.ValueTimeMark) {
        val time = app.time
        val stats = app.stats
        app.events.endFrame()
        time.beginFrame(delta)

        translatePlatformEvents()
        app.run(Phase.INPUT)
        var mark = clock.markNow()
        stats.add(Phase.INPUT.ordinal, (mark - start).inWholeNanoseconds)

        val steps = stepper.accumulate(time.delta)
        for (step in 0 until steps) {
            time.beginFixedStep()
            app.run(Phase.FIXED_UPDATE)
            time.endFixedStep()
        }
        mark = lap(Phase.FIXED_UPDATE.ordinal, mark)

        app.run(Phase.UPDATE)
        mark = lap(Phase.UPDATE.ordinal, mark)

        app.run(Phase.LATE_UPDATE)
        mark = lap(Phase.LATE_UPDATE.ordinal, mark)

        app.world.propagateTransforms()
        mark = lap(FrameStats.PROPAGATION, mark)

        app.run(Phase.RENDER)
        pass.render(app.world, app.spriteTexture, app.clearColor, stats)
        lap(Phase.RENDER.ordinal, mark)

        time.endFrame()
        stats.publish(totalNanos = start.elapsedNow().inWholeNanoseconds, fixedSteps = steps, spriteCount = pass.spriteCount)
    }

    /** Adds the time since [mark] to [slot] of the frame's stats and returns the current time. */
    private fun lap(slot: Int, mark: TimeSource.Monotonic.ValueTimeMark): TimeSource.Monotonic.ValueTimeMark {
        val now = clock.markNow()
        app.stats.add(slot, (now - mark).inWholeNanoseconds)
        return now
    }

    /** Drains the platform's event queue into [App.events], the engine's first step of [Phase.INPUT]. */
    private fun translatePlatformEvents() {
        val events = app.events
        while (true) {
            when (val event = pump.pollOrNull() ?: return) {
                PlatformEvent.Quit -> {
                    events.publish(QuitRequested)
                    app.quit()
                }
                is PlatformEvent.KeyDown -> events.publish(KeyPressed(event.key, isRepeat = event.isRepeat))
                is PlatformEvent.KeyUp -> events.publish(KeyReleased(event.key))
                is PlatformEvent.WindowResized -> {
                    pass.resize(event.pixelWidth, event.pixelHeight)
                    events.publish(WindowResized(pixelWidth = event.pixelWidth, pixelHeight = event.pixelHeight))
                }
            }
        }
    }

    override fun toString(): String = "FrameLoop(app=$app)"
}
