package klyph.sandbox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.entity.Transform
import klyph.event.GamepadConnected
import klyph.event.GamepadDisconnected
import klyph.event.KeyPressed
import klyph.event.WindowFocusGained
import klyph.event.WindowFocusLost
import klyph.event.WindowResized
import klyph.math.degrees
import klyph.platform.GamepadButton
import klyph.platform.EventPump
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.PlatformEvent
import klyph.platform.Sdl
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag
import klyph.platform.Color
import klyph.platform.CursorMode
import klyph.platform.Image
import klyph.platform.Scancode
import klyph.platform.WindowMode
import klyph.particle.ParticleEmitter
import klyph.render.DrawSpace
import klyph.render.ParallaxLayer
import klyph.render.Sprite
import klyph.text.Text
import klyph.text.TextAlign
import klyph.text.TextShadow
import klyph.platform.SamplerFilter
import klyph.tiles.get
import klyph.tiles.spawnSolids
import klyph.tiles.spawnTileLayer
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Sandbox entry point: a smoke test that runs a klyph app, which clears the window every frame
 * and draws a little world-space and screen-space text and shapes, a sky under the world, two
 * parallax layers that repeat across a camera that sways, a particle fountain, a small Tiled
 * map from `assets/tiles.tmj` and two files packed into one atlas page, the second at frame
 * [LATE_REGION_FRAME], until the window closes, escape is pressed, `KLYPH_FRAMES` frames have run or
 * `KLYPH_SECONDS` seconds have passed. It prints a line for every mouse or gamepad button press,
 * gamepad connection and focus change it sees through `app.input` and the engine events, the manual
 * check of the engine's input path.
 *
 * With `KLYPH_EVENTS=1` it skips the engine and runs [printPlatformEvents] instead: a window with
 * no GPU work that prints every platform event, the manual check for keyboard, mouse, gamepad and
 * focus input.
 *
 * Environment, read by `AppConfig.fromEnvironment` and honoured by the engine: `KLYPH_FRAMES`
 * frames before exit, `KLYPH_SECONDS` seconds before exit, `KLYPH_VALIDATION=1` for the GPU
 * validation layer, `KLYPH_OFFSCREEN=1` to draw into a texture instead of the window,
 * `KLYPH_OVERLAY=1` to show the debug overlay from the first frame (F3 toggles it either way),
 * `KLYPH_SCREENSHOT=<frame>:<path>` to save that frame as a PNG, `KLYPH_FULLSCREEN=1` to open in
 * fullscreen, `KLYPH_FRAME_DELTA=<seconds>` to advance every frame by the same game time, so two
 * runs save identical PNGs. `KLYPH_EVENTS=1` runs the event printer, which reads the two limits itself.
 *
 * Keys: escape quits, F3 toggles the debug overlay, F11 switches between a window and fullscreen,
 * and C cycles the cursor through the system arrow, an image cursor and hidden, the manual check
 * of the window features.
 */
