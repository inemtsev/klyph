package klyph.physics

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Everything that moves, and the one way to move time: [step]. The world owns its stores; bodies,
 * particles and constraints are value-class handles into them, such as [Particle] and [RigidBody],
 * so a step touches plain arrays and allocates nothing. The same calls in the same order give the
 * same results, to the bit.
 *
 * [toString] gives how much is in it, for example
 * `PhysicsWorld(particles=120, rigidBodies=8, softBodies=2, steps=600)`.
 *
 * @sample samples.physics.WorldSamples.step
 */
public class PhysicsWorld(
    /** How the world moves time: gravity, substeps, damping and sleep. */
    public val config: PhysicsConfig = PhysicsConfig(),
) {
    /** Every particle: loose ones, and those soft bodies, ropes and cloth are made of. */
    public val particles: Particles = Particles()

    /** Distances, ropes, pins and areas between [particles]. */
    public val constraints: Constraints = Constraints(particles)

    /** Bodies that keep their shape and turn: see [RigidBodies]. */
    public val rigidBodies: RigidBodies = RigidBodies()

    /** Soft bodies: groups of [particles] held together by [constraints]. */
    public val softBodies: SoftBodies = SoftBodies(this)

    /** What happened during the last step: which particles stood on a floor, and what it was tagged. */
    public val events: Events = Events(particles, rigidBodies)

    /**
     * The level's walls, floors, slopes and moving solids, or `null` for none. Every particle is pushed
     * out of them. Setting it wakes every sleeping body: what they rested on may be gone.
     */
    public var solids: SolidGrid? = null
        set(value) {
            field = value
            rigidBodies.wakeAll()
        }

    /** Hand that drags particles and rigid bodies about: see [grab]. */
    public val hand: Grab = Grab(this)

    private val collisions = Collisions(this)

    /** Hinges, sliders, welds and distance joints between rigid bodies: see [Joints]. */
    public val joints: Joints = Joints(particles, rigidBodies, events)

    /** Particles held to points on rigid bodies: see [attach] and [Attachments]. */
    public val attachments: Attachments = Attachments(particles, rigidBodies)

    private val coupling = Coupling(this)

    // Where rigid bodies touched each other and the solids in the last substep, for drawing.
    internal val rigidContacts: RigidContacts = RigidContacts(this)

    private val sleeping = Sleeping(this)

    private val bullets = Bullets(this)

    private val queries = Queries(this)

    /**
     * The boxes this step's collisions were found from, for drawing: one per soft body and one per
     * colliding particle that isn't part of a body. A box's id is `2·particle` for a particle and
     * `2·body + 1` for a body. Read it; don't change it.
     */
    internal val broadphase: Broadphase get() = collisions.broadphase

    // The length of the last substep, in seconds: what velocities in units per second are measured against.
    internal var substepLength: Float = 1f / 60f / config.substeps
        private set

    /** Number of contacts between rigid bodies, and between rigid bodies and the solids, found in the last substep. */
    public val contactCount: Int get() = rigidContacts.count

    /** Number of times [step] has run. */
    public var stepCount: Long = 0
        private set

    /**
     * Advances the world by [dt], in [PhysicsConfig.substeps] substeps. Step by the same fixed time
     * every frame, such as a 60th of a second, for results that do not depend on the frame rate.
     *
     * @throws IllegalArgumentException if [dt] is not more than zero or is infinite.
     *
     * @sample samples.physics.WorldSamples.step
     */
    public fun step(dt: Duration) {
        require(dt.isPositive() && dt.isFinite()) { "Step $dt is not a finite time more than zero." }
        step(dt.toDouble(DurationUnit.SECONDS).toFloat())
    }

    /**
     * Advances the world by [dt] seconds, in [PhysicsConfig.substeps] substeps. Each one moves every
     * particle and rigid body and finds where rigid bodies touch, then [PhysicsConfig.iterations] times
     * solves the [constraints], [attachments] and [joints], keeps bodies out of each other, soft and rigid
     * alike, and pushes everything out of the
     * [solids], so walls have the last word. Last, rigid bodies take their velocities from how far they
     * moved, and their contacts' friction and bounce change them.
     *
     * After the substeps, islands of rigid bodies that have been still long enough fall asleep: they
     * cost nothing until something wakes them. See [RigidBodies.isAsleep] and [RigidBodies.wake].
     */
    internal fun step(dt: Float) {
        require(dt > 0f && dt.isFinite()) { "Step $dt is not a finite time more than 0." }
        val h = dt / config.substeps
        substepLength = h
        val keep = exp(-config.damping * h)
        val restingMove = config.bounceSpeed * h
        val gravity = config.gravity
        events.beginStep()
        hand.beginStep()
        rigidBodies.beginStep(dt)
        collisions.beginStep(dt)
        coupling.beginStep(dt)
        rigidContacts.beginStep(dt)
        repeat(config.substeps) {
            integrate(h, keep)
            rigidBodies.integrate(h, keep, gravity.x, gravity.y, config.maxStep)
            bullets.sweepParticles(particles)
            bullets.sweepBodies()
            rigidContacts.find()
            constraints.beginSubstep()
            attachments.beginSubstep()
            joints.beginSubstep(h)
            hand.beginSubstep()
            repeat(config.iterations) { pass ->
                constraints.solve(h)
                attachments.solve(h)
                joints.solve(h)
                hand.solve(h)
                // Grip just before the last pass, so collisions still have the last word over it.
                if (pass == config.iterations - 1) particles.applyFriction(rigidBodies)
                events.beginPass(last = pass == config.iterations - 1)
                collisions.solve()
                coupling.solve()
                solids?.collide(particles, events, restingMove)
                rigidContacts.solve()
            }
            rigidContacts.recordTouches(events)
            // a body woken from here on, by a joint breaking, waits for the next substep to move
            rigidBodies.inSubstep = false
            joints.endSubstep(h)
            rigidBodies.updateVelocities(h)
            rigidContacts.solveVelocities(h, config.bounceSpeed, config.iterations)
        }
        constraints.tear()
        sleeping.update(dt)
        events.endStep()
        stepCount++
    }

    /**
     * Removes [particle] and every constraint on it. Use this rather than [Particles.remove] when the
     * particle has constraints: its slot will be reused, and a new particle must not inherit them.
     * Removing one that is gone does nothing.
     */
    public fun removeParticle(particle: Particle) {
        if (particles.isAliveAt(particle.index)) removeParticle(particle.index)
    }

    internal fun removeParticle(p: Int) {
        val c = constraints
        for (link in 0 until c.linkEnd) if (c.isLinkAlive(link) && (c.linkA(link) == p || c.linkB(link) == p)) c.removeLink(link)
        for (pin in 0 until c.pinEnd) if (c.isPinAlive(pin) && c.pinnedParticle(pin) == p) c.removePin(pin)
        for (area in 0 until c.areaEnd) if (c.isAreaAlive(area) && c.ringContains(area, p)) c.removeArea(area)
        for (bend in 0 until c.bendEnd) {
            if (c.isBendAlive(bend) && (c.bendA(bend) == p || c.bendB(bend) == p || c.bendC(bend) == p)) c.removeBend(bend)
        }
        particles.remove(p)
    }

    /**
     * Takes hold of the dynamic rigid body at ([x], [y]), by that point, or else the movable particle
     * nearest it, within [reach], and pulls it towards that point with a soft pin until [release].
     * One thing at a time: grabbing again lets go of the last. Read what it holds from
     * [Grab.particleOrNull] and [Grab.bodyOrNull] on [hand].
     *
     * @sample samples.physics.WorldSamples.grab
     */
    public fun grab(x: Float, y: Float, reach: Float = Grab.REACH, compliance: Float = Grab.COMPLIANCE) {
        hand.grab(x, y, reach, compliance)
    }

    /** Moves the point the grabbed thing is pulled towards. However far it jumps, it pulls gently. */
    public fun moveGrab(x: Float, y: Float) {
        hand.moveTo(x, y)
    }

    /** Lets go of what [grab] took hold of. Does nothing when nothing is held. */
    public fun release() {
        hand.release()
    }

    /**
     * Adds a soft body of [shape] with its middle at [centerX] and its bottom on [baseY], and returns
     * its handle in [softBodies].
     *
     * @sample samples.physics.SoftBodySamples.add
     */
    public fun softBody(
        shape: SoftBodyShape,
        centerX: Float,
        baseY: Float,
        tuning: SoftBodyTuning = SoftBodyTuning(),
        material: Material = Material.DEFAULT,
    ): SoftBody = softBodies.add(shape, centerX, baseY, tuning, material = material)

    /**
     * Adds a rigid body made of [shape] with the shape's origin at ([x], [y]), turned by [angle]
     * radians, and returns its handle in [rigidBodies]. Its mass and inertia come from the shape's
     * size and [density].
     *
     * @throws IllegalArgumentException if [density] is not more than 0.
     *
     * @sample samples.physics.RigidBodySamples.rigidBody
     */
    public fun rigidBody(
        shape: Shape,
        x: Float,
        y: Float,
        angle: Float = 0f,
        density: Float = RigidBodies.DENSITY,
        material: Material = Material.DEFAULT,
        kind: BodyKind = BodyKind.DYNAMIC,
    ): RigidBody = rigidBodies.add(listOf(shape), x, y, angle, density, material, kind)

    /**
     * Adds a rigid body made of several [shapes], such as a hammer of two boxes, and returns its
     * handle: see [RigidBodies.add].
     *
     * @throws IllegalArgumentException if [shapes] is empty or [density] is not more than 0.
     */
    public fun rigidBody(
        shapes: List<Shape>,
        x: Float,
        y: Float,
        angle: Float = 0f,
        density: Float = RigidBodies.DENSITY,
        material: Material = Material.DEFAULT,
        kind: BodyKind = BodyKind.DYNAMIC,
    ): RigidBody = rigidBodies.add(shapes, x, y, angle, density, material, kind)

    /**
     * Returns whether the ray from ([fromX], [fromY]) to ([toX], [toY]) hits anything of [targets],
     * and writes the first hit into [hit]: what it hit, the point, the surface's normal there and how
     * far along the ray.
     *
     * Rigid bodies are hit on their shapes, soft bodies on their outlines, and the solids on their
     * open faces; sensors are not hit. Something the ray starts inside is not hit: a ray from a
     * surface going out does not hit that surface, so a reflected ray can start where the last one
     * hit. Allocates nothing.
     *
     * @sample samples.physics.QuerySamples.raycast
     */
    public fun raycast(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        hit: RayHit,
        targets: QueryTargets = QueryTargets.EVERYTHING,
    ): Boolean = queries.raycast(fromX, fromY, toX, toY, hit, targets.bits)

    /**
     * Finds everything of [targets] that the point ([x], [y]) is inside, writes it into [found] and
     * returns how many. A point on a surface is outside it. Allocates nothing once [found] is big
     * enough.
     *
     * @sample samples.physics.QuerySamples.queryPoint
     */
    public fun queryPoint(x: Float, y: Float, found: Overlaps, targets: QueryTargets = QueryTargets.EVERYTHING): Int =
        queries.queryPoint(x, y, found, targets.bits)

    /**
     * Finds everything of [targets] that reaches into the box from ([left], [top]) to ([right],
     * [bottom]), writes it into [found] and returns how many. Touching the box's edge counts.
     * Allocates nothing once [found] is big enough.
     *
     * @sample samples.physics.QuerySamples.queryBox
     */
    public fun queryBox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        found: Overlaps,
        targets: QueryTargets = QueryTargets.EVERYTHING,
    ): Int = queries.queryBox(left, top, right, bottom, found, targets.bits)

    /**
     * Holds [particle] to [body] at the point where the particle is now, as softly as [compliance],
     * so each drags the other, as a rope tied to a crate does. Returns the handle in [attachments].
     *
     * @throws IllegalArgumentException if [particle] or [body] is gone, or [compliance] is negative.
     *
     * @sample samples.physics.AttachmentSamples.attach
     */
    public fun attach(particle: Particle, body: RigidBody, compliance: Float = 0f): Attachment =
        attachments.attach(particle, body, compliance)

    /** Returns the velocity of [particle] across, in units per second, over the last substep. */
    public fun velocityXOf(particle: Particle): Float = particles.movedX(particle.index) / substepLength

    /** Returns the velocity of [particle] down, in units per second, over the last substep. */
    public fun velocityYOf(particle: Particle): Float = particles.movedY(particle.index) / substepLength

    /** Gives [particle] a velocity of ([vx], [vy]) units per second. */
    public fun setVelocity(particle: Particle, vx: Float, vy: Float) {
        particles.setVelocity(particle.index, vx, vy, substepLength)
    }

    internal fun grabbed(): Int = hand.particle

    internal fun velocityX(p: Int): Float = particles.movedX(p) / substepLength

    internal fun velocityY(p: Int): Float = particles.movedY(p) / substepLength

    internal fun setVelocity(p: Int, vx: Float, vy: Float) {
        particles.setVelocity(p, vx, vy, substepLength)
    }

    internal fun attach(p: Int, body: Int, compliance: Float = 0f): Int = attachments.attach(p, body, compliance)

    override fun toString(): String =
        "PhysicsWorld(particles=${particles.size}, rigidBodies=${rigidBodies.size}, softBodies=${softBodies.size}, steps=$stepCount)"

    /**
     * Moves every particle that can move: it repeats its last move, a little weaker for [keep], plus
     * gravity, capped at [PhysicsConfig.maxStep] unless it's a bullet.
     */
    private fun integrate(h: Float, keep: Float) {
        val ps = particles
        val x = ps.x
        val y = ps.y
        val prevX = ps.prevX
        val prevY = ps.prevY
        val invMass = ps.invMass
        val gx = config.gravity.x * h * h
        val gy = config.gravity.y * h * h
        val maxStep = config.maxStep
        for (p in 0 until ps.end) {
            if (invMass[p] == 0f) continue
            var dx = (x[p] - prevX[p]) * keep + gx
            var dy = (y[p] - prevY[p]) * keep + gy
            val length = sqrt(dx * dx + dy * dy)
            if (length > maxStep && ps.flags[p] and Particles.BULLET == 0) {
                dx *= maxStep / length
                dy *= maxStep / length
            }
            prevX[p] = x[p]
            prevY[p] = y[p]
            x[p] += dx
            y[p] += dy
        }
    }
}
