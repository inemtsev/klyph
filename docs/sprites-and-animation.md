<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/SpritesAndAnimation.kt -->
# Sprites and animation

Everything klyph draws from a texture is a quad: an entity's `Sprite`, a glyph of text, a
particle, a rectangle on the canvas. This page covers loading textures, drawing them as sprites,
cutting sheets into frames, playing animations, and the camera that turns world units into
points on screen.

The snippets on this page are code for the setup block of `runApp`, where `assets`, `commands`,
`camera` and `addSystem` are members of the `App` in scope.

## Assets

`app.assets` loads files from the `assets/` folder next to the executable, which the Gradle
plugin fills from your project's `assets/` on every build. `AppConfig(assetRoot = ...)` points it
somewhere else. Every loader caches: the first call for a path reads and uploads the file, later
calls return the same object.

| Call | Returns |
|---|---|
| `assets.texture(path, filter, address)` | A `Texture` from a PNG or JPEG file. |
| `assets.spriteSheet(path, columns, rows, filter)` | The texture cut into a grid of equal cells. |
| `assets.image(path)` | An `Image` you can read and change, not cached and not uploaded. |
| `assets.createTexture(image, filter, address)` | A new texture from an `Image`, every call. |
| `assets.font(path, filter)`, `assets.sound(path)`, `assets.musicTrack(path)` | Fonts, sounds and music, covered on their pages. |

Each loader that reads a file throws `FileException` when the file is missing and `ImageException`
when it is not a valid image. Every one has an `OrNull` sibling, such as `textureOrNull`, that
returns `null` instead, for files that may or may not be there.

## Sprites

A `Sprite` draws a region of a texture as a quad placed on its entity's `Transform` by its origin,
the center by default. Its size starts as the region's size in texels, so at the camera's default zoom each texel covers one
point.

<!-- snippet: sprite -->
```kotlin
val ship = assets.texture("ship.png")
commands.spawn {
    add(Transform, Transform(x = 640f, y = 360f))
    add(Sprite, Sprite(ship.asRegion()))
}
```

The other properties tint, order and mirror it. `color` multiplies every texel, so
`Color.WHITE` shows the texture unchanged. A higher `layer` draws on top of a lower one. The
`Transform` adds rotation in radians, clockwise on screen because y points down, and scale.

<!-- snippet: sprite-options -->
```kotlin
val ship = assets.texture("ship.png").asRegion()
commands.spawn {
    add(Transform, Transform(x = 646f, y = 368f, rotation = 0.3f, scaleX = 0.5f, scaleY = 0.5f))
    add(Sprite, Sprite(ship, color = Color.BLACK.withAlpha(0.4f), layer = -1, isFlippedX = true))
}
```

The origin is the point of the picture that lies on the `Transform`. `originX` and `originY` are
fractions of the sprite's size from its top-left corner, and the default, (0.5, 0.5), is the
center. For a character that stands on the ground, (0.5, 1) is the middle of the bottom edge,
which puts the `Transform` at his feet. Rotation, scale and flips pivot about the origin, so a hero
squashed on landing keeps his feet on the floor. A `Collider` does not follow the origin, as
[Scrolling worlds](scrolling.md) shows.

<!-- snippet: origin -->
```kotlin
val hero = assets.texture("hero.png").asRegion()
commands.spawn {
    // The origin (0.5, 1) is the middle of the bottom edge, so the Transform is at the hero's feet.
    add(Transform, Transform(x = 640f, y = 500f))
    add(Sprite, Sprite(hero, originX = 0.5f, originY = 1f))
}
```

To cover an area with one small texture, give the sprite a `tileWidth` or `tileHeight`, the size in
world units of one repeat. The texture then repeats across the sprite instead of stretching. It
must be loaded with `address = SamplerAddress.REPEAT`; [Scrolling worlds](scrolling.md) has the
rules for tiling.

Within one layer, sprites are grouped by texture, so where two sprites with different textures
overlap in the same layer, which one is on top is not defined. Give them different layers. Each
distinct pair of layer and texture costs one draw call, so use a few layers as draw-order groups,
such as background, world and effects, rather than one per sprite.

`sprite.isVisible = false` hides a sprite without removing it, and a hidden sprite costs no
drawing work.

## Regions and sprite sheets

A `TextureRegion` is a rectangle of a texture in texels, measured from its top-left corner.
`texture.asRegion()` covers the whole texture and `texture.region(x, y, width, height)` a part of
it:

<!-- snippet: region -->
```kotlin
val tiles = assets.texture("tiles.png")
val grass = tiles.region(x = 0, y = 0, width = 32, height = 32)
val wall = tiles.region(x = 32, y = 0, width = 32, height = 32)
commands.spawn {
    add(Transform, Transform(x = 16f, y = 16f))
    add(Sprite, Sprite(grass))
}
commands.spawn {
    add(Transform, Transform(x = 48f, y = 16f))
    add(Sprite, Sprite(wall, layer = 1))
}
```

