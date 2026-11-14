package klyph.roller

import klyph.app.App
import klyph.app.Assets
import klyph.audio.MusicTrack
import klyph.audio.Sound
import klyph.entity.Entity
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.render.Texture
import klyph.render.TextureRegion
import klyph.render.asRegion
import klyph.render.region
import kotlin.random.Random

/** The textures the game draws, loaded or painted once at startup. */
class Art(
    val skate: List<TextureRegion>,
    val jump: TextureRegion,
    val blob: TextureRegion,
    val heli: TextureRegion,
    val ring: TextureRegion,
    val heart: TextureRegion,
    val sign: TextureRegion,
    val plant: TextureRegion,
    val airConditioner: TextureRegion,
    /** One repeating tile of brick wall for each palette; a building takes the one its index picks. */
    val bricks: List<TextureRegion>,
    /** Repeating tiles: the stone cap along a roof, a row of windows and a steel girder. */
    val cap: TextureRegion,
    val windows: TextureRegion,
    val girder: TextureRegion,
    val posterFace: TextureRegion,
    val posterCity: TextureRegion,
    val sky: TextureRegion,
    val skyline: TextureRegion,
    val clouds: List<TextureRegion>,
    /** A tall lattice pylon; [pylon] cuts the height a billboard needs from its top. */
    private val pylonTexture: Texture,
    val star: TextureRegion,
    val dust: TextureRegion,
    val whitePixel: TextureRegion,
) {
    fun poster(poster: Poster): TextureRegion = if (poster == Poster.FACE) posterFace else posterCity

    /** Returns the top [texels] rows of the pylon texture. */
    fun pylon(texels: Int): TextureRegion = pylonTexture.region(0, 0, pylonTexture.width, texels.coerceIn(1, pylonTexture.height))

    override fun toString(): String = "Art(clouds=${clouds.size})"

    companion object {
        fun load(assets: Assets): Art {
            // The generated sprites are downscaled pixel art, so they keep linear sampling; the
            // painted art is drawn at whole texels and wants nearest sampling to stay blocky. The
            // tiles are the painted art that a Sprite with a tile size repeats, so they also repeat.
            val pixel = SamplerFilter.NEAREST
            val repeat = SamplerAddress.REPEAT
            return Art(
                skate = listOf(assets.texture("ninja_skate1.png").asRegion(), assets.texture("ninja_skate2.png").asRegion()),
                jump = assets.texture("ninja_jump.png").asRegion(),
                blob = assets.texture("blob.png").asRegion(),
                heli = assets.texture("heli.png").asRegion(),
                ring = assets.texture("ring.png").asRegion(),
                heart = assets.texture("heart.png").asRegion(),
                sign = assets.texture("sign.png").asRegion(),
                plant = assets.texture("plant.png").asRegion(),
                airConditioner = assets.createTexture(paintAirConditioner(), pixel).asRegion(),
                bricks = List(BRICK_STYLE_COUNT) { assets.createTexture(paintBrickTile(it), pixel, repeat).asRegion() },
                cap = assets.createTexture(paintCapTile(), pixel, repeat).asRegion(),
                windows = assets.createTexture(paintWindowTile(), pixel, repeat).asRegion(),
                girder = assets.createTexture(paintGirderTile(), pixel, repeat).asRegion(),
                posterFace = assets.texture("poster_face.png").asRegion(),
                posterCity = assets.texture("poster_city.png").asRegion(),
                sky = assets.createTexture(paintGradient(64, Tuning.SKY_TOP, Tuning.SKY_BOTTOM)).asRegion(),
                skyline = assets.createTexture(paintSkyline(Tuning.SKYLINE_TEXELS_WIDE, Tuning.SKYLINE_TEXELS_HIGH), pixel).asRegion(),
                clouds = List(3) { assets.createTexture(paintCloud(it), pixel).asRegion() },
                pylonTexture = assets.createTexture(paintPylon(PYLON_TEXELS), pixel),
                star = assets.createTexture(paintStar(16), pixel).asRegion(),
                dust = assets.disc(8),
                whitePixel = assets.whitePixel,
            )
        }

        /** Height of the painted pylon in texels, enough for the tallest billboard. */
        private const val PYLON_TEXELS = 64
    }
}

