package klyph.roller

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Lifetime
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.math.degrees
import klyph.particle.ParticleEmitter
import klyph.particle.spawnBurst
import klyph.platform.Color
import klyph.render.Fade
import klyph.render.FadeEnd
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.aspectRatio
import klyph.text.Font
import klyph.text.Text
import klyph.text.TextAlign
import klyph.text.TextShadow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

// Factories for every kind of entity in the game. They queue the entity on App.commands, so they
// are safe to call inside a World.forEach. The level's buildings, ledges and decoration are
// spawned once at startup and belong to no scene, so the title screen shows them too; the hero,
// enemies, pickups and effects are spawned by the play scene and its systems, so they belong to
// it and vanish when it leaves the stack.

/**
 * Builds the level's scenery: a solid for every building and ledge, the tiled sprites that show
 * them, the billboards on their pylons, the potted plants, the air conditioners and the arrow sign
 * at the end.
 */
fun Commands.spawnScenery(art: Art) {
    val level = RooftopRun
    for (block in level.blocks) spawnSolid(block)
    for (index in level.buildings.indices) spawnBuilding(art, level.buildings[index], art.bricks[index % art.bricks.size])
    for (ledge in level.ledges) spawnLedge(art, ledge)
    for (billboard in level.billboards) spawnBillboard(art, billboard)
    for (plant in level.plants) {
        val height = PLANT_HEIGHT
        val width = height * art.plant.aspectRatio
        spawnProp(art.plant, plant.x, plant.y, width, height)
    }
    for (unit in level.airConditioners) {
        val region = art.airConditioner
        spawnProp(region, unit.x, unit.y, region.width * Tuning.PIXEL, region.height * Tuning.PIXEL)
    }
    val signHeight = SIGN_HEIGHT
    spawnProp(art.sign, RooftopRun.SIGN_X, RooftopRun.signRoof, signHeight * art.sign.aspectRatio, signHeight)
}

private const val PLANT_HEIGHT = 44f
private const val SIGN_HEIGHT = 92f

/**
 * Spawns the invisible solid of [block], which the hero's body stops at: a building from its roof
 * down past the pits' bottom, so its sides are walls, or a ledge as thick as its girder that he
 * jumps up through and lands on.
 */
private fun Commands.spawnSolid(block: Block) {
    val bottom = if (block.isLedge) block.top + Tuning.LEDGE_HEIGHT else Tuning.WORLD_HEIGHT + 2 * Tuning.PIT_MARGIN
    spawn {
        add(Transform, Transform((block.left + block.right) / 2, (block.top + bottom) / 2))
        add(Collider, Collider(Shape.Box(block.right - block.left, bottom - block.top), layers = Layers.LEVEL, mask = CollisionLayers.NONE))
        add(Solid, Solid(if (block.isLedge) Passable.FROM_BELOW else Passable.NEVER))
        add(Block, block)
    }
}

/**
 * Spawns a static sprite of [width] by [height] whose top-left corner is at ([left], [top]),
 * showing [region] stretched, or repeated every [tileWidth] by [tileHeight] units when given, and tinted by [color].
 */
private fun Commands.spawnBlock(
    region: TextureRegion,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    layer: Int,
    color: Color = Color.WHITE,
    tileWidth: Float = 0f,
    tileHeight: Float = 0f,
) {
    spawn {
        add(Transform, Transform(left, top))
        add(
            Sprite,
            Sprite(
                region, width, height, color = color, layer = layer,
                tileWidth = tileWidth, tileHeight = tileHeight, originX = 0f, originY = 0f,
            ),
        )
    }
}

/**
 * Spawns a static sprite of [width] by [height] whose top-left corner is at ([left], [top]) and
 * which repeats [region], a texture made to repeat, at its own size in texels times [Tuning.PIXEL],
 * starting from that corner. A repeat that does not fit whole is cut short at the right and the bottom.
 */
private fun Commands.spawnTiled(region: TextureRegion, left: Float, top: Float, width: Float, height: Float, layer: Int) =
    spawnBlock(region, left, top, width, height, layer, tileWidth = region.width * Tuning.PIXEL, tileHeight = region.height * Tuning.PIXEL)

/**
 * Spawns the picture of [building] from its roof down past the bottom of the world: a face of
 * [bricks], a dark column at each side, the stone cap along the roof and rows of windows, a second
 * row when it is tall.
 */
