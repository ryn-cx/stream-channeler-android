// TODO: Validate
package com.streamchanneler.app.profiles

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.screenautomation.AccessibilityScreenshots
import com.streamchanneler.app.screenautomation.MINIMUM_SCREENSHOT_INTERVAL
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenSignature
import com.streamchanneler.app.screenautomation.ScreenTaps
import com.streamchanneler.app.screenautomation.ScreenTextReader
import java.util.concurrent.Executors

// TODO: Validate
class RemoteButtonProfilePicker(
    private val localAppStorage: LocalAppStorage,
    private val mediaSessions: MediaSessions,
    private val screenshots: AccessibilityScreenshots,
    private val screenTaps: ScreenTaps,
    private val showWaitingMessage: (ProfilePickerSession) -> Unit,
    private val showPlaybackDetectedAndDisarm: (ProfilePickerSession) -> Unit,
    private val showStep: (ProfilePickerSession, String) -> Unit,
) {
    // TODO: Validate
    private val remoteButtonExecutor = Executors.newSingleThreadExecutor()
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val screenTextReader = ScreenTextReader()

    // TODO: Validate
    fun startPickingProfile(
        armedSession: ProfilePickerSession,
        profileScreen: P2RemoteButtonProfileScreen,
        wasArmedAppRunningAtArm: Boolean,
    ) {
        if (!armedSession.isPressingRemoteButtons.compareAndSet(false, true)) return
        remoteButtonExecutor.execute {
            try {
                pickProfileAndEnterPin(armedSession, profileScreen, wasArmedAppRunningAtArm)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                armedSession.isPressingRemoteButtons.set(false)
                if (armedSession.isArmed) showWaitingMessage(armedSession)
            }
        }
    }

    // TODO: Validate
    fun shutDown() {
        remoteButtonExecutor.shutdownNow()
        screenTextReader.close()
    }

    // TODO: Validate
    private fun pickProfileAndEnterPin(
        armedSession: ProfilePickerSession,
        profileScreen: P2RemoteButtonProfileScreen,
        wasArmedAppRunningAtArm: Boolean,
    ) {
        val streamingApp = armedSession.armedStreamingApp
        val savedProfile = localAppStorage.savedStreamingProfile(streamingApp)
        val profileScreenSignature = profileScreen.profileScreenSignature
        val pinScreenSignature = (streamingApp.pinEntry as? P3RemoteButtonPinEntry)?.pinScreenSignature
            ?.takeIf { savedProfile.entersPin }
        val isProfileScreenShowing = if (profileScreenSignature != null) {
            waitForProfileScreenInScreenshots(armedSession, profileScreenSignature, pinScreenSignature, savedProfile.pinToEnter)
        } else {
            waitForProfileScreenWithoutSeeingIt(armedSession, wasArmedAppRunningAtArm)
        }
        if (!isProfileScreenShowing || !armedSession.isArmed) return
        if (mediaSessions.isPackagePlaying(streamingApp.packageName)) {
            showPlaybackDetectedAndDisarm(armedSession)
            return
        }
        val wasProfileNameTapped = profileScreenSignature != null && savedProfile.name.isNotEmpty() &&
            tapProfileName(armedSession, profileScreenSignature, savedProfile.name)
        if (!armedSession.isArmed) return
        if (!wasProfileNameTapped && !pressProfileChoiceKeys(armedSession, profileScreen.profileList, savedProfile.number)) return
        armedSession.markProfilePicked()
        showStep(armedSession, profilePickedStep(savedProfile.entersPin))
        if (!savedProfile.entersPin) {
            reopenLinkUnlessPlaying(armedSession)
            return
        }
        if (!armedSession.isArmed) return
        showWaitingMessage(armedSession)
        if (pinScreenSignature != null) {
            waitForFirstScreen(armedSession, listOf(pinScreenSignature)) ?: return
            showStep(armedSession, "PIN screen detected")
        } else {
            val blindPinScreenAt = SystemClock.elapsedRealtime() + BLIND_PIN_SCREEN_WAIT
            if (!waitUntilOrSkipped(armedSession, profilePickedStep(waitsForPinScreen = true), blindPinScreenAt)) return
        }
        armedSession.pauseDetection()
        enterPinWithRemoteButtons(armedSession, savedProfile.pinToEnter)
        reopenLinkUnlessPlaying(armedSession)
    }

    // TODO: Validate
    private fun waitForProfileScreenInScreenshots(
        armedSession: ProfilePickerSession,
        profileScreenSignature: ScreenSignature,
        pinScreenSignature: ScreenSignature?,
        profilePin: String,
    ): Boolean {
        showStep(armedSession, "Waiting for the profile screen")
        val firstShownScreenSignature =
            waitForFirstScreen(armedSession, listOfNotNull(profileScreenSignature, pinScreenSignature)) ?: return false
        armedSession.pauseDetection()
        if (firstShownScreenSignature === pinScreenSignature) {
            showStep(armedSession, "PIN screen detected without a profile screen")
            enterPinWithRemoteButtons(armedSession, profilePin)
            return false
        }
        val profileScreenLoadedAt = SystemClock.elapsedRealtime() + PROFILE_SCREEN_LOAD_TIME
        return waitUntilOrSkipped(armedSession, "Profile screen detected", profileScreenLoadedAt)
    }

    // TODO: Validate
    private fun waitForProfileScreenWithoutSeeingIt(armedSession: ProfilePickerSession, wasArmedAppRunningAtArm: Boolean): Boolean {
        val blindProfileScreenWait =
            if (wasArmedAppRunningAtArm) BLIND_PROFILE_SCREEN_WAIT_WHILE_RUNNING else BLIND_PROFILE_SCREEN_WAIT
        val blindProfilePickAt = armedSession.armedAt + blindProfileScreenWait
        val blindWaitStep = "Waiting ${blindProfileScreenWait / 1000} seconds for the profile screen, which can't be seen"
        if (!waitUntilOrSkipped(armedSession, blindWaitStep, blindProfilePickAt)) return false
        armedSession.pauseDetection()
        return true
    }

    // TODO: Validate
    private fun tapProfileName(
        armedSession: ProfilePickerSession,
        profileScreenSignature: ScreenSignature,
        wantedProfileName: String,
    ): Boolean {
        val streamingSiteName = armedSession.streamingSiteName
        showStep(armedSession, "Looking for $wantedProfileName on the screen")
        val profileScreenPixels = screenshots.takeScreenshotNow() ?: return false
        val profileNameCenter = screenTextReader.centerOfText(profileScreenPixels, wantedProfileName)
        if (profileNameCenter == null) {
            Log.i(PROFILE_PICKER_TAG, "Couldn't find $wantedProfileName on the $streamingSiteName profile screen")
            return false
        }
        if (!armedSession.isArmed) return false
        showStep(armedSession, "Picking $wantedProfileName")
        Log.i(PROFILE_PICKER_TAG, "Tapping $wantedProfileName on the $streamingSiteName profile screen")
        if (!screenTaps.tap(profileNameCenter, profileScreenPixels)) return false
        Thread.sleep(PROFILE_TAP_CHECK_DELAY)
        val screenPixelsAfterTap = screenshots.takeScreenshotNow()
        if (screenPixelsAfterTap != null && profileScreenSignature.isShownInScreenshot(screenPixelsAfterTap)) {
            Log.w(PROFILE_PICKER_TAG, "$streamingSiteName still shows its profile screen after the tap, so remote buttons are used instead")
            return false
        }
        return true
    }

    // TODO: Validate
    private fun pressProfileChoiceKeys(
        armedSession: ProfilePickerSession,
        profileList: ProfileList?,
        wantedProfileNumber: Int,
    ): Boolean {
        val streamingSiteName = armedSession.streamingSiteName
        val profileChoiceKeyCodes = if (profileList != null && wantedProfileNumber > 0) {
            showStep(armedSession, "Picking profile $wantedProfileNumber")
            Log.i(PROFILE_PICKER_TAG, "Moving to $streamingSiteName profile number $wantedProfileNumber and pressing Select")
            profileList.keyCodesToPick(wantedProfileNumber)
        } else {
            showStep(armedSession, "Picking the highlighted profile")
            Log.i(PROFILE_PICKER_TAG, "Pressing Select on the highlighted $streamingSiteName profile")
            listOf(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        return RemoteButtons.press(profileChoiceKeyCodes, DELAY_BETWEEN_PROFILE_MOVES)
    }

    // TODO: Validate
    private fun reopenLinkUnlessPlaying(armedSession: ProfilePickerSession) {
        val reopenLinkAfterProfilePicked = armedSession.reopenLinkAfterProfilePicked ?: return
        Thread.sleep(LINK_REOPEN_DELAY)
        if (!armedSession.isArmed || mediaSessions.isPackagePlaying(armedSession.armedStreamingApp.packageName)) return
        showStep(armedSession, "Reopening the video")
        mainHandler.post(reopenLinkAfterProfilePicked)
    }

    // TODO: Validate
    private fun enterPinWithRemoteButtons(armedSession: ProfilePickerSession, profilePin: String) {
        if (mediaSessions.isPackagePlaying(armedSession.armedStreamingApp.packageName)) {
            showPlaybackDetectedAndDisarm(armedSession)
            return
        }
        val pinEntry = armedSession.armedStreamingApp.pinEntry as? P3RemoteButtonPinEntry ?: return
        showStep(armedSession, "Entering the PIN")
        armedSession.markPinEntered()
        RemoteButtons.pressPinKeys(pinEntryKeyCodes(pinEntry, profilePin))
    }

    // TODO: Validate
    private fun waitUntilOrSkipped(armedSession: ProfilePickerSession, waitStep: String, waitEndsAt: Long): Boolean {
        armedSession.isWaitSkipRequested = false
        armedSession.skipCurrentWait = { armedSession.isWaitSkipRequested = true }
        try {
            showStep(armedSession, waitStep)
            while (armedSession.isArmed) {
                val timeUntilWaitEnds = waitEndsAt - SystemClock.elapsedRealtime()
                if (timeUntilWaitEnds <= 0 || armedSession.isWaitSkipRequested) return true
                Thread.sleep(timeUntilWaitEnds.coerceAtMost(SKIPPABLE_WAIT_CHECK_INTERVAL))
            }
            return false
        } finally {
            armedSession.skipCurrentWait = null
        }
    }

    // TODO: Validate
    private fun pinEntryKeyCodes(pinEntry: P3RemoteButtonPinEntry, profilePin: String): List<Int> {
        val pinKeypad = pinEntry.pinKeypad
        return if (pinKeypad != null && (RemoteButtons.pressesDpadWithoutShizuku || !pinEntry.typesPinWithNumberKeys)) {
            pinKeypad.dpadKeyCodesTypingPin(profilePin)
        } else {
            profilePin.map { pinDigit -> KeyEvent.KEYCODE_0 + pinDigit.digitToInt() }
        }
    }

    // TODO: Validate
    private fun waitForFirstScreen(
        armedSession: ProfilePickerSession,
        screenSignatures: List<ScreenSignature>,
    ): ScreenSignature? {
        while (armedSession.detectsScreensAndPlayback) {
            val screenPixels = screenshots.takeScreenshotNow()
            if (screenPixels != null) {
                screenSignatures.firstOrNull { screenSignature -> screenSignature.isShownInScreenshot(screenPixels) }?.let { return it }
            }
            Thread.sleep(MINIMUM_SCREENSHOT_INTERVAL)
        }
        return null
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val PROFILE_SCREEN_LOAD_TIME = 2_000L
        // TODO: Validate
        const val BLIND_PROFILE_SCREEN_WAIT = 20_000L

        // TODO: Validate
        const val BLIND_PROFILE_SCREEN_WAIT_WHILE_RUNNING = 5_000L

        // TODO: Validate
        const val BLIND_PIN_SCREEN_WAIT = 1_000L

        // TODO: Validate
        const val SKIPPABLE_WAIT_CHECK_INTERVAL = 100L

        // TODO: Validate
        const val PROFILE_TAP_CHECK_DELAY = 3_000L

        // TODO: Validate
        const val DELAY_BETWEEN_PROFILE_MOVES = 250L
    }
}
