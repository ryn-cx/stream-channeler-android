// TODO: Validate
package com.streamchanneler.app.ui.common

import android.content.Context
import com.streamchanneler.app.playback.ForegroundReturner
import com.streamchanneler.app.playback.OverlayTestResult
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.playback.PlaybackWatcherService
import com.streamchanneler.app.screenautomation.ScreenAutomationService

// TODO: Validate
enum class AppPermission(val storageKey: String, val displayName: String, val purpose: String) {
    END_DETECTION("endDetection", "Notification access", "detects when a video ends"),
    RETURN_TO_GUIDE("returnToGuide", "Display over other apps", "brings the guide back when a video ends"),
    PROFILE_PICKER("profilePicker", "Accessibility", "picks streaming profiles and clicks skip buttons"),
}

// TODO: Validate
enum class PermissionState {
    WORKING,
    TURNED_OFF,

    ALLOWED_BUT_NOT_WORKING,
}

// TODO: Validate
fun currentPermissionStates(context: Context, overlayTestResult: OverlayTestResult?): Map<AppPermission, PermissionState> =
    mapOf(
        AppPermission.END_DETECTION to permissionStateFromAllowedAndWorking(
            isAllowed = PlaybackMonitor.hasNotificationListenerAccess(context),
            isWorking = PlaybackWatcherService.isListenerConnected,
        ),
        AppPermission.RETURN_TO_GUIDE to permissionStateFromAllowedAndWorking(
            isAllowed = ForegroundReturner.canReturnFromBackground(context),
            isWorking = overlayTestResult !is OverlayTestResult.Broken,
        ),
        AppPermission.PROFILE_PICKER to permissionStateFromAllowedAndWorking(
            isAllowed = ScreenAutomationService.isEnabled(context),
            isWorking = ScreenAutomationService.connectedService != null,
        ),
    )

// TODO: Validate
private fun permissionStateFromAllowedAndWorking(isAllowed: Boolean, isWorking: Boolean): PermissionState = when {
    !isAllowed -> PermissionState.TURNED_OFF
    isWorking -> PermissionState.WORKING
    else -> PermissionState.ALLOWED_BUT_NOT_WORKING
}
