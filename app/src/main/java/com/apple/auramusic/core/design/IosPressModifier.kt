package com.apple.auramusic.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.apple.auramusic.core.physics.IosSprings
import kotlinx.coroutines.launch

@Stable
class IosPressState {
    val scale = Animatable(1.0f)
    val specularBoost = Animatable(0.0f)
    var lastTouchOffset: Offset = Offset.Zero
}

@Composable
fun rememberIosPressState(): IosPressState = remember { IosPressState() }

/**
 * Zero-Ripple iOS 27 Touch Feedback Modifier.
 * - Scales down to 0.95f on standard buttons/rows or expands to 1.04f on expandable glass pills.
 * - Boosts internal specular luminance using a critically damped spring (dampingRatio = 0.82f, stiffness = 600f).
 */
fun Modifier.iosPressEffect(
    enabled: Boolean = true,
    isExpandableGlassPill: Boolean = false,
    pressedScale: Float = if (isExpandableGlassPill) 1.04f else 0.95f,
    pressState: IosPressState? = null,
    onPressChanged: ((Boolean, Float) -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val state = pressState ?: rememberIosPressState()

    this
        .pointerInput(enabled, isExpandableGlassPill, pressedScale, onClick) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                state.lastTouchOffset = down.position
                onPressChanged?.invoke(true, 0.32f)

                scope.launch {
                    state.scale.animateTo(
                        targetValue = pressedScale,
                        animationSpec = IosSprings.TouchPress
                    )
                }
                scope.launch {
                    state.specularBoost.animateTo(
                        targetValue = 0.32f,
                        animationSpec = IosSprings.TouchPress
                    )
                }

                val up = waitForUpOrCancellation()
                onPressChanged?.invoke(false, 0.0f)

                scope.launch {
                    state.scale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = IosSprings.TouchPress
                    )
                }
                scope.launch {
                    state.specularBoost.animateTo(
                        targetValue = 0.0f,
                        animationSpec = IosSprings.TouchPress
                    )
                }

                if (up != null) {
                    onClick?.invoke()
                }
            }
        }
        .graphicsLayer {
            scaleX = state.scale.value
            scaleY = state.scale.value
        }
        .drawWithContent {
            drawContent()
            val boost = state.specularBoost.value
            if (boost > 0.005f) {
                val center = if (state.lastTouchOffset == Offset.Zero) {
                    Offset(size.width / 2f, size.height / 2f)
                } else {
                    state.lastTouchOffset
                }
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = boost),
                            Color.White.copy(alpha = boost * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.maxDimension * 0.85f
                    ),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
