package com.apple.auramusic.core.graphics

import android.graphics.RuntimeShader
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape

/**
 * Renders the iOS 27 Liquid Glass optical shader on the background canvas of the surface
 * while keeping all foreground text, icons, and album artwork 100% razor-sharp.
 */
fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 26.dp,
    frostFactor: Float,
    lightDirection: Offset,
    specularBoost: Float = 0f,
    tintColor: Color = Color(0xFF1A1C26),
    accentColor: Color = Color(0xFF5E5CE6)
): Modifier = composed {
    val density = LocalDensity.current
    val runtimeShader = remember { RuntimeShader(LiquidGlassShaders.LIQUID_GLASS_SURFACE_AGSL) }
    val shaderBrush = remember(runtimeShader) { ShaderBrush(runtimeShader) }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val squircle = remember(cornerRadius) { ContinuousRoundedCornerShape(cornerRadius) }

    this
        .clip(squircle)
        .drawBehind {
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
            runtimeShader.setFloatUniform(
                "uAccentColor",
                accentColor.red,
                accentColor.green,
                accentColor.blue,
                accentColor.alpha
            )

            drawRect(brush = shaderBrush)
        }
}
