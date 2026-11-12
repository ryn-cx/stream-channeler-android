// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.playback.MediaState
import com.streamchanneler.app.screenautomation.OnScreenElement
import com.streamchanneler.app.screenautomation.findOnScreenElementInActiveWindow

// TODO: Validate
class P4OnScreenElementAppears(
    private val onScreenElement: OnScreenElement,
    private val delayAfterOnScreenElementSeen: Long = 0L,
    private val checkWhilePaused: Boolean = false,
    private val isOnScreenElementShowing: (packageName: String, onScreenElement: OnScreenElement) -> Boolean =
        { packageName, shownOnScreenElement -> findOnScreenElementInActiveWindow(packageName, shownOnScreenElement) != null },
) : PlaybackEndRule {
    private var lastOnScreenElementCheckAt: Long? = null

    private var onScreenElementSeenAt: Long? = null

    // TODO: Validate
    override fun hasVideoEnded(playbackEndCheck: PlaybackEndCheck): Boolean? {
        if (onScreenElementSeenAt != null) return hasDelayAfterOnScreenElementSeenPassed(playbackEndCheck.now)
        val observation = playbackEndCheck.observation
        val isStatusChecked = observation.playbackStatus == MediaState.PLAYING ||
            observation.playbackStatus == MediaState.STOPPED ||
            (checkWhilePaused && observation.playbackStatus == MediaState.PAUSED)
        if (!isStatusChecked) return null
        val previousOnScreenElementCheckAt = lastOnScreenElementCheckAt
        if (previousOnScreenElementCheckAt != null && playbackEndCheck.now - previousOnScreenElementCheckAt < ON_SCREEN_ELEMENT_CHECK_INTERVAL) {
            return null
        }
        lastOnScreenElementCheckAt = playbackEndCheck.now
        if (!isOnScreenElementShowing(observation.packageName, onScreenElement)) return null
        onScreenElementSeenAt = playbackEndCheck.now
        return hasDelayAfterOnScreenElementSeenPassed(playbackEndCheck.now)
    }

    // TODO: Validate
    override fun hasVideoEndedWithoutSession(now: Long): Boolean? =
        if (onScreenElementSeenAt != null) hasDelayAfterOnScreenElementSeenPassed(now) else null

    // TODO: Validate
    private fun hasDelayAfterOnScreenElementSeenPassed(now: Long): Boolean? {
        val seenAt = onScreenElementSeenAt ?: return null
        return if (now - seenAt >= delayAfterOnScreenElementSeen) true else null
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val ON_SCREEN_ELEMENT_CHECK_INTERVAL = 1_000L
    }
}
