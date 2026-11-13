# Prior art: klyph 2D rendering on SDL_GPU

Swept 2026-10-26. Sources searched: arXiv, OpenAlex, Semantic Scholar (via sweep script; near-empty for this domain), SDL_GPU examples and SDL source, Godot, Bevy, raylib, MonoGame and LÖVE source, Unity and Godot docs, Moonside Games, Kitware, GPUOpen, Apple WWDC, GDC slides, Linebender, JCGT. Window: 2014-2026.

Evidence levels: "code inspected" means a subagent fetched the file and read a model summary of it, not every line. "abstract only" means the abstract page. Benchmark numbers appear only where the source stated them. Preprints are marked.

## Decisions this informs

- **Sprite submission**: CPU writes one compact record per sprite (64 to 80 bytes: transform, UV rect, color) into a storage buffer, and the vertex shader pulls it by `SV_VertexID / 6`. One non-instanced draw per batch. This is SDL_GPU's reference pattern and the same shape as Godot 4.4 and Bevy, which use instance-rate attributes instead. [1, 2, 7, 8]
- **Upload strategy**: one transfer buffer and one storage buffer, mapped with `cycle = true` on the first map of the frame, uploaded in a copy pass before any render pass. SDL_GPU's cycling replaces a hand-built ring of frames in flight. [3, 4]
- **Ordering and batching**: keep the user's draw order (sorted by layer, then z, then submission index). Merge only adjacent items with equal pipeline, texture, blend mode, scissor and render target. No engine surveyed re-sorts blended 2D to maximise batching. [7, 8, 10, 11, 12]
- **Opaque vs blended**: opaque and alpha-masked sprites may be binned by state and drawn in any order; blended sprites stay in a sorted pass. Bevy does exactly this split. [8]
- **Textures**: atlases are the only batch-break mitigation any engine ships. Bindless is not exposed through SDL_GPU and on Metal needs argument buffers tier 2, so do not plan on it. Texture arrays are a candidate to prototype, not a baseline. [9, 7, 8]
- **Passes**: a fixed pass list (world, lights, composite, post chain, UI) with a small pool of render targets. A Frostbite-style frame graph only pays off at AAA pass counts. [13, 14, 15]
- **Tilemaps**: chunked, one draw per chunk, reusing the sprite record path with a persistent per-chunk buffer. The one-quad-plus-index-texture approach is simpler but caps at one tileset per layer and needs a re-upload on edit. [16, 17, 18]
- **Lighting**: per-light accumulation into a light texture with normal maps, additive/multiply/subtract blend styles, and 1D polar shadow maps for occluder polygons. Radiance Cascades is a later compute-shader option. [14, 15, 19, 20, 21]
- **Text**: SDL_ttf's GPU text engine returns atlas draw sequences that go through the sprite batcher. Slug (now public domain) and MSDF are later upgrades. [22, 23, 24]
- **Vector shapes**: defer. Vello's own maintainers now call the pure-compute renderer experimental. Rounded rects and UI primitives use SDF or analytic coverage shaders on quads for v1. [25, 26]

## Sprite submission and upload

1. **SDL_gpu_examples: PullSpriteBatch**. Reference code, current main, not a paper. https://github.com/TheSpydog/SDL_gpu_examples (Examples/PullSpriteBatch.c, Content/Shaders/Source/PullSpriteBatch.vert.hlsl)
   Claim: each frame maps a transfer buffer with cycle=true, fills 8192 `SpriteInstance` structs (64 bytes: position, rotation, size, UV rect, color), copies to a storage buffer bound with `SDL_BindGPUVertexStorageBuffers`, then one `SDL_DrawGPUPrimitives(count * 6)`. The vertex shader uses only `SV_VertexID`; sprite index = id / 6, corner from a constant table. Shader comment: StructuredBuffer in a vertex shader works through SDL_shadercross but not direct dxc (SDL issue 12200). Evidence read: code.
   Fit: exact target API and shader toolchain. Single texture, no batch breaks, no sorting.
   Repro: public repo.
   Verdict: adopt, as the baseline submission path.

2. **SDL_gpu_examples: ComputeSpriteBatch**. Reference code. Same repo, Examples/ComputeSpriteBatch.c
   Claim: uploads instance data, dispatches compute (count / 64) to write 4 vertices per sprite into a vertex buffer, then draws indexed. Evidence read: code, skimmed.
   Fit: adds a pass and a barrier; useful if sprites are generated or culled on the GPU (particles). No comparison against pulling in the repo.
   Verdict: prototype, only for a GPU particle path.

3. **SDL GPU API Concepts: Data Transfer and Cycling**. Evan Hemsley (SDL_GPU author), blog, 2024. https://moonside.games/posts/sdl-gpu-concepts-cycling/
   Claim: cycle a transfer buffer on the first map of the frame and a GPU buffer on the first upload when it is overwritten each frame; upload before render and compute passes; contents are undefined after a cycle. Evidence read: full page.
   Fit: defines the upload contract. The inference that cycling replaces a user-built ring buffer is ours, not the article's.
   Verdict: adopt.

4. **SDL_MapGPUTransferBuffer, SDL_UploadToGPUBuffer**. SDL3 wiki. https://wiki.libsdl.org/SDL3/SDL_MapGPUTransferBuffer
   Claim: `cycle` cycles the transfer buffer if already bound; unmap before encoding the upload; upload happens on the GPU timeline. No persistent mapping is documented. Evidence read: Map page full, Upload page snippet.
   Verdict: adopt.

5. **Particle System in WebGPU**. Benedikt Peter, TU Wien bachelor's thesis, 2023, not peer reviewed. https://cg.tuwien.ac.at/research/publications/2023/PETER-2023-PSW
   Claim (abstract): "on recent high end GPUs, using vertex pulling leads to significantly better performance than using instancing" for textured quads. Evidence read: abstract only.
   Fit: WebGPU, particles, one prototype. Supports pulling over instancing but is not an SDL_GPU sprite benchmark.
   Repro: none found.
   Verdict: prototype; measure pulling vs instancing on Vulkan, D3D12 and Metal before freezing the vertex format.

