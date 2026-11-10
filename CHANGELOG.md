# Changelog

All notable changes to klyph are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `Collider.scalesWithTransform`, `true` by default. Set to `false`, the collider keeps its shape
  and offset at scale 1 whatever the entity's scale, so a squash or a spin animated through
  `Transform.scaleX` and `scaleY` leaves the hitbox as it is.
- `Time.fixedStepCount`, the number of `FIXED_UPDATE` steps completed so far.
- `Velocity(dx, dy)`, a component the engine applies to its entity's `Transform` in every fixed
  step, after the FIXED_UPDATE systems and by the fixed step length. `Interpolated` entities are
  recorded before the move, and entities of a frozen scene stand still.
- `ParticleEmitter.burst(region, count, ...)`, which makes an emitter that sends out one burst of
  `count` particles and then destroys its entity, and `Commands.spawnBurst(x, y, emitter)`, which
  spawns the entity that carries it.
- `Animation.speed` and `SpriteAnimation.speed`, a playback rate multiplier; 0 holds the current
  frame. `Animation` and `SpriteAnimation` include it in `toString`.

### Changed

- `isJustPressed` and `isJustReleased` on `Keyboard`, `Mouse`, `Gamepad` and `List<Gamepad>` now
  count fixed steps inside `FIXED_UPDATE`: a press or release is seen by the first step after it
  and by no other. In 1.0 they answered for the frame in every phase, so a system reading them in
  `FIXED_UPDATE` missed a press in a frame that ran no step, which happens on displays faster than
  the fixed rate, and saw it twice in a frame that ran two. This is a behaviour change for 1.0
  games that read edges in `FIXED_UPDATE`: they now get each press once, and a hand-made latch
  that queued presses from `INPUT` for the next step can go. A tap shorter than a step reads as
  both just pressed and just released in that step. Every other phase still answers for the
  frame, and `isDown` is unchanged.
- Rooftop Roller reads its jump press in `FIXED_UPDATE` and drops its jump latch; its autopilot
  decides once per fixed step instead of once per frame.

## [1.0.0] - 2026-11-04

### Added

- API reference built with Dokka, with samples shown inline, published to GitHub Pages from
  `v*` tags.
- Release workflow that publishes the engine and the Gradle plugin to Maven Central from `v*`
  tags and runs the consumer test against Central.
- `CONTRIBUTING.md`, this changelog and a bug report template.
- `@KlyphPlatformApi`, an opt-in marker (an error without opt-in) on the platform plumbing in
  `klyph.platform` that only the engine calls: the GPU wrappers, `EventPump` and `PlatformEvent`,
  `AudioDevice`, `AudioRenderer` and `Audio.renderer`, `Sdl.init` and `Sdl.quit`, the `Window`
  factory and `WindowFlag`, `CompiledShader`, `FrameCapture` and `printError`. They are outside
  the 1.0 compatibility promise. Games use none of them.
- `AudioOutput` with `DEVICE` and `NONE`, for `AudioConfig.output`.
- `Assets.spriteSheetOrNull` and `Files.readTextOrNull`.
- `World(initialCapacity)` rejects a capacity below 1 with `IllegalArgumentException` instead of
  failing on the first `create`.
- `Color.BLACK`, `RED`, `GREEN`, `BLUE`, `YELLOW`, `CYAN`, `MAGENTA` and `GRAY`, all opaque, next
  to `WHITE` and `TRANSPARENT`.
- `Keyboard.axis(negative: KeyCode, positive: KeyCode)`, which finds both keys by their labels
  under the current layout, beside the `Scancode` overload.

### Changed

- The sample games moved to `games/shooter` and `games/squish`; their Gradle paths are now
  `:games:shooter` and `:games:squish`.
- The shooter comes in waves, shown as "Wave n" in the HUD, and gains a diver, an enemy that
  enters from the top of the view and swoops through the ship's position on an arc.
