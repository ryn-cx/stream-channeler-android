// TODO: Validate
package com.streamchanneler.app

import android.app.Application
import android.content.ComponentName
import android.media.AudioManager
import com.streamchanneler.app.data.AccountSettingsRepository
import com.streamchanneler.app.data.GuideRepository
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.data.StreamChannelerApi
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.playback.NextEpisodeCountdown
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.playback.PlaybackWatcherService
import com.streamchanneler.app.playback.PlayerWithoutMediaSessionPlayback
import com.streamchanneler.app.playback.VideoPlayer
import com.streamchanneler.app.profiles.ProfilePicker
import com.streamchanneler.app.profiles.ProfilePickerAutomation
import com.streamchanneler.app.screenautomation.ForegroundActivityTracker
import com.streamchanneler.app.screenautomation.ScreenAutomationFeature
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.ui.common.ImageLoader
import java.io.File

// TODO: Validate
class StreamChannelerApp : Application() {
    // TODO: Validate
    lateinit var localAppStorage: LocalAppStorage
        private set
    // TODO: Validate
    lateinit var api: StreamChannelerApi
        private set
    // TODO: Validate
    lateinit var accountSettings: AccountSettingsRepository
        private set
    // TODO: Validate
    lateinit var guideRepository: GuideRepository
        private set
    // TODO: Validate
    lateinit var imageLoader: ImageLoader
        private set
    // TODO: Validate
    lateinit var mediaSessions: MediaSessions
        private set
    // TODO: Validate
    lateinit var playbackMonitor: PlaybackMonitor
        private set
    // TODO: Validate
    lateinit var profilePicker: ProfilePicker
        private set
    // TODO: Validate
    lateinit var videoPlayer: VideoPlayer
        private set
    // TODO: Validate
    private lateinit var foregroundActivityTracker: ForegroundActivityTracker

    // TODO: Validate
    override fun onCreate() {
        super.onCreate()
        localAppStorage = LocalAppStorage(this)
        api = StreamChannelerApi(
            readAccessToken = { localAppStorage.accessToken },
            readLocalSourcePreferencesJson = { localAppStorage.localSourcePreferencesJson },
        )
        accountSettings = AccountSettingsRepository(api, localAppStorage)
        guideRepository = GuideRepository(api, localAppStorage, accountSettings, File(filesDir, "guide-cache.json"))
        imageLoader = ImageLoader()
        mediaSessions = MediaSessions(this, ComponentName(this, PlaybackWatcherService::class.java))
        val audioManager = getSystemService(AudioManager::class.java)
        foregroundActivityTracker = ForegroundActivityTracker(packageManager)
        val playerWithoutMediaSessionPlayback = PlayerWithoutMediaSessionPlayback(foregroundActivityTracker, audioManager)
        profilePicker = ProfilePicker()
        playbackMonitor = PlaybackMonitor(this, localAppStorage, mediaSessions, playerWithoutMediaSessionPlayback, profilePicker)
        videoPlayer = VideoPlayer(this, localAppStorage, guideRepository, playbackMonitor, profilePicker)
        playbackMonitor.resumeMonitoringIfNeeded()
    }

    // TODO: Validate
    fun createScreenAutomationFeatures(screenAutomationService: ScreenAutomationService): List<ScreenAutomationFeature> =
        listOf(
            foregroundActivityTracker,
            NextEpisodeCountdown(screenAutomationService, playbackMonitor),
            ProfilePickerAutomation(screenAutomationService, profilePicker, localAppStorage, mediaSessions),
        )
}

// TODO: Validate
val android.content.Context.streamChannelerApp: StreamChannelerApp
    get() = applicationContext as StreamChannelerApp