/** The sound effects and the music the game plays, loaded once at startup. */
class Sounds(
    val jump: Sound,
    val ring: Sound,
    val stomp: Sound,
    val hurt: Sound,
    val heart: Sound,
    val complete: Sound,
    val gameOver: Sound,
    val music: MusicTrack,
) {
    override fun toString(): String = "Sounds(music=$music)"

    companion object {
        fun load(assets: Assets): Sounds = Sounds(
            jump = assets.sound("jump.wav"),
            ring = assets.sound("ring.wav"),
            stomp = assets.sound("stomp.wav"),
            hurt = assets.sound("hurt.wav"),
            heart = assets.sound("heart.wav"),
            complete = assets.sound("complete.wav"),
            gameOver = assets.sound("gameover.wav"),
            music = assets.musicTrack("music.ogg"),
        )
    }
}

/**
 * What lasts for the whole process, across every scene and every run: art, sounds, controls, the
 * best score and the totals the exit line reports. It is an `App.resources` resource, inserted at
 * startup and read through [App.game]. The state of one run of the level lives in [Run].
 */
class Game(val art: Art, val sounds: Sounds, val isAutopilot: Boolean) {
    val random: Random = if (isAutopilot) Random(Tuning.AUTOPILOT_SEED) else Random.Default

    val controls = Controls()

    var bestScore = 0

    /** Totals over every run, for the line printed at exit and the autopilot's check. */
    var totalRings = 0
    var totalStomps = 0
    var levelsCompleted = 0
    var gameOvers = 0

    /** Where the title screen's camera has drifted to along the level. */
    var titleCameraX = 0f

    /** Where the autopilot's last look ahead ended, reused so that planning allocates nothing of its own. */
    val arc = Arc()

    private var isMusicOn = false

    /** Reused for screen text that changes every frame, so drawing it allocates no `String`. */
    val text = StringBuilder()

    /** Fades the music in, unless it is already on. */
    fun startMusic(app: App) {
        if (isMusicOn) return
        app.audio.music.play(sounds.music, fadeIn = Tuning.MUSIC_FADE)
        isMusicOn = true
    }

    override fun toString(): String =
        "Game(best=$bestScore, rings=$totalRings, stomps=$totalStomps, completed=$levelsCompleted, autopilot=$isAutopilot)"
}

/** What the hero is doing, as far as the run's rules care. */
enum class RunState {
    /** Rolling about under the player's control. */
    PLAYING,

    /** Fell into a gap; put back on the last roof once the timer runs out. */
    FALLING,

    /** Lost the last heart and tumbles off the screen before the game over screen. */
    DYING,

    /** Reached the sign and glides to a stop before the tally. */
    FINISHING,
}

/** How a run ended, once its last moment has played out. */
enum class RunOutcome { COMPLETE, GAME_OVER }

/**
 * The state of one run of the level, from the play scene's entry to its end screen. The play
 * scene inserts a new one each time it enters, so every run starts fresh, and reads it through
 * [App.run].
 */
class Run(
    val game: Game,
    val player: Entity,
    /** Fixed steps completed when the run started, so its first step can tell itself apart. */
    val startStep: Long,
) {
    var score = 0
    var rings = 0
    var stomps = 0
    var hearts = Tuning.MAX_HEARTS

    /** Seconds spent [RunState.PLAYING], for the time bonus; the play scene's own clock also runs while he falls or glides. */
    var elapsed = 0f

    var state = RunState.PLAYING

    /** Seconds left in the current [state] when it is timed. */
    var stateTimer = 0f

    /** Set once the run is over; the play scene then shows the end screen. */
    var outcomeOrNull: RunOutcome? = null

    /** Seconds of camera shake left. */
    var shake = 0f

    /** How far ahead of the hero the camera looks, eased as he speeds up and turns. */
    var lookAhead = 0f

    // The level complete tally, worked out when the hero reaches the sign.
    var heartBonus = 0
    var timeBonus = 0
    val finalScore: Int get() = score + heartBonus + timeBonus

    override fun toString(): String = "Run(score=$score, rings=$rings, hearts=$hearts, state=$state)"
}

/** The state of the whole process, inserted as a resource at startup. */
val App.game: Game get() = resources.get()

/** The state of the run being played, inserted as a resource each time the play scene enters. */
val App.run: Run get() = resources.get()