private fun Commands.spawnBuilding(art: Art, building: Block, bricks: TextureRegion) {
    val pixel = Tuning.PIXEL
    val width = building.right - building.left
    // A little past the bottom of the world, so a falling camera never sees the end.
    val height = Tuning.WORLD_HEIGHT + FACE_OVERHANG - building.top
    val capHeight = art.cap.height * pixel
    spawnTiled(bricks, building.left, building.top, width, height, Tuning.LAYER_BUILDINGS)
    // The walls' edges, so neighbouring buildings and the sky do not run into the bricks.
    for (x in listOf(building.left, building.right - pixel)) {
        spawnBlock(art.whitePixel, x, building.top + capHeight, pixel, height - capHeight, Tuning.LAYER_BUILDING_DETAILS, WALL_EDGE)
    }
    spawnTiled(art.cap, building.left, building.top, width, capHeight, Tuning.LAYER_BUILDING_DETAILS)
    spawnWindowRow(art, building, WINDOWS_FIRST_ROW_TEXELS * pixel)
    if (height > TALL_BUILDING_TEXELS * pixel) spawnWindowRow(art, building, WINDOWS_SECOND_ROW_TEXELS * pixel)
}

/** How far a building's face reaches past the bottom of the world. */
private const val FACE_OVERHANG = 20f

/** A building taller than this many texels has a second row of windows. */
private const val TALL_BUILDING_TEXELS = 80

/** The rows of windows start this many texels below the roof, and keep this many texels clear of the walls' edges. */
private const val WINDOWS_FIRST_ROW_TEXELS = 13
private const val WINDOWS_SECOND_ROW_TEXELS = 45
private const val WINDOWS_MARGIN_TEXELS = 8

/**
 * Spawns a row of as many windows as fit across [building], with their tops [top] units below its
 * roof, centered on it; a face too narrow for one gets none.
 */
private fun Commands.spawnWindowRow(art: Art, building: Block, top: Float) {
    val pixel = Tuning.PIXEL
    val texelsWide = ceil((building.right - building.left) / pixel).toInt()
    val count = max(0, (texelsWide - 2 * WINDOWS_MARGIN_TEXELS + WINDOW_GAP_TEXELS) / (WINDOW_TEXELS + WINDOW_GAP_TEXELS))
    if (count == 0) return
    val used = count * WINDOW_TEXELS + (count - 1) * WINDOW_GAP_TEXELS
    // The tile starts a texel left of its window, for the sill that sticks out.
    val left = building.left + ((texelsWide - used) / 2 - 1) * pixel
    val rowWidth = count * art.windows.width * pixel
    spawnTiled(art.windows, left, building.top + top, rowWidth, art.windows.height * pixel, Tuning.LAYER_BUILDING_DETAILS)
}

/** Spawns the picture of [ledge]: a steel girder with a dark plate at each end. */
private fun Commands.spawnLedge(art: Art, ledge: Block) {
    val pixel = Tuning.PIXEL
    spawnTiled(art.girder, ledge.left, ledge.top, ledge.right - ledge.left, Tuning.LEDGE_HEIGHT, Tuning.LAYER_BUILDINGS)
    for (x in listOf(ledge.left, ledge.right - pixel)) {
        spawnBlock(art.whitePixel, x, ledge.top, pixel, Tuning.LEDGE_HEIGHT, Tuning.LAYER_BUILDING_DETAILS, STEEL_OUTLINE)
    }
}

/** Spawns a decoration standing with its bottom on [floor], centered on [x]. */
private fun Commands.spawnProp(region: TextureRegion, x: Float, floor: Float, width: Float, height: Float) {
    spawn {
        add(Transform, Transform(x, floor))
        add(Sprite, Sprite(region, width, height, layer = Tuning.LAYER_DECOR, originY = 1f))
    }
}

/**
 * Spawns a billboard: a poster in a dark steel frame over a catwalk, held up by two lattice
 * pylons standing on the roof. The frame and catwalk are the engine's white texel, tinted.
 */
private fun Commands.spawnBillboard(art: Art, billboard: Billboard) {
    val poster = art.poster(billboard.poster)
    val posterHeight = 120f
    val posterWidth = posterHeight * poster.aspectRatio
    val pylonHeight = 64f
    val catwalk = 6f
    val posterBottom = billboard.roof - pylonHeight - catwalk
    val frameMargin = 6f
    val frameBottom = posterBottom + frameMargin
    val frameWidth = posterWidth + 2 * frameMargin
    val frameHeight = posterHeight + 2 * frameMargin
    val x = billboard.x
    val white = art.whitePixel
    spawn {
        add(Transform, Transform(x, posterBottom))
        add(Sprite, Sprite(poster, posterWidth, posterHeight, layer = Tuning.LAYER_POSTERS, originY = 1f))
    }
    spawn {
        add(Transform, Transform(x, frameBottom))
        add(Sprite, Sprite(white, frameWidth, frameHeight, color = FRAME_COLOR, layer = Tuning.LAYER_BILLBOARDS, originY = 1f))
    }
    spawn {
        add(Transform, Transform(x, frameBottom))
        add(Sprite, Sprite(white, posterWidth + 30f, catwalk, color = CATWALK_COLOR, layer = Tuning.LAYER_BILLBOARDS, originY = 0f))
    }
    val legHeight = billboard.roof - frameBottom
    val leg = art.pylon((legHeight / Tuning.PIXEL).toInt())
    for (side in -1..1 step 2) {
        spawn {
            add(Transform, Transform(x + side * posterWidth * 0.3f, billboard.roof))
            add(Sprite, Sprite(leg, leg.width * Tuning.PIXEL, legHeight, layer = Tuning.LAYER_PYLONS, originY = 1f))
        }
    }
}

