# Game engine research: Godot, Unity, GameMaker (October 2026)

Research for a new 2D game engine in Kotlin. Three sub-agent reports follow the synthesis.
Claims marked [bg] in the agent reports come from model background knowledge, not a fetched source.

## Synthesis

### What all three got right

1. The engine owns the loop and the lifecycle. Godot (`_ready`/`_process`), Unity (`Awake`/`Update`), GameMaker (Create/Step/Draw). Beginners never write a main loop.
2. One declaration is both code and editor data. Godot `@export`, Unity `[SerializeField]`, GameMaker object variables. The inspector is generated from the code.
3. A reusable, instanceable unit that is also the level unit. Godot scenes, Unity prefabs, GameMaker objects and rooms.
4. Time to first moving sprite is the most-cited reason people stay. GameMaker wins this outright; Godot is close; Unity has lost it to domain reload.
5. Text, diffable project files. Godot `.tscn`/`.tres`, Unity YAML, GameMaker YY (and plain text with GM-CLI). All three still break references on rename or merge badly, so stable IDs matter more than the text format.

### What each one uniquely got right

- Godot: scene = prefab = level = UI, one concept. Signals as the built-in decoupling primitive. A real 2D renderer in pixel units. The editor is built from the engine's own UI nodes, which keeps the UI toolkit honest. MIT licence and a small binary.
- Unity: component composition as the default mental model. Nested prefabs with explicit per-instance overrides and apply/revert. ScriptableObjects as data assets separate from scenes. A mainstream typed language with real IDE support. Separately versioned packages.
- GameMaker: immediate-mode drawing (`draw_sprite`, `draw_text`, surfaces) on top of the retained instance model. Collision queries (`place_meeting`, `instance_place`) that cover most 2D gameplay with no physics engine. Physics and particles opt-in per room, so the simple case pays nothing.

### Where each one fell down