fun main() {
    val config = AppConfig.fromEnvironment(AppConfig(title = "klyph sandbox"))
    if (Sdl.environmentVariable("KLYPH_EVENTS") == "1") {
        printPlatformEvents(config.frameLimit, config.timeLimit)
        return
    }

    runApp(config) {
        addWindowControls(this)
        var swayCenter = 0f
        addSystem(Phase.STARTUP) { app ->
            println("${app.assets}, ${app.camera}, validation: ${config.validation}")
            swayCenter = app.camera.x
            // A world-space label, drawn through the camera by the sprite pass.
            val label = app.world.create()
            app.world.add(label, Transform, Transform(x = 40f, y = 120f))
            app.world.add(label, Text, Text(app.assets.defaultFont, "world text\nsecond line", scale = 2f))
            // Turned labels, one stretched with a shadow, so a run covers glyphs the vertex shader rotates.
            val turned = app.world.create()
            app.world.add(turned, Transform, Transform(x = 420f, y = 300f, rotation = 20f.degrees, scaleX = 1.5f))
            val font = app.assets.defaultFont
            val turnedText = Text(font, "turned text\ncentered", scale = 2f, align = TextAlign.CENTER, shadow = TextShadow())
            app.world.add(turned, Text, turnedText)
            val tilted = app.world.create()
            app.world.add(tilted, Transform, Transform(x = 760f, y = 220f, rotation = (-35f).degrees))
            app.world.add(tilted, Text, Text(font, "tilted, right aligned", scale = 2f, align = TextAlign.RIGHT))
            addBackdrop(app)
            addFountain(app)
            addTileMap(app)
            addAtlasSprite(app, "tiles.png", x = 560f, y = 330f, width = 128f, height = 32f)
        }
        addSystem(Phase.UPDATE) { app ->
            // frameCount counts the frames before this one.
            if (app.time.frameCount + 1 == LATE_REGION_FRAME) addAtlasSprite(app, "star.png", x = 760f, y = 330f, width = 64f, height = 64f)
        }
        // The camera sways 100 units either way, so the layers of the backdrop scroll by their own factors.
        addSystem(Phase.UPDATE) { app -> app.camera.x = swayCenter + 100f * sin(app.time.frameCount * 0.05f) }
        addSystem(Phase.RENDER) { app -> drawOverlay(app) }
        addSystem(Phase.INPUT) { app -> printInput(app) }
        addSystem(Phase.UPDATE) { app ->
            app.events.forEach<KeyPressed> { if (it.key == KeyCode.ESCAPE) app.quit() }
        }
        addSystem(Phase.SHUTDOWN) { app ->
            println(
                "Ran ${app.time.frameCount} frames, last ${app.stats.spriteCount} quads in ${app.stats.drawCallCount} draw calls, " +
                    "${app.stats.particleCount} particles.",
            )
        }
    }
}

/**
 * Adds the window feature checks: F11 switches between a window and fullscreen, and C cycles the
 * cursor through the system arrow, an image cursor made from a small [Image] and hidden.
 */
private fun addWindowControls(app: App) {
    // A 15x15 crosshair, yellow with a dark outline, its hotspot in the middle.
    val outline = Color(red = 0f, green = 0f, blue = 0f)
    val fill = Color(red = 1f, green = 0.9f, blue = 0.2f)
    val crosshair = Image(15, 15)
    for (i in 0 until 15) {
        for (j in 6..8) {
            crosshair[i, j] = outline
            crosshair[j, i] = outline
        }
    }
    for (i in 1 until 14) {
        crosshair[i, 7] = fill
        crosshair[7, i] = fill
    }
    val cursorNames = listOf("system arrow", "crosshair image", "hidden")
    var cursorStep = 0
    app.addSystem(Phase.INPUT) { app ->
        val window = app.window
        val keyboard = app.input.keyboard
        if (keyboard.isJustPressed(Scancode.F11)) {
            val wanted = if (window.mode == WindowMode.FULLSCREEN) WindowMode.WINDOWED else WindowMode.FULLSCREEN
            window.mode = wanted
            println("Window mode: asked for $wanted, now ${window.mode}.")
        }
        if (keyboard.isJustPressed(Scancode.C)) {
            cursorStep = (cursorStep + 1) % cursorNames.size
            when (cursorStep) {
                0 -> window.resetCursor()
                1 -> window.setCursor(crosshair, hotspotX = 7, hotspotY = 7)
            }
            window.cursor = if (cursorStep == 2) CursorMode.HIDDEN else CursorMode.VISIBLE
            println("Cursor: ${cursorNames[cursorStep]}, ${window.cursor}.")
        }
        app.events.forEach<WindowResized> { println("Resized: $it, ${window.width}x${window.height} points, ${app.camera}.") }
    }
}

/**
 * Draws a sky in the background space, a square in world space and, in screen space, a panel with
 * the frame number in the built-in font and a right-aligned line at the window's right edge, so a
 * run covers all three draw spaces and the built-in font.
 */
