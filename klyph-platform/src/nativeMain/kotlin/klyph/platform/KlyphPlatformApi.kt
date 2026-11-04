package klyph.platform

/**
 * Marks the platform plumbing that the klyph engine is built on: the SDL_GPU wrappers, the event
 * pump, the audio device and its renderer, SDL start-up and shutdown, window creation, compiled
 * shaders and frame capture.
 *
 * A game never needs these; `runApp` calls them and exposes what a game uses through `App`. They
 * follow SDL closely and change when the engine needs them to, in any release, so the 1.0
 * compatibility promise does not cover them. Code that calls them anyway, such as a tool or a
 * renderer experiment, opts in with `@OptIn(KlyphPlatformApi::class)` or the compiler option
 * `-opt-in=klyph.platform.KlyphPlatformApi`; without it, using one is a compile error.
 */
@RequiresOptIn(
    message = "Engine plumbing outside the 1.0 compatibility promise; it may change in any release.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class KlyphPlatformApi
