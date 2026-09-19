package one.only.player.feature.player.state

import android.net.Uri
import androidx.compose.runtime.Stable
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.ui.base.DataState

@Stable
data class OnlineSubtitleSearchUiState(
    val query: String = "",
    val languageFilter: OnlineSubtitleLanguageFilter = OnlineSubtitleLanguageFilter.ALL,
    val providers: Set<OnlineSubtitleProvider> = DEFAULT_PROVIDERS,
    val outcome: DataState<List<OnlineSubtitleResult>>? = null,
    val downloadingKey: String? = null,
) {
    val results: List<OnlineSubtitleResult>
        get() = outcome?.result.orEmpty()

    val isSearching: Boolean
        get() = outcome == DataState.Loading

    companion object {
        // 两个 .org 来源命中同一份库，默认只开 REST 与 SubtitleCat，避免重复结果
        val DEFAULT_PROVIDERS = setOf(
            OnlineSubtitleProvider.OPEN_SUBTITLES,
            OnlineSubtitleProvider.SUBTITLE_CAT,
        )
    }
}

sealed interface OnlineSubtitleEvent {
    val mediaId: String

    data class Saved(val uri: Uri, override val mediaId: String) : OnlineSubtitleEvent
    data class Failed(val cause: Throwable, override val mediaId: String) : OnlineSubtitleEvent
}
