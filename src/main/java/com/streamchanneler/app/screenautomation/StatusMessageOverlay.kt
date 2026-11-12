// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.overlayMessageView

// TODO: Validate
class StatusMessageOverlay(private val accessibilityService: AccessibilityService) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val windowManager = accessibilityService.getSystemService(WindowManager::class.java)
    // TODO: Validate
    private var shownStatusMessageView: TextView? = null

    // TODO: Validate
    private val hideStatusMessageAfterVisibleDuration = Runnable { removeStatusMessageView() }

    // TODO: Validate
    val cancelInstruction: String
        get() = "Press Back to cancel."

    // TODO: Validate
    fun skipInstruction(skipAction: String): String = "Press OK to $skipAction. "

    // TODO: Validate
    fun show(statusMessage: String, visibleDuration: Long) {
        mainHandler.post {
            mainHandler.removeCallbacks(hideStatusMessageAfterVisibleDuration)
            val statusMessageView = shownStatusMessageView
            if (statusMessageView != null) {
                statusMessageView.text = statusMessage
            } else {
                runCatching { addStatusMessageView(statusMessage) }
                    .onFailure { Log.w(TAG, "Couldn't show the status message overlay", it) }
            }
            mainHandler.postDelayed(hideStatusMessageAfterVisibleDuration, visibleDuration)
        }
    }

    // TODO: Validate
    fun updateMessageIfShown(statusMessage: String) {
        mainHandler.post { shownStatusMessageView?.text = statusMessage }
    }

    // TODO: Validate
    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(hideStatusMessageAfterVisibleDuration)
            removeStatusMessageView()
        }
    }

    // TODO: Validate
    private fun addStatusMessageView(statusMessage: String) {
        val statusMessageView = accessibilityService.overlayMessageView(statusMessage)
        val statusMessageLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = accessibilityService.dp(24)
            y = accessibilityService.dp(24)
        }
        windowManager.addView(statusMessageView, statusMessageLayoutParams)
        shownStatusMessageView = statusMessageView
    }

    // TODO: Validate
    private fun removeStatusMessageView() {
        val statusMessageView = shownStatusMessageView ?: return
        shownStatusMessageView = null
        runCatching { windowManager.removeView(statusMessageView) }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "StatusMessageOverlay"
    }
}
