// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaState
import com.streamchanneler.app.playback.PLAYBACK_END_SLACK

// TODO: Validate
class P8GuideLength : PlaybackEndRule {
    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        if (playbackEndCheck.observation.playbackStatus != MediaState.PLAYING) return null
        val videoDuration = playbackEndCheck.videoDuration ?: return null
        val videoEndPosition = videoDuration - PLAYBACK_END_SLACK
        return if (playbackEndCheck.observation.position >= videoEndPosition) true else null
    }
}
