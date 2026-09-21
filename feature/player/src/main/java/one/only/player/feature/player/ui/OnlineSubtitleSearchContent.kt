package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.model.OnlineSubtitleSearchPreferences
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.extensions.label
import one.only.player.feature.player.state.OnlineSubtitleSearchUiState
import one.only.player.feature.player.ui.panel.PanelActionButton
import one.only.player.feature.player.ui.panel.PanelOptionList
import one.only.player.feature.player.ui.panel.PanelOptionRow
import one.only.player.feature.player.ui.panel.rememberPlayerPanelTokens
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OnlineSubtitleSearchContent(
    state: OnlineSubtitleSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (OnlineSubtitleResult) -> Unit,
) {
    val tokens = rememberPlayerPanelTokens()
    val scrollState = rememberScrollState()
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
        PanelActionButton(
            modifier = Modifier.testTag("btn_online_subtitle_search"),
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

            is DataState.Error -> Unit

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
internal fun OnlineSubtitleSearchSettingsContent(
    preferences: OnlineSubtitleSearchPreferences,
    onShowLanguageFilter: () -> Unit,
    onProviderToggle: (OnlineSubtitleProvider) -> Unit,
) {
    PanelOptionList(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ListSectionTitle(text = stringResource(R.string.online_subtitle_search_language))
        PanelOptionRow(
            text = preferences.languageFilter.label(),
            isSelected = false,
            testTag = "btn_online_subtitle_language",
            onClick = onShowLanguageFilter,
        )
        ListSectionTitle(text = stringResource(R.string.online_subtitle_search_source))
        OnlineSubtitleProvider.entries.forEach { provider ->
            PanelOptionRow(
                text = provider.label(),
                isSelected = provider in preferences.providers,
                testTag = "option_online_subtitle_source_${provider.name.lowercase()}",
                onClick = { onProviderToggle(provider) },
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
