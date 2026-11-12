// TODO: Validate
package com.streamchanneler.app.ui.guide

import com.streamchanneler.app.data.ApiException
import com.streamchanneler.app.data.GuideChannel
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.describeLoadError
import com.streamchanneler.app.data.isSignInRejectedError
import com.streamchanneler.app.data.nextPlayableVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// TODO: Validate
class GuideLogicTest {
    // TODO: Validate
    private fun netflixGuideVideoWithDuration(episodeId: String, duration: Double?) =
        GuideVideo(episodeId, "Title", "", "https://netflix.com/watch/$episodeId", null, duration, null, null, "Netflix", false, titleId = "test", sourceKey = "test")

    // TODO: Validate
    @Test
    fun schedulesVideosBackToBackWithAFiveMinuteMinimum() {
        val scheduledVideos = scheduleChannelVideos(
            listOf(netflixGuideVideoWithDuration("a", 2640.0), netflixGuideVideoWithDuration("b", 6.0), netflixGuideVideoWithDuration("c", null)),
            minimumVideoLength = 5,
        )
        assertEquals(listOf(0, 44, 49), scheduledVideos.map { it.startMinute })
        assertEquals(listOf(44, 5, 30), scheduledVideos.map { it.length })
    }

    // TODO: Validate
    @Test
    fun schedulesVideosByTheirTextLengthWhenAsked() {
        val textFittingLengthByEpisodeId = mapOf("a" to 7, "b" to 3)
        val scheduledVideos = scheduleChannelVideos(
            listOf(netflixGuideVideoWithDuration("a", 2640.0), netflixGuideVideoWithDuration("b", null)),
            minimumVideoLength = 15,
            textFittingLength = { video -> textFittingLengthByEpisodeId.getValue(video.episodeId) },
        )
        assertEquals(listOf(0, 7), scheduledVideos.map { it.startMinute })
        assertEquals(listOf(7, 3), scheduledVideos.map { it.length })
    }

    // TODO: Validate
    @Test
    fun schedulesVeryShortVideosAtLeastOneMinuteLongWithoutAMinimum() {
        val scheduledVideos = scheduleChannelVideos(listOf(netflixGuideVideoWithDuration("a", 6.0)), minimumVideoLength = 0)
        assertEquals(listOf(1), scheduledVideos.map { it.length })
    }

    // TODO: Validate
    @Test
    fun nextPlayableVideoFollowsTheCurrentEpisode() {
        val channel = GuideChannel("channel", "Channel", 1.0, null, listOf(netflixGuideVideoWithDuration("a", 60.0), netflixGuideVideoWithDuration("b", 60.0), netflixGuideVideoWithDuration("c", 60.0)))
        assertEquals("b", nextPlayableVideo(channel, "a")?.episodeId)
        assertNull(nextPlayableVideo(channel, "c"))
        assertEquals("a", nextPlayableVideo(channel, "missing")?.episodeId)
    }

    // TODO: Validate
    @Test
    fun nextPlayableVideoSkipsVideosWithoutALink() {
        val videoWithoutLink = netflixGuideVideoWithDuration("b", 60.0).copy(url = null)
        val channel = GuideChannel("channel", "Channel", 1.0, null, listOf(netflixGuideVideoWithDuration("a", 60.0), videoWithoutLink, netflixGuideVideoWithDuration("c", 60.0)))
        assertEquals("c", nextPlayableVideo(channel, "a")?.episodeId)
    }

    // TODO: Validate
    private fun squareLogoOnTransparency(logoArgb: Int): IntArray =
        IntArray(10 * 10) { pixelIndex ->
            val pixelX = pixelIndex % 10
            val pixelY = pixelIndex / 10
            if (pixelX in 3..6 && pixelY in 3..6) logoArgb else 0x00000000
        }

    // TODO: Validate
    @Test
    fun darkLogosOnTransparencyNeedALightBackground() {
        assertTrue(hasDarkEdgesOnTransparency(squareLogoOnTransparency(0xFF101010.toInt()), 10, 10))
        assertFalse(hasDarkEdgesOnTransparency(squareLogoOnTransparency(0xFFF0F0F0.toInt()), 10, 10))
        assertFalse(hasDarkEdgesOnTransparency(IntArray(10 * 10) { 0xFF101010.toInt() }, 10, 10))
    }

    // TODO: Validate
    @Test
    fun loadErrorsDescribeExpiredSignInsAndServerAnswers() {
        assertEquals("your sign-in has expired", describeLoadError(ApiException(403, "Could not validate credentials", isSignInRejected = true)))
        assertTrue(isSignInRejectedError(java.util.concurrent.ExecutionException(ApiException(401, "Unauthorized", isSignInRejected = true))))
        assertFalse(isSignInRejectedError(ApiException(403, "Forbidden")))
        assertEquals("the server answered 500: Internal Server Error", describeLoadError(ApiException(500, "Internal Server Error")))
        assertEquals("the server took too long to answer", describeLoadError(java.net.SocketTimeoutException("timeout")))
    }
}
