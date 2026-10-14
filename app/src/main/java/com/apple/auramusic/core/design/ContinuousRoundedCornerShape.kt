package com.apple.auramusic.core.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sign

/**
 * iOS 27 Continuous Curvature Squircle (Superellipse with exponent n = 4).
 * Ensures G2 curvature continuity along straight-to-corner transitions and
 * optical concentricity with hardware display bezels.
 */
class ContinuousRoundedCornerShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
    private val stepsPerCorner: Int = 28
) : Shape {

    constructor(cornerRadius: Dp) : this(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomEnd = cornerRadius,
        bottomStart = cornerRadius
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxR = min(size.width, size.height) / 2f
        val rTopLeft = with(density) {
            (if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd).toPx()
        }.coerceIn(0f, maxR)
        val rTopRight = with(density) {
            (if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart).toPx()
        }.coerceIn(0f, maxR)
        val rBottomRight = with(density) {
            (if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart).toPx()
        }.coerceIn(0f, maxR)
        val rBottomLeft = with(density) {
            (if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd).toPx()
        }.coerceIn(0f, maxR)

        val path = buildSquirclePath(
            width = size.width,
            height = size.height,
            rTL = rTopLeft,
            rTR = rTopRight,
            rBR = rBottomRight,
            rBL = rBottomLeft,
            steps = stepsPerCorner
        )
        return Outline.Generic(path)
    }

    private fun buildSquirclePath(
        width: Float,
        height: Float,
        rTL: Float,
        rTR: Float,
        rBR: Float,
        rBL: Float,
        steps: Int
    ): Path {
        val path = Path()
        val exponent = 0.5f // 2 / n where n = 4 -> (cos(t))^(2/4) = sqrt(cos(t))

        fun superellipsePoint(
            cx: Float,
            cy: Float,
            r: Float,
            theta: Float
        ): Pair<Float, Float> {
            val cosT = cos(theta)
            val sinT = sin(theta)
            val x = cx + r * sign(cosT) * kotlin.math.abs(cosT).pow(exponent)
            val y = cy + r * sign(sinT) * kotlin.math.abs(sinT).pow(exponent)
            return x to y
        }

        // Start at top edge after top-left corner
        path.moveTo(rTL, 0f)
        path.lineTo(width - rTR, 0f)

        // Top-Right Corner: theta from -PI/2 to 0
        if (rTR > 0f) {
            val cx = width - rTR
            val cy = rTR
            for (i in 1..steps) {
                val theta = (-Math.PI / 2.0 + (Math.PI / 2.0) * (i.toDouble() / steps)).toFloat()
                val (x, y) = superellipsePoint(cx, cy, rTR, theta)
                path.lineTo(x, y)
            }
        }

        path.lineTo(width, height - rBR)

        // Bottom-Right Corner: theta from 0 to PI/2
        if (rBR > 0f) {
            val cx = width - rBR
            val cy = height - rBR
            for (i in 1..steps) {
                val theta = ((Math.PI / 2.0) * (i.toDouble() / steps)).toFloat()
                val (x, y) = superellipsePoint(cx, cy, rBR, theta)
                path.lineTo(x, y)
            }
        }

        path.lineTo(rBL, height)

        // Bottom-Left Corner: theta from PI/2 to PI
        if (rBL > 0f) {
            val cx = rBL
            val cy = height - rBL
            for (i in 1..steps) {
                val theta = (Math.PI / 2.0 + (Math.PI / 2.0) * (i.toDouble() / steps)).toFloat()
                val (x, y) = superellipsePoint(cx, cy, rBL, theta)
                path.lineTo(x, y)
            }
        }

        path.lineTo(0f, rTL)

        // Top-Left Corner: theta from PI to 3*PI/2
        if (rTL > 0f) {
            val cx = rTL
            val cy = rTL
            for (i in 1..steps) {
                val theta = (Math.PI + (Math.PI / 2.0) * (i.toDouble() / steps)).toFloat()
                val (x, y) = superellipsePoint(cx, cy, rTL, theta)
                path.lineTo(x, y)
            }
        }

        path.close()
        return path
    }
}

fun SquircleShape(radius: Dp) = ContinuousRoundedCornerShape(radius)
