// Sprite vertex shader: pulls one record per sprite from a storage buffer by SV_VertexID.
// Each draw covers one run of sprites; firstSprite is the run's first record. The draw always
// starts at vertex 0 because SDL_GPU does not promise that a draw's first vertex reaches
// SV_VertexID on every backend.
// Compiled by the compileShaders Gradle task (SDL_shadercross) into render/generated/Shaders.kt.
//
// SDL_GPU HLSL binding convention: vertex storage buffers in space0 (t registers),
// vertex uniform buffers in space1 (b registers).

struct Sprite {
    float4 posRot;   // x, y, rotation, unused
    float4 size;     // width, height, unused, unused
    float4 uv;       // u0, v0, u1, v1
    float4 color;
};

StructuredBuffer<Sprite> sprites : register(t0, space0);

cbuffer Uniforms : register(b0, space1) {
    float4x4 viewProjection; // bytes 0-63
    uint firstSprite;        // bytes 64-67
    uint3 padding;           // bytes 68-79, keeps the block a multiple of 16 bytes
};

struct Output {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
    float4 color : TEXCOORD1;
};

Output main(uint vertexId : SV_VertexID) {
    uint runIndex = vertexId / 6;
    uint corner = vertexId - runIndex * 6;
    float2 unit = float2(
        (corner == 1 || corner == 3 || corner == 4) ? 1.0 : 0.0,
        (corner == 2 || corner == 4 || corner == 5) ? 1.0 : 0.0
    );
    Sprite s = sprites[firstSprite + runIndex];
    float2 local = (unit - 0.5) * s.size.xy;
    float c = cos(s.posRot.z);
    float sn = sin(s.posRot.z);
    float2 world = float2(local.x * c - local.y * sn, local.x * sn + local.y * c) + s.posRot.xy;

    Output output;
    output.position = mul(viewProjection, float4(world, 0.0, 1.0));
    output.uv = lerp(s.uv.xy, s.uv.zw, unit);
    output.color = s.color;
    return output;
}
