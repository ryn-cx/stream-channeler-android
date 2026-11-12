// TODO: Validate
package com.streamchanneler.app.ui.upnext

import org.junit.Assert.assertEquals
import org.junit.Test

// TODO: Validate
class UpNextAnnouncementTest {
    // TODO: Validate
    @Test
    fun saysTitleAndEpisodeNameWithoutSeasonAndEpisodeNumbers() {
        assertEquals(
            "Up next: Blue Ribbon Baking Championship. Crowning the Champion.",
            upNextAnnouncement("Blue Ribbon Baking Championship", "S1 Ep8 · Crowning the Champion"),
        )
    }

    // TODO: Validate
    @Test
    fun saysOnlyTitleWhenSubtitleIsOnlyNumbers() {
        assertEquals("Up next: Bluey.", upNextAnnouncement("Bluey", "S2 Ep14"))
    }

    // TODO: Validate
    @Test
    fun keepsSubtitlesThatAreNotEpisodeNumbers() {
        assertEquals("Up next: Night Mind. Black Horizon: Explained.", upNextAnnouncement("Night Mind", "Black Horizon: Explained"))
    }

    // TODO: Validate
    @Test
    fun saysFollowingVideoAfterThen() {
        assertEquals(
            "Up next: Bluey. Dad Baby. Then: Night Mind. Black Horizon: Explained.",
            upNextAnnouncement("Bluey", "S2 Ep14 · Dad Baby", "Night Mind", "Black Horizon: Explained"),
        )
    }

    // TODO: Validate
    @Test
    fun saysOnlyEpisodeNameWhenFollowingVideoIsSameShow() {
        assertEquals(
            "Up next: Bluey. Dad Baby. Then: Sleepytime.",
            upNextAnnouncement("Bluey", "S2 Ep14 · Dad Baby", "Bluey", "S2 Ep26 · Sleepytime"),
        )
    }

    // TODO: Validate
    @Test
    fun saysShowNameWhenSameShowFollowingVideoHasNoEpisodeName() {
        assertEquals("Up next: Bluey. Dad Baby. Then: Bluey.", upNextAnnouncement("Bluey", "S2 Ep14 · Dad Baby", "Bluey", "S2 Ep26"))
    }

    // TODO: Validate
    @Test
    fun saysUpNextAloneWithoutAnyName() {
        assertEquals("Up next.", upNextAnnouncement(null, ""))
    }
}
