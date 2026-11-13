package klyph.render

// Layout of one sprite record as the vertex shader in src/shaders/sprite.vert.hlsl reads it:
// x, y, rotation, 0 | width, height, originX, originY | u0, v0, u1, v1 | r, g, b, a.
// The origin is the point of the quad, as fractions of its size from its top-left corner, that sits
// on (x, y); 0.5 and 0.5 is the center.
internal const val SPRITE_RECORD_FLOATS: Int = 16
internal const val SPRITE_RECORD_BYTES: Int = SPRITE_RECORD_FLOATS * 4
