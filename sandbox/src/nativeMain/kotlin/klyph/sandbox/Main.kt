package klyph.sandbox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.event.GamepadConnected
import klyph.event.GamepadDisconnected
import klyph.event.KeyPressed
import klyph.event.WindowFocusGained
import klyph.event.WindowFocusLost
import klyph.platform.GamepadButton
import klyph.platform.EventPump
import klyph.platform.GpuValidation
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.PlatformEvent
import klyph.platform.Sdl
import klyph.platform.Subsystem
import klyph.platform.Window
import klyph.platform.WindowFlag

/**
 * Sandbox entry point: a smoke test that runs an empty klyph app, which clears the window every
 * frame, until the window closes, escape is pressed, `KLYPH_FRAMES` frames have run or
 * `KLYPH_SECONDS` seconds have passed. It prints a line for every mouse or gamepad button press,
 * gamepad connection and focus change it sees through `app.input` and the engine events, the manual
 * check of the engine's input path.
 *
 * With `KLYPH_EVENTS=1` it skips the engine and runs [printPlatformEvents] instead: a window with
 * no GPU work that prints every platform event, the manual check for keyboard, mouse, gamepad and
 * focus input.
 *
 * Environment: `KLYPH_FRAMES` frames before exit, `KLYPH_SECONDS` seconds before exit,
 * `KLYPH_VALIDATION=1` for the GPU validation layer,
 * `KLYPH_EVENTS=1` for the event printer.
 */
fun main() {
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val timeLimit = Sdl.environmentVariable("KLYPH_SECONDS")?.toDoubleOrNull()?.seconds
    if (Sdl.environmentVariable("KLYPH_EVENTS") == "1") {
        printPlatformEvents(frameLimit, timeLimit)
        return
    }
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED

    runApp(AppConfig(title = "klyph sandbox", validation = validation)) {
        addSystem(Phase.STARTUP) { app ->
            println("${app.assets}, ${app.camera}, validation: $validation")
        }
        addSystem(Phase.INPUT) { app -> printInput(app) }
        addSystem(Phase.UPDATE) { app ->
            app.events.forEach<KeyPressed> { if (it.key == KeyCode.ESCAPE) app.quit() }
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
            if (timeLimit != null && app.time.elapsed >= timeLimit) app.quit()
        }
        addSystem(Phase.SHUTDOWN) { app ->
            println("Ran ${app.time.frame} frames.")
        }
    }
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
