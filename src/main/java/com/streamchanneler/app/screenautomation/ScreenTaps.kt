// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Validate
class ScreenTaps(private val accessibilityService: AccessibilityService) {
    // TODO: Validate
    fun tap(pointInScreenshot: Point, screenPixels: ScreenPixels): Boolean {
        val displayMetrics = DisplayMetrics()
        val defaultDisplay = accessibilityService.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        defaultDisplay.getRealMetrics(displayMetrics)
        val tapX = pointInScreenshot.x.toFloat() * displayMetrics.widthPixels / screenPixels.width
        val tapY = pointInScreenshot.y.toFloat() * displayMetrics.heightPixels / screenPixels.height
        return dispatchTapAndWait(tapX, tapY)
    }

    // TODO: Validate
    private fun dispatchTapAndWait(tapX: Float, tapY: Float): Boolean {
        val tapPath = Path().apply { moveTo(tapX, tapY) }
        val tapGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, TAP_DURATION))
            .build()
        val tapFinished = CountDownLatch(1)
        val wasTapCompleted = AtomicBoolean(false)
        val wasTapDispatched = accessibilityService.dispatchGesture(
            tapGesture,
            object : AccessibilityService.GestureResultCallback() {
                // TODO: Validate
                override fun onCompleted(gestureDescription: GestureDescription) {
                    wasTapCompleted.set(true)
                    tapFinished.countDown()
                }

                // TODO: Validate
                override fun onCancelled(gestureDescription: GestureDescription) {
                    tapFinished.countDown()
                }
            },
            null,
        )
        if (!wasTapDispatched) {
            Log.w(TAG, "Android refused to tap at ($tapX, $tapY)")
            return false
        }
        tapFinished.await(TAP_TIMEOUT, TimeUnit.MILLISECONDS)
        Log.i(TAG, "Tapped at ($tapX, $tapY): ${if (wasTapCompleted.get()) "completed" else "cancelled or timed out"}")
        return wasTapCompleted.get()
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "ScreenTaps"

        // TODO: Validate
        const val TAP_DURATION = 50L

        // TODO: Validate
        const val TAP_TIMEOUT = 2_000L
    }
}
