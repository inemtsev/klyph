package klyph.particle

import klyph.entity.ComponentType
import klyph.platform.Color
import klyph.render.TextureRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A component that sends out small quads showing [region], each living for a while and moving in a
 * straight line under [gravityX] and [gravityY] while its color and size blend from their start to
 * their end values. Particles are kept in a pool of [capacity] slots inside the emitter, not as
 * entities, so thousands of them cost no more than the arrays they live in.
 *
 * Once a frame, after [klyph.app.Phase.UPDATE] and before [klyph.app.Phase.LATE_UPDATE], the
 * engine advances every emitter whose entity has a `Transform`: it ages and moves the live
 * particles, removes those that have reached their lifetime, and then emits new ones at the
 * entity's current world position, taken from its `WorldTransform` if it has one and from its
 * `Transform` otherwise. An entity in a hierarchy therefore emits where the last transform
 * propagation put it. New particles come from the bursts requested with [emit] since the last
 * advance and from [rate]. Once emitted, a particle moves in world space and no longer follows the
 * entity. The sprite pass draws every live particle of a visible emitter in world space, in
 * draw [layer], as one quad centered on the particle. An emitter on an entity without a `Transform` is
 * neither advanced nor drawn. Destroying the entity removes its particles at once, so to let a
 * burst fade out after its source is gone, put the emitter on an entity of its own and set
 * [whenEmpty] to [EmitterEnd.DESTROY], which destroys that entity once the burst is over.
 *
 * Each particle draws its direction, speed, lifetime, start rotation and angular velocity at random
 * from [random] when it is emitted. Its direction is [direction] plus up to half of [spread] either
 * side, turned by the entity's world rotation too when [space] is [EmitterSpace.LOCAL]. Angles are
 * in radians, as for `Transform.rotation`: 0 points along +x and positive angles turn clockwise on
 * screen, because y points down; `klyph.math.degrees` converts from degrees, as in
 * `spread = 30f.degrees`. Speeds are in world units per second and gravity in world units per
 * second squared. At a particle's age `t`, as a fraction of its lifetime from 0 to 1, its color is
 * the linear blend of [startColor] and [endColor] at `t`, channel by channel, alpha included, and
 * its size is the region's size in texels times the blend of [startScale] and [endScale] at `t`.
 *
 * At most [capacity] particles are alive at once; emission beyond that is dropped. [capacity]
 * must be positive. Setting a property outside its documented range throws
 * [IllegalArgumentException].
 *
 * Advancing allocates nothing. Not thread-safe; use it from systems only.
 *
 * Has no sample because creating a [klyph.render.Texture] needs a GPU device, which samples cannot
 * open.
 *
 * @throws IllegalArgumentException if [capacity] is not positive.
 */
