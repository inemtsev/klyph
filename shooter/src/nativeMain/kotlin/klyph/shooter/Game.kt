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
import kotlin.time.Duration

/** The textures the game draws, loaded once at startup. */
class Art(
    val ship: TextureRegion,
    val enemy: TextureRegion,
    val bullet: TextureRegion,
    val star: TextureRegion,
    /** A small white square that particles are drawn from, tinted by their emitter. */
    val particle: TextureRegion,
    val explosion: SpriteSheet,
) {
    override fun toString(): String = "Art(explosion=$explosion)"

    companion object {
        fun load(assets: Assets): Art = Art(
            ship = assets.texture("ship.png").asRegion(),
            enemy = assets.texture("enemy.png").asRegion(),
            bullet = assets.texture("bullet.png").asRegion(),
            star = assets.texture(Image(1, 1).apply { fill(Color.WHITE) }).asRegion(),
            particle = assets.texture(Image(Tuning.PARTICLE_SIZE, Tuning.PARTICLE_SIZE).apply { fill(Color.WHITE) }).asRegion(),
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
 * What lasts for the whole process, across every scene and every game: art, sounds, controls, the
 * best score and the totals the exit line reports. It is an `App.resources` resource, inserted at
 * startup and read through [App.game]. The state of one game lives in [Round].
 */
class Game(val art: Art, val sounds: Sounds, val isAutopilot: Boolean) {
    val random: Random = if (isAutopilot) Random(Tuning.AUTOPILOT_SEED) else Random.Default

    /** Picks shot pitches. Kept apart from [random] so sound leaves the autopilot's runs unchanged. */
    val soundRandom: Random = Random.Default

    val controls = Controls()

    /** Score of the last game that ended. */
    var lastScore = 0

    /** Best score of every game since the process started. */
    var bestScore = 0

    /** Enemies destroyed over every game, for the line printed at exit. */
    var kills = 0

    /** Ships lost over every game, for the line printed at exit. */
    var deaths = 0

    /** Set once the mouse moves and cleared by the right stick: the ship keeps facing the cursor while set. */
    var isAimingWithMouse = false

    /** When the game over screen appeared, so the autopilot knows when to start the next game. */
    var gameOverAt: Duration = Duration.ZERO

    /** Whether the music was last started rather than stopped; a track still fading out counts as stopped. */
    private var isMusicOn = false

    /** Reused for screen text that changes every frame, so drawing it allocates no `String`. */
    val text = StringBuilder()

    /** Fades the music in, unless it is already on. */
    fun startMusic(app: App) {
        if (isMusicOn) return
        app.audio.music.play(sounds.music, fadeIn = Tuning.MUSIC_FADE)
        isMusicOn = true
    }

    /** Fades the music out. */
    fun stopMusic(app: App) {
        app.audio.music.stop(fadeOut = Tuning.MUSIC_FADE)
        isMusicOn = false
    }

    override fun toString(): String =
        "Game(best=$bestScore, kills=$kills, deaths=$deaths, autopilot=$isAutopilot)"
}

/**
 * The state of one game, from the play scene's entry to game over: the ship, its exhaust, the
 * score, the lives and the spawn timer. The play scene inserts a new one each time it enters, so
 * every game starts fresh, and reads it through [App.round].
 *
 * [player] and [thruster] are constructor values rather than late-set fields, because [Entity] is
 * a value class and cannot be `lateinit`; the play scene spawns both first, which gives their
 * handles at once.
 */
class Round(val game: Game, val player: Entity, val thruster: Entity) {
    var score = 0
    var lives = Tuning.PLAYER_LIVES

    /** Seconds until the next enemy spawns. */
    var spawnTimer = 0f

    /** Set once the last life is lost and its wreck has cleared; the play scene then shows game over. */
    var isOver = false

    override fun toString(): String = "Round(score=$score, lives=$lives, over=$isOver, player=$player)"
}

/** The state of the whole process, inserted as a resource at startup. */
val App.game: Game get() = resources.get()

/** The state of the game being played, inserted as a resource each time the play scene enters. */
val App.round: Round get() = resources.get()
