package klyph.physics

/**
 * What happened in the last [PhysicsWorld.step].
 *
 * **Touches.** [contactsBegun] and [contactsEnded] list the things that started and stopped
 * touching: rigid bodies, soft bodies and the solids, in any pair. Loose particles get none, as sand
 * would flood them: they have the readouts below. A touch begins in the step two things first push on
 * each other, or come within [TOUCH_SLOP], and ends in the first step they don't, so each comes once,
 * however many substeps and points it has. Two bodies asleep against each other stay touching, and a
 * body that's removed ends its touches in the next step.
 *
 * **Sensors.** [sensorsEntered] and [sensorsExited] list what came into and went out of a sensor (see
 * [RigidBodies.setResponse]): rigid bodies, soft bodies and loose particles. The sensor is always [Touch.a].
 *
 * **Joints.** [brokenJointCount] and [brokenJointAt] list the joints that broke.
 *
 * **Particles on floors.** Which particles stood on a floor, and the [Solid.tags] of those floors. It
 * keeps this step and the one before apart, because "can I jump?" asks about before you left the
 * ground. A floor is any solid that pushed the particle up: a surface flatter than 60 degrees. Walls
 * and ceilings push too, but aren't floors and report no tags.
 *
 * [toString] gives how many touches of each kind the last step reported, for example
 * `Events(contactsBegun=2, contactsEnded=0, sensorsEntered=1, sensorsExited=0, brokenJoints=0)`.
 *
 * @sample samples.physics.EventSamples.contactsBegun
 */
public class Events internal constructor(private val particles: Particles, private val bodies: RigidBodies = RigidBodies()) {
    /** Things that started touching in the last step. */
    public val contactsBegun: Touches = Touches()

    /** Things that stopped touching in the last step. */
    public val contactsEnded: Touches = Touches()

    /** Things that came into a sensor in the last step: the sensor is each one's [Touch.a]. */
    public val sensorsEntered: Touches = Touches()

    /** Things that left a sensor, or were removed while in it, in the last step. */
    public val sensorsExited: Touches = Touches()

    private var broken = IntArray(4)

    /** Number of joints that broke in the last step. */
    public var brokenJointCount: Int = 0
        private set

