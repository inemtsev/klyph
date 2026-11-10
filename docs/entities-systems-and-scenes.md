<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/EntitiesSystemsAndScenes.kt -->
# Entities, systems and scenes

A klyph game keeps its state in a world of entities and changes it with systems: plain functions
the frame loop calls in fixed phases. Scenes group systems and entities into screens such as a
title, a level and a pause menu. This page covers all three, the order of a frame, the fixed
step, the gameplay components the engine runs for you, resources and events.

## Entities and components

An `Entity` is a handle, an index plus a generation, with no data of its own. Components are
plain Kotlin objects attached to entities. Each component type has a key, a `ComponentType`,
which by convention is the class's companion object:

<!-- snippet: component -->
```kotlin
/** How fast an entity turns, in radians per second. */
class Spin(var rate: Float) {
    companion object : ComponentType<Spin>("Spin")
}
```

The engine's components follow the same pattern: `Transform`, `Velocity`, `Sprite`, `Text`,
`Collider`, `ParticleEmitter`, `SpriteAnimation`, `Lifetime` and `Fade` are both the class and the key, so
you write `world.add(entity, Transform, Transform(x = 10f))`. A marker that carries no data, such
as `Interpolated`, is an object that is its own key and value.

`app.world` holds every entity. It works without an app too, which makes game logic easy to test:

<!-- snippet: world -->
```kotlin
val world = World()
val rock = world.create()
world.add(rock, Transform, Transform(x = 10f, y = 20f))
world.add(rock, Spin, Spin(rate = 2f))

world.get(rock, Spin).rate += 0.5f
println(world.getOrNull(rock, Sprite)) // null: the rock has no sprite
println(world.has(rock, Spin)) // true

world.forEach(Transform, Spin) { entity, transform, spin ->
    transform.rotation += spin.rate / 60f
    println("$entity is turned ${transform.rotation} radians")
}
world.destroy(rock)
println(world.isAlive(rock)) // false
```

`forEach` takes one, two or three component keys and visits every entity that has all of them.
`get` throws `NoSuchElementException` when the component is missing; `getOrNull` returns `null`.
A destroyed entity's index is reused with a new generation, so an old handle never reaches the
new entity: `isAlive` returns `false` for it and `getOrNull` returns `null`.

## Systems and phases

A system is a function of `App`, added to a `Phase` with `addSystem`. The loop runs STARTUP once,
then INPUT, FIXED_UPDATE, UPDATE, LATE_UPDATE and RENDER every frame, then SHUTDOWN once after the
last frame. Systems of a phase run in the order they were added.

<!-- snippet: system -->
```kotlin
addSystem(Phase.FIXED_UPDATE) { app ->
    val dt = app.time.deltaSeconds
    app.world.forEach(Transform, Spin) { _, transform, spin ->
        transform.rotation += spin.rate * dt
    }
}
```

What each phase is for:

| Phase | Runs | Use it for |
|---|---|---|
| `STARTUP` | Once, before the first frame | Loading, spawning the first entities, picking the first scene. |
| `INPUT` | Once per frame, first | Reading `app.input`, pausing, quitting. |
| `FIXED_UPDATE` | Zero or more times per frame, at `AppConfig.fixedRate` | Movement, physics, spawning on timers. |
| `UPDATE` | Once per frame | Reacting to `Overlap` events, game rules, scoring. |
| `LATE_UPDATE` | Once per frame | Work that needs final positions, such as a camera that follows. |
| `RENDER` | Once per frame, last | Drawing on `app.canvas`, the only phase it accepts calls in. |
| `SHUTDOWN` | Once, after the last frame | Saving, printing a summary. |

## The order of a frame

Each frame runs these steps, in this order:

1. Drop the previous frame's events and refresh the audio state.
2. Update `app.input` from the platform's events and publish them as `klyph.event` events.
3. Show or hide the debug overlay if its key went down.
4. INPUT.
5. FIXED_UPDATE, zero or more times, recording each `Interpolated` entity's transform before
   each step, moving every entity with a `Velocity` after the step's systems, and then moving
   every `KinematicBody` against the solids (see [Platformers](platformers.md)).
6. Propagate transforms through the entity hierarchy, then detect collisions and publish
   `Overlap` events.
