// TODO: Validate
package com.streamchanneler.app.ui.guide

import com.streamchanneler.app.data.DEFAULT_MINIMUM_VIDEO_LENGTH
import com.streamchanneler.app.data.GuideChannel
import com.streamchanneler.app.data.GuideVideo
import java.util.Calendar
import kotlin.math.roundToInt

// TODO: Validate
data class ScheduledVideo(
    val video: GuideVideo,
    val startMinute: Int,
    val length: Int,
) {
    // TODO: Validate
    val endMinute: Int
        get() = startMinute + length
}

// TODO: Validate
data class GuideRow(
    val channel: GuideChannel,
    val scheduledVideos: List<ScheduledVideo>,
    val isCurrentChannelRow: Boolean,
    val isFavorite: Boolean,
    val channelStatusText: String?,
    val isLoadingMoreVideos: Boolean,
) {
    // TODO: Validate
    val scheduledLength: Int = scheduledVideos.sumOf { it.length }

    // TODO: Validate
    val lastFocusableVideoIndex: Int
        get() = if (channel.hasMoreVideos) scheduledVideos.size else scheduledVideos.lastIndex

    // TODO: Validate
    fun isLoadMoreVideosIndex(videoIndex: Int): Boolean = channel.hasMoreVideos && videoIndex == scheduledVideos.size
}

// TODO: Validate
internal val GuideChannel.displayName: String
    get() = name ?: "Untitled channel"

// TODO: Validate
fun scheduleChannelVideos(
    videos: List<GuideVideo>,
    minimumVideoLength: Int = DEFAULT_MINIMUM_VIDEO_LENGTH,
    textFittingLength: ((GuideVideo) -> Int)? = null,
): List<ScheduledVideo> {
    var nextStartMinute = 0
    return videos.map { video ->
        val videoLength = textFittingLength?.invoke(video)
            ?: maxOf(1, minimumVideoLength, ((video.duration ?: FALLBACK_VIDEO_DURATION) / 60).roundToInt())
        ScheduledVideo(video, nextStartMinute, videoLength).also { nextStartMinute += videoLength }
    }
}

// TODO: Validate
fun startOfCurrentHalfHourSlot(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.MINUTE, if (get(Calendar.MINUTE) < 30) 0 else 30)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

// TODO: Validate
private const val FALLBACK_VIDEO_DURATION = 1800.0
