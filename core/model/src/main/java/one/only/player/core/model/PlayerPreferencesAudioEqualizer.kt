package one.only.player.core.model

fun PlayerPreferences.equalizerBandLevel(band: AudioEqualizerBand): Int = (audioEqualizerBandLevels.getOrNull(band.ordinal) ?: PlayerPreferences.DEFAULT_AUDIO_EQUALIZER_GAIN_DB).coerceIn(
    PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB,
    PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB,
)

fun PlayerPreferences.withAudioEqualizerBandLevel(
    band: AudioEqualizerBand,
    levelDb: Int,
): PlayerPreferences = copy(
    audioEqualizerBandLevels = normalizedEqualizerBandLevels(audioEqualizerBandLevels).toMutableList().apply {
        this[band.ordinal] = levelDb.coerceIn(
            PlayerPreferences.MIN_AUDIO_EQUALIZER_GAIN_DB,
            PlayerPreferences.MAX_AUDIO_EQUALIZER_GAIN_DB,
        )
    },
)

fun PlayerPreferences.withAudioEqualizerBuiltInPresetApplied(preset: AudioEqualizerBuiltInPreset): PlayerPreferences = copy(
    shouldApplyAudioEqualizer = true,
    audioEqualizerBandLevels = preset.bandLevels,
)

fun PlayerPreferences.withAudioEqualizerPresetApplied(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    shouldApplyAudioEqualizer = true,
    audioEqualizerBandLevels = normalizedEqualizerBandLevels(preset.bandLevels),
)

fun PlayerPreferences.toAudioEqualizerPreset(
    name: String,
    id: Long,
): AudioEqualizerPreset = AudioEqualizerPreset(
    id = id,
    name = name,
    bandLevels = normalizedEqualizerBandLevels(audioEqualizerBandLevels),
)

fun PlayerPreferences.withAudioEqualizerPresetSaved(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    audioEqualizerPresets = audioEqualizerPresets
        .filterNot { it.id == preset.id || it.name == preset.name } + preset,
)

fun PlayerPreferences.withAudioEqualizerPresetDeleted(preset: AudioEqualizerPreset): PlayerPreferences = copy(
    audioEqualizerPresets = audioEqualizerPresets.filterNot { it.id == preset.id },
)

fun PlayerPreferences.isAudioEqualizerPresetSelected(preset: AudioEqualizerPreset): Boolean = shouldApplyAudioEqualizer &&
    normalizedEqualizerBandLevels(audioEqualizerBandLevels) == normalizedEqualizerBandLevels(preset.bandLevels)

fun PlayerPreferences.isAudioEqualizerPresetSelected(preset: AudioEqualizerBuiltInPreset): Boolean = shouldApplyAudioEqualizer &&
    normalizedEqualizerBandLevels(audioEqualizerBandLevels) == preset.bandLevels
