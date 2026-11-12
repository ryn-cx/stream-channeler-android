// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation
import com.streamchanneler.app.playback.MediaState
import com.streamchanneler.app.screenautomation.ScreenRegion
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class P5ScreenshotTextDisappears(
    shownText: String,
    textRegion: ScreenRegion,
) : PlaybackEndRule {
    // TODO: Validate
    private val screenshotTextCheck = ScreenshotTextCheck(shownText, textRegion)

    // TODO: Validate
    private var playingPackageName: String? = null

    // TODO: Validate
    private val hasShownTextBeenSeenWhilePlaying = AtomicBoolean(false)

    // TODO: Validate
    private val hasShownTextDisappeared = AtomicBoolean(false)

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        playingPackageName = observation.packageName
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? = hasShownTextDisappeared(
        now = playbackEndCheck.now,
        isVideoPlaying = playbackEndCheck.observation.playbackStatus == MediaState.PLAYING,
    )

    // TODO: Validate
    override fun hasVideoEndedWithoutSession(now: Long): Boolean? = hasShownTextDisappeared(now, isVideoPlaying = false)

    // TODO: Validate
    private fun hasShownTextDisappeared(now: Long, isVideoPlaying: Boolean): Boolean? {
        if (hasShownTextDisappeared.get()) return true
        val checkedPackageName = playingPackageName ?: return null
        screenshotTextCheck.startCheckWhenDue(checkedPackageName, now) { isShownTextShowing ->
            if (isShownTextShowing) {
                if (isVideoPlaying) hasShownTextBeenSeenWhilePlaying.set(true)
            } else if (hasShownTextBeenSeenWhilePlaying.get()) {
                hasShownTextDisappeared.set(true)
            }
        }
        return null
    }
}
