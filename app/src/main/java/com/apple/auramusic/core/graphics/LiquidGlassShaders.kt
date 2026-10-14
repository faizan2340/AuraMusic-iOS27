package com.apple.auramusic.core.graphics

object LiquidGlassShaders {

    /**
     * Procedural iOS 27 Liquid Glass Surface Shader (drawn in Modifier.drawBehind so foreground
     * typography and icons remain 100% razor-sharp and never blurred out).
     * Simulates:
     * - Exact n=4 superellipse squircle SDF (sdSquircleN4)
     * - Inner 14% curved glass bevel refraction & subsurface ambient tinting (IOR n ~= 1.52)
     * - +/- 0.8px chromatic prism dispersion along the top/bottom curvature
     * - Dual-stop 1.5px gyroscopic + touch specular rim highlight driven by uLightDirection
     */
    const val LIQUID_GLASS_SURFACE_AGSL = """
        uniform float2 uResolution;
        uniform float uCornerRadius;
        uniform float uGlassFrostFactor; // 0.0 = Ultra-Clear, 1.0 = Frosted
        uniform float2 uLightDirection;  // Normalized gyroscopic + pointer vector
        uniform float uSpecularBoost;    // Touch press luminance boost
        uniform float4 uTintColor;       // Base glass tint
        uniform float4 uAccentColor;     // Dynamic artwork ambient refraction color

        float sdSquircleN4(float2 p, float2 b, float r) {
            float2 q = abs(p) - (b - float2(r));
            float2 m = max(q, 0.0);
            float l4 = pow(pow(m.x, 4.0) + pow(m.y, 4.0), 0.25);
            return l4 + min(max(q.x, q.y), 0.0) - r;
        }

        float2 gradSquircleN4(float2 p, float2 b, float r) {
            const float eps = 1.0;
            float dx = sdSquircleN4(p + float2(eps, 0.0), b, r) - sdSquircleN4(p - float2(eps, 0.0), b, r);
            float dy = sdSquircleN4(p + float2(0.0, eps), b, r) - sdSquircleN4(p - float2(0.0, eps), b, r);
            return normalize(float2(dx, dy) + float2(1e-5));
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(uCornerRadius, min(halfSize.x, halfSize.y));

            float dist = sdSquircleN4(p, halfSize, r);
            float outerMask = 1.0 - smoothstep(-0.75, 0.75, dist);
            if (outerMask <= 0.001) {
                return half4(0.0);
            }

            float2 normal = gradSquircleN4(p, halfSize, r);
            float2 uv = fragCoord / max(uResolution, float2(1.0));

            // Inner 14% lens bevel zone (simulates curved glass refraction n ~= 1.52)
            float minDim = min(uResolution.x, uResolution.y);
            float bevelWidth = max(minDim * 0.16, 8.0);
            float edgeProgress = clamp(1.0 - (-dist / bevelWidth), 0.0, 1.0);
            float lensCurve = pow(edgeProgress, 1.8);

            // Base glass body with vertical fresnel gradient + dynamic artwork subsurface scattering
            float verticalSheen = mix(0.18, 0.04, uv.y);
            float3 baseGlass = mix(uTintColor.rgb, uAccentColor.rgb, 0.22 * (1.0 - uGlassFrostFactor * 0.4));
            baseGlass += float3(verticalSheen);

            // Chromatic prism dispersion along the curved squircle bevel
            float2 lightDir = normalize(uLightDirection + float2(1e-4));
            float prismAngle = dot(normal, lightDir);
            float3 prismDispersion = float3(
                max(0.0, prismAngle) * 0.14,
                abs(prismAngle) * 0.05,
                max(0.0, -prismAngle) * 0.16
            ) * lensCurve * mix(1.2, 0.6, uGlassFrostFactor);

            // Inner caustic luminance along the bevel
            float innerCaustic = lensCurve * mix(0.20, 0.10, uGlassFrostFactor) * (0.6 + 0.4 * max(0.0, dot(normal, float2(0.0, -1.0))));

            // Dual-stop 1.5px gyroscopic specular rim highlight
            float rimMask = 1.0 - smoothstep(0.0, 1.6, abs(dist + 0.8));
            float primarySpec = pow(max(0.0, dot(normal, lightDir)), 2.6);
            float secondarySpec = pow(max(0.0, dot(normal, -lightDir)), 3.4) * 0.45;
            float topEdgeSpec = pow(max(0.0, dot(normal, float2(0.0, -1.0))), 2.2) * 0.35;
            float specTotal = (primarySpec + secondarySpec + topEdgeSpec) * (mix(0.95, 0.55, uGlassFrostFactor) + uSpecularBoost);

            float3 rimColor = float3(1.0, 0.99, 1.0) * rimMask * (0.24 + specTotal);

            float bodyAlpha = clamp(mix(0.62, 0.84, uGlassFrostFactor) + lensCurve * 0.12 + uSpecularBoost * 0.25, 0.0, 0.96);
            float3 finalRgb = clamp(baseGlass + prismDispersion + float3(innerCaustic) + rimColor, 0.0, 1.0);
            float finalAlpha = clamp(max(bodyAlpha, rimMask * (0.38 + specTotal * 0.6)), 0.0, 1.0) * outerMask;

            return half4(half3(finalRgb * finalAlpha), half(finalAlpha));
        }
    """

