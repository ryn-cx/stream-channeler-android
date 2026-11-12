// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

// TODO: Validate
val AccessibilityNodeInfo.childNodes: Sequence<AccessibilityNodeInfo>
    get() = (0 until childCount).asSequence().mapNotNull { childIndex -> getChild(childIndex) }

// TODO: Validate
val AccessibilityNodeInfo.boundsOnScreen: Rect
    get() = Rect().also { boundsOnScreen -> getBoundsInScreen(boundsOnScreen) }

// TODO: Validate
fun flattenNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
    val flattenedNodes = mutableListOf<AccessibilityNodeInfo>()
    val nodesToVisit = ArrayDeque(listOf(rootNode))
    while (nodesToVisit.isNotEmpty()) {
        val visitedNode = nodesToVisit.removeFirst()
        flattenedNodes.add(visitedNode)
        nodesToVisit.addAll(visitedNode.childNodes)
    }
    return flattenedNodes
}

// TODO: Validate
fun clickableNodeCovering(
    targetNode: AccessibilityNodeInfo,
    windowNodes: List<AccessibilityNodeInfo>,
): AccessibilityNodeInfo? {
    if (targetNode.isClickable) return targetNode
    val targetBounds = targetNode.boundsOnScreen
    return windowNodes
        .filter { it.isClickable }
        .map { clickableNode -> clickableNode to clickableNode.boundsOnScreen }
        .filter { (_, clickableBounds) -> clickableBounds.contains(targetBounds.centerX(), targetBounds.centerY()) }
        .minByOrNull { (_, clickableBounds) -> clickableBounds.width().toLong() * clickableBounds.height() }
        ?.first
}

// TODO: Validate
fun clickNodeOrClickableNodeCovering(targetNode: AccessibilityNodeInfo, windowRootNode: AccessibilityNodeInfo): Boolean =
    clickableNodeCovering(targetNode, flattenNodes(windowRootNode))?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
