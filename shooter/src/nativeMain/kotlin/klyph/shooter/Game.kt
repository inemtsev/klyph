package klyph.shooter

import klyph.app.App
import klyph.app.Assets
import klyph.audio.MusicTrack
import klyph.audio.Sound
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

/** The sound effects and the music the game plays, loaded once at startup. */
class Sounds(
    val shot: Sound,
    val explosion: Sound,
    val hit: Sound,
    val gameOver: Sound,
    val music: MusicTrack,
) {
    override fun toString(): String = "Sounds(music=$music)"

    companion object {
        fun load(assets: Assets): Sounds = Sounds(
            shot = assets.sound("shot.wav"),
            explosion = assets.sound("explosion.wav"),
            hit = assets.sound("hit.wav"),
            gameOver = assets.sound("gameover.wav"),
            music = assets.music("music.ogg"),
        )
    }
}

/**
 * The state the game's systems share: art, sounds, controls, score and timers. It is an
 * `App.resources` resource, inserted at startup and read through [App.game].
 *
 * [player] is a constructor value rather than a late-set field, because [Entity] is a value class
 * and cannot be `lateinit`; startup spawns the ship first, which gives its handle at once.
 */
class Game(val art: Art, val sounds: Sounds, val isAutopilot: Boolean, val player: Entity) {
    val random: Random = if (isAutopilot) Random(Tuning.AUTOPILOT_SEED) else Random.Default

    /** Picks shot pitches. Kept apart from [random] so sound leaves the autopilot's runs unchanged. */
    val soundRandom: Random = Random.Default

    val controls = Controls()

    var score = 0
    var lives = Tuning.PLAYER_LIVES
    var kills = 0
    var deaths = 0

    /** Seconds until the next enemy spawns. */
    var spawnTimer = 0f

    /** Set while the window is in the background; the gameplay systems skip their work. */
    var isPaused = false

    /** Set once the mouse moves and cleared by the right stick: the ship keeps facing the cursor while set. */
    var isAimingWithMouse = false

    /** Prints the score line after the score or the lives changed. */
    fun printScore() {
        println("Score $score, lives $lives")
    }

    override fun toString(): String =
        "Game(score=$score, lives=$lives, kills=$kills, paused=$isPaused, autopilot=$isAutopilot)"
}

/** The game state, inserted as a resource at startup. */
val App.game: Game get() = resources.get()
