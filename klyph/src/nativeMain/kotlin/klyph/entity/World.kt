package klyph.entity

/**
 * Holds entities and their components. Entities are cheap handles; components are plain objects
 * stored per [ComponentType] in dense arrays, so iterating one component type touches contiguous
 * memory and a two-type query costs one array lookup per entity.
 *
 * Not thread-safe.
 */
public class World(initialCapacity: Int = 1024) {
    private var generations = IntArray(initialCapacity)
    private var alive = BooleanArray(initialCapacity)
    private var freeSlots = IntArray(16)
    private var freeCount = 0
    private var nextSlot = 0
    private val stores = HashMap<ComponentType<*>, ComponentStore<*>>()

    /** Number of live entities. */
    public var entityCount: Int = 0
        private set

    /** Creates an entity with no components. */
    public fun create(): Entity {
        val index = if (freeCount > 0) freeSlots[--freeCount] else allocateSlot()
        alive[index] = true
        entityCount++
        return Entity.of(index, generations[index])
    }

    /**
     * Removes [entity] and all its components. Does nothing if it is already gone.
     */
    public fun destroy(entity: Entity) {
        if (!isAlive(entity)) return
        for (store in stores.values) store.remove(entity)
        val index = entity.index
        alive[index] = false
        generations[index]++
        if (freeCount == freeSlots.size) freeSlots = freeSlots.copyOf(freeCount * 2)
        freeSlots[freeCount++] = index
        entityCount--
    }

    /** Returns `true` while [entity] exists in this world. */
    public fun isAlive(entity: Entity): Boolean {
        val index = entity.index
        return index < alive.size && alive[index] && generations[index] == entity.generation
    }

    /**
     * Attaches [component] to [entity] under [type], replacing any component of that type.
     *
     * @throws IllegalArgumentException if [entity] is not alive.
     */
    public fun <T : Any> add(entity: Entity, type: ComponentType<T>, component: T) {
        require(isAlive(entity)) { "Entity $entity is not alive." }
        store(type).set(entity, component)
    }

    /** Detaches the [type] component from [entity]. Returns `true` if there was one. */
    public fun <T : Any> remove(entity: Entity, type: ComponentType<T>): Boolean =
        stores[type]?.remove(entity) ?: false

    /** Returns the [type] component of [entity], or `null` if it has none. */
    public fun <T : Any> getOrNull(entity: Entity, type: ComponentType<T>): T? =
        storeOrNull(type)?.getOrNull(entity)

    /**
     * Returns the [type] component of [entity].
     *
     * @throws NoSuchElementException if [entity] has no such component.
     */
    public fun <T : Any> get(entity: Entity, type: ComponentType<T>): T =
        getOrNull(entity, type) ?: throw NoSuchElementException("Entity $entity has no ${type.name} component.")

    /** Returns `true` if [entity] has a [type] component. */
    public fun has(entity: Entity, type: ComponentType<*>): Boolean = getOrNull(entity, type) != null

    /** Number of entities that have a [type] component. */
    public fun count(type: ComponentType<*>): Int = storeOrNull(type)?.size ?: 0

    /**
     * Calls [action] for every entity that has both an [a] and a [b] component. Iterates the
     * [a] components in storage order; do not add or remove components of these types inside [action].
     */
    @Suppress("UNCHECKED_CAST")
    public inline fun <A : Any, B : Any> forEach(a: ComponentType<A>, b: ComponentType<B>, action: (Entity, A, B) -> Unit) {
        val storeA = storeOrNull(a) ?: return
        val storeB = storeOrNull(b) ?: return
        val entities = storeA.entities
        val itemsA = storeA.items
        val itemsB = storeB.items
        val sparseB = storeB.sparse
        for (i in 0 until storeA.size) {
            val entity = Entity(entities[i])
            val j = sparseB[entity.index]
            if (j < 0) continue
            action(entity, itemsA[i] as A, itemsB[j] as B)
        }
    }

    /**
     * Calls [action] for every entity that has [a], [b] and [c] components, in [a] storage order.
     */
    @Suppress("UNCHECKED_CAST")
    public inline fun <A : Any, B : Any, C : Any> forEach(
        a: ComponentType<A>,
        b: ComponentType<B>,
        c: ComponentType<C>,
        action: (Entity, A, B, C) -> Unit,
    ) {
        val storeA = storeOrNull(a) ?: return
        val storeB = storeOrNull(b) ?: return
        val storeC = storeOrNull(c) ?: return
        val entities = storeA.entities
        val itemsA = storeA.items
        val itemsB = storeB.items
        val itemsC = storeC.items
        val sparseB = storeB.sparse
        val sparseC = storeC.sparse
        for (i in 0 until storeA.size) {
            val entity = Entity(entities[i])
            val j = sparseB[entity.index]
            if (j < 0) continue
            val k = sparseC[entity.index]
            if (k < 0) continue
            action(entity, itemsA[i] as A, itemsB[j] as B, itemsC[k] as C)
        }
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> storeOrNull(type: ComponentType<T>): ComponentStore<T>? =
        stores[type] as ComponentStore<T>?

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> store(type: ComponentType<T>): ComponentStore<T> =
        stores.getOrPut(type) { ComponentStore<T>(alive.size) } as ComponentStore<T>

    private fun allocateSlot(): Int {
        if (nextSlot == alive.size) {
            val capacity = alive.size * 2
            alive = alive.copyOf(capacity)
            generations = generations.copyOf(capacity)
            for (store in stores.values) store.ensureEntityCapacity(capacity)
        }
        return nextSlot++
    }

    override fun toString(): String = "World(entities=$entityCount, componentTypes=${stores.size})"
}
