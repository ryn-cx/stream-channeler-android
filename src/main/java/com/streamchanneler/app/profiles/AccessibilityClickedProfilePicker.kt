// TODO: Validate
package com.streamchanneler.app.profiles

import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.screenautomation.boundsOnScreen
import com.streamchanneler.app.screenautomation.clickableNodeCovering
import com.streamchanneler.app.screenautomation.flattenNodes

// TODO: Validate
class AccessibilityClickedProfilePicker(
    private val localAppStorage: LocalAppStorage,
    private val mainHandler: Handler,
    private val activeWindowRootNode: () -> AccessibilityNodeInfo?,
    private val showWaitingMessage: (ProfilePickerSession) -> Unit,
    private val showStep: (ProfilePickerSession, String) -> Unit,
) {
    // TODO: Validate
    private var isEventDetectionPosted = false

    // TODO: Validate
    fun onArmedAppWindowChanged(armedSession: ProfilePickerSession, profileScreen: P1AccessibilityClickedProfileScreen) {
        if (!armedSession.detectsScreensAndPlayback) return
        if (armedSession.isWindowPolled) return
        if (isEventDetectionPosted) return
        isEventDetectionPosted = true
        mainHandler.post {
            isEventDetectionPosted = false
            if (armedSession.detectsScreensAndPlayback) detectProfileOrPinScreen(armedSession, profileScreen)
        }
    }

    // TODO: Validate
    private fun detectProfileOrPinScreen(
        armedSession: ProfilePickerSession,
        profileScreen: P1AccessibilityClickedProfileScreen,
    ) {
        if (armedSession.isPinEntryScheduled.get()) return
        val savedProfile = localAppStorage.savedStreamingProfile(armedSession.armedStreamingApp)
        val windowNodes = flattenNodes(activeWindowRootNode() ?: return)
        if (!armedSession.hasPickedProfile && clickProfile(windowNodes, armedSession, profileScreen, savedProfile)) {
            waitForNextScreenAfterProfileClick(armedSession, profileScreen, waitsForPinScreen = savedProfile.entersPin)
            return
        }
        if (savedProfile.entersPin && pinKeyNodesOnScreen(windowNodes, armedSession).isNotEmpty()) {
            enterPinAfterPinScreenLoads(armedSession, savedProfile.pinToEnter)
            return
        }
        clickPlayButton(windowNodes, armedSession, profileScreen)
    }

    // TODO: Validate
    private fun waitForNextScreenAfterProfileClick(
        armedSession: ProfilePickerSession,
        profileScreen: P1AccessibilityClickedProfileScreen,
        waitsForPinScreen: Boolean,
    ) {
        armedSession.pauseDetection()
        armedSession.markProfilePicked()
        showStep(armedSession, profilePickedStep(waitsForPinScreen))
        if (!waitsForPinScreen) reopenLinkAfterDelay(armedSession)
        resumeDetectionAfterClick(armedSession) {
            if (waitsForPinScreen) {
                pollWindowWhileWaitingUntil(armedSession, profileScreen, PIN_SCREEN_POLL_INTERVAL) { armedSession.hasEnteredPin }
            } else if (profileScreen.playButtonOnScreen != null) {
                pollWindowWhileWaitingUntil(armedSession, profileScreen, PLAY_BUTTON_POLL_INTERVAL) {
                    armedSession.hasClickedPlayButton
                }
            }
        }
    }

    // TODO: Validate
    private fun clickPlayButton(
        windowNodes: List<AccessibilityNodeInfo>,
        armedSession: ProfilePickerSession,
        profileScreen: P1AccessibilityClickedProfileScreen,
    ) {
        if (armedSession.hasClickedPlayButton) return
        val playButtonNode = profileScreen.playButtonOnScreen?.playButtonNode(windowNodes) ?: return
        val playButtonClickTarget = clickableNodeCovering(playButtonNode, windowNodes) ?: return
        showStep(armedSession, "Video page detected. Pressing play")
        armedSession.markPlayButtonClicked()
        if (!playButtonClickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.w(PROFILE_PICKER_TAG, "${armedSession.streamingSiteName} didn't accept the play button click")
        }
    }

    // TODO: Validate
    private fun pollWindowWhileWaitingUntil(
        armedSession: ProfilePickerSession,
        profileScreen: P1AccessibilityClickedProfileScreen,
        pollInterval: Long,
        hasFinishedWaiting: () -> Boolean,
    ) {
        if (!armedSession.detectsScreensAndPlayback || hasFinishedWaiting()) {
            armedSession.isWindowPolled = false
            return
        }
        armedSession.isWindowPolled = true
        detectProfileOrPinScreen(armedSession, profileScreen)
        mainHandler.postDelayed(
            { pollWindowWhileWaitingUntil(armedSession, profileScreen, pollInterval, hasFinishedWaiting) },
            pollInterval,
        )
    }

    // TODO: Validate
    private fun resumeDetectionAfterClick(
        armedSession: ProfilePickerSession,
        onDetectionResumed: () -> Unit = {},
    ) {
        mainHandler.postDelayed(
            {
                if (armedSession.detectsScreensAndPlayback || !armedSession.isArmed) return@postDelayed
                showWaitingMessage(armedSession)
                onDetectionResumed()
            },
            DETECTION_PAUSE_AFTER_CLICK,
        )
    }

    // TODO: Validate
    private fun clickProfile(
        windowNodes: List<AccessibilityNodeInfo>,
        armedSession: ProfilePickerSession,
        profileScreen: P1AccessibilityClickedProfileScreen,
        savedProfile: SavedStreamingProfile,
    ): Boolean {
        val profileClickTarget = savedProfileClickTarget(windowNodes, profileScreen, savedProfile) ?: return false
        showStep(
            armedSession,
            when {
                savedProfile.name.isNotEmpty() -> "Profile screen detected. Picking ${savedProfile.name}"
                savedProfile.number > 0 -> "Profile screen detected. Picking profile ${savedProfile.number}"
                else -> "Profile screen detected. Picking the highlighted profile"
            },
        )
        val isProfileClickAccepted = profileClickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!isProfileClickAccepted) {
            Log.w(PROFILE_PICKER_TAG, "${armedSession.streamingSiteName} didn't accept the profile click")
        }
        return isProfileClickAccepted
    }

    // TODO: Validate
    private fun describedProfiles(
        windowNodes: List<AccessibilityNodeInfo>,
        profileScreen: P1AccessibilityClickedProfileScreen,
    ): List<DescribedProfile> =
        windowNodes
            .mapNotNull { windowNode ->
                val nodeDescription = windowNode.contentDescription?.toString() ?: return@mapNotNull null
                val describedProfileName = profileScreen.profileNameFromDescription.find(nodeDescription)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                clickableNodeCovering(windowNode, windowNodes)
                    ?.let { profileClickTarget -> DescribedProfile(profileClickTarget, describedProfileName.trim()) }
            }
            .distinctBy { describedProfile -> describedProfile.clickTarget }

    // TODO: Validate
    private fun savedProfileClickTarget(
        windowNodes: List<AccessibilityNodeInfo>,
        profileScreen: P1AccessibilityClickedProfileScreen,
        savedProfile: SavedStreamingProfile,
    ): AccessibilityNodeInfo? {
        val describedProfiles = describedProfiles(windowNodes, profileScreen)
        return when {
            savedProfile.name.isNotEmpty() -> describedProfiles
                .firstOrNull { describedProfile -> describedProfile.name.equals(savedProfile.name, ignoreCase = true) }
            savedProfile.number > 0 -> describedProfiles
                .sortedWith(
                    compareBy(
                        { describedProfile -> describedProfile.clickTarget.boundsOnScreen.top },
                        { describedProfile -> describedProfile.clickTarget.boundsOnScreen.left },
                    ),
                )
                .getOrNull(savedProfile.number - 1)
            else -> describedProfiles.firstOrNull { describedProfile -> describedProfile.clickTarget.isFocused }
        }?.clickTarget
    }

    // TODO: Validate
    private fun enterPin(
        windowNodes: List<AccessibilityNodeInfo>,
        armedSession: ProfilePickerSession,
        profilePin: String,
    ): Boolean {
        val pinKeyNodes = pinKeyNodesOnScreen(windowNodes, armedSession)
        if (pinKeyNodes.isEmpty()) return false
        val digitToKeyNode = pinKeyNodes.associateBy { it.text?.toString()?.trim() }
        val pinKeyClickTargets = profilePin.map { pinDigit ->
            clickableNodeCovering(digitToKeyNode.getValue(pinDigit.toString()), windowNodes)!!
        }
        showStep(armedSession, "Entering the PIN")
        armedSession.markPinEntered()
        armedSession.pauseDetection()
        pinKeyClickTargets.forEachIndexed { keyIndex, pinKeyClickTarget ->
            mainHandler.postDelayed(
                { pinKeyClickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) },
                keyIndex * DELAY_BETWEEN_PIN_CLICKS,
            )
        }
        return true
    }

    // TODO: Validate
    private fun enterPinAfterPinScreenLoads(armedSession: ProfilePickerSession, profilePin: String) {
        if (!armedSession.isPinEntryScheduled.compareAndSet(false, true)) return

        // TODO: Validate
        val enterPinRunnable = Runnable {
            armedSession.skipCurrentWait = null
            armedSession.isPinEntryScheduled.set(false)
            if (!armedSession.detectsScreensAndPlayback) return@Runnable
            val loadedPinScreenWindowNodes = flattenNodes(activeWindowRootNode() ?: return@Runnable)
            if (enterPin(loadedPinScreenWindowNodes, armedSession, profilePin)) {
                reopenLinkAfterDelay(armedSession)
                resumeDetectionAfterClick(armedSession)
            }
        }
        armedSession.skipCurrentWait = {
            mainHandler.removeCallbacks(enterPinRunnable)
            enterPinRunnable.run()
        }
        if (!armedSession.hasEnteredPin) showStep(armedSession, "PIN screen detected")
        mainHandler.postDelayed(enterPinRunnable, PIN_SCREEN_LOAD_TIME)
    }

    // TODO: Validate
    private fun reopenLinkAfterDelay(armedSession: ProfilePickerSession) {
        val reopenLinkAfterProfilePicked = armedSession.reopenLinkAfterProfilePicked ?: return
        mainHandler.postDelayed(
            {
                if (!armedSession.isArmed) return@postDelayed
                showStep(armedSession, "Reopening the video")
                reopenLinkAfterProfilePicked()
            },
            LINK_REOPEN_DELAY,
        )
    }

    // TODO: Validate
    private fun pinKeyNodesOnScreen(
        windowNodes: List<AccessibilityNodeInfo>,
        armedSession: ProfilePickerSession,
    ): List<AccessibilityNodeInfo> =
        (armedSession.armedStreamingApp.pinEntry as? P2PinKeysOnScreen)?.pinKeyNodes(windowNodes).orEmpty()

    // TODO: Validate
    private class DescribedProfile(val clickTarget: AccessibilityNodeInfo, val name: String)

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val DELAY_BETWEEN_PIN_CLICKS = 250L

        // TODO: Validate
        const val PIN_SCREEN_LOAD_TIME = 1_000L

        // TODO: Validate
        const val DETECTION_PAUSE_AFTER_CLICK = 2_000L

        // TODO: Validate
        const val PIN_SCREEN_POLL_INTERVAL = 100L

        // TODO: Validate
        const val PLAY_BUTTON_POLL_INTERVAL = 100L
    }
}
