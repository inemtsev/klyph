# klyph

A Kotlin library written in the style of the Kotlin standard library.

Every public declaration must follow the API style spec. Read it before adding
or changing public API, and run its review checklist before finishing a change.

@API_STYLE.md

## Build and run

Kotlin/Native only, no JVM target. By default the build configures the host's own target
(macosArm64, linuxX64, linuxArm64, mingwX64); `-Pklyph.targets` picks others (see Publishing
below). SDL3 is located automatically (Homebrew, /usr/local, /usr); override with
`-Psdl3.prefix=<path>`. The library is taken from the prefix's `lib/` or, on Linux, the first of
`lib/`, `lib64/` (Fedora), `lib/x86_64-linux-gnu/` and `lib/aarch64-linux-gnu/` (Debian, Ubuntu)
that holds it (`Sdl3Locator.libraryDir` in the klyph plugin, which every module applies). The
root `gradle.properties` sets `klyph.sdl3.download=false`, so the engine build never downloads
SDL3: without an install it stops and names the folders it searched.

Modules: `klyph-platform` (SDL3 cinterop and the thin Kotlin API over it; the only module allowed
to import `kotlinx.cinterop`, `sdl3`, `stb`, `vorbis`, `cnames` or `platform.*`; its engine-only
plumbing, the GPU wrappers, `EventPump`, `AudioDevice`, `AudioRenderer`, `Sdl.init`/`quit`, the
`Window` factory, `CompiledShader`, `FrameCapture` and `printError`, is marked
`@KlyphPlatformApi`, an opt-in that is an error without it, outside the 1.0 promise; `klyph`,
`sandbox` and `benchmarks` opt in in their build files, the games never do), `klyph` (the engine, no interop;
its `klyph.app` package owns the frame loop, which games and executables enter through `runApp`;
systems get `app.assets`, which loads from `assets/` next to the executable unless
`AppConfig.assetRoot` overrides it, `app.camera`, which sprites are drawn through, in points at zoom 1 on every display, `app.input`, the
keyboard, mouse and gamepad state updated at the start of INPUT, `app.commands`, the world command
queue applied after every system, `app.resources`, shared state keyed by class, `app.audio`,
sound effects and music, with sounds and tracks loaded through `app.assets.sound` and
`app.assets.musicTrack`, `app.scenes`, a scene stack whose top scene (`current`, or `currentOrNull` when it may be empty) runs its systems and whose scenes own
the entities they spawn and each keep a clock, `Scene.elapsed` (`elapsedSeconds` as a `Float`), that
counts only the time the scene is on top (`Time.elapsed` runs whatever scene is on top, and `Time.elapsedSeconds` and
`fixedDeltaSeconds` are the `Float` seconds of `elapsed` and `fixedDelta`, as `deltaSeconds` is of `delta`), `app.collisions`,
overlap detection after FIXED_UPDATE that publishes `Overlap` events (`entity in overlap` asks whether an entity is in the
pair, `otherThan` returns the other one and `otherOrNull` returns `null` for an entity in neither slot)
and answers shape queries (overlap only for ordinary colliders), plus solid
response: a `Solid` next to a box `Collider` is a wall or floor (`Passable.FROM_BELOW` a one-way
ledge) kept in a second grid inside `Collisions` (`SolidGrid`, rescanned at every body move and
detection, or before a sweep when the `Solid` or `Collider` store's version moved; each rescan finds
every solid's box of the last one by entity, in the same world only, and adds the move of its
top-left corner to the solid's shift unless its size changed, which the body move uses and then
clears; still solids sit in cells rebuilt only when one is added, removed or changed, or starts or
stops moving, and moving solids, those with a `Velocity` or whose box changed in the last 120
rescans, in a second `SolidCells` rebuilt at a rescan where one moved, its shift was cleared or the
set changed, over the box each covered while moving), a solid moves like any root entity, by a
`Velocity` or a system setting its `Transform`, and is never shortened, a `KinematicBody(dx, dy)` on a root entity with a
box collider is moved by the engine in every fixed step and stopped `Collisions.CONTACT_GAP` short
of solids, reporting `isOnFloor` (contact from the last move and `dy >= 0`), `isOnWall`/`wallNormalX`,
`isOnCeiling`, `floorOrNull`, `hasLanded` and `landingSpeed`, and `wasPushedOut` and `isStuck` for a
body that starts a move overlapping a solid (pushed out the shortest way in at most four pushes,
else left in place), `wasCarried`, `floorVelocityX`/`floorVelocityY` (the floor's shift over the
step, which the engine never adds to `dx`/`dy`; a jump adds it) and `isCrushed` (a moving solid's
push that another solid stopped, the body left where it stopped and nothing destroyed; a one-way
carrier lets its rider drop through instead, and a body that jumped is neither carried nor pushed
by the floor it left), with a
`FLOOR_SNAP_DISTANCE` floor snap and
`dropThrough()`, `sweepBox`/`sweepBoxTo`/`sweepCollider` sweep a box against the solids as they
are now (a thick-ray Amanatides and Woo walk shared with the body move), `floorBelowOrNull` probes
for a floor under a line, and `slideBox`/`slideCollider` make the body's move into a reusable
`BoxSlide` through the same internal `SolidGrid.slide` the body uses (gravity, coyote time and
jump buffers stay game code), `app.canvas`, immediate quads and text in `BACKGROUND` (points, drawn under the
whole world, for a sky), `WORLD` or `SCREEN` space queued during RENDER, and `app.overlay`, the debug overlay toggled with F3; the
`Text` and `ParticleEmitter` components draw with the sprites, fonts load through
`app.assets.font` or come built in as `app.assets.defaultFont`, a texture's `SamplerFilter`,
chosen when it loads (`AppConfig.textureFilter`, LINEAR unless set and read back as `Assets.textureFilter`, is what every
texture, sprite sheet and font gets when its call passes none), picks linear or nearest sampling, its `SamplerAddress`, chosen the same way, picks
clamped (the default) or repeating addressing, `Assets` caches a texture by path, filter and address, `app.assets.region(path)` returns the whole of a file as a
`TextureRegion` packed into a shared 2048 by 2048 atlas page, one list of pages per filter (the internal `AtlasPages`
places each file with a `SkylinePacker`, rings it with its own edge texels and writes it through `Texture.write`, the
`@KlyphPlatformApi` `GpuDevice.uploadToTexture(texture, image, x, y)`; a REPEAT request, a file over 2046 texels on a side
and `AppConfig.atlas = TextureAtlas.NONE` (`KLYPH_ATLAS=0`) give the file its own texture, and `region` caches by path and
filter apart from `texture`, which is for tiling and hand-cut regions),
and a `Sprite` with `tileWidth` or `tileHeight` (world units before the entity's scale, 0 stretches
that axis; `Sprite.tiled(region, width, height, scale)` sets both to the region's size times `scale`) or `Canvas.drawTiled` repeats a texture loaded with `address = SamplerAddress.REPEAT`
across a larger area, the region having to cover the whole texture (a `check` naming the entity when
the sprite is drawn, a `require` in `drawTiled`), with the sprite renderer holding one sampler for
each filter and address pair, a `Sprite`'s `originX` and `originY` (fractions of the quad's size from its
top-left corner, 0.5 by default, as `Canvas.drawSprite`'s) set where its `Transform` lies on the quad and
are what rotation, scale and flips pivot about, carried in the last two floats of the record's size row
(particle and plain `QuadBuffer.add` quads write 0.5, a `Text` glyph is written at the text's
position with that point as its origin, in the glyph's own fractions and so mostly outside 0 to 1, so
the vertex shader turns it about the text's position and nothing is rotated on the CPU, a flipped axis records `1 - origin` so the
picture mirrors about the origin, and a non-finite origin is a `check` failure naming the entity when the
sprite is drawn), and `app.window` has `cursor` (`CursorMode`,
hidden only while over the window), `setCursor(image, hotspotX, hotspotY)`, `mode` (`WindowMode`,
fullscreen is borderless desktop fullscreen; `AppConfig.windowMode` and `AppConfig.cursor` set
both at startup) and `capture(path)`, which reads the next drawn frame back and writes it as a PNG
through the vendored `stb_image_write.h`, offscreen too; the gameplay kit: `Velocity(dx, dy)`
moves its entity's `Transform` in units per second, applied by the engine in every fixed step
after the FIXED_UPDATE systems; `Lifetime` destroys its
entity when its time runs out and `Fade` blends the alpha of its `Sprite` and `Text` to a target
(`FadeEnd.DESTROY` destroys after), both counted down by the engine after UPDATE; a ONCE
`SpriteAnimation` publishes `AnimationFinished` and with `AnimationEnd.DESTROY` destroys its entity,
and its `speed` (the playhead's `Animation.speed`) scales its playback rate;
a `ParticleEmitter` with `whenEmpty = EmitterEnd.DESTROY` destroys its entity once it has nothing
left, `ParticleEmitter.burst(region, count, …)` makes such a one-shot emitter and
`Commands.spawnBurst(x, y, emitter)` spawns the entity carrying it, and
`space = EmitterSpace.LOCAL` turns its direction with the entity; `Camera.follow` eases
toward a point, `Camera.bounds` keeps the view inside a `Rect`, and `Camera.offsetX`/`offsetY` shift
the view on top of both for a shake (`worldToScreen`, `screenToWorld` and the projection add them;
`follow`, `lookAt` and `clampToBounds` never read or change them, so the view can show past the
bounds by the offset; both keep their value until the game changes them or a scene transition
applies, which zeroes them, so a scene pushed over a shake is drawn centered); a `ParallaxLayer(factorX, factorY = factorX, repeatX = 0f, repeatY = 0f, anchorX = 0f,
anchorY = 0f)` on a root entity with a `Transform` and a `Sprite` is drawn at its transform plus `1 - factor` times
the view less the anchor (the view at which it sits at its transform; the view is the camera after
its bounds clamp plus `offsetX`/`offsetY`, so a factor-0 sky does not shake), and with a repeat once for each
period the bounding box of the view touches, at most `ParallaxLayer.MAX_COPIES`; the internal `ParallaxPlan`
plans the copies at the start of `SpriteRenderer.extract`, which also sizes the record buffers, layer sprites
are written before the ordinary sprites of their group, and a `check` naming the entity rejects a layer on a
child, with a negative or non-finite factor or repeat or with a non-finite anchor; tile maps live in
`klyph.tiles`: a `TileLayer` component (a mutable grid of `Tile`s, a value class over Tiled's 32-bit id with its
three flip bits, drawing from a `TileSet` of a `SpriteSheet`, a tile size and `solidIds`/`oneWayIds`) on a root,
unrotated entity with a positive scale is drawn by the internal `TilePlan`, which after the parallax plan maps the
same view into each layer's cells, sizes the record buffers for them and writes one sprite record per non-empty
cell in view (a diagonal flip is a quarter turn clockwise with the quad's x flip the tile's y flip and its y flip
the opposite of its x flip) before the ordinary sprites; `TileGrid` is the read-only interface both `TileLayer` and
the immutable `TileMapLayer` implement, with `tileAt`, `solidRectangles` (row runs merged down equal columns),
`toTileLayer`, and `Commands.spawnTileLayer`/`spawnSolids` (one `Solid` box per rectangle, `Passable.FROM_BELOW`
for one-way tiles); `app.assets.tileMap(path)` reads orthogonal finite Tiled JSON maps (`.tmj`, CSV or uncompressed
base64 layers, one tileset embedded or as `.tsj`, bool tile properties `solid` and `oneWay`) through the internal
`readTiledMap` and the internal recursive-descent JSON reader `klyph.json.parseJson` (errors name line and column;
`JsonObject` gives typed reads whose messages name the field and file), rejecting everything else with a
`TileMapException` naming the file, layer and the fix in Tiled, and `Assets.readText`/`readBytes` are the seam for
other loaders; `app.drawnPositionOf(entity)` (`World.drawnPositionOf(entity, alpha)`) returns
where the renderer draws an entity this frame, through the internal `drawnTransform` that the sprite
pass and the text pass share (a child's world transform is composed on the call from its chain's
current transforms by the internal `composeWorldTransform`, so a parent moved since the last
propagation counts), so a camera that follows an `Interpolated` entity moves with its picture, and
`World.snapInterpolation(entity)`, called after moving the `Transform`, copies it over
the saved previous transform so a respawn is drawn at the new place at once; `TextShadow` draws under text
through `Text.shadow` or `drawText(shadow = …)`; `app.assets.whitePixel` and `app.assets.disc(d)`
are ready-made regions, and a region's `aspectRatio`, its width over its height, sizes a `Sprite` by one side
without distorting it; `AppConfig.fromEnvironment()` reads the `KLYPH_*` limits and switches,
including `KLYPH_SCREENSHOT`, `KLYPH_FULLSCREEN`, `KLYPH_ATLAS` and `KLYPH_FRAME_DELTA` (`AppConfig.frameDelta`,
which replaces the measured frame delta so that runs repeat);
every angle is in radians, with `90f.degrees` and `toDegrees()` in `klyph.math` beside `Vec2` and
`Rect`),
`klyph-physics` (the physics engine, depending on `klyph`, package `klyph.physics`: one
`PhysicsWorld` stepped by a `Duration` in substeps, XPBD, its stores (`particles`, `rigidBodies`,
`softBodies`, `constraints`, `joints`, `attachments`) handing out value-class handles (`Particle`,
`RigidBody`, `SoftBody`, `Link`, `Pin`, `Area`, `Bend`, `Joint`, `Attachment`) over plain arrays, so a
step allocates nothing (`AllocationTest` checks that with the GC's statistics); the old `Int`-slot
functions stay `internal` for the solver and its tests; `SolidGrid` is the level, `Events` what the
last step did, `addPhysics` and `PhysicsBody` the bridge to `App` and the entity world, and
`DebugDraw` draws it; collision filters are `klyph.collision.CollisionLayers`, with the level on
`PhysicsLayers.SOLIDS`, layer 30; `docs/physics.md` is its guide page and `docs/physics/` the
explainers, read on GitHub),
`sandbox` (executable; a smoke test that opens a window and clears it each frame), `games:shooter`
(executable in `games/shooter`; the reference game sample, keyboard with mouse aim or gamepad, its art and sounds in
`games/shooter/assets/` synced next to the binary after every link; enemies come in waves kept in a
`Wave` resource and shown in the HUD, drifters and divers that swoop at the ship, and every fifth
wave is a boss with 12 hit points, a health bar drawn in world space and bullets on the
`ENEMY_BULLET` layer; `KLYPH_AUTOPILOT=1` plays itself, with 4 enemies a wave so a 90 second run
reaches the boss, and `KLYPH_FRAMES` or `KLYPH_SECONDS` bounds a run), `games:squish` (executable in `games/squish`; a bug-squishing game for young children, same layout and variables as the shooter; the system cursor is hidden because the swatter is the pointer, and F11 toggles fullscreen), `games:roller` (executable in `games/roller`; Rooftop Roller, a side-scrolling platformer of one level, a ninja on rollerblades with momentum, variable jumps, coyote time and a jump buffer, rings, hearts, stompable blobs and helicopters, same layout and variables as squish; the level is Kotlin data (`Level.kt`) whose roofs and ledges become invisible `Solid`s, and whose `Shuttle`, a girder over the widest gap, is a play-scene `Solid` with a `Velocity` and its own tiled `Sprite`, marked `Interpolated` and turned at the ends of its track by `moveShuttles`, which the hero rides and jumps off with `floorVelocityX` added, the hero is a `KinematicBody` whose acceleration, gravity, jumps, coyote time and jump buffer are the game's own (`Systems.kt`), the blobs and helicopters walk and fly their patrols by their `Transform`s, the brick, cap, window and girder tiles and the one-off art are painted into `Image`s at startup with `Image.fillRect`, `fillCircle` and `set`; the one-off art (air conditioner, skyline, clouds, pylon, star) is then drawn into one sheet image with `Image.draw` (alpha blending, clipped like the shapes) and uploaded as one texture, a `SheetLayout` setting the pieces in a row with their edge texels repeated one texel outwards, each building being `Sprite`s that repeat the tiles (`SamplerAddress.REPEAT` textures, one each because a tiled sprite repeats a whole texture, `Sprite.tiled`) with props standing on `originY = 1f`, the sky is one `DrawSpace.BACKGROUND` quad, the clouds and skyline are `ParallaxLayer` sprites anchored at the camera's start, the camera follows `app.drawnPositionOf(hero)` and shakes through `Camera.offsetX`, a respawn calls `snapInterpolation`, and the title's hero, the pickups and the end screens read their scene's `elapsed`; the autopilot plans again every fixed step, probing for the roof's end with `floorBelowOrNull` and for walls ahead with `sweepCollider` and following jump arcs with `slideCollider`, and fails at exit if it took no ring and stomped no enemy), `games:physics` (executable in `games/physics`, package `klyph.physics.demo`; the physics playground of eighteen scenes, from a blob garden to a stress test of 1 000 crates, 5 000 grains and 50 blobs, drawn with `DebugDraw`; keys switch scenes and debug views, the mouse grabs and spawns; its scene tests, `HeadlessTest`, `MouseTest` and `SceneTest`, take about ten minutes on the debug build, so `check` skips them unless `-Pklyph.physics.sceneTests=1` is set), `benchmarks`
(executable; named benchmarks, run from the release build), `samples` (the `@sample` functions from
API_STYLE.md section 8, run as tests; `@Sample` is a typealias of `kotlin.test.Test` and
`assertPrints` lives in `samples/src/nativeTest/kotlin/samples/Harness.kt`), and
`klyph-gradle-plugin` (the `com.eventslooped.klyph` Gradle plugin games apply, with its own
reference in `klyph-gradle-plugin/README.md`; an included build through `pluginManagement` in
`settings.gradle.kts`, so `sandbox`, `games:shooter`, `games:squish`, `games:roller`, `games:physics` and `benchmarks` apply it by id and take
their targets, SDL3 link flags, asset sync, `run`, `runRelease` and `distribute` from it; its
library mode, `com.eventslooped.klyph.library`, does the same without executables or their tasks
for the engine modules; it is a separate Gradle build, so its unit and TestKit tests run with
`./gradlew -p klyph-gradle-plugin check`, not with the root `check`), and `tools/detekt-rules`
(klyph's own detekt rules, the `api-style` rule set: `FunctionKDocStartsWithVerb`,
`BooleanBehaviorParameter` and `StatefulClassWithoutToString`, the API_STYLE rules stock detekt
cannot express, written against the PSI alone; a separate JVM build the root settings include with
`includeBuild`, so the `detektPlugins` dependency `klyph.lint` declares resolves to its jar; its
tests run with `./gradlew -p tools/detekt-rules check`, not with the root `check`). `./gradlew check`
runs every module's tests and samples plus the interop-import rule; a single module's tests run
with `:<module>:macosArm64Test`. GPU, window, event and audio device functions, `runApp`, `App` and
`Assets` have no samples because they need a display or a device; `Audio` has samples because it
also mixes without a device. `buildSrc` serves the engine modules (`klyph-platform`, `klyph`, `klyph-physics`,
`samples`) and depends on the plugin's classes through `buildSrc/settings.gradle.kts`, which
includes `klyph-gradle-plugin` too (Gradle builds it once for both): `klyph.native` applies
the plugin's library mode, which configures their targets, finds SDL3 (`klyph.sdl3IncludeDir`
feeds the `sdl3` cinterop) and adds the SDL3 link flags their test binaries need, and keeps only
the engine's extras (host-only tests, `check` compiling the other targets and the shared
metadata), `klyph.publish` their Maven publications, `CompileShadersTask` the shaders, and
`klyph.lint` registers the interop-import rule (`checkNoInteropImports`, part of `check`), which
`klyph.native` applies and the executables apply next to the plugin; it also applies detekt
(`dev.detekt`, whose version is in `gradle/libs.versions.toml` with every other third-party
version), whose `detekt` task, part of `check`, reads `config/detekt/detekt.yml` and the module's
`detekt-baseline.xml` (`./gradlew detektBaseline` rewrites them) and runs without type resolution
on Kotlin/Native, with the `api-style` rules of `tools/detekt-rules` on for `klyph` and
`klyph-platform` only (excluded by path in `detekt.yml`, whose `config.excludes` keeps their
section out of validation because the plugin build, which reads the same file, does not load them). `klyph.docs`, applied through
`klyph.publish`, configures Dokka for the three published modules: `@sample` bodies from
`samples/src/nativeTest/kotlin`, module documentation from `docs/klyph.md`,
`docs/klyph-platform.md` and `docs/klyph-physics.md`, and source links to GitHub. `./gradlew dokkaGenerate` writes the
aggregated site to `build/dokka/html`, with `docs/index.md` as its landing page; it is not part of
`check`. The guide pages under `docs/` are rendered by the root `renderGuide` task into
`build/guide/` and copied by `installGuide` into `build/dokka/html/guide/`; their Kotlin snippets
are checked against `samples/src/nativeTest/kotlin/samples/guide/` by `:samples:checkGuideSnippets`,
part of `check`. The published javadoc jars hold
the same Dokka HTML. `.github/workflows/docs.yml` deploys the site to GitHub Pages and
`.github/workflows/release.yml` publishes to Maven Central, both on `v*` tags.

`tools/consumer-test.sh local|central [version]` builds the shooter as a game outside the
repository would: a fresh project under `$TMPDIR` holding `games/shooter/src`, `games/shooter/assets`, the
wrapper and the three-line build file from the plugin README, resolved from `mavenLocal()` and
Maven Central (`local`) or Maven Central alone (`central`). It runs the autopilot for five
seconds, runs `distribute` and checks that the zip holds the executable, `assets/ship.png` and
the SDL3 library. `local` needs `./gradlew publishToMavenLocal -Pklyph.targets=all` and
`./gradlew -p klyph-gradle-plugin publishToMavenLocal` first. `SDL3_PREFIX` in the environment
becomes `sdl3.prefix`, and `KLYPH_CONSUMER_RUN=0` links instead of running, which is how every
CI job (no display, no GPU) uses it; the Windows job runs it in bash like its other steps.

```
./gradlew :sandbox:run -Pklyph.frames=120
./gradlew :games:shooter:run                                    # WASD/arrows/stick, mouse aim, Space/click/south
./gradlew :games:shooter:run -Pklyph.autopilot=1 -Pklyph.seconds=10   # plays itself, fails if no kill
./gradlew :games:squish:run                                     # click or arrows/stick + Space/south; autopilot fails if no squish
./gradlew :games:roller:run                                     # A/D/arrows/stick + Space/W/south; autopilot finishes the level in ~20 s
./gradlew :games:shooter:distribute                             # zip in games/shooter/build/distributions/, runs without SDL3 installed
./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkSprites -Pklyph.sprites=50000     # in a window
./gradlew :benchmarks:benchmarkHierarchy -Pklyph.sprites=5000 -Pklyph.children=8
./gradlew :benchmarks:benchmarkCollisionsOffscreen -Pklyph.sprites=10000 -Pklyph.cell=64
./gradlew :benchmarks:benchmarkParticlesOffscreen -Pklyph.sprites=100000   # 100 emitters of 1000
./gradlew :benchmarks:benchmarkDrawnOffscreen -Pklyph.sprites=10000   # drawnPositionOf for each of 10000 interpolated sprites
./gradlew :benchmarks:benchmarkTilesOffscreen                   # eight full-screen layers of 16-point tiles, camera moving
./gradlew :benchmarks:benchmarkOverlapsOffscreen -Pklyph.sprites=10000   # Overlap.otherOrNull against contains plus otherThan
./gradlew :benchmarks:benchmarkParentsOffscreen -Pklyph.sprites=10000   # World.parentOrNull for every child of the hierarchy tree
./gradlew :benchmarks:benchmarkMixer                            # headless, no window or device
./gradlew :benchmarks:benchmarkMixer -Pklyph.music=games/shooter/assets/music.ogg
./gradlew :benchmarks:benchmarkPhysics                          # the physics stress world, headless
./gradlew :benchmarks:benchmarkList
```

`:sandbox:run` runs the debug smoke test and `:games:shooter:run` the debug game; the `benchmark*`
tasks run the release build. Every `KLYPH_*` variable below can be given as a Gradle property
instead (`-Pklyph.sprites=…`, `-Pklyph.frames=…`, `-Pklyph.offscreen=1`, `-Pklyph.vsync=1`,
`-Pklyph.validation=1`, `-Pklyph.autopilot=1`, `-Pklyph.music=…`, and `-Pklyph.frameDelta=0.016` for
`KLYPH_FRAME_DELTA`, a name of several words being camelCase), which is how the IntelliJ run configurations in
`.run/` pass them. `sprites`, `hierarchy`, `collisions`, `particles`, `drawn`, `overlaps`, `parents` and `tiles` each have a
`benchmark<Name>` task in a window and a `benchmark<Name>Offscreen` task, which sets
`KLYPH_OFFSCREEN=1` and 2000 frames; a run's summary skips a warm-up of one second or a quarter
of the frame limit, whichever is shorter;
`-Pklyph.benchmark=<name>` picks the benchmark for the generic `benchmark` task. The binaries
are also runnable directly: `benchmarks/build/bin/macosArm64/releaseExecutable/benchmarks.kexe
sprites`.

`KLYPH_SPRITES=<n>` sprite count (default 10000), `KLYPH_CHILDREN=<n>` children per parent in the
`hierarchy` benchmark (default 8), `KLYPH_FRAMES=<n>` exits after `n` frames, `KLYPH_SECONDS=<s>`
exits once `Time.elapsed` reaches `s` seconds, `KLYPH_VSYNC=1` caps at the display rate,
`KLYPH_VALIDATION=1` turns on the GPU validation layer (slow; use it after touching the platform
module), `KLYPH_OFFSCREEN=1` draws into a texture and waits for the GPU each frame so the display
is out of the loop. These five, `KLYPH_OVERLAY`, `KLYPH_SCREENSHOT`, `KLYPH_FULLSCREEN`, `KLYPH_ATLAS` and `KLYPH_FRAME_DELTA` are
read by `AppConfig.fromEnvironment`, which
the sandbox, benchmarks and both games call, and the engine quits by itself at `AppConfig.frameLimit` or
`timeLimit`. `KLYPH_AUTOPILOT=1` makes the
shooter play itself and fail at exit if it destroyed no enemy. `KLYPH_MUSIC=<path to an .ogg>` makes
the `mixer` benchmark stream that file instead of its synthetic music, to measure Vorbis decoding;
a relative path resolves against the executable's directory, or the repository root through
Gradle. `KLYPH_CELL=<units>` sets the collision grid's cell size in the `collisions` benchmark
(default 64), `KLYPH_MOVING=<share>` (0 to 1) adds a platformer level to it, a 64 by 16 `Solid` per
ten circles with a falling `KinematicBody` on each, and gives that share of the solids a `Velocity`, and `KLYPH_OVERLAY=1` starts the sandbox with the debug overlay shown.
`KLYPH_SCREENSHOT=<frame>:<path>` (`AppConfig.screenshot`) saves frame `<frame>`, counted from 1
like `KLYPH_FRAMES`, as a PNG at `<path>`; through Gradle a relative path resolves against the
repository root; a malformed value is ignored. `KLYPH_FULLSCREEN=1` (`AppConfig.windowMode`)
starts in fullscreen. `KLYPH_ATLAS=0` (`AppConfig.atlas`) gives every file `assets.region` loads its own
texture, to compare a frame with the atlas off. `KLYPH_FRAME_DELTA=<seconds>` (`AppConfig.frameDelta`, above 0, at most 0.25 so no
`Time` clamp changes it, and at most `maxFixedStepsPerFrame` steps of `1.seconds / fixedRate` so the
`FixedStepper` drops no step, which is 4 steps of 16.666666 ms by default) replaces the measured frame
delta in `FrameLoop`, so every frame advances `Time` by exactly that and the fixed steps fall in the
same frames on every run; the clamp, the accumulator, rendering and presenting work as before, and a
game's random sources, `ParticleEmitter.random` included, stay its own business. Two sandbox runs
with `-Pklyph.frameDelta=0.016` and the same
`-Pklyph.screenshot` give PNGs that `cmp` finds equal, so use it to compare the picture of two builds.
In the sandbox F11 toggles fullscreen and C cycles the cursor. macOS has no
`timeout`, so bound
runs with `KLYPH_FRAMES`. Do not pipe `./gradlew` output into
another command: the daemon inherits the pipe and the shell never sees EOF. Redirect to a file.

On Windows the host target is `mingwX64`, built with the MinGW toolchain Kotlin/Native downloads
into `~/.konan`; no Visual Studio is needed. Use SDL's own MinGW package,
`SDL3-devel-<version>-mingw.zip` from the SDL GitHub releases, and pass its `x86_64-w64-mingw32`
folder with forward slashes, for example `-Psdl3.prefix=C:/SDL3-3.4.18/x86_64-w64-mingw32`, or
set `sdl3.prefix` in `gradle.properties`; nothing is found automatically on Windows. Binaries link
against the import library `lib/libSDL3.dll.a`, and every link copies `<prefix>/bin/SDL3.dll`
next to its `.exe`, so tests and executables run without `PATH` changes. MSYS2's
`mingw-w64-x86_64-sdl3` also builds (prefix `C:/msys64/mingw64`), but its `SDL3.dll` needs
`libiconv-2.dll`, which is not copied, so put `C:\msys64\mingw64\bin` on `PATH` to run anything.
The `windows` CI job builds this target and runs the tests; it never opens a window, so the D3D12
rendering path is only checked by running the sandbox on a real machine. Frame capture is only
verified on Metal; on D3D12 SDL pads texture download rows to 256 bytes and copies them into the
tightly packed transfer buffer when the fence signals, which the capture's fence wait covers, so
check `-Pklyph.screenshot=20:shot.png` on Windows after touching it. The same build
cross-compiles from macOS or Linux with `-Pklyph.target=mingwX64 -Psdl3.prefix=<that folder>`
(compile, and link the executables; the tests need Windows to run).

Publishing. `-Pklyph.targets` takes `host` (the default), `all` (the four targets above) or a
comma-separated list such as `macosArm64,mingwX64`; `-Pklyph.target=<name>` is a one-element
list. Every configured target gets the cinterops, all from the one SDL3 prefix's headers, and
`kotlin.mpp.enableCInteropCommonization` lets the shared `nativeMain` see them. Tests and `run`
tasks exist for the host target only; `check` compiles the other targets without linking them.
The same holds for the executables, which apply the klyph plugin: when the host's target is
configured, only its binaries link, and the other targets' link tasks are skipped (so
`./gradlew build -Pklyph.targets=all` passes) and get no `distribute<Target>`. A build whose
targets leave the host out, such as `-Pklyph.target=mingwX64 -Psdl3.prefix=<MinGW folder>`, is a
cross build and links every executable. `all` counts only as the whole value; an unknown name or
both properties at once stops the build, and targets are configured in the order above whatever
the order given (the rules and their error texts live once, in the plugin's `KlyphTargets.kt`;
`klyph.native`, `klyph.publish` and `benchmarks` read the result through `klyph.hostTarget`,
`klyph.configuredTargets` and `klyph.supportedTargets`).
Value classes in `nativeMain` carry no `@JvmInline`: the shared native metadata compilation
rejects it. `klyph`, `klyph-platform` and `klyph-physics` publish as `com.eventslooped.klyph:<module>:<version>`, the
root build file's default (`-Pklyph.version=<v>` overrides), through `com.vanniktech.maven.publish`, configured in
`buildSrc/src/main/kotlin/klyph.publish.gradle.kts`. The plugin publishes as
`com.eventslooped.klyph:klyph-gradle-plugin` with the same version and override; the root
`publishToMavenLocal` leaves it out, so publish it with `./gradlew -p klyph-gradle-plugin
publishToMavenLocal` or `./gradlew :klyph-gradle-plugin:publishToMavenLocal` (command-line `-P`
properties, `-Pklyph.version` included, reach the included build). `./gradlew publishToMavenLocal
-Pklyph.targets=all` writes them to `~/.m2`; `publishToMavenCentral -Pklyph.targets=all` uploads
to the Central Portal and needs a macOS host, the only one that builds every target (Linux and
Windows leave macosArm64 out). Any publish task fails fast without all targets. Credentials are
the Gradle properties `mavenCentralUsername`, `mavenCentralPassword`, `signingInMemoryKey` and
`signingInMemoryKeyPassword`, in `~/.gradle/gradle.properties` or as `ORG_GRADLE_PROJECT_*`
environment variables; without `signingInMemoryKey` nothing is signed, which is how CI publishes
to `mavenLocal`.

## Frame order

`runApp` runs STARTUP once and applies the scene transitions queued so far. Each frame then runs:
drop last frame's events, advance the top scene's `Scene.elapsed` by the frame's clamped delta, the
time it took or `AppConfig.frameDelta` when set (`SceneStack.advanceClock`, never by a fixed
step), refresh audio, translate platform events into `app.input` and
`app.events`, toggle the overlay, INPUT, FIXED_UPDATE zero or more times (interpolated transforms
recorded before each step, then every `Velocity` applied to its `Transform` after the step's
systems and their commands, by the fixed step length, then through `app.collisions.moveBodies` every
`KinematicBody` carried by its floor if that solid moved since the last step (both axes, before
anything else, so a descending platform keeps its rider), pushed along the move of every other
solid that moved into it (crushed and left where the push stopped when a solid is in the way; one-way
solids never push), pushed out of the solids it overlaps and moved against the solids), propagate transforms, detect collisions and publish `Overlap` events,
UPDATE, count down `Lifetime` and advance `Fade`, advance particles, advance sprite animations
(publishing `AnimationFinished`), LATE_UPDATE, propagate transforms, RENDER, queue the overlay on
`app.canvas`, clamp `app.camera` to its bounds, plan the `ParallaxLayer` copies and the `TileLayer` cells in view from it, draw tile cells and sprites,
`Text`, particles and `app.canvas` (BACKGROUND runs with the screen projection, WORLD runs with the
camera's, then SCREEN runs with the screen projection again) and present, apply scene transitions (zeroing `app.camera`'s `offsetX`/`offsetY` when one applied), quit if `AppConfig.frameLimit`
or `timeLimit` is reached. Inside FIXED_UPDATE, `isJustPressed` and `isJustReleased` count fixed
steps instead of frames: `ButtonStates` stamps each change with the number of the step that will see
it, from the `StepClock` the loop advances through `Input.beginFixedStep`, so the first step after a
press sees it once, even when the frame of the press ran no step. Commands apply after every system,
after each of the lifetime/fade, particle and animation steps (so what they destroy is gone before
the next step) and after the second propagation. Scene systems run after the App-level systems of
the same phase. A pushed scene freezes the one below: its systems do not run, its `Scene.elapsed` stops, its velocities, kinematic bodies, lifetimes, fades,
particle emitters, sprite animations and colliders stand still (its solids stop nothing and do not move, and a moving solid of a scene above neither carries nor pushes its bodies) (the engine steps skip them and
collision detection leaves them out), its entities keep rendering. A frozen body keeps its flags: one
that landed in the last step before the freeze still reads `hasLanded` through it, and its scene's
systems see that landing in their first step back, as `Scene.elapsed` stands still. `SceneStack.enter` sets `elapsed`
to zero for every push and replace and restores the old value if `onEnter` throws; a scene that left
keeps its last value until it enters again. The engine steps ask the internal
`SceneStack.isFrozen(owner)` for an entity's owner id, and entities of no scene are never frozen;
games cannot ask, so a game keeps its game logic in scene systems, which leaves frozen entities
untouched. After the last frame every scene leaves the stack, top first, and SHUTDOWN runs.

## Audio

klyph mixes its own voices (`klyph.audio`) on SDL's audio thread: `AudioDevice` in
`klyph-platform` calls `Audio.renderer` from its callback, and effects are resampled, panned and
summed there with music decoded a block at a time by stb_vorbis. The main thread talks to the
mixer only through lock-free single-producer single-consumer rings (commands in, finished music
streams back) and atomics, and the mix path allocates nothing. `Audio`, `Music` and `Assets` are
main thread only. Without an audio device `runApp` prints one line to stderr, and with
`AppConfig(audio = AudioConfig(output = AudioOutput.NONE))` it prints nothing; either way every audio call
does nothing, so CI and autopilot runs are quiet. `Audio(config)` without an app mixes through `renderer` (a `@KlyphPlatformApi` member) by hand, which the tests,
samples and the `benchmarkMixer` task use. `SDL_AUDIO_DRIVER=dummy` gives a device that discards
its output; environment variables that are not `KLYPH_*` reach a Gradle run task only with
`--no-configuration-cache`, because the cached task keeps the environment it was configured with.

## Shaders

Shaders are written once in HLSL under `klyph/src/shaders/` as `<name>.vert.hlsl` or
`<name>.frag.hlsl`. `./gradlew :klyph:compileShaders` runs SDL_shadercross and regenerates
`klyph/src/nativeMain/kotlin/klyph/render/generated/Shaders.kt`, which embeds MSL, SPIR-V and
DXIL plus resource counts as `CompiledShader` values. That file is checked in; commit it together
with the HLSL change. The task runs before every Kotlin compile and is skipped with a warning when
the tool is missing (default path `~/.local/shadercross/bin/shadercross`, override with
`-Pklyph.shadercross=<path>`). Binding conventions for HLSL: vertex storage buffers `space0`,
vertex uniforms `space1`, fragment samplers `space2`. The MSL entry point is `main0`;
`GpuDevice.createShader(CompiledShader)` handles that.
