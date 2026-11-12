// TODO: Validate
package com.streamchanneler.app.livetests

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.profiles.StreamingTestDevice
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.ScreenPixels
import com.streamchanneler.app.screenautomation.ScreenTextReader
import org.junit.Test

// TODO: Validate
class ScreenTextReadTiming {
    // TODO: Validate
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // TODO: Validate
    @Test
    fun reportScreenTextReadTimes() {
        val readCount = InstrumentationRegistry.getArguments().getString("readCount")?.toInt() ?: 10
        StreamingTestDevice.turnOnProfilePicker(instrumentation.targetContext)
        val screenTextReader = ScreenTextReader()
        ScreenAutomationService.connectedService?.screenshots?.takeScreenshotNow()?.let(screenTextReader::recognizedTextOfScreenshot)
        val screenshotDurations = mutableListOf<Long>()
        val fullSizeReadDurations = mutableListOf<Long>()
        val halfSizeReadDurations = mutableListOf<Long>()
        repeat(readCount) { readIndex ->
            Thread.sleep(SCREENSHOT_INTERVAL)
            val screenshotStartedAt = SystemClock.elapsedRealtime()
            val fullSizeScreenPixels = ScreenAutomationService.connectedService?.screenshots?.takeScreenshotNow()
                ?: return@repeat reportStatus("Screenshot ${readIndex + 1} failed")
            screenshotDurations += SystemClock.elapsedRealtime() - screenshotStartedAt

            val fullSizeReadStartedAt = SystemClock.elapsedRealtime()
            val fullSizeText = screenTextReader.recognizedTextOfScreenshot(fullSizeScreenPixels)
            fullSizeReadDurations += SystemClock.elapsedRealtime() - fullSizeReadStartedAt

            val halfSizeReadStartedAt = SystemClock.elapsedRealtime()
            val halfSizeText = screenTextReader.recognizedTextOfScreenshot(halfSizeScreenPixels(fullSizeScreenPixels))
            halfSizeReadDurations += SystemClock.elapsedRealtime() - halfSizeReadStartedAt

            reportStatus(
                "Read ${readIndex + 1}: screenshot ${screenshotDurations.last()} ms, " +
                    "full size ${fullSizeReadDurations.last()} ms, half size ${halfSizeReadDurations.last()} ms",
            )
            reportStatus("  full size text: ${fullSizeText?.textBlocks?.flatMap { it.lines }?.joinToString(" | ") { it.text }}")
            reportStatus("  half size text: ${halfSizeText?.textBlocks?.flatMap { it.lines }?.joinToString(" | ") { it.text }}")
        }
        reportStatus(
            "Average of $readCount: screenshot ${screenshotDurations.average().toLong()} ms, " +
                "full size ${fullSizeReadDurations.average().toLong()} ms (max ${fullSizeReadDurations.maxOrNull()}), " +
                "half size ${halfSizeReadDurations.average().toLong()} ms (max ${halfSizeReadDurations.maxOrNull()})",
        )
        screenTextReader.close()
    }

    // TODO: Validate
    private fun halfSizeScreenPixels(fullSizeScreenPixels: ScreenPixels): ScreenPixels {
        val fullSizeBitmap = Bitmap.createBitmap(
            fullSizeScreenPixels.pixels,
            fullSizeScreenPixels.width,
            fullSizeScreenPixels.height,
            Bitmap.Config.ARGB_8888,
        )
        val halfSizeBitmap = Bitmap.createScaledBitmap(
            fullSizeBitmap,
            fullSizeScreenPixels.width / 2,
            fullSizeScreenPixels.height / 2,
            true,
        )
        val halfSizePixels = IntArray(halfSizeBitmap.width * halfSizeBitmap.height)
        halfSizeBitmap.getPixels(halfSizePixels, 0, halfSizeBitmap.width, 0, 0, halfSizeBitmap.width, halfSizeBitmap.height)
        return ScreenPixels(halfSizeBitmap.width, halfSizeBitmap.height, halfSizePixels)
    }

    // TODO: Validate
    private fun reportStatus(statusMessage: String) {
        Log.i(TAG, statusMessage)
        instrumentation.sendStatus(STATUS_CODE, Bundle().apply { putString(STATUS_KEY, statusMessage) })
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "ScreenTextReadTiming"
        // TODO: Validate
        const val STATUS_KEY = "readTiming"
        // TODO: Validate
        const val STATUS_CODE = 100

        // TODO: Validate
        const val SCREENSHOT_INTERVAL = 1_000L
    }
}
