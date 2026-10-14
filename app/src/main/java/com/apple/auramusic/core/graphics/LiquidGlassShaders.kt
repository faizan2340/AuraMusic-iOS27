package com.apple.auramusic.core.graphics

import org.intellij.lang.annotations.Language

object LiquidGlassShaders {

    @Language("AGSL")
    const val LIQUID_GLASS_AGSL = """
        uniform shader uContent;
        uniform float2 uResolution;
        uniform float uCornerRadius;
        uniform float uGlassFrostFactor; // 0.0 = Ultra-Clear, 1.0 = Tinted/Frosted
        uniform float2 uLightDirection;  // Normalized gyro + pointer light vector
        uniform float uSpecularBoost;    // Touch feedback specular boost (0.0..0.35)
        uniform float4 uTintColor;       // Adaptive glass base tint

        // Exact n=4 Superellipse (Squircle) Signed Distance Field
        float sdSquircleN4(float2 p, float2 b, float r) {
            float2 q = abs(p) - (b - float2(r));
            float2 m = max(q, 0.0);
            // L4 norm distance for superellipse corner with exponent n = 4
            float l4 = pow(
                pow(m.x, 4.0) + pow(m.y, 4.0),
                0.25
            );
            return l4 + min(max(q.x, q.y), 0.0) - r;
        }

        // Analytic gradient of the n=4 squircle SDF for optical normal calculation
        float2 gradSquircleN4(float2 p, float2 b, float r) {
            const float eps = 1.0;
            float dx = sdSquircleN4(p + float2(eps, 0.0), b, r) - sdSquircleN4(p - float2(eps, 0.0), b, r);
            float dy = sdSquircleN4(p + float2(0.0, eps), b, r) - sdSquircleN4(p - float2(0.0, eps), b, r);
            float len = max(length(float2(dx, dy)), 0.0001);
            return float2(dx, dy) / len;
        }

        float luminance(float3 c) {
            return dot(c, float3(0.2126, 0.7152, 0.0722));
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(uCornerRadius, min(halfSize.x, halfSize.y));

            float dist = sdSquircleN4(p, halfSize, r);

            // Anti-aliased outer boundary mask
            float outerAlpha = 1.0 - smoothstep(-0.75, 0.75, dist);
            if (outerAlpha <= 0.001) {
                return uContent.eval(fragCoord);
            }

            float2 normal = gradSquircleN4(p, halfSize, r);

            // Inner 12% perimeter zone for lens edge bending
            float minDim = min(uResolution.x, uResolution.y);
            float edgeBandWidth = max(minDim * 0.12, 6.0);
            float edgeProgress = clamp(1.0 - (-dist / edgeBandWidth), 0.0, 1.0);

            // Simulate curved glass index of refraction n ~= 1.52 using spherical-squircle profile
            float iorRatio = 1.0 / 1.52;
            float incidentSin = edgeProgress * 0.88;
            float refractedFactor = incidentSin * (1.0 - iorRatio * sqrt(max(0.0, 1.0 - incidentSin * incidentSin)));

            // Ultra-Clear (0.0) has strong lens edge refraction; Frosted (1.0) has uniform smoother refraction
            float refractionStrength = mix(22.0, 8.5, uGlassFrostFactor);
            float2 refractedCoord = fragCoord + normal * (refractedFactor * refractionStrength);
            refractedCoord = clamp(refractedCoord, float2(1.0), uResolution - float2(1.0));

            // High-contrast background transition detection for +/- 0.8px chromatic dispersion
            float2 tangent = float2(-normal.y, normal.x);
            float3 samplePos = uContent.eval(clamp(refractedCoord + normal * 1.5, float2(0.0), uResolution)).rgb;
            float3 sampleNeg = uContent.eval(clamp(refractedCoord - normal * 1.5, float2(0.0), uResolution)).rgb;
            float contrast = clamp(abs(luminance(samplePos) - luminance(sampleNeg)) * 3.2, 0.25, 1.0);

            float dispersionPx = 0.8 * (0.35 + 0.65 * edgeProgress) * contrast * mix(1.15, 0.55, uGlassFrostFactor);
            float2 chromaOffset = tangent * dispersionPx;

            half rCh = uContent.eval(clamp(refractedCoord + chromaOffset, float2(0.0), uResolution)).r;
            half gCh = uContent.eval(refractedCoord).g;
            half bCh = uContent.eval(clamp(refractedCoord - chromaOffset, float2(0.0), uResolution)).b;
            float3 refractedColor = float3(rCh, gCh, bCh);

            // Boost luminance contrast when uGlassFrostFactor approaches 1.0 for text legibility
            float luma = luminance(refractedColor);
            float3 contrastBoosted = mix(
                refractedColor,
                clamp((refractedColor - 0.5) * 1.24 + 0.52, 0.0, 1.0),
                uGlassFrostFactor
            );

            // Adaptive tint: alpha = 0.08f at Ultra-Clear (0.0f) -> alpha = 0.38f at Frosted (1.0f)
            float tintAlpha = mix(0.08, 0.38, uGlassFrostFactor);
            float3 glassBody = mix(contrastBoosted, uTintColor.rgb, tintAlpha);

            // Internal subtle caustic glow along the inner lens bend
            float innerCaustic = pow(edgeProgress, 2.4) * mix(0.18, 0.08, uGlassFrostFactor);
            glassBody += float3(innerCaustic);

            // Gyroscopic + Pointer Dual-Stop Specular Rim Highlight (1.0px - 1.5px border)
            float rimMask = 1.0 - smoothstep(0.0, 1.5, abs(dist + 0.75));
            float2 lightDir = normalize(uLightDirection + float2(0.0001, 0.0001));
            float primaryStop = max(0.0, dot(normal, lightDir));
            float secondaryStop = max(0.0, dot(normal, -lightDir)) * 0.42;
            float dualStopSpecular = pow(primaryStop, 3.2) + pow(secondaryStop, 4.0);

            float rimIntensity = mix(0.95, 0.48, uGlassFrostFactor) + uSpecularBoost;
            float3 rimColor = float3(1.0, 0.99, 1.0) * rimMask * (0.22 + dualStopSpecular * rimIntensity);

            float3 finalRgb = clamp(glassBody + rimColor, 0.0, 1.0);
            float3 backgroundRgb = uContent.eval(fragCoord).rgb;

            return half4(half3(mix(backgroundRgb, finalRgb, outerAlpha)), 1.0);
        }
    """

