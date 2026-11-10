package klyph.roller

import klyph.app.Assets
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
import klyph.platform.SamplerFilter
import klyph.render.Fade
import klyph.render.FadeEnd
import klyph.render.Sprite
import klyph.render.TextureRegion
import klyph.render.asRegion
import klyph.text.Font
import klyph.text.Text
import klyph.text.TextAlign
import klyph.text.TextShadow
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

// Factories for every kind of entity in the game. They queue the entity on App.commands, so they
// are safe to call inside a World.forEach. The level's buildings, ledges and decoration are
// spawned once at startup and belong to no scene, so the title screen shows them too; the hero,
// enemies, pickups and effects are spawned by the play scene and its systems, so they belong to
// it and vanish when it leaves the stack.

/**
 * Builds the level's scenery: a solid for every building and ledge, a painted texture for each,
 * the billboards on their pylons, the potted plants, the air conditioners and the arrow sign at
 * the end.
 */
fun Commands.spawnScenery(assets: Assets, art: Art) {
    val level = RooftopRun
    val pixel = Tuning.PIXEL
    for (block in level.blocks) spawnSolid(block)
    for (index in level.buildings.indices) {
        val building = level.buildings[index]
        val texelsWide = ceil((building.right - building.left) / pixel).toInt()
        // A little past the bottom of the world, so a falling camera never sees the end.
        val texelsHigh = ceil((Tuning.WORLD_HEIGHT - building.top + 20f) / pixel).toInt()
        val face = assets.createTexture(paintBuilding(texelsWide, texelsHigh, index), SamplerFilter.NEAREST).asRegion()
        spawnBlock(face, left = building.left, top = building.top, width = texelsWide * pixel, height = texelsHigh * pixel)
    }
    for (ledge in level.ledges) {
        val texelsWide = ceil((ledge.right - ledge.left) / pixel).toInt()
        val girder = assets.createTexture(paintLedge(texelsWide), SamplerFilter.NEAREST).asRegion()
        spawnBlock(girder, left = ledge.left, top = ledge.top, width = texelsWide * pixel, height = Tuning.LEDGE_HEIGHT)
    }
    for (billboard in level.billboards) spawnBillboard(art, billboard)
    for (plant in level.plants) {
        val height = PLANT_HEIGHT
        val width = height * art.plant.width / art.plant.height
        spawnProp(art.plant, plant.x, plant.y, width, height)
    }
    for (unit in level.airConditioners) {
        val region = art.airConditioner
        spawnProp(region, unit.x, unit.y, region.width * Tuning.PIXEL, region.height * Tuning.PIXEL)
    }
    val signHeight = SIGN_HEIGHT
    spawnProp(art.sign, RooftopRun.SIGN_X, RooftopRun.signRoof, signHeight * art.sign.width / art.sign.height, signHeight)
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

/** Spawns a static sprite of [width] by [height] whose top-left corner is at ([left], [top]). */
private fun Commands.spawnBlock(region: TextureRegion, left: Float, top: Float, width: Float, height: Float) {
    spawn {
        add(Transform, Transform(left + width / 2, top + height / 2))
        add(Sprite, Sprite(region, width, height, layer = Tuning.LAYER_BUILDINGS))
    }
}

/** Spawns a decoration standing with its bottom on [floor], centered on [x]. */
private fun Commands.spawnProp(region: TextureRegion, x: Float, floor: Float, width: Float, height: Float) {
    spawn {
        add(Transform, Transform(x, floor - height / 2))
        add(Sprite, Sprite(region, width, height, layer = Tuning.LAYER_DECOR))
    }
}

/**
 * Spawns a billboard: a poster in a dark steel frame over a catwalk, held up by two lattice
 * pylons standing on the roof. The frame and catwalk are the engine's white texel, tinted.
 */
private fun Commands.spawnBillboard(art: Art, billboard: Billboard) {
    val poster = art.poster(billboard.poster)
    val posterHeight = 120f
    val posterWidth = posterHeight * poster.width / poster.height
    val pylonHeight = 64f
    val catwalk = 6f
    val posterBottom = billboard.roof - pylonHeight - catwalk
    val centerY = posterBottom - posterHeight / 2
    val x = billboard.x
    spawn {
        add(Transform, Transform(x, centerY))
        add(Sprite, Sprite(poster, posterWidth, posterHeight, layer = Tuning.LAYER_POSTERS))
    }
    spawn {
        add(Transform, Transform(x, centerY))
        add(Sprite, Sprite(art.whitePixel, posterWidth + 12f, posterHeight + 12f, color = FRAME_COLOR, layer = Tuning.LAYER_BILLBOARDS))
    }
    spawn {
        add(Transform, Transform(x, posterBottom + 6f + catwalk / 2))
        add(Sprite, Sprite(art.whitePixel, posterWidth + 30f, catwalk, color = CATWALK_COLOR, layer = Tuning.LAYER_BILLBOARDS))
    }
    val legHeight = billboard.roof - (posterBottom + 6f)
    val leg = art.pylon((legHeight / Tuning.PIXEL).toInt())
    for (side in -1..1 step 2) {
        spawn {
            add(Transform, Transform(x + side * posterWidth * 0.3f, billboard.roof - legHeight / 2))
            add(Sprite, Sprite(leg, leg.width * Tuning.PIXEL, legHeight, layer = Tuning.LAYER_PYLONS))
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
    add(Sprite, Sprite(region, height * region.width / region.height, height, layer = Tuning.LAYER_PLAYER))
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
            add(Sprite, Sprite(art.heart, Tuning.HEART_SIZE, Tuning.HEART_SIZE * art.heart.height / art.heart.width, layer = Tuning.LAYER_PICKUPS))
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
