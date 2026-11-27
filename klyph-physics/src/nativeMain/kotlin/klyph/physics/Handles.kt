package klyph.physics

/**
 * Identifies one particle of a [PhysicsWorld], as returned by [Particles.add]. Pass it to the
 * world's stores, such as [Particles.xOf] and [Constraints.distance].
 *
 * A particle's slot is reused once it is removed, so a handle kept after [Particles.remove] may
 * later refer to a new particle; [Particles.isAlive] tells whether it still refers to one.
 *
 * [toString] gives the slot, for example `Particle(12)`.
 */
public value class Particle @PublishedApi internal constructor(internal val index: Int) : JointEnd {
    override fun toString(): String = "Particle($index)"
}

/**
 * Identifies one rigid body of a [PhysicsWorld], as returned by [PhysicsWorld.rigidBody]. Pass it
 * to [RigidBodies], such as [RigidBodies.xOf] and [RigidBodies.setVelocity], and to [Joints].
 *
 * A body's slot is reused once it is removed, so a handle kept after [RigidBodies.remove] may later
 * refer to a new body; [RigidBodies.isAlive] tells whether it still refers to one.
 *
 * [toString] gives the slot, for example `RigidBody(3)`.
 */
public value class RigidBody @PublishedApi internal constructor(internal val index: Int) : JointEnd {
    override fun toString(): String = "RigidBody($index)"
}

/**
 * Identifies one soft body of a [PhysicsWorld], as returned by [PhysicsWorld.softBody]. Pass it to
 * [SoftBodies], such as [SoftBodies.centerXOf] and [SoftBodies.addVelocity].
 *
 * A soft body's slot is reused once it is removed, so a handle kept after [SoftBodies.remove] may
 * later refer to a new soft body; [SoftBodies.isAlive] tells whether it still refers to one.
 *
 * [toString] gives the slot, for example `SoftBody(0)`.
 */
public value class SoftBody @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "SoftBody($index)"
}

/**
 * Identifies one distance constraint between two particles, a stick or a rope, as returned by
 * [Constraints.distance] and [Constraints.rope].
 *
 * [toString] gives the slot, for example `Link(40)`.
 */
public value class Link @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Link($index)"
}

/**
 * Identifies one pin, which holds a particle at a point of the world, as returned by
 * [Constraints.pin].
 *
 * [toString] gives the slot, for example `Pin(2)`.
 */
public value class Pin @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Pin($index)"
}

/**
 * Identifies one area constraint, which keeps a ring of particles covering the same area, as
 * returned by [Constraints.area].
 *
 * [toString] gives the slot, for example `Area(5)`.
 */
public value class Area @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Area($index)"
}

/**
 * Identifies one bend, which keeps the angle at the middle of three particles, as returned by
 * [Constraints.bend].
 *
 * [toString] gives the slot, for example `Bend(17)`.
 */
public value class Bend @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Bend($index)"
}

/**
 * Identifies one joint between rigid bodies, as returned by [Joints.hinge], [Joints.slider],
 * [Joints.weld] and [Joints.distance].
 *
 * [toString] gives the slot, for example `Joint(1)`.
 */
public value class Joint @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Joint($index)"
}

/**
 * Identifies one attachment, which ties a particle to a point on a rigid body, as returned by
 * [PhysicsWorld.attach].
 *
 * [toString] gives the slot, for example `Attachment(0)`.
 */
public value class Attachment @PublishedApi internal constructor(internal val index: Int) {
    override fun toString(): String = "Attachment($index)"
}

/**
 * What the second end of a joint holds on to: another [RigidBody], a [Particle], or the world
 * itself, [JointEnd.WORLD], which never moves.
 */
public sealed interface JointEnd {
    /** Holds the end that is the world. */
    public companion object {
        /** The world as a joint's end: a point that never moves. */
        public val WORLD: JointEnd = TheWorld

        /** Returns the end a joint stores as [code]: see [JointEnd.code]. */
        internal fun decode(code: Int): JointEnd = when {
            code >= 0 -> RigidBody(code)
            code == Joints.WORLD -> TheWorld
            else -> Particle(Joints.particleOf(code))
        }
    }
}

/** How a joint stores this end: a body's slot, [Joints.WORLD], or a particle as [Joints.particle] encodes it. */
internal val JointEnd.code: Int
    get() = when (this) {
        is RigidBody -> index
        is Particle -> Joints.particle(index)
        TheWorld -> Joints.WORLD
    }

/** The world as a joint's end. */
internal object TheWorld : JointEnd {
    override fun toString(): String = "JointEnd.WORLD"
}

/** Whether a particle collides with bodies and other particles: see [Particles.add]. */
public enum class BodyCollision {
    /**
     * It passes through rigid bodies, soft bodies and other particles, such as a spark or a drop of a
     * fountain: the default, and the cheaper.
     */
    PASS,

    /** It is kept out of rigid bodies, soft bodies and every other particle that collides with bodies, like a grain of sand. */
    COLLIDE,
}

/**
 * Whether a body or a particle is kept out of the level, the [SolidGrid] and its moving solids: see
 * [RigidBodies.setSolidCollision], [SoftBodies.setSolidCollision] and [Particles.setSolidCollision].
 */
public enum class SolidCollision {
    /** It is kept out of the solids: the default. */
    COLLIDE,

    /** It passes through the solids, as a ghost does. */
    PASS,
}

/** How a particle or a rigid body moves each substep: see [Particles.setMotion] and [RigidBodies.setMotion]. */
public enum class Motion {
    /** Held to [PhysicsConfig.maxStep] a substep, which keeps it out of thin walls at ordinary speeds. */
    NORMAL,

    /** Swept from where it was to where it went each substep, so it can go as fast as it likes and never passes through a wall. */
    BULLET,
}

/** What a rigid body does to what it overlaps: see [RigidBodies.setResponse]. */
public enum class ContactResponse {
    /** Pushes and is pushed: an ordinary body. */
    PUSH,

    /** Neither pushes nor is pushed, and only notices what is in it: a sensor. */
    SENSE,
}

/** Whether a soft body's skin is kept from passing through itself: see [SoftBodies.setSelfCollision]. */
public enum class SelfCollision {
    /** The skin may pass through itself: the default, and the cheaper. */
    PASS,

    /** The skin is kept from passing through itself, as when the body folds over. */
    COLLIDE,
}
