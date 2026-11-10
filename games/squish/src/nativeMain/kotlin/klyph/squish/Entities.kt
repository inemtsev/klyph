package klyph.squish

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Shape
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.math.Vec2
import klyph.math.angle
import klyph.math.degrees
import klyph.particle.ParticleEmitter
import klyph.particle.spawnBurst
import klyph.platform.Color
import klyph.platform.withAlpha
import klyph.render.Fade
import klyph.render.FadeEnd
import klyph.render.Sprite
import klyph.text.Font
import klyph.text.Text
import klyph.text.TextAlign
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Factories for every kind of entity in the game. They queue the entity on App.commands, so they
// are safe to call inside a World.forEach; the components attach after the running system.
// Bugs move in FIXED_UPDATE and are Interpolated, so they are drawn smoothly between steps.
// Entities spawned while a scene's onEnter or systems run belong to that scene and are destroyed
// when it leaves the stack, so nothing here needs cleaning up by hand.

/** Lays out the picnic, a cake between two watermelon slices, and returns the three entities. */
fun Commands.spawnPicnic(art: Art): List<Entity> = listOf(
    spawnFood(art, FoodKind.WATERMELON, Tuning.MELON_LEFT_X, Tuning.MELON_Y, -Tuning.MELON_TILT, isFlipped = false),
    spawnFood(art, FoodKind.CAKE, Tuning.CAKE_X, Tuning.CAKE_Y, rotation = 0f, isFlipped = false),
    spawnFood(art, FoodKind.WATERMELON, Tuning.MELON_RIGHT_X, Tuning.MELON_Y, Tuning.MELON_TILT, isFlipped = true),
)

private fun Commands.spawnFood(art: Art, kind: FoodKind, x: Float, y: Float, rotation: Float, isFlipped: Boolean): Entity = spawn {
    val size = if (kind == FoodKind.CAKE) Tuning.CAKE_SIZE else Tuning.MELON_SIZE
    val radius = if (kind == FoodKind.CAKE) Tuning.CAKE_RADIUS else Tuning.MELON_RADIUS
    val bites = if (kind == FoodKind.CAKE) Tuning.CAKE_BITES else Tuning.MELON_BITES
    add(Transform, Transform(x, y, rotation = rotation))
    add(Sprite, Sprite(art.food(kind), size, size, layer = Tuning.LAYER_FOOD, isFlippedX = isFlipped))
    // Food looks for nothing; bugs look for it, so the engine reports each bug that reaches it.
    add(Collider, Collider(Shape.Circle(radius), layers = Layers.FOOD, mask = CollisionLayers.NONE))
    add(Food, Food(kind, bites, size, radius))
}

/**
 * Sends a bug of a random kind onto the blanket from a random point just outside the world's
 * edge, heading for the nearest of [food] that is still there, at its kind's speed times
 * [speedFactor].
 */
fun Commands.spawnBug(art: Art, random: Random, food: Entity, foodX: Float, foodY: Float, startX: Float, startY: Float, speedFactor: Float) {
    val kind = randomKind(random)
    spawn {
        add(Transform, Transform(startX, startY, rotation = headingOf(foodX - startX, foodY - startY)))
        add(Sprite, Sprite(art.bug(kind), kind.size, kind.size, layer = Tuning.LAYER_BUGS))
        add(Collider, Collider(Shape.Circle(kind.radius), layers = Layers.BUG, mask = Layers.FOOD))
        add(Bug, Bug(kind, food, kind.speed * speedFactor, random.nextFloat() * 360f.degrees))
        add(Interpolated, Interpolated)
    }
}

/** Returns a kind of bug drawn by the kinds' weights. */
private fun randomKind(random: Random): BugKind {
    val kinds = BugKind.entries
    var pick = random.nextInt(kinds.sumOf { it.weight })
    for (kind in kinds) {
        pick -= kind.weight
        if (pick < 0) return kind
    }
    return kinds.last()
}

/**
 * Sets a title screen bug walking around the picnic: one of each kind in turn, on a flat ellipse
 * of its own size that keeps clear of the title and the prompt, at about its walking pace.
 */
