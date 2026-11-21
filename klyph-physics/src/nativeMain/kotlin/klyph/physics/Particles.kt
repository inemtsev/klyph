package klyph.physics

import klyph.collision.CollisionLayers
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Every particle in a [PhysicsWorld], as parallel arrays that a [Particle] handle indexes. A removed
 * particle's slot is handed out again by the next [add], so hold on to handles only while their
 * particles are alive.
 *
 * Velocity is implicit, as in Verlet integration: the difference between where a particle is and where
 * it was a substep ago. That's why the velocity setters move the previous position.
 *
 * The arrays grow by doubling when a particle is added, never during a step.
 *
 * [toString] gives how many particles are alive, for example `Particles(size=120)`.
 */
public class Particles internal constructor(initialCapacity: Int = 64) {
    internal var x = FloatArray(initialCapacity)
    internal var y = FloatArray(initialCapacity)
    internal var prevX = FloatArray(initialCapacity)
    internal var prevY = FloatArray(initialCapacity)
    internal var invMass = FloatArray(initialCapacity)
    internal var radius = FloatArray(initialCapacity)
    internal var flags = IntArray(initialCapacity)

    /** Whether each particle stood on a floor this step ([Events.NOW]) and the step before ([Events.BEFORE]). */
    internal var contact = IntArray(initialCapacity)

    /** The [Solid.tag]s of the floors each particle stood on this step, or-ed together. */
    internal var floorTag = IntArray(initialCapacity)

    /** The soft body each particle belongs to, or -1 for a loose one. */
    internal var owner = IntArray(initialCapacity)

    /** Each particle's collision layers and mask, as bits: see [PhysicsLayers]. */
    internal var category = IntArray(initialCapacity)
    internal var mask = IntArray(initialCapacity)

    /** Whether any particle has ever been given a filter: until then, nobody need look. */
    internal var anyFiltered = false

    /** Each particle's [Material], spread over three arrays. */
    internal var friction = FloatArray(initialCapacity)
    internal var staticFriction = FloatArray(initialCapacity)
    internal var restitution = FloatArray(initialCapacity)

    // Each particle's contact this substep, for [applyFriction]: how far it was pushed out in all, along
    // which normal, with what grip, and against what: nothing that moves (-1), a particle (contactA),
    // the point `(1 - contactT)·contactA + contactT·contactB` of an edge, or the point (contactLocalX,
    // contactLocalY) of rigid body contactBody, from its centre of mass in its own frame.
    internal var contactDepth = FloatArray(initialCapacity)
    internal var contactNx = FloatArray(initialCapacity)
    internal var contactNy = FloatArray(initialCapacity)
    internal var contactGrip = FloatArray(initialCapacity)
    internal var contactHold = FloatArray(initialCapacity)
    internal var contactA = IntArray(initialCapacity)
    internal var contactB = IntArray(initialCapacity)
    internal var contactT = FloatArray(initialCapacity)
    internal var contactBody = IntArray(initialCapacity) { -1 }
    internal var contactLocalX = FloatArray(initialCapacity)
    internal var contactLocalY = FloatArray(initialCapacity)
    private var free = IntArray(16)
    private var freeCount = 0

    /** One past the highest slot ever handed out. */
    @PublishedApi
    internal var end: Int = 0

    /** Number of particles alive. */
    public val size: Int get() = end - freeCount

    /**
     * Calls [action] with every particle alive, in slot order. [action] may remove the particle it is
     * given, but must not add any.
     *
     * @sample samples.physics.ParticleSamples.forEach
     */
    public inline fun forEach(action: (Particle) -> Unit) {
        for (p in 0 until end) if (isAliveAt(p)) action(Particle(p))
    }