private fun drawOverlay(app: App) {
    val canvas = app.canvas
    val font = app.assets.defaultFont
    val sky = Color(red = 0.1f, green = 0.12f, blue = 0.25f)
    val windowWidth = app.window.width.toFloat()
    val windowHeight = app.window.height.toFloat()
    canvas.drawRect(x = 0f, y = 0f, width = windowWidth, height = windowHeight, color = sky, space = DrawSpace.BACKGROUND)
    canvas.drawRect(x = 40f, y = 200f, width = 64f, height = 64f, color = Color(red = 0.3f, green = 0.6f, blue = 1f), space = DrawSpace.WORLD)
    canvas.drawRect(x = 8f, y = 8f, width = 220f, height = 28f, color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f), space = DrawSpace.SCREEN)
    canvas.drawText(font, "frame ${app.time.frameCount}", x = 14f, y = 14f, scale = 2f, shadow = TextShadow())
    canvas.drawText(font, "klyph sandbox", x = app.window.width - 8f, y = 14f, scale = 2f, align = TextAlign.RIGHT)
}

/**
 * Adds two [ParallaxLayer] sprites: a row of green blocks that scrolls at half the camera's speed
 * and a seamless band of cloud at a quarter of it. Both repeat along x, so the view is covered
 * whatever the camera does. At the starting camera, world and screen coordinates agree, so the
 * blocks sit near the bottom of the window and the band near the top. A plain sprite, a crate on
 * the blocks, moves with the world and sorts with the layers.
 */
private fun addBackdrop(app: App) {
    val green = Color(red = 0.1f, green = 0.45f, blue = 0.25f)
    val cloud = Color(red = 0.8f, green = 0.85f, blue = 1f, alpha = 0.6f)
    val blocks = app.world.create()
    app.world.add(blocks, Transform, Transform(x = 0f, y = 500f))
    app.world.add(blocks, Sprite, Sprite(app.assets.whitePixel, width = 120f, height = 80f, color = green, layer = -2))
    app.world.add(blocks, ParallaxLayer, ParallaxLayer(factorX = 0.5f, factorY = 1f, repeatX = 160f))
    val clouds = app.world.create()
    app.world.add(clouds, Transform, Transform(x = 0f, y = 60f))
    app.world.add(clouds, Sprite, Sprite(app.assets.whitePixel, width = 128f, height = 24f, color = cloud, layer = -3))
    app.world.add(clouds, ParallaxLayer, ParallaxLayer(factorX = 0.25f, factorY = 1f, repeatX = 128f))
    val gold = Color(red = 1f, green = 0.8f, blue = 0.2f)
    val crate = app.world.create()
    app.world.add(crate, Transform, Transform(x = 640f, y = 436f))
    app.world.add(crate, Sprite, Sprite(app.assets.whitePixel, width = 48f, height = 48f, color = gold))
}

/**
 * Adds a particle fountain: an emitter that sprays small discs upwards at a steady rate, pulled
 * back down by gravity and fading from orange to transparent, plus one burst at startup, so a run
 * covers the particle step and its drawing.
 */
private fun addFountain(app: App) {
    val emitter = ParticleEmitter(app.assets.disc(6), capacity = 512).apply {
        rate = 120f
        direction = 270f.degrees
        spread = 40f.degrees
        speed = 120f..220f
        lifetime = 1.seconds..1.6.seconds
        angularVelocity = (-180f).degrees..180f.degrees
        gravityY = 200f
        startColor = Color(red = 1f, green = 0.7f, blue = 0.2f)
        endColor = Color(red = 1f, green = 0.2f, blue = 0.1f, alpha = 0f)
        startScale = 1.5f
        endScale = 0.5f
        layer = 1
        // Seeded, so two runs with a frame delta draw the same particles.
        random = Random(seed = 1)
    }
    emitter.emit(64)
    val fountain = app.world.create()
    app.world.add(fountain, Transform, Transform(x = 320f, y = 420f))
    app.world.add(fountain, ParticleEmitter, emitter)
}

