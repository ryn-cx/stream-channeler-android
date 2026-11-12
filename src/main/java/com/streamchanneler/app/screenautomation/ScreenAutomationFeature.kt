// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

// TODO: Validate
interface ScreenAutomationFeature {
    // TODO: Validate
    val filtersKeyEvents: Boolean
        get() = false

    // TODO: Validate
    fun onServiceConnected() = Unit

    // TODO: Validate
    fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    // TODO: Validate
    fun onKeyEvent(keyEvent: KeyEvent): Boolean = false

    // TODO: Validate
    fun onServiceDestroyed() = Unit
}
