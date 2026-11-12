// TODO: Validate
package com.streamchanneler.app.profiles

import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
internal const val PROFILE_PICKER_TAG = "ProfilePicker"

// TODO: Validate
internal const val LINK_REOPEN_DELAY = 4_000L

// TODO: Validate
internal fun profilePickedStep(waitsForPinScreen: Boolean): String =
    if (waitsForPinScreen) "Profile picked. Waiting for the PIN screen" else "Profile picked"

// TODO: Validate
class ProfilePicker {
    // TODO: Validate
    @Volatile
    var armedSession: ProfilePickerSession? = null
        private set

    // TODO: Validate
    @Volatile
    private var connectedArmingListener: ProfilePickerArmingListener? = null

    // TODO: Validate
    @Synchronized
    fun connectArmingListener(connectingArmingListener: ProfilePickerArmingListener) {
        connectedArmingListener = connectingArmingListener
    }

    // TODO: Validate
    @Synchronized
    fun disconnectArmingListener(disconnectingArmingListener: ProfilePickerArmingListener) {
        if (connectedArmingListener === disconnectingArmingListener) connectedArmingListener = null
    }

    // TODO: Validate
    @Synchronized
    fun arm(launchedStreamingApp: StreamingApp?, reopenLaunchedLink: (() -> Unit)? = null) {
        val replacedSession = armedSession
        val newlyArmedSession = launchedStreamingApp
            ?.takeIf { it.profileScreen != null }
            ?.let { streamingAppToArm -> ProfilePickerSession(streamingAppToArm, reopenLaunchedLink) }
        replacedSession?.markDisarmed()
        armedSession = newlyArmedSession
        if (newlyArmedSession != null) {
            connectedArmingListener?.onArmed(newlyArmedSession)
        } else if (replacedSession != null) {
            connectedArmingListener?.onDisarmed()
        }
    }

    // TODO: Validate
    @Synchronized
    fun disarm() {
        armedSession?.let { disarm(it) }
    }

    // TODO: Validate
    @Synchronized
    fun disarm(sessionToDisarm: ProfilePickerSession) {
        if (armedSession !== sessionToDisarm) return
        sessionToDisarm.markDisarmed()
        armedSession = null
        connectedArmingListener?.onDisarmed()
    }

    // TODO: Validate
    fun armedSessionWatchingApp(packageName: String): ProfilePickerSession? =
        armedSession?.takeIf { it.armedStreamingApp.packageName == packageName && it.isArmed }

    // TODO: Validate
    companion object {
        // TODO: Validate
        const val ARMED_DURATION = 30_000L
    }
}
