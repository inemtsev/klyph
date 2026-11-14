package klyph.app

import klyph.audio.Audio
import klyph.audio.AudioConfig
import klyph.audio.AudioOutput
import klyph.entity.advanceLifetimes
import klyph.entity.applyVelocities
import klyph.entity.propagateTransforms
import klyph.entity.recordPreviousTransforms
import klyph.particle.advanceParticles
import klyph.event.GamepadButtonPressed
import klyph.event.GamepadButtonReleased
import klyph.event.GamepadConnected
import klyph.event.GamepadDisconnected
import klyph.event.KeyPressed
import klyph.event.KeyReleased
import klyph.event.MouseButtonPressed
import klyph.event.MouseButtonReleased
import klyph.event.MouseMoved
import klyph.event.MouseWheelScrolled
import klyph.event.QuitRequested
import klyph.event.WindowFocusGained
import klyph.event.WindowFocusLost
import klyph.event.WindowDensityChanged
import klyph.event.WindowResized
import klyph.platform.AudioDevice
import klyph.platform.EventPump
import klyph.platform.Files
import klyph.platform.GpuDevice
import klyph.platform.PlatformEvent
import klyph.platform.PresentMode
import klyph.platform.Sdl
import klyph.platform.SdlException
import klyph.platform.ShaderFormat
import klyph.platform.printError
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag
import klyph.platform.WindowMode
import klyph.render.Camera
import klyph.render.advanceFades
import klyph.render.advanceSpriteAnimations
import klyph.render.clampToBounds
import klyph.render.lookAt
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Opens a window, calls [setup] on a new [App], runs the frame loop until [App.quit] is called,
 * and releases everything before it returns.
 *
 * [setup] registers systems with [App.addSystem]. Then [Phase.STARTUP] runs once, the loop runs
 * [Phase.INPUT], [Phase.FIXED_UPDATE] (zero or more steps), a transform propagation followed by
 * overlap detection through [App.collisions], [Phase.UPDATE], [Phase.LATE_UPDATE], another
 * transform propagation and [Phase.RENDER] once per frame until a frame ends with a quit
 * request, and finally [Phase.SHUTDOWN] runs once. STARTUP and SHUTDOWN run even when no frame
 * does. The loop also quits by itself once [AppConfig.frameLimit] frames have run or
 * [Time.elapsed] has reached [AppConfig.timeLimit]. At the start of INPUT the engine updates
 * [App.input] from the platform events and translates them into `klyph.event` events; inside a
 * FIXED_UPDATE step, [App.input]'s "just pressed" and "just released" answer for the changes since
 * the previous step instead of this frame's, so each change is seen by exactly one step. In each
 * FIXED_UPDATE step, after the systems have run and their commands have been applied, it moves
 * every entity with a `Velocity` by its velocity times [Time.fixedDelta], and then every
 * `KinematicBody` through [App.collisions], stopping it at the `Solid`s in its way. Right after
 * UPDATE it counts down every `Lifetime` and advances every `Fade`, destroying the entities whose
 * time is up. Between UPDATE and LATE_UPDATE it advances every `ParticleEmitter` by the frame's
 * delta, emitting at the entity's current world position. It advances every `SpriteAnimation` and
 * shows its current frame in the entity's `Sprite` at the start of LATE_UPDATE, publishing
 * `AnimationFinished` when a one-shot animation ends, and draws every entity with a `Transform`
 * and a `Sprite` or `Text` through [App.camera], clamped to its bounds, at the end of RENDER,
 * together with what RENDER systems queued on [App.canvas], which it then empties. Before each FIXED_UPDATE step it records the transform of
 * every entity marked `Interpolated`, and the drawing blends those entities between that record
 * and their current transform by [Time.fixedStepAlpha]. A `QuitRequested` event also calls
 * [App.quit], so closing the window ends the app. [App.commands] is applied after `setup`, after
 * every system and after the transform propagation.
 *
 * Right after it updates [App.input], the engine shows or hides [App.overlay] if
 * [AppConfig.overlayKey] went down this frame. While the overlay is visible, the engine queues it
 * on [App.canvas] after the last RENDER system, so it is drawn over everything else.
 *
 * In every phase but STARTUP and SHUTDOWN, the systems of the scene on top of [App.scenes] run
 * after the App-level systems. A pushed scene freezes the one below: its systems do not run, its
 * velocities, kinematic bodies, particle emitters, sprite animations, lifetimes, fades and colliders stand still, its entities keep rendering. The
 * engine skips the velocities, bodies, emitters and animations of entities a frozen scene owns when it advances them,
 * and leaves their colliders out of overlap detection, so they are in no `Overlap` event and no
 * [App.collisions] query finds them. Entities of no scene are never frozen. Once per frame, as soon
 * as the frame's duration is known and before INPUT, the engine adds that duration to
 * [klyph.scene.Scene.elapsed] of the scene on top, so the clock of a frozen scene stands still and
 * every phase of the frame, FIXED_UPDATE included, reads the same value. The scene transitions
 * queued during a frame apply at its end, after
 * drawing, and those queued in `setup` or STARTUP apply after STARTUP, before the first frame.
 * After the last frame every scene leaves the stack, top first, running its `onExit` action and
 * losing its entities, and then SHUTDOWN runs.
 *
 * The camera starts with the window's size in points as its viewport, zoom 1 so that one world
 * unit is one point, and centered on the middle of the window, so world (0, 0) is the top-left
 * corner. When the window is resized the engine updates the viewport and leaves the camera where
 * it is. When the window's pixel density changes, for example because it moved to another display,
 * the view keeps its framing without any change to the zoom, because the renderer turns points
 * into pixels, and the engine publishes `WindowDensityChanged`.
 *
 * The window opens in [AppConfig.windowMode] with [AppConfig.cursor] applied. When
 * [AppConfig.screenshot] names a frame, the engine calls `App.window.capture` during that frame,
 * before [Phase.RENDER], so the file is written once the frame is drawn.
 *
 * Audio starts before the window when [AppConfig.audio] enables it: the engine starts SDL's audio
 * subsystem and opens the default playback device, which calls [App.audio]'s mixer on its own
 * thread. Without a device, or when the audio subsystem cannot start, it prints one line to
 * standard error and the app runs with `App.audio.isEnabled` set to `false`. At the start of every
 * frame, before INPUT, the engine closes the music streams the mixer is done with and refreshes
 * `App.audio.stats`; if the mixer has thrown on the audio thread, it prints the exception once and
 * turns audio off. At shutdown it closes the device, then [App.audio].
 *
 * The SDL video, gamepad and audio subsystems, the window, the GPU device, the audio device,
 * [App.assets], [App.audio] and the renderer exist only while this call runs, and only one app may
 * run at a time. Exceptions thrown by [setup] or a system end
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
        val audio = Audio(config.audio)
        try {
            // The device closes first, so the mixer is no longer called when audio closes.
            openAudioDeviceOrNull(audio, config.audio).use { audioDevice ->
                openWindow(config).use { window ->
                    GpuDevice(setOf(ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL), config.validation).use { device ->
                        device.claim(window)
                        try {
                            val presentMode = if (device.supportsPresentMode(window, config.presentMode)) {
                                device.setPresentMode(window, config.presentMode)
                                config.presentMode
                            } else {
                                PresentMode.VSYNC
                            }
                            // Opening the assets also uploads the built-in font, so the device must exist.
                            Assets(device, config.assetRoot ?: Files.basePath + "assets/", config.textureFilter).use { assets ->
                                EventPump().use { pump ->
                                    SpritePass(device, window, config.target, config.gpuSync).use { pass ->
                                        val app = App(config, window, presentMode, assets, startCamera(window), audio)
                                        app.setup()
                                        if (!app.commands.isEmpty()) app.commands.apply()
                                        FrameLoop(app, pump, pass, audioDevice).run()
                                    }
                                }
                            }
                        } finally {
                            device.unclaim(window)
                        }
                    }
                }
            }
        } finally {
            audio.close()
        }
    } finally {
        Sdl.quit()
    }
}

