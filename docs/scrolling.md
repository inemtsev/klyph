<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Scrolling.kt -->
# Scrolling worlds

A level larger than the window needs a camera that follows the hero without jitter and scenery that
moves slower than the world. This page covers both. It also covers the details a scrolling game runs
into: shaking the view, respawns, a sky, repeating floors, sprite origins and a scene clock.

The snippets are code for the setup block of `runApp`. Where one takes a `hero`, it is the
interpolated `KinematicBody` of [Platformers](platformers.md), but nothing here needs a platformer.
The camera itself, `follow` and `bounds` are on [Sprites and animation](sprites-and-animation.md).

## Following the drawn position

`app.drawnPositionOf(entity)` tells the camera where the hero is drawn this frame, so it follows
what the player sees and not the simulation. The two differ for an `Interpolated` entity. The
renderer draws it between its last two fixed steps, so its picture trails its `Transform` by up to
one step.

A camera that follows the `Transform` chases a target that changes once per step, while the picture
moves every frame. On a display faster than the fixed rate, the hero then jitters against the
scenery. For an entity that is not interpolated the drawn position is the `Transform`, so the same
code works for both.

<!-- snippet: follow-drawn -->
```kotlin
addSystem(Phase.LATE_UPDATE) { app ->
    // Where the hero's picture is this frame, which is not where his Transform is.
    val drawn = app.drawnPositionOf(hero)
    // Aim a quarter of a second ahead of the way he runs.
    val lookAhead = app.world.get(hero, KinematicBody).dx * 0.25f
    app.camera.follow(drawn.x + lookAhead, drawn.y, smoothing = 5f, delta = app.time.delta)
}
```

Call `drawnPositionOf` from `LATE_UPDATE`, after the hero has moved. It throws
`NoSuchElementException` when the entity is gone, so a game that can lose its hero checks
`world.isAlive` first. Code that has a `World` and no `App` calls
`world.drawnPositionOf(entity, alpha)`, with `alpha` from 0 to 1 as the blend between the last two
steps.

## Shaking the view

`camera.offsetX` and `offsetY` shift the view without moving the camera, so a shake never fights
`follow` or the bounds. Set them to a new small value every frame while the shake lasts, and back to
0 when it ends. The camera keeps whatever offset it was last given.

<!-- snippet: shake -->
```kotlin
var shake = 0f
addSystem(Phase.FIXED_UPDATE) { app ->
    val body = app.world.get(hero, KinematicBody)
    // A hard landing starts a shake of 10 units.
    if (body.hasLanded && body.landingSpeed > 600f) shake = 10f
}
addSystem(Phase.LATE_UPDATE) { app ->
    // The shake fades out in a third of a second. At 0 both offsets are 0 again.
    shake = maxOf(shake - 30f * app.time.deltaSeconds, 0f)
    app.camera.offsetX = (Random.nextFloat() * 2f - 1f) * shake
    app.camera.offsetY = (Random.nextFloat() * 2f - 1f) * shake
}
```

The snippet starts a shake on a hard landing and lets it fade in a third of a second. `follow` keeps
easing toward the hero meanwhile, and `x` and `y` never hold the shake. Both systems belong to the
app, so a pause scene pushed mid-shake does not freeze the view off-center.

The order of the camera's steps decides what a shake can show. `follow` eases `x` and `y`, the clamp
keeps them inside `bounds`, and the offset is added last. Nothing clamps the offset, so the view can
show past the bounds by the offset. At the left edge of a level, a shake of 10 units can show up to
10 units of whatever lies beyond it. Keep the art going a little past the bounds, or keep the shake
small near the edges.

Bounds smaller than the view along an axis stop the camera scrolling along it. The camera sits in
the middle of the bounds on that axis, so a level no taller than the view scrolls sideways only.

`worldToScreen`, `screenToWorld` and `Mouse.worldPosition` include the offset, so during a shake the
cursor still points at the world point it is over.

## Teleports

`world.snapInterpolation(entity)` draws a respawned or teleported hero at his new place at once,
with no streak across the screen. Without it, an `Interpolated` entity moved by setting its
`Transform` is still blended from its old place. Its picture slides to the new place during the next
step.

Move the `Transform` first and snap after. The call copies the entity's current transform over the
copy the engine saved at the start of the step. Called before the move, it copies the old place, and
the hero still streaks. The entity blends again from the next step on.

<!-- snippet: respawn -->
```kotlin
/** Puts [hero] back at ([x], [y]) at rest, drawn there at once, with the camera on him. */
fun App.respawn(hero: Entity, x: Float, y: Float) {
    val transform = world.get(hero, Transform)
    transform.x = x
    transform.y = y
    // After the move, never before: it copies the new place over the one the engine saved.
    world.snapInterpolation(hero)
    val body = world.get(hero, KinematicBody)
    body.dx = 0f
    body.dy = 0f
    // The camera cuts to him instead of gliding across the level.
    camera.lookAt(x, y)
}
```

