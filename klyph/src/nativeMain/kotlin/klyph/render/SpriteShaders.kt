package klyph.render

// Layout of one sprite record as the vertex shader in src/shaders/sprite.vert.hlsl reads it:
// x, y, rotation, 0 | width, height, 0, 0 | u0, v0, u1, v1 | r, g, b, a.
internal const val SPRITE_RECORD_FLOATS: Int = 16
internal const val SPRITE_RECORD_BYTES: Int = SPRITE_RECORD_FLOATS * 4
