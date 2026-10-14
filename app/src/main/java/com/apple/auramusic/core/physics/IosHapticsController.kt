package com.apple.auramusic.core.physics

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
class IosHapticsController @Inject constructor(
    @ApplicationContext context: Context
) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    private var lastScrubberBucket: Int = -1
    private var lastVolumeBucket: Int = -1
    private var lastLyricIndex: Int = -1

    /**
     * Fires Taptic Engine detents for scrubber or volume slider progress [0.0f .. 1.0f]:
     * - Sharp PRIMITIVE_CLICK (1.0f) when hitting 0% or 100% bounds.
     * - Subtle PRIMITIVE_LOW_TICK (0.3f) when crossing 10% intervals.
     */
    fun onSliderProgressChanged(progress: Float, isVolume: Boolean = false) {
        if (!vibrator.hasVibrator()) return

        val clamped = progress.coerceIn(0f, 1f)
        val bucket = floor(clamped * 10f).toInt().coerceIn(0, 10)
        val previousBucket = if (isVolume) lastVolumeBucket else lastScrubberBucket

        if (previousBucket == -1) {
            if (isVolume) lastVolumeBucket = bucket else lastScrubberBucket = bucket
            return
        }

        if (bucket != previousBucket) {
            if (isVolume) lastVolumeBucket = bucket else lastScrubberBucket = bucket

            if (bucket == 0 || bucket == 10) {
                // Sharp boundary detent click at 0% or 100%
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .compose()
                vibrator.vibrate(effect)
            } else {
                // 10% interval micro-detent tick at 0.3f scale
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f)
                    .compose()
                vibrator.vibrate(effect)
            }
        }
    }

    fun resetSliderDetents(isVolume: Boolean = false) {
        if (isVolume) lastVolumeBucket = -1 else lastScrubberBucket = -1
    }

    /**
     * Fires an ultra-subtle PRIMITIVE_TICK at 0.15f scale when manually scrubbing synced lyric lines.
     */
    fun onLyricLineScrubbed(lineIndex: Int) {
        if (!vibrator.hasVibrator() || lineIndex == lastLyricIndex) return
        lastLyricIndex = lineIndex

        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.15f)
            .compose()
        vibrator.vibrate(effect)
    }

    /**
     * Fires a crisp tactile confirmation for glass pill expansion or sheet lock.
     */
    fun onGlassSnapConfirmation() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f)
            .compose()
        vibrator.vibrate(effect)
    }
}
