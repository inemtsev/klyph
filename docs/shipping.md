<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Shipping.kt -->
# Shipping

A klyph game ships as a folder with a native executable, its `assets/` and the SDL3 library. The
Gradle plugin builds that folder and zips it. This page covers the zip, how the executable finds
SDL3 on a player's machine, the window features a finished game wants (fullscreen, cursor,
screenshots) and how CI can run the game and keep a picture of every build.

## distribute

```
./gradlew distribute
```

`distribute` links the release executable for your machine and writes
`build/distributions/<project>-<version>-<target>.zip`, or `<project>-<target>.zip` when the
project sets no `version`. The template's zip on an Apple silicon Mac holds:

```
my-game-macosArm64/
  my-game.kexe
  libSDL3.0.dylib
  assets/
    ship.png
```

| System | Executable | SDL3 in the folder |
|---|---|---|
| macOS | `my-game.kexe` | `libSDL3.0.dylib`, or `SDL3.framework` when the plugin downloaded SDL |
| Linux | `my-game.kexe` | `libSDL3.so.0` |
| Windows | `my-game.exe` | `SDL3.dll` |

Everything in your project's `assets/` goes in, except files matching `klyph { assetExcludes }`,
which leaves out `README.md` by default. Add patterns such as `"*.psd"` or `"drafts/**"` to keep
source files out.

## SDL3 at runtime