/**
 * Starts SDL's audio subsystem and opens the default playback device fed by [audio]'s mixer, or
 * turns [audio] off and returns `null` when [config] disables audio or no device can be opened.
 * A disabled config is silent; a failure prints one line to standard error naming the reason.
 *
 * The audio subsystem starts on its own, after video, so a missing or broken audio driver leaves
 * the rest of the app running.
 */
private fun openAudioDeviceOrNull(audio: Audio, config: AudioConfig): AudioDevice? {
    if (config.output == AudioOutput.NONE) {
        audio.isEnabled = false
        return null
    }
    return try {
        Sdl.init(setOf(Subsystem.AUDIO))
        AudioDevice.open(config.sampleRate, config.bufferFrames, audio.renderer)
    } catch (e: SdlException) {
        audio.isEnabled = false
        printError("klyph: audio is off: ${e.message}")
        null
    }
}

/**
 * Opens the app's resizable, high-density window in [AppConfig.windowMode] and applies
 * [AppConfig.cursor] to it.
 */
private fun openWindow(config: AppConfig): Window {
    val flags = mutableSetOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY)
    if (config.windowMode == WindowMode.FULLSCREEN) flags += WindowFlag.FULLSCREEN
    val window = Window(config.title, config.width, config.height, flags)
    window.cursor = config.cursor
    return window
}

