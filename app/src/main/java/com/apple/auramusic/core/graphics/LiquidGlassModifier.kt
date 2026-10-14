package com.apple.auramusic.core.graphics

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape

/**
 * Applies the iOS 27 Liquid Glass optical pipeline to a surface container:
 * 1. Multi-pass hardware Gaussian blur interpolated from 8dp (Ultra-Clear 0.0f) to 36dp (Frosted 1.0f).
 * 2. AGSL squircle SDF lens refraction (n ~= 1.52 along the outer 12% perimeter).
 * 3. +/- 0.8px chromatic dispersion perpendicular to the SDF normal.
 * 4. Gyroscopic + pointer 1.5px dual-stop specular rim lighting.
 */
fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 26.dp,
    frostFactor: Float,
    lightDirection: Offset,
    specularBoost: Float = 0f,
    tintColor: Color = Color(0xFF1C1E26)
): Modifier = composed {
    val density = LocalDensity.current
    val runtimeShader = remember { RuntimeShader(LiquidGlassShaders.LIQUID_GLASS_AGSL) }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    // Interpolate Gaussian blur radius from 8dp (0.0f Ultra-Clear) to 36dp (1.0f Tinted/Frosted)
    val blurRadiusDp = (8f + (36f - 8f) * frostFactor.coerceIn(0f, 1f)).dp
    val blurRadiusPx = with(density) { blurRadiusDp.toPx() }.coerceAtLeast(1f)

    this.graphicsLayer {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)

        runtimeShader.setFloatUniform("uResolution", w, h)
        runtimeShader.setFloatUniform("uCornerRadius", cornerRadiusPx)
        runtimeShader.setFloatUniform("uGlassFrostFactor", frostFactor.coerceIn(0f, 1f))
        runtimeShader.setFloatUniform("uLightDirection", lightDirection.x, lightDirection.y)
        runtimeShader.setFloatUniform("uSpecularBoost", specularBoost)
        runtimeShader.setFloatUniform(
            "uTintColor",
            tintColor.red,
            tintColor.green,
            tintColor.blue,
            tintColor.alpha
        )

        val blurEffect = RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP
        )
        val refractionEffect = RenderEffect.createRuntimeShaderEffect(
            runtimeShader,
            "uContent"
        )
        // Chain Gaussian Blur -> Optical Squircle Refraction & Chromatic Dispersion
        renderEffect = RenderEffect.createChainEffect(refractionEffect, blurEffect)
            .asComposeRenderEffect()
        shape = ContinuousRoundedCornerShape(cornerRadius)
        clip = false
    }
}
