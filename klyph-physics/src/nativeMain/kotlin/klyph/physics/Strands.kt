package klyph.physics

import kotlin.math.sqrt

/**
 * A rope or a chain made by [PhysicsWorld.rope] or [PhysicsWorld.chain]: its particles in order from
 * one end to the other, the links between neighbours, and the bends at every particle but the ends
 * (empty when it has none). They are ordinary world particles and constraints; this just remembers
 * which ones.
 *
 * [toString] gives how many of each it has, for example `Strand(particles=11, links=10, bends=9)`.
 *
 * @sample samples.physics.StrandSamples.rope
 */
public class Strand internal constructor(
    /** The particles, from the start to the end. */
    public val particles: List<Particle>,
    /** The links between neighbours: the one from `particles[i]` to `particles[i + 1]` is `links[i]`. */
    public val links: List<Link>,
    /** The bends: the one at `particles[i + 1]` is `bends[i]`. */
    public val bends: List<Bend>,
) {
    /** The particle at the start. */
    public val first: Particle get() = particles.first()

    /** The particle at the end. */
    public val last: Particle get() = particles.last()

    /** Returns how long it is now in [world], along its particles. */
    public fun length(world: PhysicsWorld): Float {
        val ps = world.particles
        var sum = 0f
        for (i in 1 until particles.size) {
            val dx = ps.xOf(particles[i]) - ps.xOf(particles[i - 1])
            val dy = ps.yOf(particles[i]) - ps.yOf(particles[i - 1])
            sum += sqrt(dx * dx + dy * dy)
        }
        return sum
    }

    /** Returns how long it is at rest in [world]: the sum of its links' rest lengths. */
    public fun restLength(world: PhysicsWorld): Float {
        var sum = 0f
        for (link in links) sum += world.constraints.restLengthOf(link)
        return sum
    }

    override fun toString(): String = "Strand(particles=${particles.size}, links=${links.size}, bends=${bends.size})"
}

/**
 * A sheet made by [PhysicsWorld.cloth]: a grid of [columns] by [rows] particles, held by structural
 * links along its rows and columns, shear links across each square, and bends along its rows and
 * columns.
 *
 * [toString] gives its size, for example `Cloth(12x8)`.
 *
 * @sample samples.physics.StrandSamples.cloth
 */
public class Cloth internal constructor(
    /** Number of particles across. */
    public val columns: Int,
    /** Number of particles down. */
    public val rows: Int,
    /** The particles, row by row from the top-left: see [particleAt]. */
    public val particles: List<Particle>,
    /** Every link: structural ones first, then shear. */
    public val links: List<Link>,
    /** Every bend: along the rows, then along the columns. */
    public val bends: List<Bend>,
) {
    /**
     * Returns the particle at [column] and [row], counted from the top-left.
     *
     * @throws IndexOutOfBoundsException if the column or row is outside the sheet.
     */
    public fun particleAt(column: Int, row: Int): Particle {
        if (column !in 0 until columns || row !in 0 until rows) {
            throw IndexOutOfBoundsException("Cell ($column, $row) is outside the ${columns}x$rows cloth.")
        }
        return particles[row * columns + column]
    }

    override fun toString(): String = "Cloth(${columns}x$rows)"
}

/** Which ends of a rope or a chain are pinned where they start, so it hangs from them. */
public enum class PinnedEnds {
    /** Neither: it falls. */
    NONE,

    /** The start only: it hangs from there. */
    START,

    /** The end only. */
    END,

    /** Both: it hangs between them, like a bridge. */
    BOTH,
}

/**
 * Adds a rope from ([fromX], [fromY]) to ([toX], [toY]), cut into [segments] links, and returns it.
 *
 * It has `segments + 1` particles of [mass], held by rope constraints that pull but go slack when
 * pushed. With a [bendCompliance] it also resists folding (see [Constraints.bend]); without, it folds
 * anywhere. [pinned] says which ends are static, so it hangs from there. A link stretched past
 * [tearAt] times its rest length breaks (0, the default, never). Its particles are as round as
 * [radius], and collide as [collision] says. Pass the arguments after the ends by name.
 *
 * @throws IllegalArgumentException if [segments] is less than 1 or the two ends are the same point.
 *
 * @sample samples.physics.StrandSamples.rope
 */
public fun PhysicsWorld.rope(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    segments: Int,
    mass: Float = 1f,
    compliance: Float = 0f,
    bendCompliance: Float = Float.NaN,
    pinned: PinnedEnds = PinnedEnds.START,
    tearAt: Float = 0f,
    radius: Float = 0f,
    collision: ParticleCollision = ParticleCollision.SOLIDS,
    material: Material = Material.DEFAULT,
): Strand = strand(fromX, fromY, toX, toY, segments, mass, compliance, bendCompliance, pinned, tearAt, radius, collision, material, slack = true)

/**
 * Adds a chain from ([fromX], [fromY]) to ([toX], [toY]), cut into [segments] links, and returns it:
 * like [rope], but its links are rigid rods that push as well as pull, its particles are heavier and
 * round, and it bends a little, so a chain hangs in a smoother curve and does not bunch up.
 *
 * @throws IllegalArgumentException as [rope] does.
 *
 * @sample samples.physics.StrandSamples.chain
 */
