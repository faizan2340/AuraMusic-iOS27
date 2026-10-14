package com.apple.auramusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.liquidGlassSurface
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.ui.nowplaying.LiquidGlassExpandableBar
import kotlin.math.roundToInt

@Composable
fun LiquidGlassSpectrumSheet(
    frostFactor: Float,
    onFrostFactorChange: (Float) -> Unit,
    crossfadeSeconds: Int,
    onCrossfadeSecondsChange: (Int) -> Unit,
    lightDirection: Offset,
    hapticsController: IosHapticsController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = ContinuousRoundedCornerShape(32.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .liquidGlassSurface(
                cornerRadius = 32.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection
            )
            .clip(shape)
            .background(Color(0xFF14161F).copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(text = "iOS 27 Liquid Glass Spectrum", style = IosTypography.Title2)
            Box(
                modifier = Modifier
                    .clip(ContinuousRoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .iosPressEffect(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicText(text = "Done", style = IosTypography.Subheadline.copy(color = Color.White))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val blurDp = (8f + 28f * frostFactor).roundToInt()
        val tintPct = ((0.08f + 0.30f * frostFactor) * 100f).roundToInt()
        BasicText(
            text = "uGlassFrostFactor: ${"%.2f".format(frostFactor)}  •  Blur ${blurDp}dp  •  Tint ${tintPct}%",
            style = IosTypography.TabularTimestamp
        )

        Spacer(modifier = Modifier.height(10.dp))

        LiquidGlassExpandableBar(
            progress = frostFactor,
            onProgressChange = onFrostFactorChange,
            hapticsController = hapticsController,
            leftTimestamp = "0.0 Ultra-Clear",
            rightTimestamp = "1.0 Tinted / Frosted",
            audioBadgeText = "IOR n=1.52"
        )

        Spacer(modifier = Modifier.height(22.dp))

        BasicText(
            text = "Equal-Power Audio Crossfade: ${crossfadeSeconds}s",
            style = IosTypography.Subheadline.copy(color = Color.White)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LiquidGlassExpandableBar(
            progress = ((crossfadeSeconds - 1) / 11f).coerceIn(0f, 1f),
            onProgressChange = { frac ->
                val secs = (1 + (frac * 11f).roundToInt()).coerceIn(1, 12)
                onCrossfadeSecondsChange(secs)
            },
            hapticsController = hapticsController,
            leftTimestamp = "1s",
            rightTimestamp = "12s",
            audioBadgeText = "cos/sin Curve"
        )
    }
}