/**
 * Loads `assets/tiles.tmj`, a 20 by 12 map of 16-point tiles with an embedded tileset, and spawns
 * its two tile layers and the solids of its ground at the right of the window, so a run covers the
 * Tiled loader, flipped tiles and the tile pass. The tile art is drawn with nearest sampling.
 */
private fun addTileMap(app: App) {
    val map = app.assets.tileMap("tiles.tmj", filter = SamplerFilter.NEAREST)
    app.commands.spawnTileLayer(map["ground"], x = 880f, y = 100f)
    app.commands.spawnTileLayer(map["decor"], x = 880f, y = 100f)
    val solids = app.commands.spawnSolids(map["ground"], x = 880f, y = 100f)
    println("$map, ${solids.size} solids.")
}

/**
 * The frame, counted from 1, at which the sandbox loads `star.png` through `Assets.region`, so the
 * atlas page that `tiles.png` went into at startup is written while earlier frames that draw from
 * it may still be on the GPU.
 */
private const val LATE_REGION_FRAME = 30L

/** Loads [path] through `Assets.region`, which packs it into an atlas page, and spawns a [width] by [height] sprite of it at [x], [y]. */
private fun addAtlasSprite(app: App, path: String, x: Float, y: Float, width: Float, height: Float) {
    val region = app.assets.region(path)
    val entity = app.world.create()
    app.world.add(entity, Transform, Transform(x, y))
    app.world.add(entity, Sprite, Sprite(region, width = width, height = height, layer = 2))
    println("Frame ${app.time.frameCount + 1}: $path packed as $region.")
}

/** Prints the mouse and gamepad buttons that went down this frame, gamepad connections and focus changes. */
private fun printInput(app: App) {
    val input = app.input
    for (index in 0 until MouseButton.COUNT) {
        val button = MouseButton(index)
        if (input.mouse.isJustPressed(button)) {
            println("Mouse $button pressed at ${input.mouse.position}, world ${input.mouse.worldPosition}.")
        }
    }
    for (gamepad in input.gamepads) {
        for (index in 0 until GamepadButton.COUNT) {
            val button = GamepadButton(index)
            if (gamepad.isJustPressed(button)) {
                println("$gamepad: $button pressed, left stick ${gamepad.leftStick}, right stick ${gamepad.rightStick}.")
            }
        }
    }
    app.events.forEach<GamepadConnected> { println("Connected: $it, $input.") }
    app.events.forEach<GamepadDisconnected> { println("Disconnected: $it, $input.") }
    app.events.forEach<WindowFocusGained> { println("Focus gained.") }
    app.events.forEach<WindowFocusLost> { println("Focus lost, $input.") }
}

/**
 * Opens a window and prints each [PlatformEvent] the pump returns until the window closes, escape
 * is pressed, [frameLimit] frames of about 16 ms have passed, or [timeLimit] has passed. A frame waits for the first event
 * and then drains the queue.
 */
private fun printPlatformEvents(frameLimit: Int?, timeLimit: Duration?) {
    Sdl.init(setOf(Subsystem.VIDEO, Subsystem.GAMEPAD))
    try {
        Window("klyph events", width = 800, height = 600, flags = setOf(WindowFlag.RESIZABLE, WindowFlag.HIGH_PIXEL_DENSITY)).use { window ->
            EventPump().use { pump ->
                println("$window, $pump: printing events, escape quits.")
                var frames = 0
                val start = TimeSource.Monotonic.markNow()
                while ((frameLimit == null || frames < frameLimit) && (timeLimit == null || start.elapsedNow() < timeLimit)) {
                    var event = pump.waitOrNull(16.milliseconds)
                    while (event != null) {
                        println(event)
                        if (event == PlatformEvent.Quit) return
                        if (event is PlatformEvent.KeyDown && event.key == KeyCode.ESCAPE) return
                        event = pump.pollOrNull()
                    }
                    frames++
                }
                println("Ran $frames frames.")
            }
        }
    } finally {
        Sdl.quit()
    }
}
