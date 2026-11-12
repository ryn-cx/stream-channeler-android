// TODO: Validate
package com.streamchanneler.app.playback

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import com.streamchanneler.app.screenautomation.ForegroundActivityTracker
import com.streamchanneler.app.screenautomation.ScreenAutomationService

// TODO: Validate
data class PlayerWithoutMediaSession(
    val playerActivityClassName: String,
    val loadingViewId: String?,
    val controlsViewId: String,
    val adViewId: String?,
)

// TODO: Validate
class PlayerWithoutMediaSessionPlayback(
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val audioManager: AudioManager,
) {
    // TODO: Validate
    private var playerVideoNumber = 0
    // TODO: Validate
    private var existingSoundPlayer: AudioPlaybackConfiguration? = null
    // TODO: Validate
    private var playbackStarted = false
    // TODO: Validate
    private var hasShownControlsOrAdSinceLastPlaying = false

    // TODO: Validate
    fun resetForNewPlayback() {
        playerVideoNumber = 0
        existingSoundPlayer = null
        playbackStarted = false
        hasShownControlsOrAdSinceLastPlaying = false
    }

    // TODO: Validate
    fun mediaSessionObservationOfPlayer(packageName: String, playerWithoutMediaSession: PlayerWithoutMediaSession): MediaSessionObservation? {
        val observedForegroundPackageName = ScreenAutomationService.foregroundPackageName ?: return null
        val observedPlaybackStatus = when {
            observedForegroundPackageName != packageName -> MediaState.PAUSED
            foregroundActivityTracker.lastActivityClassName(packageName) != playerWithoutMediaSession.playerActivityClassName -> MediaState.STOPPED
            else -> playbackStatusOfPlayer(playerWithoutMediaSession)
        }

        return MediaSessionObservation(
            packageName = packageName,
            playbackStatus = observedPlaybackStatus,
            position = 0L,
            duration = null,
            mediaTitle = null,
            playbackSpeed = if (observedPlaybackStatus == MediaState.PLAYING) 1f else 0f,
            playerVideoNumber = playerVideoNumber,
        )
    }

    // TODO: Validate
    private fun playbackStatusOfPlayer(playerWithoutMediaSession: PlayerWithoutMediaSession): MediaState {
        val isAdShowing = playerWithoutMediaSession.adViewId?.let(::isViewShowing) == true
        if (isAdShowing || isViewShowing(playerWithoutMediaSession.controlsViewId)) hasShownControlsOrAdSinceLastPlaying = true

        if (playerWithoutMediaSession.loadingViewId?.let(::isViewShowing) == true) {
            playbackStarted = true
            return MediaState.BUFFERING
        }

        val soundPlayers =
            audioManager.activePlaybackConfigurations.filter { audioPlaybackConfiguration ->
                audioPlaybackConfiguration.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
            }

        if (soundPlayers.isEmpty()) return MediaState.PAUSED
        if (soundPlayers.size > 1) return MediaState.PLAYING
        if (isAdShowing) return MediaState.PLAYING
        
        
        val onlySoundPlayer = soundPlayers.single()
        val newSoundPlayerDetected = existingSoundPlayer != null && onlySoundPlayer != existingSoundPlayer
        if (newSoundPlayerDetected && playbackStarted && !hasShownControlsOrAdSinceLastPlaying) playerVideoNumber++

        existingSoundPlayer = onlySoundPlayer
        playbackStarted = false
        hasShownControlsOrAdSinceLastPlaying = false
        return MediaState.PLAYING
    }

    // TODO: Validate
    private fun isViewShowing(viewId: String): Boolean =
        ScreenAutomationService.connectedService?.rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId(viewId)
            ?.any { viewNode -> viewNode.isVisibleToUser } == true
}
