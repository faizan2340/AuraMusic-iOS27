package com.apple.auramusic.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.pow

class ReplayGainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var replayGainDb: Float = 0f
        set(value) {
            field = value
            recomputeLinearGain()
        }

    @Volatile
    var trackPeak: Float = 1.0f
        set(value) {
            field = value.coerceAtLeast(0.01f)
            recomputeLinearGain()
        }

    @Volatile
    private var linearGain: Float = 1.0f

    private fun recomputeLinearGain() {
        val rawGain = 10.0.pow(replayGainDb.toDouble() / 20.0).toFloat()
        // Prevent clipping beyond 0 dBFS using trackPeak
        val maxNonClippingGain = 1.0f / trackPeak
        linearGain = min(rawGain, maxNonClippingGain)
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        ) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val gain = linearGain
        val outBuffer = replaceOutputBuffer(remaining)

        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.hasRemaining()) {
                val sample = inputBuffer.float * gain
                outBuffer.putFloat(sample.coerceIn(-1.0f, 1.0f))
            }
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.hasRemaining()) {
                val scaled = (inputBuffer.short * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outBuffer.putShort(scaled.toShort())
            }
        }
        outBuffer.flip()
    }
}
