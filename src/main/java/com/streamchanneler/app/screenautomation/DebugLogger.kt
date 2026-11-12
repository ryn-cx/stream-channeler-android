// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.streamchanneler.app.BuildConfig
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.playback.VideoLaunchResult
import com.streamchanneler.app.streamChannelerApp
import java.io.File

// TODO: Validate
class DebugLogger(private val accessibilityService: AccessibilityService, private val mediaSessions: MediaSessions) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val loggingCallbackBySessionToken = mutableMapOf<MediaSession.Token, MediaControllerLoggingCallback>()
    // TODO: Validate
    private var previousScreenElementDescriptions: Set<String> = emptySet()
    // TODO: Validate
    private var isScreenScanScheduled = false
    // TODO: Validate
    private var isDebugReceiverRegistered = false
    // TODO: Validate
    private var isLogging = false

    // TODO: Validate
    private val debugReceiver = object : BroadcastReceiver() {
        // TODO: Validate
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                START_LOGGING_ACTION -> startLogging()
                STOP_LOGGING_ACTION -> stopLogging()
                DUMP_WINDOWS_ACTION -> writeWindowDump()
                OPEN_VIDEO_ACTION -> openVideo(intent)
            }
        }
    }

    // TODO: Validate
    /**
     * Opens the video in [openVideoIntent] the same way the guide opens a video, so that tools\capture.ps1 can open a
     * URL with the profile picker. The profile in the intent is saved first, so the picker picks it and enters its PIN.
     * The result is logged with the request ID that capture.ps1 waits for.
     */
    private fun openVideo(openVideoIntent: Intent) {
        val openVideoRequestId = openVideoIntent.getStringExtra(REQUEST_ID_EXTRA)
        val pluginKey = openVideoIntent.getStringExtra(PLUGIN_KEY_EXTRA).orEmpty()
        val streamChannelerApp = accessibilityService.streamChannelerApp
        if (openVideoIntent.hasExtra(PROFILE_PIN_EXTRA)) {
            val profilePin = openVideoIntent.getStringExtra(PROFILE_PIN_EXTRA).orEmpty()
            // A profile screen is matched either by name or by position, so both are saved.
            streamChannelerApp.localAppStorage.writeStreamingProfile(
                pluginKey,
                openVideoIntent.getStringExtra(PROFILE_NAME_EXTRA).orEmpty(),
                profilePin,
            )
            streamChannelerApp.localAppStorage.writeNumberedStreamingProfile(
                pluginKey,
                openVideoIntent.getIntExtra(PROFILE_NUMBER_EXTRA, 0),
                profilePin,
            )
        }
        val requestedVideo = GuideVideo(
            episodeId = "capture",
            title = null,
            subtitle = "",
            url = openVideoIntent.getStringExtra(URL_EXTRA),
            episodeKey = openVideoIntent.getStringExtra(EPISODE_KEY_EXTRA),
            duration = null,
            thumbnailUrl = null,
            faviconUrl = null,
            pluginKey = pluginKey,
            watched = false,
            titleId = "capture",
            sourceKey = "capture",
        )
        val videoLaunchResult = streamChannelerApp.videoPlayer.launchVideoAndPickProfile(accessibilityService, requestedVideo)
        val openVideoResultText = when (videoLaunchResult) {
            is VideoLaunchResult.Launched -> "opened in ${videoLaunchResult.launchedPackageName}"
            is VideoLaunchResult.Failed -> "failed: ${videoLaunchResult.reason}"
        }
        Log.i(TAG, "OPEN_VIDEO $openVideoRequestId: $openVideoResultText")
    }

    // TODO: Validate
    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { activeMediaControllers ->
        logMediaControllers(activeMediaControllers.orEmpty())
    }

    // TODO: Validate
    private val screenScanRunnable = Runnable {
        isScreenScanScheduled = false
        logScreenElementChanges()
    }

    // TODO: Validate
    fun start() {
        if (!BuildConfig.DEBUG || isDebugReceiverRegistered) return
        val debugActionsFilter = IntentFilter().apply {
            addAction(START_LOGGING_ACTION)
            addAction(STOP_LOGGING_ACTION)
            addAction(DUMP_WINDOWS_ACTION)
            addAction(OPEN_VIDEO_ACTION)
        }
        ContextCompat.registerReceiver(accessibilityService, debugReceiver, debugActionsFilter, ContextCompat.RECEIVER_EXPORTED)
        isDebugReceiverRegistered = true
    }

    // TODO: Validate
    fun stop() {
        stopLogging()
        if (!isDebugReceiverRegistered) return
        accessibilityService.unregisterReceiver(debugReceiver)
        isDebugReceiverRegistered = false
    }

    // TODO: Validate
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isLogging) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.i(TAG, "window ${event.packageName}: class=${event.className} text=${event.text}")
        }
        if (!isScreenScanScheduled) {
            isScreenScanScheduled = true
            mainHandler.postDelayed(screenScanRunnable, SCREEN_SCAN_DELAY)
        }
    }

    // TODO: Validate
    private fun startLogging() {
        if (isLogging) return
        isLogging = true
        Log.i(TAG, "Started logging")
        logScreenElementChanges()
        val activeMediaControllers = mediaSessions.startListeningToActiveSessions(activeSessionsChangedListener, mainHandler)
        if (activeMediaControllers == null) {
            Log.w(TAG, "Notification access is off, so media sessions can't be logged")
            return
        }
        logMediaControllers(activeMediaControllers)
    }

    // TODO: Validate
    private fun stopLogging() {
        if (!isLogging) return
        isLogging = false
        mediaSessions.stopListeningToActiveSessions(activeSessionsChangedListener)
        loggingCallbackBySessionToken.values.forEach { loggingCallback -> loggingCallback.unregister() }
        loggingCallbackBySessionToken.clear()
        mainHandler.removeCallbacks(screenScanRunnable)
        isScreenScanScheduled = false
        previousScreenElementDescriptions = emptySet()
        Log.i(TAG, "Stopped logging")
    }

    // TODO: Validate
    private fun logScreenElementChanges() {
        val currentScreenElementDescriptions = linkedSetOf<String>()
        for (accessibilityWindow in accessibilityService.windows) {
            val windowRootNode = accessibilityWindow.root ?: continue
            collectScreenElementDescriptions(windowRootNode, windowRootNode.packageName, currentScreenElementDescriptions)
        }
        for (goneScreenElementDescription in previousScreenElementDescriptions - currentScreenElementDescriptions) {
            Log.i(TAG, "screen gone: $goneScreenElementDescription")
        }
        for (appearedScreenElementDescription in currentScreenElementDescriptions - previousScreenElementDescriptions) {
            Log.i(TAG, "screen appeared: $appearedScreenElementDescription")
        }
        previousScreenElementDescriptions = currentScreenElementDescriptions
    }

    // TODO: Validate
    private fun collectScreenElementDescriptions(
        node: AccessibilityNodeInfo,
        windowPackageName: CharSequence?,
        screenElementDescriptions: MutableSet<String>,
    ) {
        val isReadableOrPressable = !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty() || node.isClickable
        if (node.isVisibleToUser && isReadableOrPressable) {
            val screenElementDescription =
                "$windowPackageName ${node.className?.toString()?.substringAfterLast('.')} id=${node.viewIdResourceName} " +
                    "text=${node.text} desc=${node.contentDescription} clickable=${node.isClickable} focused=${node.isFocused}"
            screenElementDescriptions += screenElementDescription.replace(LINE_BREAK_WITH_SURROUNDING_WHITESPACE, " ")
        }
        node.childNodes.forEach { childNode ->
            collectScreenElementDescriptions(childNode, windowPackageName, screenElementDescriptions)
        }
    }

    // TODO: Validate
    private fun logMediaControllers(activeMediaControllers: List<MediaController>) {
        Log.i(
            TAG,
            "sessions: ${activeMediaControllers.joinToString { "${it.packageName} (token ${it.sessionToken.hashCode()})" }}",
        )
        val activeSessionTokens = activeMediaControllers.map { it.sessionToken }.toSet()
        val goneLoggingCallbackBySessionToken = loggingCallbackBySessionToken.filterKeys { it !in activeSessionTokens }
        for ((goneSessionToken, goneLoggingCallback) in goneLoggingCallbackBySessionToken) {
            goneLoggingCallback.unregister()
            loggingCallbackBySessionToken.remove(goneSessionToken)
        }
        for (activeMediaController in activeMediaControllers) {
            loggingCallbackBySessionToken.getOrPut(activeMediaController.sessionToken) {
                MediaControllerLoggingCallback(activeMediaController).also { loggingCallback ->
                    activeMediaController.registerCallback(loggingCallback, mainHandler)
                }
            }
            logWholeSession(activeMediaController)
        }
    }

    // TODO: Validate
    private fun logWholeSession(mediaController: MediaController) {
        logSessionLine(
            mediaController,
            "session: flags=${mediaController.flags} ratingType=${mediaController.ratingType} " +
                "playbackType=${mediaController.playbackInfo?.playbackType} " +
                "volume=${mediaController.playbackInfo?.currentVolume}/${mediaController.playbackInfo?.maxVolume}",
        )
        logSessionLine(mediaController, "metadata: ${describeMetadata(mediaController.metadata)}")
        logSessionLine(mediaController, "state: ${describePlaybackState(mediaController.playbackState)}")
        logSessionLine(mediaController, "queue: ${describeQueue(mediaController.queue)} queueTitle=${mediaController.queueTitle}")
        logSessionLine(mediaController, "extras: ${describeBundle(mediaController.extras)}")
    }

    // TODO: Validate
    private fun logSessionLine(mediaController: MediaController, sessionLine: String) {
        Log.i(TAG, "${mediaController.packageName} $sessionLine")
    }

    // TODO: Validate
    fun writeWindowDump() {
        if (!BuildConfig.DEBUG) return
        val windowDumpText = buildString {
            appendLine("Active window package: ${accessibilityService.rootInActiveWindow?.packageName}")
            for (accessibilityWindow in accessibilityService.windows) {
                appendLine(
                    "=== Window id=${accessibilityWindow.id} type=${accessibilityWindow.type} " +
                        "layer=${accessibilityWindow.layer} active=${accessibilityWindow.isActive} " +
                        "title=${accessibilityWindow.title}",
                )
                accessibilityWindow.root?.let { windowRootNode -> appendNodeTree(windowRootNode, depth = 0) }
            }
        }
        val windowDumpDirectory = File(accessibilityService.filesDir, "window_dumps").apply { mkdirs() }
        val windowDumpFile = File(windowDumpDirectory, "window_dump_${System.currentTimeMillis()}.txt")
        windowDumpFile.writeText(windowDumpText)
        Log.i(TAG, "Wrote ${windowDumpFile.absolutePath}")
    }

    // TODO: Validate
    private fun StringBuilder.appendNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        appendLine(
            "  ".repeat(depth) +
                "${node.className} id=${node.viewIdResourceName} text=${node.text} desc=${node.contentDescription} " +
                "clickable=${node.isClickable} focusable=${node.isFocusable} focused=${node.isFocused} " +
                "visible=${node.isVisibleToUser} bounds=${node.boundsOnScreen}",
        )
        node.childNodes.forEach { childNode -> appendNodeTree(childNode, depth + 1) }
    }

    // TODO: Validate
    private inner class MediaControllerLoggingCallback(private val mediaController: MediaController) : MediaController.Callback() {
        // TODO: Validate
        fun unregister() = mediaController.unregisterCallback(this)

        // TODO: Validate
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            logSessionLine(mediaController, "metadata: ${describeMetadata(metadata)}")
        }

        // TODO: Validate
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            logSessionLine(mediaController, "state: ${describePlaybackState(state)}")
        }

        // TODO: Validate
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            logSessionLine(mediaController, "queue: ${describeQueue(queue)}")
        }

        // TODO: Validate
        override fun onQueueTitleChanged(title: CharSequence?) {
            logSessionLine(mediaController, "queueTitle: $title")
        }

        // TODO: Validate
        override fun onExtrasChanged(extras: Bundle?) {
            logSessionLine(mediaController, "extras: ${describeBundle(extras)}")
        }

        // TODO: Validate
        override fun onSessionEvent(event: String, extras: Bundle?) {
            logSessionLine(mediaController, "event: $event ${describeBundle(extras)}")
        }

        // TODO: Validate
        override fun onSessionDestroyed() {
            logSessionLine(mediaController, "destroyed")
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "DebugLog"
        // TODO: Validate
        const val START_LOGGING_ACTION = "com.streamchanneler.app.START_DEBUG_LOG"
        // TODO: Validate
        const val STOP_LOGGING_ACTION = "com.streamchanneler.app.STOP_DEBUG_LOG"
        // TODO: Validate
        const val DUMP_WINDOWS_ACTION = "com.streamchanneler.app.DUMP_WINDOWS"
        // TODO: Validate
        const val OPEN_VIDEO_ACTION = "com.streamchanneler.app.OPEN_VIDEO"
        // TODO: Validate
        const val REQUEST_ID_EXTRA = "requestId"
        // TODO: Validate
        const val URL_EXTRA = "url"
        // TODO: Validate
        const val PLUGIN_KEY_EXTRA = "pluginKey"
        // TODO: Validate
        const val EPISODE_KEY_EXTRA = "episodeKey"
        // TODO: Validate
        const val PROFILE_NAME_EXTRA = "profileName"
        // TODO: Validate
        const val PROFILE_NUMBER_EXTRA = "profileNumber"
        // TODO: Validate
        const val PROFILE_PIN_EXTRA = "profilePin"

        // TODO: Validate
        const val SCREEN_SCAN_DELAY = 250L

        // TODO: Validate
        val LINE_BREAK_WITH_SURROUNDING_WHITESPACE = Regex("\\s*\\n\\s*")
    }
}

