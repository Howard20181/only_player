package one.only.player.core.data.repository

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.common.Logger
import one.only.player.core.model.UpdateChannel
import org.json.JSONArray

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
)

// 拿不到结果和确认已是最新是两回事，前者不能当成最新版本展示
sealed interface AppUpdateResult {
    data class Available(val info: AppUpdateInfo) : AppUpdateResult
    data object UpToDate : AppUpdateResult
    data object Failed : AppUpdateResult
}

private data class RemoteRelease(
    val version: String,
    val releaseUrl: String,
    val isPrerelease: Boolean,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val RELEASES_URL =
            "https://api.github.com/repos/Kindness-Kismet/only_player/releases?per_page=100"
    }

    suspend fun checkForUpdate(
        currentVersion: String,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): AppUpdateResult = withContext(ioDispatcher) {
        runCatching {
            val candidate = fetchReleases()
                .asSequence()
                .filter { release -> release.matchesChannel(channel) }
                .maxWithOrNull { left, right -> compareVersions(left.version, right.version) }
                ?: return@runCatching AppUpdateResult.UpToDate

            if (compareVersions(candidate.version, currentVersion) > 0) {
                AppUpdateResult.Available(
                    AppUpdateInfo(
                        latestVersion = candidate.version,
                        releaseUrl = candidate.releaseUrl,
                    ),
                )
            } else {
                AppUpdateResult.UpToDate
            }
        }.getOrElse { throwable ->
            Logger.error(TAG, "Failed to check for updates", throwable)
            AppUpdateResult.Failed
        }
    }

    private fun fetchReleases(): List<RemoteRelease> {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "OnlyPlayer")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Unexpected response code: ${connection.responseCode}")
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            return buildList {
                for (index in 0 until array.length()) {
                    val release = array.getJSONObject(index)
                    if (release.optBoolean("draft")) continue
                    val tagName = release.optString("tag_name", "").removePrefix("v")
                    val htmlUrl = release.optString("html_url", "")
                    if (tagName.isEmpty() || htmlUrl.isEmpty()) continue
                    add(
                        RemoteRelease(
                            version = tagName,
                            releaseUrl = htmlUrl,
                            isPrerelease = release.optBoolean("prerelease"),
                        ),
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun RemoteRelease.matchesChannel(channel: UpdateChannel): Boolean = when (channel) {
    UpdateChannel.TEST -> true
    UpdateChannel.STABLE -> !isPrerelease && parseVersion(version)?.betaNumber == null
}

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val betaNumber: Int?,
)

private fun parseVersion(raw: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(raw.removePrefix("v")) ?: return null
    return ParsedVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        betaNumber = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt(),
    )
}

// 正数表示 v1 更新，负数表示 v2 更新。同版本时正式版高于测试版。
private fun compareVersions(v1: String, v2: String): Int {
    val parsed1 = parseVersion(v1)
    val parsed2 = parseVersion(v2)
    if (parsed1 == null && parsed2 == null) return v1.compareTo(v2)
    if (parsed1 == null) return -1
    if (parsed2 == null) return 1

    val coreComparison = compareValuesBy(parsed1, parsed2, { it.major }, { it.minor }, { it.patch })
    if (coreComparison != 0) return coreComparison

    val beta1 = parsed1.betaNumber
    val beta2 = parsed2.betaNumber
    return when {
        beta1 == null && beta2 == null -> 0
        beta1 == null -> 1
        beta2 == null -> -1
        else -> beta1.compareTo(beta2)
    }
}

private val VERSION_PATTERN = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta(\d+))?$""")
