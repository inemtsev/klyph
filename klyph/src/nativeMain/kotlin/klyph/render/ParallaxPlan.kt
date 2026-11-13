package klyph.render

import klyph.entity.ComponentStore
import klyph.entity.Entity
import klyph.entity.Parent
import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.drawnTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The copies of every [ParallaxLayer] sprite in one frame. [update] works out from the camera,
 * before the renderer writes any record, where each layer's first copy goes and how many copies
 * there are; the renderer then asks [fill] for the record of a layer's sprite, sets each copy's
 * position with [placeCopy] and copies [template] out, once per copy. Planning first tells the
 * renderer how many records to make room for, and makes a layer that cannot be drawn throw before
 * any buffer is mapped.
 *
 * Layers are numbered by their slot in the world's [ParallaxLayer] store, which [layerSlots] maps
 * from entity indices. The arrays are kept between frames, so a frame allocates nothing once they
 * have grown to the number of layers, and the stores of the other components are looked up once
 * per [update], not once per layer.
 */
internal class ParallaxPlan {
    /** Records the layers of the last [update] make beyond one for each of their sprites. */
    var extraRecords: Int = 0
        private set

    /**
     * Maps an entity's index to the slot of its [ParallaxLayer], or -1, for the last [update]; `null`
     * when the world has no layer, which is the case for most games.
     */
    var layerSlots: IntArray? = null
        private set

    /** Number of layers, the slots from 0 up to it, in the last [update]; 0 when the world has none. */
    var layerCount: Int = 0
        private set

    /** The record of the sprite [fill] saw last, with the position [placeCopy] gave it. */
    val template: FloatArray = FloatArray(SPRITE_RECORD_FLOATS)

    /** The blend of fixed steps the last [update] drew its sprites at, which [fill] uses too. */
    private var alpha = 1f

    // The stores of the components a layer's entity needs, as the last update found them.
    private var transforms: ComponentStore<Transform>? = null
    private var sprites: ComponentStore<Sprite>? = null
    private var previousTransforms: ComponentStore<PreviousTransform>? = null
    private var parents: ComponentStore<Parent>? = null

    private val tiledCoordinates = FloatArray(4)

    /** The entity of each slot, as raw handles. */
    private var entities = LongArray(INITIAL_SLOTS)

    /** Records each slot draws: 0 when its sprite is not drawn, else the number of copies. */
    private var copies = IntArray(INITIAL_SLOTS)

    /** Copies in a row, so `copy % columns` is the column and `copy / columns` the row. */
    private var columns = IntArray(INITIAL_SLOTS)

    /** Per slot, [GEOMETRY_FLOATS] floats: the first copy's x and y, then the step between copies in x and y. */
    private var geometry = FloatArray(INITIAL_SLOTS * GEOMETRY_FLOATS)

    // The area the camera sees: the box along the world's axes around the view, and the point the layers shift by.
    private var centerX = 0f
    private var centerY = 0f
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    /** Where [planAxis] put the first copy it found. */
    private var axisStart = 0f

    /**
     * Plans the copies of every layer in [world] seen through [camera], with sprites blended
     * [alpha] of the way between fixed steps. The camera is read as it is: the engine clamps it to
     * its bounds before it draws.
     *
     * @throws IllegalStateException if a layer that would be drawn is not allowed; see
     *   [checkDrawable].
     */
    fun update(world: World, camera: Camera, alpha: Float) {
        this.alpha = alpha
        extraRecords = 0
        layerCount = 0
        val store = world.storeOrNull(ParallaxLayer)
        if (store == null || store.size == 0) {
            layerSlots = null
            return
        }
        val size = store.size
        if (copies.size < size) grow(size)
        transforms = world.storeOrNull(Transform)
        sprites = world.storeOrNull(Sprite)
        previousTransforms = world.storeOrNull(PreviousTransform)
        parents = world.storeOrNull(Parent)
        setView(camera)
        for (slot in 0 until size) {
            copies[slot] = 0
            entities[slot] = store.entities[slot]
            planLayer(world, Entity(entities[slot]), store.items[slot] as ParallaxLayer, slot)
        }
        layerSlots = store.sparse
        layerCount = size
    }