    @Language("AGSL")
    const val METABALL_GLASS_CONTAINER_AGSL = """
        uniform shader uContent;
        uniform float2 uResolution;
        uniform float4 uRectA; // (centerX, centerY, halfW, halfH) - e.g. Mini-Player Capsule
        uniform float4 uRectB; // (centerX, centerY, halfW, halfH) - e.g. Floating Tab Bar
        uniform float2 uRadii; // (radiusA, radiusB)
        uniform float uSmoothK; // Surface-tension smin k parameter (e.g. 0.085)
        uniform float uGlassFrostFactor;
        uniform float2 uLightDirection;

        // Exact Smooth Minimum (smin) SDF function per specification:
        // smin(a, b, k) = -log(exp(-k * a) + exp(-k * b)) / k
        float sminExp(float a, float b, float k) {
            float res = exp(clamp(-k * a, -40.0, 40.0)) + exp(clamp(-k * b, -40.0, 40.0));
            return -log(max(res, 1e-12)) / k;
        }

        float sdSquircleN4(float2 p, float2 b, float r) {
            float2 q = abs(p) - (b - float2(r));
            float2 m = max(q, 0.0);
            float l4 = pow(pow(m.x, 4.0) + pow(m.y, 4.0), 0.25);
            return l4 + min(max(q.x, q.y), 0.0) - r;
        }

        float evalMetaballField(float2 coord) {
            float dA = sdSquircleN4(coord - uRectA.xy, uRectA.zw, uRadii.x);
            float dB = sdSquircleN4(coord - uRectB.xy, uRectB.zw, uRadii.y);
            return sminExp(dA, dB, uSmoothK);
        }

        float2 gradMetaballField(float2 coord) {
            const float eps = 1.0;
            float dx = evalMetaballField(coord + float2(eps, 0.0)) - evalMetaballField(coord - float2(eps, 0.0));
            float dy = evalMetaballField(coord + float2(0.0, eps)) - evalMetaballField(coord - float2(0.0, eps));
            return normalize(float2(dx, dy) + float2(1e-5));
        }

        half4 main(float2 fragCoord) {
            float dist = evalMetaballField(fragCoord);
            float mask = 1.0 - smoothstep(-0.75, 0.75, dist);
            if (mask <= 0.001) {
                return uContent.eval(fragCoord);
            }

            float2 normal = gradMetaballField(fragCoord);
            float edgeProgress = clamp(1.0 - (-dist / 14.0), 0.0, 1.0);
            float2 refractedCoord = clamp(
                fragCoord + normal * (edgeProgress * edgeProgress * mix(18.0, 7.5, uGlassFrostFactor)),
                float2(1.0),
                uResolution - float2(1.0)
            );

            float2 tangent = float2(-normal.y, normal.x);
            float disp = 0.8 * edgeProgress;
            half rCh = uContent.eval(clamp(refractedCoord + tangent * disp, float2(0.0), uResolution)).r;
            half gCh = uContent.eval(refractedCoord).g;
            half bCh = uContent.eval(clamp(refractedCoord - tangent * disp, float2(0.0), uResolution)).b;

            float3 baseRgb = float3(rCh, gCh, bCh);
            float tintAlpha = mix(0.08, 0.38, uGlassFrostFactor);
            float3 tinted = mix(baseRgb, float3(0.14, 0.15, 0.20), tintAlpha);

            float rimMask = 1.0 - smoothstep(0.0, 1.5, abs(dist + 0.75));
            float2 lightDir = normalize(uLightDirection + float2(0.001));
            float spec = pow(max(0.0, dot(normal, lightDir)), 3.0) +
                         0.4 * pow(max(0.0, dot(normal, -lightDir)), 4.0);
            float3 rim = float3(1.0) * rimMask * (0.24 + spec * mix(0.92, 0.45, uGlassFrostFactor));

            float3 bg = uContent.eval(fragCoord).rgb;
            return half4(half3(mix(bg, clamp(tinted + rim, 0.0, 1.0), mask)), 1.0);
        }
    """

