package one.only.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.data.repository.SubtitleSearchRepository
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleSearchResult

class SearchOnlineSubtitlesUseCase @Inject constructor(
    private val subtitleSearchRepository: SubtitleSearchRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(
        query: String,
        languageFilter: OnlineSubtitleLanguageFilter,
        providers: Set<OnlineSubtitleProvider>,
    ): Flow<OnlineSubtitleSearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || providers.isEmpty()) return flowOf(OnlineSubtitleSearchResult())

        return subtitleSearchRepository.search(
            query = trimmedQuery,
            languageFilter = languageFilter,
            providers = providers,
        ).flowOn(ioDispatcher)
    }
}