7. UPDATE.
8. Count down every `Lifetime` and advance every `Fade`.
9. Advance particles.
10. Advance sprite animations and publish `AnimationFinished`.
11. LATE_UPDATE.
12. Propagate transforms again.
13. RENDER, then queue the overlay, clamp the camera to its bounds, draw everything and present.
14. Apply the scene transitions queued during the frame.
15. Quit if `AppConfig.frameLimit` or `timeLimit` has been reached.

Systems of the top scene run after the App-level systems of the same phase.

## Commands

Adding or removing components while `forEach` walks them would change the stores under the loop,
so systems queue structural changes on `app.commands` instead. The engine applies the queue after
every system, so the next system sees the changes, and after each of the engine's own steps that
destroy entities.

<!-- snippet: commands -->
```kotlin
addSystem(Phase.FIXED_UPDATE) { app ->
    app.world.forEach(Transform, Velocity) { entity, transform, _ ->
        // Destroying inside the loop would change the stores it walks, so queue it.
        if (transform.y < -50f) app.commands.destroy(entity)
    }
}
addSystem(Phase.UPDATE) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.SPACE)) {
        app.commands.spawn {
            add(Transform, Transform(x = 640f, y = 600f))
            add(Velocity, Velocity(dy = -400f))
            add(Sprite, Sprite(app.assets.disc(8)))
        }
    }
}
```

`commands.spawn { }` returns the new entity's handle at once, even though its components arrive
when the queue applies. A command for an entity that died in the meantime is skipped without an
error, because destroying something other commands still refer to is normal within a frame.

## Hierarchies

`setParent` in a spawn builder, `commands.setParent(child, parent)` or `world.setParent` makes an
entity a child. A child's `Transform` is then in its parent's space: it moves, turns and scales
with the parent. The engine computes each child's `WorldTransform` twice per frame, after the
fixed steps and after LATE_UPDATE. Destroying a parent destroys its children.

<!-- snippet: hierarchy -->
```kotlin
val ship = commands.spawn {
    add(Transform, Transform(x = 640f, y = 500f))
    add(Sprite, Sprite(assets.texture("ship.png").asRegion()))
}
val gun = commands.spawn {
    setParent(ship)
    // In the ship's space: 40 units ahead of its center, turning and scaling with it.
    add(Transform, Transform(y = -40f))
    add(Sprite, Sprite(assets.disc(10), layer = 1))
}
addSystem(Phase.LATE_UPDATE) { app ->
    app.world.get(ship, Transform).rotation += 1f * app.time.deltaSeconds
    val muzzle = app.world.getOrNull(gun, WorldTransform)
    if (muzzle != null && app.time.frameCount % 60L == 0L) println("Muzzle at ${muzzle.x}, ${muzzle.y}")
}
```

Only entities with a parent have a `WorldTransform`; a root's `Transform` already is in world
space. To find where any entity is in the world, read its `WorldTransform` if it has one and its
`Transform` otherwise.

## The fixed step

FIXED_UPDATE runs at `AppConfig.fixedRate` steps per second of game time, 60 by default, however
fast frames come. At 144 frames a second fewer than half the frames run a step; at 30 each
frame runs two.
Inside a step `time.delta` is the fixed step, so `position += velocity * time.deltaSeconds`
works the same in either phase. After a stall the loop runs at most `maxFixedStepsPerFrame`
steps and drops the rest, so a slow frame does not make the next one slower. A single frame is
also clamped to 250 milliseconds, so a breakpoint shows up as one long frame.

Movement in fixed steps looks jerky when the display's rate is not a multiple of the step rate.
Mark the entity `Interpolated` and the engine draws it between its last two steps by
`time.fixedStepAlpha`. The picture trails the simulation by up to one step. Move marked entities
only in FIXED_UPDATE; a change in another phase is drawn partly blended until the next step.

<!-- snippet: fixed-rate -->
```kotlin
val config = AppConfig(title = "My game", fixedRate = 50, maxFixedStepsPerFrame = 5)
runApp(config) {
    val ball = world.create()
    world.add(ball, Transform, Transform(x = 100f, y = 100f))
    // The engine moves it 120 units a second, in fixed steps.
    world.add(ball, Velocity, Velocity(dx = 120f))
    world.add(ball, Sprite, Sprite(assets.disc(16)))
    // Drawn between its last two fixed steps, so it moves smoothly at any frame rate.
    world.add(ball, Interpolated, Interpolated)
}
```

