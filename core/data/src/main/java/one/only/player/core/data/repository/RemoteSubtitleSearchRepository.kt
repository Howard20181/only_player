package one.only.player.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import one.only.player.core.common.Logger
import one.only.player.core.data.remote.subtitle.OpenSubtitlesRestClient
import one.only.player.core.data.remote.subtitle.OpenSubtitlesXmlRpcClient
import one.only.player.core.data.remote.subtitle.SubtitleCatClient
import one.only.player.core.data.remote.subtitle.SubtitleSearchFailedException
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitlePayload
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult

@Singleton
class RemoteSubtitleSearchRepository @Inject constructor(
    private val openSubtitlesRestClient: OpenSubtitlesRestClient,
    private val openSubtitlesXmlRpcClient: OpenSubtitlesXmlRpcClient,
    private val subtitleCatClient: SubtitleCatClient,
) : SubtitleSearchRepository {

    // 单个来源挂掉不该拖垮整次搜索，全部失败才向上抛
    override suspend fun search(
        query: String,
        languageFilter: OnlineSubtitleLanguageFilter,
        providers: Set<OnlineSubtitleProvider>,
    ): List<OnlineSubtitleResult> = coroutineScope {
        val outcomes = providers
            .map { provider -> async { searchProvider(provider, query, languageFilter.languageCode) } }
            .awaitAll()

        val results = outcomes.flatMap { outcome ->
            when (outcome) {
                is ProviderOutcome.Success -> outcome.results
                is ProviderOutcome.Failure -> emptyList()
            }
        }
        if (results.isEmpty() && outcomes.all { it is ProviderOutcome.Failure }) {
            val cause = (outcomes.first() as ProviderOutcome.Failure).cause
            throw SubtitleSearchFailedException("All subtitle providers failed", cause)
        }

        results
            .distinctBy { result -> result.key }
            .sortedByDescending { result -> result.downloadCount ?: 0 }
            .take(MAX_RESULTS)
    }

    override suspend fun fetchSubtitle(result: OnlineSubtitleResult): OnlineSubtitlePayload = when (result.provider) {
        OnlineSubtitleProvider.OPEN_SUBTITLES -> openSubtitlesRestClient.fetchSubtitle(result)
        OnlineSubtitleProvider.OPEN_SUBTITLES_XML_RPC -> openSubtitlesXmlRpcClient.fetchSubtitle(result)
        OnlineSubtitleProvider.SUBTITLE_CAT -> subtitleCatClient.fetchSubtitle(result)
    }

    private suspend fun searchProvider(
        provider: OnlineSubtitleProvider,
        query: String,
        languageCode: String?,
    ): ProviderOutcome = try {
        // 单个来源卡住不能拖死整次搜索，超时按失败处理
        val results = withTimeoutOrNull(PROVIDER_TIMEOUT_MILLIS) {
            when (provider) {
                OnlineSubtitleProvider.OPEN_SUBTITLES -> openSubtitlesRestClient.searchByQuery(
                    query = query,
                    languageCode = languageCode,
                )

                OnlineSubtitleProvider.OPEN_SUBTITLES_XML_RPC -> openSubtitlesXmlRpcClient.searchByQuery(
                    query = query,
                    languageCode = languageCode,
                )

                OnlineSubtitleProvider.SUBTITLE_CAT -> subtitleCatClient.searchByQuery(
                    query = query,
                    languageCode = languageCode,
                )
            }
        } ?: throw SubtitleSearchFailedException("Subtitle provider timed out: $provider")

        Logger.debug(TAG, "Subtitle search ok: provider=$provider, results=${results.size}")
        ProviderOutcome.Success(results)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Logger.error(TAG, "Subtitle search failed: provider=$provider", exception)
        ProviderOutcome.Failure(exception)
    }

    private sealed interface ProviderOutcome {
        data class Success(val results: List<OnlineSubtitleResult>) : ProviderOutcome
        data class Failure(val cause: Throwable) : ProviderOutcome
    }

    private companion object {
        const val TAG = "RemoteSubtitleSearchRepository"
        const val MAX_RESULTS = 60
        const val PROVIDER_TIMEOUT_MILLIS = 30_000L
    }
}
