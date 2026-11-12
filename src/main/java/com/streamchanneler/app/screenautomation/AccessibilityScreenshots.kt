// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// TODO: Validate
const val MINIMUM_SCREENSHOT_INTERVAL = 350L

// TODO: Validate
class AccessibilityScreenshots(private val accessibilityService: AccessibilityService) {
    // TODO: Validate
    fun takeScreenshotNow(): ScreenPixels? {
        if (!canTakeScreenshots) return null
        val screenshotTaken = CountDownLatch(1)
        val takenScreenPixels = AtomicReference<ScreenPixels?>(null)
        accessibilityService.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            accessibilityService.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                // TODO: Validate
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    takenScreenPixels.set(readablePixelsOfScreenshot(screenshot))
                    screenshotTaken.countDown()
                }

                // TODO: Validate
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Couldn't take a screenshot: $errorCode")
                    screenshotTaken.countDown()
                }
            },
        )
        screenshotTaken.await(SCREENSHOT_TIMEOUT, TimeUnit.MILLISECONDS)
        return takenScreenPixels.get()
    }

    // TODO: Validate
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readablePixelsOfScreenshot(screenshot: AccessibilityService.ScreenshotResult): ScreenPixels {
        val screenHardwareBitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)!!
        try {
            val screenSoftwareBitmap = screenHardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                return ScreenPixels(screenSoftwareBitmap.width, screenSoftwareBitmap.height, screenSoftwareBitmap.argbPixels())
            } finally {
                screenSoftwareBitmap.recycle()
            }
        } finally {
            screenHardwareBitmap.recycle()
            screenshot.hardwareBuffer.close()
        }
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val TAG = "AccessibilityScreenshots"

        // TODO: Validate
        private const val SCREENSHOT_TIMEOUT = 3_000L

        // TODO: Validate
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
        val canTakeScreenshots = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
