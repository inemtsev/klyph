package klyph.squish

import klyph.collision.CollisionLayers
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.platform.Color

/**
 * The collision layers of the game. Bugs look for food, so the engine reports bug-food overlaps
 * and nothing else; the swatter finds bugs with a shape query on [BUG].
 */
object Layers {
    val BUG: CollisionLayers = CollisionLayers.of(1)
    val FOOD: CollisionLayers = CollisionLayers.of(2)
}

/**
 * The kinds of bug: how big each is on screen, the radius of its body for the swatter and the
 * food, how fast it walks before the round speeds up, how often it comes, and the color of its
 * splat. The art faces up.
 */
enum class BugKind(val size: Float, val radius: Float, val speed: Float, val weight: Int, val splatColor: Color) {
    ANT(size = 60f, radius = 20f, speed = 95f, weight = 4, splatColor = Color(red = 1f, green = 0.55f, blue = 0.1f)),
    LADYBUG(size = 76f, radius = 28f, speed = 70f, weight = 4, splatColor = Color(red = 0.9f, green = 0.12f, blue = 0.1f)),
    CATERPILLAR(size = 100f, radius = 30f, speed = 45f, weight = 2, splatColor = Color(red = 0.55f, green = 0.85f, blue = 0.15f)),
}

/**
 * A bug walking toward [target], a food entity, at [speed]. Once it has taken a bite it is
 * [isFull] and runs off the blanket away from [fleeX], [fleeY], where it ate.
 */
class Bug(val kind: BugKind, var target: Entity, val speed: Float, var wigglePhase: Float) {
    var isFull = false

    /** Set once the swatter got it, so a second swat in the same frame does not count it twice. */
    var isSquished = false

    var fleeX = 0f
    var fleeY = 0f

    override fun toString(): String = "Bug(kind=$kind, target=$target, full=$isFull, squished=$isSquished)"

    companion object : ComponentType<Bug>("Bug")
}

/** A piece of the picnic with [bites] left of [maxBites], drawn [baseSize] across when whole. */
class Food(val kind: FoodKind, val maxBites: Int, val baseSize: Float, val baseRadius: Float) {
    var bites = maxBites

    override fun toString(): String = "Food(kind=$kind, bites=$bites/$maxBites)"

    companion object : ComponentType<Food>("Food")
}

/** The two kinds of food, with the colors of their crumbs. */
enum class FoodKind(val crumbColor: Color) {
    CAKE(Color(red = 1f, green = 0.95f, blue = 0.85f)),
    WATERMELON(Color(red = 0.95f, green = 0.2f, blue = 0.2f)),
}

/** Movement in world units per second. */
class Velocity(var x: Float, var y: Float) {
    override fun toString(): String = "Velocity($x, $y)"

    companion object : ComponentType<Velocity>("Velocity")
}

/** Seconds until the entity is destroyed: the carrier of a burst of particles. */
class Lifetime(var remaining: Float) {
    override fun toString(): String = "Lifetime($remaining)"

    companion object : ComponentType<Lifetime>("Lifetime")
}

/**
 * Fades the entity's sprite or text out: it keeps [color] for [hold] seconds, then its alpha falls
 * to zero over [duration] seconds, and the entity is destroyed.
 */
class Fade(val color: Color, val hold: Float, val duration: Float) {
    var age = 0f

    override fun toString(): String = "Fade(age=$age, hold=$hold, duration=$duration)"

    companion object : ComponentType<Fade>("Fade")
}

/** A title screen bug walking round an ellipse of radii [radiusX] and [radiusY] about ([centerX], [centerY]). */
class Orbit(val centerX: Float, val centerY: Float, val radiusX: Float, val radiusY: Float, var angle: Float, val rate: Float) {
    override fun toString(): String = "Orbit(radius=$radiusX x $radiusY, angle=$angle, rate=$rate)"

    companion object : ComponentType<Orbit>("Orbit")
}