public fun PhysicsWorld.chain(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    segments: Int,
    mass: Float = 2f,
    compliance: Float = 0f,
    bendCompliance: Float = 0.00001f,
    pinned: PinnedEnds = PinnedEnds.START,
    tearAt: Float = 0f,
    radius: Float = 2f,
    collision: ParticleCollision = ParticleCollision.SOLIDS,
    material: Material = Material.DEFAULT,
): Strand = strand(fromX, fromY, toX, toY, segments, mass, compliance, bendCompliance, pinned, tearAt, radius, collision, material, slack = false)

private fun PhysicsWorld.strand(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    segments: Int,
    mass: Float,
    compliance: Float,
    bendCompliance: Float,
    pinned: PinnedEnds,
    tearAt: Float,
    radius: Float,
    collision: ParticleCollision,
    material: Material,
    slack: Boolean,
): Strand {
    require(segments >= 1) { "Strand of $segments segments has fewer than 1." }
    require(fromX != toX || fromY != toY) { "Strand ends are both ($fromX, $fromY)." }
    val ps = particles
    val cs = constraints
    val pinStart = pinned == PinnedEnds.START || pinned == PinnedEnds.BOTH
    val pinEnd = pinned == PinnedEnds.END || pinned == PinnedEnds.BOTH
    val points = List(segments + 1) { i ->
        val t = i.toFloat() / segments
        val isPinned = (i == 0 && pinStart) || (i == segments && pinEnd)
        ps.add(
            fromX + (toX - fromX) * t,
            fromY + (toY - fromY) * t,
            mass = if (isPinned) Particles.STATIC else mass,
            radius = radius,
            collision = collision,
            material = material,
        )
    }
    val links = List(segments) { i ->
        val link = if (slack) cs.rope(points[i], points[i + 1], compliance) else cs.distance(points[i], points[i + 1], compliance)
        if (tearAt > 0f) cs.setTear(link, tearAt)
        link
    }
    val bends = if (bendCompliance.isNaN() || segments < 2) {
        emptyList()
    } else {
        List(segments - 1) { i -> cs.bend(points[i], points[i + 1], points[i + 2], bendCompliance) }
    }
    return Strand(points, links, bends)
}

/**
 * Adds a sheet of [columns] by [rows] particles [spacing] apart, its top-left particle at ([left],
 * [top]), and returns it.
 *
 * Structural links along the rows and columns keep it from stretching ([compliance]); shear links
 * across every square keep the squares square ([shearCompliance]); bends along the rows and columns
 * keep it from creasing ([bendCompliance], or none at all for `NaN`). The particles [pinned] returns
 * `true` for are static, by default the whole top row, so it hangs like a curtain. Links stretched
 * past [tearAt] times their rest length break, and a bend across a broken link goes with it, so it
 * tears cleanly. Pass the arguments after [top] by name.
 *
 * @throws IllegalArgumentException if the sheet is smaller than 2 by 2 or [spacing] is not more than 0.
 *
 * @sample samples.physics.StrandSamples.cloth
 */
public fun PhysicsWorld.cloth(
    left: Float,
    top: Float,
    columns: Int,
    rows: Int,
    spacing: Float,
    mass: Float = 1f,
    compliance: Float = 0f,
    shearCompliance: Float = 0.001f,
    bendCompliance: Float = 0.0001f,
    tearAt: Float = 0f,
    radius: Float = 0f,
    collision: ParticleCollision = ParticleCollision.SOLIDS,
    material: Material = Material.DEFAULT,
    pinned: (column: Int, row: Int) -> Boolean = { _, row -> row == 0 },
): Cloth {
    val cols = columns
    require(cols >= 2 && rows >= 2) { "Cloth of ${cols}x$rows particles is smaller than 2x2." }
    require(spacing > 0f) { "Cloth spacing $spacing is not more than 0." }
    val ps = particles
    val cs = constraints
    val grid = List(cols * rows) { k ->
        val col = k % cols
        val row = k / cols
        ps.add(
            left + col * spacing,
            top + row * spacing,
            mass = if (pinned(col, row)) Particles.STATIC else mass,
            radius = radius,
            collision = collision,
            material = material,
        )
    }
    fun at(col: Int, row: Int) = grid[row * cols + col]
    val links = ArrayList<Link>()
    fun link(a: Particle, b: Particle, c: Float) {
        val link = cs.distance(a, b, c)
        if (tearAt > 0f) cs.setTear(link, tearAt)
        links += link
    }
    for (row in 0 until rows) for (col in 0 until cols) {
        if (col + 1 < cols) link(at(col, row), at(col + 1, row), compliance)
        if (row + 1 < rows) link(at(col, row), at(col, row + 1), compliance)
    }
    for (row in 0 until rows - 1) for (col in 0 until cols - 1) {
        link(at(col, row), at(col + 1, row + 1), shearCompliance)
        link(at(col + 1, row), at(col, row + 1), shearCompliance)
    }
    val bends = ArrayList<Bend>()
    if (!bendCompliance.isNaN()) {
        for (row in 0 until rows) for (col in 1 until cols - 1) bends += cs.bend(at(col - 1, row), at(col, row), at(col + 1, row), bendCompliance)
        for (col in 0 until cols) for (row in 1 until rows - 1) bends += cs.bend(at(col, row - 1), at(col, row), at(col, row + 1), bendCompliance)
    }
    return Cloth(cols, rows, grid, links, bends)
}
