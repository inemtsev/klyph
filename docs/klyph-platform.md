# Module klyph-platform

The SDL3, stb_image and stb_vorbis bindings and the thin Kotlin API over them that the klyph
engine is built on. It is the only module that touches C interop. Games depend on `klyph`, which
brings this module in as an API dependency.

The plumbing only the engine calls, the GPU wrappers, the event pump, the audio device and its
renderer, SDL start-up and shutdown, window creation, compiled shaders and frame capture, is
marked `KlyphPlatformApi`. Using it is a compile error until the caller opts in, and it is outside
the 1.0 compatibility promise. Everything else here, such as `Color`, `Image`, the key and button
types, `Files` and `Window`'s properties, is ordinary API that games use.
