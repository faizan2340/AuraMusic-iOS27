package com.apple.auramusic.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BassAmplitudeBufferSink constructor() : TeeAudioProcessor.AudioBufferSink {

    private var sampleRateHz: Int = 48000
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_FLOAT

    // 2nd-order Butterworth Low-Pass Biquad coefficients (fc = 135 Hz)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    private var smoothedBass = 0f
    private val _bassEnergy = MutableStateFlow(0f)
    val bassEnergy: StateFlow<Float> = _bassEnergy.asStateFlow()

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz.coerceAtLeast(8000)
        this.channelCount = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        recomputeBiquadCoefficients(cutoffHz = 135f, fs = this.sampleRateHz.toFloat())
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    private fun recomputeBiquadCoefficients(cutoffHz: Float, fs: Float) {
        val omega = (2.0 * Math.PI * cutoffHz / fs).toFloat()
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / sqrt(2.0f) // Q = 0.7071 (Butterworth)
        val a0 = 1f + alpha
        b0 = ((1f - cs) * 0.5f) / a0
        b1 = (1f - cs) / a0
        b2 = ((1f - cs) * 0.5f) / a0
        a1 = (-2f * cs) / a0
        a2 = (1f - alpha) / a0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val remainingBytes = dup.remaining()
        if (remainingBytes <= 0) return

        var sumSquares = 0f
        var sampleCount = 0

        when (encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val floatBuf = dup.asFloatBuffer()
                val stride = channelCount.coerceAtLeast(1)
                var idx = 0
                while (idx < floatBuf.limit()) {
                    val mono = floatBuf.get(idx)
                    val filtered = processBiquad(mono)
                    sumSquares += filtered * filtered
                    sampleCount++
                    idx += stride * 2 // Decimate by 2 for minimal CPU overhead
                }
            }
            C.ENCODING_PCM_16BIT -> {
                val shortBuf = dup.asShortBuffer()
                val stride = channelCount.coerceAtLeast(1)
                var idx = 0
                while (idx < shortBuf.limit()) {
                    val mono = shortBuf.get(idx) / 32768f
                    val filtered = processBiquad(mono)
                    sumSquares += filtered * filtered
                    sampleCount++
                    idx += stride * 2
                }
            }
            else -> return
        }

        if (sampleCount > 0) {
            val rms = sqrt(sumSquares / sampleCount)
            val normalized = (rms * 3.8f).coerceIn(0f, 1f)
            // Fast attack (0.35), smooth release (0.08)
            val alpha = if (normalized > smoothedBass) 0.35f else 0.08f
            smoothedBass += alpha * (normalized - smoothedBass)
            _bassEnergy.value = smoothedBass
        }
    }

    private fun processBiquad(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}
