// Sprite vertex shader: pulls one record per sprite from a storage buffer by SV_VertexID.
// Compile with SDL_shadercross to MSL, SPIR-V and DXIL. Not yet wired into the build; the MSL
// in SpriteShaders.kt is the hand-written equivalent.
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
    float4x4 viewProjection;
};

struct Output {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
    float4 color : TEXCOORD1;
};

Output main(uint vertexId : SV_VertexID) {
    uint spriteIndex = vertexId / 6;
    uint corner = vertexId - spriteIndex * 6;
    float2 unit = float2(
        (corner == 1 || corner == 3 || corner == 4) ? 1.0 : 0.0,
        (corner == 2 || corner == 4 || corner == 5) ? 1.0 : 0.0
    );
    Sprite s = sprites[spriteIndex];
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