A `SpriteSheet` is an ordered list of regions on one texture, such as the frames of an animation
or the tiles of a tile set. `assets.spriteSheet` cuts a file into a uniform grid, and
`SpriteSheet.fromGrid` takes margins, spacing and explicit cell sizes. Cells are numbered row by
row from the top-left, so cell `(column, row)` is `sheet[row * columns + column]`.

<!-- snippet: sheet -->
```kotlin
// Six frames of 128 by 128 texels side by side.
val explosion = assets.spriteSheet("explosion.png", columns = 6, rows = 1)
// A tile set with one texel of border and two texels between its cells.
val tiles = SpriteSheet.fromGrid(assets.texture("tiles.png"), columns = 8, rows = 8, margin = 1, spacing = 2)
println("${explosion.size} explosion frames; tile 9 is ${tiles[9]}")
```

## Animation

A `SpriteAnimation` component plays a list of regions on its entity's `Sprite`. Every frame, at
the start of `LATE_UPDATE`, the engine advances it by the frame's time and sets the sprite's
region to the current frame. The entity needs both components.

<!-- snippet: loop -->
```kotlin
commands.spawn {
    add(Transform, Transform(x = 100f, y = 100f))
    add(Sprite, Sprite(coin[0]))
    add(SpriteAnimation, SpriteAnimation(coin, frameDuration = 80.milliseconds))
}
```

The mode decides what happens after the last frame. `AnimationMode.LOOP`, the default, starts
over. `AnimationMode.PING_PONG` plays back and forth: four frames play as 0, 1, 2, 3, 2, 1, 0, 1.
`AnimationMode.ONCE` stops on the last frame.

A `ONCE` animation also has an end. With `AnimationEnd.HOLD`, the default, the entity stays on
its last frame. With `AnimationEnd.DESTROY` the engine destroys the entity, with its children,
once the last frame has been shown for its full duration. That is all an explosion needs:

<!-- snippet: once -->
```kotlin
commands.spawn {
    add(Transform, Transform(x = x, y = y))
    add(Sprite, Sprite(explosion[0]))
    add(SpriteAnimation, SpriteAnimation(explosion, 60.milliseconds, AnimationMode.ONCE, AnimationEnd.DESTROY))
}
```

When a `ONCE` animation finishes, the engine publishes one `AnimationFinished` event for the
entity, whatever its end. `LATE_UPDATE` and `RENDER` systems see it in the same frame. With
`AnimationEnd.DESTROY` the entity is already gone by then, so check `world.isAlive` before you
touch its components.

<!-- snippet: finished -->
```kotlin
var finished = 0
addSystem(Phase.LATE_UPDATE) { app ->
    app.events.forEach<AnimationFinished> { event ->
        finished++
        // With AnimationEnd.DESTROY the entity is already gone.
        println("Explosion $finished done, alive: ${app.world.isAlive(event.entity)}")
    }
}
```

`animation.playhead` is the `Animation` that tracks the frame. Pause it, jump to a frame with
`seek` or start over with `restart`:

<!-- snippet: playhead -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    val walk = app.world.get(player, SpriteAnimation).playhead
    val isMoving = app.input.keyboard.axis(Scancode.A, Scancode.D) != 0f
    walk.isPaused = !isMoving
    if (!isMoving) walk.seek(0)
}
```

`speed` changes how fast an animation plays without changing its `frameDuration`: the engine
advances it by the frame's delta times `speed`. 1 is the normal rate, 2 twice as fast, and 0
holds the current frame like `isPaused`. A negative speed throws `IllegalArgumentException`.
`animation.speed` is the playhead's `Animation.speed`, so either works:

<!-- snippet: speed -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    // The legs keep up with the ground: half rate at a standstill, one and a half at full pace.
    val run = app.world.get(player, SpriteAnimation)
    val pace = abs(app.input.keyboard.axis(Scancode.A, Scancode.D))
    run.speed = 0.5f + pace
}
```

The frames of a `SpriteAnimation` may come from different textures. The animation keeps the list
it is given rather than copying it, so many entities can share one list; do not change the list
afterwards. `SpriteAnimation(sheet, ...)` shares the sheet's own list.

## The camera

`app.camera` is the view sprites are drawn through. It starts at zoom 1 on every display: one
world unit is one point, the unit `window.width` and the mouse position are given in, which is
two pixels across on a high-density display. The renderer applies the display's pixel density,
so a game never multiplies by it. The camera is centered on the middle of the window, so world
(0, 0) is the window's top-left corner.

`camera.x` and `camera.y` are the world point at the center of the view, `zoom` scales the world
(2 shows it twice as large) and `rotation` turns the view. `lookAt(x, y)` moves the camera to a
point. `follow(x, y, smoothing, delta)` moves it part of the way toward a point, so called every
frame it eases after a moving target. The result depends only on the time that passed: at a
smoothing of 5 it covers about 99% of the distance to a still target within a second.

