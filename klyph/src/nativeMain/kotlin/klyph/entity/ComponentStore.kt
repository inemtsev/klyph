package klyph.entity

/**
 * Sparse-set storage for one component type. Components sit in a dense array that iterates
 * without gaps; `sparse` maps an entity index to its dense slot or -1.
 *
 * The inline `World.forEach` queries read the four published fields directly, so their names,
 * types and meaning are frozen ABI for every library compiled against klyph: change them only
 * in a major release. `Entity.raw`, which the entity arrays hold, is frozen for the same reason.
 */
@PublishedApi
internal class ComponentStore<T : Any>(capacity: Int) {
    @PublishedApi internal var size: Int = 0
    @PublishedApi internal var entities: LongArray = LongArray(16)
    @PublishedApi internal var items: Array<Any?> = arrayOfNulls(16)
    @PublishedApi internal var sparse: IntArray = IntArray(capacity) { -1 }

    fun ensureEntityCapacity(capacity: Int) {
        if (sparse.size >= capacity) return
        val old = sparse.size
        sparse = sparse.copyOf(capacity)
        sparse.fill(-1, old, capacity)
    }

    fun set(entity: Entity, item: T) {
        val slot = sparse[entity.index]
        if (slot >= 0) {
            items[slot] = item
            entities[slot] = entity.raw
            return
        }
        if (size == entities.size) {
            entities = entities.copyOf(size * 2)
            items = items.copyOf(size * 2)
        }
        entities[size] = entity.raw
        items[size] = item
        sparse[entity.index] = size
        size++
    }

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(entity: Entity): T? {
        val slot = sparse[entity.index]
        if (slot < 0 || entities[slot] != entity.raw) return null
        return items[slot] as T
    }

    fun remove(entity: Entity): Boolean {
        val slot = sparse[entity.index]
        if (slot < 0 || entities[slot] != entity.raw) return false
        val last = size - 1
        if (slot != last) {
            entities[slot] = entities[last]
            items[slot] = items[last]
            sparse[Entity(entities[slot]).index] = slot
        }
        items[last] = null
        sparse[entity.index] = -1
        size = last
        return true
    }
}
