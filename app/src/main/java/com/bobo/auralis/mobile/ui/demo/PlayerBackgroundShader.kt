package com.bobo.auralis.mobile.ui.demo

// Original bicubic UV patch, not a port of AMLL's mesh implementation.
// Four curved rows preserve local color regions without averaging multiple full-screen copies.
internal const val ArtworkFlowShader = """
uniform shader artwork;
uniform float2 textureSize;
uniform float2 resolution;
uniform float time;
float2 curve(float2 a, float2 b, float2 c, float2 d, float t) {
    float s = 1.0 - t;
    return s*s*s*a + 3.0*s*s*t*b + 3.0*s*t*t*c + t*t*t*d;
}
half4 main(float2 position) {
    float2 uv = position / resolution;
    float t = time * 0.16;
    float breath = sin(t);
    float drift = sin(t * 0.73 + 1.2);
    float2 row0 = curve(float2(-0.12,-0.12), float2(0.15,-0.04), float2(0.85,0.08), float2(1.12,-0.12), uv.x);
    float2 row1 = curve(float2(-0.06,0.32), float2(0.12+0.23*breath,0.65), float2(0.88,0.12+0.20*drift), float2(1.08,0.25), uv.x);
    float2 row2 = curve(float2(-0.08,0.75), float2(0.38,0.95-0.16*drift), float2(0.65+0.21*drift,0.40), float2(1.05,0.82), uv.x);
    float2 row3 = curve(float2(-0.12,1.12), float2(0.28,0.98), float2(0.78,1.08), float2(1.12,1.12), uv.x);
    float2 mapped = curve(row0, row1, row2, row3, uv.y);
    // Small asymmetric drift; local colors stay distinct rather than orbiting as a whole.
    mapped += float2(0.035*sin(t*0.61), 0.025*cos(t*0.83));
    half3 color = artwork.eval(clamp(mapped, 0.0, 1.0) * (textureSize-1.0) + 0.5).rgb;
    // Separate control-area shading from the artwork's tone mapping.
    float controls = smoothstep(0.48, 0.78, uv.y);
    float2 edge = (uv-0.5)*float2(1.0,0.7);
    float vignette = 1.0 - 0.05*smoothstep(0.12,0.65,length(edge));
    color *= mix(0.94, 0.80, controls) * vignette;
    // Sub-LSB, screen-anchored dither: avoids banding without animated grain.
    float noise = fract(sin(dot(floor(position),float2(12.9898,78.233))) * 43758.5453);
    color += (noise-0.5)/255.0;
    return half4(clamp(color,0.0,1.0),1.0);
}
"""
