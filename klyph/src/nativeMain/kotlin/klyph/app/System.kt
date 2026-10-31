package klyph.app

/**
 * Performs one unit of per-phase work, such as moving entities or drawing them.
 *
 * A system is added to a [Phase] and called each time that phase runs, with the [App] it belongs
 * to. Exceptions thrown from [run] are not caught by the loop.
 */
public fun interface System {
    /** Runs this system once against [app]. */
    public fun run(app: App)
}
