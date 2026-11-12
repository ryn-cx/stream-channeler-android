// TODO: Validate
package com.streamchanneler.app.profiles

import android.os.SystemClock
import com.streamchanneler.app.streamingapps.StreamingApp
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class ProfilePickerSession(
    val armedStreamingApp: StreamingApp,
    reopenLaunchedLink: (() -> Unit)? = null,
) {
    // TODO: Validate
    val profileScreen: StreamingProfileScreen = checkNotNull(armedStreamingApp.profileScreen)

    // TODO: Validate
    val streamingSiteName: String
        get() = armedStreamingApp.site.pluginKey

    // TODO: Validate
    val reopenLinkAfterProfilePicked: (() -> Unit)? =
        reopenLaunchedLink.takeIf { armedStreamingApp.isLinkReopenedAfterProfilePicked }

    // TODO: Validate
    val armedAt = SystemClock.elapsedRealtime()

    // TODO: Validate
    private val armedUntil = armedAt + ProfilePicker.ARMED_DURATION

    // TODO: Validate
    @Volatile
    var profilePickedAt = 0L
        private set

    // TODO: Validate
    @Volatile
    var pinEnteredAt = 0L
        private set

    // TODO: Validate
    @Volatile
    var hasClickedPlayButton = false
        private set

    // TODO: Validate
    @Volatile
    var currentStep = "Waiting for the profile screen"

    // TODO: Validate
    @Volatile
    var isWindowPolled = false

    // TODO: Validate
    @Volatile
    private var isDisarmed = false

    // TODO: Validate
    @Volatile
    private var isDetectionPaused = true

    // TODO: Validate
    @Volatile
    var skipCurrentWait: (() -> Unit)? = null

    // TODO: Validate
    val isInSkippableWait: Boolean
        get() = skipCurrentWait != null

    // TODO: Validate
    @Volatile
    var isWaitSkipRequested = false

    // TODO: Validate
    val isPressingRemoteButtons = AtomicBoolean(false)

    // TODO: Validate
    val isPinEntryScheduled = AtomicBoolean(false)

    // TODO: Validate
    val isArmed: Boolean
        get() = !isDisarmed && SystemClock.elapsedRealtime() < armedUntil

    // TODO: Validate
    val remainingArmedTime: Long
        get() = (armedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    // TODO: Validate
    val hasPickedProfile: Boolean
        get() = profilePickedAt > 0

    // TODO: Validate
    val hasEnteredPin: Boolean
        get() = pinEnteredAt > 0

    // TODO: Validate
    val detectsScreensAndPlayback: Boolean
        get() = !isDetectionPaused && isArmed

    // TODO: Validate
    fun markDisarmed() {
        isDisarmed = true
    }

    // TODO: Validate
    fun markProfilePicked() {
        profilePickedAt = SystemClock.elapsedRealtime()
    }

    // TODO: Validate
    fun markPinEntered() {
        pinEnteredAt = SystemClock.elapsedRealtime()
    }

    // TODO: Validate
    fun markPlayButtonClicked() {
        hasClickedPlayButton = true
    }

    // TODO: Validate
    fun pauseDetection() {
        isDetectionPaused = true
    }

    // TODO: Validate
    fun resumeDetection() {
        isDetectionPaused = false
    }
}
