# Module klyph

klyph is a 2D game engine in pure Kotlin/Native on SDL3 and SDL_GPU. A game is a Gradle project
with one plugin and one dependency, and it compiles to a native executable for macOS, Linux or
Windows. This site holds the guide and the API reference.

## Guide

Read the pages in order for a tour, or jump to a topic. The <a href="guide/index.html">contents page</a> has
the same list.

1. <a href="guide/getting-started.html">Getting started</a>: the build files, SDL3 on each system, a first
   window with `runApp` and the `KLYPH_*` variables.
2. <a href="guide/sprites-and-animation.html">Sprites and animation</a>: assets, sprites, sheets, animations
   and the camera.
3. <a href="guide/entities-systems-and-scenes.html">Entities, systems and scenes</a>: the world, phases and
   the order of a frame, the fixed step, scenes, `Lifetime`, `Fade`, resources and events.
4. <a href="guide/input.html">Input</a>: the keyboard by position and by label, the mouse in points and in
   the world, and gamepads.
5. <a href="guide/audio.html">Audio</a>: sound effects with pitch and pan, music with fades, volumes and
   running without a device.
6. <a href="guide/collisions-and-particles.html">Collisions and particles</a>: colliders, layers, `Overlap`
   events, shape queries and particle emitters.
7. <a href="guide/platformers.html">Platformers</a>: solids, one-way ledges, kinematic bodies with gravity,
   coyote time and a jump buffer, and sweep queries.
8. <a href="guide/text-canvas-and-overlay.html">Text, canvas and overlay</a>: fonts, the `Text` component,
   drawing on `app.canvas` and the debug overlay.
9. <a href="guide/shipping.html">Shipping</a>: the distribution zip, SDL3 at runtime, fullscreen, the cursor
   and screenshots on CI.
10. <a href="guide/performance.html">Performance</a>: what each engine stage costs and how to keep a
    frame cheap.

## API reference

The two modules below hold every public declaration. A game depends on `klyph`; `klyph-platform`
comes with it and holds the colors, images, key names and window a game uses alongside it.
