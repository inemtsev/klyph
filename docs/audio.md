<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Audio.kt -->
# Audio

klyph plays sound effects and music through `app.audio` and mixes them itself, on SDL's audio
thread. Calls return at once: each one queues a command that the
audio thread applies before it mixes its next block, a few milliseconds later. A game without an
audio device runs the same, silently.

The snippets on this page are code for the setup block of `runApp`.

## Sound effects

A `Sound` is an effect decoded fully into memory, from a WAV or Ogg Vorbis file, mono or stereo.
`assets.sound(path)` loads it once and returns the same `Sound` on later calls. `audio.play`
starts it on a free voice and returns a `Voice`, a handle to change or stop that playback.

<!-- snippet: load-play -->
```kotlin
val shot = assets.sound("shot.wav")
addSystem(Phase.UPDATE) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.SPACE)) {
        // A slightly different pitch each time keeps a repeated sound from droning.
        app.audio.play(shot, volume = 0.6f, pitch = 0.9f + 0.2f * Random.nextFloat())
    }
}
```

`play` takes, by name:

| Parameter | Range | Effect |
|---|---|---|
| `volume` | 0 to 1, clamped | Loudness of this voice, before the effects and master volumes. |
| `pitch` | 0.25 to 4 | Playback speed: 2 plays an octave up in half the time. Outside the range it throws `IllegalArgumentException`. |
| `pan` | -1 (left) to 1 (right), clamped | Position in the stereo field. A mono sound keeps an even loudness across it. |
| `mode` | `PlaybackMode.ONCE` or `LOOP` | `LOOP` repeats until `stop`. |

A small random pitch, as above, keeps a sound repeated many times a second from sounding
mechanical. Panning by screen position puts a sound where its source is:

<!-- snippet: pan -->
```kotlin
val explosion = assets.sound("explosion.wav")
addSystem(Phase.UPDATE) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.X)) {
        val x = app.world.get(enemy, Transform).x
        // -1 at the window's left edge, 1 at its right edge.
        val pan = (x / app.window.width * 2f - 1f).coerceIn(-1f, 1f)
        app.audio.play(explosion, pan = pan)
    }
}
```

## Voices

The mixer has `AudioConfig.maxVoices` voices, 32 by default. When every voice is busy, a new sound
takes the voice of the one that started earliest, and the old handle stops working. A handle to a
sound that ended, was stopped or was replaced does nothing: `setVolume`, `setPitch`, `setPan`
and `stop` on it are ignored and `isPlaying` returns `false`. That makes it safe to keep handles
around without checking them first. `Voice.NONE` is a handle that refers to nothing, a good
starting value.

<!-- snippet: voice -->
```kotlin
val hum = assets.sound("engine.wav")
var engine = Voice.NONE
addSystem(Phase.UPDATE) { app ->
    val throttle = if (app.input.keyboard.isDown(Scancode.W)) 1f else 0f
    if (throttle > 0f && !app.audio.isPlaying(engine)) {
        engine = app.audio.play(hum, volume = 0.5f, mode = PlaybackMode.LOOP)
    }
    app.audio.setPitch(engine, 1f + throttle)
    if (throttle == 0f) app.audio.stop(engine)
}
```

Volume and pan changes ramp over a few milliseconds, so they do not click. `audio.stopAll()`
stops every effect and leaves the music playing.

## Music

A `MusicTrack` is an Ogg Vorbis file kept encoded in memory and decoded a block at a time while
it plays, so a long track costs little memory. `assets.musicTrack(path)` loads it, and
`audio.music` plays one track at a time:

<!-- snippet: music -->
```kotlin
val theme = assets.musicTrack("music.ogg")
val boss = assets.musicTrack("boss.ogg")
audio.music.play(theme, fadeIn = 2.seconds)
addSystem(Phase.UPDATE) { app ->
    val keyboard = app.input.keyboard
    // Playing another track crosses the two over the same fade.
    if (keyboard.isJustPressed(Scancode.B)) app.audio.music.play(boss, fadeIn = 1.seconds)
    if (keyboard.isJustPressed(Scancode.M)) app.audio.music.stop(fadeOut = 1.seconds)
}
```

- `music.play(track, mode, fadeIn)` starts a track from its beginning, looping by default, fading
  in from silence over `fadeIn`. If another track is playing, it fades out over the same time
  while the new one fades in. Playing the same track again restarts it.
