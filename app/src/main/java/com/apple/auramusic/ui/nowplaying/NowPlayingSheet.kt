package com.apple.auramusic.ui.nowplaying

import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.HardwareArtworkImage
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.ArtworkMeshPalette
import com.apple.auramusic.core.graphics.LivingMeshGradientBackground
import com.apple.auramusic.core.graphics.liquidGlassSurface
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.data.local.TrackEntity
import com.apple.auramusic.data.lyrics.SyncedLyricLine
import com.apple.auramusic.ui.navigation.VibrantBlendNextIcon
import com.apple.auramusic.ui.navigation.VibrantBlendPlayPauseIcon
import kotlinx.coroutines.launch

/**
 * Queries the hardware display bezel corner radius via Android 12+ WindowInsets.getRoundedCorner.
 */
@Composable
fun rememberHardwareBezelCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val insets = view.rootWindowInsets
        val topLeft = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        if (topLeft != null && topLeft.radius > 0) {
            with(density) { topLeft.radius.toDp() }
        } else {
            42.dp // Concentric iOS hardware bezel fallback
        }
    }
}

@Composable
fun NowPlayingSheet(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    track: TrackEntity,
    palette: ArtworkMeshPalette,
    bassEnergy: Float,
    isPlaying: Boolean,
    currentPositionMs: Long,
    volumeFraction: Float,
    lyrics: List<SyncedLyricLine>,
    frostFactor: Float,
    lightDirection: Offset,
    hapticsController: IosHapticsController,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenFrostSpectrumSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val bezelRadius = rememberHardwareBezelCornerRadius()
    var showSyncedLyrics by remember { mutableStateOf(false) }

    // Interpolate sheet corner radius from 26dp (Mini-Player capsule) to hardware bezel radius
    val currentCornerRadius = (26f + (bezelRadius.value - 26f) * expansionFraction.value).dp
    val sheetShape = ContinuousRoundedCornerShape(currentCornerRadius)

    // Breathing Artwork Container: 1.0f playing with deep multi-colored shadow; 0.82f paused
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.82f,
        animationSpec = IosSprings.SpatialFloat,
        label = "breathingArtworkScale"
    )
    val artworkShadowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.72f else 0.14f,
        animationSpec = IosSprings.SpatialFloat,
        label = "breathingArtworkShadow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = expansionFraction.value.coerceIn(0f, 1f)
                translationY = (1f - expansionFraction.value) * size.height * 0.88f
            }
            .clip(sheetShape)
            .pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        val normalizedVelocity = -velocityY / size.height.coerceAtLeast(1).toFloat()
                        val shouldExpand = if (kotlin.math.abs(velocityY) > 650f) {
                            velocityY < 0f
                        } else {
                            expansionFraction.value > 0.52f
                        }
                        scope.launch {
                            expansionFraction.animateTo(
                                targetValue = if (shouldExpand) 1f else 0f,
                                initialVelocity = normalizedVelocity,
                                animationSpec = IosSprings.SpatialFloat
                            )
                            hapticsController.onGlassSnapConfirmation()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val deltaFraction = -dragAmount / size.height.coerceAtLeast(1).toFloat().toFloat()
                        scope.launch {
                            expansionFraction.snapTo(
                                (expansionFraction.value + deltaFraction).coerceIn(0f, 1f)
                            )
                        }
                    }
                )
            }
    ) {
        // Full-Screen Living Mesh Gradient AGSL Background modulated by bass amplitude
        LivingMeshGradientBackground(
            palette = palette,
            bassEnergy = bassEnergy,
            isPlaying = isPlaying
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    bottom = 32.dp,
                    start = 28.dp,
                    end = 28.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Grabber Pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(ContinuousRoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.38f))
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (showSyncedLyrics && lyrics.isNotEmpty()) {
                TimeSyncedLyricsView(
                    lyrics = lyrics,
                    currentPositionMs = currentPositionMs,
                    onSeekToMs = onSeekToMs,
                    hapticsController = hapticsController,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Breathing Squircle Artwork Container (aspectRatio = 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val artSquircle = remember { ContinuousRoundedCornerShape(24.dp) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            }
                            .drawBehind {
                                // Deep, diffused multi-colored drop shadow sampled from artwork
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            palette.color0.copy(alpha = artworkShadowAlpha),
                                            palette.color2.copy(alpha = artworkShadowAlpha * 0.65f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.58f),
                                        radius = size.minDimension * 0.68f
                                    )
                                )
                            }
                            .clip(artSquircle)
                            .border(0.75.dp, Color.White.copy(alpha = 0.24f), artSquircle)
                    ) {
                        HardwareArtworkImage(
                            artworkUri = track.artworkUri,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Metadata Header + Favorite / Glass Spectrum Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = track.title,
                        style = IosTypography.Title1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(
                        text = "${track.artist} — ${track.album}",
                        style = IosTypography.Body.copy(color = Color.White.copy(alpha = 0.72f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Quick-toggle pill for iOS 27 Liquid Glass Spectrum Slider
                Box(
                    modifier = Modifier
                        .liquidGlassSurface(
                            cornerRadius = 18.dp,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection
                        )
                        .clip(ContinuousRoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.28f), ContinuousRoundedCornerShape(18.dp))
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = onOpenFrostSpectrumSheet
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicText(text = "Glass", style = IosTypography.LosslessBadge)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Liquid Glass Playback Scrubber (6dp idle -> 14dp active + tabular timestamps)
            val progressFraction = (currentPositionMs.toFloat() / track.durationMs.coerceAtLeast(1L).toFloat())
                .coerceIn(0f, 1f)
            LiquidGlassExpandableBar(
                progress = progressFraction,
                onProgressChange = onSeekToFraction,
                hapticsController = hapticsController,
                isVolumeBar = false,
                leftTimestamp = formatTabularTimestamp(currentPositionMs),
                rightTimestamp = "-${formatTabularTimestamp((track.durationMs - currentPositionMs).coerceAtLeast(0L))}",
                audioBadgeText = track.audioBadge
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .iosPressEffect(onClick = onPreviousTrack)
                        .graphicsLayer { rotationZ = 180f },
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendNextIcon(sizeDp = 30f)
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .liquidGlassSurface(
                            cornerRadius = 38.dp,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection
                        )
                        .clip(ContinuousRoundedCornerShape(38.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), ContinuousRoundedCornerShape(38.dp))
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = onTogglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendPlayPauseIcon(isPlaying = isPlaying, sizeDp = 34f)
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .iosPressEffect(onClick = onNextTrack),
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendNextIcon(sizeDp = 30f)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Liquid Glass Volume Bar (6dp idle -> 14dp active + axial stretch past 100%)
            LiquidGlassExpandableBar(
                progress = volumeFraction,
                onProgressChange = onVolumeChange,
                hapticsController = hapticsController,
                isVolumeBar = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Action Row: Time-Synced Sing-Along Lyrics Toggle & AirPlay/Output Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pillShape = ContinuousRoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            if (showSyncedLyrics) Color.White.copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.10f)
                        )
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), pillShape)
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = { showSyncedLyrics = !showSyncedLyrics }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        text = if (showSyncedLyrics) "Lyrics • On" else "Lyrics",
                        style = IosTypography.Subheadline.copy(color = Color.White)
                    )
                }

                BasicText(
                    text = "${track.sampleRateHz / 1000f}kHz • ${track.bitDepth}-bit PCM Float",
                    style = IosTypography.TabularTimestamp
                )
            }
        }
    }
}

fun formatTabularTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
