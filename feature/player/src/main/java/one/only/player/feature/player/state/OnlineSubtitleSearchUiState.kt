package one.only.player.feature.player.state

import android.net.Uri
import androidx.compose.runtime.Stable
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.model.OnlineSubtitleSearchPreferences
import one.only.player.core.ui.base.DataState

@Stable
data class OnlineSubtitleSearchUiState(
    val query: String = "",
    val preferences: OnlineSubtitleSearchPreferences = OnlineSubtitleSearchPreferences(),
    val outcome: DataState<List<OnlineSubtitleResult>>? = null,
    val downloadingKey: String? = null,
) {
    val results: List<OnlineSubtitleResult>
        get() = outcome?.result.orEmpty()

    val isSearching: Boolean
        get() = outcome == DataState.Loading
}

sealed interface OnlineSubtitleEvent {
    val mediaId: String

    data class Saved(val uri: Uri, override val mediaId: String) : OnlineSubtitleEvent
    data class Failed(val cause: Throwable, override val mediaId: String) : OnlineSubtitleEvent
    data class SearchFailed(override val mediaId: String) : OnlineSubtitleEvent
}