- Godot: string-path node lookup (`get_node("../Player")`) breaks on rename; override semantics for instanced/inherited scenes are weak and lossy; GDScript typing was bolted on late and still lacks generics; C# does not run on every export target; physics backend swapped late (Jolt); 3.x to 4.x migration stranded projects; consoles need third-party ports.
- Unity: 2023 runtime-fee trust collapse; 10-20 second enter-play-mode from whole-assembly domain reload; three render pipelines, two input systems, three UI systems, two entity models all alive at once; serialization could not express Dictionary for 15 years and polymorphism needs `[SerializeReference]`; YAML merge tool misses prefab-instance conflicts (marked Won't Fix); defaults push Update polling, singletons, and `GameObject.Find`.
- GameMaker: global function soup with no namespaces or modules; integer-handle `ds_*` structures were unsafe and typed handles only arrived in LTS 2026.0 as a breaking change; parent-object inheritance encourages deep hierarchies; implicit event ordering (Begin Step/Step/End Step, depth/layer draw order) hides bugs; IDE instability and project corruption reports over many years; licence whiplash; closed source until the source-available GMRT runtime.

### Recommendations for a Kotlin 2D engine

Object model
- Entities composed of components (Unity), where a saved tree of entities is the single reusable unit for prefab, level and UI (Godot). Composition is the extension mechanism; do not make subclassing the reuse tool (Godot, GameMaker).
- Lifecycle hooks with an explicit, documented phase order. Expose the order in code (an enum or ordered list), not in docs only (GameMaker lesson).
- Typed references between entities, resolved at load time by stable ID. No string paths, no `Find`, no tags as the easy path. Kotlin can generate typed accessors from scene files.
- Signals/events as a first-class, typed primitive. "Call down, signal up."

Data and files
- Scene and resource files: plain text, one entity per block, ordered keys, stable IDs on every entity and property path. Design for plain git merges with no custom merge tool (Unity lesson).
- Instance overrides as an explicit tracked diff keyed by stable ID, with apply/revert, from day one (Godot's weakness, Unity's strength, Unity's array-index override hazard).
- Serialization must handle maps, sealed/polymorphic types and generics from the first release. Retrofitting this is what hurt Unity and GameMaker.
- Data assets separate from scenes (ScriptableObject idea).

Iteration speed
- Treat time to first sprite and edit-to-run latency as release-blocking metrics. JVM startup and classloading are the risk; plan hot reload at a designed boundary rather than whole-world reload.
- Immediate-mode draw API available inside the retained scene (GameMaker). Multi-pass 2D post-processing as a first-class feature (Godot gap).
- Small runtime; optional subsystems (physics, particles, audio) that cost nothing unless enabled.

Language and typing
- Kotlin gives static typing, generics and IDE support for free: this is Godot's biggest gap and Unity's biggest strength. Keep the API in the stdlib idiom (the klyph API style) so completion discovers it.
- Design hot loops (update, collision, render) to allocate nothing. The JVM GC is the equivalent of Unity's C# GC stutter complaints. Value classes and primitive arrays internally.
- Check that the runtime runs on every target you promise (Godot C# web gap). Pick a 2D physics backend early or make it pluggable (Godot Jolt lesson).

Ecosystem and trust
- Permissive open-source licence, small download, no login. Trust is now a feature that Unity lost and Godot won.
- One system per concern. Never ship a second renderer, UI or input system while the first is alive (Unity lesson). Migrate and remove.
- Separately versioned modules with a verified core set. Breaking changes behind a compatibility switch with a migration path (GameMaker's Deprecated Behaviours, Godot 3 to 4 pain).
- CLI for every project operation so CI and tooling are first-class (GM-CLI).
- Do not tie the engine to a storefront or a per-install fee.

### Evidence caveats

- Community sentiment sources (Reddit, forums, postmortems) were thin for all three; the agents flagged background-knowledge claims.
- Unity roadmap items beyond 6.3 come from secondary blogs.
- Godot survey numbers are jam-entry and survey shares, not market share.
- GameMaker LTS 2026.0 and GMRT claims come from the vendor blog.

---

## Agent report: Godot 4.x

Search results were thin on hard data. Claims marked **[bg]** come from background knowledge, not a fetched source.

### Positioning and adoption
- Who uses it: indies, jam participants, solo or small teams, mostly 2D, plus a growing 3D minority. MIT licence permits any commercial use.
- Survey figures: Game Developer Collective/Omdia survey has 12% naming Godot as engine of choice, up 8% year over year. Godot took 47% of GMTK Game Jam entries (10,511 games) in 2026, ending Unity's nine-year lead in that data. A jam sample is not market share.
- Post-Unity-fee growth: a Godot Foundation programmer said the user base "doubled in a month" in autumn 2023.
- About 3M cumulative Steam and Google Play installs by Feb 2026 (duplicates counted). SteamDB shows Godot releases up about 50% from 2024 to 2025 (unverified at source).
- W4 Games raised $18M to commercialise Godot services.
- Shipped titles in sources: Slay the Spire 2, Apocalypse Approaches, Ruffed Up, Iron Village. [bg] Brotato, Dome Keeper, Buckshot Roulette, Cassette Beasts.

### What developers love
- Node and scene tree, scenes as the reusable unit. A scene is a saved node tree; it can be instanced inside another scene or inherited from. One concept covers prefab, level, UI screen and character. The FAQ says Godot does not use ECS; it chose inheritance for usability, with composition via child nodes.
- Signals. Nodes emit events; parents or siblings connect. Child scenes need no knowledge of consumers. "Call down, signal up" [bg].
- GDScript. The FAQ says it exists to reduce complexity and avoid problems with Lua and Python (threading, class extension, C++ bindings). Exports show in the inspector, scripts hot-reload, the language knows the node API.
- Fast feedback loop. Miguel de Icaza: "The feedback loop is incredible." Pausing in the debugger suspends only user script. Thomas Gelman: "It can run on a potato." Cause: small binary and an editor that is itself a Godot app.
- Editor built on the engine's own UI toolkit (Control nodes). Consistent look, GPU rendering, no LGPL concerns, and it forces the UI system to be good.
- Dedicated 2D engine [bg]. Pixel coordinates and its own renderer. 2D-specific nodes: TileMapLayer, Sprite2D, AnimatedSprite2D, Camera2D.
- AnimationPlayer can key any property of any node, including method calls [bg].
- MIT licence, single small executable, no login.
- Text resources (.tscn/.tres) diff reasonably in git [bg]. Weakness: line-oriented, reference paths and UIDs.
- Prebuilt export templates [bg].

### Dislikes and weaknesses
- Console export needs third parties. Only Steam Deck is officially supported; Switch, Xbox, PS5 go through W4 Games and similar. Cause: console SDKs are under NDA, incompatible with open source.
- GDScript performance and typing. FAQ says the language is rarely the bottleneck; C++ via GDExtension is fastest. Critics call static typing "a halfway affair", note missing tuples and no user-defined generics.
- C# gaps. FAQ calls C# "relatively new"; missing on web; marshalling cost on engine calls and GC stutter. [bg] Mobile export was a long-running gap.
- Nested scene overrides. Editable Children and inherited scenes are weaker than Unity's nested prefab variants. Forum reports: edits lost when the inherited scene is opened standalone (4.7.1); a changed default stops being treated as an override; property renames break overrides; ownership lost for nodes instanced at runtime (4.4.1). Community plugins add Unity-style Apply/Revert.
- String-path node access. get_node("../Player") breaks on rename or move. Proposal #7572 notes the engine can only warn at runtime. Suggested fixes: @export references and @onready variables.
- Physics. Jolt arrived experimental in 4.4, default 3D engine for new projects in 4.6. Results differ from Godot Physics. Mostly a 3D concern.
- 2D shaders. Multi-pass 2D shaders are missing; you need SubViewports or one merged shader.
- Rendering. Vulkan and OpenGL default, D3D12 experimental since 4.3. [bg] Renderer split (Forward+, Mobile, Compatibility) and driver bugs are ongoing.
- [bg] 3D vs Unity/Unreal, 3.x to 4.x breakage (many projects stayed on 3.x), profiler and debugger gaps, documentation gaps, project-scale refactoring.

### Copy
- Scene as the single reusable unit: prefab, level and UI in one concept.
- Signals or an event type as a first-class feature.
- Dedicated 2D pipeline in pixel or world units.
- Dogfood the UI toolkit in the editor.
- Text, diffable scene and resource files with stable IDs so renames do not break references.
- Hot reload and fast startup.
- Permissive licence and small download.
- General property-keyframe animation player.
- Built-in language with editor-aware exports. Kotlin gives static typing for free.

### Avoid
- String-path node lookup. Generate typed accessors from scene files (godot-kotlin-tree does this).
- Weak nested override semantics. Design instance overrides as an explicit, tracked diff with apply/revert.
- A typing system bolted on afterward.
- Late physics replacement. Pick a 2D physics backend early or make it pluggable.
- A language runtime that cannot run on every export target.
- Gating console targets behind third-party ports.
- A big breaking redesign between major versions with no migration path.
- Deep inheritance as the main extension mechanism.
- Missing 2D post-processing in the pipeline.

### Sources
- https://www.gamedeveloper.com/programming/godot-adoption-is-rising-what-are-devs-enjoying-about-the-engine-
- https://gameworldobserver.com/2024/03/27/godot-doubled-user-base-after-unity-controversy
- https://www.pocketgamer.biz/w4-games-hopes-to-capitalise-on-godot-growth-with-18m-investment/
- https://docs.godotengine.org/en/4.5/about/faq.html
- https://docs.godotengine.org/en/4.5/tutorials/platform/consoles.html
- https://www.w4games.com/w4consoles
- https://godotengine.org/releases/4.6/
- https://gamefromscratch.com/godot-4-4-gets-native-jolt-physics-support/
- https://github.com/godotengine/godot-proposals/issues/7572
- https://forum.godotengine.org/t/did-godot-improve-how-editable-children-info-persists/143200
- https://store.godotengine.org/asset/bakacandy/scene-instance-override/
- https://medium.com/@ChadJohnsonOfficial/youre-doomed-to-fail-the-sad-truth-about-gdscript-in-godot-d6c5bd14ff44
- https://azhdarchid.com/gdscript-good-bad-ugly/
- https://www.surmado.com/blog/gdscript-code-review-checklist
- https://github.com/tomwyr/godot-kotlin-tree

Gaps: no fetched GitHub issues on 3.x to 4.x breakage, profiler gaps, C# mobile, renderer bugs or refactor-safety.

---

## Agent report: Unity (Unity 6 era)

Six web searches, no page fetches. Claims marked (background) come from background knowledge. Roadmap items beyond 6.3 come from secondary blogs.

### Positioning and adoption
- Share. Video Game Insights (Jan 2025, Steam only): Unity powered about 51% of 2024 Steam releases. Share slowly eroding, squeezed by Unreal above and Godot and GameMaker below. Godot drove over two-thirds of growth in the small-engine tier since 2020. By units sold, Unreal and in-house engines beat Unity in 2024. Numbers are vendor estimates and exclude mobile.
- Users: indie through mid-size studios, plus mobile (background).
- Shipped 2D games (background): Hollow Knight, Cuphead, Ori and the Blind Forest. Hades is commonly reported as a custom engine. Many such teams built heavy custom tooling on top.
- Runtime fee. Announced Sept 2023 as a per-install fee. Cancelled Sept 2024 for games on any Unity version. Reverted to seat pricing: Pro up 8% to $2,200 per seat, Enterprise up 25%, from Jan 2025. Personal revenue cap doubled to $200k, splash screen optional in Unity 6. Price increases to be considered annually only. Fee still applies to non-gaming Industry customers. No 2026 data measuring trust recovery.

### What developers love (background, verify before quoting)
- GameObject/Component composition. Behaviour assembled from small parts; designers build entities in the editor without code.
- MonoBehaviour lifecycle (Awake, Start, Update, OnDestroy). Engine owns the loop and calls hooks.
- Inspector plus field serialization. Public or [SerializeField] fields appear in the editor with no extra code.
- Prefabs with nested prefabs and overrides. Reusable templates with per-instance diffs.
- ScriptableObjects. Data assets decoupled from scenes: config, shared tuning, event channels.
- Play-mode iteration. Run inside the editor and tweak live. Also the biggest source of complaints.
- C#. Mainstream typed language with strong IDEs.
- Asset Store and Package Manager. Versioned, modular packages.
- Platform reach.
- 2D toolset: Sprite Atlas, Tilemap, 2D Animation (bones), Cinemachine, Input System, Box2D physics. Unity 6.3 docs describe new low-level 2D physics APIs on Box2D v3 with multithreading and better determinism, and 3D meshes alongside sprites in 2D URP. Tools ship as separately updatable packages.
- Addressables: content loading by key, with remote delivery.
- DOTS/ECS and Burst: opt-in data-oriented path.

### Dislikes and weaknesses
Widely held:
- Licensing trust (above). The reversal came with seat-price increases.
- Iteration time. Domain reload and enter-play-mode slowness; forum threads span years. Users report 10-20 second waits. "Disable domain reload" saves about 10-12 seconds but needs careful handling of static state. One poster had fought this for over a decade.
- Overlapping systems. Secondary coverage: URP becoming the single pipeline, built-in pipeline entering deprecation (~6.7), HDRP investment shrinking. UI Toolkit gaining features (world-space UI, shaders, filters in 6.3) while uGUI remains. Legacy and new input systems coexist. ECS and GameObjects only now being unified.
- "3D engine with 2D on top." Common forum opinion. A Nov 2025 preprint (same 2D platformer in both engines): Unity slightly higher frame rates, Godot less memory and smaller builds. GameDevBeginner says Unity's 2D tools are more polished but the gap is small for simple projects.
- Serialization limits. Dictionary serialization only appears in the Unity 6.6 alpha docs, so it was missing for roughly 15 years; opt-in with key-type restrictions. A forum post claims prefab overrides record dictionary entries by array index, which can misapply overrides when the base changes. [SerializeReference] allows polymorphism but needs non-generic concrete types. Unity's Behavior package docs warn that types are lost when classes are renamed or moved.
- Scene and prefab merging. YAML needs UnityYAMLMerge. The tool misses conflicts when two people change the same prefab instance; the issue tracker lists that as Won't Fix for 6.x. Unity's own advice is to publish small changes often.

Common but background knowledge: closed source, large builds and slow IL2CPP builds, GC allocation pitfalls, GameObject.Find and string lookups, editor stability, Update-polling and singleton defaults, deprecation churn.

Roadmap (secondary, unverified): 2026 plan centres on stability, CoreCLR migration (lifecycle API in 6.5, fast enter-play-mode default in 6.6, experimental desktop CoreCLR in 6.7, ecosystem readiness in 6.8), verified packages, ECS-GameObject unification.

### Copy
- Composition (entity plus components) with data-first components.
- One declaration that is both code and editor-visible data.
- Prefab-style templates with explicit, diffable overrides keyed by stable ID, not array index.
- Data assets separate from scenes, with first-class maps and polymorphic types from day one.
- A 2D-native pipeline: sprite batching and atlases, tilemaps, skeletal animation, deterministic fixed-step physics.
- Modular, separately versioned packages with a verified set.
- A text format designed for merging: stable IDs, one entity per block, ordered keys.
- Hot reload and fast iteration as a core requirement.
- Typed asset references and keyed loading without string-only lookups.
- Open source, to avoid the trust problem.

### Avoid
- A closed, non-forkable core with licensing that can change retroactively.
- Parallel systems for rendering, UI, input and entity models.
- Whole-assembly reload as the iteration mechanism.
- Serialization that cannot express maps, polymorphism and generics; global static state that breaks when reload is disabled.
- String-based lookups as the easy path.
- Defaults that push per-frame polling and singletons.
- Invisible allocations in hot loops.
- Text scene formats that need a special merge tool.

### Sources
- https://gameworldobserver.com/2024/09/12/unity-cancels-runtime-fee-new-pricing-changes
- https://wnhub.io/news/engines/item-45444
- https://support.unity.com/hc/en-us/articles/30322080156692
- https://unity.com/pricing-updates
- https://sensortower.com/blog/the-big-game-engines-report-of-2025
- https://discussions.unity.com/t/game-engines-report-2025-video-game-insights-steam-only/1681939
- https://discussions.unity.com/t/why-is-domain-reload-sooo-slow-and-will-this-ever-be-fixed/798176
- https://discussions.unity.com/t/extremely-slow-entering-play-mode-in-the-latest-unity-2022-3-22-f1/942755
- https://gamedevbeginner.com/godot-vs-unity-for-making-your-first-game/
- https://www.cubix.co/blog/unity-vs-godot-a-game-developers-guide/
- https://www.preprints.org/manuscript/202511.1981/v1
- https://digitalproduction.com/2025/11/26/unitys-2026-roadmap-coreclr-verified-packages-fewer-surprises/
- https://www.strayspark.studio/blog/unity-6-4-to-6-8-roadmap-indie-developers-2026
- https://docs.unity.com/en-us/engine/6000.6/manual/whats-new/unity63.md
- https://docs.unity.com/en-us/engine/6000.6/manual/upgrade-guides/upgrade-guide-unity63.md
- https://discussions.unity.com/t/built-in-dictionary-serialization-and-authoring-in-unity-6-6-alpha/1722926
- https://docs.unity.com/en-us/engine/6000.6/manual/programming-environment/code-reload-serialization/script-serialization/dictionaries
- https://docs.unity3d.com/Packages/com.unity.behavior@1.0/manual/understand-limitations.html
- https://issuetracker.unity3d.com/issues/unityyamlmerge-tool-is-not-detecting-conflicts-in-prefab-instances
- https://support.unity.com/hc/en-us/articles/207332543-What-are-the-solutions-to-scene-prefab-conflict-merging
- https://habr.com/en/articles/974390/comments (anecdotal)

---

## Agent report: GameMaker

Strong primary sources on licensing, LTS 2026 and GMRT; almost nothing on current community sentiment or postmortems. Sections 2 and 3 rest partly on long-standing community knowledge.

### Positioning and adoption
- Who uses it: solo and small-team 2D developers, jam participants, beginners. Known shipped games: Undertale, Hyper Light Drifter, Hotline Miami, Katana ZERO, Chicory, Pizza Tower, Forager (not re-verified).
- Why it stays popular for 2D: its core model (sprites, rooms, instances, events) is a 2D model. The 2023 blog claims a 3x rise in active users since Opera bought it and more than 6,000 games on gx.games.
- Licensing history. Before Nov 2023: Creator and Indie subscriptions for commercial export. 23 Nov 2023: free for non-commercial use on all non-console platforms; one-time commercial licence (about $99) replaced subscriptions; console export stays on Enterprise (about $80/month or $800/year); asset bundles free. GameFromScratch read the timing as a jab at Unity. Per Wikipedia (unverified): paid-game purchasing on gx.games being phased out as of Sept 2026.
- Current direction (2026). LTS 2026.0 shipped 21 May 2026; LTS cadence through 2026.4 in Q1 2028. Old GMS2 runtime declared feature complete, SDK updates and critical fixes only until at least Q1 2028. GMRT, a ground-up new runtime, is the focus: easier to modify and extend, more build control, better performance, easier support for other languages and platforms. GMRT source available to all users for desktop, mobile and web; Enterprise gets console source. Source-available, not open source.

### What developers love
- Object/event model. Behaviour hangs off an object as named events (Create, Step, Draw, Destroy, collision, alarms). The engine owns the loop and lifecycle.
- Rooms and layers. A room holds layered instances, tiles, backgrounds, and (new in 2026) UI layers using flexbox-style panels. Level layout is visual data.
- Integrated sprite editor and asset pipeline. Drawing, importing, origin and collision mask, then use by name in code, all in one tool.
- Immediate-mode drawing. draw_sprite, draw_text, surfaces. A custom effect is one function call.
- Time to first moving sprite: minutes. Probably the engine's biggest single advantage.
- GML. Dynamic and forgiving, with structs, methods and light typing added over time. Feather static analysis on by default in LTS 2026.0.
- Collision and instance queries. place_meeting, instance_place and instance lists cover most 2D gameplay. Built-in physics is opt-in per room.
- Small runtime and fast iteration (background).
- Sequences, tilesets, particles. New Particle Editor and Particle System asset in 2026. More of the engine is authored visually as data.
- Modular IDE (2026). A Package Manager installs IDE parts (Code Editor 2, GMRT, Prefab Library) separately.

### Dislikes and weaknesses
- Weak typing and unsafe data structures (widely held). Older ds_* structures are referenced by integer handles; an itch.io 2022 thread called them "insanely unsafe". LTS 2026.0 makes resource handles carry their type and rejects mismatches.
- Global function soup and no modules (widely held). No namespacing or module system; no 2026 source shows this fixed.
- Scaling to large projects (widely held). Parent-object inheritance encourages deep hierarchies. GM-CLI plus plain-text project files aims at this (one outlet; LTS blog does not confirm the format change).
- IDE stability (mixed). 2018 blog: stability and project corruption problems. 2023 blog: old Delphi codebase slowed fixes, Marketplace UI bogged down. 2023 forum report: a project overwrote a script with duplicates and lost code. Marketplace integration removed in LTS 2026.0.
- Version churn and breaking changes (sourced). LTS 2026.0: handle typing, collision bounding-box changes with a compatibility mode, asset discarding by default, removed Laptop Mode. Blog tells users to back up first. A Deprecated Behaviours section re-enables old behaviour.
- Pricing whiplash and closed source. Closed source cited as a reason to move to Godot (itch.io devlogs, anecdotal). GMRT source access is a partial answer.
- Limited 3D. GMRT 0.20 plans glTF loading and a scene graph.
- VM vs YYC performance (background). VM is slow for heavy logic; YYC is the usual fix. GMRT claims early benchmark gains with no numbers.
- Event ordering and the global instance model (background). Step Begin / Step / Step End and draw order by depth and layer are implicit; order bugs are hard to debug.
- GX.games push: no critical coverage found.
- AI integration: GamingOnLinux (Apr 2026) noted some users may dislike the Claude Code integration in GM-CLI. One outlet's remark.

### Copy
- Engine-owned loop with lifecycle hooks (create, update, draw, destroy).
- Rooms with named layers as data; plain-text, diff-friendly format.
- Immediate-mode draw API on top of a retained scene.
- Integrated asset pipeline with assets referenced by typed name.
- Optional subsystems (physics, particles) that cost nothing unless enabled.
- Small runtime, instant run.
- Typed asset handles from the start.
- A CLI and API for all project operations so CI and tooling are first-class.
- Source-available runtime with a stable LTS line.

### Avoid
- Integer handles for data structures.
- A global namespace of functions and assets.
- Implicit event ordering.
- Deep parent-object inheritance as the reuse tool.
- Opaque binary or single-file project formats.
- Feature churn without a compatibility switch.
- Separate backend per platform (GMRT's unified backend is the lesson).
- Tying the engine to a storefront.

### Sources
- https://gamemaker.io/en/blog/update-spring-2026
- https://gamemaker.io/blog/lts-2026-release
- https://gamemaker.io/en/blog/gmrt-open-beta
- https://gamefromscratch.com/gamemaker-announces-major-pricing-changes/
- https://en.wikipedia.org/wiki/GameMaker
- https://gamingonlinux.com/2026/04/gamemaker-is-launching-gmrt-a-new-modern-runtime-with-source-access
- https://www.pocketgamer.biz/gamemaker-launches-new-runtime-to-enable-larger-scale-development-workflows/
- https://80.lv/articles/see-what-s-next-for-gamemaker-in-spring-2026-update
- https://csanyk.com/2018/08/a-look-back-at-gamemaker-studio-1-x/
- https://variksenpelotin.wordpress.com/2019/07/27/transition-from-gamemaker-to-godot/
- https://itch.io/post/6056382/view-in-topic and other itch.io threads (anecdotal)

Not covered: the GameMaker forum, Reddit, developer postmortems.
