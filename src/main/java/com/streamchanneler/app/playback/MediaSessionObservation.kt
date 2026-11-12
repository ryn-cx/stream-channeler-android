// TODO: Validate
package com.streamchanneler.app.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock

// TODO: Validate
enum class MediaState {
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED,
}

// TODO: Validate
data class MediaSessionObservation(
    val packageName: String,
    val playbackStatus: MediaState,
    val position: Long,
    val duration: Long?,
    val mediaTitle: String?,
    val playbackSpeed: Float,
    val playerVideoNumber: Int? = null,
    val mediaId: String? = null,
)

// TODO: Validate
fun mediaSessionObservationOfController(mediaController: MediaController): MediaSessionObservation {
    val playbackState = mediaController.playbackState
    val observedPlaybackStatus = when (playbackState?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        -> MediaState.PLAYING
        PlaybackState.STATE_PAUSED -> MediaState.PAUSED
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        -> MediaState.BUFFERING
        else -> MediaState.STOPPED
    }
    val metadata = mediaController.metadata
    return MediaSessionObservation(
        packageName = mediaController.packageName,
        playbackStatus = observedPlaybackStatus,
        position = estimatedPosition(playbackState),
        duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
        mediaTitle = mediaTitleOfMetadata(metadata),
        playbackSpeed = playbackState?.playbackSpeed ?: 0f,
        mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotEmpty() },
    )
}

// TODO: Validate
internal fun mediaTitleOfMetadata(metadata: MediaMetadata?): String? =
    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

// TODO: Validate
internal fun estimatedPosition(playbackState: PlaybackState?): Long {
    if (playbackState == null) return 0
    val reportedPosition = playbackState.position.coerceAtLeast(0)
    if (playbackState.state != PlaybackState.STATE_PLAYING || playbackState.lastPositionUpdateTime <= 0) {
        return reportedPosition
    }
    val millisSinceReport = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
    return reportedPosition + (millisSinceReport * playbackState.playbackSpeed).toLong()
}