Release executables look for SDL3 in their own folder first (`@executable_path` on macOS,
`$ORIGIN` on Linux, and the executable's folder on Windows, as always there), so the unzipped
folder runs on a machine with no SDL3 installed. On macOS the plugin rewrites the copied
executable to load SDL3 through `@rpath` and signs it again ad hoc.

The plugin does not sign or notarize for distribution and builds no `.app` bundle. A player on
macOS who downloads the zip from the web may need to allow the executable in System Settings,
Privacy and Security, the first time. On Linux the SDL3 the plugin copies is the one it linked
against; SDL loads the X11, Wayland and audio backends it finds on the player's system.

## Building for another system

Kotlin/Native builds for the machine it runs on. To build every desktop, build on each, which is
what the template's CI workflow does: it links on macOS, Linux and Windows runners. A Windows zip
can also be cross-built from macOS or Linux with SDL's MinGW package:

```
./gradlew distributeMingwX64 -Pklyph.target=mingwX64 -Psdl3.prefix=<SDL3-devel-mingw folder>/x86_64-w64-mingw32
```

The plugin's [README](https://github.com/inemtsev/klyph/blob/main/klyph-gradle-plugin/README.md)
covers targets, cross builds and the SDL3 search in full.

## Configuration from the environment

Build the config from `AppConfig.fromEnvironment(base)`, so the same binary can be bounded,
switched to fullscreen or told to take a screenshot by `KLYPH_*` variables without code changes.
[Getting started](getting-started.md) lists them.

<!-- snippet: from-environment -->
```kotlin
val config = AppConfig.fromEnvironment(
    AppConfig(title = "My game", width = 1280, height = 720, cursor = CursorMode.HIDDEN),
)
runApp(config) {
    // The game itself.
}
```

A setting the environment does not mention keeps the value from `base`. That is also how a
shipped game picks its defaults: put them in `base`.

## Fullscreen and the cursor

`app.window.mode` switches between `WindowMode.WINDOWED` and `WindowMode.FULLSCREEN`, which is
borderless fullscreen on the window's display at its own resolution, with no change of video mode,
so switching is quick. `AppConfig.windowMode` sets it at startup and `KLYPH_FULLSCREEN=1` does so
from the environment. Each change of size arrives as a resize, and the engine updates the
camera's viewport. On macOS the switch animates for about half a second, and the player can leave
fullscreen with the window's own controls, so read `mode` rather than remembering what you set.

`app.window.cursor` hides the system cursor while it is over the window (`CursorMode.HIDDEN`) or
shows it (`VISIBLE`); mouse input arrives either way. `AppConfig.cursor` sets it at startup.

<!-- snippet: window -->
```kotlin
addSystem(Phase.INPUT) { app ->
    val window = app.window
    if (app.input.keyboard.isJustPressed(Scancode.F11)) {
        window.mode = if (window.mode == WindowMode.FULLSCREEN) WindowMode.WINDOWED else WindowMode.FULLSCREEN
    }
    if (app.input.keyboard.isJustPressed(Scancode.C)) {
        window.cursor = if (window.cursor == CursorMode.VISIBLE) CursorMode.HIDDEN else CursorMode.VISIBLE
    }
}
```

`window.setCursor(image, hotspotX, hotspotY)` replaces the arrow with an `Image`; the hotspot is
the pixel clicks land on. The cursor keeps its own copy of the pixels. `resetCursor()` goes back
to the arrow.

<!-- snippet: cursor-image -->
```kotlin
val image = Image(15, 15)
for (i in 0 until 15) {
    image[i, 7] = Color.WHITE
    image[7, i] = Color.WHITE
}
// The middle pixel is where clicks land.
window.setCursor(image, hotspotX = 7, hotspotY = 7)
```

A game that draws its own pointer hides the cursor and draws a sprite at the mouse position,
converted to world units after the camera has moved, as [Input](input.md) shows.

## Screenshots

`app.window.capture(path)` saves the next frame drawn as a PNG. The capture is read back once the
GPU has finished the frame, and the file is written before the following frame starts. A relative
path resolves against the executable's folder, and a missing folder is created. A capture that fails,
for example because the file cannot be written, prints one line to standard error and is dropped.

<!-- snippet: capture -->
```kotlin
addSystem(Phase.INPUT) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.F12)) {
        // Written next to the executable once this frame is drawn.
        app.window.capture("screenshot-${app.time.frameCount}.png")
    }
}
```

`AppConfig.screenshot = Screenshot(frame, path)` captures a given frame, counted from 1:

<!-- snippet: screenshot-config -->
```kotlin
runApp(AppConfig(title = "My game", screenshot = Screenshot(frame = 60, path = "frame-60.png"), frameLimit = 61)) {
    // The game itself.
}
```

## Screenshots on CI

`KLYPH_SCREENSHOT=<frame>:<path>` sets the same thing from the environment, and the capture works
offscreen too. A CI job can therefore run the game for a few frames and keep a picture of every
build, on a runner with a GPU and a display server:

```
./gradlew run -Pklyph.frames=40 -Pklyph.screenshot=30:build/shot.png -Pklyph.offscreen=1
```

Through Gradle a relative path resolves against the project folder. `KLYPH_FRAMES` stops the run
so the job ends. Hosted runners usually have no GPU, which is why the template's workflow only
links; a runner with a GPU can run the game and upload `build/shot.png` as an artifact.

Without an audio device the game prints one line and runs silently, so CI needs no sound setup.

## A checklist before you ship

- Run `./gradlew runRelease` once and play it: the release build is the one players get.
- Unzip the distribution on a machine without SDL3, or a fresh user account, and start it there.
- Check that every file the game loads is in `assets/` and not excluded.
- Give the window a title and pick defaults for fullscreen and the cursor in `base`.
- Decide what Escape does: quitting on Escape suits a prototype, a menu suits a release.

## See also

- [Window](../klyph-platform/klyph.platform/-window/index.html),
  [WindowMode](../klyph-platform/klyph.platform/-window-mode/index.html) and
  [CursorMode](../klyph-platform/klyph.platform/-cursor-mode/index.html)
- [Screenshot](../klyph/klyph.app/-screenshot/index.html)
- [AppConfig](../klyph/klyph.app/-app-config/index.html)
- [Image](../klyph-platform/klyph.platform/-image/index.html)
- Next: [Performance](performance.md)
