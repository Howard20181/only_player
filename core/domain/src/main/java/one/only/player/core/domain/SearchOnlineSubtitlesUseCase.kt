package one.only.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.data.repository.SubtitleSearchRepository
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult

class SearchOnlineSubtitlesUseCase @Inject constructor(
    private val subtitleSearchRepository: SubtitleSearchRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        query: String,
        languageFilter: OnlineSubtitleLanguageFilter,
        providers: Set<OnlineSubtitleProvider>,
    ): List<OnlineSubtitleResult> = withContext(ioDispatcher) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()
        if (providers.isEmpty()) return@withContext emptyList()

        subtitleSearchRepository.search(
            query = trimmedQuery,
            languageFilter = languageFilter,
            providers = providers,
        )
    }
}
