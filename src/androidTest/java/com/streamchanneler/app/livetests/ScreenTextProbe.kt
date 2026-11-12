// TODO: Validate
package com.streamchanneler.app.livetests

import android.app.UiAutomation
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.playback.estimatedPosition
import com.streamchanneler.app.profiles.StreamingTestDevice
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.ScreenTextReader
import org.junit.Test

// TODO: Validate
class ScreenTextProbe {
    // TODO: Validate
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // TODO: Validate
    private val targetContext: Context = instrumentation.targetContext

    // TODO: Validate
    @Test
    fun reportScreenTextChanges() {
        val probeMinutes = InstrumentationRegistry.getArguments().getString("probeMinutes")?.toLong() ?: 10L
        StreamingTestDevice.turnOnProfilePicker(targetContext)
        val screenTextReader = ScreenTextReader()
        var lastScreenTextDescription = ""
        val stopProbingAt = SystemClock.elapsedRealtime() + probeMinutes * 60_000L
        while (SystemClock.elapsedRealtime() < stopProbingAt) {
            val connectedService = ScreenAutomationService.connectedService
            val screenPixels = connectedService?.screenshots?.takeScreenshotNow()
            if (screenPixels != null) {
                val screenTextDescription = screenTextReader.describeScreenTextWithPositions(screenPixels)
                if (screenTextDescription != lastScreenTextDescription) {
                    val frontPackageName = StreamingTestDevice.runOnMainThreadAndWait { ScreenAutomationService.foregroundPackageName }
                    reportStatus("[${describeFrontAppPlayback(frontPackageName)}] ${screenTextDescription.ifEmpty { "no text" }}")
                    lastScreenTextDescription = screenTextDescription
                }
            }
            Thread.sleep(PROBE_INTERVAL)
        }
        screenTextReader.close()
    }

    // TODO: Validate
    private fun describeFrontAppPlayback(frontPackageName: String?): String {
        val uiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL_PERMISSION)
        try {
            val mediaSessionManager = targetContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val frontAppController = mediaSessionManager.getActiveSessions(null)
                .firstOrNull { it.packageName == frontPackageName }
                ?: return "$frontPackageName no session"
            val playbackState = frontAppController.playbackState
            val mediaId = frontAppController.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            return "$frontPackageName state=${playbackState?.state} at %.1f s id=$mediaId".format(estimatedPosition(playbackState) / 1000.0)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    // TODO: Validate
    private fun reportStatus(statusMessage: String) {
        Log.i(TAG, statusMessage)
        instrumentation.sendStatus(STATUS_CODE, Bundle().apply { putString(STATUS_KEY, statusMessage) })
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "ScreenTextProbe"
        // TODO: Validate
        const val MEDIA_CONTENT_CONTROL_PERMISSION = "android.permission.MEDIA_CONTENT_CONTROL"
        // TODO: Validate
        const val STATUS_KEY = "screenText"
        // TODO: Validate
        const val STATUS_CODE = 100

        // TODO: Validate
        const val PROBE_INTERVAL = 1_000L
    }
}