- Every fifth wave of the shooter is a boss: the diver at three times the size, with 12 hit points
  shown as a bar above it, sweeping the top of the arena and firing bullets down every second.
  Destroying it scores 50 and starts the next wave.
- Bug Squish hides the system cursor over its window, since the fly swatter is the pointer, and
  F11 switches it to fullscreen and back.
- Both sample games use the gameplay kit (`Lifetime`, `Fade`, `AnimationEnd`, `EmitterEnd`,
  `EmitterSpace`, `Camera.follow` and `Camera.bounds`, `TextShadow`, `Overlap.otherThan`, the
  gamepad list queries, `Assets.disc`) in place of their own copies.
- CI runs the sandbox offscreen on every job and uploads frame 20 as a PNG artifact; the step must
  pass on macOS and is informational on Linux and Windows, whose runners have no GPU.
- The published javadoc jars hold the Dokka HTML reference instead of being empty.
- `Camera` works in points: `viewportWidth` and `viewportHeight` are the window's size in points
  (now `Float`), zoom 1 is one world unit per point, and `worldToScreen` and `screenToWorld` are
  in points, like `Mouse.position` and `DrawSpace.SCREEN`. `App.camera` starts at zoom 1 on every
  display; the renderer applies the pixel density. A game that set `zoom = 2 * window.pixelDensity`
  sets `zoom = 2`.
- A change of pixel density no longer changes `App.camera.zoom`.
- `Canvas.drawSprite` and `Canvas.drawRect` default to `DrawSpace.SCREEN`, as `drawText` already
  did; pass `space = DrawSpace.WORLD` to draw through the camera.
- `ParticleEmitter.lifetime` is a `ClosedRange<Duration>`, such as `0.5.seconds..1.seconds`.
- `AudioConfig(enabled = false)` → `AudioConfig(output = AudioOutput.NONE)`; `AudioConfig.enabled` → `AudioConfig.output`.
- `Assets.texture(image, filter)` → `Assets.createTexture(image, filter)`.
- `Assets.music` → `Assets.musicTrack`, `Assets.musicOrNull` → `Assets.musicTrackOrNull`.
- `CollisionLayers.contains` → `CollisionLayers.intersects`.
- `Collisions.overlaps` → `Collisions.overlapping`.
- `Collisions.overlapsTo` → `Collisions.overlappingTo`.
- `Collisions.firstOverlapOrNull` → `Collisions.firstOverlappingOrNull`.
- `FrameStats.drawCalls` → `FrameStats.drawCallCount`.
- `FrameStats.collisionPairs` → `FrameStats.collisionPairCount`.
- `FrameStats.fixedSteps` → `FrameStats.fixedStepCount`.
- `AudioStats.activeVoices` → `AudioStats.activeVoiceCount`.
- `AudioStats.stolenVoices` → `AudioStats.stolenVoiceCount`.
- `AudioStats.droppedCommands` → `AudioStats.droppedCommandCount`.
- `AudioStats.closedStreams` → `AudioStats.closedStreamCount`.
- `Time.frame` → `Time.frameCount`.
- `Canvas.count` → `Canvas.quadCount`.
- `ParticleEmitter.count` → `ParticleEmitter.particleCount`.
- `ParticleEmitter(region, maxParticles)` → `ParticleEmitter(region, capacity)`.
- `Input.axis(negative, positive)` → `Keyboard.axis(negative, positive)`, called as `input.keyboard.axis(...)`.
- `MouseWheel` → `MouseWheelScrolled`, in `klyph.event` and as `PlatformEvent.MouseWheelScrolled`.
- `Commands.isEmpty` and `SceneStack.isEmpty` are functions: `isEmpty()`.
- `EntityBuilder.parent(parent)` → `EntityBuilder.setParent(parent)`.
- `Commands.spawn(build)` → `Commands.spawn(builder)`.
- `SpriteAnimation.animation` → `SpriteAnimation.playhead`.
- `Animation.elapsed` → `Animation.frameElapsed`.
- `GamepadId.id` → `GamepadId.value`.
- `GpuDevice.release(window)` → `GpuDevice.unclaim(window)`.
- `PcmStream` extends `AutoCloseable`.
- `Music.play` throws `IllegalArgumentException` instead of `IllegalStateException` when the
  track's stream does not match the track's channels or sample rate.
