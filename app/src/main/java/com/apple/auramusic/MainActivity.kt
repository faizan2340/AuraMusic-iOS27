package com.apple.auramusic

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.HardwareArtworkImage
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.liquidGlassSurface
import com.apple.auramusic.core.graphics.rememberGyroLightDirection
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.core.physics.iosRubberBandOverscroll
import com.apple.auramusic.core.physics.rememberIosFlingBehavior
import com.apple.auramusic.data.local.TrackEntity
import com.apple.auramusic.di.AppContainer
import com.apple.auramusic.ui.AlbumGroup
import com.apple.auramusic.ui.LibraryFilter
import com.apple.auramusic.ui.MusicViewModel
import com.apple.auramusic.ui.navigation.ConcentricBottomFloatingDock
import com.apple.auramusic.ui.navigation.IosMusicTab
import com.apple.auramusic.ui.navigation.MiniEqualizerBars
import com.apple.auramusic.ui.nowplaying.LiquidGlassExpandableBar
import com.apple.auramusic.ui.nowplaying.NowPlayingSheet
import com.apple.auramusic.ui.nowplaying.formatTabularTimestamp
import com.apple.auramusic.ui.settings.LiquidGlassSpectrumSheet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class AuraMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels {
        MusicViewModel.provideFactory((application as AuraMusicApp).container)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshLocalLibrary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lock120HzProMotionDisplayRate()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            viewModel.refreshLocalLibrary()
        }

        setContent {
            AuraMusicRootScreen(viewModel = viewModel)
        }
    }

    private fun lock120HzProMotionDisplayRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null
        val modes = display?.supportedModes ?: return
        val highestRateMode = modes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = highestRateMode.modeId
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun AuraMusicRootScreen(viewModel: MusicViewModel) {
    val scope = rememberCoroutineScope()
    val tracks by viewModel.libraryTracks.collectAsState()
    val albums by viewModel.albumsList.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val hideCallRecordings by viewModel.hideCallRecordings.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val activeQueue by viewModel.activeQueue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val eqBands by viewModel.eqBands.collectAsState()
    val selectedEqPreset by viewModel.selectedEqPreset.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val volumeFraction by viewModel.volumeFraction.collectAsState()
    val frostFactor by viewModel.frostFactor.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncedLyrics by viewModel.syncedLyrics.collectAsState()
    val artworkPalette by viewModel.artworkPalette.collectAsState()
    val bassEnergy by viewModel.bassEnergy.collectAsState()
    val lightDirection by rememberGyroLightDirection(viewModel.gyroSensorManager)

    var selectedTab by remember { mutableStateOf(IosMusicTab.LIBRARY) }
    var showSpectrumSheet by remember { mutableStateOf(false) }

    val sheetExpansion = remember { Animatable(0f) }

    val listState = rememberLazyListState()
    var isScrollingDown by remember { mutableStateOf(false) }

    // Crash-free scroll direction observer using snapshotFlow (never mutates state inside derivedStateOf!)
    LaunchedEffect(listState) {
        var prevIdx = listState.firstVisibleItemIndex
        var prevOff = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (idx, off) ->
            val delta = if (idx != prevIdx) {
                (idx - prevIdx) * 200
            } else {
                off - prevOff
            }
            if (abs(delta) > 18) {
                isScrollingDown = delta > 0 && idx > 0
                prevIdx = idx
                prevOff = off
            }
        }
    }

    val progressFraction = currentTrack?.let {
        (currentPositionMs.toFloat() / it.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    } ?: 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val stackScale = 1f - 0.08f * sheetExpansion.value
        val stackCorner = (24f * sheetExpansion.value).dp
        val stackDimAlpha = 0.48f * sheetExpansion.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = stackScale
                    scaleY = stackScale
                    translationY = sheetExpansion.value * 24.dp.toPx()
                }
                .clip(ContinuousRoundedCornerShape(stackCorner))
                .background(Color(0xFF07080E))
                .drawBehind {
                    // Ambient dynamic artwork glow in upper backdrop so glass cards refract color
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                artworkPalette.color0.copy(alpha = 0.28f),
                                artworkPalette.color2.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.80f, size.height * 0.12f),
                            radius = size.width * 0.85f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                artworkPalette.color1.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.55f),
                            radius = size.width * 0.70f
                        )
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = rememberIosFlingBehavior(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                    bottom = 240.dp,
                    start = 18.dp,
                    end = 18.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .iosRubberBandOverscroll()
            ) {
                // Top Header Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText(text = selectedTab.label, style = IosTypography.LargeTitle)
                            val losslessCount = tracks.count { it.audioBadge.contains("Lossless", true) }
                            BasicText(
                                text = "${tracks.size} Tracks • $losslessCount Lossless • 120Hz ProMotion",
                                style = IosTypography.TabularTimestamp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .liquidGlassSurface(
                                    cornerRadius = 18.dp,
                                    frostFactor = frostFactor,
                                    lightDirection = lightDirection,
                                    accentColor = artworkPalette.color0
                                )
                                .border(
                                    0.75.dp,
                                    Color.White.copy(alpha = 0.28f),
                                    ContinuousRoundedCornerShape(18.dp)
                                )
                                .iosPressEffect(
                                    isExpandableGlassPill = true,
                                    onClick = { showSpectrumSheet = true }
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            BasicText(
                                text = "Liquid Glass • ${"%.1f".format(frostFactor)}",
                                style = IosTypography.LosslessBadge.copy(color = Color.White)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    IosMusicTab.LIBRARY, IosMusicTab.SEARCH -> {
                        // Hero Play & Shuffle Glass Cards
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HeroGlassActionCard(
                                    title = "Play Library",
                                    subtitle = "Bit-Perfect Float",
                                    glyph = "▶",
                                    accentColor = Color(0xFFFF2D55),
                                    frostFactor = frostFactor,
                                    lightDirection = lightDirection,
                                    onClick = {
                                        if (tracks.isNotEmpty()) {
                                            viewModel.playTrack(tracks.first(), tracks)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                HeroGlassActionCard(
                                    title = "Shuffle All",
                                    subtitle = "${tracks.size} Songs",
                                    glyph = "⇄",
                                    accentColor = Color(0xFF5E5CE6),
                                    frostFactor = frostFactor,
                                    lightDirection = lightDirection,
                                    onClick = { viewModel.shufflePlayQueue(tracks) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Filter Chips + Call Recording Filter Toggle
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(LibraryFilter.entries) { filter ->
                                    val selected = filter == selectedFilter
                                    val chipShape = ContinuousRoundedCornerShape(16.dp)
                                    Box(
                                        modifier = Modifier
                                            .clip(chipShape)
                                            .background(
                                                if (selected) artworkPalette.color0.copy(alpha = 0.55f)
                                                else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                0.75.dp,
                                                if (selected) Color.White.copy(alpha = 0.45f)
                                                else Color.White.copy(alpha = 0.16f),
                                                chipShape
                                            )
                                            .iosPressEffect(onClick = { viewModel.setLibraryFilter(filter) })
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        BasicText(
                                            text = filter.label,
                                            style = IosTypography.Subheadline.copy(
                                                color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
                                            )
                                        )
                                    }
                                }

                                item {
                                    val callChipShape = ContinuousRoundedCornerShape(16.dp)
                                    Box(
                                        modifier = Modifier
                                            .clip(callChipShape)
                                            .background(
                                                if (hideCallRecordings) Color(0xFF30D158).copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                0.75.dp,
                                                if (hideCallRecordings) Color(0xFF30D158).copy(alpha = 0.55f)
                                                else Color.White.copy(alpha = 0.16f),
                                                callChipShape
                                            )
                                            .iosPressEffect(onClick = viewModel::toggleHideCallRecordings)
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        BasicText(
                                            text = if (hideCallRecordings) "Call Clips: Hidden ✓" else "Call Clips: Shown",
                                            style = IosTypography.Subheadline.copy(color = Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        // Featured Horizontal Carousel (when in Library tab and tracks exist)
                        if (selectedTab == IosMusicTab.LIBRARY && tracks.size > 1) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BasicText(
                                        text = "Featured & Hi-Res Masters",
                                        style = IosTypography.Title2
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(tracks.take(8), key = { "feat_${it.id}" }) { featTrack ->
                                            FeaturedTrackSquircleCard(
                                                track = featTrack,
                                                isCurrent = currentTrack?.id == featTrack.id,
                                                frostFactor = frostFactor,
                                                lightDirection = lightDirection,
                                                accentColor = artworkPalette.color0,
                                                onClick = { viewModel.playTrack(featTrack, tracks) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Track Rows
                        items(tracks, key = { it.id }) { track ->
                            val isCurrent = currentTrack?.id == track.id
                            val isFav = favoriteIds.contains(track.id)
                            IntricateTrackRow(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                isFavorite = isFav,
                                frostFactor = frostFactor,
                                lightDirection = lightDirection,
                                accentColor = artworkPalette.color0,
                                onPlay = { viewModel.playTrack(track, tracks) },
                                onToggleFavorite = { viewModel.toggleFavorite(track.id) }
                            )
                        }
                    }

                    IosMusicTab.ALBUMS -> {
                        // 2-Column Bento Squircle Album Grid
                        val chunkedAlbums = albums.chunked(2)
                        items(chunkedAlbums) { rowAlbums ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (album in rowAlbums) {
                                    AlbumBentoCard(
                                        album = album,
                                        frostFactor = frostFactor,
                                        lightDirection = lightDirection,
                                        accentColor = artworkPalette.color0,
                                        onPlayAlbum = {
                                            if (album.tracks.isNotEmpty()) {
                                                viewModel.playTrack(album.tracks.first(), album.tracks)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowAlbums.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    IosMusicTab.AUDIO_LAB -> {
                        item {
                            AudiophileDspStudioScreen(
                                isPlaying = isPlaying,
                                bassEnergy = bassEnergy,
                                eqBands = eqBands,
                                selectedPreset = selectedEqPreset,
                                playbackSpeed = playbackSpeed,
                                frostFactor = frostFactor,
                                crossfadeSeconds = crossfadeSeconds,
                                lightDirection = lightDirection,
                                accentColor = artworkPalette.color0,
                                hapticsController = viewModel.hapticsController,
                                onEqBandChange = viewModel::setEqBand,
                                onSelectPreset = viewModel::applyEqPreset,
                                onSpeedChange = viewModel::setPlaybackSpeed,
                                onFrostChange = viewModel::setFrostFactor,
                                onCrossfadeChange = viewModel::setCrossfadeSeconds
                            )
                        }
                    }
                }
            }

            // Concentric Floating Navigation + Mini-Player Dock
            ConcentricBottomFloatingDock(
                selectedTab = selectedTab,
                onSelectTab = {
                    selectedTab = it
                    viewModel.hapticsController.onGlassSnapConfirmation()
                },
                isScrollingDown = isScrollingDown,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                progressFraction = progressFraction,
                accentColor = artworkPalette.color0,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNextTrack = viewModel::skipNext,
                onExpandNowPlaying = {
                    scope.launch {
                        sheetExpansion.animateTo(1f, IosSprings.SpatialFloat)
                        viewModel.hapticsController.onGlassSnapConfirmation()
                    }
                },
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (stackDimAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = stackDimAlpha))
                )
            }
        }

        // Full-Screen "Now Playing" Modal Sheet
        val activeTrack = currentTrack
        if (activeTrack != null && sheetExpansion.value > 0.001f) {
            NowPlayingSheet(
                expansionFraction = sheetExpansion,
                track = activeTrack,
                queue = activeQueue.ifEmpty { tracks },
                palette = artworkPalette,
                bassEnergy = bassEnergy,
                isPlaying = isPlaying,
                isFavorite = favoriteIds.contains(activeTrack.id),
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                currentPositionMs = currentPositionMs,
                volumeFraction = volumeFraction,
                lyrics = syncedLyrics,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                hapticsController = viewModel.hapticsController,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNextTrack = viewModel::skipNext,
                onPreviousTrack = viewModel::skipPrevious,
                onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) },
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onPlayFromQueue = { chosen ->
                    viewModel.playTrack(chosen, activeQueue.ifEmpty { tracks })
                },
                onSeekToFraction = viewModel::seekToFraction,
                onSeekToMs = viewModel::seekToMs,
                onVolumeChange = viewModel::setVolume,
                onOpenFrostSpectrumSheet = { showSpectrumSheet = true }
            )
        }

        // iOS 27 Liquid Glass Spectrum Slider Customization Modal
        if (showSpectrumSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                LiquidGlassSpectrumSheet(
                    frostFactor = frostFactor,
                    onFrostFactorChange = viewModel::setFrostFactor,
                    crossfadeSeconds = crossfadeSeconds,
                    onCrossfadeSecondsChange = viewModel::setCrossfadeSeconds,
                    lightDirection = lightDirection,
                    hapticsController = viewModel.hapticsController,
                    onDismiss = { showSpectrumSheet = false }
                )
            }
        }
    }
}

@Composable
private fun HeroGlassActionCard(
    title: String,
    subtitle: String,
    glyph: String,
    accentColor: Color,
    frostFactor: Float,
    lightDirection: Offset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { ContinuousRoundedCornerShape(22.dp) }
    Row(
        modifier = modifier
            .height(66.dp)
            .liquidGlassSurface(
                cornerRadius = 22.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF151824),
                accentColor = accentColor
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.24f), shape)
            .iosPressEffect(
                isExpandableGlassPill = true,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ContinuousRoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = glyph,
                style = IosTypography.Headline.copy(color = Color.White)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            BasicText(
                text = title,
                style = IosTypography.Headline,
                maxLines = 1
            )
            BasicText(
                text = subtitle,
                style = IosTypography.LosslessBadge.copy(color = Color.White.copy(alpha = 0.68f)),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FeaturedTrackSquircleCard(
    track: TrackEntity,
    isCurrent: Boolean,
    frostFactor: Float,
    lightDirection: Offset,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = remember { ContinuousRoundedCornerShape(22.dp) }
    Column(
        modifier = Modifier
            .width(142.dp)
            .liquidGlassSurface(
                cornerRadius = 22.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF141622),
                accentColor = if (isCurrent) Color(0xFFFF2D55) else accentColor
            )
            .border(
                0.75.dp,
                if (isCurrent) Color(0xFF64D2FF).copy(alpha = 0.65f)
                else Color.White.copy(alpha = 0.18f),
                shape
            )
            .iosPressEffect(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ContinuousRoundedCornerShape(16.dp))
        ) {
            HardwareArtworkImage(
                artworkUri = track.artworkUri,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = track.title,
            style = IosTypography.Headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = track.artist,
            style = IosTypography.Subheadline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IntricateTrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    frostFactor: Float,
    lightDirection: Offset,
    accentColor: Color,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val rowShape = remember { ContinuousRoundedCornerShape(20.dp) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                cornerRadius = 20.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = if (isCurrent) Color(0xFF202436) else Color(0xFF131520),
                accentColor = if (isCurrent) Color(0xFF64D2FF) else accentColor
            )
            .border(
                0.75.dp,
                if (isCurrent) Color(0xFF64D2FF).copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.14f),
                rowShape
            )
            .iosPressEffect(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            HardwareArtworkImage(
                artworkUri = track.artworkUri,
                contentDescription = track.album,
                modifier = Modifier
                    .size(54.dp)
                    .clip(ContinuousRoundedCornerShape(14.dp))
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(ContinuousRoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniEqualizerBars(
                        isPlaying = isPlaying,
                        color = Color(0xFF64D2FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = track.title,
                style = IosTypography.Headline.copy(
                    color = if (isCurrent) Color(0xFF64D2FF) else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            BasicText(
                text = "${track.artist} • ${track.album}",
                style = IosTypography.Subheadline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(ContinuousRoundedCornerShape(6.dp))
                        .background(Color(0xFF64D2FF).copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    BasicText(
                        text = track.audioBadge,
                        style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .iosPressEffect(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (isFavorite) "♥" else "♡",
                    style = IosTypography.Headline.copy(
                        color = if (isFavorite) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.45f)
                    )
                )
            }
            BasicText(
                text = formatTabularTimestamp(track.durationMs),
                style = IosTypography.TabularTimestamp
            )
        }
    }
}

@Composable
private fun AlbumBentoCard(
    album: AlbumGroup,
    frostFactor: Float,
    lightDirection: Offset,
    accentColor: Color,
    onPlayAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { ContinuousRoundedCornerShape(24.dp) }
    Column(
        modifier = modifier
            .liquidGlassSurface(
                cornerRadius = 24.dp,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                tintColor = Color(0xFF141622),
                accentColor = accentColor
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.18f), shape)
            .iosPressEffect(onClick = onPlayAlbum)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ContinuousRoundedCornerShape(16.dp))
        ) {
            HardwareArtworkImage(
                artworkUri = album.artworkUri,
                contentDescription = album.albumName,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(ContinuousRoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                BasicText(
                    text = "${album.tracks.size} tracks",
                    style = IosTypography.LosslessBadge
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        BasicText(
            text = album.albumName,
            style = IosTypography.Headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        BasicText(
            text = album.artistName,
            style = IosTypography.Subheadline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudiophileDspStudioScreen(
    isPlaying: Boolean,
    bassEnergy: Float,
    eqBands: List<Float>,
    selectedPreset: String,
    playbackSpeed: Float,
    frostFactor: Float,
    crossfadeSeconds: Int,
    lightDirection: Offset,
    accentColor: Color,
    hapticsController: com.apple.auramusic.core.physics.IosHapticsController,
    onEqBandChange: (Int, Float) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFrostChange: (Float) -> Unit,
    onCrossfadeChange: (Int) -> Unit
) {
    val cardShape = remember { ContinuousRoundedCornerShape(26.dp) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    phase += dt * (5.2f + bassEnergy * 6f)
                }
                lastNanos = nanos
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Live 24-Band FFT Spectrum Analyzer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    cornerRadius = 26.dp,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection,
                    tintColor = Color(0xFF141622),
                    accentColor = accentColor
                )
                .border(0.75.dp, Color.White.copy(alpha = 0.24f), cardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "REAL-TIME 120HZ FFT SPECTRUM ANALYZER",
                    style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                )
                BasicText(
                    text = "32-BIT FLOAT DSP",
                    style = IosTypography.TabularTimestamp
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            ) {
                val count = 24
                val gap = size.width / (count * 1.55f)
                val barW = gap * 0.72f
                for (i in 0 until count) {
                    val norm = i.toFloat() / count.toFloat()
                    val eqGain = eqBands.getOrElse((i * 5) / count) { 0.5f }
                    val amp = if (isPlaying) {
                        (0.18f + 0.82f * abs(sin(phase + i * 0.45f)) * (0.5f + eqGain * 0.7f)).coerceIn(0.12f, 1f)
                    } else {
                        (0.15f + 0.35f * eqGain * sin(norm * Math.PI).toFloat()).coerceIn(0.12f, 0.6f)
                    }
                    val h = size.height * amp
                    val x = i * (barW + gap * 0.82f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF64D2FF), accentColor, Color(0xFFFF2D55))
                        ),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW * 0.5f)
                    )
                }
            }
        }

        // 2. 5-Band Parametric Graphic Equalizer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    cornerRadius = 26.dp,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection,
                    tintColor = Color(0xFF141622),
                    accentColor = accentColor
                )
                .border(0.75.dp, Color.White.copy(alpha = 0.24f), cardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(text = "5-Band Parametric EQ", style = IosTypography.Title2)
                BasicText(
                    text = selectedPreset,
                    style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                )
            }

            // Preset Chips
            val presets = listOf("Hi-Res Direct", "Flat Reference", "Sub-Bass Boost", "Vocal Presence", "Late Night")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    val isSel = preset == selectedPreset
                    val pShape = ContinuousRoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .clip(pShape)
                            .background(
                                if (isSel) Color.White.copy(alpha = 0.24f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(0.5.dp, Color.White.copy(alpha = 0.28f), pShape)
                            .iosPressEffect(onClick = { onSelectPreset(preset) })
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText(text = preset, style = IosTypography.Subheadline.copy(color = Color.White))
                    }
                }
            }

            val bandLabels = listOf(
                "60Hz Sub-Bass",
                "230Hz Punch",
                "910Hz Midrange",
                "3.6kHz Presence",
                "14kHz Air & Brilliance"
            )
            for (idx in 0..4) {
                val bandVal = eqBands.getOrElse(idx) { 0.5f }
                val db = ((bandVal - 0.5f) * 24f).roundToInt()
                val dbText = if (db >= 0) "+${db} dB" else "${db} dB"
                LiquidGlassExpandableBar(
                    progress = bandVal,
                    onProgressChange = { onEqBandChange(idx, it) },
                    hapticsController = hapticsController,
                    leftTimestamp = bandLabels[idx],
                    rightTimestamp = dbText,
                    audioBadgeText = if (idx == 0) "Butterworth" else null
                )
            }
        }

        // 3. Playback Speed, Crossfade & Glass Optical Spectrum Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    cornerRadius = 26.dp,
                    frostFactor = frostFactor,
                    lightDirection = lightDirection,
                    tintColor = Color(0xFF141622),
                    accentColor = accentColor
                )
                .border(0.75.dp, Color.White.copy(alpha = 0.24f), cardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText(text = "Studio Transport & Optical Engine", style = IosTypography.Title2)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(text = "Playback Speed", style = IosTypography.Subheadline.copy(color = Color.White))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f, 1.0f, 1.15f, 1.25f).forEach { spd ->
                        val active = abs(playbackSpeed - spd) < 0.02f
                        Box(
                            modifier = Modifier
                                .clip(ContinuousRoundedCornerShape(12.dp))
                                .background(
                                    if (active) Color(0xFF64D2FF).copy(alpha = 0.28f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(0.5.dp, Color.White.copy(alpha = 0.24f), ContinuousRoundedCornerShape(12.dp))
                                .iosPressEffect(onClick = { onSpeedChange(spd) })
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            BasicText(
                                text = "${spd}x",
                                style = IosTypography.TabularTimestamp.copy(color = Color.White)
                            )
                        }
                    }
                }
            }

            LiquidGlassExpandableBar(
                progress = frostFactor,
                onProgressChange = onFrostChange,
                hapticsController = hapticsController,
                leftTimestamp = "0.0 Ultra-Clear Glass",
                rightTimestamp = "1.0 Frosted Glass",
                audioBadgeText = "IOR n=1.52"
            )

            LiquidGlassExpandableBar(
                progress = ((crossfadeSeconds - 1) / 11f).coerceIn(0f, 1f),
                onProgressChange = { frac ->
                    onCrossfadeChange((1 + (frac * 11f).roundToInt()).coerceIn(1, 12))
                },
                hapticsController = hapticsController,
                leftTimestamp = "Crossfade: ${crossfadeSeconds}s",
                rightTimestamp = "12s Max",
                audioBadgeText = "Equal-Power cos/sin"
            )
        }
    }
}
