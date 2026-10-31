package klyph.entity

import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes the [WorldTransform] of every entity that has a [Transform].
 *
 * An entity without a parent, or whose parent has no [Transform], gets a copy of its own
 * [Transform]. A child combines its parent's world transform `P` with its own transform `L`:
 * its position is `L`'s position scaled by `P`'s scale, rotated by `P`'s rotation and moved by
 * `P`'s position; its rotation is the sum of both rotations; its scale is the product of both
 * scales, axis by axis. Parents are computed before their children. An entity gets a
 * [WorldTransform] the first time it is seen with a [Transform] and loses it once it no
 * longer has one.
 *
 * The engine calls this once per frame after the late update stage and before rendering, so
 * users normally never call it. Allocates nothing once every entity has its [WorldTransform].
 *
 * @sample samples.entity.Hierarchies.propagateTransforms
 */
public fun World.propagateTransforms() {
    val worlds = store(WorldTransform)
    val locals = storeOrNull(Transform)
    if (locals == null) {
        if (worlds.size > 0) removeStaleWorldTransforms(worlds, null)
        return
    }
    val parents = storeOrNull(Parent)?.takeIf { it.size > 0 }
    val children = storeOrNull(Children)?.takeIf { it.size > 0 }
    // The store arrays are read into locals because a call per entity costs more than the
    // copy itself in Kotlin/Native. Only the world store grows during the pass.
    val localEntities = locals.entities
    val localItems = locals.items
    val localSparse = locals.sparse
    val worldSparse = worlds.sparse
    var worldEntities = worlds.entities
    var worldItems = worlds.items
    var worldSize = worlds.size
    for (i in 0 until locals.size) {
        val raw = localEntities[i]
        if (parents != null) {
            val p = slotOf(parents.sparse, parents.entities, raw)
            if (p >= 0) {
                val parent = parents.items.itemAt<Parent>(p).entity.raw
                if (slotOf(localSparse, localEntities, parent) >= 0) continue
            }
        }
        val local = localItems.itemAt<Transform>(i)
        // Both stores usually list entities in the same order, so try the same slot first.
        val w = if (i < worldSize && worldEntities[i] == raw) i else slotOf(worldSparse, worldEntities, raw)
        val world: WorldTransform
        if (w >= 0) {
            world = worldItems.itemAt(w)
        } else {
            world = WorldTransform()
            worlds.set(Entity(raw), world)
            worldEntities = worlds.entities
            worldItems = worlds.items
            worldSize = worlds.size
        }
        world.x = local.x
        world.y = local.y
        world.rotation = local.rotation
        world.scaleX = local.scaleX
        world.scaleY = local.scaleY
        if (children != null && slotOf(children.sparse, children.entities, raw) >= 0) {
            propagateBelow(raw, locals, worlds, children)
            worldEntities = worlds.entities
            worldItems = worlds.items
            worldSize = worlds.size
        }
    }
    if (worlds.size > locals.size) removeStaleWorldTransforms(worlds, locals)
}

/**
 * Reads a store item without a type check. A non-reified cast is erased, so this skips the
 * check that `as Transform` would emit on every entity.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <T> Array<Any?>.itemAt(slot: Int): T = this[slot] as T

/** Returns the dense slot of the entity with handle [raw] in a store, or -1 if it has none. */
@Suppress("NOTHING_TO_INLINE")
private inline fun slotOf(sparse: IntArray, entities: LongArray, raw: Long): Int {
    val slot = sparse[Entity(raw).index]
    return if (slot >= 0 && entities[slot] == raw) slot else -1
}

/** Walks the subtree under [root] depth first, composing each child with its parent. */
private fun World.propagateBelow(
    root: Long,
    locals: ComponentStore<Transform>,
    worlds: ComponentStore<WorldTransform>,
    children: ComponentStore<Children>,
) {
    var stack = transformStack
    var top = 0
    stack[top++] = root
    while (top > 0) {
        val node = stack[--top]
        val parentSlot = slotOf(worlds.sparse, worlds.entities, node)
        val childrenSlot = slotOf(children.sparse, children.entities, node)
        if (parentSlot < 0 || childrenSlot < 0) continue
        val parentWorld = worlds.items.itemAt<WorldTransform>(parentSlot)
        val list = children.items.itemAt<Children>(childrenSlot).entities
        val cos = cos(parentWorld.rotation)
        val sin = sin(parentWorld.rotation)
        for (k in 0 until list.size) {
            val child = list[k].raw
            val localSlot = slotOf(locals.sparse, locals.entities, child)
            if (localSlot < 0) continue
            val local = locals.items.itemAt<Transform>(localSlot)
            val worldSlot = slotOf(worlds.sparse, worlds.entities, child)
            val world = if (worldSlot >= 0) {
                worlds.items.itemAt<WorldTransform>(worldSlot)
            } else {
                WorldTransform().also { worlds.set(Entity(child), it) }
            }
            val lx = local.x * parentWorld.scaleX
            val ly = local.y * parentWorld.scaleY
            world.x = parentWorld.x + lx * cos - ly * sin
            world.y = parentWorld.y + lx * sin + ly * cos
            world.rotation = parentWorld.rotation + local.rotation
            world.scaleX = parentWorld.scaleX * local.scaleX
            world.scaleY = parentWorld.scaleY * local.scaleY
            if (slotOf(children.sparse, children.entities, child) >= 0) {
                if (top == stack.size) stack = stack.copyOf(top * 2)
                stack[top++] = child
            }
        }
    }
    transformStack = stack
}

/** Removes the [WorldTransform] of every entity that no longer has a [Transform]. */
private fun removeStaleWorldTransforms(worlds: ComponentStore<WorldTransform>, locals: ComponentStore<Transform>?) {
    for (i in worlds.size - 1 downTo 0) {
        val entity = Entity(worlds.entities[i])
        if (locals?.getOrNull(entity) == null) worlds.remove(entity)
    }
}
