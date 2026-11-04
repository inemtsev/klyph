package klyph.app

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Reports how long each part of the last completed frame took. Users never construct it;
 * `App.stats` provides the instance the loop updates.
 *
 * Every value describes the previous frame, not the one running: the loop publishes a frame's
 * numbers when that frame ends, so all values read during one frame belong to the same frame.
 * Before the first frame ends every duration is zero.
 *
 * [durationOf] covers the systems of one phase. [Phase.INPUT] includes the engine's translation
 * of platform events and [Phase.RENDER] includes the engine's sprite pass, which
 * [targetAcquisition], [spriteExtraction], [spriteRecording] and [gpuWait] break down.
 * [transformPropagation] covers the two transform propagations, the one between
 * [Phase.FIXED_UPDATE] and [Phase.UPDATE] and the one between [Phase.LATE_UPDATE] and
 * [Phase.RENDER], [collisionDetection] the overlap detection that follows the first, and
 * [particleAdvance] the particle step between [Phase.UPDATE] and [Phase.LATE_UPDATE]; none of
 * them belongs to a phase. [total] covers the whole frame, so it is slightly more than the sum
 * of its parts.
 *
 * Samples live in the sandbox because they need a display.
 */
public class FrameStats internal constructor() {
    private val published = LongArray(SLOT_COUNT)
    private val pending = LongArray(SLOT_COUNT)

    /** Returns how long the whole last frame took, from its first phase to the end of its sprite pass. */
    public var total: Duration = Duration.ZERO
        private set

    /** Returns how many [Phase.FIXED_UPDATE] steps ran in the last frame. */
    public var fixedStepCount: Int = 0
        private set

    /**
     * Returns how many quads the last frame drew: entity sprites, one per visible glyph of `Text`
     * components, and everything queued on `App.canvas`, a text one quad per visible glyph.
     */
    public var spriteCount: Int = 0
        private set

    /**
     * Returns how many particles of `ParticleEmitter` components were alive after the engine
     * advanced them in the last frame. The ones drawn are also counted in [spriteCount].
     */
    public var particleCount: Int = 0
        private set

    /**
     * Returns how many draw calls the last frame's sprite pass issued. Each is one run of quads in
     * draw order that share a texture and a `DrawSpace`, so it grows with the number of distinct
     * layer and texture pairs, not with the number of sprites.
     */
    public var drawCallCount: Int = 0
        private set

    /** Returns how many overlapping pairs `App.collisions` found in the last frame. */
    public var collisionPairCount: Int = 0
        private set

    /**
     * Returns how many entities the world held when the last frame ended, after its commands and
     * scene transitions were applied.
     */
    public var entityCount: Int = 0
        internal set

    /**
     * Returns how long the transform propagations that follow [Phase.FIXED_UPDATE] and
     * [Phase.LATE_UPDATE] took together in the last frame.
     */
    public val transformPropagation: Duration get() = published[PROPAGATION].nanoseconds

    /**
     * Returns how long `App.collisions` took in the last frame to find the overlapping pairs and
     * publish their `Overlap` events, after [Phase.FIXED_UPDATE].
     */
    public val collisionDetection: Duration get() = published[COLLISION].nanoseconds

    /**
     * Returns how long the engine took in the last frame to advance every `ParticleEmitter`, between
     * [Phase.UPDATE] and [Phase.LATE_UPDATE]. Drawing the particles counts in [spriteExtraction].
     */
    public val particleAdvance: Duration get() = published[PARTICLES].nanoseconds

    /**
     * Returns how long the sprite pass took to get a command buffer and a texture to draw into in
     * the last frame. When the window presents, this includes waiting for a free swapchain image,
     * which is where the display's refresh rate shows up.
     */
    public val targetAcquisition: Duration get() = published[ACQUISITION].nanoseconds

    /** Returns how long the sprite pass took to write the last frame's sprite records into staging memory. */
    public val spriteExtraction: Duration get() = published[EXTRACTION].nanoseconds

