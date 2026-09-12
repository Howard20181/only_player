package one.only.player.feature.player.service.decoder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener

@OptIn(UnstableApi::class)
internal class VideoEffectsRenderer(
    builder: Builder,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
) : MediaCodecVideoRenderer(builder) {

    private val eventDispatcher = VideoRendererEventListener.EventDispatcher(eventHandler, eventListener)

    override fun handleMessage(
        messageType: Int,
        message: Any?,
    ) {
        super.handleMessage(messageType, message)
        val shouldRedraw = state == STATE_ENABLED &&
            ((messageType == MSG_SET_VIDEO_OUTPUT && message != null) || messageType == MSG_SET_VIDEO_OUTPUT_RESOLUTION)
        // 暂停时输出画布重建或改尺寸后，需要重绘缓存帧。
        if (shouldRedraw) setVideoEffects(VideoFrameProcessor.REDRAW)
    }

    @SuppressLint("RestrictedApi")
    override fun createPlaybackVideoGraphWrapper(
        context: Context,
        videoFrameReleaseControl: VideoFrameReleaseControl,
    ): PlaybackVideoGraphWrapper {
        val wrapper = PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
            .setEnablePlaylistMode(true)
            .setEnableReplayableCache(true)
            .experimentalSetLateThresholdToDropInputUs(DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US)
            .setClock(clock)
            .build()
        // 效果管线的输出尺寸必须继续上报，供方向和比例计算使用。
        wrapper.addListener(object : PlaybackVideoGraphWrapper.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                eventDispatcher.videoSizeChanged(videoSize)
            }
        })
        return wrapper
    }
}
