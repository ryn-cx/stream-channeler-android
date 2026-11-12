// TODO: Validate
package com.streamchanneler.app.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import com.streamchanneler.app.mediasessions.MediaSessions

// TODO: Validate
/**
 * Remembers the last media title that an app reported in any of its media sessions.
 *
 * Pluto TV reports a video's title in a media session that only lasts about 0.1 seconds. Pluto TV then replaces that
 * session with one that has no metadata. Reading the media sessions every 100 ms can miss that title, so this class
 * listens for it instead.
 */
class LastReportedMediaTitleListener(private val mediaSessions: MediaSessions) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())

    // TODO: Validate
    private var listenedPackageName: String? = null

    // TODO: Validate
    private val mediaTitleCallbackBySessionToken = mutableMapOf<MediaSession.Token, MediaTitleCallback>()

    // TODO: Validate
    @Volatile
    var lastReportedMediaTitle: String? = null
        private set

    // TODO: Validate
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { activeMediaControllers ->
            listenToMediaControllersOfListenedPackage(activeMediaControllers.orEmpty())
        }

    // TODO: Validate
    fun start(packageName: String) {
        mainHandler.post {
            stopListening()
            val activeMediaControllers =
                mediaSessions.startListeningToActiveSessions(activeSessionsChangedListener, mainHandler) ?: return@post
            listenedPackageName = packageName
            listenToMediaControllersOfListenedPackage(activeMediaControllers)
        }
    }

    // TODO: Validate
    fun stop() {
        mainHandler.post { stopListening() }
    }

    // TODO: Validate
    fun observationWithLastReportedMediaTitle(observation: MediaSessionObservation): MediaSessionObservation =
        if (observation.mediaTitle != null) observation else observation.copy(mediaTitle = lastReportedMediaTitle)

    // TODO: Validate
    private fun stopListening() {
        if (listenedPackageName == null) return
        listenedPackageName = null
        mediaSessions.stopListeningToActiveSessions(activeSessionsChangedListener)
        mediaTitleCallbackBySessionToken.values.forEach { mediaTitleCallback -> mediaTitleCallback.unregister() }
        mediaTitleCallbackBySessionToken.clear()
        lastReportedMediaTitle = null
    }

    // TODO: Validate
    private fun listenToMediaControllersOfListenedPackage(activeMediaControllers: List<MediaController>) {
        val packageNameBeingListenedTo = listenedPackageName ?: return
        val listenedPackageMediaControllers = activeMediaControllers.filter { it.packageName == packageNameBeingListenedTo }
        val activeSessionTokens = listenedPackageMediaControllers.map { it.sessionToken }.toSet()
        val goneMediaTitleCallbackBySessionToken = mediaTitleCallbackBySessionToken.filterKeys { it !in activeSessionTokens }
        for ((goneSessionToken, goneMediaTitleCallback) in goneMediaTitleCallbackBySessionToken) {
            goneMediaTitleCallback.unregister()
            mediaTitleCallbackBySessionToken.remove(goneSessionToken)
        }
        for (listenedPackageMediaController in listenedPackageMediaControllers) {
            mediaTitleCallbackBySessionToken.getOrPut(listenedPackageMediaController.sessionToken) {
                MediaTitleCallback(listenedPackageMediaController).also { mediaTitleCallback ->
                    listenedPackageMediaController.registerCallback(mediaTitleCallback, mainHandler)
                }
            }
            rememberMediaTitle(listenedPackageMediaController.metadata)
        }
    }

    // TODO: Validate
    private fun rememberMediaTitle(metadata: MediaMetadata?) {
        mediaTitleOfMetadata(metadata)?.let { reportedMediaTitle -> lastReportedMediaTitle = reportedMediaTitle }
    }

    // TODO: Validate
    private inner class MediaTitleCallback(private val mediaController: MediaController) : MediaController.Callback() {
        // TODO: Validate
        fun unregister() = mediaController.unregisterCallback(this)

        // TODO: Validate
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            rememberMediaTitle(metadata)
        }
    }
}
