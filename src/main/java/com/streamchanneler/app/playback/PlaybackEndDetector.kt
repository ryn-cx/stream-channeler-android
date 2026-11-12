// TODO: Validate
package com.streamchanneler.app.playback

import com.streamchanneler.app.playback.endrules.PlaybackEndCheck
import com.streamchanneler.app.playback.endrules.PlaybackEndRule

// TODO: Validate
enum class WatchStatus {
    STILL_WATCHING,

    FINISHED,

    NEVER_STARTED,
}

// TODO: Validate
const val PLAYBACK_END_SLACK = 2_000L

// TODO: Validate
class PlaybackEndDetector(
    private val launchedAt: Long,
    private val videoDuration: Long?,
    private val playbackEndRules: List<PlaybackEndRule>,
) {
    // TODO: Validate
    var hasStartedPlaying = false
        private set

    // TODO: Validate
    fun watchStatusAfterPoll(now: Long, observation: MediaSessionObservation?): WatchStatus {
        if (!hasStartedPlaying) {
            if (observation?.playbackStatus == MediaState.PLAYING) {
                hasStartedPlaying = true
                playbackEndRules.forEach { it.onPlaybackStarted(now, observation) }
                return WatchStatus.STILL_WATCHING
            }
            return if (now - launchedAt > START_TIMEOUT) WatchStatus.NEVER_STARTED else WatchStatus.STILL_WATCHING
        }

        val hasVideoEndedByRule = if (observation == null) {
            playbackEndRules.firstNotNullOfOrNull { it.hasVideoEndedWithoutSession(now) }
        } else {
            val playbackEndCheck = PlaybackEndCheck(now, observation, videoDuration)
            playbackEndRules.firstNotNullOfOrNull { it.hasVideoEnded(playbackEndCheck) }
        }
        return if (hasVideoEndedByRule == true) WatchStatus.FINISHED else WatchStatus.STILL_WATCHING
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val START_TIMEOUT = 3 * 60_000L
    }
}
