package com.apple.auramusic.ui.nowplaying

import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.HardwareArtworkImage
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

enum class NowPlayingPane {
    ARTWORK,
    LYRICS,
    SIGNAL_AND_QUEUE
}

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
            42.dp
        }
    }
}

@Composable
fun NowPlayingSheet(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    track: TrackEntity,
    queue: List<TrackEntity>,
    palette: ArtworkMeshPalette,
    bassEnergy: Float,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    currentPositionMs: Long,
    volumeFraction: Float,
    lyrics: List<SyncedLyricLine>,
    frostFactor: Float,
    lightDirection: Offset,
    hapticsController: IosHapticsController,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayFromQueue: (TrackEntity) -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenFrostSpectrumSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val bezelRadius = rememberHardwareBezelCornerRadius()
    var activePane by remember { mutableStateOf(NowPlayingPane.ARTWORK) }

    val currentCornerRadius = (26f + (bezelRadius.value - 26f) * expansionFraction.value).dp
    val sheetShape = ContinuousRoundedCornerShape(currentCornerRadius)

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.84f,
        animationSpec = IosSprings.SpatialFloat,
        label = "breathingArtworkScale"
    )
    val artworkShadowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.78f else 0.18f,
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
                        val shouldExpand = if (abs(velocityY) > 650f) {
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
                        val deltaFraction = -dragAmount / size.height.coerceAtLeast(1).toFloat()
                        scope.launch {
                            expansionFraction.snapTo(
                                (expansionFraction.value + deltaFraction).coerceIn(0f, 1f)
                            )
                        }
                    }
                )
            }
    ) {
        // Dynamic 5-Color Living Mesh Gradient Background driven by real album artwork palette
        LivingMeshGradientBackground(
            palette = palette,
            bassEnergy = bassEnergy,
            isPlaying = isPlaying
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                    bottom = 28.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Dismiss Grabber Pill + Lossless Signal Header
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(ContinuousRoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.42f))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Stage Area: Artwork + Waveform OR Synced Lyrics OR Signal Path & Queue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (activePane) {
                    NowPlayingPane.ARTWORK -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val artSquircle = remember { ContinuousRoundedCornerShape(26.dp) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        scaleX = artworkScale
                                        scaleY = artworkScale
                                    }
                                    .drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    palette.color0.copy(alpha = artworkShadowAlpha),
                                                    palette.color2.copy(alpha = artworkShadowAlpha * 0.65f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(size.width * 0.5f, size.height * 0.56f),
                                                radius = size.minDimension * 0.72f
                                            )
                                        )
                                    }
                                    .clip(artSquircle)
                                    .border(0.75.dp, Color.White.copy(alpha = 0.28f), artSquircle)
                            ) {
                                HardwareArtworkImage(
                                    artworkUri = track.artworkUri,
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Live 28-Bar Sub-Bass & Harmonic Waveform Visualizer
                            LiveHarmonicWaveformBar(
                                isPlaying = isPlaying,
                                bassEnergy = bassEnergy,
                                accentColor = palette.color2,
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .height(28.dp)
                            )
                        }
                    }

                    NowPlayingPane.LYRICS -> {
                        TimeSyncedLyricsView(
                            lyrics = lyrics,
                            currentPositionMs = currentPositionMs,
                            onSeekToMs = onSeekToMs,
                            hapticsController = hapticsController,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    NowPlayingPane.SIGNAL_AND_QUEUE -> {
                        SignalPathAndQueuePane(
                            currentTrack = track,
                            queue = queue,
                            accentColor = palette.color2,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection,
                            onSelectTrack = onPlayFromQueue,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Metadata Header + Favorite Heart + Crisp Glass Spectrum Pill
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
                        style = IosTypography.Body.copy(color = Color.White.copy(alpha = 0.76f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Favorite Heart Glass Circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .liquidGlassSurface(
                            cornerRadius = 19.dp,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection,
                            accentColor = if (isFavorite) Color(0xFFFF2D55) else palette.color0
                        )
                        .border(
                            0.75.dp,
                            Color.White.copy(alpha = 0.28f),
                            ContinuousRoundedCornerShape(19.dp)
                        )
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = onToggleFavorite
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = if (isFavorite) "♥" else "♡",
                        style = IosTypography.Headline.copy(
                            color = if (isFavorite) Color(0xFFFF2D55) else Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Crystal-Clear Glass Spectrum Pill (no foreground blur!)
                Box(
                    modifier = Modifier
                        .liquidGlassSurface(
                            cornerRadius = 18.dp,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection,
                            accentColor = palette.color2
                        )
                        .border(
                            0.75.dp,
                            Color.White.copy(alpha = 0.30f),
                            ContinuousRoundedCornerShape(18.dp)
                        )
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = onOpenFrostSpectrumSheet
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    BasicText(
                        text = "Glass • ${"%.1f".format(frostFactor)}",
                        style = IosTypography.LosslessBadge.copy(color = Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Liquid Glass Playback Scrubber
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

            Spacer(modifier = Modifier.height(18.dp))

            // 5-Button Transport Controls: Shuffle | Previous | 76dp Glass Play/Pause | Next | Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Pill
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(ContinuousRoundedCornerShape(21.dp))
                        .background(
                            if (isShuffleEnabled) Color.White.copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .iosPressEffect(onClick = onToggleShuffle),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "⇄",
                        style = IosTypography.Title2.copy(
                            color = if (isShuffleEnabled) Color.White else Color.White.copy(alpha = 0.55f)
                        )
                    )
                }

                // Previous Track
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .iosPressEffect(onClick = onPreviousTrack)
                        .graphicsLayer { rotationZ = 180f },
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendNextIcon(sizeDp = 28f)
                }

                // 76dp Hero Liquid Glass Play/Pause Button (crisp unblurred icon!)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .liquidGlassSurface(
                            cornerRadius = 38.dp,
                            frostFactor = frostFactor,
                            lightDirection = lightDirection,
                            tintColor = Color(0xFF222534),
                            accentColor = palette.color0
                        )
                        .border(
                            1.2.dp,
                            Color.White.copy(alpha = 0.38f),
                            ContinuousRoundedCornerShape(38.dp)
                        )
                        .iosPressEffect(
                            isExpandableGlassPill = true,
                            onClick = onTogglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendPlayPauseIcon(isPlaying = isPlaying, sizeDp = 32f)
                }

                // Next Track
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .iosPressEffect(onClick = onNextTrack),
                    contentAlignment = Alignment.Center
                ) {
                    VibrantBlendNextIcon(sizeDp = 28f)
                }

                // Repeat Mode Pill (0 = Off, 1 = All, 2 = One)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(ContinuousRoundedCornerShape(21.dp))
                        .background(
                            if (repeatMode > 0) Color.White.copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .iosPressEffect(onClick = onCycleRepeat),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = if (repeatMode == 2) "🔂" else "↻",
                        style = IosTypography.Headline.copy(
                            color = if (repeatMode > 0) Color.White else Color.White.copy(alpha = 0.55f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Liquid Glass Volume Bar
            LiquidGlassExpandableBar(
                progress = volumeFraction,
                onProgressChange = onVolumeChange,
                hapticsController = hapticsController,
                isVolumeBar = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom 3-Mode Studio Switcher Row: Artwork | Synced Lyrics | Signal & Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioModePill(
                        label = "Artwork",
                        selected = activePane == NowPlayingPane.ARTWORK,
                        onClick = { activePane = NowPlayingPane.ARTWORK }
                    )
                    StudioModePill(
                        label = "Lyrics",
                        selected = activePane == NowPlayingPane.LYRICS,
                        onClick = { activePane = NowPlayingPane.LYRICS }
                    )
                    StudioModePill(
                        label = "Signal & Queue",
                        selected = activePane == NowPlayingPane.SIGNAL_AND_QUEUE,
                        onClick = { activePane = NowPlayingPane.SIGNAL_AND_QUEUE }
                    )
                }

                BasicText(
                    text = "${track.sampleRateHz / 1000f}kHz • ${track.bitDepth}-bit",
                    style = IosTypography.TabularTimestamp
                )
            }
        }
    }
}

@Composable
private fun StudioModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pillShape = ContinuousRoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (selected) Color.White.copy(alpha = 0.26f)
                else Color.White.copy(alpha = 0.10f)
            )
            .border(
                0.75.dp,
                if (selected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.18f),
                pillShape
            )
            .iosPressEffect(
                isExpandableGlassPill = true,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        BasicText(
            text = label,
            style = IosTypography.Subheadline.copy(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
            )
        )
    }
}

@Composable
private fun LiveHarmonicWaveformBar(
    isPlaying: Boolean,
    bassEnergy: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    phase += dt * (4.5f + bassEnergy * 6f)
                }
                lastNanos = nanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.6f)
        val barWidth = spacing * 0.65f
        val centerY = size.height * 0.5f

        for (i in 0 until barCount) {
            val norm = i.toFloat() / barCount.toFloat()
            val envelope = sin(norm * Math.PI).toFloat()
            val wave = if (isPlaying) {
                0.22f + 0.78f * envelope * abs(sin(phase + i * 0.42f)) * (0.65f + bassEnergy * 0.55f)
            } else {
                0.15f * envelope + 0.08f
            }
            val barH = (size.height * wave.coerceIn(0.12f, 1f))
            val x = i * (barWidth + spacing * 0.9f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f + 0.55f * wave.coerceIn(0f, 1f)),
                topLeft = Offset(x, centerY - barH * 0.5f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth * 0.5f)
            )
        }
    }
}