    /**
     * Returns the joint at [index] of those that broke in the last step. It has been removed, so its
     * slot may be handed out again.
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until brokenJointCount`.
     *
     * @sample samples.physics.EventSamples.brokenJointAt
     */
    public fun brokenJointAt(index: Int): Joint {
        if (index !in 0 until brokenJointCount) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for size $brokenJointCount.")
        }
        return Joint(broken[index])
    }

    // Whether particles' touches are written down now: only in a substep's last pass, as once a
    // substep is enough to know, and the solver makes many. True outside a step, so a module driven
    // by hand records.
    private var recording = true

    /** Starts a pass of a substep: touches are written down in the [last] one only. */
    internal fun beginPass(last: Boolean) {
        recording = last
    }

    /**
     * Whether a touch of the soft body [owner] (a particle's [Particles.owner], negative for none) is
     * to be written down now. A module asks this before it looks for touches that are not pushes.
     */
    internal fun records(owner: Int): Boolean = owner >= 0 && recording

    /**
     * How much further than its radius a particle of [owner] looks for what it touches: [TOUCH_SLOP]
     * where [records], 0 elsewhere.
     */
    internal fun touchReach(owner: Int): Float = if (records(owner)) TOUCH_SLOP else 0f

    // what touches, and what's in each sensor, this step and the step before
    private var touching = TouchPairs()
    private var touchedBefore = TouchPairs()
    private var sensing = TouchPairs()
    private var sensedBefore = TouchPairs()

    /**
     * Returns `true` if [particle] stood on a floor at any time during the last step.
     *
     * @sample samples.physics.EventSamples.isOnFloor
     */
    public fun isOnFloor(particle: Particle): Boolean = particles.contact[particle.index] and NOW != 0

    /** Returns `true` if [particle] stood on a floor during the step before the last, for coyote time. */
    public fun wasOnFloor(particle: Particle): Boolean = particles.contact[particle.index] and BEFORE != 0

    /** Returns the tags of every floor [particle] stood on during the last step, together; [SolidTags.NONE] for none. */
    public fun floorTagsOf(particle: Particle): SolidTags = SolidTags(particles.floorTag[particle.index])

    /**
     * Returns `true` if [particle] was pushed out of anything during the last step: a solid, a body or
     * another particle, floor or not.
     */
    public fun isTouching(particle: Particle): Boolean = particles.contact[particle.index] and TOUCHING != 0

    /**
     * Returns the unit normal of [particle]'s last contact, across, pointing away from what it
     * touched: (0, -1) on a flat floor. It means something only while [isTouching].
     */
    public fun contactNormalXOf(particle: Particle): Float = particles.contactNx[particle.index]

    /** Returns the unit normal of [particle]'s last contact, down: see [contactNormalXOf]. */
    public fun contactNormalYOf(particle: Particle): Float = particles.contactNy[particle.index]

    /**
     * Returns the particle [particle] last touched, or the first particle of the edge it touched, or
     * `null` when it last touched a solid or a rigid body.
     */
    public fun touchedParticleOrNull(particle: Particle): Particle? {
        val a = particles.contactA[particle.index]
        return if (a < 0) null else Particle(a)
    }

    override fun toString(): String =
        "Events(contactsBegun=${contactsBegun.size}, contactsEnded=${contactsEnded.size}, sensorsEntered=${sensorsEntered.size}, " +
            "sensorsExited=${sensorsExited.size}, brokenJoints=$brokenJointCount)"

    /** Starts a step: this step's touches become the last step's, and nothing has been touched yet. */
    // The engine's own ways in, by slot.
    internal fun isOnFloor(p: Int): Boolean = particles.contact[p] and NOW != 0

    internal fun wasOnFloor(p: Int): Boolean = particles.contact[p] and BEFORE != 0

    internal fun floorTags(p: Int): Int = particles.floorTag[p]

    internal fun isTouching(p: Int): Boolean = particles.contact[p] and TOUCHING != 0

    internal fun contactNormalX(p: Int): Float = particles.contactNx[p]

    internal fun contactNormalY(p: Int): Float = particles.contactNy[p]

    internal fun beginStep() {
        var t = touchedBefore
        touchedBefore = touching
        touching = t
        touching.clear()
        lastTouch = -1L
        t = sensedBefore
        sensedBefore = sensing
        sensing = t
        sensing.clear()
        contactsBegun.clear()
        contactsEnded.clear()
        sensorsEntered.clear()
        sensorsExited.clear()
        brokenJointCount = 0
        val contact = particles.contact
        val floorTag = particles.floorTag
        for (p in 0 until particles.end) {
            contact[p] = if (contact[p] and NOW != 0) BEFORE else 0
            floorTag[p] = 0
        }
    }

    /**
     * Ends a step: what touches now and didn't before has begun, and what touched before and doesn't
     * now has ended. A touch held by a sleeping body is still there: sleeping, it's not looked at.
     */
    internal fun endStep() {
        for (i in 0 until touchedBefore.count) {
            val key = touchedBefore.key(i)
            if (!touching.contains(key) && isFrozen(key)) touching.add(key)
        }
        for (i in 0 until touching.count) if (!touchedBefore.contains(touching.key(i))) contactsBegun.add(touching.key(i))
        for (i in 0 until touchedBefore.count) if (!touching.contains(touchedBefore.key(i))) contactsEnded.add(touchedBefore.key(i))
        for (i in 0 until sensing.count) if (!sensedBefore.contains(sensing.key(i))) sensorsEntered.add(sensing.key(i))
        for (i in 0 until sensedBefore.count) if (!sensing.contains(sensedBefore.key(i))) sensorsExited.add(sensedBefore.key(i))
    }

    /**
     * Whether a touch can't have changed because nothing in it moves: each side is asleep, static or
     * the solids, and one at least asleep, as two still things never touch by themselves.
     */
    private fun isFrozen(key: Long): Boolean {
        var asleep = false
        for (side in 0..1) {
            val kind = if (side == 0) TouchPairs.kindA(key) else TouchPairs.kindB(key)
            if (kind == TouchKind.SOLIDS.ordinal) continue
            if (kind != TouchKind.RIGID_BODY.ordinal) return false
            val b = if (side == 0) TouchPairs.a(key) else TouchPairs.b(key)
            if (!bodies.isAlive(b)) return false
            if (bodies.asleep[b]) asleep = true else if (bodies.kind[b] != RigidBodies.STATIC) return false
        }
        return asleep
    }

    /** Records that [kindA] [a] and [kindB] [b] pushed on each other this step. */
    internal fun touch(kindA: TouchKind, a: Int, kindB: TouchKind, b: Int) {
        // the level has no handle: its side is kept as 0, as [TouchSide.SOLIDS] is
        val key = TouchPairs.ordered(kindA.ordinal, if (kindA == TouchKind.SOLIDS) 0 else a, kindB.ordinal, if (kindB == TouchKind.SOLIDS) 0 else b)
        // a soft body's particles come one after another, each against the same thing: one will do
        if (key == lastTouch) return
        lastTouch = key
        touching.add(key)
    }

    private var lastTouch = -1L

    /** Records that [kind] [other] is in [sensor] this step. */
    internal fun sense(sensor: Int, kind: TouchKind, other: Int) {
        sensing.add(TouchPairs.key(TouchKind.RIGID_BODY.ordinal, sensor, kind.ordinal, other))
    }

    /** Records that joint [j] broke. */
    internal fun jointBroke(j: Int) {
        if (brokenJointCount == broken.size) broken = broken.copyOf(brokenJointCount * 2)
        broken[brokenJointCount++] = j
    }

    /** Records that a floor carrying [tag] pushed particle [p]. */
    internal fun recordFloor(p: Int, tag: Int) {
        particles.contact[p] = particles.contact[p] or NOW
        particles.floorTag[p] = particles.floorTag[p] or tag
    }

    internal companion object {
        /**
         * Two things this close count as touching, pushing or not: things resting side by side are
         * pushed on now and then, and would touch and stop touching every few steps.
         */
        const val TOUCH_SLOP = 0.25f

        const val NOW = 1
        const val BEFORE = 2
        const val TOUCHING = 4
    }
}
