package klyph.physics

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The rules that hold particles together, solved the XPBD way. Each one has a compliance: how far it
 * gives per unit of force, in world units. 0 is rigid. Because each constraint also remembers how hard
 * it has pushed this substep (its lambda), a compliance means the same softness however many substeps
 * and iterations the world runs.
 *
 * Five kinds, each with its own handle: a [Link] for the first two, a [Pin], an [Area] and a [Bend]:
 * - **distance**: keeps two particles [rest] apart, pushing and pulling
 * - **rope**: the same, but only pulls, so it can go slack
 * - **pin**: holds a particle at a point in the world, which can move (that's how dragging works)
 * - **area**: keeps the area inside a ring of particles, so a squashed body bulges out elsewhere
 * - **bend**: keeps the angle three particles make at the middle one, so a rope or a sheet resists folding
 *
 * A constraint on a particle that has been removed is skipped. Removed constraints' slots are reused.
 *
 * [toString] gives how many of each there are, for example `Constraints(links=40, pins=1, areas=0, bends=38)`.
 *
 * @sample samples.physics.ConstraintSamples.distance
 */
public class Constraints internal constructor(private val particles: Particles) {
    init {
        particles.register(
            object : ParticleHolder {
                override fun letGo(p: Int) = forgetParticle(p)
            },
        )
    }

    // Distances and ropes.
    private var linkA = IntArray(16)
    private var linkB = IntArray(16)
    private var linkRest = FloatArray(16)
    private var linkCompliance = FloatArray(16)
    private var linkLambda = FloatArray(16)
    private var linkFlags = IntArray(16)
    private var linkTear = FloatArray(16)
    private val links = Slots()

    // Pins.
    private var pinParticle = IntArray(8)
    private var pinX = FloatArray(8)
    private var pinY = FloatArray(8)
    private var pinCompliance = FloatArray(8)
    private var pinLambda = FloatArray(8)
    private val pins = Slots()

    // Areas: each one is a run of `areaLength` particles in `ringParticles`, starting at `areaStart`.
    private var areaStart = IntArray(4)
    private var areaLength = IntArray(4)
    private var areaRest = FloatArray(4)
    private var areaCompliance = FloatArray(4)
    private var areaLambda = FloatArray(4)
    private var areaSign = FloatArray(4)
    private val areas = Slots()
    private var ringParticles = IntArray(64)
    private var ringUsed = 0
    private var scratchX = FloatArray(32)
    private var scratchY = FloatArray(32)

    // Bends: the angle at `bendB` between `bendA` and `bendC`.
    private var bendA = IntArray(16)
    private var bendB = IntArray(16)
    private var bendC = IntArray(16)
    private var bendRest = FloatArray(16)
    private var bendCompliance = FloatArray(16)
    private var bendLambda = FloatArray(16)
    private val bends = Slots()

    /** Number of links torn since the world began: see [setTear]. */
    public var tornCount: Int = 0
        private set

    /** Number of distance and rope constraints alive. */
    public val linkCount: Int get() = links.count

    /** Number of pins. */
    public val pinCount: Int get() = pins.count

    /** Number of area constraints. */
    public val areaCount: Int get() = areas.count

    /** Number of bends. */
    public val bendCount: Int get() = bends.count

    /** Calls [action] with every distance and rope constraint alive, in slot order. [action] may remove the one it is given. */
    public inline fun forEachLink(action: (Link) -> Unit) {
        for (c in 0 until linkSlotEnd) if (isLinkAlive(c)) action(Link(c))
    }

    /** Calls [action] with every pin, in slot order. [action] may remove the one it is given. */
    public inline fun forEachPin(action: (Pin) -> Unit) {
        for (c in 0 until pinSlotEnd) if (isPinAlive(c)) action(Pin(c))
    }

    /** Calls [action] with every area constraint, in slot order. [action] may remove the one it is given. */
    public inline fun forEachArea(action: (Area) -> Unit) {
        for (c in 0 until areaSlotEnd) if (isAreaAlive(c)) action(Area(c))
    }

    /** Calls [action] with every bend, in slot order. [action] may remove the one it is given. */
    public inline fun forEachBend(action: (Bend) -> Unit) {
        for (c in 0 until bendSlotEnd) if (isBendAlive(c)) action(Bend(c))
    }

    /**
     * Keeps particles [a] and [b] [rest] apart, pushing and pulling, and returns its handle. Without a
     * [rest], it is as far apart as they are now. [compliance] is how far it gives per unit of force; 0
     * is rigid.
     *
     * @throws IllegalArgumentException if a particle is gone, [a] is [b], [compliance] is negative or not
     * finite, or the rest length is not more than 0.
     *
     * @sample samples.physics.ConstraintSamples.distance
     */
    public fun distance(a: Particle, b: Particle, compliance: Float = 0f, rest: Float = Float.NaN): Link =
        Link(distance(a.index, b.index, compliance, rest))

    internal fun distance(a: Int, b: Int, compliance: Float = 0f, rest: Float = Float.NaN): Int =
        addLink(a, b, compliance, rest, 0)

    /**
     * Keeps particles [a] and [b] at most [rest] apart, and returns its handle: like [distance], but it
     * only pulls, so closer than [rest] it goes slack.
     *
     * @throws IllegalArgumentException as [distance] does.
     *
     * @sample samples.physics.ConstraintSamples.rope
     */
    public fun rope(a: Particle, b: Particle, compliance: Float = 0f, rest: Float = Float.NaN): Link =
        Link(rope(a.index, b.index, compliance, rest))

    internal fun rope(a: Int, b: Int, compliance: Float = 0f, rest: Float = Float.NaN): Int =
        addLink(a, b, compliance, rest, ROPE)

    /** Returns `true` if [link] is a rope: it only pulls, and goes slack when its ends come closer. */
    public fun isRope(link: Link): Boolean = isRope(link.index)

    internal fun isRope(link: Int): Boolean = linkFlags[link] and ROPE != 0

    /** Returns [link]'s first particle. */
    public fun firstParticleOf(link: Link): Particle = Particle(linkA[link.index])

    internal fun linkA(link: Int): Int = linkA[link]

    /** Returns [link]'s second particle. */
    public fun secondParticleOf(link: Link): Particle = Particle(linkB[link.index])

    internal fun linkB(link: Int): Int = linkB[link]

    /** Returns how long [link] wants to be. */
    public fun restLengthOf(link: Link): Float = linkRest[link.index]

    internal fun restLength(link: Int): Float = linkRest[link]

    /** Returns how far [link] is from its rest length right now, as a share of it: 0 at rest, 0.1 stretched by a tenth. */
    public fun strainOf(link: Link): Float = strain(link.index)

    internal fun strain(link: Int): Float = currentDistance(linkA[link], linkB[link]) / linkRest[link] - 1f

    @PublishedApi

    internal fun isLinkAlive(link: Int): Boolean = links.isAlive(link)

    @PublishedApi
    internal val linkSlotEnd: Int get() = links.end

    internal val linkEnd: Int get() = links.end

    internal fun removeLink(link: Int) {
        links.remove(link)
    }

    /**
     * Makes [link] break once it is stretched past [stretch] times its rest length, at the end of a
     * step; 0 never breaks it. A bend across the two particles it joined breaks with it, so a torn rope
     * or cloth comes apart cleanly instead of hanging on by its bends.
     *
     * @throws IllegalArgumentException if [stretch] is neither 0 nor more than 1.
     *
     * @sample samples.physics.ConstraintSamples.setTear
     */
    public fun setTear(link: Link, stretch: Float) {
        setTear(link.index, stretch)
    }

    internal fun setTear(link: Int, stretch: Float) {
        require(stretch == 0f || stretch > 1f) { "Tear stretch $stretch is neither 0 for never nor more than 1." }
        linkTear[link] = stretch
    }

    /** Returns the stretch [link] breaks at, or 0 when it never does. */
    public fun tearOf(link: Link): Float = linkTear[link.index]

    internal fun tearAt(link: Int): Float = linkTear[link]

    /**
     * Holds [particle] at ([x], [y]), by default where it is now, and returns the pin's handle.
     *
     * @throws IllegalArgumentException if [particle] is gone or [compliance] is negative or not finite.
     *
     * @sample samples.physics.ConstraintSamples.pin
     */
    public fun pin(particle: Particle, x: Float = particles.xOf(particle), y: Float = particles.yOf(particle), compliance: Float = 0f): Pin =
        Pin(pin(particle.index, x, y, compliance))

    internal fun pin(p: Int, x: Float = particles.x[p], y: Float = particles.y[p], compliance: Float = 0f): Int {
        requireParticle(p)
        requireCompliance(compliance)
        val pin = pins.take()
        if (pin == pinParticle.size) growPins()
        pinParticle[pin] = p
        pinX[pin] = x
        pinY[pin] = y
        pinCompliance[pin] = compliance
        pinLambda[pin] = 0f
        return pin
    }

    /**
     * Moves the point [pin] holds its particle at to ([x], [y]).
     *
     * @sample samples.physics.ConstraintSamples.pin
     */
    public fun movePin(pin: Pin, x: Float, y: Float) {
        movePin(pin.index, x, y)
    }

    internal fun movePin(pin: Int, x: Float, y: Float) {
        pinX[pin] = x
        pinY[pin] = y
    }

    /** Returns the particle [pin] holds. */
    public fun particleOf(pin: Pin): Particle = Particle(pinParticle[pin.index])

    internal fun pinnedParticle(pin: Int): Int = pinParticle[pin]

    /** Returns where [pin] holds its particle, across. */
    public fun xOf(pin: Pin): Float = pinX[pin.index]

    internal fun pinX(pin: Int): Float = pinX[pin]

    /** Returns where [pin] holds its particle, down. */
    public fun yOf(pin: Pin): Float = pinY[pin.index]

    internal fun pinY(pin: Int): Float = pinY[pin]

    @PublishedApi

    internal fun isPinAlive(pin: Int): Boolean = pins.isAlive(pin)

    internal fun removePin(pin: Int) {
        pins.remove(pin)
    }

    @PublishedApi
    internal val pinSlotEnd: Int get() = pins.end

    internal val pinEnd: Int get() = pins.end

    /**
     * Keeps the area inside the closed ring of particles [ring], in order, at [rest], and returns its
     * handle. Without a [rest], it is the area the ring has now. The list is copied.
     *
     * @throws IllegalArgumentException if [ring] has fewer than 3 particles, one of them is gone, or
     * [compliance] is negative or not finite.
     *
     * @sample samples.physics.ConstraintSamples.area
     */
    public fun area(ring: List<Particle>, compliance: Float = 0f, rest: Float = Float.NaN): Area =
        Area(area(IntArray(ring.size) { ring[it].index }, compliance, rest))

    internal fun area(ring: IntArray, compliance: Float = 0f, rest: Float = Float.NaN): Int {
        require(ring.size >= 3) { "Area ring of ${ring.size} particles has fewer than 3." }
        for (p in ring) requireParticle(p)
        requireCompliance(compliance)
        val area = areas.take()
        if (area == areaStart.size) growAreas()
        if (ringUsed + ring.size > ringParticles.size) ringParticles = ringParticles.copyOf(maxOf(ringParticles.size * 2, ringUsed + ring.size))
        ring.copyInto(ringParticles, ringUsed)
        areaStart[area] = ringUsed
        areaLength[area] = ring.size
        ringUsed += ring.size
        if (ring.size > scratchX.size) {
            scratchX = FloatArray(ring.size)
            scratchY = FloatArray(ring.size)
        }
        val signed = signedArea(area)
        areaSign[area] = if (signed < 0f) -1f else 1f
        areaRest[area] = if (rest.isNaN()) signed * areaSign[area] else rest
        areaCompliance[area] = compliance
        areaLambda[area] = 0f
        return area
    }

    /** Returns the area inside [area]'s ring right now, in units squared. */
    public fun currentAreaOf(area: Area): Float = currentArea(area.index)

    internal fun currentArea(area: Int): Float = signedArea(area) * areaSign[area]

    /** Returns the area [area] wants its ring to cover, in units squared. */
    public fun restAreaOf(area: Area): Float = areaRest[area.index]

    internal fun restArea(area: Int): Float = areaRest[area]

    @PublishedApi

    internal fun isAreaAlive(area: Int): Boolean = areas.isAlive(area)

    @PublishedApi
    internal val areaSlotEnd: Int get() = areas.end

    internal val areaEnd: Int get() = areas.end

    /** Returns the particles of [area]'s ring, in the order they were given, as a new list. */
    public fun ringOf(area: Area): List<Particle> = List(areaLength[area.index]) { Particle(ringParticles[areaStart[area.index] + it]) }

    internal fun ringSize(area: Int): Int = areaLength[area]

    internal fun ringParticle(area: Int, i: Int): Int = ringParticles[areaStart[area] + i]

    internal fun ringContains(area: Int, p: Int): Boolean {
        val start = areaStart[area]
        for (k in 0 until areaLength[area]) if (ringParticles[start + k] == p) return true
        return false
    }

    internal fun removeArea(area: Int) {
        areas.remove(area)
    }

    /**
     * Keeps the angle at particle [b], from [a] through [b] to [c], at [rest], and returns its handle.
     *
     * The angle is how far the line turns at [b]: 0 is straight on, and it is signed, positive turning
     * clockwise on screen (y grows down), so a bend can be held curled one way. Without a [rest], it is
     * the angle they make now. Its [compliance] is per radian: how far it turns per unit of torque.
     *
     * @throws IllegalArgumentException if a particle is gone, two of them are the same, or [compliance]
     * is negative or not finite.
     *
     * @sample samples.physics.ConstraintSamples.bend
     */
    public fun bend(a: Particle, b: Particle, c: Particle, compliance: Float = 0f, rest: Float = Float.NaN): Bend =
        Bend(bend(a.index, b.index, c.index, compliance, rest))

    internal fun bend(a: Int, b: Int, c: Int, compliance: Float = 0f, rest: Float = Float.NaN): Int {
        requireParticle(a)
        requireParticle(b)
        requireParticle(c)
        require(a != b && b != c && a != c) { "Bend particles ${Particle(a)}, ${Particle(b)} and ${Particle(c)} are not three different ones." }
        requireCompliance(compliance)
        val bend = bends.take()
        if (bend == bendA.size) growBends()
        bendA[bend] = a
        bendB[bend] = b
        bendC[bend] = c
        bendRest[bend] = if (rest.isNaN()) angle(a, b, c) else wrap(rest)
        bendCompliance[bend] = compliance
        bendLambda[bend] = 0f
        return bend
    }

    /** Returns the angle [bend]'s line turns at its middle particle right now, in radians from -π to π. */
    public fun angleOf(bend: Bend): Float = bendAngle(bend.index)

    internal fun bendAngle(bend: Int): Float = angle(bendA[bend], bendB[bend], bendC[bend])

    /** Returns the angle [bend] wants at its middle particle, in radians. */
    public fun restAngleOf(bend: Bend): Float = bendRest[bend.index]

    internal fun restAngle(bend: Int): Float = bendRest[bend]

    /** Returns [bend]'s three particles, first, middle and last, as a new list. */
    public fun particlesOf(bend: Bend): List<Particle> =
        listOf(Particle(bendA[bend.index]), Particle(bendB[bend.index]), Particle(bendC[bend.index]))

    internal fun bendA(bend: Int): Int = bendA[bend]

    internal fun bendB(bend: Int): Int = bendB[bend]

    internal fun bendC(bend: Int): Int = bendC[bend]

    @PublishedApi

    internal fun isBendAlive(bend: Int): Boolean = bends.isAlive(bend)

    @PublishedApi
    internal val bendSlotEnd: Int get() = bends.end

    internal val bendEnd: Int get() = bends.end

    internal fun removeBend(bend: Int) {
        bends.remove(bend)
    }

    /** Returns `true` if [link] is still there: not removed and not torn. */
    public fun isAlive(link: Link): Boolean = links.isAlive(link.index)

    /** Returns `true` if [pin] is still there. */
    public fun isAlive(pin: Pin): Boolean = pins.isAlive(pin.index)

    /** Returns `true` if [area] is still there. */
    public fun isAlive(area: Area): Boolean = areas.isAlive(area.index)

    /** Returns `true` if [bend] is still there: not removed, and not broken with a torn link. */
    public fun isAlive(bend: Bend): Boolean = bends.isAlive(bend.index)

    /** Removes [link]. Removing one that is already gone does nothing. */
    public fun remove(link: Link) {
        links.remove(link.index)
    }

    /** Removes [pin], letting its particle go. Removing one that is already gone does nothing. */
    public fun remove(pin: Pin) {
        pins.remove(pin.index)
    }

    /** Removes [area]. Its slot is reused, but the room its ring took is not. Removing one that is already gone does nothing. */
    public fun remove(area: Area) {
        areas.remove(area.index)
    }

    /** Removes [bend]. Removing one that is already gone does nothing. */
    public fun remove(bend: Bend) {
        bends.remove(bend.index)
    }

    override fun toString(): String = "Constraints(links=$linkCount, pins=$pinCount, areas=$areaCount, bends=$bendCount)"

    /** Removes every constraint. */
    public fun clear() {
        links.clear()
        pins.clear()
        areas.clear()
        bends.clear()
        ringUsed = 0
    }

    /** Forgets how hard every constraint has pushed: the start of a substep. */
    /** Removes every link, pin, area and bend that holds particle [p], so a particle that takes its slot next inherits none. */
    internal fun forgetParticle(p: Int) {
        forgetLinks(p)
        forgetPins(p)
        forgetAreas(p)
        forgetBends(p)
    }

    private fun forgetLinks(p: Int) {
        for (link in 0 until linkEnd) {
            if (isLinkAlive(link) && (linkA(link) == p || linkB(link) == p)) removeLink(link)
        }
    }

    private fun forgetPins(p: Int) {
        for (pin in 0 until pinEnd) if (isPinAlive(pin) && pinnedParticle(pin) == p) removePin(pin)
    }

    private fun forgetAreas(p: Int) {
        for (area in 0 until areaEnd) if (isAreaAlive(area) && ringContains(area, p)) removeArea(area)
    }

    private fun forgetBends(p: Int) {
        for (bend in 0 until bendEnd) if (isBendAlive(bend) && bendHolds(bend, p)) removeBend(bend)
    }

    private fun bendHolds(bend: Int, p: Int): Boolean = bendA(bend) == p || bendB(bend) == p || bendC(bend) == p

    internal fun beginSubstep() {
        linkLambda.fill(0f, 0, links.end)
        pinLambda.fill(0f, 0, pins.end)
        areaLambda.fill(0f, 0, areas.end)
        bendLambda.fill(0f, 0, bends.end)
    }

    /** Breaks every link stretched past its tear limit, and the bends across it. Once a step. */
    internal fun tear() {
        for (link in 0 until links.end) {
            val limit = linkTear[link]
            if (limit == 0f || !links.isAlive(link)) continue
            val a = linkA[link]
            val b = linkB[link]
            if (!particles.isAlive(a) || !particles.isAlive(b)) continue
            if (currentDistance(a, b) <= limit * linkRest[link]) continue
            links.remove(link)
            tornCount++
            for (bend in 0 until bends.end) {
                if (!bends.isAlive(bend)) continue
                val m = bendB[bend]
                if ((m == a || m == b) && (bendA[bend] == a || bendA[bend] == b || bendC[bend] == a || bendC[bend] == b)) bends.remove(bend)
            }
        }
    }

    /** One pass over every constraint, for a substep of [h] seconds. */
    internal fun solve(h: Float) {
        val hh = h * h
        for (c in 0 until links.end) if (links.isAlive(c)) solveLink(c, hh)
        for (c in 0 until pins.end) if (pins.isAlive(c)) solvePin(c, hh)
        for (c in 0 until areas.end) if (areas.isAlive(c)) solveArea(c, hh)
        for (c in 0 until bends.end) if (bends.isAlive(c)) solveBend(c, hh)
    }

    private fun solveLink(c: Int, hh: Float) {
        val a = linkA[c]
        val b = linkB[c]
        val ps = particles
        if (ps.flags[a] and Particles.ALIVE == 0 || ps.flags[b] and Particles.ALIVE == 0) return
        val wa = ps.invMass[a]
        val wb = ps.invMass[b]
        if (wa + wb == 0f) return
        val dx = ps.x[a] - ps.x[b]
        val dy = ps.y[a] - ps.y[b]
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-6f) return
        val error = length - linkRest[c]
        if (error < 0f && linkFlags[c] and ROPE != 0) return
        val softness = linkCompliance[c] / hh
        val change = (-error - softness * linkLambda[c]) / (wa + wb + softness)
        linkLambda[c] += change
        val nx = dx / length
        val ny = dy / length
        ps.x[a] += wa * change * nx
        ps.y[a] += wa * change * ny
        ps.x[b] -= wb * change * nx
        ps.y[b] -= wb * change * ny
    }

    private fun solvePin(c: Int, hh: Float) {
        val p = pinParticle[c]
        val ps = particles
        if (ps.flags[p] and Particles.ALIVE == 0) return
        val w = ps.invMass[p]
        if (w == 0f) return
        val dx = ps.x[p] - pinX[c]
        val dy = ps.y[p] - pinY[c]
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-6f) return
        val softness = pinCompliance[c] / hh
        val change = (-length - softness * pinLambda[c]) / (w + softness)
        pinLambda[c] += change
        ps.x[p] += w * change * dx / length
        ps.y[p] += w * change * dy / length
    }

    private fun solveArea(c: Int, hh: Float) {
        val start = areaStart[c]
        val n = areaLength[c]
        val ps = particles
        for (k in 0 until n) if (ps.flags[ringParticles[start + k]] and Particles.ALIVE == 0) return
        val sign = areaSign[c]
        val error = signedArea(c) * sign - areaRest[c]
        // Every gradient is worked out before any particle moves, so the order around the ring doesn't matter.
        var weighted = 0f
        for (k in 0 until n) {
            val prev = ringParticles[start + (k + n - 1) % n]
            val next = ringParticles[start + (k + 1) % n]
            val gx = sign * 0.5f * (ps.y[next] - ps.y[prev])
            val gy = sign * 0.5f * (ps.x[prev] - ps.x[next])
            scratchX[k] = gx
            scratchY[k] = gy
            weighted += ps.invMass[ringParticles[start + k]] * (gx * gx + gy * gy)
        }
        val softness = areaCompliance[c] / hh
        if (weighted + softness == 0f) return
        val change = (-error - softness * areaLambda[c]) / (weighted + softness)
        areaLambda[c] += change
        for (k in 0 until n) {
            val p = ringParticles[start + k]
            val w = ps.invMass[p]
            ps.x[p] += w * change * scratchX[k]
            ps.y[p] += w * change * scratchY[k]
        }
    }

    /**
     * The angle at b is θ = atan2(u × v, u · v) for u = b - a and v = c - b. Its gradient: turning u
     * by moving a changes θ by (-u.y, u.x) / |u|² per unit, turning v by moving c by (-v.y, v.x) / |v|²,
     * and b takes minus the sum, so the three moves never shift or spin the whole.
     */
    private fun solveBend(c: Int, hh: Float) {
        val a = bendA[c]
        val b = bendB[c]
        val d = bendC[c]
        val ps = particles
        if (ps.flags[a] and Particles.ALIVE == 0 || ps.flags[b] and Particles.ALIVE == 0 || ps.flags[d] and Particles.ALIVE == 0) return
        val ux = ps.x[b] - ps.x[a]
        val uy = ps.y[b] - ps.y[a]
        val vx = ps.x[d] - ps.x[b]
        val vy = ps.y[d] - ps.y[b]
        val uu = ux * ux + uy * uy
        val vv = vx * vx + vy * vy
        if (uu < 1e-8f || vv < 1e-8f) return
        val error = wrap(atan2(ux * vy - uy * vx, ux * vx + uy * vy) - bendRest[c])
        val gax = -uy / uu
        val gay = ux / uu
        val gcx = -vy / vv
        val gcy = vx / vv
        val gbx = -gax - gcx
        val gby = -gay - gcy
        val wa = ps.invMass[a]
        val wb = ps.invMass[b]
        val wc = ps.invMass[d]
        val weighted = wa * (gax * gax + gay * gay) + wb * (gbx * gbx + gby * gby) + wc * (gcx * gcx + gcy * gcy)
        val softness = bendCompliance[c] / hh
        if (weighted + softness == 0f) return
        val change = (-error - softness * bendLambda[c]) / (weighted + softness)
        bendLambda[c] += change
        ps.x[a] += wa * change * gax
        ps.y[a] += wa * change * gay
        ps.x[b] += wb * change * gbx
        ps.y[b] += wb * change * gby
        ps.x[d] += wc * change * gcx
        ps.y[d] += wc * change * gcy
    }

    private fun angle(a: Int, b: Int, c: Int): Float {
        val ps = particles
        val ux = ps.x(b) - ps.x(a)
        val uy = ps.y(b) - ps.y(a)
        val vx = ps.x(c) - ps.x(b)
        val vy = ps.y(c) - ps.y(b)
        return atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    }

    /** [angle] brought into -π..π, so a bend always turns back the short way. */
    private fun wrap(angle: Float): Float {
        var x = angle
        while (x > PI.toFloat()) x -= 2f * PI.toFloat()
        while (x < -PI.toFloat()) x += 2f * PI.toFloat()
        return x
    }

    private fun signedArea(area: Int): Float {
        val start = areaStart[area]
        val n = areaLength[area]
        val ps = particles
        var sum = 0f
        for (k in 0 until n) {
            val i = ringParticles[start + k]
            val j = ringParticles[start + (k + 1) % n]
            sum += ps.x[i] * ps.y[j] - ps.x[j] * ps.y[i]
        }
        return sum / 2f
    }

    private fun currentDistance(a: Int, b: Int): Float {
        val dx = particles.x(a) - particles.x(b)
        val dy = particles.y(a) - particles.y(b)
        return sqrt(dx * dx + dy * dy)
    }

    private fun addLink(a: Int, b: Int, compliance: Float, rest: Float, flags: Int): Int {
        requireParticle(a)
        requireParticle(b)
        require(a != b) { "Link particle ${Particle(a)} is both of its ends." }
        val length = if (rest.isNaN()) currentDistance(a, b) else rest
        require(length > 0f && length.isFinite()) { "Rest length $length is not finite and more than 0." }
        requireCompliance(compliance)
        val link = links.take()
        if (link == linkA.size) growLinks()
        linkA[link] = a
        linkB[link] = b
        linkRest[link] = length
        linkCompliance[link] = compliance
        linkLambda[link] = 0f
        linkFlags[link] = flags
        linkTear[link] = 0f
        return link
    }

    private fun requireParticle(p: Int) = require(particles.isAliveAt(p)) { "Particle ${Particle(p)} is not in the world." }

    private fun requireCompliance(compliance: Float) =
        require(compliance >= 0f && compliance.isFinite()) { "Compliance $compliance is negative or not finite." }

    private fun growLinks() {
        val size = linkA.size * 2
        linkA = linkA.copyOf(size)
        linkB = linkB.copyOf(size)
        linkRest = linkRest.copyOf(size)
        linkCompliance = linkCompliance.copyOf(size)
        linkLambda = linkLambda.copyOf(size)
        linkFlags = linkFlags.copyOf(size)
        linkTear = linkTear.copyOf(size)
    }

    private fun growPins() {
        val size = pinParticle.size * 2
        pinParticle = pinParticle.copyOf(size)
        pinX = pinX.copyOf(size)
        pinY = pinY.copyOf(size)
        pinCompliance = pinCompliance.copyOf(size)
        pinLambda = pinLambda.copyOf(size)
    }

    private fun growAreas() {
        val size = areaStart.size * 2
        areaStart = areaStart.copyOf(size)
        areaLength = areaLength.copyOf(size)
        areaRest = areaRest.copyOf(size)
        areaCompliance = areaCompliance.copyOf(size)
        areaLambda = areaLambda.copyOf(size)
        areaSign = areaSign.copyOf(size)
    }

    private fun growBends() {
        val size = bendA.size * 2
        bendA = bendA.copyOf(size)
        bendB = bendB.copyOf(size)
        bendC = bendC.copyOf(size)
        bendRest = bendRest.copyOf(size)
        bendCompliance = bendCompliance.copyOf(size)
        bendLambda = bendLambda.copyOf(size)
    }

    private companion object {
        const val ROPE = 1
    }
}

/**
 * Which handles of a store are alive, with a free list so removed ones come back. [take] returns the
 * next handle, which is [end] - 1 when the store had to grow, so the caller grows its arrays then.
 */
internal class Slots {
    private var alive = BooleanArray(16)
    private var free = IntArray(16)
    private var freeCount = 0

    var end = 0
        private set

    val count: Int get() = end - freeCount

    fun take(): Int {
        val slot = if (freeCount > 0) free[--freeCount] else end++
        if (slot >= alive.size) alive = alive.copyOf(alive.size * 2)
        alive[slot] = true
        return slot
    }

    fun isAlive(slot: Int): Boolean = slot in 0 until end && alive[slot]

    fun remove(slot: Int) {
        if (!isAlive(slot)) return
        alive[slot] = false
        if (freeCount == free.size) free = free.copyOf(free.size * 2)
        free[freeCount++] = slot
    }

    fun clear() {
        alive.fill(false, 0, end)
        end = 0
        freeCount = 0
    }
}
