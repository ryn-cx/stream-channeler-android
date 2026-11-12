// TODO: Validate
package com.streamchanneler.app.profiles

import android.os.SystemClock
import com.streamchanneler.app.playback.PlaybackMonitor

// TODO: Validate
class ProfilePickerStageTimeline(
    private val streamingPackageName: String,
    private val followedPlaybackMonitor: PlaybackMonitor,
    private val followedProfilePicker: ProfilePicker,
) {
    // TODO: Validate
    private val startedAt = SystemClock.elapsedRealtime()

    // TODO: Validate
    private val secondsByStageName = mutableMapOf<String, Double>()
    // TODO: Validate
    private var followedProfilePickerSession: ProfilePickerSession? = null

    // TODO: Validate
    fun hasReached(stageName: String): Boolean = stageName in secondsByStageName

    // TODO: Validate
    fun recordStage(stageName: String, reachedAt: Long = SystemClock.elapsedRealtime()) {
        secondsByStageName.putIfAbsent(stageName, (reachedAt - startedAt) / 1000.0)
    }

    // TODO: Validate
    fun followUntilStage(wantedStageName: String, timeout: Long): Boolean {
        val stopWaitingAt = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < stopWaitingAt) {
            val (playbackStartedPackageName, isMonitoringPlayback) = StreamingTestDevice.runOnMainThreadAndWait {
                followedPlaybackMonitor.playbackStartedPackageName to followedPlaybackMonitor.isMonitoring
            }
            followedProfilePicker.armedSession?.let { followedProfilePickerSession = it }
            followedProfilePickerSession?.profilePickedAt?.takeIf { it > 0 }
                ?.let { recordStage(PROFILE_PICKED_STAGE_NAME, it) }
            followedProfilePickerSession?.pinEnteredAt?.takeIf { it > 0 }
                ?.let { recordStage(PIN_ENTERED_STAGE_NAME, it) }
            if (playbackStartedPackageName == streamingPackageName) recordStage(PLAYBACK_STARTED_STAGE_NAME)
            if (hasReached(wantedStageName)) return true
            if (wantedStageName == PLAYBACK_STARTED_STAGE_NAME && !isMonitoringPlayback) {
                throw assertionErrorWithStageTimings("The app gave up waiting for playback to start")
            }
            Thread.sleep(POLL_INTERVAL)
        }
        return false
    }

    // TODO: Validate
    fun assertionErrorWithStageTimings(failureReason: String): AssertionError = AssertionError("$failureReason\n${describeStages()}")

    // TODO: Validate
    fun describeStages(): String = STAGE_NAMES_IN_ORDER.joinToString("\n") { stageName ->
        "$stageName: " + (secondsByStageName[stageName]?.let { "%.1f s".format(it) } ?: "not reached")
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        const val APP_OPENED_STAGE_NAME = "App opened"
        // TODO: Validate
        const val LINK_OPENED_STAGE_NAME = "Link opened"
        // TODO: Validate
        const val PROFILE_PICKED_STAGE_NAME = "Profile picked"
        // TODO: Validate
        const val PIN_ENTERED_STAGE_NAME = "PIN entered"
        // TODO: Validate
        const val PLAYBACK_STARTED_STAGE_NAME = "Playback started"

        // TODO: Validate
        private const val POLL_INTERVAL = 100L

        // TODO: Validate
        private val STAGE_NAMES_IN_ORDER = listOf(
            APP_OPENED_STAGE_NAME,
            LINK_OPENED_STAGE_NAME,
            PROFILE_PICKED_STAGE_NAME,
            PIN_ENTERED_STAGE_NAME,
            PLAYBACK_STARTED_STAGE_NAME,
        )
    }
}
