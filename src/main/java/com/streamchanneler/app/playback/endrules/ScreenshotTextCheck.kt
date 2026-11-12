// TODO: Validate
package com.streamchanneler.app.playback.endrules

import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.ScreenRegion
import com.streamchanneler.app.screenautomation.ScreenTextReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class ScreenshotTextCheck(
    private val shownText: String,
    private val textRegion: ScreenRegion,
) {
    // TODO: Validate
    private var lastScreenshotTextCheckAt: Long? = null

    // TODO: Validate
    private val isScreenshotTextCheckRunning = AtomicBoolean(false)

    // TODO: Validate
    fun startCheckWhenDue(checkedPackageName: String, now: Long, onChecked: (isShownTextShowing: Boolean) -> Unit) {
        val previousScreenshotTextCheckAt = lastScreenshotTextCheckAt
        if (previousScreenshotTextCheckAt != null && now - previousScreenshotTextCheckAt < SCREENSHOT_TEXT_CHECK_INTERVAL) {
            return
        }
        if (!isScreenshotTextCheckRunning.compareAndSet(false, true)) return
        lastScreenshotTextCheckAt = now
        screenshotTextCheckExecutor.execute {
            try {
                isShownTextInTextRegion(checkedPackageName)?.let(onChecked)
            } finally {
                isScreenshotTextCheckRunning.set(false)
            }
        }
    }

    // TODO: Validate
    private fun isShownTextInTextRegion(checkedPackageName: String): Boolean? {
        val connectedService = ScreenAutomationService.connectedService ?: return null
        if (ScreenAutomationService.foregroundPackageName != checkedPackageName) return null
        val screenPixels = connectedService.screenshots.takeScreenshotNow() ?: return null
        val shownTextBounds = screenTextReader.boundsOfShownButtonTexts(screenPixels, listOf(shownText))[shownText]
            ?: return false
        return textRegion.containsCenterOf(shownTextBounds, screenPixels)
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val SCREENSHOT_TEXT_CHECK_INTERVAL = 1_000L

        // TODO: Validate
        val screenshotTextCheckExecutor = Executors.newSingleThreadExecutor()
        // TODO: Validate
        val screenTextReader = ScreenTextReader()
    }
}
