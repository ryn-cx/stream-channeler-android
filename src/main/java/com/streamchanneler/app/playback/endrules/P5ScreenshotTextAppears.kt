// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaSessionObservation
import com.streamchanneler.app.screenautomation.ScreenRegion
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class P5ScreenshotTextAppears(
    shownText: String,
    textRegion: ScreenRegion,
) : PlaybackEndRule {
    // TODO: Validate
    private val screenshotTextCheck = ScreenshotTextCheck(shownText, textRegion)

    // TODO: Validate
    private var playingPackageName: String? = null

    // TODO: Validate
    private val hasShownTextBeenSeen = AtomicBoolean(false)

    // TODO: Validate
    override fun onPlaybackStarted(now: Long, observation: MediaSessionObservation) {
        playingPackageName = observation.packageName
    }

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? = hasShownTextAppeared(playbackEndCheck.now)

    // TODO: Validate
    override fun hasVideoEndedWithoutSession(now: Long): Boolean? = hasShownTextAppeared(now)

    // TODO: Validate
    private fun hasShownTextAppeared(now: Long): Boolean? {
        if (hasShownTextBeenSeen.get()) return true
        val checkedPackageName = playingPackageName ?: return null
        screenshotTextCheck.startCheckWhenDue(checkedPackageName, now) { isShownTextShowing ->
            if (isShownTextShowing) hasShownTextBeenSeen.set(true)
        }
        return null
    }
}
