package com.apple.auramusic.core.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object HardwareBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(uri: String): Bitmap? = cache.get(uri)
    fun put(uri: String, bitmap: Bitmap) {
        cache.put(uri, bitmap)
    }
}

/**
 * Hardware-accelerated offline album artwork loader using ContentResolver.loadThumbnail
 * and Bitmap.Config.HARDWARE for zero-copy GPU rendering at 120Hz.
 */
@Composable
fun HardwareArtworkImage(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(artworkUri) { mutableStateOf(HardwareBitmapCache.get(artworkUri)) }

    LaunchedEffect(artworkUri) {
        if (bitmap == null && artworkUri.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(artworkUri)
                    context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                } catch (_: Exception) {
                    try {
                        val uri = Uri.parse(artworkUri)
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.HARDWARE
                            }
                            BitmapFactory.decodeStream(stream, null, opts)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (loaded != null) {
                HardwareBitmapCache.put(artworkUri, loaded)
                bitmap = loaded
            }
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
        Box(modifier = modifier.background(Color(0xFF232533)))
    }
}