    /**
     * Adds a particle at ([x], [y]) at rest and returns its handle.
     *
     * [mass] is more than 0, or [STATIC] for a particle nothing moves. [radius] is how far it keeps
     * out of things, 0 for a point. With [ParticleCollision.EVERYTHING] it is kept out of soft bodies
     * and out of every other such particle, by their radii, like a grain of sand; with
     * [ParticleCollision.SOLIDS] it only meets the solids. Its [material] says how it grips and
     * bounces. Pass the arguments after [y] by name.
     *
     * @throws IllegalArgumentException if [mass] is not more than 0 or [radius] is negative.
     *
     * @sample samples.physics.ParticleSamples.add
     */
    public fun add(
        x: Float,
        y: Float,
        mass: Float = 1f,
        radius: Float = 0f,
        collision: ParticleCollision = ParticleCollision.SOLIDS,
        material: Material = Material.DEFAULT,
    ): Particle {
        require(mass > 0f) { "Particle mass $mass is not more than 0." }
        require(radius >= 0f) { "Particle radius $radius is negative." }
        val p = if (freeCount > 0) free[--freeCount] else {
            if (end == this.x.size) grow()
            end++
        }
        this.x[p] = x
        this.y[p] = y
        prevX[p] = x
        prevY[p] = y
        invMass[p] = if (mass == STATIC) 0f else 1f / mass
        this.radius[p] = radius
        flags[p] = if (collision == ParticleCollision.EVERYTHING) ALIVE or COLLIDES else ALIVE
        owner[p] = -1
        category[p] = CollisionLayers.DEFAULT.bits
        mask[p] = CollisionLayers.ALL.bits
        contactDepth[p] = 0f
        setMaterialAt(p, material)
        contact[p] = 0
        floorTag[p] = 0
        return Particle(p)
    }

    /**
     * Removes [particle] and every constraint that holds it, so that the particle that takes its
     * slot, which may be handed out again, inherits none. Removing a particle that is gone does
     * nothing. A particle of a soft body is removed with it instead, by [SoftBodies.remove].
     */
    public fun remove(particle: Particle) {
        removeAt(particle.index)
    }

    // What holds on to these particles, and must let go of one that is removed: its slot is handed
    // out again, and the next particle must not inherit what held this one. None for particles on their own.
    private val holders = ArrayList<ParticleHolder>(6)

    /** Makes [holder] let go of every particle that is removed from now on. */
    internal fun register(holder: ParticleHolder) {
        holders.add(holder)
    }

    internal fun removeAt(p: Int) {
        if (!isAliveAt(p)) return
        for (i in 0 until holders.size) holders[i].letGo(p)
        flags[p] = 0
        invMass[p] = 0f
        if (freeCount == free.size) free = free.copyOf(free.size * 2)
        free[freeCount++] = p
    }

    /** Removes every particle and everything that holds them. Slots start from 0 again. */
    public fun clear() {
        for (i in 0 until holders.size) holders[i].letGoAll()
        flags.fill(0, 0, end)
        invMass.fill(0f, 0, end)
        end = 0
        freeCount = 0
    }

    /** Returns `true` if [particle] is still in the world. */
    public fun isAlive(particle: Particle): Boolean = isAliveAt(particle.index)

    @PublishedApi
    internal fun isAliveAt(p: Int): Boolean = p in 0 until end && flags[p] and ALIVE != 0

    /** Makes [particle] grip and bounce like [material]. */
    public fun setMaterial(particle: Particle, material: Material) {
        setMaterialAt(particle.index, material)
    }

    internal fun setMaterialAt(p: Int, material: Material) {
        friction[p] = material.friction
        staticFriction[p] = material.staticFriction
        restitution[p] = material.restitution
    }

    /**
     * Sets how [particle] moves. A [Motion.BULLET] is not held to [PhysicsConfig.maxStep] a substep:
     * it goes as fast as it is thrown, and every substep it is swept from where it was to where it
     * went and stopped where it first meets a solid, so it cannot pass through a thin one.
     *
     * @sample samples.physics.ParticleSamples.setMotion
     */
    public fun setMotion(particle: Particle, motion: Motion) {
        val p = particle.index
        flags[p] = if (motion == Motion.BULLET) flags[p] or BULLET else flags[p] and BULLET.inv()
    }

    /** Returns how [particle] moves: see [setMotion]. */
    public fun motionOf(particle: Particle): Motion = if (flags[particle.index] and BULLET != 0) Motion.BULLET else Motion.NORMAL

    internal fun isBulletAt(p: Int): Boolean = flags[p] and BULLET != 0

    /**
     * Puts [particle] on [layers] and has it collide only with what is on a layer of [mask], as a
     * klyph [klyph.collision.Collider] does; see [PhysicsLayers]. For a soft body's particles, set
     * the whole body's with [SoftBodies.setLayers].
     *
     * @throws IllegalArgumentException if [layers] is empty or holds [PhysicsLayers.SOLIDS].
     *
     * @sample samples.physics.ParticleSamples.setLayers
     */
    public fun setLayers(particle: Particle, layers: CollisionLayers, mask: CollisionLayers) {
        PhysicsLayers.checkLayers(layers)
        val p = particle.index
        category[p] = layers.bits
        this.mask[p] = mask.bits
        if (layers != CollisionLayers.DEFAULT || mask != CollisionLayers.ALL) anyFiltered = true
    }