6. **One PolyData Mapper Instead of Two: Measuring Vertex Pulling on the Desktop**. Kitware blog, 2026-06-11. https://www.kitware.com/one-polydata-mapper-instead-of-two-measuring-vertex-pulling-on-the-desktop/
   Claim: on an RTX A5000 with OpenGL 4.6, indexed vertex pulling tied or beat the classic VBO path in every regime tested; non-indexed pulling was slower (0.31 vs 0.18 ms) because it loses the post-transform cache. Authors say AMD, Intel and Apple are unvalidated. Evidence read: full page.
   Fit: 3D meshes on one NVIDIA GPU. For quads the non-indexed id/6 pattern has only two shared vertices per quad, so the gap is probably small. Worth one micro-benchmark of indexed (4 records per quad) vs non-indexed (6).
   Verdict: prototype.

## Batching, sorting and textures in shipped engines

7. **Godot 4.4 RD canvas renderer and PR #92797 "Add batching to RendererCanvasRenderRD"**. Stuart Carnie, merged 2024-09-11. https://github.com/godotengine/godot/pull/92797 and servers/rendering/renderer_rd/renderer_canvas_render_rd.cpp
   Claim: per-item `InstanceData` (2x3 transform, modulate, src rect, dst rect, packed light indices) goes into instance-rate vertex attributes; items are walked in draw order with no re-sort; a batch breaks on clip owner, material, command type, lighting, blend, modulate, MSDF/LCD flags or texture. No atlas, array or bindless. PR text: tilemap draw calls fell from over 573,440 to 36. Post-merge regressions: font subpixel artifacts (#97195) and mobile descriptor-set limits (#97341). Evidence read: code (first 100k chars) and PR page.
   Fit: closest shipped equivalent of the planned design. Instance attributes rather than pulling, otherwise the same record shape and break rules.
   Verdict: adopt the break rules and record layout; note the mobile descriptor limit of 4 sets.

8. **Bevy sprite rendering and 2D phases** (main, 0.17/0.18). https://github.com/bevyengine/bevy crates/bevy_sprite_render/src/render/mod.rs and crates/bevy_core_pipeline/src/core_2d/mod.rs
   Claim: `SpriteInstance` is 80 bytes in an instance-step vertex buffer with a static 6-index buffer, drawn `draw_indexed(0..6, 0, range)`. Sprites go in the sorted `Transparent2d` phase keyed by z; a batch breaks when the image changes or a non-sprite item interleaves. Mesh2d routes Opaque and Mask to binned phases keyed by (pipeline, draw function, asset, material bind group) and Blend to the sorted phase. No in-tree 2D lighting; post is tonemap and upscale. 0.17 notes: the Material/Material2d split forced copy-pasted 2D code and is being unified. Evidence read: code and release notes.
   Fit: confirms opaque-binned vs blended-sorted split and image-change batch breaks.
   Verdict: adopt the phase split; avoid duplicating a 2D material system from a 3D one.

9. **Explore bindless rendering in Metal**. Apple WWDC21 session 10286. https://developer.apple.com/videos/play/wwdc2021/10286
   Claim: bindless needs argument buffers tier 2 and explicit residency declarations. Evidence read: transcript.
   Fit: SDL_GPU does not expose argument buffers (unverified), so bindless sprites are out of reach through this stack.
   Verdict: skip; atlases and possibly texture arrays instead.

10. **SDL's own GPU renderer**. src/render/gpu/SDL_render_gpu.c. https://github.com/libsdl-org/SDL
    Claim: CPU-interleaved vertices in one vertex buffer that doubles from 64 KiB; consecutive commands merge when texture, scale mode, address mode, blend, color scale and render state match; no sort key; pipelines cached by parameters. A source comment admits the load-op logic after a clear is imperfect. Evidence read: code.
    Fit: the fallback the engine would get by using SDL_CreateGPURenderer. State-equality merging without layers or lighting.
    Verdict: skip as the engine renderer; keep as a bootstrap option for tools.

11. **raylib rlgl**. src/rlgl.h. https://github.com/raysan5/raylib
    Claim: CPU-filled dynamic vertex and index arrays, 8192 quads and 256 draw entries per batch, break on primitive mode or texture change, no sort, one texture per entry. Evidence read: code (first 100k chars).
    Verdict: skip; the classic CPU-expanded baseline the pulling path replaces.

12. **MonoGame SpriteBatcher and LÖVE Graphics**. https://github.com/MonoGame/MonoGame (SpriteBatcher.cs), https://github.com/love2d/love (Graphics.cpp)
    Claim: MonoGame pools 4-vertex items with 16-bit indices in 5461-item chunks, sorts for Texture/FrontToBack/BackToFront modes and flushes on texture change. LÖVE accumulates into persistently mapped stream buffers and flushes on mode, format, texture, shader, index overflow or render target change. Evidence read: code.
    Verdict: skip; same lesson as 11, with the extra point that an explicit sort mode (texture vs depth) is a user-visible choice worth offering.

## Passes and pixel-perfect output

13. **FrameGraph: Extensible Rendering Architecture in Frostbite**. Yuriy O'Donnell, GDC 2017, talk slides. https://www.slideshare.net/DICEStudio/framegraph-extensible-rendering-architecture-in-frostbite
    Claim: passes declare resources, the graph is compiled (culling, lifetimes, aliasing) then executed; the deck reports 1042 MB to 472 MB transient memory at 4K on DX12. Evidence read: slide text, diagrams missing.
    Fit: the payoff is aliasing and async compute at AAA pass counts. A 2D engine with under ten passes does not need the compile step.
    Verdict: skip for v1; revisit if user-composable post chains arrive.

14. **Godot docs: 2D lights and shadows**. https://docs.godotengine.org/en/stable/tutorials/2d/2d_lights_and_shadows.html
    Claim: PointLight2D and DirectionalLight2D, CanvasModulate ambient, normal and specular maps via CanvasTexture, LightOccluder2D, shadow filters None/PCF5/PCF13, blend modes Add/Subtract/Mix, SDF occluder option for custom shaders. In the RD renderer lights sit in a uniform buffer and are shaded inside the item draw. Evidence read: docs and renderer code.
    Verdict: adopt as the feature target; the in-draw light indexing avoids a separate light texture at the cost of a per-item light limit.

15. **Unity URP 2D Renderer data and light blend styles**. https://docs.unity3d.com/6000.0/Documentation/Manual/urp/2DRendererData-overview.html, https://docs.unity3d.com/6000.2/Documentation/Manual/urp/LightBlendStyles.html
    Claim: lights render into light textures (render scale default 0.5, up to 4 light textures, 1 shadow texture) before 2D objects; up to 4 blend styles (Additive, Multiply, Subtractive) with mask-channel routing; lights target sorting layers; a sorting-layer colour capture texture exists. SRP Batcher for 2D arrived in 2023.1 and a 6000.0.38f1 fix repaired sprite batching. Evidence read: docs; batching from issue-tracker snippets.
    Verdict: adopt the light-texture-at-half-resolution pattern as the first lighting implementation; it is simpler than Godot's in-draw indexing.

16. **Phaser 4 TilemapGPULayer**. API docs. https://docs.phaser.io/api-documentation/class/tilemaps-tilemapgpulayer
    Claim: whole layer is one quad sampling a data texture (32-bit texel: flip flags, animation flag, 28-bit index) plus an animation texture; fixed cost per screen pixel; limits: one tileset, 4096x4096 tiles, orthographic only, explicit regeneration after edits. Evidence read: docs.
    Verdict: prototype only if a single-tileset, static-layer fast path is needed; otherwise skip.

17. **Bevy tilemap chunk example and bevy_ecs_tilemap**. https://bevy.org/examples/2d-rendering/tilemap-chunk/
    Claim: a 64x64 chunk rendered with a single draw call; bevy_ecs_tilemap uses one mesh per chunk. Evidence read: example page; code not inspected.
    Verdict: adopt chunking.

18. **Godot TileMapLayer rendering quadrants**. https://docs.godotengine.org/en/4.3/classes/class_tilemaplayer.html
    Claim: `rendering_quadrant_size` default 16 (256 tiles per quadrant) batched into one canvas item; ignored on Y-sorted layers. Evidence read: class reference snippet.
    Verdict: adopt; 16x16 chunks are a sensible default, and Y-sort disables chunking.

## 2D lighting and shadows

19. **Fast 2D Shadows in Unity Using 1D Shadow Mapping**. Rob Ware, Game Developer, 2018. https://gamedeveloper.com/programming/fast-2d-shadows-in-unity-using-1d-shadow-mapping
    Claim: one polar row per light stores nearest occluder distance by angle; with instancing the whole map is one draw; a 64-light stress test used about a 1024x64 map. No timings given. Evidence read: full text.
    Verdict: adopt as the shadow method for v1 lighting.

20. **2D Pixel Perfect Shadows**. mattdesl, LibGDX wiki, undated. https://github.com/mattdesl/lwjgl-basics/wiki/2D-Pixel-Perfect-Shadows
    Claim: occluder target, lightSize x 1 distance texture, blurred edges, additive blend; fill-rate bound at around 20 lights on a GeForce 9400. Evidence read: full text.
    Verdict: adopt as the implementation walkthrough for 19.

21. **Holographic Radiance Cascades for 2D Global Illumination**. Freeman, Sannikov, Margel, arXiv:2505.02041, 2025, preprint. https://arxiv.org/abs/2505.02041
    Claim (abstract page): single-shot scene-agnostic 2D GI, 1.85 ms at 512x512 and 7.67 ms at 1024x1024 on an RTX 3080 Laptop GPU. Evidence read: abstract only. Related: GM Shaders Radiance Cascades tutorial https://mini.gmshaders.com/p/radiance-cascades (full text), and Osborne and Sannikov arXiv:2408.14425 (abstract only, formal basis).
    Fit: needs the compute path and a scene radiance plus occluder texture.
    Repro: none checked.
    Verdict: watch; a post-v1 lighting mode.

## Text and vector shapes

22. **SDL_ttf GPU text engine**. API docs, SDL_ttf 3.0+. https://wiki.libsdl.org/SDL3_ttf/TTF_CreateGPUTextEngine, https://wiki.libsdl.org/SDL3_ttf/TTF_GetGPUTextDrawData
    Claim: the engine takes an `SDL_GPUDevice` and returns a linked list of atlas draw sequences (vertices and texture coordinates) that the caller submits. The docs do not say whether the atlas is SDF. Evidence read: docs.
    Verdict: adopt for v1; route its draw data through the sprite batcher.

23. **Slug: GPU-Centered Font Rendering Directly from Glyph Outlines**. Eric Lengyel, JCGT 6(2), 2017, peer reviewed; reference shaders released March 2026 with the patent dedicated to the public domain. https://jcgt.org/published/0006/02/02/, https://github.com/EricLengyel/Slug
    Claim (repo README; paper not read this session): coverage evaluated from quadratic Bezier bands in the pixel shader, HLSL reference shaders under MIT/Apache with a credit request. Evidence read: README only.
    Verdict: watch; read the paper and the grant wording before adopting.

24. **msdfgen and multi-channel signed distance fields**. Viktor Chlumský, code plus thesis, 2015 onward. https://github.com/Chlumsky/msdfgen
    Claim: multi-channel fields reproduce sharp corners almost perfectly; anti-aliasing fails below a screen pixel range of 2. Evidence read: README; thesis not read.
    Verdict: prototype when scaled UI text is needed.

25. **Vello**. Linebender, project README, 2026. https://github.com/linebender/vello
    Claim: three renderers exist; the pure compute renderer "remains an experimental implementation", and the sparse-strips CPU/GPU designs that need no compute shaders are the production direction. Evidence read: README.
    Fit: Rust/wgpu, design reference only.
    Verdict: skip for v1; the field itself moved away from pure compute 2D.

26. **Pathfinder**. servo/pathfinder, Patrick Walton. https://github.com/servo/pathfinder
    Claim: GPU rasterizer with compute and hardware-raster modes, README says incomplete. Evidence read: README.
    Verdict: skip.

## Gaps

- No peer-reviewed source compares vertex pulling, instancing and compute-generated vertices for 2D sprites on Vulkan, D3D12 and Metal. Sources 5 and 6 are the nearest, and both are single-GPU prototypes. The engine needs its own benchmark.
- No source covered texture arrays as a batch-break mitigation on SDL_GPU, or whether SDL_GPU exposes argument buffers on Metal.
- Depth and layer sorting for blended 2D turned up nothing beyond engine code; order-independent transparency searches returned noise.
- Auto-tiling is a CPU data step in every engine seen, never a renderer feature; no primary source on tile bleeding beyond "use nearest sampling".
- Subpixel camera motion on a low-resolution target remains a reported problem (forum evidence only); bevy_pixcam documents the two trade-offs (per-sprite upscale vs offscreen target).
- The Slug paper, the MSDF thesis, the Vello "Potato" design doc and Evan Wallace's text post were not read.
- Paper script queries for GPU-driven rendering, sprite batching, vertex pulling, frame graphs, tilemaps and 2D shadows returned zero relevant results; the field lives in engine source and talks.

# Prior art: klyph 1.1 platformer kit

Swept 2026-11-10. Sources searched: arXiv, OpenAlex and Semantic Scholar through the sweep script (0 results on every query; this domain lives in engine source, docs and blogs), Godot source and docs, Box2D v3.1 docs and release post, Rapier and Jolt docs, Avian 0.6 release post, bevy_rapier docs, Bevy issues and examples, Unity Input System and 2D physics docs, Unity DOTS Physics docs, Gradle user guide, Maddy Thorson's Celeste article, Jakub Tomsu's blog. Window: 1987 (one paper) and 2019-2026.

Evidence levels as in the first sweep. "code inspected" below means the raw file was downloaded and the quoted lines read in this session; "page read" means a subagent fetched the page and read a model summary of it; "snippet only" means a search result, not opened. No preprints turned up.

## Decisions this informs

- **Input edges in FIXED_UPDATE (#18)**: implement the planned semantics as a per-key *step stamp*, not a latch set. On a press, record `pressedStep = currentStep + 1`; inside FIXED_UPDATE `isJustPressed` is `pressedStep == currentStep`; outside it keeps the per-frame edge. No clearing step, no ordering between a clearing system and its readers, and `isJustReleased` comes for free. This is exactly Godot 4's shipped design. A tap that is pressed and released before a frame's first step still reports `isJustPressed` true in that step. [1, 2, 3]
- **Body model (#13)**: velocity-driven `KinematicBody` moved by the engine with `isOnFloor`, `isOnWall`, `isOnCeiling` set by the last move; gravity stays in the game. Godot, Rapier and Jolt all leave gravity to the caller. [5, 9, 10]
- **Resolution (#13)**: axis-separated swept AABB, X then Y, keeping float world units. The 2025 designs (Box2D 3.1's plane solver, Avian 0.6's sweep-and-project loop) exist to reconcile several conflicting contact planes from round shapes against meshes and slopes; with static axis-aligned boxes and no slopes the planes are always axis-aligned and the two-axis split gives the same answer. Copy three details from them: zero the velocity component on the blocked axis after each move, keep a small skin gap between body and solid, and snap to the floor only when the body was on the floor before the move and is moving down, within a short snap distance. [5, 8, 9, 11]
- **One-way ledges (#13)**: block only when the body moves down and its bottom edge was at or above the ledge's top before the move. Use that one predicate in the sweep and in any overlap or depenetration path; Godot's one-way bugs came from the motion check and the rest check disagreeing. Drop-through is a game-side "ignore this ledge for N frames", as in Unity. [6, 7, 12]
- **Sweep queries (#13)**: walk the grid cells the swept box covers with Amanatides and Woo's traversal, treat the mover as a point against solids expanded by its half-extents, compute the time of impact per axis, and return the earliest hit as a fraction along the move plus normal and entity, the shape of `b2World_CastShape`. Deduplicate solids that span several cells. [4, 8]
- **Collider scale (#23)**: keep "transform scale scales the collider" as the default; Godot, Unity DOTS Physics and bevy_rapier all do this. Model the opt-out on bevy_rapier's `ColliderScale::Absolute`. Say in the KDoc that a circle takes the uniform scale and that non-uniform scale on a circle is not exact, which every engine surveyed warns about. [13, 14, 15]
- **Build logic (#12)**: Gradle now recommends an included `build-logic` build over `buildSrc`, because any change in `buildSrc` invalidates the whole build. Whatever the engine modules end up applying, do the #12 change as "retire buildSrc into an included build" and test two things Gradle's docs do not answer: a root project applying a plugin from an included build that the convention plugins also depend on, and the configuration cache across the two. [16, 17, 18]

## Input edges under a fixed timestep

1. **Godot `core/input/input.cpp`**. Engine source, master as of 2026-11-10. https://github.com/godotengine/godot/blob/master/core/input/input.cpp
   Claim: on a press transition the action stores `pressed_physics_frame = get_physics_frames() + 1` and `pressed_process_frame = get_process_frames()`, with the comment "As input may come in part way through a physics tick, the earliest we can react to it is the next physics tick." `is_action_just_pressed` returns `pressed_physics_frame == get_physics_frames()` when `is_in_physics_frame()`, else the process-frame comparison. Release mirrors it. A `legacy_just_pressed_behavior` project setting additionally requires the key to still be down. Evidence: code inspected (lines 440-452, 1105-1125).
   Fit: the same semantics klyph plans, with a stamp instead of a latch. Verdict: adopt.
2. **Godot `main/main.cpp`**, the physics step loop. Engine source, master. https://github.com/godotengine/godot/blob/master/main/main.cpp
   Claim: inside `for (iters < advance.physics_steps)` the loop calls `Input::flush_buffered_events()` (when agile flushing is on), sets `_in_physics = true` and increments `_physics_frames` once per step. Evidence: code inspected (around line 5012-5020).
   Fit: confirms the stamp is per step, so the second step of a frame does not see a press the first one saw. klyph translates events once per frame, so it needs no mid-frame flush. Verdict: adopt.
3. **Jakub Tomsu, "Input in fixed timestep"**. Blog, 2024-08-04. https://jakubtomsu.github.io/posts/input_in_fixed_timestep
   Claim: keeps a frame input and a tick input; after each tick clears the Pressed and Released flags and keeps Down, so the first tick after a press sees it; divides mouse and scroll deltas by the number of ticks in the frame. Evidence: page read.
   Fit: the latch form of the same semantics; the delta split is worth copying if mouse motion is ever read in FIXED_UPDATE. Verdict: watch.
4. **Bevy issue #6183, "Inputs can be missed (or duplicated) when using a fixed time step"**. Issue, opened 2022-10, still open 2026. https://github.com/bevyengine/bevy/issues/6183. Related: discussion #9164 and the "physics in fixed timestep" example, https://bevy.org/examples/movement/physics-in-fixed-timestep
   Claim: `just_pressed` resets per frame so fixed-step systems miss or duplicate it; proposals include a timestamped event buffer and per-schedule tracking; the shipped guidance is an accumulate-before, clear-after-only-if-a-step-ran pattern in user code. Evidence: pages read.
   Fit: the same bug, unsolved at engine level for four years; evidence that the transparent fix is worth having. Verdict: skip as a design source.
5. **Unity Input System 1.12, "Avoid missed or duplicate discrete events"** and "Mixed timing scenarios". Docs, 2025. https://docs.unity3d.com/Packages/com.unity.inputsystem@1.12/manual/timing-missed-duplicate-events.html
   Claim: with Update Mode "Process in FixedUpdate", `WasPressedThisFrame` must be read from `FixedUpdate` only; in Dynamic mode the docs tell you to set a bool in `Update` and consume it in `FixedUpdate`. Evidence: pages read.
   Fit: a global mode switch, not phase-aware. Verdict: skip.

Not retrieved: Unreal, Defold's engine behaviour (a 2022 forum thread shows users latching by hand, page read), the Overwatch GDC talk.

## Kinematic bodies, solids and one-way ledges

6. **Godot `CharacterBody2D` class reference**. Engine docs, 4.x stable. https://docs.godotengine.org/en/stable/classes/class_characterbody2d.html
   Claim: `move_and_slide()` moves by `velocity` and slides, `max_slides` 4; `is_on_floor/wall/ceiling` are set by the last call and classified by `up_direction` and `floor_max_angle`; `floor_snap_length` 1.0, skipped while moving up; `safe_margin` 0.08 treats near-touching as colliding and pushes out before moving. Evidence: page read.
   Fit: the template for the flags and the skin margin; slopes and platform velocity out of scope. Verdict: adopt the flag set and margin.
7. **Godot `CollisionShape2D.one_way_collision`** and issue #25967. Docs plus issue (2019, 3.x). https://docs.godotengine.org/en/stable/classes/class_collisionshape2d.html, https://github.com/godotengine/godot/issues/25967
   Claim: one-way is a per-shape flag with a `one_way_collision_margin` that thickens the shape for fast movers. The issue's fix (snippet only) notes the motion check and rest-info check must agree. Evidence: page read; issue snippet only.
   Fit: with the previous position known, the "bottom was above the top" test replaces the margin; the agreement rule is the one to keep. Verdict: adopt the rule.
8. **Box2D 3.1 character mover docs and release post**. Docs and blog, April 2025, marked experimental. https://box2d.org/documentation/md_character.html, https://box2d.org/posts/2025/04/box2d-3.1
   Claim: a geometric solver in four steps: `b2World_CastMover` (shape cast that does not get stuck when touching), `b2World_CollideMover` (gather contact planes), `b2SolvePlanes` (closest position satisfying all planes, depenetration and movement in one loop), `b2ClipVector` (remove velocity into the planes so it does not accumulate). Assumes a capsule with fixed rotation; named for rollback and networked games. Evidence: page read.
   Fit: the newest mainstream design; overkill for axis-aligned planes, but the clip-velocity step is the right rule and `CastShape`'s fraction result is the right query shape. Verdict: prototype the API shape, skip the plane solver.
9. **Rapier character controller guide**. Engine docs, current. https://rapier.rs/docs/user_guides/rust/character_controller
   Claim: `move_shape` returns a corrected translation the caller applies; no gravity; a small `offset` gap for stability; snap-to-ground only if the body touched ground at the start, the move has a downward component and the end gap is under the snap distance; hits reported in chronological order. Evidence: page read.
   Fit: the clearest statement of the snap conditions. Verdict: adopt the snap rule.
10. **Jolt `CharacterVirtual`**. Class reference, current. https://jrouwe.github.io/JoltPhysics/class_character_virtual.html
    Claim: `Update` moves along velocity and stops where blocked; `StickToFloor` casts down a step distance; ground state is OnGround, OnSteepGround, NotSupported or InAir; gravity is the caller's. Evidence: page read.
    Fit: confirms the shared structure. Verdict: skip, 3D and stairs.
11. **Avian 0.6 release post, "move-and-slide"** (Joona Aalto). Blog, after 2025-11, read via a mirror. https://joonaa.dev/blog/12/avian-0-6
    Claim: built-in move-and-slide: an initial depenetration pass, then a loop of sweep, contact-plane collection and velocity projection until the move completes or an iteration cap. Evidence: page read (mirror).
    Fit: the 2025-26 production design for the general case; for static boxes it reduces to the axis split. Verdict: watch.
12. **Unity `PlatformEffector2D`**. Engine docs, current. https://docs.unity.com/en-us/engine/current/manual/unity2d/2d-physics/effectors-2d/platform-effector-2d-reference
    Claim: one-way is decided by the contact normal against a "Surface Arc" centred on local up; no drop-through, games disable the contact for a while. Evidence: page read.
    Fit: with boxes the arc is "top face only"; drop-through stays game-side. Verdict: adopt the drop-through stance.
    Also: **Maddy Thorson, "Celeste and TowerFall Physics"** (blog, undated). http://maddymakesgames.com/articles/celeste_and_towerfall_physics/ Claim: integer pixel stepping with a sub-pixel remainder, one axis at a time, one pixel per step, Solids push or carry Actors. Evidence: page read. Fit: the pixel-engine alternative; klyph's units are floats and its boxes are axis-aligned, so an exact time of impact gives the same result without per-pixel loops. Verdict: skip for 1.1, revisit if moving platforms land (its carry and squish rules).
    And: **Amanatides and Woo, "A Fast Voxel Traversal Algorithm for Ray Tracing"**, Eurographics 1987, peer-reviewed. https://www.eecs.yorku.ca/~amana/research/grid.pdf Claim (abstract only): one addition and two comparisons per cell step, with handling for objects spanning several cells. Fit: the traversal for the sweep over the hashed grid. Verdict: adopt; read the PDF before implementing.

Not retrieved: Paul Firth's speculative contacts article (belongs to dynamic solvers anyway), Box2D's one-sided platform pre-solve sample, Godot's `character_body_2d.cpp`, the gamedev.net swept-AABB tutorial (403).

## Collider scale

13. **Godot `CollisionShape2D` docs and `collision_shape_2d.cpp`**. Docs 4.2 and source. https://docs.godotengine.org/en/4.2/classes/class_collisionshape2d.html
    Claim: "A non-uniformly scaled CollisionShape2D will likely not behave as expected. Make sure to keep its scale the same on all axes and adjust its shape resource instead"; the node passes its whole transform, scale included, to the physics server. Evidence: page read; code excerpt inspected by a subagent. No opt-out. Verdict: adopt the default and the warning.
14. **Unity DOTS Physics 1.1, "Physics body data concepts"**. Docs. https://docs.unity3d.com/Packages/com.unity.physics@1.1/manual/concepts-data.html
    Claim: transform scale "automatically also applies to the collider geometry"; non-uniform scale is exact for a box along its axes and not for a sphere, capsule or cylinder; baking folds the scale into the collider. Evidence: page read. Verdict: adopt the circle caveat.
15. **bevy_rapier2d `ColliderScale`**. API docs, current. https://docs.rs/bevy_rapier2d/latest/bevy_rapier2d/geometry/enum.ColliderScale.html
    Claim: `Relative(v)` multiplies the `GlobalTransform` scale, `Absolute(v)` replaces it; non-uniform scale of a ball is approximated by a polygon. Evidence: page read (partial). Verdict: adopt as the model for `scalesWithTransform`.

## Gradle build logic

16. **Gradle, "Structuring Builds" best practices**. Docs, Gradle 9.x. https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html
    Claim: "The preferred location for build logic is an included build (typically named `build-logic`), not in `buildSrc`"; any change in `buildSrc` makes the entire build out of date; settings plugins from `build-logic` must be included in `pluginManagement`. Evidence: page read. Verdict: adopt.
17. **Gradle, "Composite Builds"**. Docs. https://docs.gradle.org/current/userguide/composite_builds.html
    Claim: `buildSrc` is "essentially an included build that Gradle manages automatically"; configuration-cache properties are read from the root build only. Evidence: page read. Verdict: adopt.
18. **Gradle, "Sharing build logic between subprojects"**. Docs. https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html
    Claim: convention plugins over `subprojects {}`; a `build-logic` composite is the other sharing route. Evidence: page read. Verdict: adopt.

## Gaps

The paper search found nothing on any of the four topics; the knowledge is in engine source and docs. Not answered by any source: whether a root project may apply a plugin from an included build that its own convention plugins also depend on (test it in the #12 prototype); Box2D 3.1's one-way handling for the mover; Unity 2D's exact transform-scale rule for `Collider2D` (the reference page only says sizes are local).

# Prior art: klyph 1.2 scrolling worlds

Swept 2026-11-13. Sources searched: Godot docs, source and blog, Phaser docs and source, Bevy docs, examples and two community crates, SDL3 wiki, raylib and libGDX source, Unity Cinemachine and Rigidbody2D docs, Unreal docs (search snippets only), Glenn Fiedler's blog. The paper sources were skipped: the 1.1 sweep returned nothing for engine conventions and these topics are the same kind. Window: 2017-2026.

Evidence levels: "read" means a subagent fetched the page this session and read a model summary of it; "snippet" means a search-result summary only, unverified. Where a page was silent on a question, the entry says so.

## Decisions this informs

- **Parallax factor**: 0 is fixed to the screen, 1 moves with the world, fractions are far away. Godot, Phaser and the Bevy crates agree. A sky is a factor-0 layer, not a special case. [1, 3, 6, 9]
- **Parallax repeat**: a period in world units per axis (0 = none), wrapped with a floored modulo before the position is used, and enough copies emitted to cover the visible world rectangle. Godot's `repeat_times` is a manual zoom fix the blog itself calls brute force; computing copies from the visible rect avoids it. [1, 3, 4, 5]
- **Zoom and parallax**: no engine read scales the factor by zoom; Godot's `parallax_2d.cpp` ignores zoom. Layers scale like world content. [5]
- **Camera offset**: applied after the bounds clamp, never fed into follow. Godot documents that `offset` "can push the view past the limit"; Bevy's official shake example adds the offset last and removes it next frame so gameplay never sees it. [10, 17]
- **Teleport**: set the transform first, then reset interpolation; the reverse streaks. Godot's `reset_physics_interpolation()`. [11, 13]
- **Camera follows the drawn position**: Godot's advanced interpolation page says to follow the interpolated position and calls that read a special case for cameras. Phaser and Cinemachine follow a target object; the point form plus a "drawn position" read is the smaller primitive. [12, 16]
- **Texture repeat**: sampler address mode set per texture, as raylib `SetTextureWrap` and SDL_GPU's sampler do; Godot's per-node `texture_repeat` is the one exception. Tiling a sub-region is unsupported in Godot's `TextureRect` TILE for an atlas with a margin and libGDX avoids wrap entirely by splitting quads. klyph forbids tiling a region that is not the whole texture. [18, 19, 20, 22, 24, 26]
- **Tile size**: SDL's `RenderTextureTiled` and Phaser's `tileScale` express the tile as a size, libGDX as the region size. A tile size in world units is the primary parameter. [23, 26, 28]
- **Sprite origin**: normalized within the region, 0..1, default centre; it is the pivot for rotation and scale (libGDX, raylib). Flip mirrors about the anchored position (Godot's source negates the rect size). Collider offsets stay independent everywhere read. [21, 25, 27, 29]
- **Scene clock**: a clock owned by the scene that stops when the scene does is Phaser's `scene.time`; Bevy's `Time<Virtual>` and Unity's `timeScale` are global clocks with pause and scale on the same object. klyph puts `elapsed` on `Scene` and leaves time scale for later. [30, 32, 34, 35]

## Backdrops and parallax

1. **Parallax2D class reference**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_parallax2d.html
   Claim: `scroll_scale` multiplies the offset "to simulate distance from the camera"; `repeat_size` repeats the children's textures by that value, an axis of 0 does not repeat; `repeat_times` is "useful for when zooming out". Also `ignore_camera_scroll`, `follow_viewport`, `autoscroll`, `limit_begin`/`limit_end`.
   Fit: same model as the klyph component. Verdict: adopt for factor and repeat-period semantics.
2. **ParallaxLayer class reference**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_parallaxlayer.html
   Claim: `motion_scale` axis 0 does not scroll; `motion_mirroring` is "the interval, in pixels, at which the layer is drawn repeatedly". Deprecated in favour of Parallax2D.
   Verdict: skip, superseded by [1].
3. **2D parallax tutorial**. Godot docs, read. https://docs.godotengine.org/en/stable/tutorials/2d/2d_parallax.html
   Claim: 1 scrolls with the camera, below 1 looks farther, 0 holds still; sky 0.1, forest 0.7. `repeat_size` snaps the node forward or back as the camera scrolls past it. Pitfall: a texture centred on the origin breaks infinite repeat. Zooming out breaks coverage; the fix is `repeat_times` 3, called brute force.
   Verdict: adopt the semantics; the zoom workaround is the gap klyph closes by sizing copies from the visible rect.
4. **Parallax progress report**. Godot blog, read. https://godotengine.org/article/parallax-progress-report/
   Claim: ParallaxBackground's problems were a strict two-node hierarchy, a CanvasLayer base class and a "mirroring" property that only repeats. Parallax2D is a plain Node2D. Scaling a layer to fake depth scales size and speed together, undesired. Automatic `repeat_times` on zoom is a wish.
   Verdict: adopt the lesson: a parallax layer is an ordinary entity component, not a separate pass.
5. **parallax_2d.cpp**. Godot source, read (excerpted summary). https://raw.githubusercontent.com/godotengine/godot/master/scene/2d/parallax_2d.cpp
   Claim: `scroll_ofs = screen_offset * scroll_scale`; with repeat, `mod = fposmod(scroll_ofs.x - scroll_offset.x, repeat_size.x * scale.x)` then `scroll_ofs.x = screen_offset.x - mod`. Zoom is never referenced.
   Verdict: adopt the floored modulo on the scaled offset.
6. **ScrollFactor component**. Phaser 4.2.1 source, read. https://app.unpkg.com/phaser@4.2.1/files/src/gameobjects/components/ScrollFactor.js
   Claim: 1 moves in sync with the camera, 0 does not move at all; physics ignores the factor; `setScrollFactor(x)` sets both axes.
   Verdict: confirms [1].
7. **TileSprite**. Phaser docs, read. https://docs.phaser.io/api-documentation/class/gameobjects-tilesprite
   Claim: `tilePositionX/Y` scroll the texture, `tileScaleX/Y` scale it; the period is the texture size.
   Verdict: watch; a tile scroll offset is a later addition if a game wants it.
8. **bevy-parallax 0.10.0**. Crate source, read (partial). https://docs.rs/bevy-parallax/0.10.0/src/bevy_parallax/parallax.rs.html
   Claim: spawns a tile grid of `ceil(diagonal / (tile_size * scale))` tiles; speed per axis; no zoom logic.
   Verdict: skip; tile grid at spawn is the pre-computed form of what klyph does per frame.
9. **bevy-prototype-parallax**. Crate page, snippet. https://lib.rs/crates/bevy-prototype-parallax
   Claim: speed 0 static, 1.0 linear with the camera.
   Verdict: confirms [1], unverified.

Not read: Unity (no built-in parallax, background knowledge), LÖVE, libGDX, GameMaker (403). No source discussed far-from-origin precision.

## Camera offset, clamp order, interpolation

10. **Camera2D class reference**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_camera2d.html
    Claim: `limit_*` stop the camera "but offset can push the view past the limit"; `offset` is "useful for looking around or camera shake"; `get_screen_center_position()` is the real view centre; `reset_smoothing()` jumps and clamps at once.
    Verdict: adopt: offset after the clamp, position unshaken.
11. **Using physics interpolation**. Godot docs, read. https://docs.godotengine.org/en/stable/tutorials/physics/interpolation/using_physics_interpolation.html
    Claim: on teleport, set the transform first, then call `reset_physics_interpolation()`; the reverse gives "unwanted streaking". Objects that start moving: set, reset, then set the expected first-tick transform.
    Verdict: adopt the order rule and the KDoc wording.
12. **Advanced physics interpolation**. Godot docs, read. https://docs.godotengine.org/en/stable/tutorials/physics/interpolation/advanced_physics_interpolation.html
    Claim: cameras should follow "not the position of the target on the physics tick, but the interpolated position", read with `get_global_transform_interpolated()` in `_process()`, "only once or twice for special cases such as cameras". Camera3D only.
    Verdict: adopt: a public drawn-position read, used by the camera.
13. **Node class reference**. Godot docs, read (first 100k chars). https://docs.godotengine.org/en/stable/classes/class_node.html
    Claim: `reset_physics_interpolation()` notifies the node and its ancestors; recommended for temporarily disabling interpolation. The "previous = current" semantics come from [11] and a snippet.
14. **Physics interpolation introduction**. Godot docs, read. https://docs.godotengine.org/en/stable/tutorials/physics/interpolation/physics_interpolation_introduction.html
    Claim: previous and current tick transforms blended by the interpolation fraction. Nothing on cameras.
15. **BaseCamera**. Phaser docs, read. https://docs.phaser.io/api-documentation/class/cameras-scene2d-basecamera
    Claim: with bounds the worldview is clamped; bounds smaller than the viewport stop scrolling. Nothing on shake order.
    Verdict: adopt the "bounds smaller than the view stop scrolling" sentence for the guide.
16. **Camera**. Phaser docs, read. https://docs.phaser.io/api-documentation/class/cameras-scene2d-camera
    Claim: `shake(intensity, duration)`; `followOffset` is subtracted from the target; `startFollow(target, …)` takes an object. Shake-vs-bounds order not stated.
17. **2D screen shake example**. Bevy examples, read. https://bevy.org/examples/camera/2d-screen-shake/
    Claim: the shake is added to the camera transform in PostUpdate before propagation and restored in PreUpdate "so that gameplay logic doesn't need to care about camera shake".
    Verdict: adopt the principle: the shaken position is never the camera's state.

Also read: Cinemachine Confiner 2D (https://docs.unity3d.com/Packages/com.unity.cinemachine@3.1/manual/CinemachineConfiner2D.html, pipeline order not stated, pipeline page 404), Rigidbody2D.interpolation (nothing on reading the rendered position), Fix Your Timestep (https://gafferongames.com/post/fix_your_timestep/, blended state only goes to render, nothing on cameras).

## Texture repeat and sprite origin

18. **CanvasItem**. Godot docs, read (first 100k). https://docs.godotengine.org/en/stable/classes/class_canvasitem.html
    Claim: `texture_repeat` DISABLED / ENABLED / MIRROR, inherited per node; TextureRect has its own tiling and ignores it.
    Verdict: skip the per-node state; klyph keeps it per texture.
19. **TextureRect**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_texturerect.html
    Claim: STRETCH_TILE "tile inside the node's bounding rectangle", unsupported for an AtlasTexture with a non-zero margin.
    Verdict: adopt the atlas restriction.
20. **Sprite2D**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_sprite2d.html
    Claim: `centered` bool plus `offset` in pixels; `region_enabled` cuts from an atlas; `flip_h` bool.
21. **sprite_2d.cpp**. Godot source, read. https://raw.githubusercontent.com/godotengine/godot/master/scene/2d/sprite_2d.cpp
    Claim: `dest_offset = offset`, minus half the frame if centred; flip negates the rect size, so the flip happens about the anchored position.
    Verdict: adopt: flip mirrors about the origin.
22. **SDL_GPUSamplerAddressMode**. SDL3 wiki, read. https://wiki.libsdl.org/SDL3/SDL_GPUSamplerAddressMode
    Claim: REPEAT wraps, MIRRORED_REPEAT, CLAMP_TO_EDGE clamps to 0..1. Part of the sampler, not the draw.
    Verdict: adopt; klyph already creates one sampler per filter, it becomes one per (filter, address).
23. **SDL_RenderTextureTiled**. SDL3 wiki, read. https://wiki.libsdl.org/SDL3/SDL_RenderTextureTiled
    Claim: the source rect is repeated to fill the destination; `scale` sets the tile size (32x32 at scale 2 is 64x64 tiles); a sub-rectangle is allowed (the 2D renderer splits quads, not stated on the page).
    Verdict: adopt the tile-size parameter.
24. **raylib.h**. Source, read. https://raw.githubusercontent.com/raysan5/raylib/master/src/raylib.h
    Claim: `SetTextureWrap(texture, wrap)` per texture; `DrawTexturePro(texture, src, dst, origin, rotation, tint)` with a pixel origin.
25. **rtextures.c**. Source, read (partial). https://raw.githubusercontent.com/raysan5/raylib/master/src/rtextures.c
    Claim: in DrawTexturePro "origin is relative to destination rectangle size", rotation is about it.
26. **TiledDrawable.java**. libGDX source, read. https://raw.githubusercontent.com/libgdx/libgdx/master/gdx/src/com/badlogic/gdx/scenes/scene2d/utils/TiledDrawable.java
    Claim: tiles without wrap: whole tiles counted from `width / regionWidth`, one draw per tile, UVs trimmed for the partial edge; rotation unsupported. Works for atlas regions.
    Verdict: watch; the split-quad path is the fallback if atlas tiling is ever wanted.
27. **Sprite.java**. libGDX source, read. https://raw.githubusercontent.com/libgdx/libgdx/master/gdx/src/com/badlogic/gdx/graphics/g2d/Sprite.java
    Claim: `setOrigin` "in relation to the sprite's position for scaling and rotation", in pixels; vertices start at `-originX`, scale, then rotate; flip is separate state.
    Verdict: adopt: the origin is the pivot for both rotation and scale.
28. **TileSprite** (see 7): `tileScale` is the tile size relative to the texture; a frame must belong to the current texture; power-of-two restriction lifted in v4.
29. **Anchor**. Bevy docs, read. https://docs.rs/bevy/latest/bevy/sprite/struct.Anchor.html
    Claim: `Anchor(Vec2)`, "normalized (relative to its size) offset of a 2d renderable entity from its Transform", nine named constants, required by Sprite; `flip_x`/`flip_y` separate.
    Verdict: adopt the normalized form; klyph's 0..1 matches Canvas.drawSprite already.

Not read: Unity tiled sprite modes and pivot (pages not reached), LÖVE (403), libGDX `Texture.setWrap`, Phaser's origin default, mipmap bleeding under atlas wrap (klyph has no mipmaps, so it does not arise).

## Scene-local clock

30. **Pausing games**. Godot docs, read. https://docs.godotengine.org/en/stable/tutorials/scripting/pausing_games.html
    Claim: `process_mode` (Inherit, Pausable, WhenPaused, Always, Disabled) decides what runs under `get_tree().paused`; AnimationPlayer, audio and particles pause on their own.
    Verdict: skip; klyph already freezes by scene.
31. **Engine class**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_engine.html
    Claim: `time_scale` is "the speed multiplier at which the in-game clock updates", affects timers and delta, not audio.
32. **Time class**. Godot docs, read. https://docs.godotengine.org/en/stable/classes/class_time.html
    Claim: `get_ticks_msec` is time since the engine started; nothing on pause (the "never pauses" reading is inference).
33. **Time.timeScale**. Unity docs, read. https://docs.unity.com/en-us/engine/current/script-reference/unityengine/time/timescale
    Claim: at 0 the application "acts as if paused if all your functions are frame rate independent"; FixedUpdate and WaitForSeconds stop; fixedDeltaTime must be scaled by hand.
    Verdict: watch; shows a time scale must reach the fixed step, so it is not bolted on later.
34. **Time<Virtual>**. Bevy docs, read (first 100k). https://docs.rs/bevy/latest/bevy/time/struct.Virtual.html
    Claim: the default clock; `pause()` zeroes delta and stops elapsed; `set_relative_speed()`; `set_max_delta()`; updated from `Time<Real>`.
35. **Time.Clock**. Phaser docs, read. https://docs.phaser.io/api-documentation/class/time-clock
    Claim: a scene plugin (`scene.time`) that "respects Scene pausing, time scaling, and shutdown lifecycle"; `now`, `timeScale` (0 freezes), `paused`.
    Verdict: adopt the scene-owned clock.
36. **Scenes**. Phaser docs, read. https://docs.phaser.io/phaser/concepts/scenes
    Claim: pause is render without update; sleep is neither; stop shuts down. Clock effect not stated.

Snippet only: Unreal `GetTimeSeconds` (stops when paused, dilated), `GetUnpausedTimeSeconds`, `GetRealTimeSeconds` (pages 403). Not read: Unity `unscaledTime`, Godot Timer and Tween under pause, LÖVE and libGDX idioms. No source states what a stopped clock does when its scene is removed and re-added.

## Gaps

- Parallax under camera rotation: no source read handles it; klyph covers the rotated view's bounding box or documents that parallax layers ignore rotation.
- Far-from-origin float precision for repeating layers: no source; the floored modulo keeps numbers small, which is the only mitigation found.
- Cinemachine's pipeline order (Body, Aim, Noise, Confiner): page 404, unconfirmed.
- Pop and re-push of a scene with its own clock: no precedent; klyph's rule (reset on enter, keep while frozen) is its own.
