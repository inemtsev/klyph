package klyph.physics

import klyph.app.App
import klyph.app.Phase
import klyph.entity.ComponentType
import klyph.entity.Transform
import klyph.entity.World
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.time.Duration

/**
 * A [PhysicsWorld] run by a klyph app: [App.addPhysics] makes one and steps it every fixed step. After
 * each step, every entity with a [PhysicsBody] and a [Transform] is moved to where its body is.
 *
 * Give the entity [klyph.entity.Interpolated] too and klyph draws it between the last two steps, so it
 * moves smoothly at any frame rate.
 *
 * [toString] gives the step count and whether it is paused, for example
 * `Physics(steps=600, paused=false)`.
 *
 * @sample samples.physics.AppSamples.physics
 */
public class Physics(world: PhysicsWorld) {
    /**
     * World that is stepped. Swap it to change levels: entities still linked to bodies of the old
     * world follow whatever has the same slot in the new one, so remove or relink them.
     */
    public var world: PhysicsWorld = world

    /** Whether the world is held still: it is not stepped and entities do not move. [step] still works, one at a time. */
    public var isPaused: Boolean = false

    /** Number of steps taken since this was made. */
    public var stepCount: Long = 0L
        private set

    /**
     * Steps [world] by [dt], then moves every linked entity of [entities] to its body.
     *
     * @throws IllegalArgumentException if [dt] is not more than zero or is infinite.
     */
    public fun step(entities: World, dt: Duration) {
        world.step(dt)
        stepCount++
        follow(entities)
    }

    /** Moves every entity of [entities] with a [PhysicsBody] and a [Transform] to where its body is now. */
    public fun follow(entities: World) {
        val world = world
        entities.forEach(PhysicsBody, Transform) { _, body, transform -> body.moveTo(world, transform) }
    }

    override fun toString(): String = "Physics(steps=$stepCount, paused=$isPaused)"
}

/**
 * Links an entity to a body in the [Physics]' world: after each step its [Transform] is put where
 * the body is, and turned as the body is. Make one with [PhysicsBody.of], and add it as
 * `world.add(entity, PhysicsBody, PhysicsBody.of(box))`.
 *
 * A rigid body's place is its centre of mass, a soft body's the middle of its ring. A soft body has
 * no one angle, so it turns as its first ring point goes round the middle, from where that was when
 * the link was first followed. A particle does not turn. A body that has been removed leaves its
 * entity where it was.
 *
 * [toString] names the body, for example `PhysicsBody(RigidBody(3))`.
 *
 * @sample samples.physics.AppSamples.physicsBody
 */
public class PhysicsBody private constructor(private val kind: Int, private val index: Int) {
    /** For a soft body: the angle its first ring point was at, seen from its middle, when first followed. */
    private var startAngle = Float.NaN

    internal fun moveTo(world: PhysicsWorld, transform: Transform) {
        when (kind) {
            RIGID -> {
                val bodies = world.rigidBodies
                if (!bodies.isAlive(index)) return
                transform.x = bodies.x(index)
                transform.y = bodies.y(index)
                transform.rotation = bodies.angle(index)
            }
            SOFT -> {
                val bodies = world.softBodies
                if (!bodies.isAlive(index)) return
                val x = bodies.centerX(index)
                val y = bodies.centerY(index)
                val first = bodies.particle(index, 0)
                val angle = atan2(world.particles.y(first) - y, world.particles.x(first) - x)
                if (startAngle.isNaN()) startAngle = angle
                transform.x = x
                transform.y = y
                transform.rotation = wrap(angle - startAngle)
            }
            else -> {
                val particles = world.particles
                if (!particles.isAlive(index)) return
                transform.x = particles.x(index)
                transform.y = particles.y(index)
            }
        }
    }

    override fun toString(): String {
        val body = when (kind) {
            RIGID -> RigidBody(index).toString()
            SOFT -> SoftBody(index).toString()
            else -> Particle(index).toString()
        }
        return "PhysicsBody($body)"
    }

    /** Makes the links, and is the component type they are stored under. */
    public companion object : ComponentType<PhysicsBody>("PhysicsBody") {
        private const val RIGID = 0
        private const val SOFT = 1
        private const val PARTICLE = 2

        /** Returns a link that follows [body]: its centre of mass and its angle. */
        public fun of(body: RigidBody): PhysicsBody = PhysicsBody(RIGID, body.index)

        /** Returns a link that follows [body]: the middle of its ring, and how far it has turned. */
        public fun of(body: SoftBody): PhysicsBody = PhysicsBody(SOFT, body.index)

        /** Returns a link that follows [particle]: its place. */
        public fun of(particle: Particle): PhysicsBody = PhysicsBody(PARTICLE, particle.index)

        private const val TWO_PI = (2.0 * PI).toFloat()

        /** [angle] brought into -π..π, so a soft body that has rolled many times is not drawn at a huge angle. */
        private fun wrap(angle: Float): Float {
            var a = angle % TWO_PI
            if (a > PI.toFloat()) a -= TWO_PI
            if (a < -PI.toFloat()) a += TWO_PI
            return a
        }
    }
}

/**
 * Runs [world] in this app and returns its [Physics]: steps it in [Phase.FIXED_UPDATE] by the fixed
 * step, unless paused, then moves every entity with a [PhysicsBody] and a [Transform] to its body.
 * The [Physics] is also kept as a resource: [physics] finds it again.
 *
 * Systems run in the order they are added. Add the fixed-step systems that push, steer or spawn
 * things before calling this, and those that read what happened, such as contacts, after.
 *
 * @throws IllegalArgumentException if this app already runs physics.
 */
public fun App.addPhysics(world: PhysicsWorld = PhysicsWorld()): Physics {
    require(!resources.has<Physics>()) { "App already runs physics; change its world with physics.world." }
    val physics = Physics(world)
    resources.insert(physics)
    addSystem(Phase.FIXED_UPDATE) { app ->
        if (!physics.isPaused) physics.step(app.world, app.time.fixedDelta)
    }
    return physics
}

/**
 * The [Physics] that [addPhysics] added to this app.
 *
 * @throws NoSuchElementException if [addPhysics] has not been called.
 */
public val App.physics: Physics get() = resources.get()

/** The [Physics] that [addPhysics] added to this app, or `null` if it has not been called. */
public val App.physicsOrNull: Physics? get() = resources.getOrNull()
