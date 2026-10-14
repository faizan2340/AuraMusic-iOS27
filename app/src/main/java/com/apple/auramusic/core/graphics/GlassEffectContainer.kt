package com.apple.auramusic.core.graphics

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared AGSL coordinate space container implementing Metaball Liquid Morphing:
 * smin(a, b, k) = -log(exp(-k * a) + exp(-k * b)) / k
 */
@Composable
fun GlassEffectContainer(
    rectA: Rect,
    rectB: Rect,
    cornerRadiusA: Dp,
    cornerRadiusB: Dp,
    frostFactor: Float,
    lightDirection: Offset,
    smoothK: Float = 0.085f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val shader = remember { RuntimeShader(LiquidGlassShaders.METABALL_GLASS_CONTAINER_AGSL) }
    val rAPx = with(density) { cornerRadiusA.toPx() }
    val rBPx = with(density) { cornerRadiusB.toPx() }
    val blurPx = with(density) { (8f + 28f * frostFactor.coerceIn(0f, 1f)).dp.toPx() }

    Box(
        modifier = modifier.graphicsLayer {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)

            shader.setFloatUniform("uResolution", w, h)
            shader.setFloatUniform(
                "uRectA",
                rectA.center.x,
                rectA.center.y,
                rectA.width * 0.5f,
                rectA.height * 0.5f
            )
            shader.setFloatUniform(
                "uRectB",
                rectB.center.x,
                rectB.center.y,
                rectB.width * 0.5f,
                rectB.height * 0.5f
            )
            shader.setFloatUniform("uRadii", rAPx, rBPx)
            shader.setFloatUniform("uSmoothK", smoothK)
            shader.setFloatUniform("uGlassFrostFactor", frostFactor.coerceIn(0f, 1f))
            shader.setFloatUniform("uLightDirection", lightDirection.x, lightDirection.y)

            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            val metaball = RenderEffect.createRuntimeShaderEffect(shader, "uContent")
            renderEffect = RenderEffect.createChainEffect(metaball, blur).asComposeRenderEffect()
        },
        content = content
    )
}
