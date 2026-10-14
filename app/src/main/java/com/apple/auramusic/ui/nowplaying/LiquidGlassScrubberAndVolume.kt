package com.apple.auramusic.ui.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.core.physics.iosRubberBandTransform
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * iOS 27 Liquid Glass Scrubber & Volume Track:
 * - 6dp idle height -> 14dp active height with luminous glass bloom.
 * - Relative/direct horizontal drag anywhere on track without exact thumb grab.
 * - Fires PRIMITIVE_LOW_TICK (0.3f) on 10% intervals and PRIMITIVE_CLICK on 0% / 100% bounds.
 * - Physically stretches along its axis when pulled past 0% or 100%.
 */
@Composable
fun LiquidGlassExpandableBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onDragFinished: (() -> Unit)? = null,
    hapticsController: IosHapticsController,
    isVolumeBar: Boolean = false,
    leftTimestamp: String? = null,
    rightTimestamp: String? = null,
    audioBadgeText: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val overscrollStretchPx = remember { Animatable(0f) }

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 6.dp,
        animationSpec = IosSprings.FluidDp,
        label = "glassBarHeight"
    )
    val luminousGlowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.42f else 0.0f,
        animationSpec = IosSprings.FluidFloat,
        label = "glassBarGlow"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(isVolumeBar) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        hapticsController.resetSliderDetents(isVolumeBar)

                        var rawProgress = (down.position.x / trackWidthPx)
                        onProgressChange(rawProgress.coerceIn(0f, 1f))
                        hapticsController.onSliderProgressChanged(rawProgress, isVolumeBar)

                        horizontalDrag(down.id) { change ->
                            val deltaX = change.positionChange().x
                            change.consume()
                            rawProgress += deltaX / trackWidthPx

                            if (rawProgress < 0f) {
                                val excessPx = rawProgress * trackWidthPx
                                scope.launch { overscrollStretchPx.snapTo(excessPx) }
                            } else if (rawProgress > 1f) {
                                val excessPx = (rawProgress - 1f) * trackWidthPx
                                scope.launch { overscrollStretchPx.snapTo(excessPx) }
                            } else if (overscrollStretchPx.value != 0f) {
                                scope.launch { overscrollStretchPx.snapTo(0f) }
                            }

                            val clamped = rawProgress.coerceIn(0f, 1f)
                            onProgressChange(clamped)
                            hapticsController.onSliderProgressChanged(clamped, isVolumeBar)
                        }

                        isDragging = false
                        scope.launch {
                            overscrollStretchPx.animateTo(0f, IosSprings.SpatialFloat)
                        }
                        onDragFinished?.invoke()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val shape = ContinuousRoundedCornerShape((trackHeight.value * 0.5f).dp)
            val rubberStretch = iosRubberBandTransform(
                overscrollDistance = overscrollStretchPx.value,
                dimension = trackWidthPx,
                c = 0.55f
            )
            val axialScaleX = 1f + (abs(rubberStretch) / trackWidthPx) * 0.14f
            val transformOrigin = if (rubberStretch < 0f) {
                TransformOrigin(1f, 0.5f)
            } else {
                TransformOrigin(0f, 0.5f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .graphicsLayer {
                        this.transformOrigin = transformOrigin
                        this.scaleX = axialScaleX
                        this.translationX = rubberStretch * 0.22f
                    }
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.28f), shape)
                    .drawWithContent {
                        drawContent()
                        if (luminousGlowAlpha > 0.01f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = luminousGlowAlpha * 0.5f),
                                        Color.White.copy(alpha = luminousGlowAlpha),
                                        Color.White.copy(alpha = luminousGlowAlpha * 0.3f)
                                    )
                                ),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0.001f, 1f))
                        .fillMaxHeight()
                        .background(
                            Color.White.copy(alpha = if (isDragging) 0.96f else 0.78f)
                        )
                )
            }
        }

        if (leftTimestamp != null && rightTimestamp != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tabular figures ("tnum") enforced via IosTypography.TabularTimestamp
                BasicText(text = leftTimestamp, style = IosTypography.TabularTimestamp)
                if (!audioBadgeText.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(ContinuousRoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        BasicText(text = audioBadgeText, style = IosTypography.LosslessBadge)
                    }
                }
                BasicText(text = rightTimestamp, style = IosTypography.TabularTimestamp)
            }
        }
    }
}
