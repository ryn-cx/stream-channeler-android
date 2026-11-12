// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation
import com.streamchanneler.app.playback.MediaState

// TODO: Validate
class P7PositionReset : PlaybackEndRule {
    // TODO: Validate
    private var furthestPlayedPosition = 0L

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        furthestPlayedPosition = observation.position
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        val observation = playbackEndCheck.observation
        if (observation.playbackStatus != MediaState.PLAYING) return null
        val videoDuration = playbackEndCheck.videoDuration ?: return null
        val observedPosition = observation.position
        if (furthestPlayedPosition >= videoDuration / 2 && observedPosition < NEW_VIDEO_START) {
            return true
        }
        furthestPlayedPosition = maxOf(furthestPlayedPosition, observedPosition)
        return null
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val NEW_VIDEO_START = 60_000L
    }
}
