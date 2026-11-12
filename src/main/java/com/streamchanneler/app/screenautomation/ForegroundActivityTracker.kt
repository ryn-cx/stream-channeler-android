// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent

// TODO: Validate
class ForegroundActivityTracker(private val packageManager: PackageManager) : ScreenAutomationFeature {
    // TODO: Validate
    @Volatile
    private var activityClassNameByPackageName: Map<String, String> = emptyMap()

    // TODO: Validate
    fun lastActivityClassName(packageName: String): String? = activityClassNameByPackageName[packageName]

    // TODO: Validate
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPackageName = event.packageName?.toString() ?: return
        val eventClassName = event.className?.toString() ?: return
        if (activityClassNameByPackageName[eventPackageName] == eventClassName) return
        if (!isActivity(eventPackageName, eventClassName)) return
        activityClassNameByPackageName = activityClassNameByPackageName + (eventPackageName to eventClassName)
    }

    // TODO: Validate
    private fun isActivity(packageName: String, className: String): Boolean = try {
        packageManager.getActivityInfo(ComponentName(packageName, className), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
