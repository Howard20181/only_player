package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import one.only.player.core.model.AudioEqualizerBand
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.equalizerBandLevel
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.AppIcons

// 十段增益都按整数分贝保存，滑杆刻度与存储值一一对应
private val AUDIO_EQUALIZER_GAIN_INT_RANGE =
    PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB..PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB
private val AUDIO_EQUALIZER_GAIN_RANGE =
    AUDIO_EQUALIZER_GAIN_INT_RANGE.first.toFloat()..AUDIO_EQUALIZER_GAIN_INT_RANGE.last.toFloat()

@Composable
fun AudioEqualizerPanel(
    preferences: PlayerPreferences,
    onEnabledChange: (Boolean) -> Unit,
    onBandLevelChange: (AudioEqualizerBand, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_audio_equalizer"),
            title = stringResource(R.string.enable_audio_equalizer),
            description = stringResource(R.string.enable_audio_equalizer_description),
            icon = AppIcons.Equalizer,
            isChecked = preferences.shouldApplyAudioEqualizer,
            onClick = { onEnabledChange(!preferences.shouldApplyAudioEqualizer) },
        )
        PreferenceGroup {
            AudioEqualizerBandSliders(
                preferences = preferences,
                onBandLevelChange = onBandLevelChange,
            )
        }
    }
}

@Composable
fun AudioEqualizerBandSliders(
    preferences: PlayerPreferences,
    onBandLevelChange: (AudioEqualizerBand, Int) -> Unit,
    sliderTestTagPrefix: String = "slider_audio_equalizer_band",
    resetTestTagPrefix: String = "btn_reset_audio_equalizer_band",
) {
    AudioEqualizerBand.entries.forEach { band ->
        AudioEqualizerBandSlider(
            band = band,
            levelDb = preferences.equalizerBandLevel(band),
            isEnabled = preferences.shouldApplyAudioEqualizer,
            onCommit = { onBandLevelChange(band, it) },
            sliderTestTag = "${sliderTestTagPrefix}_${band.ordinal}",
            resetTestTag = "${resetTestTagPrefix}_${band.ordinal}",
        )
    }
}

@Composable
private fun AudioEqualizerBandSlider(
    band: AudioEqualizerBand,
    levelDb: Int,
    isEnabled: Boolean,
    onCommit: (Int) -> Unit,
    sliderTestTag: String,
    resetTestTag: String,
) {
    // 只保留当前频段的拖动草稿，其他偏好更新不打断手势
    var draftLevel by remember(levelDb, isEnabled) { mutableIntStateOf(levelDb) }
    val frequencyLabel = band.frequencyLabel()
    PreferenceSlider(
        modifier = Modifier.testTag(sliderTestTag),
        title = frequencyLabel,
        description = stringResource(R.string.decibel_value, signedDecibels(draftLevel)),
        isEnabled = isEnabled,
        value = draftLevel.toFloat(),
        valueRange = AUDIO_EQUALIZER_GAIN_RANGE,
        steps = AUDIO_EQUALIZER_GAIN_INT_RANGE.sliderStepCount(),
        onValueChange = { draftLevel = it.roundToInt() },
        onValueChangeFinished = { onCommit(draftLevel) },
        trailingContent = {
            ResetIconButton(
                modifier = Modifier.testTag(resetTestTag),
                enabled = isEnabled,
                onClick = {
                    draftLevel = PlayerPreferences.DEFAULT_AUDIO_EQUALIZER_GAIN_DB
                    onCommit(draftLevel)
                },
                contentDescription = stringResource(R.string.reset_audio_equalizer_band, frequencyLabel),
            )
        },
    )
}

// 频段标题与重置说明共用同一份中心频率文案
@Composable
fun AudioEqualizerBand.frequencyLabel(): String = if (centerFrequencyHz >= KILOHERTZ_THRESHOLD_HZ) {
    stringResource(R.string.frequency_khz, centerFrequencyHz / KILOHERTZ_THRESHOLD_HZ)
} else {
    stringResource(R.string.frequency_hz, centerFrequencyHz)
}

// 面板与设置页共用的增益文案，正值显式带加号
fun signedDecibels(valueDb: Int): String = if (valueDb > 0) "+$valueDb" else "$valueDb"

private const val KILOHERTZ_THRESHOLD_HZ = 1000
