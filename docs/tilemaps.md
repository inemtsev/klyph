<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Tilemaps.kt -->
# Tile maps

A tile map is a level painted from small square pictures on a grid. You paint it in the free
[Tiled](https://www.mapeditor.org) map editor, save it as a JSON file, and klyph draws it and turns
its walls and floors into solids that a `KinematicBody` stands on. This page goes from an empty
Tiled window to a level the hero runs through.

The snippets are code for the setup block of `runApp`. The solids and bodies are the ones of
[Platformers](platformers.md).

## Making a map in Tiled

1. **New map.** Choose File > New > New Map. Set Orientation to *Orthogonal*, Tile layer format to
   *CSV*, and Tile render order to *Right Down*. Pick the tile size of your art, such as 16 by 16
   pixels, and a map size in tiles, such as 40 by 20. Leave *Infinite* unticked.
2. **New tileset.** In the Tilesets panel, choose New Tileset. Set Type to *Based on Tileset Image*
   and pick your tile picture, such as `tiles.png`. If the picture has a border around the tiles or
   gaps between them, enter them as Margin and Spacing. Tick *Embed in map* to keep everything in
   one file. A tileset saved on its own works too, as long as you save it as JSON (`.tsj`).
3. **Paint.** Rename the first layer in the Layers panel to `ground` and paint the floor, the walls
   and the platforms. Add a second tile layer called `sky` below it for clouds and hills that the
   hero walks in front of.
4. **Mark the solid tiles.** Click the tileset's edit button, select every tile the hero should
   stand on or bump into, and in the Properties panel add a custom property called `solid` of type
   *bool*. Tick its checkbox. For a ledge the hero jumps up through and lands on, add a bool
   property called `oneWay` instead.
5. **Save as JSON.** Choose File > Save As and pick the JSON map format, which gives a `.tmj` file.
   Put `level.tmj` and `tiles.png` into your game's `assets/` folder, next to each other.

Tile art has sharp pixels, so load it with the nearest filter. Without it the tiles blur, and the
edge of one tile bleeds into its neighbor when the camera moves by part of a pixel. The easy way is
to set it once for the whole game:

<!-- snippet: nearest -->
```kotlin
runApp(AppConfig(title = "My platformer", textureFilter = SamplerFilter.NEAREST)) {
    // Every texture, sprite sheet and tileset image now loads with sharp pixels.
}
```

## Loading and drawing the map

`assets.tileMap` reads the file once and returns the same map every time after. `map["ground"]`
picks a layer by the name you gave it in Tiled. `commands.spawnTileLayer` draws it, and
`commands.spawnSolids` makes solids from the tiles you marked:

<!-- snippet: load -->
```kotlin
val map = assets.tileMap("level.tmj")
// Layers spawned into the same sprite layer draw in the order they are spawned: the sky first.
commands.spawnTileLayer(map["sky"])
commands.spawnTileLayer(map["ground"])
// Walls and floors from the tiles marked solid, ledges from the ones marked oneWay.
commands.spawnSolids(map["ground"])
```

That is a level. A `KinematicBody` with a box collider now lands on the floor, stops at the walls
and jumps up through the one-way ledges. Neighboring solid tiles merge into one solid, so a long
floor is one box and the hero slides along it without catching on the seams between tiles.

Only the cells the camera sees are drawn each frame, so a layer can be much larger than the window.
Each layer draws in one draw call, because all its tiles come from one picture.

If a layer name is wrong, the message lists the layers the map has. If the map uses something klyph
does not read, the message names the file and the layer and says what to change in Tiled:

```
Layer 'ground' of level.tmj is compressed with zlib; export the map with Tile Layer Format CSV,
or Base64 (uncompressed), in Tiled's Map Properties.
```

## Placing a map in the world

A layer's top-left corner sits at the position you pass, and each tile covers its size in world
units, so a 16 pixel tile is 16 units across. Pass the same position to `spawnSolids` so the walls
line up with the picture. Both add the layer's offset from Tiled, so a layer lands where Tiled
shows it. `layer` is the sprite layer the tiles draw in, as for a `Sprite`:

<!-- snippet: place -->
```kotlin
val map = assets.tileMap("level.tmj")
// The second room starts where the first one ends, 40 tiles of 16 units to the right.
commands.spawnTileLayer(map["ground"], x = 640f, y = 0f, layer = 1)
commands.spawnSolids(map["ground"], x = 640f, y = 0f)
```

## Asking which tile is where

`tileAt(x, y)` returns the tile under a point of the layer, or `Tile.EMPTY` outside it. A tile's
`id` is the number Tiled shows for it, counted from 0 at the top-left tile of the tileset picture.
Here the hero goes back to the start when he steps on spikes:

<!-- snippet: query -->
```kotlin
val map = assets.tileMap("level.tmj")
val ground = map["ground"]
// The id Tiled shows for the spike tile in its Tile Properties panel.
val spikes = 7
addSystem(Phase.FIXED_UPDATE) { app ->
    val feet = app.world.get(hero, Transform)
    // The layer was spawned at (0, 0), so world positions are layer positions too.
    val tile = ground.tileAt(feet.x, feet.y + 1f)
    // Back to the start of the level.
    if (tile.id == spikes) feet.x = 32f
}
```

## Changing tiles while the game runs

The map you loaded never changes. `spawnTileLayer` gives its entity a `TileLayer`, a copy of the
layer's cells, and that copy you can change with `tiles[column, row] = tile`. The next frame draws
the change. Here a click removes a brick:

<!-- snippet: change -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    val tiles = app.world.get(level, TileLayer)
    val mouse = app.input.mouse.worldPosition
    // The layer is at (0, 0) at scale 1, so a world position divided by the tile size is a cell.
    val column = (mouse.x / tiles.tileSet.tileWidth).toInt()
    val row = (mouse.y / tiles.tileSet.tileHeight).toInt()
    val clicked = app.input.mouse.isJustPressed(MouseButton.LEFT)
    // Clicked on a brick, tile 3: it is gone from the next frame on.
    if (clicked && tiles.tileAt(mouse.x, mouse.y).id == 3) tiles[column, row] = Tile.EMPTY
}
```

The solids do not follow. `spawnSolids` made them from the cells as they were. To open a hole in
a wall, destroy the solid entities it returned and call it again with the changed layer.

## Colliders of your own

`solidRectangles` returns the merged boxes that `spawnSolids` uses, for any test of a tile you like.
Here every tile of a `water` layer becomes a plain collider on a layer of its own, which the hero's
collider overlaps instead of standing on:

<!-- snippet: own-colliders -->
```kotlin
val water = assets.tileMap("level.tmj")["water"]
val waterLayer = CollisionLayers.of(3)
// Every cell with any tile in it counts, merged into as few boxes as possible.
for (area in water.solidRectangles { !it.isEmpty }) {
    commands.spawn {
        add(Transform, Transform(area.x + area.width / 2, area.y + area.height / 2))
        add(Collider, Collider(Shape.Box(area.width, area.height), layers = waterLayer))
    }
}
```

## A map made in code

A `TileLayer` also works without Tiled. Make a `TileSet` from a sprite sheet, say which tile ids are
solid, and fill the cells yourself:

<!-- snippet: in-code -->
```kotlin
val sheet = SpriteSheet.fromGrid(assets.texture("tiles.png"), columns = 4, rows = 1)
// Tile 0 is a brick, solid; tile 2 a plank you can jump up through.
val tileSet = TileSet(sheet, tileWidth = 16f, tileHeight = 16f, solidIds = setOf(0), oneWayIds = setOf(2))
val floor = TileLayer(tileSet, columns = 30, rows = 4)
for (column in 0 until 30) floor[column, 3] = Tile.of(0)
for (column in 10 until 15) floor[column, 1] = Tile.of(2)
commands.spawn {
    add(Transform, Transform(x = 0f, y = 300f))
    add(TileLayer, floor)
}
commands.spawnSolids(floor, x = 0f, y = 300f)
```

## What klyph reads

klyph reads orthogonal, finite maps with one tileset, embedded in the map or saved as a `.tsj` file.
Tile layers may be stored as CSV or as base64 without compression. It reads the tileset's margin and
spacing, the `solid` and `oneWay` bool properties of its tiles, and every flip and turn Tiled's flip
and rotate buttons make. Each layer keeps its visibility, and its opacity becomes the alpha of the
`TileLayer`'s color.

Tile layers inside groups are read, with the group's offset added. Object layers and image layers
are skipped. Their names are in `map.skippedLayers`, so you can tell that something is missing.
These stop the load with a message that says what to change: compressed layers, layers with a
tint or a parallax factor other than 1, a tileset with a drawing offset, infinite maps, isometric, hexagonal and staggered maps, a map with more than one
tileset, a tileset saved as XML (`.tsx`), a tileset made of separate images, and a tileset image
that cannot be found. The image path in the tileset is relative to the tileset's file, which for an
embedded tileset is the map.

## See also

- [TileMap](../klyph/klyph.tiles/-tile-map/index.html) and
  [tileMap](../klyph/klyph.app/-assets/tile-map.html)
- [TileLayer](../klyph/klyph.tiles/-tile-layer/index.html) and
  [TileGrid](../klyph/klyph.tiles/-tile-grid/index.html)
- [Tile](../klyph/klyph.tiles/-tile/index.html) and
  [TileSet](../klyph/klyph.tiles/-tile-set/index.html)
- [spawnTileLayer](../klyph/klyph.tiles/spawn-tile-layer.html) and
  [spawnSolids](../klyph/klyph.tiles/spawn-solids.html)
- [Solid](../klyph/klyph.collision/-solid/index.html) and
  [KinematicBody](../klyph/klyph.collision/-kinematic-body/index.html)
- Next: [Text, canvas and overlay](text-canvas-and-overlay.md)