The snippet also stops the body and cuts the camera to the new place with `lookAt`. Left alone, the
camera would glide across the level after the hero. Snapping does nothing for an entity that is not
`Interpolated`, so calling it for every teleport is safe.

## A sky under the world

`DrawSpace.BACKGROUND` puts a canvas quad under the whole world, so a sky takes one call a frame and
no entity. It works in points from the window's top-left corner, like `SCREEN`, so the camera has no
effect on it. A frame draws every `BACKGROUND` quad before any world quad, whatever their layers.

<!-- snippet: sky -->
```kotlin
val sky = assets.texture("sky.png").asRegion()
addSystem(Phase.RENDER) { app ->
    // The whole window in points, from its top-left corner, under everything the world draws.
    app.canvas.drawSprite(
        sky,
        x = 0f,
        y = 0f,
        width = app.window.width.toFloat(),
        height = app.window.height.toFloat(),
        space = DrawSpace.BACKGROUND,
        originX = 0f,
        originY = 0f,
    )
}
```

The snippet reads the window's size every frame, so the sky follows a resize. A flat color is one
`drawRect` in the same space, as on [Text, canvas and overlay](text-canvas-and-overlay.md). Entity
sprites are never in this space. Scenery that moves with the camera, only slower than the world, is
a sprite with a `ParallaxLayer`.

## Parallax layers

A `ParallaxLayer` makes a sprite follow the camera at a fraction of the world's speed, so far hills
drift while the ground moves past. Add it to an entity with a `Transform`, a `Sprite` and no parent.
A factor says how much of the camera's movement the sprite shows. The constructor takes `factorX`,
`factorY`, which starts as `factorX`, and then `repeatX` and `repeatY`, which come up below.

| Factor | The layer is |
|---|---|
| 0 | Stuck to the screen, such as a sky or a moon. |
| 0.25 | Far hills. |
| 0.5 | In the middle distance. |
| 1 | Part of the world: it moves as a sprite without the component does. |
| 1.5 | Nearer than the world, such as a fence in front, so it scrolls faster. |

A layer is drawn at its `Transform` plus `1 - factor` times the camera's position, on each axis. The
camera's position is read after the clamp to the bounds, with its offset added, so a layer with a
factor of 0 does not shake with the screen and one with a factor of 1 does. To start a layer at a
chosen place, subtract that shift from the place. At a factor of 0 the `Transform` is the offset
from the center of the screen.

A layer zooms with the camera like any sprite, which a `BACKGROUND` quad does not. It sorts with the
other sprites by `Sprite.layer`, so give the backdrop negative layers to keep the world in front.
Among sprites of one layer and texture, the parallax layers draw first.

`repeatX` and `repeatY` are the distance in world units from one copy to the next, or 0 for no
repeat. Set a repeat to the drawn size, the sprite's `width` times the entity's scale, for a strip
whose copies meet edge to edge. The engine draws every copy that touches the view at any zoom, so
the strip never ends. An axis with no repeat is drawn once, wherever it is.

<!-- snippet: hills -->
```kotlin
val hills = Sprite(assets.texture("hills.png").asRegion(), layer = -2, originY = 1f)
commands.spawn {
    // The bottom edge of the strip stands on the horizon, at y = 400.
    add(Transform, Transform(x = 0f, y = 400f))
    add(Sprite, hills)
    // Half the world's speed along x, the world's own height along y, a copy every strip width.
    add(ParallaxLayer, ParallaxLayer(0.5f, factorY = 1f, repeatX = hills.width))
}
```

Along a repeating axis the `Transform` only decides where the copies start. At most
`ParallaxLayer.MAX_COPIES`, 256, copies of one sprite are drawn, so a period far smaller than the
view leaves the far copies out. A small texture across a large area is a job for `tileWidth`, in the
next section. Drawing a layer throws `IllegalStateException`, naming the entity, if it has a parent
or a negative or infinite factor or repeat.

## Repeating textures

A tiled sprite repeats one small texture across a large area, such as a floor or a wall, instead of
stretching it. It needs two things: a texture loaded with `SamplerAddress.REPEAT` and a sprite with
a `tileWidth` or `tileHeight`.

The address is chosen when the texture loads, by the `address` argument of `assets.texture` or
`assets.createTexture`. A texture has one address, so a file loaded both clamped and repeating is
two textures. Load `REPEAT` only for what you tile. With linear filtering the texels at one edge
blend with those at the opposite edge, and a texture drawn once would get a faint border.

`tileWidth` and `tileHeight` are the size of one repeat in world units before the entity's scale, as
`width` and `height` are. The sprite shows `width / tileWidth` repeats across and
`height / tileHeight` down, and a count that is not whole cuts the last repeat short. The repeats
start at the sprite's top-left corner. A tile size of 0, the default, stretches the texture along
that axis.