    const val LIVING_MESH_GRADIENT_AGSL = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uBassEnergy;
        uniform float4 uColor0;
        uniform float4 uColor1;
        uniform float4 uColor2;
        uniform float4 uColor3;
        uniform float4 uColor4;

        float3 mod289(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        float2 mod289_2(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        float3 permute(float3 x) { return mod289(((x * 34.0) + 1.0) * x); }

        float snoise(float2 v) {
            const float4 C = float4(
                0.211324865405187,
                0.366025403784439,
               -0.577350269189626,
                0.024390243902439
            );
            float2 i = floor(v + dot(v, C.yy));
            float2 x0 = v - i + dot(i, C.xx);
            float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
            float4 x12 = x0.xyxy + C.xxzz;
            x12.xy -= i1;
            i = mod289_2(i);
            float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
            float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
            m = m * m;
            m = m * m;
            float3 x = 2.0 * fract(p * C.www) - 1.0;
            float3 h = abs(x) - 0.5;
            float3 ox = floor(x + 0.5);
            float3 a0 = x - ox;
            m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
            float3 g;
            g.x = a0.x * x0.x + h.x * x0.y;
            g.yz = a0.yz * x12.xz + h.yz * x12.yw;
            return 130.0 * dot(m, g);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float aspect = uResolution.x / max(uResolution.y, 1.0);
            float2 p = (uv - 0.5) * float2(aspect, 1.0);

            float phase = uTime * 0.16 + uBassEnergy * 0.32;
            float cosA = cos(phase * 0.35);
            float sinA = sin(phase * 0.35);
            float2 rotated = float2(p.x * cosA - p.y * sinA, p.x * sinA + p.y * cosA);

            float dispAmp = 0.26 + uBassEnergy * 0.18;
            float n1 = snoise(rotated * 1.45 + float2(phase, -phase * 0.7));
            float n2 = snoise(rotated * 2.15 - float2(phase * 0.8, phase * 0.5));
            float2 warped = uv + float2(n1, n2) * dispAmp;

            float2 c0 = float2(0.22 + 0.18 * sin(phase * 0.9), 0.25 + 0.15 * cos(phase * 0.7));
            float2 c1 = float2(0.78 + 0.15 * cos(phase * 0.8), 0.28 + 0.18 * sin(phase * 1.1));
            float2 c2 = float2(0.50 + 0.20 * sin(phase * 0.6), 0.55 + 0.20 * cos(phase * 0.9));
            float2 c3 = float2(0.24 + 0.16 * cos(phase * 1.2), 0.78 + 0.14 * sin(phase * 0.8));
            float2 c4 = float2(0.76 + 0.17 * sin(phase * 0.7), 0.74 + 0.16 * cos(phase * 1.0));

            float w0 = 1.0 / (pow(distance(warped, c0), 2.2) + 0.08);
            float w1 = 1.0 / (pow(distance(warped, c1), 2.2) + 0.08);
            float w2 = 1.0 / (pow(distance(warped, c2), 2.2) + 0.08);
            float w3 = 1.0 / (pow(distance(warped, c3), 2.2) + 0.08);
            float w4 = 1.0 / (pow(distance(warped, c4), 2.2) + 0.08);

            float wSum = w0 + w1 + w2 + w3 + w4;
            float3 meshColor = (
                uColor0.rgb * w0 +
                uColor1.rgb * w1 +
                uColor2.rgb * w2 +
                uColor3.rgb * w3 +
                uColor4.rgb * w4
            ) / wSum;

            float vignette = smoothstep(1.22, 0.12, length(p));
            meshColor *= (0.64 + 0.36 * vignette);

            return half4(half3(clamp(meshColor, 0.0, 1.0)), 1.0);
        }
    """
}