    @Language("AGSL")
    const val LIVING_MESH_GRADIENT_AGSL = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uBassEnergy; // 0.0 .. 1.0 real-time low-frequency amplitude
        uniform float4 uColor0;
        uniform float4 uColor1;
        uniform float4 uColor2;
        uniform float4 uColor3;
        uniform float4 uColor4;

        float3 mod289(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        float2 mod289_2(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        float3 permute(float3 x) { return mod289(((x * 34.0) + 1.0) * x); }

        // 2D Simplex Noise
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

            // Slow rotation + bass-modulated organic simplex displacement
            float phase = uTime * 0.16 + uBassEnergy * 0.28;
            float cosA = cos(phase * 0.35);
            float sinA = sin(phase * 0.35);
            float2 rotated = float2(p.x * cosA - p.y * sinA, p.x * sinA + p.y * cosA);

            float dispAmp = 0.26 + uBassEnergy * 0.16;
            float n1 = snoise(rotated * 1.45 + float2(phase, -phase * 0.7));
            float n2 = snoise(rotated * 2.15 - float2(phase * 0.8, phase * 0.5));
            float2 warped = uv + float2(n1, n2) * dispAmp;

            // 5 drifting attractor centers for the 5 extracted artwork swatches
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

            // Subtle deep vignette for iOS 27 Now Playing contrast
            float vignette = smoothstep(1.15, 0.15, length(p));
            meshColor *= (0.72 + 0.28 * vignette);

            return half4(half3(clamp(meshColor, 0.0, 1.0)), 1.0);
        }
    """
}
