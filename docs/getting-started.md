<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/GettingStarted.kt -->
# Getting started

This page takes you from an empty folder to a window with a ship you can fly, then shows how to
run it, bound a run and pass settings from the command line. A klyph game is a Gradle project
with one plugin, one dependency and a `main` function that calls `runApp`.

The fastest start is the template repository,
[inemtsev/klyph-template](https://github.com/inemtsev/klyph-template), which holds everything on
this page. The rest of the page builds the same project by hand, so you know what each file does.

## What you need

- **JDK 17 or newer.** Gradle runs on the JVM. The game itself does not: klyph compiles to a
  native executable with Kotlin/Native, and players need no Java.
- **SDL3 3.4 or newer.** SDL opens the window, reads input, plays audio and talks to the GPU. The
  klyph Gradle plugin looks for an installed SDL3 first and downloads one when it finds none on
  macOS and Windows.
- **An Apple silicon Mac, a Linux machine (x64 or arm64) or a Windows x64 machine.** The build
  makes an executable for the machine it runs on.

How SDL3 gets onto each system:

| System | SDL3 |
|---|---|
| macOS | `brew install sdl3`, or nothing: the plugin downloads SDL's official release and checks its SHA-256. |
| Windows | Nothing: the plugin downloads SDL's MinGW package. Every link copies `SDL3.dll` next to the executable. |
| Debian 13, Ubuntu 25.04 or newer | `sudo apt install libsdl3-dev` |
| Fedora | `sudo dnf install SDL3-devel` |
| Arch | `sudo pacman -S sdl3` |
| Older Linux | Build SDL from source as its [README-linux](https://github.com/libsdl-org/SDL/blob/main/docs/README-linux.md) says, install it into `/usr/local`, or pass its folder with `-Psdl3.prefix=<path>`. |

The plugin searches `-Psdl3.prefix`, then Homebrew, `/usr/local` and `/usr`. Its
[README](https://github.com/inemtsev/klyph/blob/main/klyph-gradle-plugin/README.md) lists every
folder it tries and where downloads are cached.

## The project

A game has this layout:

```
my-game/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/
  src/nativeMain/kotlin/mygame/Main.kt
  assets/ship.png
```

`settings.gradle.kts` names the repositories that hold the plugin and the engine:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-game"
```

`build.gradle.kts` is three lines:

```kotlin
plugins { id("com.eventslooped.klyph") version "1.0.0" }
klyph { entryPoint = "mygame.main" }
kotlin { sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:1.0.0") } }
```

The plugin applies Kotlin Multiplatform itself, so the build file does not declare Kotlin.
`entryPoint` is the fully qualified name of your `main` function. Copy the Gradle wrapper
(`gradlew`, `gradlew.bat` and `gradle/wrapper/`) from the template or create it with
`gradle wrapper` if you have Gradle installed.

`gradle.properties` gives Gradle enough memory for the Kotlin/Native compiler and turns on the
configuration cache:

```
org.gradle.jvmargs=-Xmx4g
org.gradle.configuration-cache=true
org.gradle.caching=true
```

The first build downloads the Kotlin/Native compiler and its toolchain into `~/.konan`, which
takes a few minutes once.

## A first window

`runApp` opens a window, runs the frame loop until something calls `app.quit()`, and closes
everything before it returns. Its trailing lambda is the setup block, where you add systems: plain
functions the loop calls in a given phase of every frame.

<!-- snippet: first-window -->
```kotlin
fun main() {
    runApp(AppConfig(title = "My game", width = 1280, height = 720)) {
        addSystem(Phase.INPUT) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
        }
    }
}
```

This opens a 1280 by 720 window cleared to a dark blue every frame. The window's close button
also ends the loop. `AppConfig` takes every setting by name and has a default for each, so pass
only what you change.

## A ship that flies

The template's `src/nativeMain/kotlin/mygame/Main.kt` puts `assets/ship.png` in the middle of the
window and flies it with the arrow keys, WASD or a gamepad's left stick. It starts with
`package mygame` and these imports:

<!-- snippet: imports -->
```kotlin
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.input.axis
import klyph.platform.Scancode
import klyph.render.Sprite
import klyph.render.asRegion
import kotlin.math.sqrt
```

<!-- snippet: speed -->
```kotlin
/** How fast the ship flies, in world units per second. At the camera's default zoom a unit is a point. */
private const val SHIP_SPEED = 200f
```

<!-- snippet: main -->
```kotlin
fun main() {
    val config = AppConfig.fromEnvironment(AppConfig(title = "My game", width = 1280, height = 720))
    runApp(config) {
        // World (0, 0) is the window's top-left corner, so this is the middle of the window.
        val ship = world.create()
        world.add(ship, Transform, Transform(x = window.width / 2f, y = window.height / 2f))
        world.add(ship, Sprite, Sprite(assets.texture("ship.png").asRegion()))
        world.add(ship, Interpolated, Interpolated)

        addSystem(Phase.INPUT) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
        }

        addSystem(Phase.FIXED_UPDATE) { app ->
            val keyboard = app.input.keyboard
            var dx = keyboard.axis(Scancode.LEFT, Scancode.RIGHT) + keyboard.axis(Scancode.A, Scancode.D)
            var dy = keyboard.axis(Scancode.UP, Scancode.DOWN) + keyboard.axis(Scancode.W, Scancode.S)
            val pad = app.input.gamepads.firstOrNull()
            if (pad != null) {
                dx += pad.leftStick.x
                dy += pad.leftStick.y
            }
            // Two keys at once, or a key and the stick, must not fly faster than one.
            val length = sqrt(dx * dx + dy * dy)
            if (length > 1f) {
                dx /= length
                dy /= length
            }
            val transform = app.world.get(ship, Transform)
            transform.x += dx * SHIP_SPEED * app.time.deltaSeconds
            transform.y += dy * SHIP_SPEED * app.time.deltaSeconds
        }
    }
}
```

What each part does:

- The ship is an **entity** with three **components**: a `Transform` that places it, a `Sprite`
  that draws a texture at that place, and the `Interpolated` marker. Entities and components are
  covered in [Entities, systems and scenes](entities-systems-and-scenes.md).
- `assets.texture("ship.png")` loads the file from the `assets/` folder next to the executable.
  The plugin copies your project's `assets/` there on every build.
- The camera starts at zoom 1, where one world unit is one point, the unit window sizes are given
  in, and world (0, 0) is the window's top-left corner with y pointing down.
- The movement system runs in `FIXED_UPDATE`, which runs 60 times a second of game time whatever
  the frame rate. Inside it `time.deltaSeconds` is the fixed step, 1/60 s. `Interpolated` draws
  the ship between its last two steps, so it moves smoothly on a 120 Hz display too.
- `keyboard.axis(negative, positive)` gives -1, 0 or 1 for a pair of keys. Keys are named by
  `Scancode`, their position on the keyboard, so WASD works on every layout.

## Run it

```
./gradlew run
```

`run` links the debug executable for your machine, copies `assets/` next to it and starts it.
`./gradlew runRelease` does the same with the optimized build, which is the one to judge speed
by. Gradle prints the program's output, and the build fails if the game exits with an error.

The executables land in `build/bin/<target>/debugExecutable/` and `releaseExecutable/`, named
after the project (`my-game.kexe` on macOS and Linux, `my-game.exe` on Windows), with `assets/`
beside them. You can start them directly.

## Settings from the environment

`AppConfig.fromEnvironment(base)` returns `base` with the settings that `KLYPH_*` environment
variables give replaced. The template calls it, so a run from a script or CI can bound itself
and take screenshots without code changes. Through Gradle, pass each variable as a property:
`-Pklyph.frames=60` sets `KLYPH_FRAMES=60` for the run.

| Variable | Gradle property | Effect |
|---|---|---|
| `KLYPH_FRAMES=<n>` | `-Pklyph.frames=<n>` | Quits after `n` frames. |
| `KLYPH_SECONDS=<s>` | `-Pklyph.seconds=<s>` | Quits once game time reaches `s` seconds, such as `2.5`. |
| `KLYPH_VSYNC=1` or `0` | `-Pklyph.vsync=1` | Waits for the display's refresh, or presents as fast as possible. |
| `KLYPH_OFFSCREEN=1` | `-Pklyph.offscreen=1` | Draws into a texture and waits for the GPU each frame, for measuring. |
| `KLYPH_VALIDATION=1` | `-Pklyph.validation=1` | Turns on the GPU validation layer, which is slow. |
| `KLYPH_OVERLAY=1` | `-Pklyph.overlay=1` | Shows the debug overlay from the first frame. |
| `KLYPH_FULLSCREEN=1` | `-Pklyph.fullscreen=1` | Starts in borderless fullscreen. |
| `KLYPH_SCREENSHOT=<frame>:<path>` | `-Pklyph.screenshot=30:shot.png` | Saves frame `frame`, counted from 1, as a PNG. |

```
./gradlew run -Pklyph.frames=60 -Pklyph.screenshot=30:shot.png
```

Through Gradle a relative screenshot path resolves against the project folder. A variable that is
not set keeps the value of `base`; a value outside the listed ones stops the game with an
`IllegalArgumentException` that names the variable. A malformed `KLYPH_SCREENSHOT` is ignored.

## When something goes wrong

- **The build cannot find SDL3** on Linux: install the package from the table above, or build
  SDL and pass `-Psdl3.prefix=<folder>`, the folder that holds `include/SDL3/SDL.h`.
- **`assets.texture` throws `FileException`**: the file is not in `assets/` or the name differs in
  case. Linux file names are case-sensitive.
- **No sound and one line on standard error**: the machine has no audio device. The game runs on
  silently; [Audio](audio.md) explains this.
- **The first build is slow**: it downloads the Kotlin/Native toolchain once. Later builds of a
  small game take seconds.

## See also

- [runApp](../klyph/klyph.app/run-app.html)
- [AppConfig](../klyph/klyph.app/-app-config/index.html) and
  [AppConfig.fromEnvironment](../klyph/klyph.app/-app-config/-companion/from-environment.html)
- [App](../klyph/klyph.app/-app/index.html)
- [Phase](../klyph/klyph.app/-phase/index.html)
- Next: [Sprites and animation](sprites-and-animation.md)
