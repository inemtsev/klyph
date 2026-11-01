package klyph.render

import klyph.entity.Interpolated
import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.blendRotation
import klyph.platform.BlendMode
import klyph.platform.BufferUsage
import klyph.platform.CommandBuffer
import klyph.platform.Cycle
import klyph.platform.GpuBuffer
import klyph.platform.GpuDevice
import klyph.platform.GpuShader
import klyph.platform.GraphicsPipeline
import klyph.platform.MappedFloats
import klyph.platform.RenderPass
import klyph.platform.Sampler
import klyph.platform.SamplerFilter
import klyph.platform.TextureFormat
import klyph.platform.TransferBuffer
import klyph.platform.beginCopyPass
import klyph.platform.bind
import klyph.platform.bindFragmentSampler
import klyph.platform.bindVertexStorageBuffer
import klyph.platform.createBuffer
import klyph.platform.createGraphicsPipeline
import klyph.platform.createSampler
import klyph.platform.createShader
import klyph.platform.createTransferBuffer
import klyph.platform.draw
import klyph.platform.map
import klyph.platform.pushVertexUniforms
import klyph.platform.release
import klyph.platform.unmap
import klyph.platform.uploadToBuffer
import klyph.render.generated.SpriteFragmentShader
import klyph.render.generated.SpriteVertexShader

/**
 * Draws every entity that has a [Transform] and a visible [Sprite], with one draw call per run of
 * sprites that share a texture. An entity in a hierarchy is drawn at its [WorldTransform], an
 * [Interpolated] entity between its last two fixed steps, and any other entity at its [Transform].
 *
 * Each frame has three steps so callers can time them: [extract] sorts the sprites by layer and
 * texture and writes one 64-byte record per sprite into mapped staging memory, [upload] copies
 * the records to a GPU storage buffer, and [draw] issues one draw of six vertices per sprite for
 * each run. The vertex shader finds each record from the run's first sprite, passed as a
 * uniform, and the vertex index; there are no vertex buffers. The shaders come from
 * `src/shaders/sprite.*.hlsl`, compiled by the `compileShaders` task.
 *
 * At most [capacity] sprites are drawn per frame; extra sprites are skipped. [ensureCapacity]
 * grows it. The renderer starts with room for [initialCapacity] sprites.
 *
 * @throws IllegalArgumentException if [initialCapacity] is not positive or above [MAX_CAPACITY].
 */
