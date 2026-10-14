package com.apple.auramusic.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.HardwareArtworkImage
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.GlassEffectContainer
import com.apple.auramusic.core.graphics.liquidGlassSurface
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.data.local.TrackEntity
import kotlinx.coroutines.isActive
import kotlin.math.sin

enum class IosMusicTab(val label: String) {
    LIBRARY("Library"),
    ALBUMS("Albums"),
    AUDIO_LAB("Audio Lab"),
    SEARCH("Search")
}

@Composable
fun ConcentricBottomFloatingDock(
    selectedTab: IosMusicTab,
    onSelectTab: (IosMusicTab) -> Unit,
    isScrollingDown: Boolean,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    progressFraction: Float,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    frostFactor: Float,
    lightDirection: Offset,
    modifier: Modifier = Modifier
) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = maxOf(navBottomPadding + 14.dp, imeBottomPadding + 10.dp)

    var miniPlayerBounds by remember { mutableStateOf(Rect(0f, 0f, 900f, 160f)) }
    var tabBarBounds by remember { mutableStateOf(Rect(0f, 180f, 900f, 340f)) }

    GlassEffectContainer(
        rectA = miniPlayerBounds,
        rectB = tabBarBounds,
        cornerRadiusA = 26.dp,
        cornerRadiusB = 30.dp,
        frostFactor = frostFactor,
        lightDirection = lightDirection,
        accentColor = accentColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = effectiveBottomPadding, start = 14.dp, end = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedTab == IosMusicTab.SEARCH) {
                BottomDockedLiquidSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection,
                    accentColor = accentColor
                )
            }

            if (currentTrack != null) {
                FloatingMiniPlayerCapsule(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    progressFraction = progressFraction,
                    accentColor = accentColor,
                    onTogglePlayPause = onTogglePlayPause,
                    onNextTrack = onNextTrack,
                    onExpandNowPlaying = onExpandNowPlaying,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        miniPlayerBounds = coords.boundsInParent()
                    }
                )
            }

            ScrollReactiveTabBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                isCompactCapsule = isScrollingDown,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                accentColor = accentColor,
                modifier = Modifier.onGloballyPositioned { coords ->
                    tabBarBounds = coords.boundsInParent()
                }
            )
        }
    }
}

