package com.apple.auramusic.core.physics

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Crash-free 120Hz exponential decay fling behavior matching UIScrollViewDecelerationRateNormal (0.998).
 */
class IosUIScrollViewFlingBehavior(
    private val decelerationRate: Float = 0.998f
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f || !initialVelocity.isFinite()) return 0f
        var velocity = initialVelocity.coerceIn(-18000f, 18000f)
        var lastNanos = 0L
        while (abs(velocity) > 1.0f) {
            var stopFling = false
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtMs = ((frameNanos - lastNanos) / 1_000_000f).coerceIn(1f, 32f)
                    val decay = decelerationRate.toDouble().pow(dtMs.toDouble()).toFloat()
                    val nextVelocity = velocity * decay
                    val delta = ((velocity + nextVelocity) * 0.5f) * (dtMs / 1000f)
                    if (delta.isFinite()) {
                        val consumed = scrollBy(delta)
                        if (abs(delta - consumed) > 0.75f) {
                            stopFling = true
                        }
                    } else {
                        stopFling = true
                    }
                    velocity = nextVelocity
                }
                lastNanos = frameNanos
            }
            if (stopFling) break
        }
        return velocity
    }
}

@Composable
fun rememberIosFlingBehavior(): FlingBehavior = remember { IosUIScrollViewFlingBehavior() }

/**
 * Exact iOS UIScrollView logarithmic rubber-band resistance equation:
 * f(x) = (c * x * d) / (d + c * x)
 */
fun iosRubberBandTransform(overscrollDistance: Float, dimension: Float, c: Float = 0.55f): Float {
    if (!overscrollDistance.isFinite() || !dimension.isFinite()) return 0f
    val d = dimension.coerceAtLeast(1f)
    val absX = abs(overscrollDistance).coerceAtMost(d * 6f)
    val mapped = (c * absX * d) / (d + c * absX).coerceAtLeast(1f)
    return sign(overscrollDistance) * mapped
}

/**
 * Synchronous, race-free iOS Rubber-Band Overscroll Modifier.
 * Updates rawOverscroll synchronously on the main UI thread during drag (zero coroutine race conditions)
 * and animates back to 0f on release.
 */
fun Modifier.iosRubberBandOverscroll(
    orientation: Orientation = Orientation.Vertical,
    resistanceCoefficient: Float = 0.55f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    var rawOverscroll by remember { mutableFloatStateOf(0f) }
    var viewportDimension by remember { mutableFloatStateOf(1080f) }
    var springJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }

    val connection = remember(orientation, resistanceCoefficient) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = if (orientation == Orientation.Vertical) available.y else available.x
                if (!delta.isFinite()) return Offset.Zero
                val currentRaw = rawOverscroll
                if (abs(currentRaw) > 0.01f && sign(delta) != sign(currentRaw)) {
                    springJob?.cancel()
                    val nextRaw = if (abs(delta) >= abs(currentRaw)) 0f else currentRaw + delta
                    val consumed = currentRaw - nextRaw
                    rawOverscroll = nextRaw
                    return if (orientation == Orientation.Vertical) Offset(0f, -consumed) else Offset(-consumed, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val unconsumed = if (orientation == Orientation.Vertical) available.y else available.x
                if (unconsumed.isFinite() && abs(unconsumed) > 0.01f) {
                    springJob?.cancel()
                    rawOverscroll = (rawOverscroll + unconsumed).coerceIn(-viewportDimension * 3f, viewportDimension * 3f)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(rawOverscroll) > 0.5f) {
                    springJob?.cancel()
                    val startVal = rawOverscroll
                    val anim = Animatable(startVal)
                    val leftoverV = (if (orientation == Orientation.Vertical) available.y else available.x)
                        .let { if (it.isFinite()) it.coerceIn(-6000f, 6000f) else 0f }
                    springJob = scope.launch {
                        anim.animateTo(
                            targetValue = 0f,
                            initialVelocity = leftoverV,
                            animationSpec = IosSprings.SpatialFloat
                        ) {
                            rawOverscroll = value
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .onSizeChanged { size ->
            viewportDimension = (if (orientation == Orientation.Vertical) size.height else size.width)
                .toFloat()
                .coerceAtLeast(1f)
        }
        .nestedScroll(connection)
        .graphicsLayer {
            val mappedOffset = iosRubberBandTransform(
                overscrollDistance = rawOverscroll,
                dimension = viewportDimension,
                c = resistanceCoefficient
            )
            if (orientation == Orientation.Vertical) {
                translationY = mappedOffset
            } else {
                translationX = mappedOffset
            }
        }
}
