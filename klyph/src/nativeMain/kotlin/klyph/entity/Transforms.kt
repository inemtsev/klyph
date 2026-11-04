package klyph.entity

import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes the [WorldTransform] of every entity that has a parent and a [Transform].
 *
 * Only entities in a hierarchy get a [WorldTransform]. An entity without a parent has none: its
 * [Transform] already is in world space, and the renderer reads it directly. A child combines its
 * parent's world transform `P` with its own transform `L`: its position is `L`'s position scaled
 * by `P`'s scale, rotated by `P`'s rotation and moved by `P`'s position; its rotation is the sum
 * of both rotations; its scale is the product of both scales, axis by axis. `P` is the parent's
 * [Transform] when the parent is a root, and the identity when the parent has no [Transform], so
 * such a child's world transform is a copy of its own. Parents are computed before their
 * children. A child gets a [WorldTransform] the first time it is seen with a parent and a
 * [Transform], and loses it once it has no [Transform]; [World.removeParent] removes it at once.
 *
 * The engine calls this twice per frame, after the fixed update stage and before collision
 * detection, and again after the late update stage and before rendering, so users normally
 * never call it. The cost grows with the number of entities in hierarchies, not
 * with the number of entities, so a world without parents costs next to nothing. Allocates
 * nothing once every child has its [WorldTransform].
 *
 * @sample samples.entity.Hierarchies.propagateTransforms
 */
public fun World.propagateTransforms() {
    val worlds = storeOrNull(WorldTransform)
    val children = storeOrNull(Children)
    if (children == null || children.size == 0) {
        // No entity has a child, so none has a parent and every WorldTransform is stale.
        if (worlds != null) while (worlds.size > 0) worlds.remove(Entity(worlds.entities[worlds.size - 1]))
        return
    }
    val parents = store(Parent)
    val locals = store(Transform)
    val worldStore = worlds ?: store(WorldTransform)
    // Every entity in a hierarchy descends from exactly one root: an entity with children and no
    // parent. Walking each root's tree reaches every child once.
    var written = 0
    var i = 0
    while (i < children.size) {
        val raw = children.entities[i++]
        if (slotOf(parents.sparse, parents.entities, raw) >= 0) continue
        written += propagateBelow(raw, locals, worldStore, children)
    }
    if (worldStore.size > written) removeStaleWorldTransforms(worldStore, locals, parents)
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

/**
 * Walks the tree under [root] depth first, composing each child with its parent, and returns the
 * number of [WorldTransform]s written. The root itself gets none; its [Transform], or the identity
 * if it has none, is the parent transform of its children.
 */
private fun World.propagateBelow(
    root: Long,
    locals: ComponentStore<Transform>,
    worlds: ComponentStore<WorldTransform>,
    children: ComponentStore<Children>,
): Int {
    var stack = transformStack
    var top = 0
    var written = 0
    stack[top++] = root
    while (top > 0) {
        val node = stack[--top]
        val childrenSlot = slotOf(children.sparse, children.entities, node)
        if (childrenSlot < 0) continue
        // The parent transform: the root's Transform, a child's WorldTransform written above, or
        // the identity for an entity without a Transform.
        var px = 0f
        var py = 0f
        var rotation = 0f
        var scaleX = 1f
        var scaleY = 1f
        val localSlot = slotOf(locals.sparse, locals.entities, node)
        if (localSlot >= 0) {
            if (node == root) {
                val local = locals.items.itemAt<Transform>(localSlot)
                px = local.x
                py = local.y
                rotation = local.rotation
                scaleX = local.scaleX
                scaleY = local.scaleY
            } else {
                val parentWorld = worlds.items.itemAt<WorldTransform>(slotOf(worlds.sparse, worlds.entities, node))
                px = parentWorld.x
                py = parentWorld.y
                rotation = parentWorld.rotation
                scaleX = parentWorld.scaleX
                scaleY = parentWorld.scaleY
            }
        }
        val list = children.items.itemAt<Children>(childrenSlot).entities
        val cos = cos(rotation)
        val sin = sin(rotation)
        for (k in 0 until list.size) {
            val child = list[k].raw
            val childLocal = slotOf(locals.sparse, locals.entities, child)
            if (childLocal >= 0) {
                val local = locals.items.itemAt<Transform>(childLocal)
                val worldSlot = slotOf(worlds.sparse, worlds.entities, child)
                val world = if (worldSlot >= 0) {
                    worlds.items.itemAt<WorldTransform>(worldSlot)
                } else {
                    WorldTransform().also { worlds.set(Entity(child), it) }
                }
                val lx = local.x * scaleX
                val ly = local.y * scaleY
                world.x = px + lx * cos - ly * sin
                world.y = py + lx * sin + ly * cos
                world.rotation = rotation + local.rotation
                world.scaleX = scaleX * local.scaleX
                world.scaleY = scaleY * local.scaleY
                written++
            }
            if (slotOf(children.sparse, children.entities, child) >= 0) {
                if (top == stack.size) stack = stack.copyOf(top * 2)
                stack[top++] = child
            }
        }
    }
    transformStack = stack
    return written
}

/** Removes the [WorldTransform] of every entity that no longer has a [Transform] or a parent. */
private fun removeStaleWorldTransforms(
    worlds: ComponentStore<WorldTransform>,
    locals: ComponentStore<Transform>,
    parents: ComponentStore<Parent>,
) {
    for (i in worlds.size - 1 downTo 0) {
        val entity = Entity(worlds.entities[i])
        if (locals.getOrNull(entity) == null || parents.getOrNull(entity) == null) worlds.remove(entity)
    }
}
