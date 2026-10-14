package com.apple.auramusic.audio

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class EqualPowerCrossfadeController(
    private val scope: CoroutineScope,
    private val primaryPlayer: ExoPlayer,
    private val secondaryPlayer: ExoPlayer
) {
    var crossfadeDurationSeconds: Int = 6 // Configurable 1s..12s (0 = Gapless only)
    private var crossfadeJob: Job? = null
    private var isPrimaryActive: Boolean = true

    val activePlayer: ExoPlayer
        get() = if (isPrimaryActive) primaryPlayer else secondaryPlayer

    private val standbyPlayer: ExoPlayer
        get() = if (isPrimaryActive) secondaryPlayer else primaryPlayer

    /**
     * Executes an equal-power crossfade from activePlayer to standbyPlayer over [crossfadeDurationSeconds].
     */
    fun triggerEqualPowerCrossfade(nextMediaItem: MediaItem, onDeckSwapped: (ExoPlayer) -> Unit) {
        val durationMs = (crossfadeDurationSeconds.coerceIn(1, 12) * 1000L)
        val outgoing = activePlayer
        val incoming = standbyPlayer

        crossfadeJob?.cancel()
        incoming.setMediaItem(nextMediaItem)
        incoming.prepare()
        incoming.volume = 0f
        incoming.play()

        crossfadeJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val angle = (t * Math.PI * 0.5).toFloat()

                // Equal-power curve: cos^2(theta) + sin^2(theta) = 1 (constant acoustic power)
                outgoing.volume = cos(angle)
                incoming.volume = sin(angle)

                if (t >= 1.0f) break
                delay(8L) // ~120Hz volume envelope update
            }
            outgoing.pause()
            outgoing.volume = 1.0f
            incoming.volume = 1.0f
            isPrimaryActive = !isPrimaryActive
            onDeckSwapped(incoming)
        }
    }
}
