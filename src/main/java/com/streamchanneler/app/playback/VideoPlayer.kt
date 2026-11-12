// TODO: Validate
package com.streamchanneler.app.playback

import android.content.Context
import com.streamchanneler.app.data.AfterVideoEnds
import com.streamchanneler.app.data.GuideChannel
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.GuideRepository
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.data.nextPlayableVideo
import com.streamchanneler.app.playback.archive.ArchiveOrgVideos
import com.streamchanneler.app.playback.archive.ArchivePlayerActivity
import com.streamchanneler.app.profiles.ProfilePicker
import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
data class NextVideo(val channel: GuideChannel, val video: GuideVideo)

// TODO: Validate
sealed interface VideoEndResult {
    // TODO: Validate
    val endedPlayback: ActivePlayback

    // TODO: Validate
    data class StoppedEarly(override val endedPlayback: ActivePlayback) : VideoEndResult

    // TODO: Validate
    data class Finished(override val endedPlayback: ActivePlayback) : VideoEndResult

    // TODO: Validate
    data class PlayNext(override val endedPlayback: ActivePlayback, val nextVideo: NextVideo) : VideoEndResult

    // TODO: Validate
    data class NoMoreVideos(override val endedPlayback: ActivePlayback) : VideoEndResult
}

// TODO: Validate
class VideoPlayer(
    private val applicationContext: Context,
    private val localAppStorage: LocalAppStorage,
    private val guideRepository: GuideRepository,
    private val playbackMonitor: PlaybackMonitor,
    private val profilePicker: ProfilePicker,
) {
    // TODO: Validate
    fun playVideo(launchingContext: Context, channel: GuideChannel, video: GuideVideo): VideoLaunchResult {
        val videoLaunchResult = launchAndFollowVideo(launchingContext, channel, video)
        if (videoLaunchResult is VideoLaunchResult.Launched) warmUpOrShutDownUpNextAnnouncer()
        return videoLaunchResult
    }

    // TODO: Validate
    private fun launchAndFollowVideo(launchingContext: Context, channel: GuideChannel, video: GuideVideo): VideoLaunchResult {
        val archiveVideoLink = ArchiveOrgVideos.archiveVideoLinkOfGuideVideo(video)
        if (archiveVideoLink != null) {
            val ownPackageName = applicationContext.packageName
            playbackMonitor.rememberPlaybackWithoutMonitoring(newMonitoredActivePlayback(channel, video, ownPackageName))
            launchingContext.startActivity(ArchivePlayerActivity.playArchiveVideoIntent(launchingContext, archiveVideoLink, video.title))
            return VideoLaunchResult.Launched(ownPackageName, launchedStreamingApp = null)
        }
        val streamingAppLaunchResult = StreamingAppLauncher.launchVideo(launchingContext, video)
        if (streamingAppLaunchResult is VideoLaunchResult.Launched) {
            val launchedActivePlayback = newMonitoredActivePlayback(channel, video, streamingAppLaunchResult.launchedPackageName)
            followLaunchedVideo(streamingAppLaunchResult, launchedActivePlayback)
        }
        return streamingAppLaunchResult
    }

    // TODO: Validate
    fun launchVideoAndPickProfile(launchingContext: Context, video: GuideVideo): VideoLaunchResult {
        val videoLaunchResult = StreamingAppLauncher.launchVideo(launchingContext, video)
        if (videoLaunchResult is VideoLaunchResult.Launched) armProfilePicker(videoLaunchResult.launchedStreamingApp, video)
        return videoLaunchResult
    }

    // TODO: Validate
    private fun armProfilePicker(launchedStreamingApp: StreamingApp?, launchedVideo: GuideVideo?) {
        profilePicker.arm(launchedStreamingApp) {
            if (launchedVideo != null) StreamingAppLauncher.launchVideo(applicationContext, launchedVideo)
        }
    }

    // TODO: Validate
    fun followLaunchedVideo(videoLaunch: VideoLaunchResult.Launched, activePlayback: ActivePlayback) {
        val launchedStreamingApp = videoLaunch.launchedStreamingApp
        armProfilePicker(launchedStreamingApp, activePlayback.playingVideo)
        if (!PlaybackMonitor.hasNotificationListenerAccess(applicationContext)) {
            playbackMonitor.rememberPlaybackWithoutMonitoring(activePlayback)
            return
        }
        playbackMonitor.startMonitoring(activePlayback, launchedStreamingApp)
    }

    // TODO: Validate
    private fun warmUpOrShutDownUpNextAnnouncer() {
        if (localAppStorage.afterVideoEnds == AfterVideoEnds.PLAY_NEXT_VIDEO && localAppStorage.showsUpNextInformation) {
            UpNextAnnouncer.warmUp(applicationContext)
        } else {
            UpNextAnnouncer.shutdown()
        }
    }

    // TODO: Validate
    fun endPlayback(playbackOutcome: PlaybackOutcome): VideoEndResult? {
        val endedPlayback = playbackMonitor.readActivePlayback() ?: return null
        val videoEndResult = when {
            playbackOutcome != PlaybackOutcome.FINISHED -> VideoEndResult.StoppedEarly(endedPlayback)
            localAppStorage.afterVideoEnds != AfterVideoEnds.PLAY_NEXT_VIDEO -> VideoEndResult.Finished(endedPlayback)
            else -> nextVideoInPlayedOrLatestOrder(endedPlayback, guideRepository.findChannel(endedPlayback.channelId))
                ?.let { VideoEndResult.PlayNext(endedPlayback, it) }
                ?: VideoEndResult.NoMoreVideos(endedPlayback)
        }
        if (videoEndResult !is VideoEndResult.PlayNext) UpNextAnnouncer.shutdown()
        return videoEndResult
    }

    // TODO: Validate
    fun declineNextVideo() = UpNextAnnouncer.shutdown()

    // TODO: Validate
    fun stopFollowingAfterUserReturned() {
        val activePlayback = playbackMonitor.readActivePlayback()
        val millisSinceLaunch = System.currentTimeMillis() - (activePlayback?.launchedAt ?: 0)
        if (playbackMonitor.isMonitoring && millisSinceLaunch > MANUAL_RETURN_GRACE) {
            playbackMonitor.stopMonitoring()
            UpNextAnnouncer.shutdown()
        }
    }

    // TODO: Validate
    private fun newMonitoredActivePlayback(channel: GuideChannel, video: GuideVideo, targetPackageName: String?): ActivePlayback =
        ActivePlayback(
            channelId = channel.id,
            channelName = channel.name,
            channelVideos = channel.videos,
            playingEpisodeId = video.episodeId,
            targetPackageName = targetPackageName,
            launchedAt = System.currentTimeMillis(),
            isMonitoring = true,
        )

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val MANUAL_RETURN_GRACE = 3_000L
    }
}

// TODO: Validate
fun nextVideoInPlayedOrLatestOrder(endedPlayback: ActivePlayback, latestChannel: GuideChannel?): NextVideo? {
    val playedChannel = GuideChannel(
        id = endedPlayback.channelId,
        name = endedPlayback.channelName,
        number = null,
        defaultOrder = latestChannel?.defaultOrder,
        videos = endedPlayback.channelVideos,
    )
    nextPlayableVideo(playedChannel, endedPlayback.playingEpisodeId)?.let { return NextVideo(playedChannel, it) }
    return latestChannel?.let { latestGuideChannel ->
        nextPlayableVideo(latestGuideChannel, endedPlayback.playingEpisodeId)?.let { NextVideo(latestGuideChannel, it) }
    }
}
