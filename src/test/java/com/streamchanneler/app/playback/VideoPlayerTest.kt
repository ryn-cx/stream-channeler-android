// TODO: Validate
package com.streamchanneler.app.playback

import com.streamchanneler.app.data.GuideChannel
import com.streamchanneler.app.data.GuideVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// TODO: Validate
class VideoPlayerTest {
    // TODO: Validate
    private fun netflixGuideVideo(episodeId: String) =
        GuideVideo(episodeId, "Title", "", "https://netflix.com/watch/$episodeId", null, 60.0, null, null, "Netflix", false, titleId = "test", sourceKey = "test")

    // TODO: Validate
    private fun endedActivePlayback(playedEpisodeIds: List<String>, endedEpisodeId: String) = ActivePlayback(
        channelId = "channel",
        channelName = "Channel",
        channelVideos = playedEpisodeIds.map(::netflixGuideVideo),
        playingEpisodeId = endedEpisodeId,
        targetPackageName = "com.netflix.ninja",
        launchedAt = 0,
        isMonitoring = true,
    )

    // TODO: Validate
    private fun latestGuideChannelWithEpisodes(latestEpisodeIds: List<String>) =
        GuideChannel("channel", "Channel", 1.0, "shuffled", latestEpisodeIds.map(::netflixGuideVideo))

    // TODO: Validate
    @Test
    fun prefersTheEpisodeOrderSavedAtLaunchOverAReshuffledGuide() {
        val nextVideoAfterEndedPlayback = nextVideoInPlayedOrLatestOrder(
            endedActivePlayback(listOf("a", "b", "c"), endedEpisodeId = "a"),
            latestGuideChannelWithEpisodes(listOf("a", "c", "b")),
        )
        assertEquals("b", nextVideoAfterEndedPlayback?.video?.episodeId)
        assertEquals("shuffled", nextVideoAfterEndedPlayback?.channel?.defaultOrder)
    }

    // TODO: Validate
    @Test
    fun fallsBackToTheLatestGuideWhenTheSavedOrderHasNoNextEpisode() {
        val latestChannel = latestGuideChannelWithEpisodes(listOf("a", "b", "c"))
        val nextVideoAfterEndedPlayback = nextVideoInPlayedOrLatestOrder(
            endedActivePlayback(listOf("a", "b"), endedEpisodeId = "b"),
            latestChannel,
        )
        assertEquals("c", nextVideoAfterEndedPlayback?.video?.episodeId)
        assertEquals(latestChannel, nextVideoAfterEndedPlayback?.channel)
    }

    // TODO: Validate
    @Test
    fun hasNoNextVideoAfterTheLastEpisodeOfBothOrders() {
        assertNull(nextVideoInPlayedOrLatestOrder(endedActivePlayback(listOf("a", "b"), endedEpisodeId = "b"), latestGuideChannelWithEpisodes(listOf("a", "b"))))
        assertNull(nextVideoInPlayedOrLatestOrder(endedActivePlayback(listOf("a", "b"), endedEpisodeId = "b"), latestChannel = null))
    }
}
