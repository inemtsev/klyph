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
