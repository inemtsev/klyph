package klyph.shooter

import klyph.entity.ComponentType
import klyph.render.Sprite

/**
 * The ship the player flies. [sprite] is kept here so the ship can leave the screen while it
 * waits to respawn and come back as it was.
 */
class Player(val sprite: Sprite) {
    /** Seconds until the next shot. Negative while catching up on shots owed to the fire rate. */
    var fireCooldown = 0f

    /** Seconds until the ship respawns; zero while it is in play. */
    var respawnTimer = 0f

    /** Seconds during which enemies pass through the ship. */
    var invulnerableTimer = 0f

    /** Bank angle the ship is easing toward, in radians. */
    var bank = 0f

    val isAlive: Boolean get() = respawnTimer <= 0f

    override fun toString(): String = "Player(respawn=$respawnTimer, invulnerable=$invulnerableTimer)"

    companion object : ComponentType<Player>("Player")
}

/** An enemy ship drifting down while it sways around [centerX] at [swayRate] radians per second. */
class Enemy(val centerX: Float, var swayPhase: Float, val swayRate: Float) {
    /** Set once something hits it, so a second bullet in the same step does not count twice. */
    var isHit = false

    override fun toString(): String = "Enemy(centerX=$centerX, phase=$swayPhase, hit=$isHit)"

    companion object : ComponentType<Enemy>("Enemy")
}

/** A player bullet. */
class Bullet {
    /** Set once it hits an enemy, so it cannot hit a second one in the same step. */
    var isSpent = false

    override fun toString(): String = "Bullet(spent=$isSpent)"

    companion object : ComponentType<Bullet>("Bullet")
}

/** Movement in world units per second. */
class Velocity(var x: Float, var y: Float) {
    override fun toString(): String = "Velocity($x, $y)"

    companion object : ComponentType<Velocity>("Velocity")
}

/** Seconds until the entity is destroyed. */
class Lifetime(var remaining: Float) {
    override fun toString(): String = "Lifetime($remaining)"

    companion object : ComponentType<Lifetime>("Lifetime")
}

/** An explosion, destroyed once its one-shot animation has played. */
class Explosion {
    override fun toString(): String = "Explosion"

    companion object : ComponentType<Explosion>("Explosion")
}
