package com.apple.auramusic.core.physics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign

/**
 * Spline-free exponential decay spec matching iOS UIScrollViewDecelerationRateNormal (0.998 per ms).
 */
class IosExponentialDecaySpec(
    private val decelerationRate: Float = 0.998f
) : FloatDecayAnimationSpec {

    // k = 1000 * ln(0.998) ~= -2.0020027 s^-1
    private val k: Float = (1000.0 * ln(decelerationRate.toDouble())).toFloat()
    override val absVelocityThreshold: Float = 0.5f

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val tSeconds = playTimeNanos / 1_000_000_000f
        val decayFactor = decelerationRate.toDouble().pow((tSeconds * 1000f).toDouble()).toFloat()
        return initialValue + (initialVelocity * (decayFactor - 1f)) / k
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val tSeconds = playTimeNanos / 1_000_000_000f
        val decayFactor = decelerationRate.toDouble().pow((tSeconds * 1000f).toDouble()).toFloat()
        return initialVelocity * decayFactor
    }

    override fun getDurationNanos(
        initialValue: Float,
        initialVelocity: Float
    ): Long {
        val speed = abs(initialVelocity)
        if (speed <= absVelocityThreshold) return 0L
        val seconds = ln(absVelocityThreshold / speed) / k
        return (seconds * 1_000_000_000f).toLong().coerceAtLeast(0L)
    }

    override fun getTargetValue(
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        if (abs(initialVelocity) <= absVelocityThreshold) return initialValue
        return initialValue - (initialVelocity / k)
    }
}

class IosUIScrollViewFlingBehavior(
    private val decaySpec: FloatDecayAnimationSpec = IosExponentialDecaySpec(0.998f)
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity
        ).animateDecay(decaySpec) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = this.velocity
            if (abs(delta - consumed) > 0.5f) {
                this.cancelAnimation()
            }
        }
        return velocityLeft
    }
}

@Composable
fun rememberIosFlingBehavior(): FlingBehavior = remember { IosUIScrollViewFlingBehavior() }

/**
 * Exact iOS UIScrollView logarithmic rubber-band resistance equation:
 * f(x) = (c * x * d) / (d + c * x)
 * where d is the viewport dimension and c = 0.55 is the resistance coefficient.
 */
fun iosRubberBandTransform(overscrollDistance: Float, dimension: Float, c: Float = 0.55f): Float {
    val d = dimension.coerceAtLeast(1f)
    val absX = abs(overscrollDistance)
    val mapped = (c * absX * d) / (d + c * absX)
    return sign(overscrollDistance) * mapped
}

/**
 * Inverse of f(x) so incremental drag deltas accumulate accurately in raw gesture space.
 */
private fun iosRubberBandInverse(rubberDisplacement: Float, dimension: Float, c: Float = 0.55f): Float {
    val d = dimension.coerceAtLeast(1f)
    val absY = abs(rubberDisplacement).coerceAtMost(d * 0.99f)
    val raw = (absY * d) / (c * (d - absY)).coerceAtLeast(0.001f)
    return sign(rubberDisplacement) * raw
}

fun Modifier.iosRubberBandOverscroll(
    orientation: Orientation = Orientation.Vertical,
    resistanceCoefficient: Float = 0.55f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val rawOverscroll = remember { Animatable(0f) }
    var viewportDimension = remember { 1080f }

    val connection = remember(orientation, resistanceCoefficient) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = if (orientation == Orientation.Vertical) available.y else available.x
                val currentRaw = rawOverscroll.value
                // If user drags back toward equilibrium while overscrolled, consume delta first
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
