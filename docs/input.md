<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Input.kt -->
# Input

`app.input` holds the state of the keyboard, the mouse and every connected gamepad as of this
frame, so a system asks "is this key down?" instead of following key events. The engine updates
it at the start of each frame's INPUT phase, before any system runs, and it stays the same for
the rest of the frame.

Every device answers three questions per button:

| Call | `true` when |
|---|---|
| `isDown(button)` | The button is held now. |
| `isJustPressed(button)` | The button went down this frame, or since the last step in FIXED_UPDATE. |
| `isJustReleased(button)` | The button went up this frame, or since the last step in FIXED_UPDATE. |

"Just pressed" and "just released" last one frame, or one step in FIXED_UPDATE. A tap shorter
than a frame reads as both in the same frame, so no press is lost. Key repeat does not count as a
press. When the window loses focus every held key and button is released, and reads as just
released that frame, so nothing stays stuck while the game is in the background.

Held state reads the same in every phase. Edges, the "just" calls, follow the phase that reads
them. In INPUT, UPDATE and the other phases that run once a frame, "just" means this frame.
FIXED_UPDATE runs zero, one or several steps a frame, so there it means since the previous step:
the first step after a press sees it and no other step does. A press in a frame that runs no step
waits for the next frame's first step, and a frame's second step does not see it again. A tap
shorter than a step reads as both just pressed and just released in that step, with the key
already up. Read a jump where the physics runs:

<!-- snippet: jump -->
```kotlin
var velocityY = 0f
addSystem(Phase.FIXED_UPDATE) { app ->
    val transform = app.world.get(player, Transform)
    val ground = 500f
    // One step sees each press, however many steps this frame runs.
    val jump = app.input.keyboard.isJustPressed(Scancode.SPACE) || app.input.gamepads.isJustPressed(GamepadButton.SOUTH)
    if (jump && transform.y >= ground) velocityY = -600f
    velocityY += 1800f * app.time.deltaSeconds
    transform.y += velocityY * app.time.deltaSeconds
    if (transform.y >= ground) {
        transform.y = ground
        velocityY = 0f
    }
}
```

Each press reaches one step; a jump buffer or coyote time, which remember it for a few steps, is
game logic, as in Rooftop Roller. A press that a menu reads in INPUT to start the game can also
reach the first step of the scene it starts, when its frame ran no step. The snippets on this page
are code for the setup block of `runApp`.

## Keyboard

A key has two names. A `Scancode` is the key's position, named after the key in that place on a
US keyboard. A `KeyCode` is the label the user's layout gives it. Use scancodes for controls that
depend on where keys sit, such as movement on W, A, S and D, so they work on every layout. Use key
codes when the letter matters, such as M for the map: on a French layout `KeyCode.A` is the key
where Q sits on a US keyboard.

<!-- snippet: keyboard -->
```kotlin
addSystem(Phase.INPUT) { app ->
    val keyboard = app.input.keyboard
    // By position: the key where W sits on a US keyboard, whatever the layout prints on it.
    if (keyboard.isDown(Scancode.W)) println("Forward")
    // By label: the key that types "m" on the user's layout.
    if (keyboard.isJustPressed(KeyCode.M)) println("Map")
    if (keyboard.isJustReleased(Scancode.SPACE)) println("Jump released")
}
```

The `KeyCode` calls look the key up through the current layout on every call, so a layout switch
takes effect at once. A key code that no key on the layout produces is never down.

`keyboard.axis(negative, positive)` turns a pair of keys, two scancodes or two key codes, into a
direction: -1 while only the negative key is down, 1 while only the positive one is, and 0 while
both or neither are.

<!-- snippet: axis -->
```kotlin
addSystem(Phase.FIXED_UPDATE) { app ->
    val keyboard = app.input.keyboard
    val dx = keyboard.axis(Scancode.A, Scancode.D) + keyboard.axis(Scancode.LEFT, Scancode.RIGHT)
    val transform = app.world.get(player, Transform)
    transform.x += dx.coerceIn(-1f, 1f) * 300f * app.time.deltaSeconds
    // By label: the keys that type "q" and "e" turn the ship, wherever the layout puts them.
    transform.rotation += keyboard.axis(KeyCode.Q, KeyCode.E) * 3f * app.time.deltaSeconds
}
```

For text entry or a key binding screen, read `KeyPressed` events from `app.events` instead: each
carries the `scancode`, the `key` code and `isRepeat`, and they arrive in order.

## Mouse

`mouse.position` is the cursor in window points, with (0, 0) at the window's top-left corner and
y pointing down, the same unit as `window.width` and the canvas's screen space. It is (0, 0)
until the mouse first moves over the window. `mouse.delta` is how far it moved this frame and
`mouse.wheel` how far the wheel or touchpad scrolled: positive y scrolls up, one notch of a wheel
is usually 1, and touchpads report fractions.

`mouse.worldPosition` is the world point under the cursor, seen through `app.camera`. Aim with
it:

