// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation

// TODO: Validate
class P2MediaIdChange : PlaybackEndRule {
    // TODO: Validate
    private var currentMediaId: String? = null

    // TODO: Validate
    private var furthestPositionInCurrentMediaId = 0L

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        currentMediaId = observation.mediaId
        furthestPositionInCurrentMediaId = observation.position
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        val observation = playbackEndCheck.observation
        val observedMediaId = observation.mediaId ?: return null
        val knownCurrentMediaId = currentMediaId
        if (knownCurrentMediaId == null || observedMediaId == knownCurrentMediaId) {
            currentMediaId = observedMediaId
            furthestPositionInCurrentMediaId = maxOf(furthestPositionInCurrentMediaId, observation.position)
            return null
        }
        val videoDuration = playbackEndCheck.videoDuration
        val previousMediaWasTheVideo =
            videoDuration == null || furthestPositionInCurrentMediaId >= videoDuration / 2
        if (previousMediaWasTheVideo) return true
        currentMediaId = observedMediaId
        furthestPositionInCurrentMediaId = observation.position
        return null
    }
}
