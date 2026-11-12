// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import com.streamchanneler.app.BuildConfig
import com.streamchanneler.app.playback.ForegroundReturner
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.StreamingApps
import com.streamchanneler.app.ui.common.dp

// TODO: Validate
internal fun Activity.showDebugInformationDialog() {
    val debugInformationPages = debugInformationPages()
    var shownDebugInformationPageIndex = 0
    val debugInformationPageText = TextView(this).apply {
        textSize = 15f
        setPadding(dp(24), dp(8), dp(24), 0)
    }
    val debugInformationDialog = AlertDialog.Builder(this)
        .setTitle("Debug information")
        .setView(ScrollView(this).apply { addView(debugInformationPageText) })
        .setNeutralButton("Previous page", null)
        .setPositiveButton("Next page", null)
        .setNegativeButton("Close", null)
        .create()

    // TODO: Validate
    fun showDebugInformationPage(debugInformationPageIndex: Int) {
        shownDebugInformationPageIndex = debugInformationPageIndex
        debugInformationDialog.setTitle(
            "Debug information, page ${debugInformationPageIndex + 1} of ${debugInformationPages.size}",
        )
        debugInformationPageText.text = debugInformationPages[debugInformationPageIndex]
        val previousPageButton = debugInformationDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val nextPageButton = debugInformationDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        previousPageButton.isEnabled = debugInformationPageIndex > 0
        nextPageButton.isEnabled = debugInformationPageIndex < debugInformationPages.lastIndex
        if (!nextPageButton.isEnabled && nextPageButton.hasFocus()) previousPageButton.requestFocus()
        if (!previousPageButton.isEnabled && previousPageButton.hasFocus()) nextPageButton.requestFocus()
    }

    debugInformationDialog.setOnShowListener {
        debugInformationDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            showDebugInformationPage(shownDebugInformationPageIndex - 1)
        }
        debugInformationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            showDebugInformationPage(shownDebugInformationPageIndex + 1)
        }
        showDebugInformationPage(0)
        debugInformationDialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
    }
    debugInformationDialog.show()
}

// TODO: Validate
private fun Context.debugInformationPages(): List<String> {
    val displayMetrics = resources.displayMetrics
    val deviceAndPermissionsPage = buildString {
        appendLine("App: $packageName ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}")
        appendLine("Device name: ${Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: "Unknown"}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
        appendLine("Screen: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, ${displayMetrics.densityDpi} dpi")
        appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("Notification access: ${PlaybackMonitor.hasNotificationListenerAccess(this@debugInformationPages)}")
        appendLine("Display over other apps: ${ForegroundReturner.canReturnFromBackground(this@debugInformationPages)}")
        appendLine("Overlay test: ${ForegroundReturner.lastOverlayTestResult?.description ?: "Not run yet"}")
        appendLine("Accessibility profile picker: ${ScreenAutomationService.isEnabled(this@debugInformationPages)}")
        appendLine("Remote buttons without Shizuku: ${RemoteButtons.pressesDpadWithoutShizuku}")
        appendLine("Shizuku remote buttons: ${RemoteButtons.shizukuAccess()}")
    }.trimEnd()
    val installedStreamingAppLines = StreamingApps.all.distinctBy { it.packageName }.map { streamingApp -> installedStreamingAppLine(streamingApp) }
    val streamingAppsPage = (listOf("Streaming apps:") + installedStreamingAppLines).joinToString("\n")
    return listOf(deviceAndPermissionsPage, streamingAppsPage)
}

// TODO: Validate
private fun Context.installedStreamingAppLine(streamingApp: StreamingApp): String {
    val installedStreamingAppVersion = try {
        val streamingAppPackageInfo = packageManager.getPackageInfo(streamingApp.packageName, 0)
        val streamingAppVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            streamingAppPackageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            streamingAppPackageInfo.versionCode.toLong()
        }
        "${streamingAppPackageInfo.versionName} ($streamingAppVersionCode)"
    } catch (_: PackageManager.NameNotFoundException) {
        "Not installed"
    }
    return "${streamingApp.site.pluginKey} (${streamingApp.packageName}): $installedStreamingAppVersion"
}
