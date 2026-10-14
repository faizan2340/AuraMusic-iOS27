package com.apple.auramusic.core.graphics

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class ArtworkMeshPalette(
    val color0: Color = Color(0xFF3A1C71),
    val color1: Color = Color(0xFFD76D77),
    val color2: Color = Color(0xFFFFAF7B),
    val color3: Color = Color(0xFF1F1C2C),
    val color4: Color = Color(0xFF4A2574)
)

suspend fun extractArtworkMeshPalette(bitmap: Bitmap?): ArtworkMeshPalette =
    withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext ArtworkMeshPalette()
        val palette = Palette.from(bitmap).maximumColorCount(24).generate()
        val c0 = palette.getVibrantColor(0xFF3A1C71.toInt())
        val c1 = palette.getDarkVibrantColor(0xFF1F1C2C.toInt())
        val c2 = palette.getLightVibrantColor(0xFFD76D77.toInt())
        val c3 = palette.getMutedColor(0xFF4A2574.toInt())
        val c4 = palette.getDominantColor(0xFF281B45.toInt())
        ArtworkMeshPalette(
            color0 = Color(c0),
            color1 = Color(c1),
            color2 = Color(c2),
            color3 = Color(c3),
            color4 = Color(c4)
        )
    }

@Composable
fun LivingMeshGradientBackground(
    palette: ArtworkMeshPalette,
    bassEnergy: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val shader = remember { RuntimeShader(LiquidGlassShaders.LIVING_MESH_GRADIENT_AGSL) }
    val shaderBrush = remember(shader) { ShaderBrush(shader) }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    // Locked 120Hz frame-clock driver via withFrameNanos
    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos) / 1_000_000_000f
                    val speedMultiplier = if (isPlaying) (1.0f + bassEnergy * 1.45f) else 0.18f
                    elapsedSeconds += dt * speedMultiplier
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uTime", elapsedSeconds)
        shader.setFloatUniform("uBassEnergy", bassEnergy.coerceIn(0f, 1f))
        shader.setFloatUniform("uColor0", palette.color0.red, palette.color0.green, palette.color0.blue, 1f)
        shader.setFloatUniform("uColor1", palette.color1.red, palette.color1.green, palette.color1.blue, 1f)
        shader.setFloatUniform("uColor2", palette.color2.red, palette.color2.green, palette.color2.blue, 1f)
        shader.setFloatUniform("uColor3", palette.color3.red, palette.color3.green, palette.color3.blue, 1f)
        shader.setFloatUniform("uColor4", palette.color4.red, palette.color4.green, palette.color4.blue, 1f)

        drawRect(brush = shaderBrush)
    }
}
