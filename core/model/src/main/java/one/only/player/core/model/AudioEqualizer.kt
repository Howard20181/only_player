package one.only.player.core.model

import kotlinx.serialization.Serializable

// 均衡器固定 10 段，中心频率是 UI 与 DSP 共用的唯一来源
@Serializable
enum class AudioEqualizerBand(
    val centerFrequencyHz: Int,
) {
    HZ_31(31),
    HZ_62(62),
    HZ_125(125),
    HZ_250(250),
    HZ_500(500),
    HZ_1000(1000),
    HZ_2000(2000),
    HZ_4000(4000),
    HZ_8000(8000),
    HZ_16000(16000),
}

// 内置预设只提供曲线，名称留给 UI 层本地化
@Serializable
enum class AudioEqualizerBuiltInPreset(
    val bandLevels: List<Int>,
) {
    FLAT(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    BASS_BOOST(listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
    TREBLE_BOOST(listOf(0, 0, 0, 0, 0, 0, 2, 4, 5, 6)),
    VOCAL(listOf(-2, -1, 0, 2, 4, 4, 3, 1, 0, 0)),
    ROCK(listOf(5, 3, 0, -1, -1, 0, 2, 3, 4, 4)),
    POP(listOf(-1, 0, 2, 3, 2, 0, -1, -1, 0, 1)),
    JAZZ(listOf(3, 2, 1, 1, -1, -1, 0, 1, 2, 3)),
    CLASSICAL(listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4)),
}

@Serializable
data class AudioEqualizerPreset(
    val id: Long = 0L,
    val name: String,
    val bandLevels: List<Int> = PlayerPreferences.DEFAULT_AUDIO_EQUALIZER_BAND_LEVELS,
)

// 频段增益按索引存放，长度始终与 AudioEqualizerBand 一致
fun List<Int>.normalizedEqualizerBandLevels(): List<Int> {
    val defaults = PlayerPreferences.DEFAULT_AUDIO_EQUALIZER_BAND_LEVELS
    if (size == defaults.size) {
        return map { it.coerceIn(PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB, PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB) }
    }
    return List(defaults.size) { index -> (getOrNull(index) ?: defaults[index]).coerceIn(PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB, PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB) }
}

fun PlayerPreferences.equalizerBandLevel(band: AudioEqualizerBand): Int = audioEqualizerBandLevels.getOrElse(band.ordinal) {
    PlayerPreferences.DEFAULT_AUDIO_EQUALIZER_GAIN_DB
}

fun PlayerPreferences.withAudioEqualizerFrom(preferences: PlayerPreferences): PlayerPreferences = copy(
    shouldApplyAudioEqualizer = preferences.shouldApplyAudioEqualizer,
    audioEqualizerBandLevels = preferences.audioEqualizerBandLevels,
    audioEqualizerPresets = preferences.audioEqualizerPresets,
)

fun PlayerPreferences.withAudioEqualizerAdjustment(
    transform: (PlayerPreferences) -> PlayerPreferences,
): PlayerPreferences {
    if (!shouldApplyAudioEqualizer) return this
    return transform(this)
}

fun PlayerPreferences.withAudioEqualizerBandLevel(
    band: AudioEqualizerBand,
    levelDb: Int,
): PlayerPreferences {
    val normalizedLevel = levelDb.coerceIn(
        PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB,
        PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB,
    )
    return copy(
        audioEqualizerBandLevels = audioEqualizerBandLevels.normalizedEqualizerBandLevels().toMutableList().apply {
            this[band.ordinal] = normalizedLevel
        },
    )
}

fun PlayerPreferences.withAudioEqualizerBandLevels(levels: List<Int>): PlayerPreferences = copy(
    audioEqualizerBandLevels = levels.normalizedEqualizerBandLevels(),
)

fun PlayerPreferences.withAudioEqualizerBuiltInPresetApplied(preset: AudioEqualizerBuiltInPreset): PlayerPreferences = copy(
    shouldApplyAudioEqualizer = true,
    audioEqualizerBandLevels = preset.bandLevels.normalizedEqualizerBandLevels(),
)

fun PlayerPreferences.withAudioEqualizerPresetApplied(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    shouldApplyAudioEqualizer = true,
    audioEqualizerBandLevels = preset.bandLevels.normalizedEqualizerBandLevels(),
)

fun PlayerPreferences.toAudioEqualizerPreset(name: String, id: Long): AudioEqualizerPreset = AudioEqualizerPreset(
    id = id,
    name = name,
    bandLevels = audioEqualizerBandLevels.normalizedEqualizerBandLevels(),
)

fun PlayerPreferences.withAudioEqualizerPresetSaved(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    audioEqualizerPresets = audioEqualizerPresets
        .filterNot { it.id == preset.id || it.name == preset.name } + preset,
)

fun PlayerPreferences.withAudioEqualizerPresetDeleted(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    audioEqualizerPresets = audioEqualizerPresets.filterNot { it.id == preset.id },
)

fun PlayerPreferences.matchesAudioEqualizerPreset(preset: AudioEqualizerPreset): Boolean = audioEqualizerBandLevels.normalizedEqualizerBandLevels() == preset.bandLevels.normalizedEqualizerBandLevels()

fun PlayerPreferences.matchesAudioEqualizerBuiltInPreset(preset: AudioEqualizerBuiltInPreset): Boolean = audioEqualizerBandLevels.normalizedEqualizerBandLevels() == preset.bandLevels.normalizedEqualizerBandLevels()