- `music.stop(fadeOut)` fades the track out and stops it. `music.isPlaying` stays `true` until the
  fade has finished.
- `music.currentOrNull` is the track that is playing, or `null`.

The audio thread ends a track that reaches its end, so `isPlaying` and `currentOrNull` can change
between two reads in one frame.

## Volumes

Four volumes multiply together. Each is from 0 to 1, clamped, and starts at 1.

<!-- snippet: volumes -->
```kotlin
audio.masterVolume = 0.8f
audio.effectsVolume = 1f
audio.musicVolume = 0.5f
// On top of musicVolume, for ducking the current track under a pause menu.
audio.music.volume = 0.3f
```

| Volume | Applies to |
|---|---|
| `audio.masterVolume` | Everything. |
| `audio.effectsVolume` | Every sound effect, on top of each voice's own volume. |
| `audio.musicVolume` | Music, on top of `audio.music.volume`. |
| `audio.music.volume` | The music channel alone, for ducking under a pause menu. |

A good split: let the player set the first three in a settings menu, and keep the fourth for the
game's own fades. Setting any of them to `NaN` throws `IllegalArgumentException`.

## No audio device

When the engine cannot open an audio device, as on a CI runner or a machine with no sound card,
`runApp` prints one line to standard error and the game runs on. `audio.isEnabled` is then
`false`, every call does nothing, `play` returns `Voice.NONE` and `isPlaying` returns `false`.
Game code needs no checks for this.

To turn audio off on purpose, for tests, a dedicated server or an autopilot run, set
`AudioConfig(output = AudioOutput.NONE)`. The engine then opens no device and prints nothing:

<!-- snippet: no-device -->
```kotlin
val quiet = AudioConfig(output = AudioOutput.NONE)
runApp(AppConfig(title = "Headless check", audio = quiet, frameLimit = 120)) {
    addSystem(Phase.STARTUP) { app ->
        // Every audio call does nothing, and play returns Voice.NONE.
        println("Audio on: ${app.audio.isEnabled}")
    }
}
```

If the mixer ever throws on the audio thread, the engine prints the exception once and turns
audio off the same way.

## Tuning the mixer

`AudioConfig` sets the mixer up when the app starts. `maxVoices` is from 1 to 256. `sampleRate`,
48000 by default, is the rate the mixer runs at; sounds at other rates are resampled as they
play. `bufferFrames`, 1024 by default, is how many frames the device asks for at a time: a smaller
buffer lowers latency, a larger one rides out longer pauses of Kotlin/Native's garbage collector
without a click. `audio.stats` reports voices playing, voices stolen and commands dropped, as of
the start of the frame:

<!-- snippet: config -->
```kotlin
val audio = AudioConfig(maxVoices = 64, bufferFrames = 512)
runApp(AppConfig(title = "Busy game", audio = audio)) {
    addSystem(Phase.UPDATE) { app ->
        val stats = app.audio.stats
        if (stats.stolenVoiceCount > 0) println("${stats.activeVoiceCount} voices playing, ${stats.stolenVoiceCount} stolen so far")
    }
}
```

Mixing 32 voices and a music track takes about 0.06 ms per 21 ms block on an Apple M4 Pro, and
the mixer allocates nothing, so it never starts a garbage collection itself. See
[Performance](performance.md).

## Making sounds

The sample shooter's effects are synthesized by a short Python script in
`games/shooter/tools/synth.py` that writes WAV files with the standard library only, which is a
quick way to get placeholder sounds. Any tool that exports WAV or Ogg Vorbis works. Keep
effects mono when their position does not matter; pan works on mono sounds too.

## See also

- [Audio](../klyph/klyph.audio/-audio/index.html) and
  [Voice](../klyph/klyph.audio/-voice/index.html)
- [Music](../klyph/klyph.audio/-music/index.html) and
  [MusicTrack](../klyph/klyph.audio/-music-track/index.html)
- [Sound](../klyph/klyph.audio/-sound/index.html)
- [AudioConfig](../klyph/klyph.audio/-audio-config/index.html) and
  [AudioOutput](../klyph/klyph.audio/-audio-output/index.html)
- Next: [Collisions and particles](collisions-and-particles.md)
