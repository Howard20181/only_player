package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.VideoFilterPreset
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.AppIcons
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoFilterPresetListContent(
    preferences: PlayerPreferences,
    onApplyPreset: (VideoFilterPreset) -> Unit,
    onDeletePreset: (VideoFilterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 列表本身不滚动，由外层容器或 LazyColumn 提供
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (preferences.videoFilterPresets.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_video_filter_presets_empty"),
                text = stringResource(R.string.video_filter_presets_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        preferences.videoFilterPresets.forEach { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioTextButton(
                    text = preset.name,
                    isSelected = preferences.matchesVideoFilterPreset(preset),
                    onClick = { onApplyPreset(preset) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("option_filter_preset_${preset.id}"),
                )
                IconButton(
                    modifier = Modifier.testTag("btn_delete_filter_preset_${preset.id}"),
                    onClick = { onDeletePreset(preset) },
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = stringResource(R.string.delete_video_filter_preset),
                    )
                }
            }
        }
    }
}

@Composable
fun VideoFilterPresetPickerDialog(
    preferences: PlayerPreferences,
    onApplyPreset: (VideoFilterPreset) -> Unit,
    onDeletePreset: (VideoFilterPreset) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionsDialog(
        modifier = modifier.testTag("dialog_video_filter_presets"),
        title = stringResource(R.string.video_filter_presets),
        onDismissClick = onDismissRequest,
    ) {
        item {
            VideoFilterPresetListContent(
                preferences = preferences,
                onApplyPreset = onApplyPreset,
                onDeletePreset = onDeletePreset,
            )
        }
    }
}

@Composable
fun SaveVideoFilterPresetDialog(
    onDismissRequest: () -> Unit,
    onSavePreset: (String) -> Unit,
    shouldKeepSystemBarsHidden: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AppDialog(
        modifier = Modifier.testTag("dialog_save_video_filter_preset"),
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.save_current_as_video_filter_preset),
        content = {
            if (shouldKeepSystemBarsHidden) {
                KeepSystemBarsHidden()
            }
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_filter_preset_name"),
                singleLine = true,
                label = stringResource(R.string.video_filter_preset_name),
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_save_filter_preset"),
                text = stringResource(R.string.save),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { onSavePreset(name.trim()) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismissRequest) },
    )
}

// 弹窗获得焦点时保持系统栏隐藏，避免沉浸模式被打断导致底层面板高度变化
@Composable
private fun KeepSystemBarsHidden() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun PlayerPreferences.matchesVideoFilterPreset(preset: VideoFilterPreset): Boolean = videoBrightness == preset.brightness &&
    videoContrast == preset.contrast &&
    videoSaturation == preset.saturation &&
    videoHue == preset.hue &&
    videoGamma == preset.gamma &&
    videoSharpening == preset.sharpening
