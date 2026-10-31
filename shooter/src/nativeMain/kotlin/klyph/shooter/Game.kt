package klyph.shooter

import klyph.app.Assets
import klyph.entity.Entity
import klyph.platform.Color
import klyph.platform.Image
import klyph.render.SpriteSheet
import klyph.render.TextureRegion
import klyph.render.asRegion
import kotlin.random.Random

/** The textures the game draws, loaded once at startup. */
class Art(
    val ship: TextureRegion,
    val enemy: TextureRegion,
    val bullet: TextureRegion,
    val star: TextureRegion,
    val explosion: SpriteSheet,
) {
    override fun toString(): String = "Art(explosion=$explosion)"

    companion object {
        fun load(assets: Assets): Art = Art(
            ship = assets.texture("ship.png").asRegion(),
            enemy = assets.texture("enemy.png").asRegion(),
            bullet = assets.texture("bullet.png").asRegion(),
            star = assets.texture(Image(1, 1).apply { fill(Color.WHITE) }).asRegion(),
            explosion = assets.spriteSheet("explosion.png", columns = 6, rows = 1),
        )
    }
}

/**
 * The state shared by the game's systems: art, controls, score and the scratch lists the systems
 * reuse every step.
 */
class Game(val art: Art, val isAutopilot: Boolean) {
    val random: Random = if (isAutopilot) Random(Tuning.AUTOPILOT_SEED) else Random.Default

    val keys = HeldKeys()
    val controls = Controls()

    private var playerOrNull: Entity? = null

    /** The player's ship, created at startup. */
    var player: Entity
        get() = checkNotNull(playerOrNull) { "The player is created at startup." }
        set(value) {
            playerOrNull = value
        }

    var score = 0
    var lives = Tuning.PLAYER_LIVES
    var kills = 0
    var deaths = 0

    /** Seconds until the next enemy spawns. */
    var spawnTimer = 0f

    /** Entities to destroy once the running query is over. */
    val doomed = ArrayList<Entity>()

    /** Enemies shot or rammed in this step; each leaves an explosion before it goes. */
    val wrecked = ArrayList<Entity>()

    /** Prints the score line after the score or the lives changed. */
    fun printScore() {
        println("Score $score, lives $lives")
    }

    override fun toString(): String = "Game(score=$score, lives=$lives, kills=$kills, autopilot=$isAutopilot)"
}
