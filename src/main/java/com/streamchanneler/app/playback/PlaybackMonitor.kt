// TODO: Validate
package com.streamchanneler.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.streamchanneler.app.data.AfterVideoEnds
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.mediasessions.MediaSessions
import com.streamchanneler.app.playback.endrules.P4OnScreenElementAppears
import com.streamchanneler.app.playback.endrules.PlaybackEndRule
import com.streamchanneler.app.playback.endrules.standardPlaybackEndRules
import com.streamchanneler.app.profiles.ProfilePicker
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.streamingapps.PlayNextEpisodeWhen
import com.streamchanneler.app.streamingapps.PlayerButtonKind
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.StreamingApps
import com.streamchanneler.app.streamingapps.activePlayNextEpisodeMoments
import com.streamchanneler.app.ui.guide.GuideActivity
import org.json.JSONObject

// TODO: Validate
private class MonitoredPlayback(
    val activePlayback: ActivePlayback,
    val streamingApp: StreamingApp?,
    val endDetector: PlaybackEndDetector,
    val playerButtonPresser: PlayerButtonPresser?,
) {
    // TODO: Validate
    var observedPackageName: String? = activePlayback.targetPackageName

    // TODO: Validate
    var hasSeenPlayingAppInFront = false
}

// TODO: Validate
class PlaybackMonitor(
    private val applicationContext: Context,
    private val localAppStorage: LocalAppStorage,
    private val mediaSessions: MediaSessions,
    internal val playerWithoutMediaSessionPlayback: PlayerWithoutMediaSessionPlayback,
    private val profilePicker: ProfilePicker,
) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private var monitoredPlayback: MonitoredPlayback? = null
    // TODO: Validate
    private val pollRunnable = Runnable { pollMediaSessions() }
    // TODO: Validate
    private val lastReportedMediaTitleListener = LastReportedMediaTitleListener(mediaSessions)

    // TODO: Validate
    val isMonitoring: Boolean
        get() = monitoredPlayback != null

    // TODO: Validate
    var playbackStartedPackageName: String? = null
        private set

    // TODO: Validate
    private var connectedNextEpisodeCountdown: NextEpisodeCountdown? = null

    // TODO: Validate
    fun connectNextEpisodeCountdown(connectingNextEpisodeCountdown: NextEpisodeCountdown) {
        connectedNextEpisodeCountdown = connectingNextEpisodeCountdown
    }

    // TODO: Validate
    fun disconnectNextEpisodeCountdown(disconnectingNextEpisodeCountdown: NextEpisodeCountdown) {
        if (connectedNextEpisodeCountdown === disconnectingNextEpisodeCountdown) connectedNextEpisodeCountdown = null
    }

    // TODO: Validate
    fun startMonitoring(activePlayback: ActivePlayback, monitoredStreamingApp: StreamingApp?) {
        val playbackToMonitor = activePlayback.copy(isMonitoring = true)
        saveActivePlayback(playbackToMonitor)
        startPolling(playbackToMonitor, monitoredStreamingApp)
    }

    // TODO: Validate
    fun rememberPlaybackWithoutMonitoring(activePlayback: ActivePlayback) {
        stopMonitoring()
        saveActivePlayback(activePlayback.copy(isMonitoring = false))
    }

    // TODO: Validate
    fun stopMonitoring() {
        stopPolling()
        readActivePlayback()?.let { saveActivePlayback(it.copy(isMonitoring = false)) }
    }

    // TODO: Validate
    fun resumeMonitoringIfNeeded() {
        if (monitoredPlayback != null) return
        val persistedPlayback = readActivePlayback() ?: return
        if (!persistedPlayback.isMonitoring) return
        if (System.currentTimeMillis() - persistedPlayback.launchedAt > RESUME_MONITORING_WITHIN) {
            stopMonitoring()
            return
        }
        val persistedStreamingApp = persistedPlayback.targetPackageName?.let { persistedTargetPackageName ->
            StreamingApps.installedAppWithPackageName(persistedTargetPackageName, applicationContext.packageManager)
        }
        startPolling(persistedPlayback, persistedStreamingApp)
    }

    // TODO: Validate
    fun readActivePlayback(): ActivePlayback? {
        val activePlaybackJson = localAppStorage.activePlaybackJson ?: return null
        return runCatching { ActivePlayback.fromJson(JSONObject(activePlaybackJson)) }.getOrNull()
    }

    // TODO: Validate
    private fun saveActivePlayback(activePlayback: ActivePlayback) {
        localAppStorage.activePlaybackJson = activePlayback.toJson().toString()
    }

    // TODO: Validate
    private fun stopPolling() {
        mainHandler.removeCallbacks(pollRunnable)
        lastReportedMediaTitleListener.stop()
        connectedNextEpisodeCountdown?.cancelSilently()
        monitoredPlayback?.playerButtonPresser?.close()
        monitoredPlayback = null
    }

    // TODO: Validate
    private fun startPolling(activePlayback: ActivePlayback, monitoredStreamingApp: StreamingApp?) {
        stopPolling()
        playbackStartedPackageName = null
        playerWithoutMediaSessionPlayback.resetForNewPlayback()
        activePlayback.targetPackageName?.let(lastReportedMediaTitleListener::start)
        monitoredPlayback = MonitoredPlayback(
            activePlayback = activePlayback,
            streamingApp = monitoredStreamingApp,
            endDetector = PlaybackEndDetector(
                launchedAt = activePlayback.launchedAt,
                videoDuration = activePlayback.playingVideo?.duration?.let { (it * 1000).toLong() },
                playbackEndRules = playbackEndRulesWithCheckedMoments(monitoredStreamingApp),
            ),
            playerButtonPresser = monitoredStreamingApp?.let { PlayerButtonPresser(it, localAppStorage) },
        )
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL)
    }

    // TODO: Validate
    internal fun playbackEndRulesWithCheckedMoments(monitoredStreamingApp: StreamingApp?): List<PlaybackEndRule> {
        if (monitoredStreamingApp == null) return standardPlaybackEndRules()
        val checkedPlayNextEpisodeMoments = localAppStorage
            .streamingSiteSettings(monitoredStreamingApp.site.pluginKey)
            .checkedPlayNextEpisodeMoments
        val checkedMomentEndRules =
            monitoredStreamingApp.activePlayNextEpisodeMoments(checkedPlayNextEpisodeMoments).mapNotNull { activeMoment ->
                when (activeMoment) {
                    PlayNextEpisodeWhen.SKIP_CREDITS_APPEARS ->
                        PlayerButtonKind.SKIP_CREDITS.onScreenElementOfStreamingApp(monitoredStreamingApp)?.let(::P4OnScreenElementAppears)
                    PlayNextEpisodeWhen.NEXT_EPISODE_BUTTON_APPEARS -> monitoredStreamingApp.nextEpisodeCard?.shownElement?.let(::P4OnScreenElementAppears)
                    PlayNextEpisodeWhen.EPISODE_ENDS -> null
                }
            }
        return checkedMomentEndRules + monitoredStreamingApp.playbackEndRules()
    }

    // TODO: Validate
    private fun pollMediaSessions() {
        val polledPlayback = monitoredPlayback ?: return
        val activeControllers = mediaSessions.activeMediaControllers() ?: run {
            Log.w(TAG, "Notification access is off, so video ends can't be detected")
            stopMonitoring()
            return
        }
        if (hasPlayingAppLeftForeground(polledPlayback)) {
            Log.i(TAG, "Another app came to the front, so the viewer left $playbackStartedPackageName")
            stopMonitoring()
            UpNextAnnouncer.shutdown()
            return
        }
        val targetPackageName = polledPlayback.activePlayback.targetPackageName
        if (targetPackageName != null && profilePicker.armedSessionWatchingApp(targetPackageName) != null) {
            mainHandler.postDelayed(pollRunnable, POLL_INTERVAL)
            return
        }
        val now = System.currentTimeMillis()
        val playerWithoutMediaSession = polledPlayback.streamingApp?.playerWithoutMediaSession
        val observedController =
            if (playerWithoutMediaSession != null) null else mediaControllerToWatch(polledPlayback, activeControllers, now)
        val observation = if (playerWithoutMediaSession != null) {
            targetPackageName?.let { playerWithoutMediaSessionPlayback.mediaSessionObservationOfPlayer(it, playerWithoutMediaSession) }
        } else {
            observedController?.let(::mediaSessionObservationOfController)
                ?.let(lastReportedMediaTitleListener::observationWithLastReportedMediaTitle)
        }
        val endDetector = polledPlayback.endDetector
        val hadStartedPlaying = endDetector.hasStartedPlaying
        val watchStatus = endDetector.watchStatusAfterPoll(now, observation)
        if (!hadStartedPlaying && endDetector.hasStartedPlaying) {
            Log.i(TAG, "Playback started in ${observation!!.packageName}")
            playbackStartedPackageName = observation.packageName
        }
        when (watchStatus) {
            WatchStatus.STILL_WATCHING -> {
                val playingPackageName = playbackStartedPackageName
                if (endDetector.hasStartedPlaying && playingPackageName != null) {
                    polledPlayback.playerButtonPresser?.pressShowingButtons(
                        playingPackageName,
                        now,
                        isAppPlaying = observation?.playbackStatus == MediaState.PLAYING,
                    )
                }
                mainHandler.postDelayed(pollRunnable, POLL_INTERVAL)
            }
            WatchStatus.FINISHED -> {
                Log.i(TAG, "Video finished in $targetPackageName: $observation")
                ScreenAutomationService.connectedService?.debugLogger?.writeWindowDump()
                finishVideoAfterCountdown(observedController)
            }
            WatchStatus.NEVER_STARTED -> {
                Log.i(TAG, "Playback never started in $targetPackageName")
                stopMonitoring()
            }
        }
    }

    // TODO: Validate
    private fun hasPlayingAppLeftForeground(polledPlayback: MonitoredPlayback): Boolean {
        val playingAppPackageName = playbackStartedPackageName ?: return false
        val foregroundPackageName = ScreenAutomationService.foregroundPackageName ?: return false
        if (foregroundPackageName == playingAppPackageName) {
            polledPlayback.hasSeenPlayingAppInFront = true
            return false
        }
        return polledPlayback.hasSeenPlayingAppInFront
    }

    // TODO: Validate
    private fun finishVideoAfterCountdown(observedController: MediaController?) {
        val nextEpisodeCountdownLength = localAppStorage.nextEpisodeCountdown
        val nextEpisodeCountdown = connectedNextEpisodeCountdown
        if (
            localAppStorage.afterVideoEnds != AfterVideoEnds.PLAY_NEXT_VIDEO ||
            nextEpisodeCountdownLength <= 0 ||
            nextEpisodeCountdown == null
        ) {
            pauseAndReturnToGuideAsFinished(observedController)
            return
        }
        nextEpisodeCountdown.start(
            nextEpisodeCountdownLength,
            onCountdownFinished = { pauseAndReturnToGuideAsFinished(observedController) },
            onCountdownCancelled = {
                Log.i(TAG, "The viewer cancelled the next episode countdown")
                stopMonitoring()
            },
        )
    }

    // TODO: Validate
    private fun pauseAndReturnToGuideAsFinished(observedController: MediaController?) {
        runCatching { observedController?.transportControls?.pause() }
        returnToGuide(PlaybackOutcome.FINISHED)
    }

    // TODO: Validate
    private fun mediaControllerToWatch(
        polledPlayback: MonitoredPlayback,
        activeControllers: List<MediaController>,
        now: Long,
    ): MediaController? {
        val otherAppControllers = activeControllers.filter { it.packageName != applicationContext.packageName }
        polledPlayback.observedPackageName?.let { packageName ->
            otherAppControllers.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        if (polledPlayback.endDetector.hasStartedPlaying) return null
        val millisSinceLaunch = now - polledPlayback.activePlayback.launchedAt
        val mayAdoptAnotherApp = polledPlayback.observedPackageName == null || millisSinceLaunch > ADOPT_OTHER_SESSION_AFTER
        if (!mayAdoptAnotherApp) return null
        val playingController = otherAppControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: return null
        Log.i(TAG, "Watching ${playingController.packageName} for the video's end")
        polledPlayback.observedPackageName = playingController.packageName
        return playingController
    }

    // TODO: Validate
    private fun returnToGuide(playbackOutcome: PlaybackOutcome) {
        stopMonitoring()
        val guideIntent = Intent(applicationContext, GuideActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PLAYBACK_OUTCOME, playbackOutcome.name)
        ForegroundReturner.bringToFront(applicationContext, guideIntent)
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val TAG = "PlaybackMonitor"

        // TODO: Validate
        private const val POLL_INTERVAL = 100L

        // TODO: Validate
        private const val ADOPT_OTHER_SESSION_AFTER = 15_000L

        // TODO: Validate
        private const val RESUME_MONITORING_WITHIN = 6 * 60 * 60_000L

        // TODO: Validate
        const val EXTRA_PLAYBACK_OUTCOME = "com.streamchanneler.app.PLAYBACK_OUTCOME"

        // TODO: Validate
        fun hasNotificationListenerAccess(context: Context): Boolean {
            val enabledListenerComponents =
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val watcherComponentName = ComponentName(context, PlaybackWatcherService::class.java)
            return enabledListenerComponents.split(":").any {
                ComponentName.unflattenFromString(it) == watcherComponentName
            }
        }
    }
}

// TODO: Validate
enum class PlaybackOutcome {
    FINISHED,
    LEFT_EARLY,
    ;

    // TODO: Validate
    companion object {
        // TODO: Validate
        fun fromName(playbackOutcomeName: String?): PlaybackOutcome? = entries.firstOrNull { it.name == playbackOutcomeName }
    }
}
