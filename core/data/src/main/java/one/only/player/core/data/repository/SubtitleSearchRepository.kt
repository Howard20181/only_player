package one.only.player.core.data.repository

import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitlePayload
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult

interface SubtitleSearchRepository {

    suspend fun search(
        query: String,
        languageFilter: OnlineSubtitleLanguageFilter,
        providers: Set<OnlineSubtitleProvider>,
    ): List<OnlineSubtitleResult>

    suspend fun fetchSubtitle(result: OnlineSubtitleResult): OnlineSubtitlePayload
}
