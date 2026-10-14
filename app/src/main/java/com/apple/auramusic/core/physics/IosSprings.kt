package com.apple.auramusic.core.physics

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

object IosSprings {
    /**
     * Standard UI spatial transitions (sheet expansion, navigation pops, lyric line advances).
     * dampingRatio = 0.85f, stiffness = 380f
     */
    fun <T> spatialTransition(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = 0.85f,
            stiffness = 380f,
            visibilityThreshold = visibilityThreshold
        )

    /**
     * Interactive fluid morphs (capsule expansion, scrubber height morph, tab bar collapse).
     * dampingRatio = 0.72f, stiffness = 500f
     */
    fun <T> interactiveFluidMorph(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = 0.72f,
            stiffness = 500f,
            visibilityThreshold = visibilityThreshold
        )

    /**
     * Critically damped touch feedback spring for Modifier.iosPressEffect.
     * dampingRatio = 0.82f, stiffness = 600f
     */
    val TouchPress: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 600f
    )

    val SpatialFloat: SpringSpec<Float> = spatialTransition(0.0005f)
    val SpatialOffset: SpringSpec<Offset> = spatialTransition(Offset(0.5f, 0.5f))
    val FluidFloat: SpringSpec<Float> = interactiveFluidMorph(0.0005f)
    val FluidDp: SpringSpec<Dp> = interactiveFluidMorph()
}
