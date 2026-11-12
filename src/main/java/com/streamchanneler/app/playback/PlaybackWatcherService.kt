// TODO: Validate
package com.streamchanneler.app.playback

import android.service.notification.NotificationListenerService
import com.streamchanneler.app.streamChannelerApp

// TODO: Validate
class PlaybackWatcherService : NotificationListenerService() {
    // TODO: Validate
    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        streamChannelerApp.playbackMonitor.resumeMonitoringIfNeeded()
    }

    // TODO: Validate
    override fun onListenerDisconnected() {
        isListenerConnected = false
        super.onListenerDisconnected()
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        @Volatile
        var isListenerConnected = false
            private set
    }
}