fun Commands.spawnTitleBug(art: Art, random: Random, index: Int) {
    val kind = BugKind.entries[index % BugKind.entries.size]
    val size = Tuning.TITLE_ORBIT_MIN + random.nextFloat() * (Tuning.TITLE_ORBIT_MAX - Tuning.TITLE_ORBIT_MIN)
    val radiusX = size * Tuning.TITLE_ORBIT_WIDTH
    val radiusY = size * Tuning.TITLE_ORBIT_HEIGHT
    // Alternate directions.
    val rate = kind.speed / size * if (index % 2 == 0) 1f else -1f
    val angle = random.nextFloat() * 360f.degrees
    spawn {
        // Placed on its path already, so the first interpolated frame does not sweep it out from the middle.
        add(Transform, Transform(Tuning.CAKE_X + radiusX * cos(angle), Tuning.CAKE_Y + radiusY * sin(angle)))
        add(Sprite, Sprite(art.bug(kind), kind.size, kind.size, layer = Tuning.LAYER_BUGS))
        add(Orbit, Orbit(Tuning.CAKE_X, Tuning.CAKE_Y, radiusX, radiusY, angle, rate))
        add(Interpolated, Interpolated)
    }
}

/**
 * Plays the squish of a [kind] bug at ([x], [y]) facing [rotation]: a splat on the blanket that
 * stays a while, the flattened bug fading on top of it, a burst of droplets and a rising "+1".
 */
fun Commands.spawnSquish(art: Art, font: Font, kind: BugKind, x: Float, y: Float, rotation: Float, random: Random) {
    val splatSize = kind.size * Tuning.SPLAT_SCALE
    val splatColor = kind.splatColor.withAlpha(Tuning.SPLAT_ALPHA)
    spawn {
        add(Transform, Transform(x, y, rotation = random.nextFloat() * 360f.degrees))
        add(Sprite, Sprite(art.splat, splatSize, splatSize, color = splatColor, layer = Tuning.LAYER_SPLATS))
        add(Fade, Fade(Tuning.SPLAT_FADE, end = FadeEnd.DESTROY, delay = Tuning.SPLAT_HOLD))
    }
    spawn {
        // The bug's own frame is turned with it, so x scale widens it and y scale flattens it nose to tail.
        add(Transform, Transform(x, y, rotation, scaleX = Tuning.SQUASHED_WIDTH_SCALE, scaleY = Tuning.SQUASHED_LENGTH_SCALE))
        add(Sprite, Sprite(art.bug(kind), kind.size, kind.size, color = Tuning.SQUASHED_TINT, layer = Tuning.LAYER_SPLATS))
        add(Fade, Fade(Tuning.SQUASHED_FADE, end = FadeEnd.DESTROY))
    }
    spawnBurst(
        x, y,
        ParticleEmitter.burst(
            art.droplet, Tuning.DROPLETS, startColor = kind.splatColor,
            speed = Tuning.DROPLET_MIN_SPEED..Tuning.DROPLET_MAX_SPEED, lifetime = Tuning.DROPLET_MIN_LIFETIME..Tuning.DROPLET_MAX_LIFETIME,
            startScale = Tuning.DROPLET_START_SCALE, endScale = Tuning.DROPLET_END_SCALE, layer = Tuning.LAYER_EFFECTS,
        ),
    )
    spawn {
        val lineHeight = font.lineHeight * Tuning.POPUP_SCALE
        add(Transform, Transform(x, y - kind.size / 2 - lineHeight))
        add(Text, Text(font, "+1", Tuning.POPUP_COLOR, Tuning.POPUP_SCALE, TextAlign.CENTER, layer = Tuning.LAYER_EFFECTS))
        add(Velocity, Velocity(dy = -Tuning.POPUP_RISE))
        add(Fade, Fade(Tuning.POPUP_FADE, end = FadeEnd.DESTROY))
    }
}

/** Scatters crumbs of [food] at ([x], [y]): a few for a bite, a shower when the last bite goes. */
fun Commands.spawnCrumbs(art: Art, food: Food, x: Float, y: Float) {
    val count = if (food.bites == 0) Tuning.CRUMBS_LAST_BITE else Tuning.CRUMBS
    spawnBurst(
        x, y,
        ParticleEmitter.burst(
            art.droplet, count, startColor = food.kind.crumbColor,
            speed = Tuning.CRUMB_MIN_SPEED..Tuning.CRUMB_MAX_SPEED, lifetime = Tuning.CRUMB_MIN_LIFETIME..Tuning.CRUMB_MAX_LIFETIME,
            startScale = Tuning.DROPLET_START_SCALE, endScale = Tuning.DROPLET_END_SCALE, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}

/**
 * Returns the rotation that points a bug's head along ([dx], [dy]). The art faces up, a quarter
 * turn back from +x where [Vec2.angle] starts, and rotation grows clockwise on screen because y
 * points down.
 */
fun headingOf(dx: Float, dy: Float): Float = Vec2(dx, dy).angle + 90f.degrees
