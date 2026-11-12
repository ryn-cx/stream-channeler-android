// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation

// TODO: Validate
data class PlaybackEndCheck(
    val now: Long,
    val observation: MediaSessionObservation,
    val videoDuration: Long?,
)

// TODO: Validate
interface PlaybackEndRule {
    // TODO: Validate
    fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) = Unit

    // TODO: Validate
    fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean?

    // TODO: Validate
    fun hasVideoEndedWithoutSession(now: Long): Boolean? = null
}

// TODO: Validate
fun standardPlaybackEndRules(): List<PlaybackEndRule> =
    listOf(P3StoppedSession(), ShortMediaIsAdBreak(), P1MediaTitleChange(), P9ReportedDuration())