    /**
     * Returns how long the sprite pass took to record the last frame's upload, clear and draw
     * commands and submit them.
     */
    public val spriteRecording: Duration get() = published[RECORDING].nanoseconds

    /**
     * Returns how long the last frame waited for the GPU to finish it. It is zero unless the app
     * runs with [GpuSync.WAIT_EACH_FRAME].
     */
    public val gpuWait: Duration get() = published[GPU_WAIT].nanoseconds

    /**
     * Returns how long the systems of [phase] took in the last frame, summed over every step for
     * [Phase.FIXED_UPDATE]. [Phase.STARTUP] reports how long startup took, and [Phase.SHUTDOWN]
     * is always zero because no frame follows it.
     */
    public fun durationOf(phase: Phase): Duration = published[phase.ordinal].nanoseconds

    /** Adds [nanos] to [slot] of the frame in progress. */
    internal fun add(slot: Int, nanos: Long) {
        pending[slot] += nanos
    }

    /** Records how long [Phase.STARTUP] took. It stays visible for the whole run. */
    internal fun recordStartup(nanos: Long) {
        published[Phase.STARTUP.ordinal] = nanos
    }

    /** Ends the frame in progress: makes its numbers the published ones and clears them for the next frame. */
    internal fun publish(totalNanos: Long, fixedStepCount: Int, spriteCount: Int, drawCallCount: Int, collisionPairCount: Int, particleCount: Int) {
        val startup = published[Phase.STARTUP.ordinal]
        pending.copyInto(published)
        published[Phase.STARTUP.ordinal] = startup
        pending.fill(0L)
        total = totalNanos.nanoseconds
        this.fixedStepCount = fixedStepCount
        this.spriteCount = spriteCount
        this.drawCallCount = drawCallCount
        this.collisionPairCount = collisionPairCount
        this.particleCount = particleCount
    }

    /**
     * Returns the last frame in one line, as
     * `FrameStats(total=2.512ms, input=0.004ms, ..., fixedStepCount=1, spriteCount=100000, drawCallCount=1,
     * collisionPairCount=12, entityCount=100000, particleAdvance=0.000ms, particleCount=0)`, with durations
     * in milliseconds. The format may change.
     */
    override fun toString(): String =
        "FrameStats(total=${ms(total)}, input=${ms(durationOf(Phase.INPUT))}, " +
            "fixedUpdate=${ms(durationOf(Phase.FIXED_UPDATE))}, collisionDetection=${ms(collisionDetection)}, " +
            "update=${ms(durationOf(Phase.UPDATE))}, lateUpdate=${ms(durationOf(Phase.LATE_UPDATE))}, " +
            "transformPropagation=${ms(transformPropagation)}, " +
            "render=${ms(durationOf(Phase.RENDER))}, targetAcquisition=${ms(targetAcquisition)}, " +
            "spriteExtraction=${ms(spriteExtraction)}, spriteRecording=${ms(spriteRecording)}, " +
            "gpuWait=${ms(gpuWait)}, fixedStepCount=$fixedStepCount, spriteCount=$spriteCount, drawCallCount=$drawCallCount, " +
            "collisionPairCount=$collisionPairCount, entityCount=$entityCount, " +
            "particleAdvance=${ms(particleAdvance)}, particleCount=$particleCount)"

    private fun ms(duration: Duration): String = duration.toString(DurationUnit.MILLISECONDS, 3)

    internal companion object {
        // Slots 0 until Phase.entries.size are the phases, by ordinal.
        val PROPAGATION: Int = Phase.entries.size
        val ACQUISITION: Int = PROPAGATION + 1
        val EXTRACTION: Int = PROPAGATION + 2
        val RECORDING: Int = PROPAGATION + 3
        val GPU_WAIT: Int = PROPAGATION + 4
        val COLLISION: Int = PROPAGATION + 5
        val PARTICLES: Int = PROPAGATION + 6
        val SLOT_COUNT: Int = PROPAGATION + 7
    }
}
