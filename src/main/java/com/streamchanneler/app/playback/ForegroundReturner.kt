// TODO: Validate
package com.streamchanneler.app.playback

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.overlayMessageView

// TODO: Validate
object ForegroundReturner {
    // TODO: Validate
    private const val TAG = "ForegroundReturner"
    // TODO: Validate
    private const val OVERLAY_VISIBLE_DURATION = 2_500L
    // TODO: Validate
    private const val OVERLAY_TEST_TIMEOUT = 3_000L
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())

    // TODO: Validate
    fun canReturnFromBackground(context: Context): Boolean = Settings.canDrawOverlays(context)

    // TODO: Validate
    var lastOverlayTestResult: OverlayTestResult? = null
        private set

    // TODO: Validate
    fun testOverlayWindow(context: Context, onTestFinished: (OverlayTestResult) -> Unit) {
        val applicationContext = context.applicationContext
        if (!canReturnFromBackground(applicationContext)) {
            lastOverlayTestResult = OverlayTestResult.NotAllowed
            onTestFinished(OverlayTestResult.NotAllowed)
            return
        }
        val windowManager = applicationContext.getSystemService(WindowManager::class.java)
        var isOverlayTestFinished = false
        lateinit var testOverlayView: View

        // TODO: Validate
        fun finishOverlayTest(overlayTestResult: OverlayTestResult) {
            if (isOverlayTestFinished) return
            isOverlayTestFinished = true
            if (testOverlayView.isAttachedToWindow) runCatching { windowManager.removeView(testOverlayView) }
            if (overlayTestResult is OverlayTestResult.Broken) {
                Log.w(TAG, "\"Display over other apps\" is allowed but not working: ${overlayTestResult.failureReason}")
            }
            lastOverlayTestResult = overlayTestResult
            onTestFinished(overlayTestResult)
        }

        // TODO: Validate
        testOverlayView = object : View(applicationContext) {
            // TODO: Validate
            override fun onDraw(canvas: Canvas) {
                mainHandler.post { finishOverlayTest(OverlayTestResult.Working) }
            }
        }
        val testOverlayLayoutParams = untouchableOverlayLayoutParams(width = 1, height = 1, Gravity.TOP or Gravity.START)
        try {
            windowManager.addView(testOverlayView, testOverlayLayoutParams)
        } catch (addViewError: Exception) {
            finishOverlayTest(OverlayTestResult.Broken("Android refused the overlay window: ${addViewError.message}"))
            return
        }
        mainHandler.postDelayed({
            finishOverlayTest(OverlayTestResult.Broken("Android accepted the overlay window but never showed it"))
        }, OVERLAY_TEST_TIMEOUT)
    }

    // TODO: Validate
    fun bringToFront(context: Context, activityIntent: Intent) {
        val applicationContext = context.applicationContext
        val returningOverlay = if (canReturnFromBackground(applicationContext)) {
            runCatching { showReturningOverlay(applicationContext) }
                .onFailure { Log.w(TAG, "Couldn't show the overlay", it) }
                .getOrNull()
        } else {
            null
        }
        mainHandler.post {
            try {
                applicationContext.startActivity(activityIntent)
            } catch (launchError: Exception) {
                Log.w(TAG, "Couldn't return to the guide", launchError)
            }
            if (returningOverlay != null) {
                mainHandler.postDelayed({
                    runCatching {
                        applicationContext.getSystemService(WindowManager::class.java).removeView(returningOverlay)
                    }
                }, OVERLAY_VISIBLE_DURATION)
            }
        }
    }

    // TODO: Validate
    private fun showReturningOverlay(context: Context): View {
        val returningMessageView = context.overlayMessageView("Returning to Stream Channeler…")
        val overlayLayoutParams = untouchableOverlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END,
        ).apply {
            x = context.dp(24)
            y = context.dp(24)
        }
        context.getSystemService(WindowManager::class.java).addView(returningMessageView, overlayLayoutParams)
        return returningMessageView
    }

    // TODO: Validate
    private fun untouchableOverlayLayoutParams(width: Int, height: Int, overlayGravity: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = overlayGravity }
}

// TODO: Validate
sealed class OverlayTestResult(val description: String) {
    // TODO: Validate
    object NotAllowed : OverlayTestResult("Not allowed")

    // TODO: Validate
    object Working : OverlayTestResult("Working")

    // TODO: Validate
    class Broken(val failureReason: String) : OverlayTestResult("Allowed but not working: $failureReason")
}
