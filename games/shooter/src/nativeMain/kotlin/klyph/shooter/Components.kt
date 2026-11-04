package klyph.shooter

import klyph.collision.CollisionLayers
import klyph.entity.ComponentType

/**
 * The collision layers of the game. Bullets look for enemies, enemies for the player and bullets,
 * enemy bullets for the player, and the player for enemies and enemy bullets, so the engine
 * reports bullet-enemy, player-enemy and player-enemy-bullet overlaps and never a pair of bullets,
 * of enemies or a bullet and the player.
 */
object Layers {
    val PLAYER: CollisionLayers = CollisionLayers.of(1)
    val ENEMY: CollisionLayers = CollisionLayers.of(2)
    val BULLET: CollisionLayers = CollisionLayers.of(3)
    val ENEMY_BULLET: CollisionLayers = CollisionLayers.of(4)
}

/** The ship the player flies. While it waits to respawn it is hidden at the arena's center. */
class Player {
    /** Seconds until the next shot. Negative while catching up on shots owed to the fire rate. */
    var fireCooldown = 0f

    /** Seconds until the ship respawns; zero while it is in play. */
    var respawnTimer = 0f

    /** Seconds during which enemies and their bullets pass through the ship. */
    var invulnerableTimer = 0f

    val isAlive: Boolean get() = respawnTimer <= 0f

    override fun toString(): String = "Player(respawn=$respawnTimer, invulnerable=$invulnerableTimer)"

    companion object : ComponentType<Player>("Player")
}

/** The kinds of enemy, with the hit points each starts with and the score for destroying it. */
enum class EnemyKind(val hitPoints: Int, val score: Int) {
    /** Drifts down from the top of the arena, swaying; see [Sway]. */
    DRIFTER(hitPoints = 1, score = Tuning.DRIFTER_SCORE),

    /** Enters from the top of the view and swoops at the ship on an arc; see [Dive]. */
    DIVER(hitPoints = 1, score = Tuning.DIVER_SCORE),

    /** Comes every fifth wave, sweeps side to side at the top and fires back; see [Boss]. */
    BOSS(hitPoints = Tuning.BOSS_HIT_POINTS, score = Tuning.BOSS_SCORE),
}

/** An enemy of any [kind] with the [hitPoints] it has left. How it moves is a component of its own. */
class Enemy(val kind: EnemyKind) {
    var hitPoints = kind.hitPoints

    /** Set once it is destroyed, so a second bullet in the same frame does not count twice. */
    var isHit = false

    override fun toString(): String = "Enemy(kind=$kind, hitPoints=$hitPoints, hit=$isHit)"

    companion object : ComponentType<Enemy>("Enemy")
}

/** A drifter's sway around [centerX] at [rate] radians per second; its [Velocity] carries it down. */
class Sway(val centerX: Float, var phase: Float, val rate: Float) {
    override fun toString(): String = "Sway(centerX=$centerX, phase=$phase, rate=$rate)"

    companion object : ComponentType<Sway>("Sway")
}

/**
 * A diver's swoop: a curve from ([startX], [startY]) through the pull of ([controlX], [controlY])
 * to ([targetX], [targetY]), where the ship was when it spawned, flown in [duration] seconds. The
 * dive steers its [Velocity] along the curve, and once [progress] reaches 1 it keeps the last
 * heading and leaves the arena.
 */
class Dive(
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val targetX: Float,
    val targetY: Float,
    val duration: Float,
) {
    /** How far along the curve, from 0 at the start to 1 at the target. */
    var progress = 0f

    override fun toString(): String = "Dive(target=$targetX, $targetY, progress=$progress)"

    companion object : ComponentType<Dive>("Dive")
}

/** The boss's sweep across the top of the arena and its guns. It moves in UPDATE, not interpolated. */
class Boss {
    /** Middle of the side-to-side sweep, which drifts after the ship so the boss stays in play. */
    var centerX = Tuning.ARENA_WIDTH / 2

    /** Angle of the side-to-side sweep, in radians. */
    var phase = 0f

    /** Seconds until the next volley; it holds fire until it has come down to its height. */
    var fireTimer = Tuning.BOSS_FIRE_INTERVAL

    /** Seconds the boss still shows the white flash of the last hit. */
    var flashTimer = 0f

    override fun toString(): String = "Boss(phase=$phase, fire=$fireTimer)"

    companion object : ComponentType<Boss>("Boss")
}

/** A player bullet. */
class Bullet {
    /** Set once it hits an enemy, so it cannot hit a second one in the same frame. */
    var isSpent = false

    override fun toString(): String = "Bullet(spent=$isSpent)"

    companion object : ComponentType<Bullet>("Bullet")
}

/** A bullet the boss fired, which hurts the player. */
object EnemyBullet : ComponentType<EnemyBullet>("EnemyBullet")

/** Movement in world units per second. The engine has no velocity of its own, by design. */
class Velocity(var x: Float, var y: Float) {
    override fun toString(): String = "Velocity($x, $y)"

    companion object : ComponentType<Velocity>("Velocity")
}
