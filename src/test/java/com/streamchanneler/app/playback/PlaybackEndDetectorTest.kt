// TODO: Validate
package com.streamchanneler.app.playback

import com.streamchanneler.app.playback.endrules.P4OnScreenElementAppears
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.playback.endrules.PlaybackEndRule
import com.streamchanneler.app.playback.endrules.P7PositionReset
import com.streamchanneler.app.playback.endrules.standardPlaybackEndRules
import com.streamchanneler.app.screenautomation.ViewIdAndTextOnScreen
import com.streamchanneler.app.screenautomation.ViewIdOnScreen
import com.streamchanneler.app.streamingapps.primeVideoSite
import org.junit.Assert.assertEquals
import org.junit.Test

// TODO: Validate
class PlaybackEndDetectorTest {
    // TODO: Validate
    private val launchedAt = 1_000_000L

    // TODO: Validate
    private val videoDuration = 30 * 60_000L

    // TODO: Validate
    private fun netflixMediaSessionObservation(
        playbackStatus: MediaState,
        position: Long,
        duration: Long? = videoDuration,
        mediaTitle: String? = "Episode 1",
        playbackSpeed: Float = 1f,
    ) = MediaSessionObservation("com.netflix.ninja", playbackStatus, position, duration, mediaTitle, playbackSpeed)

    // TODO: Validate
    private fun newPlaybackEndDetector(playbackEndRules: List<PlaybackEndRule> = standardPlaybackEndRules()) =
        PlaybackEndDetector(launchedAt, videoDuration, playbackEndRules)

    // TODO: Validate
    private fun disneyPlusObservation(position: Long) =
        netflixMediaSessionObservation(MediaState.PLAYING, position, duration = null, mediaTitle = "The Simpsons")