/**
 * Returns a camera at zoom 1, so one world unit is one point, whose viewport is [window]'s drawable
 * size in points, positioned so that world (0, 0) is the window's top-left corner.
 */
private fun startCamera(window: Window): Camera {
    val density = window.pixelDensity
    val width = pointsOf(window.pixelWidth, density)
    val height = pointsOf(window.pixelHeight, density)
    return Camera(viewportWidth = width, viewportHeight = height).apply {
        lookAt(width / 2f, height / 2f)
    }
}

/**
 * Returns [pixels] in points at [density] pixels per point, at least 1, the size the sprite pass
 * gives `DrawSpace.SCREEN`, so the camera's viewport and the screen projection agree.
 */
private fun pointsOf(pixels: Int, density: Float): Float = (pixels / density).coerceAtLeast(1f)

/** Runs [app]'s phases: startup, frames until a quit request, shutdown. */
private class FrameLoop(
    private val app: App,
    private val pump: EventPump,
    private val pass: SpritePass,
    private val audioDevice: AudioDevice?,
) {
    private val clock = TimeSource.Monotonic
    private val stepper = FixedStepper(app.time.fixedDelta, app.config.maxFixedStepsPerFrame)

    /** The window's pixel density the camera's viewport was last computed with. */
    private var pixelDensity = app.window.pixelDensity

    /** The window's drawable width in pixels, as the last resize reported it. */
    private var pixelWidth = app.window.pixelWidth

    /** The window's drawable height in pixels, as the last resize reported it. */
    private var pixelHeight = app.window.pixelHeight

    fun run() {
        val startup = clock.markNow()
        app.run(Phase.STARTUP)
        app.applySceneTransitions()
        app.stats.recordStartup(startup.elapsedNow().inWholeNanoseconds)

        var frameStart = clock.markNow()
        // A frame limit of zero runs no frame; a time limit needs a frame to count time.
        val frameLimit = app.config.frameLimit
        if (frameLimit != null && frameLimit <= 0) app.quit()
        while (!app.isQuitRequested) {
            val now = clock.markNow()
            val delta = now - frameStart
            frameStart = now
            frame(delta, now)
        }
        app.exitScenes()
        app.run(Phase.SHUTDOWN)
    }

    private fun frame(delta: Duration, start: TimeSource.Monotonic.ValueTimeMark) {
        val time = app.time
        val stats = app.stats
        app.events.endFrame()
        time.beginFrame(delta)
        // Outside a fixed step, so delta is the frame's duration and the scene clock counts frames, not steps.
        app.scenes.advanceClock(time.delta)
        updateAudio()

        app.input.beginFrame()
        translatePlatformEvents()
        app.input.endFrame(app.camera)
        toggleOverlay()
        app.run(Phase.INPUT)
        var mark = clock.markNow()
        stats.add(Phase.INPUT.ordinal, (mark - start).inWholeNanoseconds)

        val steps = stepper.accumulate(time.delta)
        time.fixedStepAlpha = stepper.alpha
        for (step in 0 until steps) {
            time.beginFixedStep()
            app.input.beginFixedStep()
            app.world.recordPreviousTransforms()
            app.run(Phase.FIXED_UPDATE)
            // Where a last FIXED_UPDATE system would move bodies, after the systems' commands applied.
            app.world.applyVelocities(time.deltaSeconds, app.scenes)
            // Bodies after plain velocities, against the solids as this step's systems left them.
            app.collisions.moveBodies(app.world, time.deltaSeconds, app.scenes)
            app.input.endFixedStep()
            time.endFixedStep()
        }
        mark = lap(Phase.FIXED_UPDATE.ordinal, mark)

        // Parented colliders need this frame's world transforms before detection.
        app.world.propagateTransforms()
        mark = lap(FrameStats.PROPAGATION, mark)
        app.collisions.detect(app.world, app.events, app.scenes)
        mark = lap(FrameStats.COLLISION, mark)

        app.run(Phase.UPDATE)
        // Lifetimes and fades count down with UPDATE; what they destroy is gone before particles advance.
        app.world.advanceLifetimes(time.delta, app.commands, app.scenes)
        app.world.advanceFades(time.delta, app.commands, app.scenes)
        if (!app.commands.isEmpty()) app.commands.apply()
        mark = lap(Phase.UPDATE.ordinal, mark)

        val particleCount = app.world.advanceParticles(time.deltaSeconds, app.scenes, app.commands)
        if (!app.commands.isEmpty()) app.commands.apply()
        mark = lap(FrameStats.PARTICLES, mark)

        app.world.advanceSpriteAnimations(time.delta, app.scenes, app.events, app.commands)
        if (!app.commands.isEmpty()) app.commands.apply()
        app.run(Phase.LATE_UPDATE)
        mark = lap(Phase.LATE_UPDATE.ordinal, mark)

        app.world.propagateTransforms()
        if (!app.commands.isEmpty()) app.commands.apply()
        mark = lap(FrameStats.PROPAGATION, mark)

        queueScreenshot()
        // The canvas accepts calls from the first RENDER system until the pass has read it.
        app.canvas.open()
        app.run(Phase.RENDER)
        if (app.overlay.isVisible) app.overlay.draw(app.canvas, app.assets.defaultFont, stats, time.fixedDelta, app.audio.stats.activeVoiceCount)
        app.camera.clampToBounds()
        pass.render(app.world, app.camera, app.canvas, app.clearColor, time.fixedStepAlpha, stats)
        lap(Phase.RENDER.ordinal, mark)

        app.applySceneTransitions()
        time.endFrame()
        if (isLimitReached()) app.quit()
        stats.entityCount = app.world.entityCount
        stats.publish(
            totalNanos = start.elapsedNow().inWholeNanoseconds,
            fixedStepCount = steps,
            spriteCount = pass.spriteCount,
            drawCallCount = pass.drawCallCount,
            collisionPairCount = app.collisions.pairCount,
            particleCount = particleCount,
        )
        app.overlay.record(stats.total)
    }

    /** Returns `true` once [AppConfig.frameLimit] frames have run or [Time.elapsed] has reached [AppConfig.timeLimit]. */
    private fun isLimitReached(): Boolean {
        val frames = app.config.frameLimit
        val seconds = app.config.timeLimit
        return (frames != null && app.time.frameCount >= frames) || (seconds != null && app.time.elapsed >= seconds)
    }

    /** Queues a capture of this frame when it is the one [AppConfig.screenshot] names. */
    private fun queueScreenshot() {
        val screenshot = app.config.screenshot ?: return
        // time.frameCount counts completed frames, so this frame is number frame + 1.
        if (app.time.frameCount + 1 == screenshot.frame.toLong()) app.window.capture(screenshot.path)
    }

    /** Shows or hides [App.overlay] when [AppConfig.overlayKey] went down this frame. */
    private fun toggleOverlay() {
        val key = app.config.overlayKey ?: return
        if (app.input.keyboard.isJustPressed(key)) app.overlay.isVisible = !app.overlay.isVisible
    }

    /**
     * Turns audio off, with one line to standard error, once the mixer has failed on the audio
     * thread, then closes the music streams the mixer is done with and refreshes the audio stats.
     */
    private fun updateAudio() {
        val audio = app.audio
        if (audio.isEnabled) {
            val failure = audioDevice?.failureOrNull()
            if (failure != null) {
                audio.isEnabled = false
                printError("klyph: audio is off: the mixer failed on the audio thread: $failure")
            }
        }
        audio.endFrame()
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
                is PlatformEvent.MouseWheelScrolled -> events.publish(MouseWheelScrolled(dx = event.dx, dy = event.dy))
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
                    pixelWidth = event.pixelWidth
                    pixelHeight = event.pixelHeight
                    updateViewport()
                    events.publish(WindowResized(pixelWidth = event.pixelWidth, pixelHeight = event.pixelHeight))
                }
                is PlatformEvent.WindowDensityChanged -> {
                    // The platform also reports content scale changes that leave the density as it was.
                    if (event.pixelDensity != pixelDensity) {
                        pixelDensity = event.pixelDensity
                        updateViewport()
                        events.publish(WindowDensityChanged(event.pixelDensity))
                    }
                }
            }
        }
    }

    /**
     * Sets the camera's viewport to the window's drawable size in points. A minimized window can
     * report a zero size; the camera then keeps its last viewport.
     */
    private fun updateViewport() {
        if (pixelWidth <= 0 || pixelHeight <= 0) return
        app.camera.viewportWidth = pointsOf(pixelWidth, pixelDensity)
        app.camera.viewportHeight = pointsOf(pixelHeight, pixelDensity)
    }

    override fun toString(): String = "FrameLoop(app=$app)"
}