<!-- snippet: mouse -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    val mouse = app.input.mouse
    val ship = app.world.get(player, Transform)
    // The world point under the cursor, through the camera as it stood at the start of INPUT.
    val target = mouse.worldPosition
    ship.rotation = Vec2(target.x - ship.x, target.y - ship.y).angle
    if (mouse.isJustPressed(MouseButton.LEFT)) println("Fire at $target")
    if (mouse.wheel.y != 0f) app.camera.zoom = (app.camera.zoom * (1f + 0.1f * mouse.wheel.y)).coerceIn(0.5f, 4f)
}
```

The engine computes `worldPosition` at the start of INPUT, through the camera as it stood then.
A camera that follows the player in LATE_UPDATE moves after that, so the value is one frame
behind the picture. When it matters, as for a cursor drawn in the world, convert the position
yourself after the camera has moved:

<!-- snippet: mouse-now -->
```kotlin
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
```

Buttons are `MouseButton.LEFT`, `MIDDLE`, `RIGHT`, `X1` and `X2`. `MouseButtonPressed` events
also carry the click count, for double clicks. To hide the system cursor or replace it with an
image, see [Shipping](shipping.md).

## Gamepads

`input.gamepads` lists the connected pads in the order they were connected. It is a live view,
so a reference to it stays current as pads come and go. Buttons and axes follow SDL's standard
layout and are named by position, so `GamepadButton.SOUTH` is the bottom face button on every pad:
A on an Xbox pad, cross on a PlayStation pad.

| Read | Range |
|---|---|
| `pad.leftStick`, `pad.rightStick` | x from -1 (left) to 1 (right), y from -1 (up) to 1 (down), dead zone applied. |
| `pad.axis(GamepadAxis.LEFT_TRIGGER)`, `RIGHT_TRIGGER` | 0 released to 1 fully pressed. |
| `pad.axis(GamepadAxis.LEFT_X)` and the other stick axes | -1 to 1, raw, no dead zone. |
| `pad.isDown(GamepadButton.SOUTH)` and the other buttons | As for keys. |

<!-- snippet: gamepad -->
```kotlin
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
```

Sticks rarely rest at exactly zero, so `leftStick` and `rightStick` apply a radial dead zone,
`input.deadZone`, 0.15 by default. Below it a stick reads zero; past it the distance is rescaled
to grow from 0 at the edge of the dead zone to 1 at full tilt, so there is no jump.

A game that lets whichever pad is at hand play can ask the whole list. The `List<Gamepad>`
extensions `isDown`, `isJustPressed` and `isJustReleased` are `true` when any connected pad
matches, and `false` for an empty list:

<!-- snippet: gamepad-list -->
```kotlin
addSystem(Phase.INPUT) { app ->
    val pads = app.input.gamepads
    // True when any connected pad matches, so whichever pad is at hand can play.
    if (pads.isJustPressed(GamepadButton.START)) println("Pause")
    if (pads.isDown(GamepadButton.SOUTH)) println("Jump held")
}
```

A `Gamepad` object lives for one connection. When the pad is unplugged it leaves the list, its
`isConnected` turns `false` and it reads as released, but it stays safe to read. Plugged back in,
the pad gets a new `GamepadId` and a new object. `input.gamepadOrNull(id)` finds a connected pad
by id, and `GamepadConnected` and `GamepadDisconnected` events report the changes:

<!-- snippet: gamepad-events -->
```kotlin
input.deadZone = 0.2f
addSystem(Phase.INPUT) { app ->
    app.events.forEach<GamepadConnected> { event -> println("Connected ${event.name} as ${event.id}") }
    app.events.forEach<GamepadDisconnected> { event ->
        println("Pad ${event.id} left; it is ${app.input.gamepadOrNull(event.id)}")
    }
}
```

## Combining devices

The template adds the keyboard and the stick together and clamps the result to length 1, so two
keys at once, or a key and the stick, are no faster than one. See the movement system in
[Getting started](getting-started.md). The shooter in `games/shooter` goes further: it aims with
the mouse until the right stick moves, and back once the mouse moves.

## See also

- [Input](../klyph/klyph.input/-input/index.html),
  [Keyboard](../klyph/klyph.input/-keyboard/index.html) and
  [Keyboard.axis](../klyph/klyph.input/axis.html)
- [Mouse](../klyph/klyph.input/-mouse/index.html) and
  [Camera.screenToWorld](../klyph/klyph.render/screen-to-world.html)
- [Gamepad](../klyph/klyph.input/-gamepad/index.html) and the
  [list queries](../klyph/klyph.input/is-just-pressed.html)
- [Scancode](../klyph-platform/klyph.platform/-scancode/index.html),
  [KeyCode](../klyph-platform/klyph.platform/-key-code/index.html) and
  [GamepadButton](../klyph-platform/klyph.platform/-gamepad-button/index.html)
- [Input events](../klyph/klyph.event/index.html)
- Next: [Audio](audio.md)
