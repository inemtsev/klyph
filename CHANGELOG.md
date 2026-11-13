# Changelog

All notable changes to klyph are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `Sprite.tileWidth` and `tileHeight`, and `Canvas.drawTiled`: repeat a texture across a larger area instead of stretching it.
- `SamplerAddress` (`CLAMP`, `REPEAT`), `Texture.address` and an `address` argument on `Assets.texture`, `textureOrNull` and `createTexture`: load a texture that repeats, which tiling needs.
- `Camera.offsetX` and `offsetY`: shift the view after `follow` and the bounds clamp, such as for a screen shake.
- `drawnPositionOf` on `World` and `App`: where an `Interpolated` entity is drawn this frame, for a camera to follow.
- `World.snapInterpolation(entity)`: draws a teleported or respawned `Interpolated` entity at its new place, with no sweep from the old one.
- `Scene.elapsed` and `elapsedSeconds`: how long a scene has been on top, standing still while another scene covers it.
- `Sprite.originX` and `originY`: anchor a sprite to its `Transform` at any point, such as a character's feet, instead of the center.
- `ParallaxLayer`: a sprite that follows the camera at a fraction of the world's speed and repeats to fill the view.
- `DrawSpace.BACKGROUND`: canvas quads in points drawn under the whole world, such as a one-call sky.

## [1.1.0] - 2026-11-10

Movement, jumps and floors: the engine moves your entities and stops them at walls and floors,
so a platformer takes as little code as a shooter.

### Added

- `Solid`: makes a box `Collider` a wall or floor, or a one-way ledge with `Passable.FROM_BELOW`.
- `KinematicBody(dx, dy)`: moved by the engine each fixed step and stopped at solids, reporting
  `isOnFloor`, `isOnWall`, `isOnCeiling`, `floorOrNull` and `hasLanded`, with `dropThrough()` for
  ledges. Gravity and jumps stay game code; see the
  [Platformers guide](https://inemtsev.github.io/klyph/guide/platformers.html).
- Sweeps against solids: `Collisions.sweepBox`, `sweepBoxTo`, `sweepCollider` and `floorBelowOrNull`.
- Planning a move without a body: `Collisions.slideBox` and `slideCollider`, into a reusable `BoxSlide`.
- `Velocity(dx, dy)`: applied to the entity's `Transform` each fixed step.
- `ParticleEmitter.burst(region, count, ...)` and `Commands.spawnBurst(x, y, emitter)` for one-shot effects.
- `Animation.speed` and `SpriteAnimation.speed`: a playback rate multiplier.
- `Collider.scalesWithTransform`: set to `false` to keep the hitbox at scale 1 while the sprite squashes or spins.
- `Time.fixedStepCount`.
- Gradle plugin: `com.eventslooped.klyph.library` for a game's shared module, read-only `klyph { }`
  members `hostTarget`, `supportedTargets`, `configuredTargets` and `sdl3IncludeDir`, and
  `klyph.sdl3.download=false` to stop the build instead of downloading SDL3.

### Changed

- `isJustPressed` and `isJustReleased` count fixed steps inside `FIXED_UPDATE`: each press is seen
  by exactly one step. A game that latched presses from `INPUT` for the next step can drop the latch.
- Rooftop Roller runs on `KinematicBody` and `Solid`; its own physics code is gone.
- `Collisions.toString` includes `solids=N`.

## [1.0.0] - 2026-11-04

The first stable release. Code written for 1.0 keeps working in every 1.x release, except the
platform plumbing marked `@KlyphPlatformApi`.

### Added

- `@KlyphPlatformApi`: an opt-in marker on the SDL and GPU plumbing in `klyph.platform` that only
  the engine calls. It is outside the compatibility promise, and games use none of it.
- `AudioOutput` with `DEVICE` and `NONE`, for `AudioConfig.output`.
- `Assets.spriteSheetOrNull` and `Files.readTextOrNull`.
- `Color.BLACK`, `RED`, `GREEN`, `BLUE`, `YELLOW`, `CYAN`, `MAGENTA` and `GRAY`.
- `Keyboard.axis(negative: KeyCode, positive: KeyCode)`, beside the `Scancode` overload.
- The [API reference](https://inemtsev.github.io/klyph/) with inline samples, published on every
  release, and Maven Central publishing from `v*` tags.

### Changed

- `Camera` works in points: zoom 1 is one world unit per point on every display, and
  `viewportWidth`, `viewportHeight`, `worldToScreen` and `screenToWorld` are in points. A game that
  set `zoom = 2 * window.pixelDensity` sets `zoom = 2`.
- `Canvas.drawSprite` and `drawRect` default to `DrawSpace.SCREEN`; pass `space = DrawSpace.WORLD`
  to draw through the camera.
- `ParticleEmitter.lifetime` is a `ClosedRange<Duration>`, such as `0.5.seconds..1.seconds`.
- `World(initialCapacity)` rejects a capacity below 1 with `IllegalArgumentException`.
- `Music.play` throws `IllegalArgumentException` instead of `IllegalStateException` for a stream
  that does not match the track.
- `Files.writeBytes`, `Image.save`, `Window.capture` and `AppConfig.screenshot` create missing
  folders on the way to the file.
- The published javadoc jars hold the Dokka reference instead of being empty.
- The sample games, benchmarks and sandbox read every `KLYPH_*` setting through
  `AppConfig.fromEnvironment`.
- The sample games moved to `games/shooter` and `games/squish` (`:games:shooter`, `:games:squish`).
  The shooter has waves, divers and a boss every fifth wave; Bug Squish hides the cursor and
  toggles fullscreen with F11. Both use the gameplay kit in place of their own copies.
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
- `EntityBuilder.parent(parent)` → `EntityBuilder.setParent(parent)`.
- `Commands.spawn(build)` → `Commands.spawn(builder)`.
- `SpriteAnimation.animation` → `SpriteAnimation.playhead`.
- `Animation.elapsed` → `Animation.frameElapsed`.
- `GamepadId.id` → `GamepadId.value`.
- `GpuDevice.release(window)` → `GpuDevice.unclaim(window)`.
- `Commands.isEmpty` and `SceneStack.isEmpty` are functions: `isEmpty()`.
- `PcmStream` extends `AutoCloseable`.

### Removed

- The `Animation` component key; `SpriteAnimation` is the animation component.
- `SpriteRenderer`, the public `Texture` constructor, `Entity.raw` and `TextureRegion.u0`, `v0`,
  `u1` and `v1` are internal.

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

[Unreleased]: https://github.com/inemtsev/klyph/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/inemtsev/klyph/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/inemtsev/klyph/compare/v0.1.0...v1.0.0
[0.1.0]: https://github.com/inemtsev/klyph/releases/tag/v0.1.0
