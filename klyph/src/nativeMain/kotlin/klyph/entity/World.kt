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

    /**
     * Creates an entity with no components.
     *
     * @sample samples.entity.Worlds.create
     */
    public fun create(): Entity {
        val index = if (freeCount > 0) freeSlots[--freeCount] else allocateSlot()
        alive[index] = true
        entityCount++
        return Entity.of(index, generations[index])
    }

    /**
     * Removes [entity], all its descendants and all their components. Does nothing if it is
     * already gone.
     *
     * Every child is gone before its parent, however deep the hierarchy is. [entity] is also
     * removed from its own parent's [childrenOf].
     *
     * @sample samples.entity.Worlds.destroy
     * @sample samples.entity.Hierarchies.destroy
     */
    public fun destroy(entity: Entity) {
        if (!isAlive(entity)) return
        val childrenStore = storeOrNull(Children)
        // Collect the subtree breadth first without recursion, so depth cannot overflow the stack.
        var queue = destroyQueue
        var size = 0
        queue[size++] = entity.raw
        var head = 0
        if (childrenStore != null) {
            while (head < size) {
                val list = childrenStore.getOrNull(Entity(queue[head++]))?.entities ?: continue
                for (k in 0 until list.size) {
                    if (size == queue.size) queue = queue.copyOf(size * 2)
                    queue[size++] = list[k].raw
                }
            }
        }
        destroyQueue = queue
        removeParent(entity)
        // Children come after their parents in the queue, so walking it backwards removes every child first.
        for (i in size - 1 downTo 0) release(Entity(queue[i]))
    }

    /** Removes the components of [entity] and frees its slot. */
    private fun release(entity: Entity) {
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
     * Makes [parent] the parent of [child], so [child]'s [Transform] becomes relative to
     * [parent] and [destroy] on [parent] also destroys [child]. If [child] already has a
     * different parent it moves: it leaves the old parent's [childrenOf] and is appended to the
     * end of [parent]'s. Does nothing if [parent] already is the parent of [child].
     *
     * Both entities must be alive, must differ, and [parent] must not be a descendant of
     * [child], because that would make a cycle.
     *
     * @throws IllegalArgumentException if either entity is not alive, if [child] equals
     *   [parent], or if [parent] is a descendant of [child].
     *
     * @sample samples.entity.Hierarchies.setParent
     */
    public fun setParent(child: Entity, parent: Entity) {
        require(isAlive(child)) { "Child entity $child is not alive." }
        require(isAlive(parent)) { "Parent entity $parent is not alive." }
        require(child != parent) { "Entity $child cannot be its own parent." }
        var ancestor = parentOrNull(parent)
        while (ancestor != null) {
            require(ancestor != child) { "Entity $parent is a descendant of $child, so it cannot be its parent." }
            ancestor = parentOrNull(ancestor)
        }
        val link = storeOrNull(Parent)?.getOrNull(child)
        if (link != null) {
            if (link.entity == parent) return
            detach(link.entity, child)
            link.entity = parent
        } else {
            store(Parent).set(child, Parent(parent))
        }
        val childrenStore = store(Children)
        val siblings = childrenStore.getOrNull(parent) ?: Children().also { childrenStore.set(parent, it) }
        siblings.entities.add(child)
    }

    /**
     * Detaches [child] from its parent, making it a root whose [Transform] is in world space.
     * Does nothing if [child] has no parent.
     *
     * @sample samples.entity.Hierarchies.setParent
     */
    public fun removeParent(child: Entity) {
        val parents = storeOrNull(Parent) ?: return
        val link = parents.getOrNull(child) ?: return
        detach(link.entity, child)
        parents.remove(child)
    }

    /**
     * Returns the parent of [child], or `null` if it has none or is not alive.
     *
     * @sample samples.entity.Hierarchies.setParent
     */
    public fun parentOrNull(child: Entity): Entity? = storeOrNull(Parent)?.getOrNull(child)?.entity

    /**
     * Returns the children of [parent] in the order they were attached, or an empty list if it
     * has none. The list is a copy, so it does not change when the hierarchy does and it is safe
     * to [destroy] or reparent its entities while iterating it.
     *
     * @sample samples.entity.Hierarchies.setParent
     */
    public fun childrenOf(parent: Entity): List<Entity> =
        storeOrNull(Children)?.getOrNull(parent)?.entities?.toList() ?: emptyList()

    /** Removes [child] from [parent]'s children, dropping the record once it is empty. */
    private fun detach(parent: Entity, child: Entity) {
        val childrenStore = storeOrNull(Children) ?: return
        val siblings = childrenStore.getOrNull(parent) ?: return
        siblings.entities.remove(child)
        if (siblings.entities.isEmpty()) childrenStore.remove(parent)
    }

    /**
     * Attaches [component] to [entity] under [type], replacing any component of that type.
     *
     * @throws IllegalArgumentException if [entity] is not alive.
     *
     * @sample samples.entity.Worlds.add
     */
    public fun <T : Any> add(entity: Entity, type: ComponentType<T>, component: T) {
        require(isAlive(entity)) { "Entity $entity is not alive." }
        store(type).set(entity, component)
    }

    /**
     * Detaches the [type] component from [entity]. Returns `true` if there was one.
     *
     * @sample samples.entity.Worlds.remove
     */
    public fun <T : Any> remove(entity: Entity, type: ComponentType<T>): Boolean =
        stores[type]?.remove(entity) ?: false

    /**
     * Returns the [type] component of [entity], or `null` if it has none.
     *
     * @sample samples.entity.Worlds.getOrNull
     */
    public fun <T : Any> getOrNull(entity: Entity, type: ComponentType<T>): T? =
        storeOrNull(type)?.getOrNull(entity)

    /**
     * Returns the [type] component of [entity].
     *
     * @throws NoSuchElementException if [entity] has no such component.
     *
     * @sample samples.entity.Worlds.get
     */
    public fun <T : Any> get(entity: Entity, type: ComponentType<T>): T =
        getOrNull(entity, type) ?: throw NoSuchElementException("Entity $entity has no ${type.name} component.")

    /**
     * Returns `true` if [entity] has a [type] component.
     *
     * @sample samples.entity.Worlds.has
     */
    public fun has(entity: Entity, type: ComponentType<*>): Boolean = getOrNull(entity, type) != null

    /**
     * Returns the number of entities that have a [type] component.
     *
     * @sample samples.entity.Worlds.count
     */
    public fun count(type: ComponentType<*>): Int = storeOrNull(type)?.size ?: 0

    /**
     * Calls [action] for every entity that has both an [a] and a [b] component. Iterates the
     * [a] components in storage order; do not add or remove components of these types inside [action].
     *
     * @sample samples.entity.Worlds.forEach
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
     *
     * @sample samples.entity.Worlds.forEach
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

    /** Work stack of raw entity handles reused by [propagateTransforms] across frames. */
    internal var transformStack: LongArray = LongArray(32)

    /** Work queue of raw entity handles reused by [destroy] to collect a subtree. */
    private var destroyQueue: LongArray = LongArray(32)

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> store(type: ComponentType<T>): ComponentStore<T> =
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
