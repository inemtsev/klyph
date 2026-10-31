package klyph.event

import kotlin.reflect.KClass

/**
 * Carries events from the code that publishes them to every reader of the same event class.
 *
 * Events are matched by their exact runtime class: an event reaches [forEach] and [any] called
 * with that class, not with one of its supertypes.
 *
 * An event lives for the rest of the frame it was published in and is dropped when the next
 * frame begins. Every system that runs after the publisher in that frame sees it exactly once;
 * a system that runs before the publisher never sees it. The engine publishes its own events
 * at the start of input handling, so every user system sees them. For events between user
 * systems, register the reader in a later phase than the publisher, or later in the same phase.
 * The engine marks the frame boundary by calling the internal `endFrame` function once per frame;
 * users do not call it. A bus created with the public constructor, for example in a test, keeps
 * every event because nothing ends its frame.
 *
 * Not thread-safe.
 */
public class Events {
    private val channels = HashMap<KClass<*>, Channel>()

    /**
     * Adds [event] to the bus. Readers of its class see it from now until the end of the frame,
     * after the events of that class published before it.
     *
     * Publishing from inside [forEach] is allowed: the running iteration does not see the new
     * event, the next one does.
     *
     * @sample samples.event.EventBus.publish
     */
    public fun <T : Any> publish(event: T) {
        channels.getOrPut(event::class) { Channel() }.add(event)
    }

    /**
     * Calls [action] on every event of class [T] published so far this frame, in publish order.
     *
     * The events to visit are fixed when the call starts. Events that [action] publishes are kept
     * and delivered by the next call, so a handler that publishes its own event class cannot loop
     * forever.
     *
     * @sample samples.event.EventBus.publish
     */
    public inline fun <reified T : Any> forEach(action: (T) -> Unit) {
        val channel = channelOrNull(T::class) ?: return
        val current = channel.current
        val size = current.size
        for (i in 0 until size) action(current[i] as T)
    }

    /**
     * Returns `true` if at least one event of class [T] was published this frame.
     *
     * @sample samples.event.EventBus.any
     */
    public inline fun <reified T : Any> any(): Boolean {
        val channel = channelOrNull(T::class) ?: return false
        return channel.current.isNotEmpty()
    }

    /** Returns the events of class [type], or `null` if no event of that class was ever published. */
    @PublishedApi
    internal fun channelOrNull(type: KClass<*>): Channel? = channels[type]

    /**
     * Marks the frame boundary: drops every event of the frame that just ended. The engine calls
     * it once per frame, before input handling. Must not be called from inside [forEach].
     */
    internal fun endFrame() {
        for (channel in channels.values) channel.clear()
    }

    /**
     * Returns `Events(types=<t>, pending=<p>)`, where `t` is the number of event classes with at
     * least one event this frame and `p` is the total number of events this frame.
     */
    override fun toString(): String {
        var types = 0
        var pending = 0
        for (channel in channels.values) {
            val size = channel.current.size
            if (size > 0) types++
            pending += size
        }
        return "Events(types=$types, pending=$pending)"
    }

    /** Holds the events of one class published during the current frame. */
    @PublishedApi
    internal class Channel {
        private val events = ArrayList<Any>()

        /** Events published this frame, in publish order. */
        @PublishedApi
        internal val current: List<Any> get() = events

        /** Appends [event] to this frame's events. */
        fun add(event: Any) {
            events.add(event)
        }

        /** Drops this frame's events, keeping the list's capacity for the next frame. */
        fun clear() {
            events.clear()
        }

        /** Returns `Channel(current=<n>)` with the number of events this frame. */
        override fun toString(): String = "Channel(current=${events.size})"
    }
}
