# My game

A starting point for a game made with [klyph](https://github.com/inemtsev/klyph), a 2D game engine
in Kotlin/Native on SDL3. It opens a window with a ship in the middle that flies with the arrow
keys, WASD or a gamepad's left stick. Escape quits.

## Requirements

- JDK 17 or newer, for Gradle. The wrapper downloads Gradle itself.
- SDL3 3.4 or newer. On macOS and Windows the klyph plugin downloads it when none is installed;
  `brew install sdl3` avoids the download on macOS. On Linux install your distribution's package
  (`libsdl3-dev` on Debian 13 and Ubuntu 25.04 or newer, `sdl3` on Arch, `SDL3-devel` on Fedora)
  or build SDL from source.

## Run and ship

```
./gradlew run          # builds the debug executable and plays it
./gradlew distribute   # zips the release executable, assets/ and SDL3 in build/distributions/
```

The zip runs on a machine without SDL3 installed. `./gradlew run -Pklyph.frames=60` stops after 60
frames, and `-Pklyph.screenshot=30:shot.png` saves frame 30 as a PNG.

## Layout

| Path | Contents |
|---|---|
| `build.gradle.kts` | The klyph plugin, the entry point and the engine dependency. |
| `src/nativeMain/kotlin/mygame/Main.kt` | The game. |
| `assets/` | Art and sounds, copied next to the executable. |
| `.github/workflows/build.yml` | Links the game on macOS, Linux and Windows on every push. |

To rename the game, change `rootProject.name` in `settings.gradle.kts`, the package in `Main.kt`
and `entryPoint` in `build.gradle.kts`.

## Where to go next

- [The guide](https://inemtsev.github.io/klyph/guide/index.html): sprites, entities and scenes,
  input, audio, collisions, text and shipping, one topic per page.
- [The API reference](https://inemtsev.github.io/klyph/).
- [The klyph Gradle plugin](https://github.com/inemtsev/klyph/blob/main/klyph-gradle-plugin/README.md),
  every task and setting.
