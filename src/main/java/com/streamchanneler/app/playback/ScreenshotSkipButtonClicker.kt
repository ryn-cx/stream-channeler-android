// TODO: Validate
package com.streamchanneler.app.playback

import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.ScreenPixels
import com.streamchanneler.app.screenautomation.ScreenTextReader
import com.streamchanneler.app.screenautomation.ScreenshotButton
import com.streamchanneler.app.streamingapps.PlayerButtonKind
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class ScreenshotSkipButtonClicker(
    private val buttonTextByPlayerButtonKind: Map<PlayerButtonKind, String>,
    private val nextEpisodeAutoplayCancelButtons: List<ScreenshotButton>,
) {
    // TODO: Validate
    private val nextEpisodeAutoplayCancelButtonTexts = nextEpisodeAutoplayCancelButtons.map { it.buttonText }.toSet()

    // TODO: Validate
    private val skipButtonCheckExecutor = Executors.newSingleThreadExecutor()
    // TODO: Validate
    private val screenTextReader = ScreenTextReader()
    // TODO: Validate
    private val isSkipButtonCheckRunning = AtomicBoolean(false)

    // TODO: Validate
    private val lastClickAtByButtonText = ConcurrentHashMap<String, Long>()

    // TODO: Validate
    private val pressedCancelButtonTextsStillShowing = ConcurrentHashMap.newKeySet<String>()

    // TODO: Validate
    fun startClickingShownButtons(
        packageName: String,
        clickedPlayerButtonKinds: Set<PlayerButtonKind>,
        clicksNextEpisodeAutoplayCancelButton: Boolean,
        isAppPlaying: Boolean,
    ) {
        val clickedButtonLabelByText = buttonTextByPlayerButtonKind
            .filterKeys { it in clickedPlayerButtonKinds }
            .entries
            .associate { (clickedPlayerButtonKind, clickedButtonText) -> clickedButtonText to clickedPlayerButtonKind.label } +
            nextEpisodeAutoplayCancelButtons
                .filter { clicksNextEpisodeAutoplayCancelButton && (isAppPlaying || !it.isPressedOnlyWhilePlaying) }
                .associate { it.buttonText to it.buttonText }
        if (clickedButtonLabelByText.isEmpty()) return
        if (!isSkipButtonCheckRunning.compareAndSet(false, true)) return
        skipButtonCheckExecutor.execute {
            try {
                clickShownButtons(packageName, clickedButtonLabelByText)
            } finally {
                isSkipButtonCheckRunning.set(false)
            }
        }
    }

    // TODO: Validate
    fun close() {
        skipButtonCheckExecutor.execute { screenTextReader.close() }
        skipButtonCheckExecutor.shutdown()
    }

    // TODO: Validate
    private fun clickShownButtons(packageName: String, clickedButtonLabelByText: Map<String, String>) {
        val connectedService = ScreenAutomationService.connectedService ?: return
        if (ScreenAutomationService.foregroundPackageName != packageName) return
        val screenPixels = connectedService.screenshots.takeScreenshotNow() ?: return
        val shownButtonTextBoundsByText = screenTextReader.boundsOfShownButtonTexts(
            screenPixels,
            buttonTextByPlayerButtonKind.values + nextEpisodeAutoplayCancelButtonTexts,
        ).filter { (shownButtonText, shownButtonTextBounds) ->
            shownButtonText !in nextEpisodeAutoplayCancelButtonTexts ||
                cancelButtonShownAt(shownButtonText, shownButtonTextBounds, screenPixels) != null
        }
        pressedCancelButtonTextsStillShowing.retainAll(shownButtonTextBoundsByText.keys)
        val firstShownCancelButtonText = nextEpisodeAutoplayCancelButtons
            .firstOrNull { cancelButton -> cancelButton.buttonText in shownButtonTextBoundsByText }
            ?.buttonText
        for ((clickedButtonText, clickedButtonLabel) in clickedButtonLabelByText) {
            val shownButtonTextBounds = shownButtonTextBoundsByText[clickedButtonText] ?: continue
            val isCancelButton = clickedButtonText in nextEpisodeAutoplayCancelButtonTexts
            if (isCancelButton && clickedButtonText != firstShownCancelButtonText) continue
            if (clickedButtonText in pressedCancelButtonTextsStillShowing) continue
            if (isCancelButton) pressedCancelButtonTextsStillShowing += clickedButtonText
            val now = System.currentTimeMillis()
            val lastClickAt = lastClickAtByButtonText[clickedButtonText]
            if (lastClickAt != null && now - lastClickAt < REPEAT_CLICK_DELAY) continue
            lastClickAtByButtonText[clickedButtonText] = now
            Log.i(TAG, "Clicking $clickedButtonLabel in $packageName")
            val cancelButtonKeyCodes = cancelButtonShownAt(clickedButtonText, shownButtonTextBounds, screenPixels)?.keyCodesToPress
            if (cancelButtonKeyCodes != null) {
                RemoteButtons.press(cancelButtonKeyCodes, 0L)
            } else if (isButtonFocused(screenPixels, shownButtonTextBounds)) {
                RemoteButtons.press(listOf(KeyEvent.KEYCODE_DPAD_CENTER), 0L)
            } else {
                connectedService.screenTaps.tap(
                    Point(shownButtonTextBounds.centerX(), shownButtonTextBounds.centerY()),
                    screenPixels,
                )
            }
        }
    }

    // TODO: Validate
    private fun cancelButtonShownAt(shownButtonText: String, shownButtonTextBounds: Rect, screenPixels: ScreenPixels): ScreenshotButton? =
        nextEpisodeAutoplayCancelButtons.firstOrNull { cancelButton ->
            cancelButton.buttonText == shownButtonText &&
                (cancelButton.textRegion == null || cancelButton.textRegion.containsCenterOf(shownButtonTextBounds, screenPixels))
        }

    // TODO: Validate
    private fun isButtonFocused(screenPixels: ScreenPixels, buttonTextBounds: Rect): Boolean {
        val buttonBackgroundX = (buttonTextBounds.left - buttonTextBounds.height() / 2).coerceIn(0, screenPixels.width - 1)
        val buttonBackgroundY = buttonTextBounds.centerY().coerceIn(0, screenPixels.height - 1)
        val buttonBackgroundColor = screenPixels.pixels[buttonBackgroundY * screenPixels.width + buttonBackgroundX]
        val buttonBackgroundBrightness =
            maxOf(Color.red(buttonBackgroundColor), Color.green(buttonBackgroundColor), Color.blue(buttonBackgroundColor))
        return buttonBackgroundBrightness >= FOCUSED_BUTTON_MINIMUM_BRIGHTNESS
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "ScreenshotSkipButtons"

        // TODO: Validate
        const val REPEAT_CLICK_DELAY = 5_000L

        // TODO: Validate
        const val FOCUSED_BUTTON_MINIMUM_BRIGHTNESS = 160
    }
}