`camera.bounds` keeps the view inside a `Rect` of the world, such as a level's edges. `lookAt`
and `follow` clamp right away, and the engine clamps once more before it draws, so writing `x`,
`y` or `zoom` directly is fine too. Along an axis where the view is larger than the bounds, the
camera centers on them.

<!-- snippet: camera -->
```kotlin
// Show the world twice as large and never past the level's edges.
camera.zoom = 2f
camera.bounds = Rect(x = 0f, y = 0f, width = 4000f, height = 3000f)
camera.lookAt(2000f, 1500f)

addSystem(Phase.LATE_UPDATE) { app ->
    val target = app.world.get(player, Transform)
    app.camera.follow(target.x, target.y, smoothing = 5f, delta = app.time.delta)
}
```

Follow from `LATE_UPDATE`, after the systems of `UPDATE` have moved the target, so the camera
never lags a frame behind it. [Scrolling worlds](scrolling.md) covers following an `Interpolated`
entity and shaking the view.

`camera.worldToScreen` and `camera.screenToWorld` convert between world units and screen points,
with (0, 0) at the window's top-left corner. Screen points are also what `app.canvas` draws in by
default, which makes labels over entities simple:

<!-- snippet: world-to-screen -->
```kotlin
addSystem(Phase.RENDER) { app ->
    val target = app.world.get(player, Transform)
    val above = app.camera.worldToScreen(Vec2(target.x, target.y - 40f))
    app.canvas.drawText(app.assets.defaultFont, "P1", x = above.x, y = above.y, scale = 2f)
}
```

`camera.visibleWidth` and `visibleHeight` give the size of the visible area in world units. When
the window is resized, the engine updates the camera's viewport and leaves its position alone.

## Sampling: linear or nearest

Every texture has a `SamplerFilter`, chosen when it loads, that decides how it looks when drawn
larger or smaller than its texels. `SamplerFilter.LINEAR`, the default, blends neighboring texels
and suits painted art. `SamplerFilter.NEAREST` keeps hard pixel edges and suits pixel art and
bitmap fonts drawn at whole scales.

<!-- snippet: filter -->
```kotlin
val hero = assets.spriteSheet("hero.png", columns = 4, rows = 1, filter = SamplerFilter.NEAREST)
camera.zoom = 4f
commands.spawn {
    add(Transform, Transform(x = 40f, y = 30f))
    add(Sprite, Sprite(hero[0]))
}
```

The cache key is the path, the filter and the address together, so the same file loaded with both
filters becomes two textures. The address, `SamplerAddress.CLAMP` by default, matters only for
tiling, which [Scrolling worlds](scrolling.md) covers.

## Textures without files

Some art is easier to make in code. `Image` is a grid of pixels you can set and fill, and
`assets.createTexture(image)` uploads it. Two regions come ready-made: `assets.whitePixel`, one
white texel to stretch into bars and backgrounds, and `assets.disc(diameter)`, a white disc with
a soft edge for particles, bullets and shadows, cached by diameter. Tint both with the sprite's
color.

<!-- snippet: generated -->
```kotlin
val checker = Image(2, 2).apply {
    set(0, 0, Color.WHITE)
    set(1, 1, Color.WHITE)
}
val board = assets.createTexture(checker, SamplerFilter.NEAREST).asRegion()
commands.spawn {
    add(Transform, Transform(x = 200f, y = 200f))
    add(Sprite, Sprite(board, width = 64f, height = 64f))
}
commands.spawn {
    add(Transform, Transform(x = 400f, y = 200f))
    add(Sprite, Sprite(assets.disc(24), color = Color(1f, 0.8f, 0.2f)))
}
commands.spawn {
    add(Transform, Transform(x = 640f, y = 700f))
    add(Sprite, Sprite(assets.whitePixel, width = 1280f, height = 40f, color = Color(0.2f, 0.6f, 0.3f)))
}
```

## See also

- [Sprite](../klyph/klyph.render/-sprite/index.html),
  [TextureRegion](../klyph/klyph.render/-texture-region/index.html) and
  [SpriteSheet](../klyph/klyph.render/-sprite-sheet/index.html)
- [SpriteAnimation](../klyph/klyph.render/-sprite-animation/index.html) and
  [Animation](../klyph/klyph.render/-animation/index.html)
- [Camera](../klyph/klyph.render/-camera/index.html) and
  [follow](../klyph/klyph.render/follow.html)
- [Assets](../klyph/klyph.app/-assets/index.html)
- [SamplerFilter](../klyph-platform/klyph.platform/-sampler-filter/index.html) and
  [Image](../klyph-platform/klyph.platform/-image/index.html)
- Next: [Entities, systems and scenes](entities-systems-and-scenes.md)
