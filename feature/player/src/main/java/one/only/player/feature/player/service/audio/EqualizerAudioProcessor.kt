package one.only.player.feature.player.service.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import one.only.player.core.model.AudioEqualizerBand

// 固定 10 段峰值滤波串联，参数由播放线程之外的偏好流写入
@OptIn(UnstableApi::class)
internal class EqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var settings: AudioEqualizerSettings = AudioEqualizerSettings()

    private var channelCount = 0
    private var filterState = FloatArray(0)
    private val coefficients = FloatArray(BAND_COUNT * COEFFICIENTS_PER_BAND)
    private var appliedLevels: List<Int>? = null

    fun applySettings(settings: AudioEqualizerSettings) {
        this.settings = settings
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        filterState = FloatArray(BAND_COUNT * channelCount * STATE_PER_CHANNEL)
        // 采样率或声道布局换了，系数必须按新格式重算
        appliedLevels = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size <= 0) return
        val outputBuffer = replaceOutputBuffer(size)
        val currentSettings = settings
        if (currentSettings.isBypass || !updateCoefficients(currentSettings)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processShortPcm(inputBuffer, outputBuffer)
            C.ENCODING_PCM_FLOAT -> processFloatPcm(inputBuffer, outputBuffer)
            else -> outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        clearFilterState()
    }

    override fun onReset() {
        clearFilterState()
    }

    private fun clearFilterState() {
        if (filterState.isNotEmpty()) filterState.fill(0f)
    }

    private fun processShortPcm(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
    ) {
        val bytesPerFrame = BYTES_PER_SHORT * channelCount
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                val sample = inputBuffer.short / SHORT_SCALE
                val filtered = filterSample(sample, channel)
                outputBuffer.putShort((filtered * SHORT_SCALE).toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort())
            }
        }
        // 尾部不足一帧的字节原样透传，避免丢样本
        while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
    }

    private fun processFloatPcm(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
    ) {
        val bytesPerFrame = BYTES_PER_FLOAT * channelCount
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                val sample = inputBuffer.float
                val filtered = filterSample(sample, channel)
                outputBuffer.putFloat(filtered.coerceIn(FLOAT_MIN, FLOAT_MAX))
            }
        }
        while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
    }

    private fun filterSample(
        sample: Float,
        channel: Int,
    ): Float {
        var value = sample
        for (band in 0 until BAND_COUNT) {
            val coefficientOffset = band * COEFFICIENTS_PER_BAND
            val stateOffset = (band * channelCount + channel) * STATE_PER_CHANNEL
            val x1 = filterState[stateOffset]
            val x2 = filterState[stateOffset + 1]
            val filtered = coefficients[coefficientOffset] * value +
                coefficients[coefficientOffset + 1] * x1 +
                coefficients[coefficientOffset + 2] * x2 -
                coefficients[coefficientOffset + 3] * filterState[stateOffset + 2] -
                coefficients[coefficientOffset + 4] * filterState[stateOffset + 3]
            filterState[stateOffset + 1] = x1
            filterState[stateOffset] = value
            filterState[stateOffset + 3] = filterState[stateOffset + 2]
            filterState[stateOffset + 2] = filtered
            value = filtered
        }
        return value
    }

    // 返回是否可继续滤波；曲线变化时不重置状态，避免拖动滑杆时效果被反复清零
    private fun updateCoefficients(currentSettings: AudioEqualizerSettings): Boolean {
        val levels = currentSettings.bandLevelsDb
        if (levels == appliedLevels) return true
        if (levels.size < BAND_COUNT) return false
        appliedLevels = levels

        val sampleRate = inputAudioFormat.sampleRate
        for (band in 0 until BAND_COUNT) {
            computeBandCoefficients(
                band = band,
                frequencyHz = AudioEqualizerBand.entries[band].centerFrequencyHz,
                levelDb = levels[band],
                sampleRate = sampleRate,
            )
        }
        return true
    }

    private fun computeBandCoefficients(
        band: Int,
        frequencyHz: Int,
        levelDb: Int,
        sampleRate: Int,
    ) {
        val offset = band * COEFFICIENTS_PER_BAND
        // 中心频率逼近奈奎斯特时峰值滤波会失稳，直接直通
        if (levelDb == 0 || sampleRate <= 0 || frequencyHz >= sampleRate * MAX_FREQUENCY_RATIO) {
            coefficients[offset] = 1f
            for (index in 1 until COEFFICIENTS_PER_BAND) coefficients[offset + index] = 0f
            return
        }

        val angularFrequency = 2.0 * PI * frequencyHz / sampleRate
        val amplitude = 10.0.pow(levelDb / 40.0)
        val alpha = sin(angularFrequency) / (2.0 * BAND_Q)
        val cosine = cos(angularFrequency)
        val a0 = 1.0 + alpha / amplitude
        coefficients[offset] = ((1.0 + alpha * amplitude) / a0).toFloat()
        coefficients[offset + 1] = (-2.0 * cosine / a0).toFloat()
        coefficients[offset + 2] = ((1.0 - alpha * amplitude) / a0).toFloat()
        coefficients[offset + 3] = (-2.0 * cosine / a0).toFloat()
        coefficients[offset + 4] = ((1.0 - alpha / amplitude) / a0).toFloat()
    }

    private companion object {
        private val BAND_COUNT = AudioEqualizerBand.entries.size
        private const val COEFFICIENTS_PER_BAND = 5
        private const val STATE_PER_CHANNEL = 4
        private const val BYTES_PER_SHORT = 2
        private const val BYTES_PER_FLOAT = 4
        private const val SHORT_SCALE = 32768f
        private const val SHORT_MIN = -32768
        private const val SHORT_MAX = 32767
        private const val FLOAT_MIN = -1f
        private const val FLOAT_MAX = 1f

        // 十分之一倍频程相邻，Q 取 1.414 对应约一个倍频程带宽
        private const val BAND_Q = 1.4142135623730951
        private const val MAX_FREQUENCY_RATIO = 0.45
    }
}