Interpolation applies to entities without a parent. A child is drawn at its current
`WorldTransform`.

## Scenes

A scene is a named set of systems with optional `onEnter` and `onExit` actions, made with
`buildScene`. `app.scenes` is a stack: only the scene on top runs its systems.

- `scenes.replace(scene)` empties the stack and pushes `scene`: title to game to game over.
- `scenes.push(scene)` puts a scene on top and freezes the one below: a pause menu.
- `scenes.pop()` removes the top scene; the one below runs again without entering again.

Transitions are queued and applied at the end of the frame, so every system of a frame sees the
same scene. Those queued in the setup block or STARTUP apply before the first frame.

<!-- snippet: scenes -->
```kotlin
val pause = buildScene("pause") {
    onEnter { app -> app.audio.music.volume = 0.3f }
    onExit { app -> app.audio.music.volume = 1f }
    addSystem(Phase.INPUT) { app ->
        if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.scenes.pop()
    }
    addSystem(Phase.RENDER) { app ->
        app.canvas.drawText(app.assets.defaultFont, "Paused", x = 640f, y = 340f, scale = 4f, align = TextAlign.CENTER)
    }
}
val play = buildScene("play") {
    // The player belongs to this scene, so it goes away when the scene leaves the stack.
    onEnter { app ->
        app.commands.spawn {
            add(Transform, Transform(x = 640f, y = 600f))
            add(Sprite, Sprite(app.assets.texture("ship.png").asRegion()))
        }
    }
    addSystem(Phase.INPUT) { app ->
        if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.scenes.push(pause)
    }
}
val title = buildScene("title") {
    addSystem(Phase.INPUT) { app ->
        if (app.input.keyboard.isJustPressed(Scancode.SPACE)) app.scenes.replace(play)
    }
    addSystem(Phase.RENDER) { app ->
        app.canvas.drawText(app.assets.defaultFont, "Press Space", x = 640f, y = 340f, scale = 4f, align = TextAlign.CENTER)
    }
}
scenes.replace(title)
```

**Ownership.** A scene owns the entities created while its `onEnter` action or one of its
systems runs, directly or through `commands`. When the scene leaves the stack, its entities are
destroyed with their children. Entities created by the setup block, by App-level systems or by an
`onExit` action belong to no scene and stay, which suits a starfield behind every screen.

**Freezing.** While a scene is pushed over another, the one below is frozen: its systems do not
run, and its velocities, kinematic bodies, lifetimes, fades, particle emitters, sprite
animations and colliders stand still.
Its entities keep rendering, so the pause menu draws over the stopped game without a paused flag.
App-level systems still run and still see frozen entities, so keep game logic in scene systems.

If an `onEnter` action throws, its scene is taken off the stack again and its entities are
destroyed before the exception propagates.

## Velocity

`Velocity(dx, dy)` moves its entity's `Transform` by `dx` and `dy` world units per second. The
engine applies it in every fixed step, after that step's FIXED_UPDATE systems, using the step
length, so it moves exactly as a FIXED_UPDATE system adding `velocity * time.deltaSeconds` would,
and `Interpolated` entities with a velocity are drawn smoothly. A FIXED_UPDATE system that changes
the velocity, to steer or to add gravity, affects the move of the same step. For a child, the
velocity is in its parent's space.

<!-- snippet: velocity -->
```kotlin
val rock = commands.spawn {
    add(Transform, Transform(x = x, y = y))
    add(Sprite, Sprite(assets.disc(12)))
    // Thrown up and to the right; the engine moves it in every fixed step.
    add(Velocity, Velocity(dx = 150f, dy = -300f))
    add(Lifetime, Lifetime(3.seconds))
}
addSystem(Phase.FIXED_UPDATE) { app ->
    // Runs before the engine's move in the same step, so gravity counts at once.
    app.world.getOrNull(rock, Velocity)?.let { it.dy += 600f * app.time.deltaSeconds }
}
```

## Lifetime and Fade

Two components cover the timers most games write by hand. The engine advances both after UPDATE.

- `Lifetime(duration)` destroys its entity once the time has run out. Set `remaining` to give
  it more or less.
- `Fade(duration, target, end, delay)` blends the alpha of the entity's `Sprite` and `Text` from
  where it is to `target`, 0 by default, after `delay`. With `FadeEnd.DESTROY` it destroys the
  entity at the end; with `FadeEnd.HOLD`, the default, it leaves it at the target alpha.

