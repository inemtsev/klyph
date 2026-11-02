package klyph.platform

import platform.posix.usleep
import sdl3.SDL_GetCurrentAudioDriver
import sdl3.SDL_HINT_AUDIO_DRIVER
import sdl3.SDL_SetHint
import kotlinx.cinterop.toKString
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class AudioDeviceTest {
    @Test
    fun rendersOnTheAudioThread() = withDummyAudio {
        val calls = AtomicInt(0)
        val device = AudioDevice.openOrNull(sampleRate = 48000, bufferFrames = 512) { output, frames ->
            for (i in 0..<frames * 2) output[i] = 0.25f
            calls.incrementAndFetch()
        }
        assertNotNull(device, "The dummy driver should always open.")
        device.use {
            assertEquals(48000, it.sampleRate)
            assertEquals(512, it.bufferFrames)
            assertTrue(!it.isPaused)
            waitFor { calls.load() > 0 }
            assertTrue(calls.load() > 0, "The renderer was never called.")
            assertNull(it.failureOrNull())
            it.pause()
            assertTrue(it.isPaused)
            it.resume()
            assertTrue(!it.isPaused)
        }
        assertTrue(device.isPaused)
        assertFailsWith<IllegalStateException> { device.resume() }
        device.close()
    }

    @Test
    fun keepsTheFirstRendererFailure() = withDummyAudio {
        val calls = AtomicInt(0)
        val device = AudioDevice.open(sampleRate = 44100, bufferFrames = 256) { _, _ ->
            throw IllegalStateException("Renderer failure ${calls.incrementAndFetch()}.")
        }
        device.use {
            waitFor { it.failureOrNull() != null }
            assertEquals("Renderer failure 1.", it.failureOrNull()?.message)
            // After a failure the renderer is not called again.
            usleep(50_000u)
            assertEquals(1, calls.load())
        }
    }

    @Test
    fun rejectsInvalidArguments() {
        val silence = AudioRenderer { output, frames -> output.fill(0f, 0, frames * 2) }
        assertFailsWith<IllegalArgumentException> { AudioDevice.openOrNull(0, 512, silence) }
        assertFailsWith<IllegalArgumentException> { AudioDevice.openOrNull(48000, 0, silence) }
        assertFailsWith<IllegalArgumentException> { AudioDevice.openOrNull(48000, 1_000_000, silence) }
    }

    @Test
    fun absurdRateDoesNotCrash() = withDummyAudio {
        val silence = AudioRenderer { output, frames -> output.fill(0f, 0, frames * 2) }
        AudioDevice.openOrNull(Int.MAX_VALUE, 1, silence)?.close()
        AudioDevice.openOrNull(1, 65536, silence)?.close()
    }

    @Test
    fun returnsNullWithoutTheAudioSubsystem() {
        val silence = AudioRenderer { output, frames -> output.fill(0f, 0, frames * 2) }
        assertNull(AudioDevice.openOrNull(48000, 512, silence))
        val e = assertFailsWith<SdlException> { AudioDevice.open(48000, 512, silence) }
        assertTrue(e.message.orEmpty().startsWith("SDL_OpenAudioDeviceStream failed: "), e.message)
    }

    /** Runs [block] with SDL's audio subsystem started on the dummy driver, which needs no hardware. */
    private fun withDummyAudio(block: () -> Unit) {
        SDL_SetHint(SDL_HINT_AUDIO_DRIVER, "dummy")
        Sdl.init(setOf(Subsystem.AUDIO))
        try {
            val driver = SDL_GetCurrentAudioDriver()?.toKString()
            if (driver != "dummy") {
                println("AudioDeviceTest: skipped, audio driver is '$driver' rather than 'dummy'.")
                return
            }
            block()
        } finally {
            Sdl.quit()
        }
    }

    /** Polls [condition] for up to two seconds. */
    private inline fun waitFor(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            usleep(10_000u)
        }
    }
}
