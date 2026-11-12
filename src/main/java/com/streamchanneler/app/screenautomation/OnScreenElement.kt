// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.view.accessibility.AccessibilityNodeInfo

// TODO: Validate
sealed interface PlayerButton

// TODO: Validate
fun interface OnScreenElement : PlayerButton {
    // TODO: Validate
    fun visibleNodeUnderWindowRoot(windowRootNode: AccessibilityNodeInfo): AccessibilityNodeInfo?
}

// TODO: Validate
class ViewIdOnScreen(private val viewId: String) : OnScreenElement {
    // TODO: Validate
    override fun visibleNodeUnderWindowRoot(windowRootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        firstVisibleNodeMatching(windowRootNode) { node -> node.viewIdResourceName == viewId }
}

// TODO: Validate
class ViewIdAndTextOnScreen(private val viewId: String, private val viewText: String) : OnScreenElement {
    // TODO: Validate
    override fun visibleNodeUnderWindowRoot(windowRootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        firstVisibleNodeMatching(windowRootNode) { node ->
            node.viewIdResourceName == viewId && node.text?.toString().equals(viewText, ignoreCase = true)
        }
}

// TODO: Validate
class PressedWithOkButton(shownElement: OnScreenElement) : OnScreenElement by shownElement

// TODO: Validate
class ContentDescriptionOnScreen(private val contentDescriptionPattern: Regex) : OnScreenElement {
    // TODO: Validate
    override fun visibleNodeUnderWindowRoot(windowRootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        firstVisibleNodeMatching(windowRootNode) { node ->
            node.contentDescription?.let(contentDescriptionPattern::containsMatchIn) == true
        }
}

// TODO: Validate
fun findOnScreenElementInActiveWindow(packageName: String, onScreenElement: OnScreenElement): AccessibilityNodeInfo? =
    ScreenAutomationService.activeWindowRootNodeOfApp(packageName)?.let(onScreenElement::visibleNodeUnderWindowRoot)

// TODO: Validate
private fun firstVisibleNodeMatching(
    node: AccessibilityNodeInfo,
    isWantedNode: (AccessibilityNodeInfo) -> Boolean,
): AccessibilityNodeInfo? {
    if (node.isVisibleToUser && isWantedNode(node)) return node
    return node.childNodes.firstNotNullOfOrNull { childNode -> firstVisibleNodeMatching(childNode, isWantedNode) }
}
