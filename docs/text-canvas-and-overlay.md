<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/TextCanvasAndOverlay.kt -->
# Text, canvas and overlay

Text in klyph is a bitmap font drawn as one quad per glyph, sorted with the sprites. It reaches
the screen two ways: a `Text` component on an entity, which lives in the world and moves with the
camera, or `app.canvas`, which draws quads, rectangles and text for one frame without entities.
The canvas is also where the debug overlay draws.

The snippets on this page are code for the setup block of `runApp`.

## The built-in font

`assets.defaultFont` is an 8 by 8 pixel font of the printable ASCII characters, built into the
engine, so a game can show text before it has any font file. It is sampled with nearest filtering,
so draw it at whole scales: 2, 3 or 4.

<!-- snippet: built-in -->
```kotlin
addSystem(Phase.RENDER) { app ->
    app.canvas.drawText(app.assets.defaultFont, "Hello, klyph", x = 16f, y = 16f, scale = 3f)
}
```

## BMFont fonts

Other fonts come from BMFont text files, which tools such as AngelCode's BMFont, Hiero and
msdf-bmfont export. A `.fnt` file names one or more page images that sit next to it; the loader
reads them too. Only the text format of BMFont is read; export that rather than the binary one.

<!-- snippet: bmfont -->
```kotlin
// A BMFont text file; its page images sit next to it and load with it.
val title = assets.font("fonts/title.fnt")
// A pixel font drawn at whole scales stays crisp with nearest sampling.
val pixel = assets.font("fonts/pixel.fnt", SamplerFilter.NEAREST)
println("Line heights: ${title.lineHeight} and ${pixel.lineHeight}")
```

`assets.font` throws `FileException` for a missing file and `FontException` for a malformed one;
`fontOrNull` returns `null` for either. Kerning pairs in the file apply. A character the font has
no glyph for is drawn as its `?` glyph if it has one, and skipped otherwise. Line breaks are `\n`.

## Text in the world

A `Text` component draws text at its entity's transform, in world units through the camera. The
entity's position is the text's anchor: with `TextAlign.LEFT` it is the top-left corner of the
block, with `CENTER` each line is centered on it, with `RIGHT` each line ends at it. The entity's
rotation turns the block and its scale multiplies `scale`.

<!-- snippet: text-component -->
```kotlin
commands.spawn {
    add(Transform, Transform(x = 300f, y = 200f))
    add(
        Text,
        Text(
            assets.defaultFont,
            "Shop\nopen",
            color = Color(1f, 0.9f, 0.5f),
            scale = 2f,
            align = TextAlign.CENTER,
            layer = 5,
            shadow = TextShadow(offsetX = 2f, offsetY = 2f),
        ),
    )
}
```

`text` is a `CharSequence` read every frame, so it may be a `StringBuilder` you refill in place.
A score that changes every frame then allocates no new `String`. `layer` is shared with sprites,
so text can sit in front of or behind them.

## Shadows

A `TextShadow` draws a copy of every glyph under the text, moved by `offsetX` and `offsetY` and
tinted by `color`, black at 70% alpha by default. It keeps text readable on a busy background.
Pass it as `Text(shadow = ...)` or `drawText(shadow = ...)`. The shadow's alpha follows the text's,
so a fading text takes its shadow with it. Each shadowed glyph costs a second quad.

## The canvas

`app.canvas` queues sprites, rectangles and text for the current frame only. Call it from RENDER
systems; anywhere else it throws `IllegalStateException`. The engine draws what was queued with the
world's sprites at the end of RENDER and empties it. It suits a HUD, menus, debug shapes and
anything else that changes every frame.

Every call draws in `DrawSpace.SCREEN` by default: points from the window's top-left corner, y
down, unaffected by the camera. A frame draws all `DrawSpace.BACKGROUND` quads first, then all
world-space ones, then all screen-space ones, so a HUD always covers the world and a sky always lies
under it. `BACKGROUND` uses the same points as `SCREEN`. Within a space, quads sort by layer, lowest
first.

<!-- snippet: hud -->
```kotlin
val font = assets.defaultFont
val shadow = TextShadow()
// Refilled every frame, so the changing score allocates no new String.
val line = StringBuilder()
var score = 0
addSystem(Phase.UPDATE) { app -> if (app.input.keyboard.isJustPressed(Scancode.SPACE)) score += 10 }
addSystem(Phase.RENDER) { app ->
    line.clear()
    line.append("Score ").append(score)
    app.canvas.drawRect(x = 8f, y = 8f, width = 220f, height = 40f, color = Color.BLACK.withAlpha(0.5f))
    app.canvas.drawText(font, line, x = 20f, y = 16f, scale = 3f, layer = 1, shadow = shadow)
}
```

The three calls anchor differently, each like the thing it resembles:

| Call | (x, y) is |
|---|---|
| `drawSprite(region, x, y, ...)` | The quad's origin, its center by default, like an entity's `Sprite`. `originX` and `originY` move it, as fractions of the size. |
| `drawRect(x, y, width, height, color, ...)` | The top-left corner, like a `Rect`. |
| `drawText(font, text, x, y, ...)` | The top of the first line, aligned on x by `align`, like a `Text` component. |

`font.measure(text, scale)` returns the size of a block of text, the widest line by the height of
all lines, for centering or for a panel behind it:

<!-- snippet: measure -->
```kotlin
addSystem(Phase.RENDER) { app ->
    val font = app.assets.defaultFont
    val size = font.measure("GAME OVER", scale = 6f)
    val x = (app.window.width - size.x) / 2f
    val y = (app.window.height - size.y) / 2f
    app.canvas.drawRect(x = x - 16f, y = y - 16f, width = size.x + 32f, height = size.y + 32f, color = Color(0.1f, 0f, 0f, 0.8f))
    app.canvas.drawText(font, "GAME OVER", x = x, y = y, scale = 6f, layer = 1)
}
```

<!-- snippet: draw-sprite -->
```kotlin
val heart = assets.disc(20)
addSystem(Phase.RENDER) { app ->
    for (i in 0 until 3) {
        // Centered on (x, y) like an entity's sprite.
        app.canvas.drawSprite(heart, x = app.window.width - 24f - 28f * i, y = 24f, color = Color(1f, 0.2f, 0.3f))
    }
}
```

Many parameters share the type `Float`, so pass them by name. Rectangles and the built-in font
are drawn from the same texture, so a panel and its text in one layer share a draw call.

## Drawing in the world

Pass `space = DrawSpace.WORLD` to draw through the camera instead, in world units, for debug
drawing such as outlines of every collider:

<!-- snippet: world-space -->
```kotlin
val outline = Color(0f, 1f, 0f, 0.35f)
addSystem(Phase.RENDER) { app ->
    app.world.forEach(Transform, Collider) { _, transform, collider ->
        val box = collider.shape as? Shape.Box ?: return@forEach
        // drawRect takes the top-left corner; a box collider is centered on its entity.
        app.canvas.drawRect(
            x = transform.x + collider.offsetX - box.width / 2f,
            y = transform.y + collider.offsetY - box.height / 2f,
            width = box.width,
            height = box.height,
            color = outline,
            layer = 100,
            space = DrawSpace.WORLD,
        )
    }
}
```

## The debug overlay

F3 shows and hides the debug overlay in the window's top-left corner: the frame rate, the frame
time and the time of each phase and engine step, the counts of entities, sprites, draw calls,
audio voices and fixed steps, and a graph of the last 60 frame times with a line at
the fixed step. Every number describes the previous frame. It draws on the canvas in screen space
at the highest layer with the built-in font, so it covers the game's own drawing, and drawing it
allocates nothing once its buffers have grown.

`AppConfig.overlayKey` picks another key, or `null` for none. `AppConfig.overlay =
OverlayStart.VISIBLE` shows it from the first frame, as does `KLYPH_OVERLAY=1`, and
`app.overlay.isVisible` switches it from code:

<!-- snippet: overlay -->
```kotlin
val config = AppConfig(title = "My game", overlayKey = Scancode.F1, overlay = OverlayStart.VISIBLE)
runApp(config) {
    addSystem(Phase.UPDATE) { app ->
        // The same numbers the overlay shows, for the previous frame.
        if (app.stats.total.inWholeMilliseconds > 20) println("Slow frame: ${app.stats.durationOf(Phase.UPDATE)} in UPDATE")
        if (app.input.keyboard.isJustPressed(Scancode.F2)) app.overlay.isVisible = !app.overlay.isVisible
    }
}
```

The numbers come from `app.stats`, a `FrameStats` that systems can read too: `total`,
`durationOf(phase)`, `transformPropagation`, `collisionDetection`, `particleAdvance`,
`spriteExtraction`, `gpuWait`, `spriteCount`, `drawCallCount` and the other counts.
[Performance](performance.md) explains what they measure.

## See also

- [Text](../klyph/klyph.text/-text/index.html),
  [TextAlign](../klyph/klyph.text/-text-align/index.html) and
  [TextShadow](../klyph/klyph.text/-text-shadow/index.html)
- [Font](../klyph/klyph.text/-font/index.html) and
  [measure](../klyph/klyph.text/measure.html)
- [Canvas](../klyph/klyph.render/-canvas/index.html) and
  [DrawSpace](../klyph/klyph.render/-draw-space/index.html)
- [DebugOverlay](../klyph/klyph.app/-debug-overlay/index.html) and
  [FrameStats](../klyph/klyph.app/-frame-stats/index.html)
- Next: [Shipping](shipping.md)
