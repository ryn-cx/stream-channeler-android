// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation

// TODO: Validate
class P6NextPlayerVideo : PlaybackEndRule {
    // TODO: Validate
    private var startedPlayerVideoNumber: Int? = null

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        startedPlayerVideoNumber = observation.playerVideoNumber
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        val observedPlayerVideoNumber = playbackEndCheck.observation.playerVideoNumber ?: return null
        val knownStartedPlayerVideoNumber = startedPlayerVideoNumber ?: return null
        return if (observedPlayerVideoNumber != knownStartedPlayerVideoNumber) true else null
    }
}
