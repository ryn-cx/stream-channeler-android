// TODO: Validate
package com.streamchanneler.app.playback

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.streamchanneler.app.screenautomation.OnScreenElement
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.clickNodeOrClickableNodeCovering
import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
class NextEpisodeAutoplayCanceller(
    private val nextEpisodeCard: OnScreenElement,
    private val autoplayCancelButtons: List<OnScreenElement>,
    private val autoplayCancelKeyCodes: List<Int>,
) {
    // TODO: Validate
    private var isShowingNextEpisodeCardAutoplayCancelled = false

    // TODO: Validate
    fun cancelWhenShowing(packageName: String): Boolean {
        val nextEpisodeCardWindowRootNode = ScreenAutomationService.activeWindowRootNodeOfApp(packageName)
            ?.takeIf { activeWindowRootNode -> nextEpisodeCard.visibleNodeUnderWindowRoot(activeWindowRootNode) != null }
        if (nextEpisodeCardWindowRootNode == null) {
            isShowingNextEpisodeCardAutoplayCancelled = false
            return false
        }
        if (isShowingNextEpisodeCardAutoplayCancelled) return false
        if (clickAutoplayCancelButton(nextEpisodeCardWindowRootNode)) {
            isShowingNextEpisodeCardAutoplayCancelled = true
            Log.i(TAG, "Clicked the next episode autoplay cancel button in $packageName")
            return true
        }
        if (autoplayCancelKeyCodes.isEmpty()) return false
        isShowingNextEpisodeCardAutoplayCancelled = true
        Log.i(TAG, "Cancelling the next episode autoplay in $packageName")
        Thread { RemoteButtons.press(autoplayCancelKeyCodes, DELAY_BETWEEN_CANCEL_KEY_PRESSES) }.start()
        return true
    }

    // TODO: Validate
    private fun clickAutoplayCancelButton(windowRootNode: AccessibilityNodeInfo): Boolean {
        val showingAutoplayCancelButtonNode = autoplayCancelButtons.firstNotNullOfOrNull { autoplayCancelButton ->
            autoplayCancelButton.visibleNodeUnderWindowRoot(windowRootNode)
        } ?: return false
        return clickNodeOrClickableNodeCovering(showingAutoplayCancelButtonNode, windowRootNode)
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val TAG = "PlaybackMonitor"

        // TODO: Validate
        private const val DELAY_BETWEEN_CANCEL_KEY_PRESSES = 300L

        // TODO: Validate
        fun ofStreamingApp(streamingApp: StreamingApp): NextEpisodeAutoplayCanceller? {
            val nextEpisodeCard = streamingApp.nextEpisodeCard ?: return null
            if (nextEpisodeCard.autoplayCancelButtons.isEmpty() && nextEpisodeCard.autoplayCancelKeyCodes.isEmpty()) return null
            return NextEpisodeAutoplayCanceller(
                nextEpisodeCard.shownElement,
                nextEpisodeCard.autoplayCancelButtons,
                nextEpisodeCard.autoplayCancelKeyCodes,
            )
        }
    }
}
