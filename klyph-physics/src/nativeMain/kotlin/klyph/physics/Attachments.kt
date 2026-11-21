package klyph.physics

import kotlin.math.sqrt

/**
 * Particles held to points on rigid bodies: a rope tied to a crate, a blob glued to a wheel. Each one
 * pulls its particle and the point of its body together, the XPBD way, as softly as its compliance
 * says, so the particle is dragged along by the body and the body is tugged by the particle, moving
 * and turning it. 0 is a firm hold. Each is an [Attachment] handle; a removed one's slot is reused,
 * and removing its particle or body removes the attachment.
 *
 * [toString] gives how many there are, for example `Attachments(size=2)`.
 *
 * @sample samples.physics.AttachmentSamples.attach
 */
public class Attachments internal constructor(private val particles: Particles, private val bodies: RigidBodies) {
    private val slots = Slots()

    init {
        particles.register(
            object : ParticleHolder {
                override fun letGo(p: Int) {
                    for (a in 0 until slots.end) if (slots.isAlive(a) && particle[a] == p) remove(a)
                }

                override fun letGoAll() = clear()
            },
        )
        bodies.register(
            object : RigidBodyHolder {
                override fun letGo(b: Int) {
                    for (a in 0 until slots.end) if (slots.isAlive(a) && body[a] == b) remove(a)
                }

                override fun letGoAll() = clear()
            },
        )
    }

    // The particle, the body, the point on the body from its centre of mass in its own frame, how
    // soft, and how hard it has pulled this substep.
    private var particle = IntArray(8)
    private var body = IntArray(8)
    private var localX = FloatArray(8)
    private var localY = FloatArray(8)
    private var compliance = FloatArray(8)
    private var lambda = FloatArray(8)

    @PublishedApi
    internal val end: Int get() = slots.end

    /** Number of attachments. */
    public val size: Int get() = slots.count

    /** Calls [action] with every attachment, in slot order. [action] may remove the one it is given. */
    public inline fun forEach(action: (Attachment) -> Unit) {
        for (a in 0 until end) if (isAliveAt(a)) action(Attachment(a))
    }

    /**
     * Holds [particle] to [body] at the point where the particle is now, as softly as [compliance], and
     * returns the attachment's handle.
     *
     * @throws IllegalArgumentException if [particle] or [body] is gone, or [compliance] is negative.
     *
     * @sample samples.physics.AttachmentSamples.attach
     */
    public fun attach(particle: Particle, body: RigidBody, compliance: Float = 0f): Attachment =
        Attachment(attach(particle.index, body.index, compliance))

    internal fun attach(p: Int, b: Int, compliance: Float = 0f): Int {
        require(particles.isAliveAt(p)) { "Particle ${Particle(p)} is not in the world." }
        require(bodies.isAlive(b)) { "Body ${RigidBody(b)} is not in the world." }
        require(compliance >= 0f) { "Compliance $compliance is negative." }
        bodies.wake(b)
        val a = slots.take()
        if (a >= particle.size) {
            val size = particle.size * 2
            particle = particle.copyOf(size)
            body = body.copyOf(size)
            localX = localX.copyOf(size)
            localY = localY.copyOf(size)
            this.compliance = this.compliance.copyOf(size)
            lambda = lambda.copyOf(size)
        }
        val dx = particles.x(p) - bodies.x[b]
        val dy = particles.y(p) - bodies.y[b]
        val c = bodies.cos[b]
        val s = bodies.sin[b]
        particle[a] = p
        body[a] = b
        localX[a] = c * dx + s * dy
        localY[a] = -s * dx + c * dy
        this.compliance[a] = compliance
        lambda[a] = 0f
        return a
    }

    /** Lets go: [attachment] no longer holds its particle. Removing one that is gone does nothing. */
    public fun remove(attachment: Attachment) {
        slots.remove(attachment.index)
    }

    internal fun remove(a: Int) {
        slots.remove(a)
    }

    internal fun clear() {
        slots.clear()
    }

    /** Returns `true` if [attachment] is still there. */
    public fun isAlive(attachment: Attachment): Boolean = slots.isAlive(attachment.index)

    internal fun isAlive(a: Int): Boolean = slots.isAlive(a)

    @PublishedApi
    internal fun isAliveAt(a: Int): Boolean = slots.isAlive(a)

    /** Returns the particle [attachment] holds. */
    public fun particleOf(attachment: Attachment): Particle = Particle(particle[attachment.index])

    internal fun particle(a: Int): Int = particle[a]

    /** Returns the rigid body [attachment] ties its particle to. */
    public fun bodyOf(attachment: Attachment): RigidBody = RigidBody(body[attachment.index])

    internal fun body(a: Int): Int = body[a]

    /** Returns where [attachment] holds its particle, the point on its body, in the world now, across. */
    public fun pointXOf(attachment: Attachment): Float = pointX(attachment.index)

    internal fun pointX(a: Int): Float = bodies.worldX(body[a], localX[a], localY[a])

    /** Returns where [attachment] holds its particle in the world now, down. */
    public fun pointYOf(attachment: Attachment): Float = pointY(attachment.index)

    override fun toString(): String = "Attachments(size=$size)"

    internal fun pointY(a: Int): Float = bodies.worldY(body[a], localX[a], localY[a])

    /** Forgets how hard each has pulled: the start of a substep. */
    internal fun beginSubstep() {
        lambda.fill(0f, 0, slots.end)
    }

    /** One pass over every attachment, for a substep of [h] seconds. */
    internal fun solve(h: Float) {
        val hh = h * h
        for (a in 0 until slots.end) if (slots.isAlive(a)) solve(a, hh)
    }

    private fun solve(a: Int, hh: Float) {
        val p = particle[a]
        val b = body[a]
        if (!particles.isAlive(p) || !bodies.isAlive(b)) return
        val rx = bodies.cos[b] * localX[a] - bodies.sin[b] * localY[a]
        val ry = bodies.sin[b] * localX[a] + bodies.cos[b] * localY[a]
        val dx = particles.x[p] - bodies.x[b] - rx
        val dy = particles.y[p] - bodies.y[b] - ry
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-6f) return
        val nx = dx / length
        val ny = dy / length
        val wp = particles.invMass[p]
        val softness = compliance[a] / hh
        val w = wp + bodies.inverseMassAt(b, rx, ry, nx, ny) + softness
        if (w == 0f) return
        val change = (-length - softness * lambda[a]) / w
        lambda[a] += change
        particles.x[p] += wp * change * nx
        particles.y[p] += wp * change * ny
        bodies.shove(b, -change * nx, -change * ny, rx, ry)
    }
}