- `toString` of `Camera`, `Sprite`, `Transform`, `WorldTransform` and `Collider` writes sizes as
  `800.0x600.0`, without spaces.
- The sample games read their settings through `AppConfig.fromEnvironment`, so
  `KLYPH_SCREENSHOT` and the other `KLYPH_*` variables work for them as for the sandbox.
- `Files.writeBytes`, and with it `Image.save`, `Window.capture` and `AppConfig.screenshot`,
  creates the missing folders on the way to the file instead of failing, so a first screenshot
  into a new folder is written.
- The benchmarks apply every setting `AppConfig.fromEnvironment` reads, `KLYPH_SCREENSHOT`,
  `KLYPH_OVERLAY` and `KLYPH_FULLSCREEN` included.

### Removed

- The `Animation` component key (`Animation.Companion`): no engine step advanced a bare
  `Animation` component. `SpriteAnimation` is the animation component.
- `SpriteRenderer` and the public `Texture(device, handle, ...)` constructor are internal; `App`
  never exposed what they need.
- `Entity.raw` is internal; `Entity.index` and `Entity.generation` remain.
- `TextureRegion.u0`, `v0`, `u1` and `v1` are internal; regions are in texels.

## [0.1.0] - 2026-11-03

First release, on Maven Central as `com.eventslooped.klyph:klyph` and
`com.eventslooped.klyph:klyph-platform`, with the Gradle plugin `com.eventslooped.klyph`.

### Added

- Framework core: `runApp` owns the loop and runs systems in startup, input, fixed update,
  update, late update, render and shutdown phases, with a fixed timestep, a typed event bus fed
  from the platform, and an entity hierarchy whose world transforms propagate each frame.
- Entity world with sparse-set component storage and typed queries over two or three
  components, a command queue and shared resources keyed by class.
- Sprite renderer that pulls per-sprite records from a storage buffer in one draw call; image
  loading, sprite sheets, animation, several textures, a camera and interpolated transforms.
- HLSL shaders compiled ahead of time through SDL_shadercross to Metal, Vulkan and Direct3D 12.
- Keyboard, mouse and gamepad state.
- Audio: WAV and Ogg Vorbis sound effects with volume, pitch and pan, and streamed Ogg Vorbis
  music with fades, mixed by klyph on SDL's audio thread. A game runs silently without an audio
  device.
- Scenes: a stack of scenes with their own systems and enter and exit actions; entities a scene
  spawns are destroyed when it leaves the stack.
- Collisions: box and circle colliders with layers and masks on a uniform grid, `Overlap` events
  and shape queries.
- Particles: emitters with bursts, a continuous rate, ranges for speed, lifetime and rotation,
  gravity, and color and scale blended over each particle's life.
- Text: BMFont fonts with kerning and a built-in 8x8 font, drawn through a `Text` component or
  `app.canvas` in world or screen space; linear or nearest sampling per texture.
- Debug overlay on F3 with frame rate, frame and phase times, entity, sprite and draw call
  counts, audio voices and a frame-time graph.
- Gradle plugin `com.eventslooped.klyph`: host or cross targets, SDL3 lookup and download, asset
  copying, `run`, `runRelease` and `distribute`.
- macOS, Linux and Windows builds, tested in CI.
- Sample games: a top-down shooter and a bug-squishing game for young children.
- Benchmarks: `sprites`, `hierarchy`, `collisions`, `particles` and `mixer`.

[Unreleased]: https://github.com/inemtsev/klyph/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/inemtsev/klyph/compare/v0.1.0...v1.0.0
[0.1.0]: https://github.com/inemtsev/klyph/releases/tag/v0.1.0
