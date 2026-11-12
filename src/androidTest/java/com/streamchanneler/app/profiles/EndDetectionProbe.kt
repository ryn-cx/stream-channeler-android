// TODO: Validate
package com.streamchanneler.app.profiles

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.playback.estimatedPosition
import com.streamchanneler.app.screenautomation.childNodes
import org.junit.Test

// TODO: Validate
class EndDetectionProbe {
    // TODO: Validate
    @Test
    fun logStreamingAppSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val probePackage = arguments.getString("probePackage") ?: error("probePackage argument is required")
        val probeDurationMinutes = arguments.getString("probeMinutes")?.toLong() ?: 8L
        val seekDistanceFromEnd = arguments.getString("seekToEndMs")?.toLong()
        val guideDuration = arguments.getString("guideDurationMs")?.toLong()
        val uiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val serviceInfo = uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        uiAutomation.serviceInfo = serviceInfo
        uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        val mediaSessionManager = instrumentation.targetContext
            .getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val probeEndsAt = System.currentTimeMillis() + probeDurationMinutes * 60_000L
        var hasSeekedToEnd = seekDistanceFromEnd == null
        var lastSessionSnapshot = ""
        var lastScreenSnapshot = ""
        while (System.currentTimeMillis() < probeEndsAt) {
            val probedControllers = mediaSessionManager.getActiveSessions(null).filter { it.packageName == probePackage }
            val sessionLines = probedControllers.map(::describeController)
            val sessionSnapshotForComparison = sessionLines.joinToString("\n") { it.substringBefore(" | ") }
                .replace(Regex("reportedPosition=-?\\d+ updated=\\d+"), "")
            if (sessionSnapshotForComparison != lastSessionSnapshot) {
                Log.i(TAG, "----- session change at ${System.currentTimeMillis()}")
                if (sessionLines.isEmpty()) Log.i(TAG, "SESSION none for $probePackage")
                sessionLines.forEach { Log.i(TAG, "SESSION $it") }
                lastSessionSnapshot = sessionSnapshotForComparison
            }
            if (!hasSeekedToEnd) {
                val playingController = probedControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                val mediaDuration = playingController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    ?.takeIf { it > 0 } ?: guideDuration
                if (playingController != null && mediaDuration != null && seekDistanceFromEnd != null) {
                    val seekPosition = mediaDuration - seekDistanceFromEnd
                    Log.i(TAG, "SEEK to $seekPosition of $mediaDuration")
                    playingController.transportControls.seekTo(seekPosition)
                    hasSeekedToEnd = true
                }
            }
            val screenLines = mutableListOf<String>()
            for (window in uiAutomation.windows) {
                screenLines += "WINDOW type=${window.type} title=${window.title}"
                window.root?.let { appendNodeLines(it, 1, screenLines) }
            }
            val screenSnapshot = screenLines.joinToString("\n")
            if (screenSnapshot != lastScreenSnapshot) {
                Log.i(TAG, "----- screen change at ${System.currentTimeMillis()} positions=" +
                    sessionLines.joinToString { it.substringAfter("estimatedPosition=") })
                screenLines.forEach { Log.i(TAG, "SCREEN $it") }
                lastScreenSnapshot = screenSnapshot
            }
            Thread.sleep(1_000L)
        }
        uiAutomation.dropShellPermissionIdentity()
    }

    // TODO: Validate
    private fun describeController(controller: MediaController): String {
        val playbackState = controller.playbackState
        return "${controller.packageName} state=${playbackState?.state} reportedPosition=${playbackState?.position} " +
            "updated=${playbackState?.lastPositionUpdateTime} speed=${playbackState?.playbackSpeed} " +
            "actions=${playbackState?.actions} activeItem=${playbackState?.activeQueueItemId} " +
            "stateExtras=${describeBundle(playbackState?.extras)} " +
            "metadata=${describeMetadata(controller.metadata)} queueSize=${controller.queue?.size} " +
            "queueTitle=${controller.queueTitle} sessionExtras=${describeBundle(controller.extras)}" +
            " | estimatedPosition=${estimatedPosition(playbackState)}"
    }

    // TODO: Validate
    private fun describeMetadata(metadata: MediaMetadata?): String {
        if (metadata == null) return "null"
        return metadata.keySet().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            val value = metadata.getText(key) ?: metadata.getLong(key).takeIf { it != 0L }
                ?: metadata.getBitmap(key)?.let { "bitmap ${it.width}x${it.height}" }
            "$key=$value"
        }
    }

    // TODO: Validate
    @Suppress("DEPRECATION")
    private fun describeBundle(bundle: Bundle?): String =
        bundle?.keySet()?.sorted()?.joinToString(prefix = "{", postfix = "}") { "$it=${bundle.get(it)}" } ?: "null"

    // TODO: Validate
    private fun appendNodeLines(node: AccessibilityNodeInfo, depth: Int, screenLines: MutableList<String>) {
        val nodeDescription = listOfNotNull(
            node.className?.toString()?.substringAfterLast('.'),
            node.viewIdResourceName?.let { "id=$it" },
            node.text?.let { "text=\"$it\"" },
            node.contentDescription?.let { "desc=\"$it\"" },
            if (node.isVisibleToUser) null else "invisible",
        ).joinToString(" ")
        screenLines += "  ".repeat(depth) + nodeDescription
        node.childNodes.forEach { childNode -> appendNodeLines(childNode, depth + 1, screenLines) }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "EndDetectionProbe"
    }
}
