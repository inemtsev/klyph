package klyph.roller

import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.sin

// The level, as plain data: where the roofs and ledges are, and where everything on them starts.
// The engine has no tile maps or level format, so the level is Kotlin. x grows to the right and
// y down; a roof's top is the y its walkers stand at, and buildings reach down past the bottom of
// the world, so their sides are walls and the gaps between them are pits.

/**
 * Something the hero can stand on: a building's roof from [left] to [right] at [top], solid all the
 * way down, or a steel ledge, which is only solid from above so the hero can jump up through it.
 */
class Solid(val left: Float, val right: Float, val top: Float, val isLedge: Boolean) {
    override fun toString(): String = "Solid($left..$right at $top${if (isLedge) ", ledge" else ""})"
}

/** A walker or a flier going back and forth between [minX] and [maxX] at height [y], starting at [startX]. */
class Patrol(val minX: Float, val maxX: Float, val y: Float, val startX: Float = (minX + maxX) / 2) {
    override fun toString(): String = "Patrol($minX..$maxX at $y)"
}

/** The two posters a billboard can show. */
enum class Poster { FACE, CITY }

/** A billboard standing on the roof at [roof], centered on [x], showing [poster]. */
class Billboard(val x: Float, val roof: Float, val poster: Poster) {
    override fun toString(): String = "Billboard($x on $roof, $poster)"
}

/**
 * The one level of the game: eight rooftops with gaps between them and two steel ledges, rings in
 * lines and arcs, two hearts, blobs patrolling the roofs, helicopters over them and the arrow sign
 * at the end. Every list is built once.
 */
object RooftopRun {
    const val NAME = "Rooftop Run"

    /** The level's width; the camera never shows past it. */
    const val WIDTH = 7000f

    /** The buildings, left to right. Their order matters to the autopilot and the respawn. */
    val buildings: List<Solid> = listOf(
        Solid(left = -40f, right = 980f, top = 400f, isLedge = false),
        Solid(left = 1120f, right = 1840f, top = 360f, isLedge = false),
        Solid(left = 2000f, right = 2560f, top = 420f, isLedge = false),
        Solid(left = 2720f, right = 3540f, top = 380f, isLedge = false),
        Solid(left = 3680f, right = 4080f, top = 330f, isLedge = false),
        Solid(left = 4480f, right = 5380f, top = 400f, isLedge = false),
        Solid(left = 5540f, right = 6240f, top = 370f, isLedge = false),
        Solid(left = 6380f, right = WIDTH + 40f, top = 410f, isLedge = false),
    )

    val ledges: List<Solid> = listOf(
        Solid(left = 2160f, right = 2400f, top = 300f, isLedge = true),
        Solid(left = 4150f, right = 4390f, top = 360f, isLedge = true),
    )

    /** Everything solid: the buildings, then the ledges. */
    val solids: List<Solid> = buildings + ledges

    /** Where the hero starts, standing on the first roof. */
    val start: Vec2 = Vec2(Tuning.PLAYER_START_X, buildings[0].top)

    /** The arrow sign at the end: reaching its x completes the level. */
    const val SIGN_X = 6720f
    val signRoof: Float = buildings[7].top

    val rings: List<Vec2> = buildList {
        line(fromX = 480f, toX = 760f, y = 370f, count = 8)
        arc(fromX = 960f, toX = 1150f, baseY = 330f, height = 90f, count = 6)
        line(fromX = 1220f, toX = 1340f, y = 330f, count = 4)
        line(fromX = 2190f, toX = 2370f, y = 270f, count = 6)
        arc(fromX = 2540f, toX = 2740f, baseY = 370f, height = 100f, count = 6)
        line(fromX = 2780f, toX = 2860f, y = 350f, count = 3)
        arc(fromX = 3740f, toX = 4020f, baseY = 290f, height = 70f, count = 7)
        arc(fromX = 4080f, toX = 4300f, baseY = 290f, height = 80f, count = 6)
        line(fromX = 4560f, toX = 4680f, y = 370f, count = 4)
        arc(fromX = 5360f, toX = 5560f, baseY = 340f, height = 90f, count = 6)
        line(fromX = 6430f, toX = 6640f, y = 380f, count = 6)
    }

    val hearts: List<Vec2> = listOf(Vec2(3330f, 270f), Vec2(5120f, 300f))

    val blobs: List<Patrol> = listOf(
        Patrol(minX = 1380f, maxX = 1640f, y = buildings[1].top),
        Patrol(minX = 2880f, maxX = 3120f, y = buildings[3].top, startX = 3040f),
        Patrol(minX = 3220f, maxX = 3440f, y = buildings[3].top, startX = 3260f),
        Patrol(minX = 4720f, maxX = 4980f, y = buildings[5].top),
        Patrol(minX = 5760f, maxX = 6060f, y = buildings[6].top, startX = 5900f),
    )

    val helis: List<Patrol> = listOf(
        Patrol(minX = 2060f, maxX = 2500f, y = 190f),
        Patrol(minX = 4760f, maxX = 5200f, y = 215f, startX = 5000f),
        Patrol(minX = 5620f, maxX = 6000f, y = 200f, startX = 5700f),
    )

    val billboards: List<Billboard> = listOf(
        Billboard(x = 360f, roof = buildings[0].top, poster = Poster.FACE),
        Billboard(x = 1700f, roof = buildings[1].top, poster = Poster.CITY),
        Billboard(x = 3000f, roof = buildings[3].top, poster = Poster.FACE),
        Billboard(x = 4850f, roof = buildings[5].top, poster = Poster.CITY),
        Billboard(x = 6000f, roof = buildings[6].top, poster = Poster.FACE),
    )

    /** Potted plants, standing on the roofs: x and the roof's top. */
    val plants: List<Vec2> = listOf(
        Vec2(40f, buildings[0].top),
        Vec2(930f, buildings[0].top),
        Vec2(1800f, buildings[1].top),
        Vec2(2040f, buildings[2].top),
        Vec2(2520f, buildings[2].top),
        Vec2(3500f, buildings[3].top),
        Vec2(4520f, buildings[5].top),
        Vec2(5590f, buildings[6].top),
        Vec2(6830f, buildings[7].top),
        Vec2(6930f, buildings[7].top),
    )

    /** Air conditioners standing on the roofs: x and the roof's top. */
    val airConditioners: List<Vec2> = listOf(
        Vec2(820f, buildings[0].top),
        Vec2(2460f, buildings[2].top),
        Vec2(3420f, buildings[3].top),
        Vec2(3960f, buildings[4].top),
        Vec2(5300f, buildings[5].top),
        Vec2(6130f, buildings[6].top),
    )

    override fun toString(): String = "RooftopRun(${buildings.size} buildings, ${rings.size} rings)"
}

/** Adds [count] rings evenly along a straight line at [y] from [fromX] to [toX]. */
private fun MutableList<Vec2>.line(fromX: Float, toX: Float, y: Float, count: Int) {
    for (i in 0 until count) add(Vec2(fromX + (toX - fromX) * i / (count - 1), y))
}

/** Adds [count] rings along an arch from [fromX] to [toX] whose ends are at [baseY] and whose middle is [height] above them. */
private fun MutableList<Vec2>.arc(fromX: Float, toX: Float, baseY: Float, height: Float, count: Int) {
    for (i in 0 until count) {
        val t = i.toFloat() / (count - 1)
        add(Vec2(fromX + (toX - fromX) * t, baseY - height * sin(PI.toFloat() * t)))
    }
}
