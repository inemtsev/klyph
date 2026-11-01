package klyph.app

import klyph.entity.propagateTransforms
import klyph.entity.recordPreviousTransforms
import klyph.event.GamepadButtonPressed
import klyph.event.GamepadButtonReleased
import klyph.event.GamepadConnected
import klyph.event.GamepadDisconnected
import klyph.event.KeyPressed
import klyph.event.KeyReleased
import klyph.event.MouseButtonPressed
import klyph.event.MouseButtonReleased
import klyph.event.MouseMoved
import klyph.event.MouseWheel
import klyph.event.QuitRequested
import klyph.event.WindowFocusGained
import klyph.event.WindowFocusLost
import klyph.event.WindowDensityChanged
import klyph.event.WindowResized
import klyph.platform.EventPump
import klyph.platform.Files
import klyph.platform.GpuDevice
import klyph.platform.PlatformEvent
import klyph.platform.PresentMode
import klyph.platform.Sdl
import klyph.platform.ShaderFormat
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag
import klyph.render.Camera
import klyph.render.advanceSpriteAnimations
import klyph.render.lookAt
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
 * does. At the start of INPUT the engine updates [App.input] from the platform events and
 * translates them into `klyph.event` events. It advances every `SpriteAnimation` and shows its
 * current frame in the entity's `Sprite` at the start of LATE_UPDATE, and draws every entity with a `Transform` and a `Sprite` through
 * [App.camera] at the end of RENDER. Before each FIXED_UPDATE step it records the transform of
 * every entity marked `Interpolated`, and the drawing blends those entities between that record
 * and their current transform by [Time.fixedStepAlpha]. A `QuitRequested` event also calls
 * [App.quit], so closing the window ends the app. [App.commands] is applied after `setup`, after
 * every system and after the transform propagation.
 *
 * The camera starts with the window's pixel size as its viewport, a zoom equal to the window's
 * pixel density so that one world unit is one point, and centered on the middle of the window, so
 * world (0, 0) is the top-left corner. When the window is resized the engine updates the viewport
 * and leaves the camera where it is. When the window's pixel density changes, for example because
 * it moved to another display, the engine multiplies the zoom by the ratio of the new density to
 * the old one, so the view keeps its framing, and publishes `WindowDensityChanged`.
 *
 * The SDL video and gamepad subsystems, the window, the GPU device, [App.assets] and the renderer exist only
 * while this call runs, and only one app may run at a time. Exceptions thrown by [setup] or a system end
 * the loop without running SHUTDOWN; everything is still released, and the exception propagates.
 * Initialization fails with an exception if SDL, the window or the GPU device cannot be created.
 *
 * Samples live in the sandbox because they need a display.
 *
 * @throws klyph.platform.SdlException if SDL, the window or the GPU device cannot be initialized.
 */
