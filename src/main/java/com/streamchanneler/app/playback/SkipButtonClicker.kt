// TODO: Validate
package com.streamchanneler.app.playback

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.streamchanneler.app.screenautomation.OnScreenElement
import com.streamchanneler.app.screenautomation.PressedWithOkButton
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.clickNodeOrClickableNodeCovering

// TODO: Validate
class SkipButtonClicker(private val skipButton: OnScreenElement) {
    // TODO: Validate
    private var lastClickedSkipButtonNode: AccessibilityNodeInfo? = null
    // TODO: Validate
    private var lastSkipButtonClickAt = 0L

    // TODO: Validate
    fun clickWhenShowing(packageName: String, now: Long): Boolean {
        val activeWindowRootNode = ScreenAutomationService.activeWindowRootNodeOfApp(packageName) ?: return false
        val showingSkipButtonNode = skipButton.visibleNodeUnderWindowRoot(activeWindowRootNode) ?: return false
        val isRepeatClickTooSoon =
            showingSkipButtonNode == lastClickedSkipButtonNode && now - lastSkipButtonClickAt < REPEAT_CLICK_DELAY
        if (isRepeatClickTooSoon) return false
        lastClickedSkipButtonNode = showingSkipButtonNode
        lastSkipButtonClickAt = now
        if (skipButton is PressedWithOkButton) {
            Thread { RemoteButtons.press(listOf(KeyEvent.KEYCODE_DPAD_CENTER), 0L) }.start()
            return true
        }
        return clickNodeOrClickableNodeCovering(showingSkipButtonNode, activeWindowRootNode)
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val REPEAT_CLICK_DELAY = 5_000L
    }
}