<!-- snippet: lifetime-fade -->
```kotlin
// A spark that lives for half a second.
commands.spawn {
    add(Transform, Transform(x = x, y = y))
    add(Sprite, Sprite(assets.disc(6), color = Color(1f, 0.9f, 0.4f)))
    add(Lifetime, Lifetime(500.milliseconds))
}
// A "+100" that waits a second, fades out over half a second and is then destroyed.
commands.spawn {
    add(Transform, Transform(x = x, y = y - 20f))
    add(Sprite, Sprite(assets.texture("plus100.png").asRegion()))
    add(Fade, Fade(500.milliseconds, end = FadeEnd.DESTROY, delay = 1.seconds))
}
```

Both stand still while their scene is frozen. To fade the same entity again, add a new `Fade`.

## Resources

State that belongs to no entity, such as a score, the settings or the current level, goes in
`app.resources`, which holds one value per class:

<!-- snippet: resource -->
```kotlin
/** The state of one game that no entity owns. */
class Score(var points: Int = 0, var lives: Int = 3)
```

<!-- snippet: resources -->
```kotlin
resources.insert(Score())
addSystem(Phase.UPDATE) { app ->
    val score = app.resources.get<Score>()
    if (score.lives == 0) app.quit()
}
```

`resources.get<T>()` throws `NoSuchElementException` when nothing of class `T` is held;
`getOrNull`, `has` and `remove` cover the other cases. A value is found by its exact class, not
by a supertype. A scene's `onEnter` can insert a fresh value each time it starts, so every game
begins from zero.

## Events

`app.events` carries events from the system that publishes them to every system after it in the
same frame. An event is any object, matched by its exact class, and lives until the next frame
begins. A reader that runs before the publisher in a frame never sees that frame's event, so
register readers later in the same phase or in a later phase.

<!-- snippet: event -->
```kotlin
/** Published when the player scores, for the systems that update the score text and play a sound. */
class Scored(val points: Int)
```

<!-- snippet: events -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.ENTER)) app.events.publish(Scored(points = 100))
}
// Added later in the same phase, so it sees what the system above published this frame.
addSystem(Phase.UPDATE) { app ->
    app.events.forEach<Scored> { scored -> app.resources.get<Score>().points += scored.points }
}
// The engine publishes its own events before the first INPUT system runs.
addSystem(Phase.INPUT) { app ->
    app.events.forEach<KeyPressed> { key -> if (!key.isRepeat) println("Pressed ${key.scancode}") }
    if (app.events.any<WindowFocusLost>()) println("The window went to the background.")
}
```

The engine publishes its own events at the start of INPUT, before any system runs: `KeyPressed`,
`KeyReleased`, `MouseMoved`, `MouseButtonPressed`, `MouseButtonReleased`, `MouseWheelScrolled`,
the gamepad events, `WindowResized`, `WindowDensityChanged`, `WindowFocusGained`,
`WindowFocusLost` and `QuitRequested`. `Overlap` follows the fixed steps and `AnimationFinished`
comes before LATE_UPDATE. Most games read `app.input` instead of key events; events suit things
that happen once, such as a window losing focus.

## See also

- [World](../klyph/klyph.entity/-world/index.html),
  [Commands](../klyph/klyph.entity/-commands/index.html) and
  [ComponentType](../klyph/klyph.entity/-component-type/index.html)
- [Transform](../klyph/klyph.entity/-transform/index.html),
  [Velocity](../klyph/klyph.entity/-velocity/index.html),
  [WorldTransform](../klyph/klyph.entity/-world-transform/index.html) and
  [Interpolated](../klyph/klyph.entity/-interpolated/index.html)
- [Phase](../klyph/klyph.app/-phase/index.html) and [Time](../klyph/klyph.app/-time/index.html)
- [SceneStack](../klyph/klyph.scene/-scene-stack/index.html) and
  [buildScene](../klyph/klyph.scene/build-scene.html)
- [Lifetime](../klyph/klyph.entity/-lifetime/index.html) and
  [Fade](../klyph/klyph.render/-fade/index.html)
- [Resources](../klyph/klyph.app/-resources/index.html) and
  [Events](../klyph/klyph.event/-events/index.html)
- Next: [Input](input.md)
