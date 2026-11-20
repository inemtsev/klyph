package klyph.physics

import klyph.collision.CollisionLayers
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The soft bodies in a [PhysicsWorld]. A [SoftBody] handle names a group of the world's particles, a
 * ring and a core, held together by the world's constraints:
 * - an **edge** (distance) between each ring particle and the next: stiff, so the skin doesn't tear
 * - a **spoke** (distance) from each ring particle to the core: soft, so it squashes and springs back
 * - an **area** over the ring, so a squashed body bulges out elsewhere
 *
 * Its particles and constraints are ordinary ones: reach them with [particlesOf], [coreOf],
 * [edgeOf] and [spokeOf].
 *
 * [toString] gives how many soft bodies are alive, for example `SoftBodies(size=5)`.
 *
 * @sample samples.physics.SoftBodySamples.add
 */
public class SoftBodies internal constructor(private val world: PhysicsWorld) {
    private val slots = Slots()
    internal var start = IntArray(8)
        private set
    internal var ringCounts = IntArray(8)
        private set
    private var linkStart = IntArray(8)
    private var areaOf = IntArray(8)
    private var tunings = arrayOfNulls<SoftBodyTuning>(8)
    private var rings = arrayOfNulls<BodyRing>(8)
    private var selfCollision = BooleanArray(8)
    private var thickness = FloatArray(8)
    internal var particlePool = IntArray(64)
        private set
    private var particleUsed = 0
    private var linkPool = IntArray(64)
    private var linkUsed = 0

    init {
        world.particles.register(
            object : ParticleHolder {
                override fun letGo(p: Int) = Unit

                override fun letGoAll() = clear()
            },
        )
    }

    internal fun clear() {
        slots.clear()
        particleUsed = 0
        linkUsed = 0
    }

    /** Number of soft bodies alive. */
    public val size: Int get() = slots.count

    @PublishedApi
    internal val end: Int get() = slots.end

    /** Calls [action] with every soft body alive, in slot order. [action] may remove the body it is given, but must not add any. */
    public inline fun forEach(action: (SoftBody) -> Unit) {
        for (b in 0 until end) if (isAliveAt(b)) action(SoftBody(b))
    }

    /** Returns `true` if [body] is still in the world. */
    public fun isAlive(body: SoftBody): Boolean = slots.isAlive(body.index)

    internal fun isAlive(body: Int): Boolean = slots.isAlive(body)

    @PublishedApi
    internal fun isAliveAt(body: Int): Boolean = slots.isAlive(body)

    /**
     * Adds a body of [shape] with its middle at [centerX] and its bottom on [baseY], at rest, and
     * returns its handle. Each ring particle weighs [particleMass]; the core is [SoftBodyShape.coreMass]
     * times that. Pass the arguments after [baseY] by name.
     *
     * @sample samples.physics.SoftBodySamples.add
     */
    public fun add(
        shape: SoftBodyShape,
        centerX: Float,
        baseY: Float,
        tuning: SoftBodyTuning = SoftBodyTuning(),
        particleMass: Float = 1f,
        material: Material = Material.DEFAULT,
    ): SoftBody = SoftBody(addAt(shape, centerX, baseY, tuning, particleMass, material))