    /** Returns the number of records the layer in [slot] draws: 0 when its sprite is hidden or out of view. */
    fun copiesOf(slot: Int): Int = copies[slot]

    /**
     * Writes into [template] the record that the renderer writes for the sprite of the layer in
     * [slot], at the position its transform gives, and returns that sprite. [placeCopy] then moves
     * the record to each copy. [world] holds the sprite, so that a sprite that cannot be drawn can
     * name its entity.
     */
    fun fill(world: World, slot: Int): Sprite {
        val entity = Entity(entities[slot])
        val sprite = checkNotNull(sprites?.getOrNull(entity)) { "$entity has a planned ParallaxLayer but no Sprite." }
        val local = checkNotNull(transforms?.getOrNull(entity)) { "$entity has a planned ParallaxLayer but no Transform." }
        val template = template
        writeRecord(
            world, tiledCoordinates, 0, local, null, previousTransforms?.getOrNull(entity), alpha, sprite,
        ) { index, value -> template[index] = value }
        return sprite
    }

    /** Sets the position in [template] to that of copy [copy], from 0 up to `copiesOf(slot) - 1`, of the layer in [slot]. */
    fun placeCopy(slot: Int, copy: Int) {
        val across = columns[slot]
        val base = slot * GEOMETRY_FLOATS
        template[0] = geometry[base] + (copy % across) * geometry[base + 2]
        template[1] = geometry[base + 1] + (copy / across) * geometry[base + 3]
    }

    private fun grow(slots: Int) {
        var size = copies.size
        while (size < slots) size *= 2
        entities = entities.copyOf(size)
        copies = copies.copyOf(size)
        columns = columns.copyOf(size)
        geometry = geometry.copyOf(size * GEOMETRY_FLOATS)
    }

    /** Takes the point layers shift by and the box around the camera's view, in world units. */
    private fun setView(camera: Camera) {
        centerX = camera.x + camera.offsetX
        centerY = camera.y + camera.offsetY
        val halfWidth = camera.visibleWidth * 0.5f
        val halfHeight = camera.visibleHeight * 0.5f
        val reachX = reachAlong(camera.rotation, halfWidth, halfHeight)
        val reachY = reachAlong(camera.rotation, halfHeight, halfWidth)
        minX = centerX - reachX
        maxX = centerX + reachX
        minY = centerY - reachY
        maxY = centerY + reachY
    }

    /** Plans the sprite of [layer], in [slot], when [entity] has a visible sprite; a hidden one draws nothing. */
    private fun planLayer(world: World, entity: Entity, layer: ParallaxLayer, slot: Int) {
        val local = transforms?.getOrNull(entity)
        val sprite = sprites?.getOrNull(entity)
        if (local == null || sprite == null || !sprite.isVisible) return
        layer.checkDrawable(entity, parents?.getOrNull(entity)?.entity)
        val originX = sprite.originX
        val originY = sprite.originY
        // A non-finite origin would make every copy miss the view and vanish without a word.
        if (!(originX.isFinite() && originY.isFinite())) sprite.checkOrigin(world)
        // The renderer checks the tiling as it writes the record, with a buffer mapped; here it is
        // checked before, which keeps the promise above.
        if (sprite.tileWidth != 0f || sprite.tileHeight != 0f) sprite.checkTiling(world)
        drawnTransform(local, null, previousTransforms?.getOrNull(entity), alpha) { x, y, rotation, scaleX, scaleY ->
            // The box around the sprite, which a copy must touch the view with. The sprite's origin
            // sits at its position, so the box reaches as far as the origin's farther side on each
            // axis; a flip mirrors the origin, which leaves that reach the same.
            val halfWidth = max(originX, 1f - originX) * abs(sprite.width * scaleX)
            val halfHeight = max(originY, 1f - originY) * abs(sprite.height * scaleY)
            val halfX = reachAlong(rotation, halfWidth, halfHeight)
            val halfY = reachAlong(rotation, halfHeight, halfWidth)
            val countX = planAxis(x + (1f - layer.factorX) * centerX, layer.repeatX, minX, maxX, halfX)
            val startX = axisStart
            val countY = planAxis(y + (1f - layer.factorY) * centerY, layer.repeatY, minY, maxY, halfY)
            val total = min(countX * countY, ParallaxLayer.MAX_COPIES)
            if (total == 0) return@drawnTransform
            copies[slot] = total
            columns[slot] = countX
            val base = slot * GEOMETRY_FLOATS
            geometry[base] = startX
            geometry[base + 1] = axisStart
            geometry[base + 2] = layer.repeatX
            geometry[base + 3] = layer.repeatY
            extraRecords += total - 1
        }
    }

