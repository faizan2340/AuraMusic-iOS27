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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
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
import coil3.compose.AsyncImage
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.GlassEffectContainer
import com.apple.auramusic.core.graphics.liquidGlassSurface
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.data.local.TrackEntity
import kotlinx.coroutines.isActive

enum class IosMusicTab(val label: String) {
    LIBRARY("Library"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    SEARCH("Search")
}

@Composable
fun ConcentricBottomFloatingDock(
    selectedTab: IosMusicTab,
    onSelectTab: (IosMusicTab) -> Unit,
    isScrollingDown: Boolean,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
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
    val effectiveBottomPadding = maxOf(navBottomPadding + 16.dp, imeBottomPadding + 12.dp)

    var miniPlayerBounds by remember { mutableStateOf(Rect(0f, 0f, 900f, 160f)) }
    var tabBarBounds by remember { mutableStateOf(Rect(0f, 180f, 900f, 340f)) }

    GlassEffectContainer(
        rectA = miniPlayerBounds,
        rectB = tabBarBounds,
        cornerRadiusA = 26.dp,
        cornerRadiusB = 30.dp,
        frostFactor = frostFactor,
        lightDirection = lightDirection,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = effectiveBottomPadding, start = 16.dp, end = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bottom-Docked Search Bar (visible in Search Tab, synced 120fps with IME insets)
            if (selectedTab == IosMusicTab.SEARCH) {
                BottomDockedLiquidSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection
                )
            }

            // Floating Mini-Player Capsule docked directly above the tab bar
            if (currentTrack != null) {
                FloatingMiniPlayerCapsule(
                    track = currentTrack,
                    isPlaying = isPlaying,
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

            // Scroll-Reactive Concentric Liquid Glass Tab Bar
            ScrollReactiveTabBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                isCompactCapsule = isScrollingDown,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
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
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    frostFactor: Float,
    lightDirection: Offset,
    modifier: Modifier = Modifier
) {
    val squircle26 = remember { ContinuousRoundedCornerShape(26.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlassSurface(
                cornerRadius = 26.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection
            )
            .clip(squircle26)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.75.dp, Color.White.copy(alpha = 0.22f), squircle26)
            .iosPressEffect(
                isExpandableGlassPill = true,
                onClick = onExpandNowPlaying
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Squircle Album Art Thumbnail
        AsyncImage(
            model = track.artworkUri,
            contentDescription = track.album,
            modifier = Modifier
                .size(44.dp)
                .clip(ContinuousRoundedCornerShape(12.dp))
                .background(Color(0xFF2A2B36))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Frame-paced 120Hz Marquee Track Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            MarqueeTrackTitle(text = track.title)
            BasicText(
                text = track.artist,
                style = IosTypography.Subheadline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play / Pause Button with BlendMode.Plus vibrant icon rendering
        Box(
            modifier = Modifier
                .size(40.dp)
                .iosPressEffect(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            VibrantBlendPlayPauseIcon(isPlaying = isPlaying, sizeDp = 20f)
        }

        // Next Track Button with BlendMode.Overlay / Plus
        Box(
            modifier = Modifier
                .size(40.dp)
                .iosPressEffect(onClick = onNextTrack),
            contentAlignment = Alignment.Center
        ) {
            VibrantBlendNextIcon(sizeDp = 20f)
        }
    }
}

@Composable
private fun MarqueeTrackTitle(text: String) {
    var scrollOffsetPx by remember(text) { mutableFloatStateOf(0f) }
    val needsMarquee = text.length > 24

    LaunchedEffect(text, needsMarquee) {
        if (!needsMarquee) {
            scrollOffsetPx = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    scrollOffsetPx = (scrollOffsetPx - dt * 36f) % 360f
                }
                lastNanos = nanos
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().clip(ContinuousRoundedCornerShape(2.dp))) {
        BasicText(
            text = if (needsMarquee) "$text     •     $text" else text,
            style = IosTypography.Headline,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                translationX = if (needsMarquee) scrollOffsetPx else 0f
            }
        )
    }
}

@Composable
fun ScrollReactiveTabBar(
    selectedTab: IosMusicTab,
    onSelectTab: (IosMusicTab) -> Unit,
    isCompactCapsule: Boolean,
    frostFactor: Float,
    lightDirection: Offset,
    modifier: Modifier = Modifier
) {
    val targetHorizontalPadding by animateDpAsState(
        targetValue = if (isCompactCapsule) 72.dp else 0.dp,
        animationSpec = IosSprings.FluidDp,
        label = "tabBarShrink"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isCompactCapsule) 0f else 1f,
        animationSpec = IosSprings.FluidFloat,
        label = "tabLabelAlpha"
    )
    val barHeight by animateDpAsState(
        targetValue = if (isCompactCapsule) 52.dp else 62.dp,
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
                lightDirection = lightDirection
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.75.dp, Color.White.copy(alpha = 0.24f), shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosMusicTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val pillAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.20f else 0f,
                animationSpec = IosSprings.FluidFloat,
                label = "tabSelectionAlpha"
            )
            Column(
                modifier = Modifier
                    .clip(ContinuousRoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = pillAlpha))
                    .iosPressEffect(
                        isExpandableGlassPill = true,
                        onClick = { onSelectTab(tab) }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TabGlyphIcon(tab = tab, isSelected = isSelected)
                if (!isCompactCapsule) {
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = tab.label,
                        style = IosTypography.TabularTimestamp.copy(
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f)
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
    modifier: Modifier = Modifier
) {
    val shape = remember { ContinuousRoundedCornerShape(20.dp) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .liquidGlassSurface(
                cornerRadius = 20.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.75.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "⌕",
            style = IosTypography.Title2.copy(color = Color.White.copy(alpha = 0.65f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    text = "Artists, Songs, Lyrics, and Lossless Audio",
                    style = IosTypography.Body.copy(color = Color.White.copy(alpha = 0.45f))
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
    }
}

@Composable
fun VibrantBlendPlayPauseIcon(isPlaying: Boolean, sizeDp: Float) {
    Canvas(
        modifier = Modifier
            .size(sizeDp.dp)
            .drawWithContent { drawContent() }
    ) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.26f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(w * 0.14f, h * 0.12f),
                size = androidx.compose.ui.geometry.Size(barW, h * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.45f),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(w * 0.60f, h * 0.12f),
                size = androidx.compose.ui.geometry.Size(barW, h * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.45f),
                blendMode = BlendMode.Plus
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.20f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.50f)
                lineTo(w * 0.20f, h * 0.88f)
                close()
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.92f),
                blendMode = BlendMode.Plus
            )
        }
    }
}

@Composable
fun VibrantBlendNextIcon(sizeDp: Float) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val firstTriangle = Path().apply {
            moveTo(w * 0.10f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.10f, h * 0.82f)
            close()
        }
        val secondTriangle = Path().apply {
            moveTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.92f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.82f)
            close()
        }
        drawPath(firstTriangle, Color.White.copy(alpha = 0.88f), blendMode = BlendMode.Plus)
        drawPath(secondTriangle, Color.White.copy(alpha = 0.88f), blendMode = BlendMode.Overlay)
    }
}

@Composable
private fun TabGlyphIcon(tab: IosMusicTab, isSelected: Boolean) {
    val tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f)
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (tab) {
            IosMusicTab.LIBRARY -> {
                drawRoundRect(tint, style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
            IosMusicTab.PLAYLISTS -> {
                drawLine(tint, Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), strokeWidth = 2.dp.toPx())
                drawLine(tint, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.55f), strokeWidth = 2.dp.toPx())
                drawLine(tint, Offset(0f, size.height * 0.85f), Offset(size.width * 0.7f, size.height * 0.85f), strokeWidth = 2.dp.toPx())
            }
            IosMusicTab.ALBUMS -> {
                drawCircle(tint, radius = size.minDimension * 0.45f, style = stroke)
                drawCircle(tint, radius = size.minDimension * 0.14f)
            }
            IosMusicTab.SEARCH -> {
                drawCircle(tint, radius = size.minDimension * 0.36f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = stroke)
                drawLine(tint, Offset(size.width * 0.68f, size.height * 0.68f), Offset(size.width * 0.94f, size.height * 0.94f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