    // TODO: Validate
    @Test
    fun disneyPlusAutoplayingTheNextEpisodeFinishes() {
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = listOf(P7PositionReset()))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, disneyPlusObservation(0))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 1_276_000, disneyPlusObservation(1_276_402)),
        )
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 1_282_000, disneyPlusObservation(1_367)),
        )
    }

    // TODO: Validate
    @Test
    fun disneyPlusRestartingBeforeHalfwayKeepsWatching() {
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = listOf(P7PositionReset()))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, disneyPlusObservation(0))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 840_000, disneyPlusObservation(14 * 60_000L))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 846_000, disneyPlusObservation(1_000)),
        )
    }

    // TODO: Validate
    @Test
    fun disneyPlusRewindingPartWayKeepsWatching() {
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = listOf(P7PositionReset()))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, disneyPlusObservation(0))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 600_000, disneyPlusObservation(10 * 60_000L))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 610_000, disneyPlusObservation(3 * 60_000L)),
        )
    }

    // TODO: Validate
    @Test
    fun finishesWhenPositionReachesTheEnd() {
        val playbackEndDetector = newPlaybackEndDetector()
        assertEquals(WatchStatus.STILL_WATCHING, playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0)))
        assertEquals(WatchStatus.STILL_WATCHING, playbackEndDetector.watchStatusAfterPoll(launchedAt + 60_000, netflixMediaSessionObservation(MediaState.PLAYING, 20 * 60_000L)))
        assertEquals(WatchStatus.FINISHED, playbackEndDetector.watchStatusAfterPoll(launchedAt + 90_000, netflixMediaSessionObservation(MediaState.PLAYING, videoDuration - 1_000)))
    }

    // TODO: Validate
    @Test
    fun stoppingCountsAsFinishedRightAway() {
        val playbackEndDetector = newPlaybackEndDetector()
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 5 * 60_000L))
        assertEquals(WatchStatus.FINISHED, playbackEndDetector.watchStatusAfterPoll(launchedAt + 6_000, null))
    }

    // TODO: Validate
    @Test
    fun stoppingCountsAsFinishedAfterTheMinimumStopDuration() {
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = listOf(P3StoppedSession(minimumStopDuration = 4_000L)))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 5 * 60_000L))
        assertEquals(WatchStatus.STILL_WATCHING, playbackEndDetector.watchStatusAfterPoll(launchedAt + 6_000, null))
        assertEquals(WatchStatus.FINISHED, playbackEndDetector.watchStatusAfterPoll(launchedAt + 10_000, null))
    }

    // TODO: Validate
    @Test
    fun pausingNeverEndsTheVideo() {
        val playbackEndDetector = newPlaybackEndDetector()
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 60_000))
        repeat(20) { secondsPaused ->
            assertEquals(
                WatchStatus.STILL_WATCHING,
                playbackEndDetector.watchStatusAfterPoll(launchedAt + 6_000 + secondsPaused * 60_000L, netflixMediaSessionObservation(MediaState.PAUSED, 60_000)),
            )
        }
    }

    // TODO: Validate
    @Test
    fun shortAdBreaksDoNotEndTheVideo() {
        val playbackEndDetector = newPlaybackEndDetector()
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 35_000, netflixMediaSessionObservation(MediaState.PLAYING, 29_500, duration = 30_000, mediaTitle = "Ad")),
        )
    }

    // TODO: Validate
    @Test
    fun differentTitleFinishes() {
        val playbackEndDetector = newPlaybackEndDetector()
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 10_000, netflixMediaSessionObservation(MediaState.PLAYING, 5 * 60_000L))
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 15_000, netflixMediaSessionObservation(MediaState.PLAYING, 0, mediaTitle = "Episode 2")),
        )
    }

    // TODO: Validate
    @Test
    fun youTubeTitleCatchingUpRightAfterPlaybackStartsKeepsWatching() {
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = listOf(P1MediaTitleChange(realTitleWaitTime = 5_000L)))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0, mediaTitle = "Previous video"))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 6_000, netflixMediaSessionObservation(MediaState.PLAYING, 1_000)),
        )
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 20_000, netflixMediaSessionObservation(MediaState.PLAYING, 0, mediaTitle = "Episode 2")),
        )
    }

    // TODO: Validate
    @Test
    fun givesUpWhenPlaybackNeverStarts() {
        val playbackEndDetector = newPlaybackEndDetector()
        assertEquals(WatchStatus.STILL_WATCHING, playbackEndDetector.watchStatusAfterPoll(launchedAt + 60_000, null))
        assertEquals(WatchStatus.NEVER_STARTED, playbackEndDetector.watchStatusAfterPoll(launchedAt + 4 * 60_000L, null))
    }

    // TODO: Validate
    private fun primeVideoObservation(playbackStatus: MediaState, position: Long, mediaId: String) =
        MediaSessionObservation(
            "com.amazon.amazonvideo.livingroom",
            playbackStatus,
            position,
            duration = null,
            mediaTitle = null,
            playbackSpeed = 1f,
            mediaId = mediaId,
        )

    // TODO: Validate
    @Test
    fun primeVideoAutoplayingTheNextEpisodeFinishes() {
        val primeVideoTvApp =
            primeVideoSite.apps.single { primeVideoApp -> primeVideoApp.packageName == "com.amazon.amazonvideo.livingroom" }
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = primeVideoTvApp.playbackEndRules())
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, primeVideoObservation(MediaState.PLAYING, 1_190_248, "episode 1"))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 100_000, primeVideoObservation(MediaState.PLAYING, 1_290_000, "episode 1"))
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 101_000, primeVideoObservation(MediaState.BUFFERING, 0, "episode 2")),
        )
    }

    // TODO: Validate
    @Test
    fun primeVideoPrerollAdKeepsWatching() {
        val primeVideoTvApp =
            primeVideoSite.apps.single { primeVideoApp -> primeVideoApp.packageName == "com.amazon.amazonvideo.livingroom" }
        val playbackEndDetector = newPlaybackEndDetector(playbackEndRules = primeVideoTvApp.playbackEndRules())
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, primeVideoObservation(MediaState.PLAYING, 0, "ad"))
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 35_000, primeVideoObservation(MediaState.PLAYING, 30_000, "ad"))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 36_000, primeVideoObservation(MediaState.PLAYING, 0, "episode 1")),
        )
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 1_000_000, primeVideoObservation(MediaState.PLAYING, 20 * 60_000L, "episode 1"))
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 1_001_000, primeVideoObservation(MediaState.PLAYING, 0, "episode 2")),
        )
    }

    // TODO: Validate
    @Test
    fun upNextCardOnScreenFinishes() {
        var isUpNextCardShowing = false
        val playbackEndDetector = newPlaybackEndDetector(
            playbackEndRules = listOf(
                P4OnScreenElementAppears(ViewIdOnScreen("upNextCardLabelText")) { _, _ -> isUpNextCardShowing },
            ),
        )
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 10_000, netflixMediaSessionObservation(MediaState.PLAYING, 5 * 60_000L)),
        )
        isUpNextCardShowing = true
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 11_000, netflixMediaSessionObservation(MediaState.PLAYING, videoDuration - 9_000)),
        )
    }

    // TODO: Validate
    @Test
    fun skipCreditsButtonFinishesButSkipIntroDoesNot() {
        var skipButtonText = "Skip Intro"
        val skipCreditsButton = ViewIdAndTextOnScreen("skip_segment_button", "SKIP CREDITS")
        val playbackEndDetector = newPlaybackEndDetector(
            playbackEndRules = listOf(
                P4OnScreenElementAppears(skipCreditsButton) { _, wantedOnScreenElement ->
                    wantedOnScreenElement === skipCreditsButton && skipButtonText.equals("SKIP CREDITS", ignoreCase = true)
                },
            ),
        )
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0))
        assertEquals(
            WatchStatus.STILL_WATCHING,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 60_000, netflixMediaSessionObservation(MediaState.PLAYING, 55_000)),
        )
        skipButtonText = "Skip Credits"
        assertEquals(
            WatchStatus.FINISHED,
            playbackEndDetector.watchStatusAfterPoll(launchedAt + 61_000, netflixMediaSessionObservation(MediaState.PLAYING, videoDuration - 60_000)),
        )
    }

    // TODO: Validate
    @Test
    fun endCardWhileStoppedFinishes() {
        val endCard = ViewIdOnScreen("up_next_play_btn")
        val playbackEndDetector = newPlaybackEndDetector(
            playbackEndRules = listOf(P4OnScreenElementAppears(endCard) { _, _ -> true }),
        )
        playbackEndDetector.watchStatusAfterPoll(launchedAt + 5_000, netflixMediaSessionObservation(MediaState.PLAYING, 0))
        val stoppedAtEndObservation = netflixMediaSessionObservation(MediaState.STOPPED, videoDuration)
        assertEquals(WatchStatus.FINISHED, playbackEndDetector.watchStatusAfterPoll(launchedAt + 10_000, stoppedAtEndObservation))
    }

    // TODO: Validate
    @Test
    fun onScreenElementWhilePausedFinishesOnlyWhenCheckedWhilePaused() {
        val endTime = ViewIdAndTextOnScreen("current_time", "--")
        val uncheckedWhilePausedEndDetector = newPlaybackEndDetector(
            playbackEndRules = listOf(P4OnScreenElementAppears(endTime) { _, _ -> true }),
        )
        val checkedWhilePausedEndDetector = newPlaybackEndDetector(
            playbackEndRules = listOf(P4OnScreenElementAppears(endTime, checkWhilePaused = true) { _, _ -> true }),
        )
        val playingObservation = netflixMediaSessionObservation(MediaState.PLAYING, 0)
        val pausedAtEndObservation = netflixMediaSessionObservation(MediaState.PAUSED, 0)
        uncheckedWhilePausedEndDetector.watchStatusAfterPoll(launchedAt + 5_000, playingObservation)
        checkedWhilePausedEndDetector.watchStatusAfterPoll(launchedAt + 5_000, playingObservation)
        assertEquals(
            WatchStatus.STILL_WATCHING,
            uncheckedWhilePausedEndDetector.watchStatusAfterPoll(launchedAt + 10_000, pausedAtEndObservation),
        )
        assertEquals(
            WatchStatus.FINISHED,
            checkedWhilePausedEndDetector.watchStatusAfterPoll(launchedAt + 10_000, pausedAtEndObservation),
        )
    }
}