private val FRAME_COLOR = Color(0x30364AFFu)
private val CATWALK_COLOR = Color(0x535C74FFu)

/** Spawns the hero standing at the level's start and returns him. */
fun Commands.spawnPlayer(art: Art): Entity = spawn {
    val start = RooftopRun.start
    val height = Tuning.PLAYER_SPRITE_HEIGHT
    val region = art.skate[0]
    add(Transform, Transform(start.x, start.y - Collisions.CONTACT_GAP - height / 2))
    add(Sprite, Sprite(region, height * region.aspectRatio, height, layer = Tuning.LAYER_PLAYER))
    // The box's bottom is the feet, at the bottom of the sprite.
    add(
        Collider,
        Collider(
            Shape.Box(Tuning.PLAYER_WIDTH, Tuning.PLAYER_HEIGHT),
            offsetY = height / 2 - Tuning.PLAYER_HEIGHT / 2,
            layers = Layers.PLAYER,
            mask = Layers.PLAYER_MASK,
        ),
    )
    add(KinematicBody, KinematicBody())
    add(Player, Player())
    add(Interpolated, Interpolated)
}

/** Spawns every ring, heart, blob and helicopter of the level. */
fun Commands.spawnPickupsAndEnemies(art: Art, random: Random) {
    val level = RooftopRun
    for (index in level.rings.indices) {
        val ring = level.rings[index]
        spawn {
            add(Transform, Transform(ring.x, ring.y))
            add(Sprite, Sprite(art.ring, Tuning.RING_SIZE, Tuning.RING_SIZE, layer = Tuning.LAYER_PICKUPS))
            // Rings look for nothing; the hero looks for them. The spin narrows the ring, not its hitbox.
            add(Collider, Collider(Shape.Circle(Tuning.RING_RADIUS), layers = Layers.RING, mask = CollisionLayers.NONE).apply { scalesWithTransform = false })
            // Neighbours spin a little apart, so a line of rings ripples.
            add(Ring, Ring(phase = index * 0.45f))
        }
    }
    for (heart in level.hearts) {
        spawn {
            add(Transform, Transform(heart.x, heart.y))
            add(Sprite, Sprite(art.heart, Tuning.HEART_SIZE, Tuning.HEART_SIZE / art.heart.aspectRatio, layer = Tuning.LAYER_PICKUPS))
            add(Collider, Collider(Shape.Circle(Tuning.HEART_SIZE / 2), layers = Layers.HEART, mask = CollisionLayers.NONE))
            add(HeartPickup, HeartPickup(heart.y))
        }
    }
    for (patrol in level.blobs) {
        spawn {
            add(Transform, Transform(patrol.startX, patrol.y - Tuning.BLOB_HEIGHT / 2))
            add(Sprite, Sprite(art.blob, Tuning.BLOB_WIDTH, Tuning.BLOB_HEIGHT, layer = Tuning.LAYER_ENEMIES))
            add(Collider, Collider(Shape.Box(Tuning.BLOB_WIDTH - 8f, Tuning.BLOB_HEIGHT - 8f), offsetY = 4f, layers = Layers.ENEMY, mask = CollisionLayers.NONE))
            add(Enemy, Enemy(EnemyKind.BLOB, patrol, direction = if (random.nextBoolean()) 1f else -1f, phase = random.nextFloat() * 6f))
            add(Interpolated, Interpolated)
        }
    }
    for (patrol in level.helis) {
        spawn {
            add(Transform, Transform(patrol.startX, patrol.y))
            add(Sprite, Sprite(art.heli, Tuning.HELI_WIDTH, Tuning.HELI_HEIGHT, layer = Tuning.LAYER_ENEMIES))
            add(Collider, Collider(Shape.Box(Tuning.HELI_WIDTH - 12f, Tuning.HELI_HEIGHT - 16f), layers = Layers.ENEMY, mask = CollisionLayers.NONE))
            add(Enemy, Enemy(EnemyKind.HELI, patrol, direction = -1f, phase = random.nextFloat() * 6f))
            add(Interpolated, Interpolated)
        }
    }
}

