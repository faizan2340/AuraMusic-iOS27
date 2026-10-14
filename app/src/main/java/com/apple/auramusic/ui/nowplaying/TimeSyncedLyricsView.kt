package com.apple.auramusic.ui.nowplaying

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.apple.auramusic.core.design.IosTypography
import com.apple.auramusic.core.design.iosPressEffect
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.core.physics.IosSprings
import com.apple.auramusic.core.physics.iosRubberBandOverscroll
import com.apple.auramusic.core.physics.rememberIosFlingBehavior
import com.apple.auramusic.data.lyrics.LyricSyllable
import com.apple.auramusic.data.lyrics.SyncedLyricLine
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TimeSyncedLyricsView(
    lyrics: List<SyncedLyricLine>,
    currentPositionMs: Long,
    onSeekToMs: (Long) -> Unit,
    hapticsController: IosHapticsController,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val activeIndex = remember(lyrics, currentPositionMs) {
        lyrics.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0)
    }

    // Fire 0.15f PRIMITIVE_TICK when user manually scrubs through lyric lines
    LaunchedEffect(listState.firstVisibleItemIndex, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            hapticsController.onLyricLineScrubbed(listState.firstVisibleItemIndex)
        }
    }

    LaunchedEffect(activeIndex) {
        if (!listState.isScrollInProgress && lyrics.isNotEmpty()) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -180
            )
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberIosFlingBehavior(),
        contentPadding = PaddingValues(vertical = 140.dp, horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = modifier
            .fillMaxSize()
            .iosRubberBandOverscroll()
    ) {
        itemsIndexed(lyrics, key = { idx, line -> "${idx}_${line.startMs}" }) { index, line ->
            val distanceFromActive = abs(index - activeIndex)
            val relativeVisibleIndex = (index - listState.firstVisibleItemIndex).coerceIn(0, 12)

            // Staggered Spring Cascade: each visible line shifts upward with (index * 18ms) delay
            val cascadeOffsetY = remember(activeIndex) { Animatable(22f) }
            LaunchedEffect(activeIndex) {
                delay(relativeVisibleIndex * 18L)
                cascadeOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = IosSprings.SpatialFloat
                )
            }

            // Depth-of-Field progressive Gaussian blur:
            // Active line = 0dp, +/-1 = 1.5dp, +/-2 = 4dp, +/-3+ = 8dp
            val blurRadiusDp = when (distanceFromActive) {
                0 -> 0.dp
                1 -> 1.5.dp
                2 -> 4.dp
                else -> 8.dp
            }
            val blurRadiusPx = with(density) { blurRadiusDp.toPx() }

            val targetOpacity by animateFloatAsState(
                targetValue = when (distanceFromActive) {
                    0 -> 1.0f
                    1 -> 0.52f
                    2 -> 0.34f
                    else -> 0.20f
                },
                animationSpec = IosSprings.SpatialFloat,
                label = "lyricOpacity"
            )

            val scale by animateFloatAsState(
                targetValue = if (distanceFromActive == 0) 1.0f else 0.94f,
                animationSpec = IosSprings.SpatialFloat,
                label = "lyricScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = cascadeOffsetY.value
                        scaleX = scale
                        scaleY = scale
                        alpha = targetOpacity
                        if (blurRadiusPx > 0.5f) {
                            renderEffect = RenderEffect.createBlurEffect(
                                blurRadiusPx,
                                blurRadiusPx,
                                Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        }
                    }
                    .iosPressEffect(onClick = { onSeekToMs(line.startMs) })
            ) {
                if (line.isWordSynced && distanceFromActive == 0) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        line.syllables.forEach { syllable ->
                            WordSyncedSyllableChip(
                                syllable = syllable,
                                currentPositionMs = currentPositionMs
                            )
                        }
                    }
                } else {
                    // Active line has subtle white outer bloom shadow
                    val bloomStyle = if (distanceFromActive == 0) {
                        IosTypography.LyricLineActive.copy(
                            shadow = Shadow(
                                color = Color.White.copy(alpha = 0.55f),
                                offset = Offset.Zero,
                                blurRadius = 18f
                            )
                        )
                    } else {
                        IosTypography.LyricLineActive
                    }
                    BasicText(text = line.rawText, style = bloomStyle)
                }
            }
        }
    }
}

@Composable
private fun WordSyncedSyllableChip(
    syllable: LyricSyllable,
    currentPositionMs: Long
) {
    val density = LocalDensity.current
    val rawFill = ((currentPositionMs - syllable.startMs).toFloat() /
        (syllable.endMs - syllable.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    val fillFraction by animateFloatAsState(
        targetValue = rawFill,
        animationSpec = IosSprings.FluidFloat,
        label = "syllableFill"
    )

    // -2dp vertical lift as syllable is sung
    val liftPx = with(density) { (-2).dp.toPx() } * fillFraction

    BasicText(
        text = syllable.text,
        style = IosTypography.LyricLineActive.copy(
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.45f * fillFraction),
                offset = Offset.Zero,
                blurRadius = 14f * fillFraction
            )
        ),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .graphicsLayer {
                translationY = liftPx
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val edge = fillFraction.coerceIn(0f, 1f)
                val maskBrush = Brush.horizontalGradient(
                    0.0f to Color.White,
                    (edge - 0.08f).coerceAtLeast(0f) to Color.White,
                    (edge + 0.08f).coerceAtMost(1f) to Color.White.copy(alpha = 0.35f),
                    1.0f to Color.White.copy(alpha = 0.35f)
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                }
            }
    )
}
