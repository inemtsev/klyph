package klyph.render

import klyph.entity.Interpolated
import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.drawnTransform
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
import klyph.platform.SamplerAddress
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
 * Inside `runApp` the engine also feeds it the glyphs of `Text` components and the quads of
 * `App.canvas`, some of them in [DrawSpace.SCREEN]; those records sort after every
 * [DrawSpace.WORLD] record and are drawn with a projection in points. Entity sprites are always in
 * [DrawSpace.WORLD].
 *
 * Each texture is sampled with its own [Texture.filter] and [Texture.address]. The renderer holds
 * one sampler per combination of [SamplerFilter] and [SamplerAddress], four in all, and binds the
 * matching one with each run's texture.
 *
 * At most [capacity] sprites are drawn per frame; extra sprites are skipped. [ensureCapacity]
 * grows it. The renderer starts with room for [initialCapacity] sprites.
 *
 * @throws IllegalArgumentException if [initialCapacity] is not positive or above [MAX_CAPACITY].
 */
internal class SpriteRenderer(
    private val device: GpuDevice,
    colorFormat: TextureFormat,
    initialCapacity: Int = 1024,
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
    private val linearClampSampler: Sampler = device.createSampler(SamplerFilter.LINEAR, SamplerAddress.CLAMP)
    private val nearestClampSampler: Sampler = device.createSampler(SamplerFilter.NEAREST, SamplerAddress.CLAMP)
    private val linearRepeatSampler: Sampler = device.createSampler(SamplerFilter.LINEAR, SamplerAddress.REPEAT)
    private val nearestRepeatSampler: Sampler = device.createSampler(SamplerFilter.NEAREST, SamplerAddress.REPEAT)

    /**
     * The vertex uniform block of `sprite.vert.hlsl`: the view-projection matrix in floats 0-15,
     * then the run's first sprite as the bits of a `uint` in float 16, then three floats of
     * padding to a multiple of 16 bytes.
     */
    private val uniforms = FloatArray(UNIFORM_FLOATS)
    private val order = SpriteOrder()

    /** Unsorted records of a frame whose guessed order was wrong; sized on first use. */
    private var scratch = FloatArray(0)

    /** The texture coordinates [tiledUv] computes for one sprite with a tile size, which [writeRecord] copies out. */
    private val tiledCoordinates = FloatArray(4)

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
     * Does nothing if [spriteCount] already fits. The pipeline, shaders and samplers are kept.
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
     * An [Interpolated] entity without a parent that has been through two fixed steps since it was
     * marked is drawn [fixedStepAlpha] of the way from its transform at the start of the last
     * fixed step to its current [Transform]: position and scale linearly, rotation the shorter
     * way round the circle. [fixedStepAlpha] is normally `Time.fixedStepAlpha`; the default of 1
     * draws the current transforms. It must be between 0 and 1.
     *
     * A sprite with a [Sprite.tileWidth] or [Sprite.tileHeight] gets texture coordinates that run
     * past 1, which its texture's repeating sampler wraps; see [Sprite].
     *
     * @throws IllegalArgumentException if [fixedStepAlpha] is below 0 or above 1.
     * @throws IllegalStateException if a sprite with a tile size has a texture that does not repeat
     *   or a region that does not cover its whole texture, or a tile size is negative. The message
     *   names the entity.
     */
    public fun extract(world: World, fixedStepAlpha: Float = 1f): Int = extract(world, fixedStepAlpha, null, null)

    /**
     * Writes the records of [world]'s sprites as the public [extract] does, followed by the quads
     * of [first] and then [second] when they are not `null`, all sorted together by space, layer
     * and texture, and returns how many were written in total.
     */
    internal fun extract(world: World, fixedStepAlpha: Float, first: QuadBuffer?, second: QuadBuffer?): Int {
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
                    writeRecord(
                        world, tiledCoordinates, count * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite,
                    ) { index, value -> records.set(index, value) }
                    count++
                }
            }
            if (first != null) count = appendInOrder(first, records, count, limit)
            if (second != null) count = appendInOrder(second, records, count, limit)
            order.finish()
            order.isSorted
        } else {
            forEachSprite(world) { local, hierarchy, previous, sprite ->
                if (count < limit) {
                    val position = order.place(sprite.layer, sprite.region.texture)
                    // Out of range only when the guess is wrong, and then every record is rewritten.
                    if (position < limit) {
                        writeRecord(
                            world, tiledCoordinates, position * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite,
                        ) { index, value -> records.set(index, value) }
                    }
                    count++
                }
            }
            if (first != null) count = appendPlaced(first, records, count, limit)
            if (second != null) count = appendPlaced(second, records, count, limit)
            order.finish()
            order.predictionHeld
        }
        if (!guessHeld) {
            fillScratch(world, count, alpha, first, second)
            scatter(records, count)
        }
        device.unmap(staging)
        expectSorted = order.isSorted
        spriteCount = count
        return count
    }

    /**
     * Adds the quads of [quads] to the order as a frame expected to be sorted and writes them at
     * the next positions of [records], from [count] up to [limit] records in all. Returns the new
     * count.
     */
    private fun appendInOrder(quads: QuadBuffer, records: MappedFloats, count: Int, limit: Int): Int {
        val order = order
        val source = quads.records
        var written = count
        for (quad in 0 until quads.count) {
            if (written >= limit) break
            order.addGroupWithoutPosition(order.groupFor(quads.layers[quad], quads.textureAt(quad), quads.spaceAt(quad)))
            copyRecord(source, quad * SPRITE_RECORD_FLOATS, written * SPRITE_RECORD_FLOATS) { index, value -> records.set(index, value) }
            written++
        }
        return written
    }

    /**
     * Adds the quads of [quads] to the order as a frame expected to repeat the last one's groups and
     * writes each at its predicted position in [records], from [count] up to [limit] records in
     * all. Returns the new count.
     */
    private fun appendPlaced(quads: QuadBuffer, records: MappedFloats, count: Int, limit: Int): Int {
        val order = order
        val source = quads.records
        var written = count
        for (quad in 0 until quads.count) {
            if (written >= limit) break
            val position = order.placeGroup(order.groupFor(quads.layers[quad], quads.textureAt(quad), quads.spaceAt(quad)))
            // Out of range only when the guess is wrong, and then every record is rewritten.
            if (position < limit) {
                copyRecord(source, quad * SPRITE_RECORD_FLOATS, position * SPRITE_RECORD_FLOATS) { index, value -> records.set(index, value) }
            }
            written++
        }
        return written
    }

    /** Copies the record at float [from] of [source] through [set], starting at float [to]. */
    private inline fun copyRecord(source: FloatArray, from: Int, to: Int, set: (Int, Float) -> Unit) {
        for (k in 0 until SPRITE_RECORD_FLOATS) set(to + k, source[from + k])
    }

    /**
     * Sorts the first [count] sprites of [world], then the quads of [first] and [second], again,
     * this time recording positions, and writes them to the scratch array in that order, blending
     * interpolated sprites by [alpha].
     */
    private fun fillScratch(world: World, count: Int, alpha: Float, first: QuadBuffer?, second: QuadBuffer?) {
        val scratch = scratchFor(capacity)
        val order = order
        order.begin()
        var index = 0
        forEachSprite(world) { local, hierarchy, previous, sprite ->
            if (index < count) {
                order.add(sprite.layer, sprite.region.texture)
                writeRecord(world, tiledCoordinates, index * SPRITE_RECORD_FLOATS, local, hierarchy, previous, alpha, sprite) { i, value ->
                    scratch[i] = value
                }
                index++
            }
        }
        if (first != null) index = scratchQuads(first, scratch, index, count)
        if (second != null) scratchQuads(second, scratch, index, count)
        order.finish()
    }

    /**
     * Adds the quads of [quads] to the order with positions and copies them into [scratch] from
     * record [index] on, stopping at [count] records in all. Returns the next record index.
     */
    private fun scratchQuads(quads: QuadBuffer, scratch: FloatArray, index: Int, count: Int): Int {
        val order = order
        val source = quads.records
        var next = index
        for (quad in 0 until quads.count) {
            if (next >= count) break
            order.addGroup(order.groupFor(quads.layers[quad], quads.textureAt(quad), quads.spaceAt(quad)))
            source.copyInto(scratch, next * SPRITE_RECORD_FLOATS, quad * SPRITE_RECORD_FLOATS, (quad + 1) * SPRITE_RECORD_FLOATS)
            next++
        }
        return next
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

    /** Records the copy of the extracted records into GPU memory. Call before any render pass. */
    public fun upload(commands: CommandBuffer) {
        if (spriteCount == 0) return
        val pass = commands.beginCopyPass()
        pass.uploadToBuffer(staging, storage, spriteCount * SPRITE_RECORD_BYTES, Cycle.CYCLE)
        pass.end()
    }

    /**
     * Draws the uploaded records into the current [pass]: one draw call per run of sprites that
     * share a texture, each with that texture bound with the sampler for its [Texture.filter] and
     * [Texture.address].
     * The [DrawSpace.WORLD] runs go through [camera], then the [DrawSpace.SCREEN] runs through a
     * projection that maps [screenWidth] by [screenHeight] points onto the target, origin
     * top-left. Both projections end in clip space, so the pass's viewport, the whole target in
     * pixels, turns points into pixels. Returns the number of draw calls issued, one per run, and
     * draws nothing if the last [extract] found no sprites.
     */
    internal fun draw(commands: CommandBuffer, pass: RenderPass, camera: Camera, screenWidth: Float, screenHeight: Float): Int {
        if (spriteCount == 0) return 0
        val order = order
        val uniforms = uniforms
        pass.bind(pipeline)
        pass.bindVertexStorageBuffer(0, storage)
        val firstScreen = order.firstScreenRun
        if (firstScreen > 0) {
            camera.writeProjection(uniforms)
            drawRuns(commands, pass, 0, firstScreen)
        }
        if (firstScreen < order.runCount) {
            writeScreenProjection(uniforms, screenWidth, screenHeight)
            drawRuns(commands, pass, firstScreen, order.runCount)
        }
        return order.runCount
    }

    /** Issues one draw call for each run from [from] until [until] with the matrix already in the uniforms. */
    private fun drawRuns(commands: CommandBuffer, pass: RenderPass, from: Int, until: Int) {
        val order = order
        val uniforms = uniforms
        // Each run draws from vertex 0 and passes its first sprite as a uniform: SDL_GPU does not
        // promise that a draw's first vertex reaches SV_VertexID on every backend (D3D12 drops it).
        for (run in from until until) {
            uniforms[FIRST_SPRITE_FLOAT] = Float.fromBits(order.runFirst(run))
            commands.pushVertexUniforms(0, uniforms)
            val texture = order.runTexture(run)
            pass.bindFragmentSampler(0, texture.handle, samplerFor(texture))
            pass.draw(order.runSize(run) * 6)
        }
    }

    /** Returns the sampler for [texture]'s [Texture.filter] and [Texture.address]. */
    private fun samplerFor(texture: Texture): Sampler = when (texture.address) {
        SamplerAddress.CLAMP -> if (texture.filter == SamplerFilter.NEAREST) nearestClampSampler else linearClampSampler
        SamplerAddress.REPEAT -> if (texture.filter == SamplerFilter.NEAREST) nearestRepeatSampler else linearRepeatSampler
    }

    override fun close() {
        device.release(pipeline)
        device.release(vertexShader)
        device.release(fragmentShader)
        device.release(linearClampSampler)
        device.release(nearestClampSampler)
        device.release(linearRepeatSampler)
        device.release(nearestRepeatSampler)
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

/**
 * Writes the record of [sprite], a sprite of [world], through [set], starting at float [base], placed
 * where [drawnTransform] puts an entity with these [local], [hierarchy] and [previous] transforms at
 * [alpha]. A sprite with a tile size takes its texture coordinates from [tiledUv] through the
 * scratch [tiledCoordinates].
 */
internal inline fun writeRecord(
    world: World,
    tiledCoordinates: FloatArray,
    base: Int,
    local: Transform,
    hierarchy: WorldTransform?,
    previous: PreviousTransform?,
    alpha: Float,
    sprite: Sprite,
    set: (Int, Float) -> Unit,
) {
    drawnTransform(local, hierarchy, previous, alpha) { x, y, rotation, scaleX, scaleY ->
        val region = sprite.region
        set(base, x)
        set(base + 1, y)
        set(base + 2, rotation)
        set(base + 3, 0f)
        set(base + 4, sprite.width * scaleX)
        set(base + 5, sprite.height * scaleY)
        set(base + 6, 0f)
        set(base + 7, 0f)
        if (sprite.tileWidth == 0f && sprite.tileHeight == 0f) {
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
        } else {
            // A sprite with a tile size is rare, so its coordinates come from a call and the path above stays as it was.
            sprite.tiledUv(world, tiledCoordinates)
            for (k in 0 until 4) set(base + 8 + k, tiledCoordinates[k])
        }
        val color = sprite.color
        set(base + 12, color.red)
        set(base + 13, color.green)
        set(base + 14, color.blue)
        set(base + 15, color.alpha)
    }
}
