// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation
import com.streamchanneler.app.playback.MediaState
import com.streamchanneler.app.playback.PLAYBACK_END_SLACK

// TODO: Validate
class P9ReportedDuration : PlaybackEndRule {
    // TODO: Validate
    private var mediaSessionDuration: Long? = null

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        rememberMediaSessionDuration(observation)
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        val observation = playbackEndCheck.observation
        if (observation.playbackStatus != MediaState.PLAYING) return null
        rememberMediaSessionDuration(observation)
        val knownMediaSessionDuration = mediaSessionDuration ?: return null
        return if (observation.position >= knownMediaSessionDuration - PLAYBACK_END_SLACK) true else null
    }

    // TODO: Validate
    private fun rememberMediaSessionDuration(observation: MediaSessionObservation) {
        observation.duration?.takeIf { it > 0 }?.let { mediaSessionDuration = it }
    }
}