public fun runApp(config: AppConfig = AppConfig(), setup: App.() -> Unit) {
    Sdl.init(setOf(Subsystem.VIDEO, Subsystem.GAMEPAD))
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
                    Assets(device, config.assetRoot ?: Files.basePath + "assets/").use { assets ->
                        EventPump().use { pump ->
                            SpritePass(device, window, config.target, config.gpuSync).use { pass ->
                                val app = App(config, window, presentMode, assets, startCamera(window))
                                app.setup()
                                if (!app.commands.isEmpty) app.commands.apply()
                                FrameLoop(app, pump, pass).run()
                            }
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

/**
 * Returns a camera whose viewport is [window]'s drawable size in pixels and whose zoom is its pixel
 * density, so one world unit is one point, positioned so that world (0, 0) is the window's
 * top-left corner.
 */
private fun startCamera(window: Window): Camera {
    val width = window.pixelWidth.coerceAtLeast(1)
    val height = window.pixelHeight.coerceAtLeast(1)
    return Camera(zoom = window.pixelDensity, viewportWidth = width, viewportHeight = height).apply {
        lookAt(window.width / 2f, window.height / 2f)
    }
}

/** Runs [app]'s phases: startup, frames until a quit request, shutdown. */
private class FrameLoop(private val app: App, private val pump: EventPump, private val pass: SpritePass) {
    private val clock = TimeSource.Monotonic
    private val stepper = FixedStepper(app.time.fixedDelta, app.config.maxFixedStepsPerFrame)

    /** The window's pixel density the camera's zoom was last scaled for. */
    private var pixelDensity = app.window.pixelDensity

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

        app.input.beginFrame()
        translatePlatformEvents()
        app.input.endFrame(app.camera, pixelDensity)
        app.run(Phase.INPUT)
        var mark = clock.markNow()
        stats.add(Phase.INPUT.ordinal, (mark - start).inWholeNanoseconds)

        val steps = stepper.accumulate(time.delta)
        time.fixedStepAlpha = stepper.alpha
        for (step in 0 until steps) {
            time.beginFixedStep()
            app.world.recordPreviousTransforms()
            app.run(Phase.FIXED_UPDATE)
            time.endFixedStep()
        }
        mark = lap(Phase.FIXED_UPDATE.ordinal, mark)

        app.run(Phase.UPDATE)
        mark = lap(Phase.UPDATE.ordinal, mark)

        app.world.advanceSpriteAnimations(time.delta)
        app.run(Phase.LATE_UPDATE)
        mark = lap(Phase.LATE_UPDATE.ordinal, mark)

        app.world.propagateTransforms()
        if (!app.commands.isEmpty) app.commands.apply()
        mark = lap(FrameStats.PROPAGATION, mark)

        app.run(Phase.RENDER)
        pass.render(app.world, app.camera, app.clearColor, time.fixedStepAlpha, stats)
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

    /**
     * Drains the platform's event queue into [App.input] and [App.events], the engine's first step
     * of [Phase.INPUT].
     */
    private fun translatePlatformEvents() {
        val events = app.events
        val input = app.input
        while (true) {
            val event = pump.pollOrNull() ?: return
            input.handle(event)
            when (event) {
                PlatformEvent.Quit -> {
                    events.publish(QuitRequested)
                    app.quit()
                }
                is PlatformEvent.KeyDown -> events.publish(KeyPressed(event.key, isRepeat = event.isRepeat, event.scancode))
                is PlatformEvent.KeyUp -> events.publish(KeyReleased(event.key, event.scancode))
                is PlatformEvent.MouseMoved -> events.publish(MouseMoved(x = event.x, y = event.y, dx = event.dx, dy = event.dy))
                is PlatformEvent.MouseButtonDown ->
                    events.publish(MouseButtonPressed(event.button, x = event.x, y = event.y, clicks = event.clicks))
                is PlatformEvent.MouseButtonUp -> events.publish(MouseButtonReleased(event.button, x = event.x, y = event.y))
                is PlatformEvent.MouseWheel -> events.publish(MouseWheel(dx = event.dx, dy = event.dy))
                is PlatformEvent.GamepadConnected -> events.publish(GamepadConnected(event.id, event.name))
                is PlatformEvent.GamepadDisconnected -> events.publish(GamepadDisconnected(event.id))
                is PlatformEvent.GamepadButtonDown -> events.publish(GamepadButtonPressed(event.id, event.button))
                is PlatformEvent.GamepadButtonUp -> events.publish(GamepadButtonReleased(event.id, event.button))
                // Axis motion is state only: App.input holds the latest value.
                is PlatformEvent.GamepadAxisMoved -> {}
                PlatformEvent.WindowFocusGained -> events.publish(WindowFocusGained)
                PlatformEvent.WindowFocusLost -> events.publish(WindowFocusLost)
                is PlatformEvent.WindowResized -> {
                    pass.resize(event.pixelWidth, event.pixelHeight)
                    // A minimized window can report a zero size; the camera keeps its last viewport.
                    if (event.pixelWidth > 0 && event.pixelHeight > 0) {
                        app.camera.viewportWidth = event.pixelWidth
                        app.camera.viewportHeight = event.pixelHeight
                    }
                    events.publish(WindowResized(pixelWidth = event.pixelWidth, pixelHeight = event.pixelHeight))
                }
                is PlatformEvent.WindowDensityChanged -> {
                    // The platform also reports content scale changes that leave the density as it was.
                    if (event.pixelDensity != pixelDensity) {
                        app.camera.zoom *= event.pixelDensity / pixelDensity
                        pixelDensity = event.pixelDensity
                        events.publish(WindowDensityChanged(event.pixelDensity))
                    }
                }
            }
        }
    }

    override fun toString(): String = "FrameLoop(app=$app)"
}
