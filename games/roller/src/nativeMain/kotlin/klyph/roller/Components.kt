package klyph.roller

import klyph.collision.CollisionLayers
import klyph.entity.ComponentType

/**
 * The collision layers of the game. Only the hero looks for anything, so the engine reports the
 * hero's overlaps with enemies and pickups and never an enemy touching a ring.
 */
object Layers {
    val PLAYER: CollisionLayers = CollisionLayers.of(1)
    val ENEMY: CollisionLayers = CollisionLayers.of(2)
    val RING: CollisionLayers = CollisionLayers.of(3)
    val HEART: CollisionLayers = CollisionLayers.of(4)
}

/**
 * The hero: his [body], which way he faces, and the timers of being hit. The entity's Transform
 * follows the body after every fixed step; the body is what the game moves.
 */
class Player(val body: Body) {
    /** 1 facing right, -1 facing left. */
    var facing = 1f

    /** Seconds of no control left after a hit. */
    var hurtLock = 0f

    /** Seconds of blinking left in which enemies cannot hurt him. */
    var invulnerable = 0f

    /** How far through the skating stride he is; each whole stride swaps the skating frame. */
    var stride = 0f

    /** Index in [RooftopRun.buildings] of the last roof he stood on, and where, for the respawn. */
    var safeBuilding = 0
    var safeX = RooftopRun.start.x

    /** Set by a respawn and cleared by the next fixed step: he stays hidden until then. */
    var isHiddenForStep = false

    override fun toString(): String = "Player($body, facing=$facing, invulnerable=$invulnerable)"

    companion object : ComponentType<Player>("Player")
}

/** The two kinds of enemy. */
enum class EnemyKind { BLOB, HELI }

/**
 * An enemy going back and forth along its [patrol]: a blob walking a roof, turning at the ends,
 * or a helicopter flying a wavy line above them. [direction] is 1 going right and -1 going left.
 */
class Enemy(val kind: EnemyKind, val patrol: Patrol, var direction: Float, var phase: Float) {
    /** Set once stomped, so a second overlap in the same frame does not count it again. */
    var isStomped = false

    override fun toString(): String = "Enemy(kind=$kind, $patrol, direction=$direction, stomped=$isStomped)"

    companion object : ComponentType<Enemy>("Enemy")
}

/** A gold ring that spins in place, [phase] radians into its spin. */
class Ring(val phase: Float) {
    var isTaken = false

    override fun toString(): String = "Ring(taken=$isTaken)"

    companion object : ComponentType<Ring>("Ring")
}

/** A heart that bobs about [baseY] and gives one heart back. */
class HeartPickup(val baseY: Float) {
    var isTaken = false

    override fun toString(): String = "HeartPickup(baseY=$baseY, taken=$isTaken)"

    companion object : ComponentType<HeartPickup>("HeartPickup")
}

/**
 * A stomped helicopter tumbling away, turning at [spin] radians per second while gravity bends
 * its [klyph.entity.Velocity] down.
 */
class Tumble(val spin: Float) {
    override fun toString(): String = "Tumble(spin=$spin)"

    companion object : ComponentType<Tumble>("Tumble")
}
