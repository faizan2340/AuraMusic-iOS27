package com.apple.auramusic.core.graphics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared coordinate space container for the Floating Mini-Player and Scroll-Reactive Tab Bar.
 * Renders a soft ambient surface-tension glow between adjacent glass capsules while keeping
 * all child text and icons 100% crisp.
 */
@Composable
fun GlassEffectContainer(
    rectA: Rect,
    rectB: Rect,
    cornerRadiusA: Dp = 26.dp,
    cornerRadiusB: Dp = 30.dp,
    frostFactor: Float,
    lightDirection: Offset,
    smoothK: Float = 0.085f,
    accentColor: Color = Color(0xFF5E5CE6),
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            if (rectA.width > 0f && rectB.width > 0f) {
                val bridgeCenter = Offset(
                    (rectA.center.x + rectB.center.x) * 0.5f,
                    (rectA.bottom + rectB.top) * 0.5f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f * (1f - frostFactor * 0.4f)),
                            Color.Transparent
                        ),
                        center = bridgeCenter,
                        radius = size.width * 0.42f
                    ),
                    center = bridgeCenter,
                    radius = size.width * 0.42f
                )
            }
        },
        content = content
    )
}
