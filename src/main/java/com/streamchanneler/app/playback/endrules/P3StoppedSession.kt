// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaState

// TODO: Validate
class P3StoppedSession(private val minimumStopDuration: Long = 0L) : PlaybackEndRule {
    // TODO: Validate
    private var stoppedSince: Long? = null

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        if (playbackEndCheck.observation.playbackStatus != MediaState.STOPPED) {
            stoppedSince = null
            return null
        }
        return hasStopLastedLongEnough(playbackEndCheck.now)
    }

    // TODO: Validate
    override fun hasVideoEndedWithoutSession(now: Long): Boolean? = hasStopLastedLongEnough(now)

    // TODO: Validate
    private fun hasStopLastedLongEnough(now: Long): Boolean {
        val stoppedSinceOrNow = stoppedSince ?: now.also { stoppedSince = it }
        return now - stoppedSinceOrNow >= minimumStopDuration
    }
}
