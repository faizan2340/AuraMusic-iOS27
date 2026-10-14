package com.apple.auramusic.core.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object ArtworkBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    fun get(uri: String): Bitmap? = if (uri.isBlank()) null else cache.get(uri)
    fun put(uri: String, bitmap: Bitmap) {
        if (uri.isNotBlank()) cache.put(uri, bitmap)
    }

    suspend fun loadArtworkBitmap(context: Context, artworkUri: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (artworkUri.isBlank()) return@withContext null
            get(artworkUri)?.let { return@withContext it }
            val loaded = try {
                val uri = Uri.parse(artworkUri)
                val thumb = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                // Ensure software ARGB_8888 copy so Palette.from(bitmap) never crashes on HARDWARE config
                if (thumb.config == Bitmap.Config.HARDWARE) {
                    thumb.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    thumb
                }
            } catch (_: Exception) {
                try {
                    val uri = Uri.parse(artworkUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (loaded != null) {
                put(artworkUri, loaded)
            }
            loaded
        }
}

@Composable
fun HardwareArtworkImage(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(artworkUri) { mutableStateOf(ArtworkBitmapCache.get(artworkUri)) }

    LaunchedEffect(artworkUri) {
        if (bitmap == null && artworkUri.isNotBlank()) {
            bitmap = ArtworkBitmapCache.loadArtworkBitmap(context, artworkUri)
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // Intricate procedural Apple Music gradient + vinyl/note glyph fallback
        val hash = abs((contentDescription ?: artworkUri).hashCode())
        val palettes = listOf(
            listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            listOf(Color(0xFF1A2980), Color(0xFF26D0CE), Color(0xFF5E5CE6)),
            listOf(Color(0xFF42275A), Color(0xFF734B6D), Color(0xFFFF2D55)),
            listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF64D2FF))
        )
        val colors = palettes[hash % palettes.size]

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = colors,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                // Subtle concentric vinyl grooves
                val r = size.minDimension * 0.34f
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = r,
                    style = Stroke(width = 1.2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = r * 0.62f,
                    style = Stroke(width = 1.2f)
                )
                // Crisp musical note glyph
                val cx = size.width * 0.5f
                val cy = size.height * 0.52f
                val noteR = size.minDimension * 0.08f
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = noteR,
                    center = Offset(cx - noteR * 0.7f, cy + noteR * 0.7f)
                )
                val stem = Path().apply {
                    moveTo(cx + noteR * 0.2f, cy + noteR * 0.7f)
                    lineTo(cx + noteR * 0.2f, cy - noteR * 1.6f)
                    lineTo(cx + noteR * 1.5f, cy - noteR * 1.1f)
                    lineTo(cx + noteR * 1.5f, cy - noteR * 0.4f)
                    lineTo(cx + noteR * 0.2f, cy - noteR * 0.9f)
                }
                drawPath(stem, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
