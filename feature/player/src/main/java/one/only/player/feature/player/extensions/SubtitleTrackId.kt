package one.only.player.feature.player.extensions

import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

// Media3 合并媒体源时会加数字前缀，字幕 uri 自身的冒号必须保留
private val PERIOD_PREFIX = Regex("""^\d+:""")

// 去掉 Media3 的 period 前缀；不带前缀时原样返回
fun String.withoutTrackPeriodPrefix(): String = replaceFirst(PERIOD_PREFIX, "")

// 把轨道 id 还原成外挂字幕 id；不属于已知外挂字幕时返回 null
fun String?.toExternalSubtitleId(externalSubtitleIds: Collection<String>): String? {
    val rawId = this ?: return null
    if (rawId in externalSubtitleIds) return rawId

    val withoutPrefix = rawId.withoutTrackPeriodPrefix()
    return withoutPrefix.takeIf { it in externalSubtitleIds }
}

// 当前媒体项声明的外挂字幕 id；元数据里的新增列表覆盖不到同目录自动加载的字幕
@UnstableApi
fun Player.externalSubtitleIds(): Set<String> {
    val configurationIds = currentMediaItem
        ?.localConfiguration
        ?.subtitleConfigurations
        ?.mapNotNull { it.id }
        .orEmpty()
    return (configurationIds + currentMediaItem?.mediaMetadata?.addedSubtitleIds.orEmpty()).toSet()
}

// 轨道组对应的外挂字幕 id；内嵌轨道返回 null
@UnstableApi
fun Tracks.Group.externalSubtitleId(externalSubtitleIds: Collection<String>): String? = getTrackFormat(0).id.toExternalSubtitleId(externalSubtitleIds)