// TODO: Validate
private fun describeMetadata(metadata: MediaMetadata?): String {
    if (metadata == null) return "null"
    return metadata.keySet().sorted().joinToString("; ") { metadataKey ->
        val metadataValue = metadata.getText(metadataKey)?.let { "\"$it\"" }
            ?: metadata.getBitmap(metadataKey)?.let { "image ${it.width}x${it.height}" }
            ?: metadata.getRating(metadataKey)?.toString()
            ?: metadata.getLong(metadataKey).toString()
        "${metadataKey.removePrefix("android.media.metadata.")}=$metadataValue"
    }
}

// TODO: Validate
private fun describePlaybackState(playbackState: PlaybackState?): String {
    if (playbackState == null) return "null"
    return "$playbackState extras={${describeBundle(playbackState.extras)}}"
}

// TODO: Validate
private fun describeQueue(queue: List<MediaSession.QueueItem>?): String {
    if (queue == null) return "null"
    return "${queue.size} items [" + queue.joinToString(" | ") { queueItem ->
        "id=${queueItem.queueId} title=${queueItem.description.title} subtitle=${queueItem.description.subtitle} " +
            "mediaId=${queueItem.description.mediaId}"
    } + "]"
}

// TODO: Validate
private fun describeBundle(bundle: Bundle?): String {
    if (bundle == null) return "null"
    return bundle.keySet().sorted().joinToString(", ") { bundleKey ->
        @Suppress("DEPRECATION")
        "$bundleKey=${bundle.get(bundleKey)}"
    }
}
