package one.only.player.core.data.repository

import kotlinx.coroutines.flow.Flow
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitlePayload
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.model.OnlineSubtitleSearchResult

interface SubtitleSearchRepository {

    fun search(
        query: String,
        languageFilter: OnlineSubtitleLanguageFilter,
        providers: Set<OnlineSubtitleProvider>,
    ): Flow<OnlineSubtitleSearchResult>

    suspend fun fetchSubtitle(result: OnlineSubtitleResult): OnlineSubtitlePayload
}
