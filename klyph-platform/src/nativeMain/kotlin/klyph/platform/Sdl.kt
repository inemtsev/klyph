package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.toKString
import sdl3.SDL_GetError
import sdl3.SDL_INIT_AUDIO
import sdl3.SDL_INIT_EVENTS
import sdl3.SDL_INIT_GAMEPAD
import sdl3.SDL_INIT_VIDEO
import sdl3.SDL_Init
import sdl3.SDL_Quit
import sdl3.SDL_getenv

/**
 * Thrown when an SDL call fails. The message names the call and repeats SDL's own error text.
 */
public class SdlException(message: String) : KlyphException(message)

/** Throws [SdlException] for the SDL function [call], reading the reason from SDL's error state. */
internal fun sdlFailure(call: String): Nothing = throw SdlException(sdlFailureMessage(call))

/** Returns the [SdlException] message for the SDL function [call], reading the reason from SDL's error state. */
internal fun sdlFailureMessage(call: String): String = "$call failed: ${sdlErrorText()}."

/** Returns SDL's current error text without its trailing period, or `unknown error` when it is empty. */
internal fun sdlErrorText(): String = SDL_GetError()?.toKString().orEmpty().trim().trimEnd('.').ifEmpty { "unknown error" }

/**
 * Parts of SDL that [Sdl.init] can start.
 */
@KlyphPlatformApi
public enum class Subsystem(internal val flag: UInt) {
    /** Windows and the display; also starts events. */
    VIDEO(SDL_INIT_VIDEO),

    /** Audio playback devices. */
    AUDIO(SDL_INIT_AUDIO),

    /** The event queue. */
    EVENTS(SDL_INIT_EVENTS),

    /** Gamepad and joystick input. */
    GAMEPAD(SDL_INIT_GAMEPAD),
}

/**
 * Process-wide SDL lifecycle. SDL itself is global, so this is the one place the platform layer
 * exposes that fact.
 */
public object Sdl {
    /**
     * Starts the given [subsystems] in addition to those already running; SDL counts each
     * subsystem separately, so the call may be repeated for different subsystems. Start video
     * before using any other platform function. [quit] shuts all of them down.
     *
     * @throws SdlException if SDL cannot start one of them.
     */
    @KlyphPlatformApi
    public fun init(subsystems: Set<Subsystem>) {
        var flags = 0u
        for (subsystem in subsystems) flags = flags or subsystem.flag
        if (!SDL_Init(flags)) sdlFailure("SDL_Init")
    }

    /** Shuts down every running subsystem, however many times [init] started it. Nothing from this package may be used afterwards. */
    @KlyphPlatformApi
    public fun quit() {
        SDL_Quit()
    }

    /**
     * Returns the value of the environment variable [name], or `null` when it is not set.
     */
    public fun environmentVariable(name: String): String? = SDL_getenv(name)?.toKString()
}
