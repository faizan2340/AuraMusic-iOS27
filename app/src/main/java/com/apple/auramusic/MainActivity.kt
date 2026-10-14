package com.apple.auramusic

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.apple.auramusic.core.design.ContinuousRoundedCornerShape
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.graphics.rememberGyroLightDirection
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.core.physics.iosRubberBandOverscroll
import com.apple.auramusic.core.physics.rememberIosFlingBehavior
import com.apple.auramusic.ui.MusicViewModel
import com.apple.auramusic.ui.navigation.ConcentricBottomFloatingDock
import com.apple.auramusic.ui.navigation.IosMusicTab
import com.apple.auramusic.ui.nowplaying.NowPlayingSheet
import com.apple.auramusic.ui.nowplaying.formatTabularTimestamp
import com.apple.auramusic.ui.settings.LiquidGlassSpectrumSheet
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class AuraMusicApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lock120HzProMotionDisplayRate()

        setContent {
            AuraMusicRootScreen(viewModel = viewModel)
        }
    }

    /**
     * Requests the highest refresh rate (120Hz+) supported by the physical display panel.
     */
    private fun lock120HzProMotionDisplayRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null
        val modes = display?.supportedModes ?: return
        val highestRateMode = modes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = highestRateMode.modeId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                frameRateBoostOnTouchEnabled = true
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun AuraMusicRootScreen(viewModel: MusicViewModel) {
    val scope = rememberCoroutineScope()
    val tracks by viewModel.libraryTracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
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

    // Shared-element Now Playing expansion fraction [0.0f = Mini-Player, 1.0f = Full Sheet]
    val sheetExpansion = remember { Animatable(0f) }

    val listState = rememberLazyListState()
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    val isScrollingDown by remember {
        derivedStateOf {
            val down = if (listState.firstVisibleItemIndex != previousIndex) {
                listState.firstVisibleItemIndex > previousIndex
            } else {
                listState.firstVisibleItemScrollOffset > previousScrollOffset + 12
            }
            previousIndex = listState.firstVisibleItemIndex
            previousScrollOffset = listState.firstVisibleItemScrollOffset
            down && listState.firstVisibleItemIndex > 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Library Stack: scales down to 0.92f with darkened backdrop & 24dp top squircle corners
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
                .background(Color(0xFF0A0B10))
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = rememberIosFlingBehavior(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                    bottom = 220.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .iosRubberBandOverscroll()
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(text = selectedTab.label, style = IosTypography.LargeTitle)
                        Box(
                            modifier = Modifier
                                .clip(ContinuousRoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.22f), ContinuousRoundedCornerShape(16.dp))
                                .iosPressEffect(
                                    isExpandableGlassPill = true,
                                    onClick = { showSpectrumSheet = true }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = "Liquid Glass • ${"%.1f".format(frostFactor)}",
                                style = IosTypography.LosslessBadge
                            )
                        }
                    }
                }

                items(tracks, key = { it.id }) { track ->
                    val rowShape = ContinuousRoundedCornerShape(18.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.10f), rowShape)
                            .iosPressEffect(onClick = { viewModel.playTrack(track, tracks) })
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = track.album,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(ContinuousRoundedCornerShape(12.dp))
                                .background(Color(0xFF1E202B))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = track.title,
                                style = IosTypography.Headline,
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
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = track.audioBadge,
                                style = IosTypography.LosslessBadge.copy(color = Color(0xFF64D2FF))
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = formatTabularTimestamp(track.durationMs),
                            style = IosTypography.TabularTimestamp
                        )
                    }
                }
            }

            // Concentric Floating Navigation + Mini-Player Dock
            ConcentricBottomFloatingDock(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                isScrollingDown = isScrollingDown,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
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

            // Stacked-Sheet Darkened Backdrop Overlay
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
                palette = artworkPalette,
                bassEnergy = bassEnergy,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                volumeFraction = volumeFraction,
                lyrics = syncedLyrics,
                frostFactor = frostFactor,
                lightDirection = lightDirection,
                hapticsController = viewModel.hapticsController,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNextTrack = viewModel::skipNext,
                onPreviousTrack = viewModel::skipPrevious,
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
                    .background(Color.Black.copy(alpha = 0.45f)),
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
