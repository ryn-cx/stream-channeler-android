// TODO: Validate
package com.streamchanneler.app.playback

import android.util.Log
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.streamingapps.PlayNextEpisodeWhen
import com.streamchanneler.app.streamingapps.PlayerButtonKind
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.activePlayNextEpisodeMoments

// TODO: Validate
class PlayerButtonPresser(private val streamingApp: StreamingApp, private val localAppStorage: LocalAppStorage) {
    // TODO: Validate
    private val skipButtonClickerByPlayerButtonKind: Map<PlayerButtonKind, SkipButtonClicker> =
        PlayerButtonKind.entries.filter { it.canBeClicked }.mapNotNull { clickablePlayerButtonKind ->
            clickablePlayerButtonKind.onScreenElementOfStreamingApp(streamingApp)?.let { skipButton ->
                clickablePlayerButtonKind to SkipButtonClicker(skipButton)
            }
        }.toMap()

    // TODO: Validate
    private val screenshotSkipButtonClicker: ScreenshotSkipButtonClicker? = streamingApp
        .takeIf { it.skipButtonTextsInScreenshots.isNotEmpty() || it.nextEpisodeAutoplayCancelButtonsInScreenshots.isNotEmpty() }
        ?.let { ScreenshotSkipButtonClicker(it.skipButtonTextsInScreenshots, it.nextEpisodeAutoplayCancelButtonsInScreenshots) }

    // TODO: Validate
    private val nextEpisodeAutoplayCanceller = NextEpisodeAutoplayCanceller.ofStreamingApp(streamingApp)

    // TODO: Validate
    private val hasButtonsToPress = skipButtonClickerByPlayerButtonKind.isNotEmpty() ||
        screenshotSkipButtonClicker != null ||
        nextEpisodeAutoplayCanceller != null

    // TODO: Validate
    private var lastButtonCheckAt = 0L

    // TODO: Validate
    fun pressShowingButtons(playingPackageName: String, now: Long, isAppPlaying: Boolean) {
        if (!hasButtonsToPress || now - lastButtonCheckAt < BUTTON_CHECK_INTERVAL) return
        lastButtonCheckAt = now
        val streamingSiteSettings = localAppStorage.streamingSiteSettings(streamingApp.site.pluginKey)
        for ((playerButtonKind, skipButtonClicker) in skipButtonClickerByPlayerButtonKind) {
            if (playerButtonKind !in streamingSiteSettings.clickedPlayerButtons) continue
            if (skipButtonClicker.clickWhenShowing(playingPackageName, now)) {
                Log.i(TAG, "Clicked the ${playerButtonKind.label} button in $playingPackageName")
            }
        }
        val cancelsNextEpisodeAutoplay = PlayNextEpisodeWhen.NEXT_EPISODE_BUTTON_APPEARS !in
            streamingApp.activePlayNextEpisodeMoments(streamingSiteSettings.checkedPlayNextEpisodeMoments)
        screenshotSkipButtonClicker?.startClickingShownButtons(
            playingPackageName,
            streamingSiteSettings.clickedPlayerButtons,
            clicksNextEpisodeAutoplayCancelButton = cancelsNextEpisodeAutoplay,
            isAppPlaying = isAppPlaying,
        )
        if (cancelsNextEpisodeAutoplay) nextEpisodeAutoplayCanceller?.cancelWhenShowing(playingPackageName)
    }

    // TODO: Validate
    fun close() {
        screenshotSkipButtonClicker?.close()
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "PlaybackMonitor"

        // TODO: Validate
        const val BUTTON_CHECK_INTERVAL = 1_000L
    }
}
