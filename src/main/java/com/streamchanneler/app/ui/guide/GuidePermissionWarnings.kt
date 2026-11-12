// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.app.AlertDialog
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.streamchanneler.app.playback.ForegroundReturner
import com.streamchanneler.app.playback.OverlayTestResult
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.PermissionState
import com.streamchanneler.app.ui.common.currentPermissionStates

// TODO: Validate
internal class GuidePermissionWarnings(
    private val activity: ComponentActivity,
    private val mainHandler: Handler,
    private val permissionWarningText: TextView,
    private val openSettings: () -> Unit,
) {
    // TODO: Validate
    private var showBrokenPermissionPopupRunnable = Runnable {}
    // TODO: Validate
    private var brokenPermissionDialog: AlertDialog? = null

    // TODO: Validate
    fun renderPermissionWarnings() {
        val permissionWarnings = buildList {
            if (!PlaybackMonitor.hasNotificationListenerAccess(activity)) {
                add("Video ends can't be detected until notification access is allowed in Settings.")
            }
            if (!ForegroundReturner.canReturnFromBackground(activity)) {
                add("Allow \"Display over other apps\" in Settings so the guide can come back when a video ends.")
            }
            if (ForegroundReturner.lastOverlayTestResult is OverlayTestResult.Broken &&
                ForegroundReturner.canReturnFromBackground(activity)
            ) {
                add(
                    "\"Display over other apps\" is allowed but not working, so the guide can't come back when a " +
                        "video ends. Turn it off and on again in Settings.",
                )
            }
        }
        permissionWarningText.text = permissionWarnings.joinToString("\n")
        permissionWarningText.isVisible = permissionWarnings.isNotEmpty()
    }

    // TODO: Validate
    fun testPermissionsAndShowBrokenWarnings() {
        ForegroundReturner.testOverlayWindow(activity) { overlayTestResult ->
            if (activity.isDestroyed) return@testOverlayWindow
            renderPermissionWarnings()
            val processAge = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            mainHandler.removeCallbacks(showBrokenPermissionPopupRunnable)
            showBrokenPermissionPopupRunnable = Runnable { showBrokenPermissionPopupIfNeeded(overlayTestResult) }
            mainHandler.postDelayed(showBrokenPermissionPopupRunnable, (SERVICE_CONNECTION_GRACE_PERIOD - processAge).coerceAtLeast(0))
        }
    }

    // TODO: Validate
    private fun showBrokenPermissionPopupIfNeeded(overlayTestResult: OverlayTestResult) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val localAppStorage = activity.streamChannelerApp.localAppStorage
        val permissionStates = currentPermissionStates(activity, overlayTestResult)
        val allowedPermissionKeys = permissionStates.filterValues { it != PermissionState.TURNED_OFF }.keys.map { it.storageKey }
        localAppStorage.permissionsSeenAllowed += allowedPermissionKeys
        val permissionsSeenAllowed = localAppStorage.permissionsSeenAllowed
        val brokenPermissionStates = permissionStates.filter { (appPermission, permissionState) ->
            permissionState == PermissionState.ALLOWED_BUT_NOT_WORKING ||
                (permissionState == PermissionState.TURNED_OFF && appPermission.storageKey in permissionsSeenAllowed)
        }
        brokenPermissionDialog?.dismiss()
        brokenPermissionDialog = null
        if (brokenPermissionStates.isEmpty()) return

        val brokenPermissionLines = brokenPermissionStates.map { (appPermission, permissionState) ->
            val problem = when (permissionState) {
                PermissionState.TURNED_OFF -> "was turned off"
                else -> "is on but not working. Turn it off and on again"
            }
            "• ${appPermission.displayName} (${appPermission.purpose}) $problem."
        }
        val turnedOffPermissions = brokenPermissionStates.filterValues { it == PermissionState.TURNED_OFF }.keys
        val brokenPermissionDialogBuilder = AlertDialog.Builder(activity)
            .setTitle("Permissions need fixing")
            .setMessage(brokenPermissionLines.joinToString("\n"))
            .setPositiveButton("Open Settings") { _, _ -> openSettings() }
            .setNegativeButton("Not now", null)
        if (turnedOffPermissions.isNotEmpty()) {
            brokenPermissionDialogBuilder.setNeutralButton("I turned it off") { _, _ ->
                localAppStorage.permissionsSeenAllowed -= turnedOffPermissions.map { it.storageKey }.toSet()
            }
        }
        brokenPermissionDialog = brokenPermissionDialogBuilder.show()
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val SERVICE_CONNECTION_GRACE_PERIOD = 5_000L
    }
}
