// TODO: Validate
package com.streamchanneler.app.profiles

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.screenautomation.KeyPressSwallowedUntilReleased
import com.streamchanneler.app.screenautomation.ScreenAutomationFeature
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.StatusMessageOverlay
import com.streamchanneler.app.screenautomation.isBackKeyCode
import com.streamchanneler.app.screenautomation.isOkKeyCode


// TODO: Validate
class ProfilePickerAutomation(
    private val screenAutomationService: ScreenAutomationService,
    private val profilePicker: ProfilePicker,
    private val localAppStorage: LocalAppStorage,
    private val mediaSessions: MediaSessions,
) : ScreenAutomationFeature, ProfilePickerArmingListener {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val profilePickerOverlay by lazy { StatusMessageOverlay(screenAutomationService) }
    // TODO: Validate
    private val armedAppPlaybackWatcher by lazy { ArmedAppPlaybackWatcher(mediaSessions, ::onArmedAppPlaybackStarted) }
    // TODO: Validate
    private val accessibilityClickedProfilePicker by lazy {
        AccessibilityClickedProfilePicker(
            localAppStorage,
            mainHandler,
            { screenAutomationService.rootInActiveWindow },
            ::showWaitingMessage,
            ::showStep,
        )
    }
    // TODO: Validate
    private val remoteButtonProfilePicker by lazy {
        RemoteButtonProfilePicker(
            localAppStorage,
            mediaSessions,
            screenAutomationService.screenshots,
            screenAutomationService.screenTaps,
            ::showWaitingMessage,
            ::showPlaybackDetectedAndDisarm,
            ::showStep,
        )
    }
    // TODO: Validate
    @Volatile
    private var waitingMessageSession: ProfilePickerSession? = null
    // TODO: Validate
    @Volatile
    private var overlayShowsPlaybackDetectedMessage = false
    // TODO: Validate
    private val backKeyPressCancellingWaiting = KeyPressSwallowedUntilReleased(screenAutomationService::updateKeyEventFiltering)
    // TODO: Validate
    private val okKeyPressSkippingWait = KeyPressSwallowedUntilReleased(screenAutomationService::updateKeyEventFiltering)

    // TODO: Validate
    override val filtersKeyEvents: Boolean
        get() = waitingMessageSession != null || backKeyPressCancellingWaiting.isSwallowingKey ||
            okKeyPressSkippingWait.isSwallowingKey

    // TODO: Validate
    override fun onArmed(armedSession: ProfilePickerSession) {
        showWaitingMessage(armedSession)
        mainHandler.postDelayed({ profilePicker.disarm(armedSession) }, armedSession.remainingArmedTime)
        val profileScreen = armedSession.profileScreen
        if (profileScreen !is P2RemoteButtonProfileScreen) return
        val wasArmedAppRunningAtArm = mediaSessions.hasMediaSession(armedSession.armedStreamingApp.packageName)
        remoteButtonProfilePicker.startPickingProfile(armedSession, profileScreen, wasArmedAppRunningAtArm)
    }

    // TODO: Validate
    override fun onDisarmed() {
        waitingMessageSession = null
        screenAutomationService.updateKeyEventFiltering()
        armedAppPlaybackWatcher.stop()
        if (!overlayShowsPlaybackDetectedMessage) profilePickerOverlay.hide()
        overlayShowsPlaybackDetectedMessage = false
    }

    // TODO: Validate
    private fun showWaitingMessage(armedSession: ProfilePickerSession) {
        waitingMessageSession = armedSession
        overlayShowsPlaybackDetectedMessage = false
        armedSession.resumeDetection()
        screenAutomationService.updateKeyEventFiltering()
        profilePickerOverlay.show(stepMessage(armedSession), armedSession.remainingArmedTime)
        armedAppPlaybackWatcher.start(armedSession.armedStreamingApp)
    }

    // TODO: Validate
    private fun showStep(armedSession: ProfilePickerSession, step: String) {
        armedSession.currentStep = step
        Log.i(PROFILE_PICKER_TAG, "${armedSession.streamingSiteName}: $step")
        if (waitingMessageSession === armedSession && armedSession.isArmed) {
            profilePickerOverlay.updateMessageIfShown(stepMessage(armedSession))
        }
    }

    // TODO: Validate
    private fun stepMessage(armedSession: ProfilePickerSession): String =
        "${armedSession.streamingSiteName}: ${armedSession.currentStep}… " +
            (if (armedSession.isInSkippableWait) profilePickerOverlay.skipInstruction("skip the wait") else "") +
            profilePickerOverlay.cancelInstruction

    // TODO: Validate
    private fun onArmedAppPlaybackStarted(playingPackageName: String) {
        val playingSession = profilePicker.armedSessionWatchingApp(playingPackageName) ?: return
        if (!playingSession.detectsScreensAndPlayback) return
        Log.i(PROFILE_PICKER_TAG, "${playingSession.streamingSiteName} started playing")
        showPlaybackDetectedAndDisarm(playingSession)
    }

    // TODO: Validate
    private fun showPlaybackDetectedAndDisarm(playingSession: ProfilePickerSession) {
        overlayShowsPlaybackDetectedMessage = true
        profilePickerOverlay.show("Video playback detected", PLAYBACK_DETECTED_MESSAGE_DURATION)
        profilePicker.disarm(playingSession)
    }

    // TODO: Validate
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString() ?: return
        val armedSession = profilePicker.armedSessionWatchingApp(eventPackageName) ?: return
        if (waitingMessageSession !== armedSession) onArmed(armedSession)
        when (val profileScreen = armedSession.profileScreen) {
            is P2RemoteButtonProfileScreen -> {
                if (armedSession.hasPickedProfile) return
                remoteButtonProfilePicker.startPickingProfile(armedSession, profileScreen, wasArmedAppRunningAtArm = false)
            }
            is P1AccessibilityClickedProfileScreen ->
                accessibilityClickedProfilePicker.onArmedAppWindowChanged(armedSession, profileScreen)
        }
    }

    // TODO: Validate
    override fun onKeyEvent(keyEvent: KeyEvent): Boolean = when {
        isOkKeyCode(keyEvent.keyCode) -> okKeyPressSkippingWait.onKeyEvent(
            keyEvent,
            { armedSessionOfShownWaitingMessage()?.takeIf { it.isInSkippableWait } },
            ::skipWaitBecauseViewerAsked,
        )
        isBackKeyCode(keyEvent.keyCode) -> backKeyPressCancellingWaiting.onKeyEvent(
            keyEvent,
            ::armedSessionOfShownWaitingMessage,
            ::disarmBecauseViewerCancelledWaiting,
        )
        else -> false
    }

    // TODO: Validate
    private fun skipWaitBecauseViewerAsked(skippableWaitSession: ProfilePickerSession) {
        Log.i(PROFILE_PICKER_TAG, "The viewer skipped the wait for ${skippableWaitSession.streamingSiteName}")
        skippableWaitSession.skipCurrentWait?.invoke()
    }

    // TODO: Validate
    private fun armedSessionOfShownWaitingMessage(): ProfilePickerSession? =
        waitingMessageSession?.takeIf { it.isArmed }

    // TODO: Validate
    private fun disarmBecauseViewerCancelledWaiting(armedSessionOfWaitingMessage: ProfilePickerSession) {
        Log.i(PROFILE_PICKER_TAG, "The viewer cancelled waiting for ${armedSessionOfWaitingMessage.streamingSiteName}")
        profilePicker.disarm(armedSessionOfWaitingMessage)
    }

    // TODO: Validate
    override fun onServiceConnected() {
        profilePicker.connectArmingListener(this)
    }

    // TODO: Validate
    override fun onServiceDestroyed() {
        profilePicker.disconnectArmingListener(this)
        mainHandler.removeCallbacksAndMessages(null)
        remoteButtonProfilePicker.shutDown()
        armedAppPlaybackWatcher.stop()
        profilePickerOverlay.hide()
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val PLAYBACK_DETECTED_MESSAGE_DURATION = 4_000L
    }
}
