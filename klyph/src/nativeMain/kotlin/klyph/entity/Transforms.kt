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

/**
 * Calls [use] with the position, rotation and scale that the next [propagateTransforms] gives the
 * [WorldTransform] of [entity], worked out now from the current [Transform]s of the entity and its
 * ancestors, and returns its result. A parent that moved since the last propagation counts, which
 * a read of the [WorldTransform] would miss.
 *
 * The entity must be alive and have a [Transform] and a parent. The walk goes up the chain of
 * parents until it reaches a root, whose [Transform] is the parent transform of the chain below it,
 * or an ancestor with no [Transform], whose children compose with the identity. It then composes
 * down to the entity one level at a time with the arithmetic of [propagateBelow], in the same
 * order, so the result equals what the propagation writes to the bit. The chain waits in
 * [World.transformStack], which a propagation never uses at the same time, so the call allocates
 * nothing once the stack has grown to the depth of the hierarchy.
 */
internal inline fun <R> World.composeWorldTransform(
    entity: Entity,
    use: (x: Float, y: Float, rotation: Float, scaleX: Float, scaleY: Float) -> R,
): R {
    val parents = store(Parent)
    val locals = store(Transform)
    var chain = transformStack
    var length = 0
    var node = entity.raw
    // The slot in locals of the transform the chain starts from: the root's, or -1 for the identity.
    var base: Int
    while (true) {
        if (length == chain.size) chain = chain.copyOf(length * 2)
        chain[length++] = node
        val above = parents.items.itemAt<Parent>(slotOf(parents.sparse, parents.entities, node)).entity.raw
        base = slotOf(locals.sparse, locals.entities, above)
        // `above` ends the chain when it has no Transform, or has one and no parent of its own.
        if (base < 0 || slotOf(parents.sparse, parents.entities, above) < 0) break
        node = above
    }
    transformStack = chain
    var x = 0f
    var y = 0f
    var rotation = 0f
    var scaleX = 1f
    var scaleY = 1f
    if (base >= 0) {
        val root = locals.items.itemAt<Transform>(base)
        x = root.x
        y = root.y
        rotation = root.rotation
        scaleX = root.scaleX
        scaleY = root.scaleY
    }
    var level = length
    while (level > 0) {
        val local = locals.items.itemAt<Transform>(slotOf(locals.sparse, locals.entities, chain[--level]))
        // Keep these lines in step with propagateBelow: TransformsTest compares both to the bit.
        val cos = cos(rotation)
        val sin = sin(rotation)
        val lx = local.x * scaleX
        val ly = local.y * scaleY
        val worldX = x + lx * cos - ly * sin
        val worldY = y + lx * sin + ly * cos
        x = worldX
        y = worldY
        rotation += local.rotation
        scaleX *= local.scaleX
        scaleY *= local.scaleY
    }
    return use(x, y, rotation, scaleX, scaleY)
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