    /** Returns the layers [particle] is on: see [setLayers]. */
    public fun layersOf(particle: Particle): CollisionLayers = CollisionLayers(category[particle.index])

    /** Returns the layers [particle] collides with: see [setLayers]. */
    public fun maskOf(particle: Particle): CollisionLayers = CollisionLayers(mask[particle.index])

    /** Returns what [particle] collides with besides the solids: see [add]. */
    public fun collisionOf(particle: Particle): ParticleCollision =
        if (flags[particle.index] and COLLIDES != 0) ParticleCollision.EVERYTHING else ParticleCollision.SOLIDS

    internal fun collidesAt(p: Int): Boolean = flags[p] and COLLIDES != 0

    /** Returns where [particle] is, across. */
    public fun xOf(particle: Particle): Float = x[particle.index]

    /** Returns where [particle] is, down. */
    public fun yOf(particle: Particle): Float = y[particle.index]

    /** Returns how far [particle] moved across in the last substep: its velocity per substep. */
    public fun movedXOf(particle: Particle): Float = x[particle.index] - prevX[particle.index]

    /** Returns how far [particle] moved down in the last substep: its velocity per substep. */
    public fun movedYOf(particle: Particle): Float = y[particle.index] - prevY[particle.index]

    /** Returns one over [particle]'s mass: 0 for a [STATIC] particle. */
    public fun inverseMassOf(particle: Particle): Float = invMass[particle.index]

    /** Returns how far [particle] keeps out of things: 0 for a point. */
    public fun radiusOf(particle: Particle): Float = radius[particle.index]

    /**
     * Moves [particle] to ([x], [y]) and keeps its velocity.
     *
     * @sample samples.physics.ParticleSamples.moveTo
     */
    public fun moveTo(particle: Particle, x: Float, y: Float) {
        moveToAt(particle.index, x, y)
    }

    internal fun moveToAt(p: Int, x: Float, y: Float) {
        prevX[p] += x - this.x[p]
        prevY[p] += y - this.y[p]
        this.x[p] = x
        this.y[p] = y
    }

    // The engine's own ways in, by slot.
    internal fun addAt(x: Float, y: Float, mass: Float, radius: Float = 0f, collides: Boolean = false): Int =
        add(x, y, mass, radius, if (collides) ParticleCollision.EVERYTHING else ParticleCollision.SOLIDS).index

    internal fun remove(p: Int) = removeAt(p)

    internal fun isAlive(p: Int): Boolean = isAliveAt(p)

    internal fun setMaterial(p: Int, material: Material) = setMaterialAt(p, material)

    internal fun setBullet(p: Int, isBullet: Boolean) = setMotion(Particle(p), if (isBullet) Motion.BULLET else Motion.NORMAL)

    internal fun isBullet(p: Int): Boolean = isBulletAt(p)

    internal fun setFilter(p: Int, category: Int, mask: Int) = setLayers(Particle(p), CollisionLayers(category), CollisionLayers(mask))

    internal fun category(p: Int): Int = category[p]

    internal fun mask(p: Int): Int = mask[p]

    internal fun collides(p: Int): Boolean = collidesAt(p)

    /** Whether slot [p], in range, is a grain: alive, colliding with everything, and part of no soft body. */
    internal fun isGrain(p: Int): Boolean = isAlive(p) && collides(p) && owner[p] < 0

    internal fun x(p: Int): Float = x[p]

    internal fun y(p: Int): Float = y[p]

    internal fun movedX(p: Int): Float = x[p] - prevX[p]

    internal fun movedY(p: Int): Float = y[p] - prevY[p]

    internal fun inverseMass(p: Int): Float = invMass[p]

    internal fun radius(p: Int): Float = radius[p]

    internal fun moveTo(p: Int, x: Float, y: Float) = moveToAt(p, x, y)

    internal fun setVelocity(p: Int, vx: Float, vy: Float, h: Float) = setVelocityAt(p, vx, vy, h)

    /** Gives particle [p] a velocity of ([vx], [vy]) units per second, for substeps of [h] seconds. */
    internal fun setVelocityAt(p: Int, vx: Float, vy: Float, h: Float) {
        prevX[p] = x[p] - vx * h
        prevY[p] = y[p] - vy * h
    }

