package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.feature.player.state.OnlineSubtitleSearchUiState
import one.only.player.feature.player.ui.panel.PanelActionButton
import one.only.player.feature.player.ui.panel.PanelChip
import one.only.player.feature.player.ui.panel.PanelOptionList
import one.only.player.feature.player.ui.panel.PanelOptionRow
import one.only.player.feature.player.ui.panel.rememberPlayerPanelTokens
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnlineSubtitleSearchContent(
    state: OnlineSubtitleSearchUiState,
    onQueryChange: (String) -> Unit,
    onShowLanguageFilter: () -> Unit,
    onProviderToggle: (OnlineSubtitleProvider) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (OnlineSubtitleResult) -> Unit,
) {
    val tokens = rememberPlayerPanelTokens()
    val scrollState = rememberScrollState()
    var searchButtonOffset by remember { mutableIntStateOf(0) }
    // 搜索结束后滚到按钮下方，让状态与结果直接可见
    LaunchedEffect(state.outcome) {
        if (state.outcome is DataState.Success && searchButtonOffset > 0) {
            scrollState.animateScrollTo(searchButtonOffset)
        }
    }

    PanelOptionList(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
            .padding(horizontal = 16.dp),
    ) {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_online_subtitle_query"),
            label = stringResource(R.string.online_subtitle_search_hint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        ListSectionTitle(text = stringResource(R.string.online_subtitle_search_language))
        PanelOptionRow(
            isSelected = false,
            text = state.languageFilter.label(),
            testTag = "btn_online_subtitle_language",
            onClick = onShowLanguageFilter,
        )
        ListSectionTitle(text = stringResource(R.string.online_subtitle_search_source))
        FilterRow {
            OnlineSubtitleProvider.entries.forEach { provider ->
                PanelChip(
                    text = provider.label(),
                    isSelected = provider in state.providers,
                    testTag = "chip_online_subtitle_source_${provider.name.lowercase()}",
                    onClick = { onProviderToggle(provider) },
                )
            }
        }
        PanelActionButton(
            modifier = Modifier
                .testTag("btn_online_subtitle_search")
                .onGloballyPositioned { coordinates ->
                    searchButtonOffset = coordinates.positionInParent().y.toInt()
                },
            text = stringResource(R.string.online_subtitle_search_action),
            isProminent = true,
            isEnabled = state.query.isNotBlank() && !state.isSearching,
            onClick = onSearch,
        )
        when (val outcome = state.outcome) {
            null -> Unit

            DataState.Loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_online_subtitle_searching"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                MiuixText(
                    text = stringResource(R.string.online_subtitle_searching),
                    color = tokens.contentColor,
                    style = MiuixTheme.textStyles.body2,
                )
            }

            is DataState.Error -> MiuixText(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_online_subtitle_search_failed"),
                text = stringResource(R.string.online_subtitle_search_failed),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )

            is DataState.Success -> if (outcome.value.isEmpty()) {
                MiuixText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_online_subtitle_search_empty"),
                    text = stringResource(R.string.online_subtitle_search_empty),
                    color = tokens.secondaryContentColor,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
        state.results.forEach { result ->
            PanelOptionRow(
                isSelected = false,
                text = result.title,
                description = if (state.downloadingKey == result.key) {
                    stringResource(R.string.online_subtitle_downloading)
                } else {
                    result.describe()
                },
                testTag = "item_online_subtitle_${result.key}",
                isEnabled = state.downloadingKey == null,
                onClick = { onSelectResult(result) },
            )
        }
    }
}

@Composable
internal fun OnlineSubtitleLanguageContent(
    selected: OnlineSubtitleLanguageFilter,
    onSelect: (OnlineSubtitleLanguageFilter) -> Unit,
) {
    PanelOptionList(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
    ) {
        OnlineSubtitleLanguageFilter.entries.forEach { filter ->
            PanelOptionRow(
                text = filter.label(),
                isSelected = filter == selected,
                testTag = "item_online_subtitle_language_${filter.name.lowercase()}",
                onClick = { onSelect(filter) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun OnlineSubtitleLanguageFilter.label(): String = stringResource(
    when (this) {
        OnlineSubtitleLanguageFilter.ALL -> R.string.online_subtitle_language_all
        OnlineSubtitleLanguageFilter.CHINESE_SIMPLIFIED -> R.string.online_subtitle_language_chinese_simplified
        OnlineSubtitleLanguageFilter.CHINESE_TRADITIONAL -> R.string.online_subtitle_language_chinese_traditional
        OnlineSubtitleLanguageFilter.CHINESE_BILINGUAL -> R.string.online_subtitle_language_chinese_bilingual
        OnlineSubtitleLanguageFilter.ENGLISH -> R.string.online_subtitle_language_english
        OnlineSubtitleLanguageFilter.JAPANESE -> R.string.online_subtitle_language_japanese
        OnlineSubtitleLanguageFilter.KOREAN -> R.string.online_subtitle_language_korean
        OnlineSubtitleLanguageFilter.FRENCH -> R.string.online_subtitle_language_french
        OnlineSubtitleLanguageFilter.GERMAN -> R.string.online_subtitle_language_german
        OnlineSubtitleLanguageFilter.SPANISH -> R.string.online_subtitle_language_spanish
        OnlineSubtitleLanguageFilter.RUSSIAN -> R.string.online_subtitle_language_russian
        OnlineSubtitleLanguageFilter.PORTUGUESE -> R.string.online_subtitle_language_portuguese
        OnlineSubtitleLanguageFilter.ITALIAN -> R.string.online_subtitle_language_italian
        OnlineSubtitleLanguageFilter.ARABIC -> R.string.online_subtitle_language_arabic
        OnlineSubtitleLanguageFilter.THAI -> R.string.online_subtitle_language_thai
        OnlineSubtitleLanguageFilter.VIETNAMESE -> R.string.online_subtitle_language_vietnamese
        OnlineSubtitleLanguageFilter.INDONESIAN -> R.string.online_subtitle_language_indonesian
        OnlineSubtitleLanguageFilter.TURKISH -> R.string.online_subtitle_language_turkish
        OnlineSubtitleLanguageFilter.DUTCH -> R.string.online_subtitle_language_dutch
        OnlineSubtitleLanguageFilter.POLISH -> R.string.online_subtitle_language_polish
    },
)

@Composable
private fun OnlineSubtitleProvider.label(): String = stringResource(
    when (this) {
        OnlineSubtitleProvider.OPEN_SUBTITLES -> R.string.online_subtitle_source_opensubtitles
        OnlineSubtitleProvider.OPEN_SUBTITLES_XML_RPC -> R.string.online_subtitle_source_opensubtitles_xml_rpc
        OnlineSubtitleProvider.SUBTITLE_CAT -> R.string.online_subtitle_source_subtitlecat
    },
)

@Composable
private fun OnlineSubtitleResult.describe(): String {
    val parts = buildList {
        add(if (languageCode.isEmpty()) stringResource(R.string.unknown) else languageName.ifEmpty { languageCode })
        if (format.isNotEmpty()) add(format.uppercase())
        add(provider.label())
        downloadCount?.let { count -> add(stringResource(R.string.online_subtitle_downloads, count)) }
    }
    return parts.joinToString(separator = " · ")
}
