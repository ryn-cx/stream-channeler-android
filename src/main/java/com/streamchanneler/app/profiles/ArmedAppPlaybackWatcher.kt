// TODO: Validate
package com.streamchanneler.app.profiles

import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
class ArmedAppPlaybackWatcher(
    private val mediaSessions: MediaSessions,
    private val onArmedAppPlaybackStarted: (playingPackageName: String) -> Unit,
) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private var watchedPackageName: String? = null
    // TODO: Validate
    private var watchedAppPlaybackStartConfirmationTime = 0L
    // TODO: Validate
    private var watchedPackageMediaControllers: List<MediaController> = emptyList()
    // TODO: Validate
    private var isPlaybackConfirmationScheduled = false

    // TODO: Validate
    private val confirmPlaybackRunnable = Runnable {
        isPlaybackConfirmationScheduled = false
        if (isWatchedPackagePlaying()) reportPlaybackStarted()
    }

    // TODO: Validate
    private val watchedPackagePlaybackStateCallback = object : MediaController.Callback() {
        // TODO: Validate
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
            if (playbackState?.state == PlaybackState.STATE_PLAYING) schedulePlaybackConfirmation()
        }
    }

    // TODO: Validate
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { activeMediaControllers ->
            watchMediaControllersOfWatchedPackage(activeMediaControllers.orEmpty())
        }

    // TODO: Validate
    fun start(armedStreamingApp: StreamingApp) {
        val armedPackageName = armedStreamingApp.packageName
        mainHandler.post {
            stopWatching()
            val activeMediaControllers =
                mediaSessions.startListeningToActiveSessions(activeSessionsChangedListener, mainHandler) ?: return@post
            watchedPackageName = armedPackageName
            watchedAppPlaybackStartConfirmationTime = armedStreamingApp.playbackStartConfirmationTime
            watchMediaControllersOfWatchedPackage(activeMediaControllers)
        }
    }

    // TODO: Validate
    fun stop() {
        mainHandler.post { stopWatching() }
    }

    // TODO: Validate
    private fun stopWatching() {
        if (watchedPackageName == null) return
        watchedPackageName = null
        mainHandler.removeCallbacks(confirmPlaybackRunnable)
        isPlaybackConfirmationScheduled = false
        mediaSessions.stopListeningToActiveSessions(activeSessionsChangedListener)
        watchedPackageMediaControllers.forEach { it.unregisterCallback(watchedPackagePlaybackStateCallback) }
        watchedPackageMediaControllers = emptyList()
    }

    // TODO: Validate
    private fun watchMediaControllersOfWatchedPackage(activeMediaControllers: List<MediaController>) {
        val packageNameBeingWatched = watchedPackageName ?: return
        watchedPackageMediaControllers.forEach { it.unregisterCallback(watchedPackagePlaybackStateCallback) }
        watchedPackageMediaControllers = activeMediaControllers.filter { it.packageName == packageNameBeingWatched }
        watchedPackageMediaControllers.forEach { it.registerCallback(watchedPackagePlaybackStateCallback, mainHandler) }
        if (isWatchedPackagePlaying()) schedulePlaybackConfirmation()
    }

    // TODO: Validate
    private fun isWatchedPackagePlaying(): Boolean =
        watchedPackageMediaControllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }

    // TODO: Validate
    private fun schedulePlaybackConfirmation() {
        if (isPlaybackConfirmationScheduled) return
        isPlaybackConfirmationScheduled = true
        mainHandler.postDelayed(confirmPlaybackRunnable, watchedAppPlaybackStartConfirmationTime)
    }

    // TODO: Validate
    private fun reportPlaybackStarted() {
        val playingPackageName = watchedPackageName ?: return
        stopWatching()
        onArmedAppPlaybackStarted(playingPackageName)
    }
}