@Composable
fun FloatingMiniPlayerCapsule(
    track: TrackEntity,
    isPlaying: Boolean,
    progressFraction: Float,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    frostFactor: Float,
    lightDirection: Offset,
    modifier: Modifier = Modifier
) {
    val squircle26 = remember { ContinuousRoundedCornerShape(26.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .liquidGlassSurface(
                cornerRadius = 26.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF181A24),
                accentColor = accentColor
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.24f), squircle26)
            .iosPressEffect(
                isExpandableGlassPill = true,
                onClick = onExpandNowPlaying
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                HardwareArtworkImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(ContinuousRoundedCornerShape(13.dp))
                )
                // Mini live equalizer badge on artwork corner
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(ContinuousRoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MiniEqualizerBars(isPlaying = isPlaying, color = accentColor)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = track.title,
                    style = IosTypography.Headline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicText(
                        text = track.artist,
                        style = IosTypography.Subheadline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(ContinuousRoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        BasicText(
                            text = if (track.bitDepth >= 24) "24-BIT" else "LOSSLESS",
                            style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ContinuousRoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .iosPressEffect(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                VibrantBlendPlayPauseIcon(isPlaying = isPlaying, sizeDp = 18f)
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .iosPressEffect(onClick = onNextTrack),
                contentAlignment = Alignment.Center
            ) {
                VibrantBlendNextIcon(sizeDp = 18f)
            }
        }

        // Subtle luminous playback progress hairline along bottom edge of capsule
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(2.5.dp)
                .clip(ContinuousRoundedCornerShape(1.25.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progressFraction.coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun MiniEqualizerBars(isPlaying: Boolean, color: Color, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    phase += dt * 11f
                }
                lastNanos = nanos
            }
        }
    }
    Canvas(modifier = modifier.size(12.dp)) {
        val barW = size.width * 0.22f
        val heights = if (isPlaying) {
            floatArrayOf(
                0.35f + 0.60f * kotlin.math.abs(sin(phase)),
                0.30f + 0.70f * kotlin.math.abs(sin(phase + 1.8f)),
                0.40f + 0.55f * kotlin.math.abs(sin(phase + 3.4f))
            )
        } else {
            floatArrayOf(0.28f, 0.28f, 0.28f)
        }
        for (i in 0..2) {
            val h = size.height * heights[i].coerceIn(0.2f, 1f)
            val x = i * (barW + size.width * 0.14f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW * 0.5f)
            )
        }
    }
}

@Composable
fun ScrollReactiveTabBar(
    selectedTab: IosMusicTab,
    onSelectTab: (IosMusicTab) -> Unit,
    isCompactCapsule: Boolean,
    frostFactor: Float,
    lightDirection: Offset,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val targetHorizontalPadding by animateDpAsState(
        targetValue = if (isCompactCapsule) 56.dp else 0.dp,
        animationSpec = IosSprings.FluidDp,
        label = "tabBarShrink"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isCompactCapsule) 0f else 1f,
        animationSpec = IosSprings.FluidFloat,
        label = "tabLabelAlpha"
    )
    val barHeight by animateDpAsState(
        targetValue = if (isCompactCapsule) 50.dp else 62.dp,
        animationSpec = IosSprings.FluidDp,
        label = "tabBarHeight"
    )

    val shape = remember { ContinuousRoundedCornerShape(30.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = targetHorizontalPadding)
            .height(barHeight)
            .liquidGlassSurface(
                cornerRadius = 30.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF141620),
                accentColor = accentColor
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.24f), shape)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosMusicTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val pillAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.22f else 0f,
                animationSpec = IosSprings.FluidFloat,
                label = "tabSelectionAlpha"
            )
            Column(
                modifier = Modifier
                    .clip(ContinuousRoundedCornerShape(22.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = pillAlpha + 0.12f)
                        else Color.Transparent
                    )
                    .iosPressEffect(
                        isExpandableGlassPill = true,
                        onClick = { onSelectTab(tab) }
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TabGlyphIcon(tab = tab, isSelected = isSelected)
                if (!isCompactCapsule) {
                    Spacer(modifier = Modifier.height(3.dp))
                    BasicText(
                        text = tab.label,
                        style = IosTypography.TabularTimestamp.copy(
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.62f)
                        ),
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomDockedLiquidSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    frostFactor: Float,
    lightDirection: Offset,
    accentColor: Color = Color(0xFF5E5CE6),
    modifier: Modifier = Modifier
) {
    val shape = remember { ContinuousRoundedCornerShape(22.dp) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .liquidGlassSurface(
                cornerRadius = 22.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF1A1C28),
                accentColor = accentColor
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.26f), shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "⌕",
            style = IosTypography.Title2.copy(color = Color.White.copy(alpha = 0.78f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    text = "Search Songs, Artists, Albums, Hi-Res...",
                    style = IosTypography.Body.copy(color = Color.White.copy(alpha = 0.48f))
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = IosTypography.Body,
                cursorBrush = SolidColor(Color(0xFFFF2D55)),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(ContinuousRoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .iosPressEffect(onClick = { onQueryChange("") })
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                BasicText(text = "Clear", style = IosTypography.LosslessBadge)
            }
        }
    }
}

@Composable
fun VibrantBlendPlayPauseIcon(isPlaying: Boolean, sizeDp: Float) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.26f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.15f, h * 0.12f),
                size = Size(barW, h * 0.76f),
                cornerRadius = CornerRadius(barW * 0.45f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.59f, h * 0.12f),
                size = Size(barW, h * 0.76f),
                cornerRadius = CornerRadius(barW * 0.45f)
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.12f)
                lineTo(w * 0.90f, h * 0.50f)
                lineTo(w * 0.22f, h * 0.88f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
fun VibrantBlendNextIcon(sizeDp: Float) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val firstTriangle = Path().apply {
            moveTo(w * 0.08f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.08f, h * 0.82f)
            close()
        }
        val secondTriangle = Path().apply {
            moveTo(w * 0.48f, h * 0.18f)
            lineTo(w * 0.90f, h * 0.50f)
            lineTo(w * 0.48f, h * 0.82f)
            close()
        }
        drawPath(firstTriangle, Color.White)
        drawPath(secondTriangle, Color.White.copy(alpha = 0.82f))
    }
}

@Composable
private fun TabGlyphIcon(tab: IosMusicTab, isSelected: Boolean) {
    val tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.62f)
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (tab) {
            IosMusicTab.LIBRARY -> {
                drawRoundRect(
                    color = tint,
                    style = stroke,
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.32f, size.height * 0.30f),
                    end = Offset(size.width * 0.32f, size.height * 0.70f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.30f),
                    end = Offset(size.width * 0.68f, size.height * 0.70f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            IosMusicTab.ALBUMS -> {
                drawCircle(tint, radius = size.minDimension * 0.45f, style = stroke)
                drawCircle(tint, radius = size.minDimension * 0.15f)
            }
            IosMusicTab.AUDIO_LAB -> {
                // 4 vertical equalizer/waveform bars
                val xs = floatArrayOf(0.15f, 0.38f, 0.62f, 0.85f)
                val hs = floatArrayOf(0.45f, 0.85f, 0.65f, 0.38f)
                for (i in 0..3) {
                    val x = size.width * xs[i]
                    val halfH = size.height * hs[i] * 0.5f
                    drawLine(
                        color = tint,
                        start = Offset(x, size.height * 0.5f - halfH),
                        end = Offset(x, size.height * 0.5f + halfH),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            IosMusicTab.SEARCH -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.36f,
                    center = Offset(size.width * 0.42f, size.height * 0.42f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.68f),
                    end = Offset(size.width * 0.94f, size.height * 0.94f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
