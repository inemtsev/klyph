// Snippets of docs/input.md. They compile with the samples but never run, because input needs a
// window. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.event.GamepadConnected
import klyph.event.GamepadDisconnected
import klyph.input.axis
import klyph.input.isDown
import klyph.input.isJustPressed
import klyph.math.Vec2
import klyph.math.angle
import klyph.math.length
import klyph.platform.Color
import klyph.platform.GamepadAxis
import klyph.platform.GamepadButton
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.Scancode
import klyph.render.DrawSpace
import klyph.render.screenToWorld

class InputGuide {
    fun App.readKeys() {
        // snippet: keyboard
        addSystem(Phase.INPUT) { app ->
            val keyboard = app.input.keyboard
            // By position: the key where W sits on a US keyboard, whatever the layout prints on it.
            if (keyboard.isDown(Scancode.W)) println("Forward")
            // By label: the key that types "m" on the user's layout.
            if (keyboard.isJustPressed(KeyCode.M)) println("Map")
            if (keyboard.isJustReleased(Scancode.SPACE)) println("Jump released")
        }
        // end snippet
    }

    fun App.steer(player: Entity) {
        // snippet: axis
        addSystem(Phase.FIXED_UPDATE) { app ->
            val keyboard = app.input.keyboard
            val dx = keyboard.axis(Scancode.A, Scancode.D) + keyboard.axis(Scancode.LEFT, Scancode.RIGHT)
            val transform = app.world.get(player, Transform)
            transform.x += dx.coerceIn(-1f, 1f) * 300f * app.time.deltaSeconds
            // By label: the keys that type "q" and "e" turn the ship, wherever the layout puts them.
            transform.rotation += keyboard.axis(KeyCode.Q, KeyCode.E) * 3f * app.time.deltaSeconds
        }
        // end snippet
    }

    fun App.aim(player: Entity) {
        // snippet: mouse
        addSystem(Phase.UPDATE) { app ->
            val mouse = app.input.mouse
            val ship = app.world.get(player, Transform)
            // The world point under the cursor, through the camera as it stood at the start of INPUT.
            val target = mouse.worldPosition
            ship.rotation = Vec2(target.x - ship.x, target.y - ship.y).angle
            if (mouse.isJustPressed(MouseButton.LEFT)) println("Fire at $target")
            if (mouse.wheel.y != 0f) app.camera.zoom = (app.camera.zoom * (1f + 0.1f * mouse.wheel.y)).coerceIn(0.5f, 4f)
        }
        // end snippet
    }

    fun App.pickAfterCameraMoved() {
        // snippet: mouse-now
        addSystem(Phase.RENDER) { app ->
            // After a LATE_UPDATE camera move, worldPosition is a frame behind; this is not.
            val underCursor = app.camera.screenToWorld(app.input.mouse.position)
            app.canvas.drawRect(
                x = underCursor.x - 4f,
                y = underCursor.y - 4f,
                width = 8f,
                height = 8f,
                color = Color.WHITE,
                space = DrawSpace.WORLD,
            )
        }
        // end snippet
    }

    fun App.readGamepads(player: Entity) {
        // snippet: gamepad
        addSystem(Phase.FIXED_UPDATE) { app ->
            val pad = app.input.gamepads.firstOrNull() ?: return@addSystem
            val stick = pad.leftStick
            val transform = app.world.get(player, Transform)
            transform.x += stick.x * 300f * app.time.deltaSeconds
            transform.y += stick.y * 300f * app.time.deltaSeconds
            // Triggers read from 0, released, to 1, fully pressed.
            if (pad.axis(GamepadAxis.RIGHT_TRIGGER) > 0.5f) println("Boost")
            if (pad.rightStick.length > 0.5f) transform.rotation = pad.rightStick.angle
        }
        // end snippet
    }

    fun App.anyPad() {
        // snippet: gamepad-list
        addSystem(Phase.INPUT) { app ->
            val pads = app.input.gamepads
            // True when any connected pad matches, so whichever pad is at hand can play.
            if (pads.isJustPressed(GamepadButton.START)) println("Pause")
            if (pads.isDown(GamepadButton.SOUTH)) println("Jump held")
        }
        // end snippet
    }

    fun App.trackPads() {
        // snippet: gamepad-events
        input.deadZone = 0.2f
        addSystem(Phase.INPUT) { app ->
            app.events.forEach<GamepadConnected> { event -> println("Connected ${event.name} as ${event.id}") }
            app.events.forEach<GamepadDisconnected> { event ->
                println("Pad ${event.id} left; it is ${app.input.gamepadOrNull(event.id)}")
            }
        }
        // end snippet
    }
}
