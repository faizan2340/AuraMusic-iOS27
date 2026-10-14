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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class IosUIScrollViewFlingBehavior(
    private val decelerationRate: Float = 0.998f
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocity = initialVelocity
        var lastNanos = 0L
        while (abs(velocity) > 0.5f) {
            var hitBounds = false
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtMs = ((frameNanos - lastNanos) / 1_000_000f).coerceIn(1f, 32f)
                    val decay = decelerationRate.toDouble().pow(dtMs.toDouble()).toFloat()
                    val nextVelocity = velocity * decay
                    val delta = ((velocity + nextVelocity) * 0.5f) * (dtMs / 1000f)
                    val consumed = scrollBy(delta)
                    velocity = nextVelocity
                    if (abs(delta - consumed) > 0.5f) {
                        hitBounds = true
                    }
                }
                lastNanos = frameNanos
            }
            if (hitBounds) break
        }
        return velocity
    }
}

@Composable
fun rememberIosFlingBehavior(): FlingBehavior = remember { IosUIScrollViewFlingBehavior() }

fun iosRubberBandTransform(overscrollDistance: Float, dimension: Float, c: Float = 0.55f): Float {
    val d = dimension.coerceAtLeast(1f)
    val absX = abs(overscrollDistance)
    val mapped = (c * absX * d) / (d + c * absX)
    return sign(overscrollDistance) * mapped
}

fun Modifier.iosRubberBandOverscroll(
    orientation: Orientation = Orientation.Vertical,
    resistanceCoefficient: Float = 0.55f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val rawOverscroll = remember { Animatable(0f) }
    var viewportDimension by remember { mutableFloatStateOf(1080f) }

    val connection = remember(orientation, resistanceCoefficient) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = if (orientation == Orientation.Vertical) available.y else available.x
                val currentRaw = rawOverscroll.value
                if (currentRaw != 0f && sign(delta) != sign(currentRaw)) {
                    val nextRaw = if (abs(delta) >= abs(currentRaw)) 0f else currentRaw + delta
                    val consumed = currentRaw - nextRaw
                    scope.launch { rawOverscroll.snapTo(nextRaw) }
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
                if (abs(unconsumed) > 0.01f) {
                    scope.launch {
                        rawOverscroll.snapTo(rawOverscroll.value + unconsumed)
                    }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val leftoverV = if (orientation == Orientation.Vertical) available.y else available.x
                if (rawOverscroll.value != 0f || abs(leftoverV) > 20f) {
                    rawOverscroll.animateTo(
                        targetValue = 0f,
                        initialVelocity = leftoverV,
                        animationSpec = IosSprings.SpatialFloat
                    )
                    return available
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
                overscrollDistance = rawOverscroll.value,
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