/** Spawns [text] at ([x], [y]) that rises and fades away: the score popup. */
fun Commands.spawnPopup(font: Font, text: String, x: Float, y: Float) {
    spawn {
        add(Transform, Transform(x, y))
        add(Text, Text(font, text, Tuning.POPUP_COLOR, Tuning.POPUP_SCALE, TextAlign.CENTER, layer = Tuning.LAYER_EFFECTS, shadow = POPUP_SHADOW))
        add(Velocity, Velocity(dy = -Tuning.POPUP_RISE))
        add(Fade, Fade(Tuning.POPUP_FADE, end = FadeEnd.DESTROY, delay = Tuning.POPUP_DELAY))
    }
}

private val POPUP_SHADOW = TextShadow(2f, 2f, Color(0x20103AC0u))

/** Spawns the stomp's impact: a yellow star flashing at ([x], [y]) and a ring of sparks. */
fun Commands.spawnImpact(art: Art, random: Random, x: Float, y: Float) {
    spawn {
        add(Transform, Transform(x, y, rotation = random.nextFloat()))
        add(Sprite, Sprite(art.star, Tuning.STAR_SIZE, Tuning.STAR_SIZE, color = Color(0xFFF27AFFu), layer = Tuning.LAYER_EFFECTS))
        add(Lifetime, Lifetime(Tuning.STAR_LIFETIME))
    }
    spawnBurst(
        x, y,
        ParticleEmitter.burst(
            art.star, count = 8, speed = 120f..220f, lifetime = 200.milliseconds..350.milliseconds,
            startScale = 0.5f, endScale = 0.15f, startRotation = 0f..6f, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}

/** Leaves a stomped blob flattened on its roof at [x], fading after a moment, with a splash of slime. */
fun Commands.spawnSquashedBlob(art: Art, x: Float, roof: Float, isFlipped: Boolean) {
    spawn {
        val height = Tuning.BLOB_HEIGHT
        add(Transform, Transform(x, roof - height * Tuning.SQUASHED_SCALE_Y / 2, scaleX = Tuning.SQUASHED_SCALE_X, scaleY = Tuning.SQUASHED_SCALE_Y))
        add(Sprite, Sprite(art.blob, Tuning.BLOB_WIDTH, height, layer = Tuning.LAYER_SQUASHED, isFlippedX = isFlipped))
        add(Fade, Fade(Tuning.SQUASHED_FADE, end = FadeEnd.DESTROY, delay = Tuning.SQUASHED_HOLD))
    }
    spawnBurst(
        x, roof - 8f,
        ParticleEmitter.burst(
            art.dust, count = 14, speed = 90f..220f, lifetime = 300.milliseconds..550.milliseconds,
            direction = (-90f).degrees, spread = 150f.degrees, gravityY = 700f, startColor = Color(0x5BD13BFFu),
            startScale = 0.9f, endScale = 0.27f, startRotation = 0f..6f, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}

/** Sends a stomped helicopter at ([x], [y]) tumbling down out of the level. */
fun Commands.spawnFallingHeli(art: Art, x: Float, y: Float, direction: Float) {
    spawn {
        add(Transform, Transform(x, y))
        add(Sprite, Sprite(art.heli, Tuning.HELI_WIDTH, Tuning.HELI_HEIGHT, layer = Tuning.LAYER_SQUASHED, isFlippedX = direction > 0f))
        add(Velocity, Velocity(dx = direction * 90f, dy = -180f))
        add(Tumble, Tumble(Tuning.FALLING_SPIN * direction))
        add(Lifetime, Lifetime(Tuning.FALLING_LIFETIME))
    }
}

/** Sparkles where a ring was taken. */
fun Commands.spawnSparkle(art: Art, x: Float, y: Float, color: Color) {
    spawnBurst(
        x, y,
        ParticleEmitter.burst(
            art.star, count = 7, speed = 60f..140f, lifetime = 250.milliseconds..450.milliseconds, startColor = color,
            startScale = 0.6f, endScale = 0.18f, startRotation = 0f..6f, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}

/** Kicks up dust under the blades where the hero landed hard. */
fun Commands.spawnDust(art: Art, x: Float, feet: Float) {
    spawnBurst(
        x, feet - 2f,
        ParticleEmitter.burst(
            art.dust, count = 10, speed = 40f..110f, lifetime = 250.milliseconds..450.milliseconds,
            direction = (-90f).degrees, spread = 170f.degrees, gravityY = 120f, startColor = Color(0xF2EEF8D0u),
            startScale = 1.1f, endScale = 0.33f, startRotation = 0f..6f, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}
