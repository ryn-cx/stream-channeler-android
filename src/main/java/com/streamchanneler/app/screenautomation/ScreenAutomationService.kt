// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.streamchanneler.app.streamChannelerApp

// TODO: Validate
class ScreenAutomationService : AccessibilityService() {
    // TODO: Validate
    val screenshots by lazy { AccessibilityScreenshots(this) }
    // TODO: Validate
    val screenTaps by lazy { ScreenTaps(this) }
    // TODO: Validate
    val debugLogger by lazy { DebugLogger(this, streamChannelerApp.mediaSessions) }

    // TODO: Validate
    @Volatile
    private var installedFeatures: List<ScreenAutomationFeature> = emptyList()
    // TODO: Validate
    private var isFilteringKeyEvents = false

    // TODO: Validate
    override fun onServiceConnected() {
        super.onServiceConnected()
        installedFeatures = streamChannelerApp.createScreenAutomationFeatures(this)
        installedFeatures.forEach { installedFeature -> installedFeature.onServiceConnected() }
        connectedService = this
        debugLogger.start()
    }

    // TODO: Validate
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        installedFeatures.forEach { installedFeature -> installedFeature.onAccessibilityEvent(event) }
        debugLogger.onAccessibilityEvent(event)
    }

    // TODO: Validate
    override fun onInterrupt() = Unit

    // TODO: Validate
    override fun onKeyEvent(event: KeyEvent): Boolean =
        installedFeatures.any { installedFeature -> installedFeature.onKeyEvent(event) }

    // TODO: Validate
    @Synchronized
    fun updateKeyEventFiltering() {
        val filtersKeyEvents = installedFeatures.any { installedFeature -> installedFeature.filtersKeyEvents }
        if (filtersKeyEvents == isFilteringKeyEvents) return
        val updatedServiceInfo = serviceInfo ?: return
        isFilteringKeyEvents = filtersKeyEvents
        updatedServiceInfo.flags = if (filtersKeyEvents) {
            updatedServiceInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            updatedServiceInfo.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = updatedServiceInfo
    }

    // TODO: Validate
    override fun onDestroy() {
        debugLogger.stop()
        installedFeatures.forEach { installedFeature -> installedFeature.onServiceDestroyed() }
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        @Volatile
        var connectedService: ScreenAutomationService? = null
            private set

        // TODO: Validate
        val foregroundPackageName: String?
            get() = connectedService?.rootInActiveWindow?.packageName?.toString()

        // TODO: Validate
        fun activeWindowRootNodeOfApp(packageName: String): AccessibilityNodeInfo? =
            connectedService?.rootInActiveWindow?.takeIf { activeWindowRootNode ->
                activeWindowRootNode.packageName?.toString() == packageName
            }

        // TODO: Validate
        private fun componentName(context: Context) = ComponentName(context, ScreenAutomationService::class.java)

        // TODO: Validate
        private fun enabledAccessibilityServiceNames(context: Context): List<String> =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .split(':')

        // TODO: Validate
        fun otherEnabledAccessibilityServiceNames(context: Context): List<String> {
            val screenAutomationServiceName = componentName(context).flattenToString()
            return enabledAccessibilityServiceNames(context).filter { it.isNotBlank() && it != screenAutomationServiceName }
        }

        // TODO: Validate
        fun isEnabled(context: Context): Boolean {
            val screenAutomationComponent = componentName(context)
            return enabledAccessibilityServiceNames(context).any { enabledServiceName ->
                ComponentName.unflattenFromString(enabledServiceName) == screenAutomationComponent
            }
        }

        // TODO: Validate
        fun enableCommand(context: Context): String =
            "adb shell settings put secure enabled_accessibility_services " +
                (otherEnabledAccessibilityServiceNames(context) + componentName(context).flattenToString()).joinToString(":")
    }
}