public class ParticleEmitter(
    /** The part of a texture every particle shows. Changing it changes live particles too. */
    public var region: TextureRegion,
    /** The most particles alive at once. */
    public val capacity: Int = 256,
) {
    init {
        require(capacity > 0) { "Capacity $capacity is not positive." }
    }

    /** Number of live particles. Bursts requested with [emit] count once the engine has emitted them. */
    public var particleCount: Int = 0
        private set

    /**
     * Particles emitted per second, spread over frames with the fraction carried from one frame to
     * the next; 0, the default, emits only the bursts requested with [emit]. Must be finite and not
     * negative.
     *
     * @throws IllegalArgumentException if set to a negative or infinite value or `NaN`.
     */
    public var rate: Float = 0f
        set(value) {
            require(value >= 0f && value.isFinite()) { "Rate $value is not a finite number of at least zero." }
            field = value
        }

    /**
     * Middle of the directions particles leave in, in radians; 0 points along +x and `PI / 2` down
     * the screen. With [EmitterSpace.LOCAL] it is measured from the entity's world rotation. Must
     * be finite.
     *
     * @throws IllegalArgumentException if set to an infinite value or `NaN`.
     */
    public var direction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Direction $value is not finite." }
            field = value
        }

    /**
     * Width of the fan of directions particles leave in, in radians, centered on [direction]; a
     * full turn, `2 * PI`, the default, sends them every way and 0 only along [direction]. Must be
     * between 0 and `2 * PI`.
     *
     * @throws IllegalArgumentException if set to a value outside 0 to `2 * PI`.
     */
    public var spread: Float = FULL_TURN
        set(value) {
            require(value in 0f..FULL_TURN) { "Spread $value is not between 0 and $FULL_TURN radians." }
            field = value
        }

    /**
     * Range a particle's speed is drawn from, in world units per second. Must not be empty.
     *
     * @throws IllegalArgumentException if set to an empty range or one with an end that is not finite.
     */
    public var speed: ClosedFloatingPointRange<Float> = 50f..100f
        set(value) {
            requireRange(value, "Speed")
            field = value
            speedStart = value.start
            speedEnd = value.endInclusive
        }

    /**
     * Range a particle's lifetime is drawn from, such as `0.5.seconds..1.seconds`, the default.
     * Must not be empty, start below zero or end at an infinite duration; a particle with a
     * lifetime of zero is drawn once and removed at the next advance.
     *
     * @throws IllegalArgumentException if set to an empty range, one that starts below zero, or one
     *   whose end is infinite.
     */
    public var lifetime: ClosedRange<Duration> = 500.milliseconds..1.seconds
        set(value) {
            require(!value.isEmpty() && !value.start.isNegative() && value.endInclusive.isFinite()) {
                "Lifetime range $value is empty, starts below zero or is not finite."
            }
            field = value
            lifetimeStart = value.start.toDouble(DurationUnit.SECONDS).toFloat()
            lifetimeEnd = value.endInclusive.toDouble(DurationUnit.SECONDS).toFloat()
        }

    /**
     * Range a particle's rotation at emission is drawn from, in radians. Must not be empty.
     *
     * @throws IllegalArgumentException if set to an empty range or one with an end that is not finite.
     */
    public var startRotation: ClosedFloatingPointRange<Float> = 0f..0f
        set(value) {
            requireRange(value, "Start rotation")
            field = value
            rotationStart = value.start
            rotationEnd = value.endInclusive
        }

    /**
     * Range a particle's turning speed is drawn from, in radians per second. Must not be empty.
     *
     * @throws IllegalArgumentException if set to an empty range or one with an end that is not finite.
     */
    public var angularVelocity: ClosedFloatingPointRange<Float> = 0f..0f
        set(value) {
            requireRange(value, "Angular velocity")
            field = value
            spinStart = value.start
            spinEnd = value.endInclusive
        }

    /** Horizontal acceleration of every live particle, in world units per second squared. */
    public var gravityX: Float = 0f

    /** Vertical acceleration of every live particle, in world units per second squared; positive pulls down. */
    public var gravityY: Float = 0f

    /** Tint of a particle when it is emitted. */
    public var startColor: Color = Color.WHITE

    /**
     * Tint of a particle at the end of its lifetime, or `null`, the default, for [startColor] at
     * zero alpha, so particles keep their color and fade out whatever [startColor] is.
     */
    public var endColor: Color? = null

    /**
     * What happens to the entity once the emitter has nothing left to do. [EmitterEnd.KEEP], the
     * default, keeps it. [EmitterEnd.DESTROY] destroys it through `App.commands` at the end of an
     * advance that leaves no live particle, no burst requested with [emit] and a [rate] of 0, so an
     * entity that carries one burst goes away once the burst has faded. Request the burst before
     * the frame's particle step, or the emitter is empty at its first advance and is destroyed
     * then.
     */
    public var whenEmpty: EmitterEnd = EmitterEnd.KEEP

    /**
     * Whether [direction] is fixed in the world, [EmitterSpace.WORLD], the default, or turns with
     * the entity's world rotation at the moment each particle is emitted, [EmitterSpace.LOCAL],
     * such as a ship's exhaust that points out of its tail whichever way the ship faces. Emitted
     * particles move in world space either way.
     */
    public var space: EmitterSpace = EmitterSpace.WORLD

    /** Size of a particle when it is emitted, as a factor of the region's size in texels. */
    public var startScale: Float = 1f

    /** Size of a particle at the end of its lifetime, as a factor of the region's size in texels. */
    public var endScale: Float = 1f

    /** Draw layer of every particle; higher layers draw on top. Any `Int`, negative included. */
    public var layer: Int = 0

    /** Whether the particles are drawn. Hidden particles still move, age and get emitted. */
    public var isVisible: Boolean = true

    /** Source of every random draw at emission. Pass a seeded `Random(seed)` for runs that repeat exactly. */
    public var random: Random = Random.Default

    // Range ends as primitives: reading a range boxes its ends, and lifetimes are kept in seconds.
    private var speedStart = 50f
    private var speedEnd = 100f
    private var lifetimeStart = 0.5f
    private var lifetimeEnd = 1f
    private var rotationStart = 0f
    private var rotationEnd = 0f
    private var spinStart = 0f
    private var spinEnd = 0f

    // The pool: particle i lives in slot i of every array, for i below particleCount.
    internal val xs = FloatArray(capacity)
    internal val ys = FloatArray(capacity)
    internal val velocitiesX = FloatArray(capacity)
    internal val velocitiesY = FloatArray(capacity)

    /** Age in seconds. */
    internal val ages = FloatArray(capacity)

    /** Lifetime in seconds. */
    internal val lifetimes = FloatArray(capacity)

    /** Rotation in radians. */
    internal val rotations = FloatArray(capacity)

    /** Angular velocity in radians per second. */
    internal val spins = FloatArray(capacity)

    /** Particles requested by [emit] and not yet emitted, at most [capacity]. */
    internal var pendingBurst: Int = 0
        private set

    /** Fraction of a particle that [rate] has built up and not yet emitted. */
    private var carry = 0f

    /**
     * Requests a burst of [count] particles, emitted at the entity's world position the next time
     * the engine advances the emitter, after [Phase.UPDATE][klyph.app.Phase.UPDATE]. Bursts
     * requested in one frame add up. The burst is clamped to the slots free at that moment, so with
     * a full pool nothing is emitted. [count] must not be negative.
     *
     * @throws IllegalArgumentException if [count] is negative.
     */
    public fun emit(count: Int) {
        require(count >= 0) { "Particle count $count is less than zero." }
        pendingBurst = (pendingBurst.toLong() + count).coerceAtMost(capacity.toLong()).toInt()
    }

    /** Removes every live particle and every burst not yet emitted. [rate] keeps emitting from the next advance. */
    public fun clear() {
        particleCount = 0
        pendingBurst = 0
        carry = 0f
    }

    /**
     * Moves the emitter on by [delta] seconds with the entity at ([x], [y]) and turned by
     * [rotation] radians: ages, moves and turns every live particle, removes those that have lived
     * their lifetime, then emits the pending burst and what [rate] built up, turning [direction]
     * by [rotation] when [space] is [EmitterSpace.LOCAL]. Returns the number of live particles.
     */
    internal fun advance(delta: Float, x: Float, y: Float, rotation: Float = 0f): Int {
        integrate(delta)
        carry += rate * delta
        val fromRate = carry.toInt()
        carry -= fromRate
        val wanted = pendingBurst.toLong() + fromRate
        pendingBurst = 0
        val free = capacity - particleCount
        val turn = if (space == EmitterSpace.LOCAL) rotation else 0f
        spawn(x, y, turn, if (wanted < free) wanted.toInt() else free)
        return particleCount
    }

    /** Whether the emitter has no live particle, no pending burst and no [rate], so it stays empty. */
    internal val isSpent: Boolean
        get() = particleCount == 0 && pendingBurst == 0 && rate == 0f

    /** Ages, moves and turns every live particle by [delta] seconds and swap-removes the dead ones. */
    private fun integrate(delta: Float) {
        val xs = xs
        val ys = ys
        val velocitiesX = velocitiesX
        val velocitiesY = velocitiesY
        val ages = ages
        val lifetimes = lifetimes
        val rotations = rotations
        val spins = spins
        val ax = gravityX * delta
        val ay = gravityY * delta
        var live = particleCount
        var i = 0
        while (i < live) {
            val age = ages[i] + delta
            if (age >= lifetimes[i]) {
                // Move the last live particle into this slot and look at the slot again.
                val last = --live
                xs[i] = xs[last]
                ys[i] = ys[last]
                velocitiesX[i] = velocitiesX[last]
                velocitiesY[i] = velocitiesY[last]
                ages[i] = ages[last]
                lifetimes[i] = lifetimes[last]
                rotations[i] = rotations[last]
                spins[i] = spins[last]
                continue
            }
            ages[i] = age
            val vx = velocitiesX[i] + ax
            val vy = velocitiesY[i] + ay
            velocitiesX[i] = vx
            velocitiesY[i] = vy
            xs[i] += vx * delta
            ys[i] += vy * delta
            rotations[i] += spins[i] * delta
            i++
        }
        particleCount = live
    }

    /** Emits [amount] particles at ([x], [y]) with [direction] turned by [turn]; the caller has checked that they fit. */
    private fun spawn(x: Float, y: Float, turn: Float, amount: Int) {
        if (amount <= 0) return
        val random = random
        val center = direction + turn
        val fan = spread
        for (slot in particleCount until particleCount + amount) {
            val angle = center + (random.nextFloat() - 0.5f) * fan
            val speed = speedStart + (speedEnd - speedStart) * random.nextFloat()
            xs[slot] = x
            ys[slot] = y
            velocitiesX[slot] = cos(angle) * speed
            velocitiesY[slot] = sin(angle) * speed
            ages[slot] = 0f
            lifetimes[slot] = lifetimeStart + (lifetimeEnd - lifetimeStart) * random.nextFloat()
            rotations[slot] = rotationStart + (rotationEnd - rotationStart) * random.nextFloat()
            spins[slot] = spinStart + (spinEnd - spinStart) * random.nextFloat()
        }
        particleCount += amount
    }

    private fun requireRange(range: ClosedFloatingPointRange<Float>, name: String) {
        require(!range.isEmpty() && range.start.isFinite() && range.endInclusive.isFinite()) {
            "$name range $range is empty or not finite."
        }
    }

    /**
     * Returns `ParticleEmitter(particles=12/256, rate=30.0, layer=0, visible=true, startColor=Color(#ffffffff),
     * endColor=null, whenEmpty=KEEP, space=WORLD, region=TextureRegion(...))`, where an `endColor` of
     * `null` means [startColor] at zero alpha. The format may change.
     */
    override fun toString(): String =
        "ParticleEmitter(particles=$particleCount/$capacity, rate=$rate, layer=$layer, visible=$isVisible, startColor=$startColor, " +
            "endColor=$endColor, whenEmpty=$whenEmpty, space=$space, region=$region)"

    /** Component key for [ParticleEmitter], and factories of emitters set up for common effects. */
    public companion object : ComponentType<ParticleEmitter>("ParticleEmitter") {
        private const val FULL_TURN: Float = (2.0 * PI).toFloat()

        /**
         * Creates an emitter that sends out one burst of [count] particles showing [region] and
         * then removes its entity: its capacity is [count], the burst is requested with [emit],
         * [rate] is 0 and [whenEmpty] is [EmitterEnd.DESTROY]. Put it on an entity of its own, for
         * example with `Commands.spawnBurst`; the engine emits the burst at the next particle step
         * and destroys the entity once the last particle has lived its lifetime.
         *
         * The other parameters set the emitter properties of the same name and default to their
         * defaults, so a call names only what it changes. Pass them as named arguments. Properties
         * without a parameter, such as [gravityX] or [angularVelocity], can be set on the result
         * before it is added. Each parameter has the range its property documents.
         *
         * Has no sample because creating a [klyph.render.Texture] needs a GPU device, which samples
         * cannot open.
         *
         * @throws IllegalArgumentException if [count] is not positive, or a parameter is outside the
         *   range its property documents.
         */
        public fun burst(
            region: TextureRegion,
            count: Int,
            speed: ClosedFloatingPointRange<Float> = 50f..100f,
            lifetime: ClosedRange<Duration> = 500.milliseconds..1.seconds,
            direction: Float = 0f,
            spread: Float = FULL_TURN,
            gravityY: Float = 0f,
            startColor: Color = Color.WHITE,
            endColor: Color? = null,
            startScale: Float = 1f,
            endScale: Float = 1f,
            startRotation: ClosedFloatingPointRange<Float> = 0f..0f,
            layer: Int = 0,
        ): ParticleEmitter {
            require(count > 0) { "Particle count $count is not positive." }
            return ParticleEmitter(region, capacity = count).apply {
                this.speed = speed
                this.lifetime = lifetime
                this.direction = direction
                this.spread = spread
                this.gravityY = gravityY
                this.startColor = startColor
                this.endColor = endColor
                this.startScale = startScale
                this.endScale = endScale
                this.startRotation = startRotation
                this.layer = layer
                whenEmpty = EmitterEnd.DESTROY
                emit(count)
            }
        }
    }
}

/** Names what happens to a [ParticleEmitter]'s entity once the emitter is empty. */
public enum class EmitterEnd {
    /** Keeps the entity, so the emitter can emit again later. */
    KEEP,

    /**
     * Destroys the entity, with its descendants, once the emitter has no live particle, no pending
     * burst and a rate of 0.
     */
    DESTROY,
}

/** Names the space a [ParticleEmitter]'s direction is measured in. */
public enum class EmitterSpace {
    /** The direction is fixed in the world, whatever the entity's rotation. */
    WORLD,

    /** The direction turns with the entity's world rotation at the moment each particle is emitted. */
    LOCAL,
}
