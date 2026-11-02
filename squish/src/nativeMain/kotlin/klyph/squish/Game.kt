package klyph.squish

import klyph.app.App
import klyph.app.Assets
import klyph.audio.MusicTrack
import klyph.audio.Sound
import klyph.entity.Entity
import klyph.platform.Color
import klyph.platform.Image
import klyph.render.TextureRegion
import klyph.render.asRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration

/** The textures the game draws, loaded or drawn once at startup. */
class Art(
    val ant: TextureRegion,
    val ladybug: TextureRegion,
    val caterpillar: TextureRegion,
    val cake: TextureRegion,
    val watermelon: TextureRegion,
    val swatter: TextureRegion,
    /** A white blob with drops around it, tinted by the kind of bug squished on it. */
    val splat: TextureRegion,
    /** A small white disc that droplets and crumbs are drawn from, tinted by their emitter. */
    val droplet: TextureRegion,
) {
    fun bug(kind: BugKind): TextureRegion = when (kind) {
        BugKind.ANT -> ant
        BugKind.LADYBUG -> ladybug
        BugKind.CATERPILLAR -> caterpillar
    }

    fun food(kind: FoodKind): TextureRegion = when (kind) {
        FoodKind.CAKE -> cake
        FoodKind.WATERMELON -> watermelon
    }

    override fun toString(): String = "Art(swatter=$swatter)"

    companion object {
        fun load(assets: Assets): Art = Art(
            ant = assets.texture("ant.png").asRegion(),
            ladybug = assets.texture("ladybug.png").asRegion(),
            caterpillar = assets.texture("caterpillar.png").asRegion(),
            cake = assets.texture("cake.png").asRegion(),
            watermelon = assets.texture("watermelon.png").asRegion(),
            swatter = assets.texture("swatter.png").asRegion(),
            splat = assets.texture(splatImage(Tuning.SPLAT_TEXELS)).asRegion(),
            droplet = assets.texture(discImage(Tuning.DROPLET_TEXELS)).asRegion(),
        )
    }
}

/** The sound effects and the music the game plays, loaded once at startup. */
class Sounds(
    val squish: Sound,
    val munch: Sound,
    val start: Sound,
    val end: Sound,
    val music: MusicTrack,
) {
    override fun toString(): String = "Sounds(music=$music)"

    companion object {
        fun load(assets: Assets): Sounds = Sounds(
            squish = assets.sound("squish.wav"),
            munch = assets.sound("munch.wav"),
            start = assets.sound("start.wav"),
            end = assets.sound("end.wav"),
            music = assets.music("music.ogg"),
        )
    }
}

/**
 * What lasts for the whole process, across every scene and every round: art, sounds, the cursor,
 * the best score and the totals the exit line reports. It is an `App.resources` resource, inserted
 * at startup and read through [App.game]. The state of one round lives in [Round].
 */
class Game(val art: Art, val sounds: Sounds, val isAutopilot: Boolean) {
    val random: Random = if (isAutopilot) Random(Tuning.AUTOPILOT_SEED) else Random.Default

    /** Picks squish pitches. Kept apart from [random] so sound leaves the autopilot's runs unchanged. */
    val soundRandom: Random = Random.Default

    val controls = Controls()

    /** Score of the last round that ended. */
    var lastScore = 0

    /** Best score of every round since the process started. */
    var bestScore = 0

    /** Bugs squished over every round, for the line printed at exit. */
    var totalSquished = 0

    /** Rounds played to the end, for the line printed at exit. */
    var roundsPlayed = 0

    /** When the end screen appeared, so it can ignore early presses and the autopilot can play again. */
    var endedAt: Duration = Duration.ZERO

    /** Seconds left of the swatter's slap; it is drawn squashed while this is above zero. */
    var slapTimer = 0f

    /** Seconds until the autopilot may swat again. */
    var autopilotCooldown = 0f

    /** Whether the music was last started rather than stopped. */
    private var isMusicOn = false

    /** Reused for screen text that changes every frame, so drawing it allocates no `String`. */
    val text = StringBuilder()

