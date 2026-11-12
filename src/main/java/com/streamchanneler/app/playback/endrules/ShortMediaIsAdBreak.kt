// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation

// TODO: Validate
class ShortMediaIsAdBreak : PlaybackEndRule {
    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? =
        false.takeIf { isLikelyAdBreak(playbackEndCheck.observation, playbackEndCheck.videoDuration) }

    // TODO: Validate
    private fun isLikelyAdBreak(observation: MediaSessionObservation, videoDuration: Long?): Boolean {
        val observedDuration = observation.duration ?: return false
        val expectedDuration = videoDuration ?: return false
        return observedDuration in 1 until (expectedDuration * AD_BREAK_DURATION_FRACTION).toLong()
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val AD_BREAK_DURATION_FRACTION = 0.5
    }
}