    /** Sets where particle [p] is and where it was a substep ago, which gives it its velocity. */
    internal fun set(p: Int, x: Float, y: Float, previousX: Float = x, previousY: Float = y) {
        this.x[p] = x
        this.y[p] = y
        prevX[p] = previousX
        prevY[p] = previousY
    }

    /**
     * Moves particle [p] out of a surface that doesn't move: [depth] units along the unit normal ([nx],
     * [ny]) pointing out of it. Its speed into the surface is dropped, or bounced back times
     * [restitution] when it came in faster than [restingMove] units a substep. The push is remembered
     * with the grip, [friction] and [staticFriction], for [applyFriction] at the end of the substep.
     */
    internal fun pushOut(
        p: Int,
        nx: Float,
        ny: Float,
        depth: Float,
        friction: Float = 0f,
        staticFriction: Float = 0f,
        restitution: Float = 0f,
        restingMove: Float = 0f,
    ) {
        var vx = x[p] - prevX[p]
        var vy = y[p] - prevY[p]
        x[p] += nx * depth
        y[p] += ny * depth
        val into = vx * nx + vy * ny
        touch(p, nx, ny, depth, friction, staticFriction, -1, -1, 0f)
        if (into < 0f) {
            val bounce = if (-into > restingMove) restitution else 0f
            vx -= (1f + bounce) * into * nx
            vy -= (1f + bounce) * into * ny
        }
        prevX[p] = x[p] - vx
        prevY[p] = y[p] - vy
    }

    /**
     * Remembers that particle [p] was pushed [depth] along ([nx], [ny]) by a contact with [grip] and
     * [hold] (its friction and static friction), against particle [a], the edge from [a] to [b] at [t],
     * or nothing that moves when [a] is -1. Pushes add up over the substep; the rest is the last one's.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun touch(p: Int, nx: Float, ny: Float, depth: Float, grip: Float, hold: Float, a: Int, b: Int, t: Float) {
        contactDepth[p] += depth
        contact[p] = contact[p] or Events.TOUCHING
        contactNx[p] = nx
        contactNy[p] = ny
        contactGrip[p] = grip
        contactHold[p] = hold
        contactA[p] = a
        contactB[p] = b
        contactT[p] = t
        contactBody[p] = -1
    }

    /**
     * Remembers that particle [p] was pushed [depth] along ([nx], [ny]) by rigid [body], at the point
     * ([localX], [localY]) of it from its centre of mass in its own frame, with [grip] and [hold].
     */
    internal fun touchBody(p: Int, nx: Float, ny: Float, depth: Float, grip: Float, hold: Float, body: Int, localX: Float, localY: Float) {
        touch(p, nx, ny, depth, grip, hold, -1, -1, 0f)
        contactBody[p] = body
        contactLocalX[p] = localX
        contactLocalY[p] = localY
    }

    /**
     * Ends a substep's contacts with their grip. For each particle that was pushed, how far it slid this
     * substep along the surface, past whatever it touched, is undone while that's under the static
     * friction times all it was pushed (the push is what holds it on), and cut by the friction times
     * that otherwise. Coulomb's law, one substep at a time.
     *
     * It runs once a substep, just before the last pass, rather than with each push: a soft body's
     * own constraints tug its particles to and fro between passes, and only what's left near the end
     * is really sliding. The pushes it weighs are the ones since it last ran: the last pass of the
     * substep before, and every pass but the last of this one.
     *
     * A particle that touched a rigid body slides past the point of [bodies] it touched, and the body
     * is held back at that point as the particle is, moving and turning it.
     */
    internal fun applyFriction(bodies: RigidBodies) {
        for (p in 0 until end) {
            val depth = contactDepth[p]
            if (depth == 0f) continue
            contactDepth[p] = 0f
            if (contactBody[p] >= 0) {
                slidePast(p, contactBody[p], depth, bodies)
                continue
            }
            val a = contactA[p]
            val b = contactB[p]
            val t = if (b < 0) 0f else contactT[p]
            val s = 1f - t
            var dx = x[p] - prevX[p]
            var dy = y[p] - prevY[p]
            if (a >= 0) {
                dx -= s * (x[a] - prevX[a])
                dy -= s * (y[a] - prevY[a])
            }
            if (b >= 0) {
                dx -= t * (x[b] - prevX[b])
                dy -= t * (y[b] - prevY[b])
            }
            val nx = contactNx[p]
            val ny = contactNy[p]
            val along = dx * nx + dy * ny
            val tx = dx - along * nx
            val ty = dy - along * ny
            val slide = sqrt(tx * tx + ty * ty)
            if (slide < 1e-7f) continue
            val held = if (slide < contactHold[p] * depth) slide else min(slide, contactGrip[p] * depth)
            if (held == 0f) continue
            val ux = -tx / slide
            val uy = -ty / slide
            val wp = invMass[p]
            val wa = if (a >= 0) invMass[a] * s else 0f
            val wb = if (b >= 0) invMass[b] * t else 0f
            val w = if (a < 0) wp else wp + wa * s + wb * t
            if (w == 0f) continue
            val push = held / w
            x[p] += wp * push * ux
            y[p] += wp * push * uy
            if (a >= 0) {
                x[a] -= wa * push * ux
                y[a] -= wa * push * uy
            }
            if (b >= 0) {
                x[b] -= wb * push * ux
                y[b] -= wb * push * uy
            }
        }
    }

