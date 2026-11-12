// TODO: Validate
package com.streamchanneler.app.mediasessions

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler

// TODO: Validate
class MediaSessions(context: Context, private val notificationListenerComponent: ComponentName) {
    // TODO: Validate
    private val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java)

    // TODO: Validate
    fun activeMediaControllers(): List<MediaController>? = try {
        mediaSessionManager.getActiveSessions(notificationListenerComponent)
    } catch (_: SecurityException) {
        null
    }

    // TODO: Validate
    fun isPackagePlaying(packageName: String): Boolean =
        activeMediaControllers().orEmpty().any { mediaController ->
            mediaController.packageName == packageName &&
                mediaController.playbackState?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
        }

    // TODO: Validate
    fun hasMediaSession(packageName: String): Boolean =
        activeMediaControllers().orEmpty().any { it.packageName == packageName }

    // TODO: Validate
    fun startListeningToActiveSessions(
        activeSessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener,
        listenerHandler: Handler,
    ): List<MediaController>? = try {
        mediaSessionManager.addOnActiveSessionsChangedListener(
            activeSessionsChangedListener,
            notificationListenerComponent,
            listenerHandler,
        )
        mediaSessionManager.getActiveSessions(notificationListenerComponent)
    } catch (_: SecurityException) {
        null
    }

    // TODO: Validate
    fun stopListeningToActiveSessions(activeSessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener) =
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
}