    /** Reused for the bugs under the swatter, so a swat allocates no list. */
    val hits = ArrayList<Entity>()

    /** Fades the music in, unless it is already on. */
    fun startMusic(app: App) {
        if (isMusicOn) return
        app.audio.music.play(sounds.music, fadeIn = Tuning.MUSIC_FADE)
        isMusicOn = true
    }

    override fun toString(): String =
        "Game(best=$bestScore, squished=$totalSquished, rounds=$roundsPlayed, autopilot=$isAutopilot)"
}

/** Why a round ended. */
enum class RoundEnd { TIME_UP, FOOD_EATEN }

/**
 * The state of one round, from the play scene's entry to the end screen: the food, the score, the
 * clock and the spawn timer. The play scene inserts a new one each time it enters, so every round
 * starts fresh, and reads it through [App.round].
 */
class Round(val game: Game, val food: List<Entity>) {
    var score = 0

    /** Seconds played; the clock stops while paused because the play scene's systems do. */
    var elapsed = 0f

    /** Seconds until the next bug comes. */
    var spawnTimer = Tuning.FIRST_SPAWN_DELAY

    /** Set once the time is up or the food is gone; the play scene then shows the end screen. */
    var endOrNull: RoundEnd? = null

    val secondsLeft: Float get() = (Tuning.ROUND_SECONDS - elapsed).coerceAtLeast(0f)

    /** How far into the round, from 0 at the start to 1 when the time is up. */
    val progress: Float get() = (elapsed / Tuning.ROUND_SECONDS).coerceIn(0f, 1f)

    override fun toString(): String = "Round(score=$score, elapsed=$elapsed, end=$endOrNull)"
}

/** The state of the whole process, inserted as a resource at startup. */
val App.game: Game get() = resources.get()

/** The state of the round being played, inserted as a resource each time the play scene enters. */
val App.round: Round get() = resources.get()

/** Returns a white disc [size] texels across with a soft edge. */
private fun discImage(size: Int): Image {
    val half = size / 2f
    return Image(size, size).apply { paint(this, floatArrayOf(half, half, half - 1f)) }
}

/**
 * Returns a white splat [size] texels across: a round blob with smaller drops around it, at
 * fixed angles so every run draws the same splat.
 */
private fun splatImage(size: Int): Image {
    val half = size / 2f
    val discs = FloatArray(3 * (1 + SPLAT_DROPS.size / 2))
    discs[0] = half
    discs[1] = half
    discs[2] = size * 0.3f
    for (i in 0 until SPLAT_DROPS.size / 2) {
        val angle = SPLAT_DROPS[2 * i] * (PI.toFloat() / 180f)
        val radius = SPLAT_DROPS[2 * i + 1] * size
        // Every drop touches the image's edge, so the larger ones sit closer to the blob.
        val distance = half - radius - 1f
        discs[3 * (i + 1)] = half + distance * cos(angle)
        discs[3 * (i + 1) + 1] = half + distance * sin(angle)
        discs[3 * (i + 1) + 2] = radius
    }
    return Image(size, size).apply { paint(this, discs) }
}

/** Angle in degrees and radius as a share of the image of each drop around the splat's blob. */
private val SPLAT_DROPS = floatArrayOf(10f, 0.09f, 75f, 0.06f, 130f, 0.11f, 200f, 0.07f, 250f, 0.1f, 315f, 0.08f, 345f, 0.05f)

/**
 * Paints white into [image] wherever it lies inside one of the discs in [discs], given as x, y and
 * radius triples, with alpha falling off over one texel at their edges.
 */
private fun paint(image: Image, discs: FloatArray) {
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            var coverage = 0f
            for (d in 0 until discs.size / 3) {
                val dx = x + 0.5f - discs[3 * d]
                val dy = y + 0.5f - discs[3 * d + 1]
                val edge = discs[3 * d + 2] - sqrt(dx * dx + dy * dy) + 0.5f
                coverage = maxOf(coverage, edge.coerceIn(0f, 1f))
            }
            if (coverage > 0f) image[x, y] = Color(1f, 1f, 1f, coverage)
        }
    }
}