    /**
     * Returns how many copies of a sprite, [period] apart and one at [position], touch the range from
     * [low] to [high] along an axis, given that the sprite reaches [half] either side of its
     * center, and leaves the first of them in [axisStart]. With no [period], 0, it returns 1 for
     * the sprite where it is, whether or not it is in range. At most [ParallaxLayer.MAX_COPIES] are
     * counted, so a period far smaller than the range ends.
     */
    private fun planAxis(position: Float, period: Float, low: Float, high: Float, half: Float): Int {
        if (period == 0f) {
            axisStart = position
            return 1
        }
        // The first center that touches the range, found by wrapping the position into one period from it.
        val start = (low - half) + floorMod(position - (low - half), period)
        axisStart = start
        val last = high + half
        val span = (last - start) / period
        return when {
            // A position that is not a number, or a period so small that wrapping overflows, draws nothing.
            !start.isFinite() || start > last -> 0
            span >= ParallaxLayer.MAX_COPIES -> ParallaxLayer.MAX_COPIES
            else -> span.toInt() + 1
        }
    }

    private companion object {
        const val INITIAL_SLOTS = 8

        /** Floats of [geometry] per slot. */
        const val GEOMETRY_FLOATS = 4
    }
}

/**
 * Returns how far a box with half-sides [along] and [across] reaches from its center along the
 * world's x axis, or y axis for the other pair, once the box is turned by [rotation] radians: half
 * the side of the smallest box along the axes that holds it. [along] is the half-side that lies on
 * the axis when [rotation] is 0, so the result is [along] then.
 */
private fun reachAlong(rotation: Float, along: Float, across: Float): Float {
    if (rotation == 0f) return along
    return abs(cos(rotation)) * along + abs(sin(rotation)) * across
}

/** Returns [value] modulo [period], from 0 up to [period], whatever the sign of [value]; [period] is positive. */
private fun floorMod(value: Float, period: Float): Float {
    val wrapped = value - floor(value / period) * period
    // Rounding can leave a result of exactly [period], or just below 0, for a [value] just below a multiple of it.
    return when {
        wrapped >= period -> wrapped - period
        wrapped < 0f -> wrapped + period
        else -> wrapped
    }
}

/**
 * Checks that this layer, which belongs to [entity], can be drawn: the entity has no [parent],
 * neither factor is negative, infinite or not a number, and neither repeat is. The messages name
 * the entity, as those of a tiled sprite do.
 *
 * @throws IllegalStateException if one of those does not hold.
 */
internal fun ParallaxLayer.checkDrawable(entity: Entity, parent: Entity?) {
    check(parent == null) { "$entity has a ParallaxLayer, which needs an entity without a parent, but its parent is $parent." }
    check(isNotNegativeAndFinite(factorX) && isNotNegativeAndFinite(factorY)) {
        "$entity has a ParallaxLayer with factor ${factorX}x$factorY, which is negative, infinite or not a number."
    }
    check(isNotNegativeAndFinite(repeatX) && isNotNegativeAndFinite(repeatY)) {
        "$entity has a ParallaxLayer with repeat ${repeatX}x$repeatY, which is negative, infinite or not a number."
    }
}

/** Returns `true` if [value] is 0 or more and finite, which a factor and a repeat must be. */
private fun isNotNegativeAndFinite(value: Float): Boolean = value >= 0f && value.isFinite()
