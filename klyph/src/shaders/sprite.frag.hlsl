// Sprite fragment shader. SDL_GPU HLSL binding convention: fragment samplers in space2.

Texture2D<float4> atlas : register(t0, space2);
SamplerState atlasSampler : register(s0, space2);

struct Input {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
    float4 color : TEXCOORD1;
};

float4 main(Input input) : SV_Target0 {
    return atlas.Sample(atlasSampler, input.uv) * input.color;
}
