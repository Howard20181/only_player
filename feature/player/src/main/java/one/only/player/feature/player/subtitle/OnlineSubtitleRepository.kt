package one.only.player.feature.player.subtitle

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.common.Logger

class OnlineSubtitleRepository(
    private val cacheRoot: File,
    private val downloader: suspend (String) -> DownloadStream,
    private val nowMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(DispatcherType.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(
        cacheRoot = context.cacheDir,
        downloader = { url -> downloadWithOkHttp(url) },
        nowMillis = System::currentTimeMillis,
        ioDispatcher = ioDispatcher,
    )

    private val subtitleCacheDir = File(cacheRoot, ONLINE_SUBTITLE_DIR_NAME)

    suspend fun downloadSubtitle(url: String): DownloadedOnlineSubtitle {
        val parsedUrl = ParsedSubtitleUrl.from(url)
        subtitleCacheDir.mkdirs()

        val baseName = url.hashCode().toUInt().toString(16)
        val targetFile = File(subtitleCacheDir, "$baseName.${parsedUrl.extension}")
        val tempFile = File.createTempFile(baseName, ".${parsedUrl.extension}.part", subtitleCacheDir)
        Logger.debug(TAG, "Download online subtitle start: extension=${parsedUrl.extension}, target=${targetFile.name}")

        try {
            downloader(url).inputStream.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    copyCapped(inputStream, outputStream)
                }
            }
            promoteTempFile(tempFile, targetFile)
        } catch (exception: OnlineSubtitleException) {
            tempFile.delete()
            throw exception
        } catch (exception: IOException) {
            Logger.error(TAG, "Download online subtitle failed", exception)
            tempFile.delete()
            throw OnlineSubtitleDownloadFailedException(exception)
        }

        Logger.debug(TAG, "Download online subtitle cached: file=${targetFile.name}, bytes=${targetFile.length()}")
        return DownloadedOnlineSubtitle(file = targetFile)
    }

    // 复制时检查大小，避免超限字幕占满缓存。
    private fun copyCapped(
        inputStream: InputStream,
        outputStream: OutputStream,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val readCount = inputStream.read(buffer)
            if (readCount == -1) break

            totalBytes += readCount
            if (totalBytes > MAX_SUBTITLE_BYTES) throw OnlineSubtitleTooLargeException()
            outputStream.write(buffer, 0, readCount)
        }
        if (totalBytes == 0L) throw EmptyOnlineSubtitleException()
    }

    // 搜索字幕已由数据层校验与解包，这里只负责缓存。
    suspend fun importSubtitle(
        bytes: ByteArray,
        extension: String,
    ): DownloadedOnlineSubtitle = withContext(ioDispatcher) {
        subtitleCacheDir.mkdirs()
        // 以内容哈希命名：同一条字幕重复导入复用同一份缓存
        val baseName = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val targetFile = File(subtitleCacheDir, "$baseName.$extension")
        val tempFile = File.createTempFile(baseName, ".$extension.part", subtitleCacheDir)
        Logger.debug(TAG, "Import online subtitle start: extension=$extension, bytes=${bytes.size}")

        try {
            tempFile.writeBytes(bytes)
            promoteTempFile(tempFile, targetFile)
        } catch (exception: IOException) {
            Logger.error(TAG, "Import online subtitle failed", exception)
            tempFile.delete()
            throw OnlineSubtitleDownloadFailedException(exception)
        }

        Logger.debug(TAG, "Import online subtitle cached: file=${targetFile.name}")
        DownloadedOnlineSubtitle(file = targetFile)
    }

    private fun promoteTempFile(
        tempFile: File,
        targetFile: File,
    ) {
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Unable to move subtitle file")
        }
        targetFile.setLastModified(nowMillis())
    }

    fun deleteExpiredSubtitles() {
        val expireBefore = nowMillis() - SUBTITLE_TTL_MILLIS
        val files = subtitleCacheDir.listFiles().orEmpty()

        files.forEach { file ->
            if (file.isFile && file.lastModified() < expireBefore) {
                file.delete()
            }
        }
    }

    fun touchSubtitle(uri: Uri) {
        if (uri.scheme != "file") return
        val file = uri.path?.let(::File) ?: return
        touchSubtitleFile(file)
    }

    internal fun touchSubtitleFile(file: File) {
        if (!file.isFile) return
        if (file.parentFile?.canonicalFile != subtitleCacheDir.canonicalFile) return

        file.setLastModified(nowMillis())
    }

    private data class ParsedSubtitleUrl(
        val extension: String,
    ) {
        companion object {
            fun from(url: String): ParsedSubtitleUrl {
                val uri = runCatching { URI(url.trim()) }
                    .getOrElse { throw InvalidOnlineSubtitleUrlException() }
                val scheme = uri.scheme?.lowercase().orEmpty()
                if (scheme !in SUPPORTED_SCHEMES) {
                    throw InvalidOnlineSubtitleSchemeException(scheme)
                }
                if (uri.host.isNullOrBlank()) {
                    throw InvalidOnlineSubtitleUrlException()
                }

                val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) {
                    throw InvalidOnlineSubtitleExtensionException(extension)
                }

                return ParsedSubtitleUrl(extension = extension)
            }
        }
    }

    private companion object {
        const val TAG = "OnlineSubtitleRepository"
        const val ONLINE_SUBTITLE_DIR_NAME = "online_subtitles"
        const val MAX_SUBTITLE_BYTES = 10L * 1024 * 1024
        const val SUBTITLE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
        val SUPPORTED_SCHEMES = setOf("http", "https")
        val SUPPORTED_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "webvtt", "smi", "sami")

        fun downloadWithOkHttp(url: String): DownloadStream {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = OkHttpClient.Builder().build().newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("Subtitle download failed with code ${response.code}")
            }

            val body = response.body
            if (body == null) {
                response.close()
                throw IOException("Subtitle response body is empty")
            }

            return DownloadStream(ResponseInputStream(body.byteStream(), response::close))
        }
    }
}

data class DownloadedOnlineSubtitle(
    val file: File,
) {
    val uriString: String = file.toURI().toString()
    val uri: Uri get() = Uri.parse(uriString)
}

class DownloadStream(
    val inputStream: InputStream,
)

open class OnlineSubtitleException(message: String) : IllegalStateException(message)

open class InvalidOnlineSubtitleException(message: String) : IllegalArgumentException(message)

class InvalidOnlineSubtitleSchemeException(
    val scheme: String,
) : InvalidOnlineSubtitleException("Unsupported subtitle scheme: $scheme")

class InvalidOnlineSubtitleUrlException : InvalidOnlineSubtitleException("Unsupported subtitle URL")

class InvalidOnlineSubtitleExtensionException(
    val extension: String,
) : InvalidOnlineSubtitleException("Unsupported subtitle extension: $extension")

class EmptyOnlineSubtitleException : OnlineSubtitleException("Online subtitle is empty")

class OnlineSubtitleTooLargeException : OnlineSubtitleException("Online subtitle exceeds 10 MB")

class OnlineSubtitleDownloadFailedException(
    cause: IOException,
) : IOException("Online subtitle download failed", cause)

private class ResponseInputStream(
    inputStream: InputStream,
    private val onClose: () -> Unit,
) : FilterInputStream(inputStream) {

    override fun close() {
        try {
            super.close()
        } finally {
            // 关闭响应，避免真实下载场景泄漏连接。
            onClose()
        }
    }
}