@Composable
private fun SignalPathAndQueuePane(
    currentTrack: TrackEntity,
    queue: List<TrackEntity>,
    accentColor: Color,
    frostFactor: Float,
    lightDirection: Offset,
    onSelectTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = ContinuousRoundedCornerShape(22.dp)
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        cornerRadius = 22.dp,
                        frostFactor = frostFactor,
                        lightDirection = lightDirection,
                        accentColor = accentColor
                    )
                    .border(0.75.dp, Color.White.copy(alpha = 0.24f), cardShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(
                    text = "AUDIOPHILE SIGNAL PATH • BIT-PERFECT FLOAT",
                    style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                )
                BasicText(
                    text = "1. Source: ${currentTrack.mimeType.uppercase()} (${currentTrack.sampleRateHz}Hz / ${currentTrack.bitDepth}-bit)",
                    style = IosTypography.Subheadline.copy(color = Color.White)
                )
                BasicText(
                    text = "2. DSP Engine: 32-bit IEEE 754 PCM Float + 135Hz Butterworth Bass Tee",
                    style = IosTypography.Subheadline
                )
                BasicText(
                    text = "3. Output: Android Direct AudioTrack Float Sink (Zero Resampling)",
                    style = IosTypography.Subheadline
                )
            }
        }

        item {
            BasicText(
                text = "Up Next in Queue (${queue.size} Tracks)",
                style = IosTypography.Headline,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }

        itemsIndexed(queue.take(30), key = { idx, item -> "${idx}_${item.id}" }) { index, item ->
            val isCurrent = item.id == currentTrack.id
            val rowShape = ContinuousRoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(
                        if (isCurrent) Color.White.copy(alpha = 0.18f)
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        0.5.dp,
                        if (isCurrent) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.12f),
                        rowShape
                    )
                    .iosPressEffect(onClick = { onSelectTrack(item) })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "%02d".format(index + 1),
                    style = IosTypography.TabularTimestamp,
                    modifier = Modifier.width(26.dp)
                )
                HardwareArtworkImage(
                    artworkUri = item.artworkUri,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ContinuousRoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = item.title,
                        style = IosTypography.Headline.copy(
                            color = if (isCurrent) Color(0xFF64D2FF) else Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        text = item.artist,
                        style = IosTypography.Subheadline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicText(
                    text = formatTabularTimestamp(item.durationMs),
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