    /** [applyFriction] for particle [p], pushed [depth] in all by rigid [body] this substep. */
    private fun slidePast(p: Int, body: Int, depth: Float, bodies: RigidBodies) {
        if (!bodies.isAliveAt(body)) return
        val lx = contactLocalX[p]
        val ly = contactLocalY[p]
        // where the point it touched is now, from the body's centre, and how far it moved this substep
        val rx = bodies.cos[body] * lx - bodies.sin[body] * ly
        val ry = bodies.sin[body] * lx + bodies.cos[body] * ly
        val before = bodies.prevAngle[body]
        val c = kotlin.math.cos(before)
        val s = kotlin.math.sin(before)
        val movedX = bodies.x[body] + rx - (bodies.prevX[body] + c * lx - s * ly)
        val movedY = bodies.y[body] + ry - (bodies.prevY[body] + s * lx + c * ly)
        val dx = x[p] - prevX[p] - movedX
        val dy = y[p] - prevY[p] - movedY
        val nx = contactNx[p]
        val ny = contactNy[p]
        val along = dx * nx + dy * ny
        val tx = dx - along * nx
        val ty = dy - along * ny
        val slide = sqrt(tx * tx + ty * ty)
        if (slide < 1e-7f) return
        val held = if (slide < contactHold[p] * depth) slide else min(slide, contactGrip[p] * depth)
        if (held == 0f) return
        val ux = -tx / slide
        val uy = -ty / slide
        val wp = invMass[p]
        val w = wp + bodies.inverseMassAt(body, rx, ry, ux, uy)
        if (w == 0f) return
        val push = held / w
        x[p] += wp * push * ux
        y[p] += wp * push * uy
        bodies.shove(body, -push * ux, -push * uy, rx, ry)
    }

    private fun grow() {
        val size = x.size * 2
        x = x.copyOf(size)
        y = y.copyOf(size)
        prevX = prevX.copyOf(size)
        prevY = prevY.copyOf(size)
        invMass = invMass.copyOf(size)
        radius = radius.copyOf(size)
        flags = flags.copyOf(size)
        contact = contact.copyOf(size)
        floorTag = floorTag.copyOf(size)
        owner = owner.copyOf(size)
        category = category.copyOf(size)
        mask = mask.copyOf(size)
        friction = friction.copyOf(size)
        staticFriction = staticFriction.copyOf(size)
        restitution = restitution.copyOf(size)
        contactDepth = contactDepth.copyOf(size)
        contactNx = contactNx.copyOf(size)
        contactNy = contactNy.copyOf(size)
        contactGrip = contactGrip.copyOf(size)
        contactHold = contactHold.copyOf(size)
        contactA = contactA.copyOf(size)
        contactB = contactB.copyOf(size)
        contactT = contactT.copyOf(size)
        contactBody = contactBody.copyOf(size)
        contactLocalX = contactLocalX.copyOf(size)
        contactLocalY = contactLocalY.copyOf(size)
    }

    override fun toString(): String = "Particles(size=$size)"

    /** Holds the mass of a particle nothing moves. */
    public companion object {
        /** A mass for a particle nothing can move, such as the top of a hanging rope. */
        public const val STATIC: Float = Float.POSITIVE_INFINITY

        internal const val ALIVE = 1
        internal const val COLLIDES = 2
        internal const val BULLET = 4
    }
}

/** Something that holds on to particles: constraints, soft bodies, attachments, joints, the hand, events. */
internal interface ParticleHolder {
    /** Lets go of everything that holds particle [p], which is being removed. */
    fun letGo(p: Int)

    /** Lets go of every particle, because the store is being cleared. */
    fun letGoAll()
}
