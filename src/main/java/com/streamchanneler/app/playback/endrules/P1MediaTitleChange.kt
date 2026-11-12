// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation

// TODO: Validate
class P1MediaTitleChange(private val realTitleWaitTime: Long = 0L) : PlaybackEndRule {
    // TODO: Validate
    private var startedMediaTitle: String? = null

    // TODO: Validate
    private var startedPlayingAt = 0L

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        startedMediaTitle = observation.mediaTitle
        startedPlayingAt = now
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        val observedMediaTitle = playbackEndCheck.observation.mediaTitle ?: return null
        val knownStartedMediaTitle = startedMediaTitle
        if (knownStartedMediaTitle == null || playbackEndCheck.now - startedPlayingAt < realTitleWaitTime) {
            startedMediaTitle = observedMediaTitle
            return null
        }
        return if (observedMediaTitle != knownStartedMediaTitle) true else null
    }
}
