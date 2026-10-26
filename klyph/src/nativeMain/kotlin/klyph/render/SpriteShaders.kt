package klyph.render

// Hand-written Metal source for the sprite pipeline. The HLSL original lives in src/shaders/;
// once SDL_shadercross is in the build these constants are replaced by its output.
//
// Record layout, 16 floats per sprite: x, y, rotation, 0 | width, height, 0, 0 | u0, v0, u1, v1 | r, g, b, a.
// Metal binding convention for SDL_GPU: uniform buffers first, then storage buffers, in [[buffer(n)]].

internal const val SPRITE_RECORD_FLOATS: Int = 16
internal const val SPRITE_RECORD_BYTES: Int = SPRITE_RECORD_FLOATS * 4

internal const val SPRITE_VERTEX_MSL: String = """
#include <metal_stdlib>
using namespace metal;

struct Sprite {
    float4 posRot;   // x, y, rotation, unused
    float4 size;     // width, height, unused, unused
    float4 uv;       // u0, v0, u1, v1
    float4 color;
};

struct Uniforms {
    float4x4 viewProjection;
};

struct VertexOut {
    float4 position [[position]];
    float2 uv;
    float4 color;
};

vertex VertexOut sprite_vertex(
    uint vertexId [[vertex_id]],
    constant Uniforms& uniforms [[buffer(0)]],
    const device Sprite* sprites [[buffer(1)]]
) {
    uint spriteIndex = vertexId / 6;
    uint corner = vertexId - spriteIndex * 6;
    // Two triangles: (0,0) (1,0) (0,1) and (1,0) (1,1) (0,1).
    float2 unit = float2(
        (corner == 1 || corner == 3 || corner == 4) ? 1.0 : 0.0,
        (corner == 2 || corner == 4 || corner == 5) ? 1.0 : 0.0
    );
    Sprite s = sprites[spriteIndex];
    float2 local = (unit - 0.5) * s.size.xy;
    float c = cos(s.posRot.z);
    float sn = sin(s.posRot.z);
    float2 world = float2(local.x * c - local.y * sn, local.x * sn + local.y * c) + s.posRot.xy;

    VertexOut out;
    out.position = uniforms.viewProjection * float4(world, 0.0, 1.0);
    out.uv = mix(s.uv.xy, s.uv.zw, unit);
    out.color = s.color;
    return out;
}
"""

internal const val SPRITE_FRAGMENT_MSL: String = """
#include <metal_stdlib>
using namespace metal;

struct VertexOut {
    float4 position [[position]];
    float2 uv;
    float4 color;
};

fragment float4 sprite_fragment(
    VertexOut in [[stage_in]],
    texture2d<float> atlas [[texture(0)]],
    sampler atlasSampler [[sampler(0)]]
) {
    return atlas.sample(atlasSampler, in.uv) * in.color;
}
"""