public class SpriteRenderer(
    private val device: GpuDevice,
    colorFormat: TextureFormat,
    initialCapacity: Int = 1024,
    filter: SamplerFilter = SamplerFilter.LINEAR,
) : AutoCloseable {
    init {
        require(initialCapacity > 0) { "Sprite capacity $initialCapacity is not positive." }
        require(initialCapacity <= MAX_CAPACITY) { "Sprite capacity $initialCapacity is above the maximum of $MAX_CAPACITY." }
    }

    /** Number of sprites [extract] writes at most. Grows through [ensureCapacity]. */
    public var capacity: Int = initialCapacity
        private set

    private var storage: GpuBuffer = device.createBuffer(BufferUsage.STORAGE_READ, capacity * SPRITE_RECORD_BYTES)
    private var staging: TransferBuffer = device.createTransferBuffer(capacity * SPRITE_RECORD_BYTES)
    private val vertexShader: GpuShader = device.createShader(SpriteVertexShader)
    private val fragmentShader: GpuShader = device.createShader(SpriteFragmentShader)
    private val pipeline: GraphicsPipeline =
        device.createGraphicsPipeline(vertexShader, fragmentShader, colorFormat, BlendMode.ALPHA)
    private val sampler: Sampler = device.createSampler(filter)
    /**
     * The vertex uniform block of `sprite.vert.hlsl`: the view-projection matrix in floats 0-15,
     * then the run's first sprite as the bits of a `uint` in float 16, then three floats of
     * padding to a multiple of 16 bytes.
     */
    private val uniforms = FloatArray(UNIFORM_FLOATS)
    private val order = SpriteOrder()

    /** Unsorted records of a frame whose guessed order was wrong; sized on first use. */
    private var scratch = FloatArray(0)

    /**
     * Whether the last frame's sprites were already in draw order. If so, this frame is guessed
     * to be in order too; if not, it is guessed to have the last frame's groups.
     */
    private var expectSorted = true

    /** Number of sprites written by the last [extract]. */
    public var spriteCount: Int = 0
        private set

    /**
     * Grows [capacity] to at least [spriteCount] by doubling it, replacing the record buffers.
     * Does nothing if [spriteCount] already fits. The pipeline, shaders and sampler are kept.
     *
     * Work already submitted may still read the old buffers; the GPU device defers their release
     * until it is done with them.
     *
     * @throws IllegalArgumentException if [spriteCount] is negative or above [MAX_CAPACITY].
     */
    public fun ensureCapacity(spriteCount: Int) {
        require(spriteCount >= 0) { "Sprite count $spriteCount is less than zero." }
        require(spriteCount <= MAX_CAPACITY) { "Sprite count $spriteCount is above the maximum of $MAX_CAPACITY." }
        if (spriteCount <= capacity) return
        var grown = capacity
        while (grown < spriteCount) grown = if (grown > MAX_CAPACITY / 2) MAX_CAPACITY else grown * 2
        val newStorage = device.createBuffer(BufferUsage.STORAGE_READ, grown * SPRITE_RECORD_BYTES)
        val newStaging = try {
            device.createTransferBuffer(grown * SPRITE_RECORD_BYTES)
        } catch (e: Throwable) {
            device.release(newStorage)
            throw e
        }
        device.release(staging)
        device.release(storage)
        storage = newStorage
        staging = newStaging
        capacity = grown
    }

    /**
     * Writes a record for every visible sprite in [world] into staging memory, in draw order, and
     * returns how many were written. Sprites whose [Sprite.isVisible] is `false` are skipped.
     * Allocates nothing once its buffers have grown to the sprite count.
     *
     * Draw order is by [Sprite.layer], lowest first; within a layer, sprites are grouped by
     * texture, and keep their storage order inside a group. Positions, rotations and scales come
     * from each entity's [WorldTransform] if it has one, which only entities with a parent do,
     * and from its [Transform] otherwise. Run [klyph.entity.propagateTransforms] first so the
     * world transforms are current; the engine does that before rendering.
     *
     * An [Interpolated] entity without a parent that has been through a fixed step since it was
     * marked is drawn [fixedStepAlpha] of the way from its transform at the start of the last
     * fixed step to its current [Transform]: position and scale linearly, rotation the shorter
     * way round the circle. [fixedStepAlpha] is normally `Time.fixedStepAlpha`; the default of 1
     * draws the current transforms. It must be between 0 and 1.
     *
     * @throws IllegalArgumentException if [fixedStepAlpha] is below 0 or above 1.
     */
    public fun extract(world: World, fixedStepAlpha: Float = 1f): Int {
        require(fixedStepAlpha >= 0f && fixedStepAlpha <= 1f) { "Fixed step alpha $fixedStepAlpha is not between 0 and 1." }
        // One walk over the world finds each sprite's group and writes its record, using a guess
        // from the previous frame. If that frame came out in draw order, this one most likely is
        // too: records are written in storage order and no group is stored per sprite. Otherwise
        // the previous frame's groups most likely repeat, so each record goes straight to the
        // position it had in that frame's sort. A wrong guess costs a second walk that writes to
        // a scratch array, then a copy of each record to its sorted position.
        val limit = capacity
        val order = order
        val alpha = fixedStepAlpha
        order.begin()
        var count = 0
        val records = device.map(staging, Cycle.CYCLE)
        val guessHeld = if (expectSorted) {
            forEachSprite(world) { local, hierarchy, previous, sprite ->
                if (count < limit) {
                    order.addWithoutPosition(sprite.layer, sprite.region.texture)
                    writeRecord(count * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite) { index, value -> records.set(index, value) }
                    count++
                }
            }
            order.finish()
            order.isSorted
        } else {
            forEachSprite(world) { local, hierarchy, previous, sprite ->
                if (count < limit) {
                    val position = order.place(sprite.layer, sprite.region.texture)
                    // Out of range only when the guess is wrong, and then every record is rewritten.
                    if (position < limit) {
                        writeRecord(position * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite) { index, value -> records.set(index, value) }
                    }
                    count++
                }
            }
            order.finish()
            order.predictionHeld
        }
        if (!guessHeld) {
            fillScratch(world, count, alpha)
            scatter(records, count)
        }
        device.unmap(staging)
        expectSorted = order.isSorted
        spriteCount = count
        return count
    }

    /**
     * Sorts the first [count] sprites of [world] again, this time recording positions, and writes
     * them to the scratch array in storage order, blending interpolated ones by [alpha].
     */
    private fun fillScratch(world: World, count: Int, alpha: Float) {
        val scratch = scratchFor(capacity)
        val order = order
        order.begin()
        var index = 0
        forEachSprite(world) { local, hierarchy, previous, sprite ->
            if (index < count) {
                order.add(sprite.layer, sprite.region.texture)
                writeRecord(index * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite) { i, value -> scratch[i] = value }
                index++
            }
        }
        order.finish()
    }

    /** Copies the first [count] records of the scratch array to their sorted positions in [records]. */
    private fun scatter(records: MappedFloats, count: Int) {
        val scratch = scratch
        val order = order
        for (sprite in 0 until count) {
            val from = sprite * SPRITE_RECORD_FLOATS
            val to = order.claimPosition(sprite) * SPRITE_RECORD_FLOATS
            for (k in 0 until SPRITE_RECORD_FLOATS) records.set(to + k, scratch[from + k])
        }
    }

    private fun scratchFor(sprites: Int): FloatArray {
        if (scratch.size < sprites * SPRITE_RECORD_FLOATS) scratch = FloatArray(sprites * SPRITE_RECORD_FLOATS)
        return scratch
    }

    /**
     * Calls [action] for every entity with a [Transform] and a visible [Sprite], in [Transform]
     * storage order, with its [WorldTransform] or `null` if it has none, and, only when it has no
     * world transform, its [PreviousTransform] or `null` if it has none. Each of the two lookups
     * costs one sparse-array read per sprite, and none in a world with no component of that type.
     */
    private inline fun forEachSprite(world: World, action: (Transform, WorldTransform?, PreviousTransform?, Sprite) -> Unit) {
        val worlds = world.storeOrNull(WorldTransform)?.takeIf { it.size > 0 }
        val previous = world.storeOrNull(PreviousTransform)?.takeIf { it.size > 0 }
        if (previous == null) {
            if (worlds == null) {
                world.forEach(Transform, Sprite) { _, local, sprite ->
                    if (sprite.isVisible) action(local, null, null, sprite)
                }
                return
            }
            val worldSparse = worlds.sparse
            val worldItems = worlds.items
            world.forEach(Transform, Sprite) { entity, local, sprite ->
                if (sprite.isVisible) {
                    val slot = worldSparse[entity.index]
                    action(local, if (slot >= 0) worldItems.itemAt<WorldTransform>(slot) else null, null, sprite)
                }
            }
            return
        }
        val previousSparse = previous.sparse
        val previousItems = previous.items
        if (worlds == null) {
            world.forEach(Transform, Sprite) { entity, local, sprite ->
                if (sprite.isVisible) {
                    val slot = previousSparse[entity.index]
                    action(local, null, if (slot >= 0) previousItems.itemAt<PreviousTransform>(slot) else null, sprite)
                }
            }
            return
        }
        val worldSparse = worlds.sparse
        val worldItems = worlds.items
        world.forEach(Transform, Sprite) { entity, local, sprite ->
            if (sprite.isVisible) {
                val index = entity.index
                val worldSlot = worldSparse[index]
                if (worldSlot >= 0) {
                    action(local, worldItems.itemAt<WorldTransform>(worldSlot), null, sprite)
                } else {
                    val slot = previousSparse[index]
                    action(local, null, if (slot >= 0) previousItems.itemAt<PreviousTransform>(slot) else null, sprite)
                }
            }
        }
    }

    /**
     * Reads a store item without a type check. A non-reified cast is erased, so this skips the
     * check that `as WorldTransform` would emit on every sprite.
     */
    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    private inline fun <T> Array<Any?>.itemAt(slot: Int): T = this[slot] as T

    /**
     * Writes the record of [sprite] through [set], starting at float [base], placed at [hierarchy]
     * if it is not `null`, [alpha] of the way from [previous] to [local] if [previous] is not
     * `null`, and at [local] otherwise.
     */
    private inline fun writeRecord(
        base: Int,
        local: Transform,
        hierarchy: WorldTransform?,
        previous: PreviousTransform?,
        alpha: Float,
        sprite: Sprite,
        set: (Int, Float) -> Unit,
    ) {
        val x: Float
        val y: Float
        val rotation: Float
        val scaleX: Float
        val scaleY: Float
        if (hierarchy != null) {
            x = hierarchy.x
            y = hierarchy.y
            rotation = hierarchy.rotation
            scaleX = hierarchy.scaleX
            scaleY = hierarchy.scaleY
        } else if (previous != null) {
            x = previous.x + alpha * (local.x - previous.x)
            y = previous.y + alpha * (local.y - previous.y)
            rotation = blendRotation(previous.rotation, local.rotation, alpha)
            scaleX = previous.scaleX + alpha * (local.scaleX - previous.scaleX)
            scaleY = previous.scaleY + alpha * (local.scaleY - previous.scaleY)
        } else {
            x = local.x
            y = local.y
            rotation = local.rotation
            scaleX = local.scaleX
            scaleY = local.scaleY
        }
        val region = sprite.region
        set(base, x)
        set(base + 1, y)
        set(base + 2, rotation)
        set(base + 3, 0f)
        set(base + 4, sprite.width * scaleX)
        set(base + 5, sprite.height * scaleY)
        set(base + 6, 0f)
        set(base + 7, 0f)
        if (sprite.isFlippedX) {
            set(base + 8, region.u1)
            set(base + 10, region.u0)
        } else {
            set(base + 8, region.u0)
            set(base + 10, region.u1)
        }
        if (sprite.isFlippedY) {
            set(base + 9, region.v1)
            set(base + 11, region.v0)
        } else {
            set(base + 9, region.v0)
            set(base + 11, region.v1)
        }
        val color = sprite.color
        set(base + 12, color.red)
        set(base + 13, color.green)
        set(base + 14, color.blue)
        set(base + 15, color.alpha)
    }

    /** Records the copy of the extracted records into GPU memory. Call before any render pass. */
    public fun upload(commands: CommandBuffer) {
        if (spriteCount == 0) return
        val pass = commands.beginCopyPass()
        pass.uploadToBuffer(staging, storage, spriteCount * SPRITE_RECORD_BYTES, Cycle.CYCLE)
        pass.end()
    }

    /**
     * Draws the uploaded sprites into the current [pass] as seen through [camera]: one draw call
     * per run of sprites that share a texture, each with that texture bound. The camera's viewport
     * should match the pass's target size in pixels. Draws nothing if the last [extract] found no
     * sprites.
     */
    public fun draw(commands: CommandBuffer, pass: RenderPass, camera: Camera) {
        if (spriteCount == 0) return
        val order = order
        camera.writeProjection(uniforms)
        pass.bind(pipeline)
        pass.bindVertexStorageBuffer(0, storage)
        // Each run draws from vertex 0 and passes its first sprite as a uniform: SDL_GPU does not
        // promise that a draw's first vertex reaches SV_VertexID on every backend (D3D12 drops it).
        val uniforms = uniforms
        for (run in 0 until order.runCount) {
            uniforms[FIRST_SPRITE_FLOAT] = Float.fromBits(order.runFirst(run))
            commands.pushVertexUniforms(0, uniforms)
            pass.bindFragmentSampler(0, order.runTexture(run).handle, sampler)
            pass.draw(order.runSize(run) * 6)
        }
    }

    override fun close() {
        device.release(pipeline)
        device.release(vertexShader)
        device.release(fragmentShader)
        device.release(sampler)
        device.release(staging)
        device.release(storage)
    }

    /** Returns `SpriteRenderer(capacity=1024, sprites=100, runs=2)`. The format may change. */
    override fun toString(): String = "SpriteRenderer(capacity=$capacity, sprites=$spriteCount, runs=${order.runCount})"

    /** Holds [MAX_CAPACITY]. */
    public companion object {
        /** The largest [capacity] a renderer supports: the most records whose bytes fit in an `Int`. */
        public const val MAX_CAPACITY: Int = Int.MAX_VALUE / SPRITE_RECORD_BYTES

        /** Floats in the vertex uniform block: a 4x4 matrix, the first sprite, three of padding. */
        private const val UNIFORM_FLOATS = 20

        /** Index of the run's first sprite in the vertex uniform block. */
        private const val FIRST_SPRITE_FLOAT = 16
    }
}