    internal fun addAt(
        shape: SoftBodyShape,
        centerX: Float,
        baseY: Float,
        tuning: SoftBodyTuning = SoftBodyTuning(),
        particleMass: Float = 1f,
        material: Material = Material.DEFAULT,
    ): Int {
        val n = shape.ringCount
        var minX = shape.ringX[0]
        var maxX = minX
        var maxY = shape.ringY[0]
        for (i in 1 until n) {
            minX = min(minX, shape.ringX[i])
            maxX = max(maxX, shape.ringX[i])
            maxY = max(maxY, shape.ringY[i])
        }
        val dx = centerX - (minX + maxX) / 2f
        val dy = baseY - maxY
        val body = slots.take()
        if (body == start.size) grow()
        ensurePools(n + 1, 2 * n)
        start[body] = particleUsed
        ringCounts[body] = n
        linkStart[body] = linkUsed
        tunings[body] = tuning
        if (rings[body] == null) rings[body] = BodyRing(body)
        val ps = world.particles
        for (i in 0 until n) particlePool[particleUsed + i] = ps.addAt(shape.ringX[i] + dx, shape.ringY[i] + dy, particleMass)
        val core = ps.addAt(shape.coreX + dx, shape.coreY + dy, particleMass * shape.coreMass)
        particlePool[particleUsed + n] = core
        for (i in 0..n) {
            val p = particlePool[particleUsed + i]
            ps.owner[p] = body
            ps.setMaterial(p, material)
        }
        selfCollision[body] = false
        var shortest = Float.MAX_VALUE
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = shape.ringX[j] - shape.ringX[i]
            val ey = shape.ringY[j] - shape.ringY[i]
            shortest = min(shortest, sqrt(ex * ex + ey * ey))
        }
        thickness[body] = shortest / 2f
        val cs = world.constraints
        for (i in 0 until n) {
            val a = particlePool[particleUsed + i]
            val b = particlePool[particleUsed + (i + 1) % n]
            linkPool[linkUsed + i] = cs.distance(a, b, tuning.edgeCompliance)
        }
        for (i in 0 until n) linkPool[linkUsed + n + i] = cs.distance(particlePool[particleUsed + i], core, tuning.spokeCompliance)
        areaOf[body] = cs.area(IntArray(n) { particlePool[particleUsed + it] }, tuning.areaCompliance)
        particleUsed += n + 1
        linkUsed += 2 * n
        return body
    }

    /**
     * Removes [body], its particles and its constraints. Its slot is reused; the room it took in the pools
     * is not. Removing one that is gone does nothing.
     */
    public fun remove(body: SoftBody) {
        remove(body.index)
    }

    internal fun remove(body: Int) {
        if (!isAlive(body)) return
        for (i in 0..ringCounts[body]) world.particles.removeAt(particle(body, i))
        slots.remove(body)
    }

    /** Returns whether [body]'s skin is kept from passing through itself: see [setSelfCollision]. */
    public fun selfCollisionOf(body: SoftBody): SelfCollision = if (selfCollision[body.index]) SelfCollision.COLLIDE else SelfCollision.PASS

    internal fun hasSelfCollision(body: Int): Boolean = selfCollision[body]

    /**
     * Puts every particle of [body] on [layers], colliding only with what is on a layer of [mask]: see
     * [PhysicsLayers].
     *
     * @throws IllegalArgumentException if [layers] is empty or holds [PhysicsLayers.SOLIDS].
     */
    public fun setLayers(body: SoftBody, layers: CollisionLayers, mask: CollisionLayers) {
        setFilter(body.index, layers.bits, mask.bits)
    }

    /** Returns the layers [body] is on: see [setLayers]. */
    public fun layersOf(body: SoftBody): CollisionLayers = CollisionLayers(category(body.index))

    /** Returns the layers [body] collides with: see [setLayers]. */
    public fun maskOf(body: SoftBody): CollisionLayers = CollisionLayers(mask(body.index))

    internal fun setFilter(body: Int, category: Int, mask: Int) {
        for (i in 0..ringCount(body)) world.particles.setFilter(particle(body, i), category, mask)
    }

    internal fun category(body: Int): Int = world.particles.category[core(body)]

    internal fun mask(body: Int): Int = world.particles.mask[core(body)]

    /**
     * Sets whether [body]'s skin is kept from passing through itself, as when it folds over. It is
     * [SelfCollision.PASS] by default: a body that does not fold does not need it, and it costs a check
     * of every ring particle against every edge.
     *
     * @sample samples.physics.SoftBodySamples.setSelfCollision
     */
    public fun setSelfCollision(body: SoftBody, selfCollision: SelfCollision) {
        setSelfCollision(body.index, selfCollision == SelfCollision.COLLIDE)
    }

    internal fun setSelfCollision(body: Int, on: Boolean) {
        selfCollision[body] = on
    }

    /** How close [body]'s skin may come to itself with [hasSelfCollision] on: half its shortest edge at rest. */
    internal fun selfThickness(body: Int): Float = thickness[body]

    /** Returns the particles of [body], its ring in order and then its core, as a new list. */
    public fun particlesOf(body: SoftBody): List<Particle> = List(ringCounts[body.index] + 1) { Particle(particle(body.index, it)) }

    internal fun ringCount(body: Int): Int = ringCounts[body]

    internal fun particle(body: Int, i: Int): Int = particlePool[start[body] + i]

    /** Returns [body]'s core: the particle in its middle. */
    public fun coreOf(body: SoftBody): Particle = Particle(core(body.index))

    internal fun core(body: Int): Int = particle(body, ringCounts[body])

    /** Returns how soft [body] was made. */
    public fun tuningOf(body: SoftBody): SoftBodyTuning = tunings[body.index]!!

    internal fun tuning(body: Int): SoftBodyTuning = tunings[body]!!

    /** Returns [body]'s outline, for [Outline] and for drawing. It is the same object every time and reads the particles as they are. */
    public fun ringOf(body: SoftBody): Ring = rings[body.index]!!

    internal fun ring(body: Int): Ring = rings[body]!!

    /**
     * Moves [body], at rest, so its middle is at [centerX] and its bottom on [baseY].
     *
     * @sample samples.physics.SoftBodySamples.placeAt
     */
    public fun placeAt(body: SoftBody, centerX: Float, baseY: Float) {
        placeAt(body.index, centerX, baseY)
    }

    internal fun placeAt(body: Int, centerX: Float, baseY: Float) {
        val dx = centerX - (minX(body) + maxX(body)) / 2f
        val dy = baseY - maxY(body)
        val ps = world.particles
        for (i in 0..ringCounts[body]) {
            val p = particle(body, i)
            ps.moveTo(p, ps.x(p) + dx, ps.y(p) + dy)
            world.setVelocity(p, 0f, 0f)
        }
    }

    /**
     * Turns [body], at rest, [radians] about its middle: clockwise on screen, where y grows down. A body
     * is always made upright; this is how to set one down on a slope, or on its side.
     */
    public fun rotate(body: SoftBody, radians: Float) {
        rotate(body.index, radians)
    }

    internal fun rotate(body: Int, radians: Float) {
        val cx = centerX(body)
        val cy = centerY(body)
        val c = cos(radians)
        val s = sin(radians)
        val ps = world.particles
        for (i in 0..ringCounts[body]) {
            val p = particle(body, i)
            val dx = ps.x(p) - cx
            val dy = ps.y(p) - cy
            ps.moveTo(p, cx + dx * c - dy * s, cy + dx * s + dy * c)
            world.setVelocity(p, 0f, 0f)
        }
    }

    /** Makes every particle of [body] grip and bounce like [material]. */
    public fun setMaterial(body: SoftBody, material: Material) {
        setMaterial(body.index, material)
    }

    internal fun setMaterial(body: Int, material: Material) {
        for (i in 0..ringCounts[body]) world.particles.setMaterial(particle(body, i), material)
    }

    /**
     * Adds ([vx], [vy]) units per second to the velocity of every particle of [body].
     *
     * @sample samples.physics.SoftBodySamples.addVelocity
     */
    public fun addVelocity(body: SoftBody, vx: Float, vy: Float) {
        addVelocity(body.index, vx, vy)
    }

    internal fun addVelocity(body: Int, vx: Float, vy: Float) {
        for (i in 0..ringCounts[body]) {
            val p = particle(body, i)
            world.setVelocity(p, world.velocityX(p) + vx, world.velocityY(p) + vy)
        }
    }

    /** Returns the mean position of [body]'s ring particles, across. */
    public fun centerXOf(body: SoftBody): Float = centerX(body.index)

    internal fun centerX(body: Int): Float = ringMean(body) { world.particles.x(it) }

    /** Returns the mean position of [body]'s ring particles, down. */
    public fun centerYOf(body: SoftBody): Float = centerY(body.index)

    internal fun centerY(body: Int): Float = ringMean(body) { world.particles.y(it) }

    /** Returns the mean velocity of [body]'s ring particles across, in units per second. */
    public fun meanVelocityXOf(body: SoftBody): Float = meanVelocityX(body.index)

    internal fun meanVelocityX(body: Int): Float = ringMean(body) { world.velocityX(it) }

    /** Returns the mean velocity of [body]'s ring particles down, in units per second. */
    public fun meanVelocityYOf(body: SoftBody): Float = meanVelocityY(body.index)

    internal fun meanVelocityY(body: Int): Float = ringMean(body) { world.velocityY(it) }

    /** Returns the left edge of [body]'s ring. */
    public fun minXOf(body: SoftBody): Float = minX(body.index)

    internal fun minX(body: Int): Float = ringExtreme(body, -1f) { world.particles.x(it) }

    /** Returns the right edge of [body]'s ring. */
    public fun maxXOf(body: SoftBody): Float = maxX(body.index)

    internal fun maxX(body: Int): Float = ringExtreme(body, 1f) { world.particles.x(it) }

    /** Returns the top edge of [body]'s ring. */
    public fun minYOf(body: SoftBody): Float = minY(body.index)

    internal fun minY(body: Int): Float = ringExtreme(body, -1f) { world.particles.y(it) }

    /** Returns the bottom edge of [body]'s ring. */
    public fun maxYOf(body: SoftBody): Float = maxY(body.index)

    internal fun maxY(body: Int): Float = ringExtreme(body, 1f) { world.particles.y(it) }

    /** Returns the area inside [body]'s ring, in units squared. */
    public fun areaOf(body: SoftBody): Float = area(body.index)

    internal fun area(body: Int): Float = world.constraints.currentArea(areaOf[body])

    /** Returns the longest edge of [body]'s ring as a multiple of its rest length: 1 at rest, large when it is tearing. */
    public fun maxEdgeStretchOf(body: SoftBody): Float = maxEdgeStretch(body.index)

    internal fun maxEdgeStretch(body: Int): Float {
        var worst = 0f
        for (i in 0 until ringCounts[body]) worst = max(worst, 1f + world.constraints.strain(linkPool[linkStart[body] + i]))
        return worst
    }

    /** Returns `true` if every coordinate of [body]'s particles is a real number. */
    public fun isFinite(body: SoftBody): Boolean = isFinite(body.index)

    internal fun isFinite(body: Int): Boolean {
        val ps = world.particles
        for (i in 0..ringCounts[body]) {
            val p = particle(body, i)
            if (!ps.x(p).isFinite() || !ps.y(p).isFinite()) return false
        }
        return true
    }

    /** Returns `true` if any particle of [body] stood on a floor during the last step. */
    public fun isOnFloor(body: SoftBody): Boolean = isOnFloor(body.index)

    internal fun isOnFloor(body: Int): Boolean {
        for (i in 0..ringCounts[body]) if (world.events.isOnFloor(particle(body, i))) return true
        return false
    }

    /** Returns how many particles of [body] stood on a floor during the last step. */
    public fun floorContactCountOf(body: SoftBody): Int = floorContactCount(body.index)

    internal fun floorContactCount(body: Int): Int {
        var n = 0
        for (i in 0..ringCounts[body]) if (world.events.isOnFloor(particle(body, i))) n++
        return n
    }

    /**
     * Returns the tags of every floor a particle of [body] stood on during the last step, together;
     * [SolidTags.NONE] for none.
     *
     * @sample samples.physics.SoftBodySamples.floorTagsOf
     */
    public fun floorTagsOf(body: SoftBody): SolidTags {
        var tags = 0
        for (i in 0..ringCounts[body.index]) tags = tags or world.particles.floorTag[particle(body.index, i)]
        return SolidTags(tags)
    }

    internal fun touched(body: Int, tag: Int): Boolean {
        for (i in 0..ringCounts[body]) if (world.particles.floorTag[particle(body, i)] and tag != 0) return true
        return false
    }

    /**
     * Returns the edge link of [body] from ring point [index] to the next.
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until` the ring's size.
     */
    public fun edgeOf(body: SoftBody, index: Int): Link {
        checkRingIndex(body, index)
        return Link(edge(body.index, index))
    }

    internal fun edge(body: Int, i: Int): Int = linkPool[linkStart[body] + i]

    /**
     * Returns the spoke link of [body] from ring point [index] to the core.
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until` the ring's size.
     */
    public fun spokeOf(body: SoftBody, index: Int): Link {
        checkRingIndex(body, index)
        return Link(spoke(body.index, index))
    }

    private fun checkRingIndex(body: SoftBody, index: Int) {
        val n = ringCounts[body.index]
        if (index !in 0 until n) throw IndexOutOfBoundsException("Index $index is out of bounds for size $n.")
    }

    override fun toString(): String = "SoftBodies(size=$size)"

    internal fun spoke(body: Int, i: Int): Int = linkPool[linkStart[body] + ringCounts[body] + i]

    private inline fun ringMean(body: Int, value: (Int) -> Float): Float {
        var sum = 0f
        val n = ringCounts[body]
        for (i in 0 until n) sum += value(particle(body, i))
        return sum / n
    }

    private inline fun ringExtreme(body: Int, direction: Float, value: (Int) -> Float): Float {
        var best = value(particle(body, 0))
        for (i in 1 until ringCounts[body]) {
            val v = value(particle(body, i))
            if (v * direction > best * direction) best = v
        }
        return best
    }

    private fun ensurePools(particles: Int, links: Int) {
        if (particleUsed + particles > particlePool.size) particlePool = particlePool.copyOf(max(particlePool.size * 2, particleUsed + particles))
        if (linkUsed + links > linkPool.size) linkPool = linkPool.copyOf(max(linkPool.size * 2, linkUsed + links))
    }

    private fun grow() {
        val size = start.size * 2
        start = start.copyOf(size)
        ringCounts = ringCounts.copyOf(size)
        linkStart = linkStart.copyOf(size)
        areaOf = areaOf.copyOf(size)
        tunings = tunings.copyOf(size)
        rings = rings.copyOf(size)
        selfCollision = selfCollision.copyOf(size)
        thickness = thickness.copyOf(size)
    }

    private inner class BodyRing(private val body: Int) : Ring {
        override val size: Int get() = ringCounts[body]

        override fun xAt(index: Int): Float = world.particles.x(particle(body, index))

        override fun yAt(index: Int): Float = world.particles.y(particle(body, index))

        override fun toString(): String = "Ring(size=$size)"
    }
}