<!-- snippet: tiled-floor -->
```kotlin
// 16 by 16 pixel-art bricks, sampled sharply and wrapping around at the edges of the texture.
val bricks = assets.texture("bricks.png", filter = SamplerFilter.NEAREST, address = SamplerAddress.REPEAT)
commands.spawn {
    // A floor of 900 by 600 units with one repeat of the texture every 32 units, twice its size.
    add(Transform, Transform(x = 450f, y = 700f))
    add(Sprite, Sprite(bricks.asRegion(), width = 900f, height = 600f, tileWidth = 32f, tileHeight = 32f))
}
```

Only a region that covers the whole texture can tile, as `texture.asRegion()` does. The sampler
wraps at the edges of the whole texture, so a cut from an atlas would show its neighbors. A sprite
with a tile size and any other region throws `IllegalStateException`, naming the entity, when it is
drawn.

`Canvas.drawTiled(region, x, y, width, height, tileWidth, tileHeight, ...)` repeats a texture for
one frame, with the same rules. Its tile size defaults to the region's size in texels and must be
positive. To stretch along an axis, pass the quad's own size. It throws `IllegalArgumentException`
for a texture or region that cannot tile.

## Sprite origin and the collider

A sprite's origin puts any point of the picture on the entity's `Transform`, such as a character's
feet instead of his center. `originX` and `originY` are fractions of the sprite's size from its
top-left corner. The default, (0.5, 0.5), is the center. (0, 0) is the top-left corner, and (0.5, 1)
is the middle of the bottom edge, because y points down.

Rotation, scale and flips pivot about the origin, so the point on it stays where the `Transform`
puts it. A hero squashed on landing keeps his feet on the floor, and a flip turns him around on the
spot. A value outside 0 to 1 puts the pivot outside the picture. `Canvas.drawSprite` and `drawTiled`
take the same `originX` and `originY`.

A `Collider` does not follow the origin. It stays centered on the `Transform` plus its own offset. A
hero with his feet on the `Transform` lifts his box with a negative `offsetY`. For a box as tall as
the sprite, that is half its height.

<!-- snippet: feet-origin -->
```kotlin
val art = assets.texture("hero.png").asRegion()
// The origin (0.5, 1) is the middle of the bottom edge, so the Transform is at the hero's feet.
val sprite = Sprite(art, originX = 0.5f, originY = 1f)
val hero = commands.spawn {
    add(Transform, Transform(x = 100f, y = 400f - Collisions.CONTACT_GAP))
    add(Sprite, sprite)
    // The collider stays centered on the Transform plus its offset, so lift it by half its height.
    add(Collider, Collider(Shape.Box(sprite.width, sprite.height), offsetY = -sprite.height / 2f))
    add(KinematicBody, KinematicBody())
    add(Interpolated, Interpolated)
}
```

Without the lift, the box would reach half its height below his feet, and he would hover above the
floor by that much.

## The scene clock

A scene's clock, `Scene.elapsed`, counts only the time the scene is on top. A timer or a bob driven
by it pauses with the scene. `elapsedSeconds` is the same value as a `Float`, for formulas. The
clock starts at zero each time the scene enters the stack, by `push` or `replace`. It stands still
while another scene is pushed on top and goes on from the same value when that scene is popped.
After the scene leaves, it keeps its last value.

`Time.elapsed` runs whatever scene is on top. A bob computed from it jumps to a new point of its
cycle when a pause ends, because that clock never stopped. A bob computed from the scene's clock
goes on from where it was.

<!-- snippet: scene-clock -->
```kotlin
lateinit var play: Scene
play = buildScene("play") {
    addSystem(Phase.UPDATE) { app ->
        // The clock stops under a pause scene, so the bob goes on from where it stopped.
        app.world.get(lantern, Transform).y = 300f + sin(play.elapsedSeconds * 3f) * 6f
        // The round lasts a minute of play, not a minute of pause.
        if (play.elapsed >= 60.seconds) app.scenes.replace(gameOver)
    }
}
```

The engine adds the frame's time to the clock once per frame, before INPUT. Every phase of the frame
reads the same value, `FIXED_UPDATE` included, so two steps of one frame see the same time.

## See also

- [Camera](../klyph/klyph.render/-camera/index.html) and
  [follow](../klyph/klyph.render/follow.html)
- [drawnPositionOf](../klyph/klyph.app/drawn-position-of.html) and
  [snapInterpolation](../klyph/klyph.entity/snap-interpolation.html)
- [DrawSpace](../klyph/klyph.render/-draw-space/index.html) and
  [Canvas](../klyph/klyph.render/-canvas/index.html)
- [ParallaxLayer](../klyph/klyph.render/-parallax-layer/index.html)
- [Sprite](../klyph/klyph.render/-sprite/index.html) and
  [SamplerAddress](../klyph-platform/klyph.platform/-sampler-address/index.html)
- [Scene](../klyph/klyph.scene/-scene/index.html) and
  [elapsedSeconds](../klyph/klyph.scene/elapsed-seconds.html)
- Next: [Text, canvas and overlay](text-canvas-and-overlay.md)
