// TODO: Validate
package com.streamchanneler.app.playback.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// TODO: Validate
class ArchiveOrgVideosTest {
    // TODO: Validate
    private val aviUploadItemFiles = listOf(
        ArchiveItemFile("TortureShip.avi", "DivX", null),
        ArchiveItemFile("TortureShip.mp4", "h.264", "TortureShip.avi"),
        ArchiveItemFile("TortureShip.ogv", "Ogg Video", "TortureShip.avi"),
        ArchiveItemFile("TortureShip_512kb.mp4", "512Kb MPEG4", "TortureShip.avi"),
        ArchiveItemFile("TortureShip.thumbs/TortureShip_000001.jpg", "Thumbnail", "TortureShip.avi"),
    )

    // TODO: Validate
    @Test
    fun parsesItemAndEncodedFileNameFromDetailsLink() {
        assertEquals(
            ArchiveVideoLink("SomeItem", "Episode 1 (1950).mp4"),
            ArchiveOrgVideos.videoLinkFromUrl("https://archive.org/details/SomeItem/Episode%201%20%281950%29.mp4"),
        )
        assertEquals(
            ArchiveVideoLink("SomeItem", null),
            ArchiveOrgVideos.videoLinkFromUrl("https://www.archive.org/details/SomeItem"),
        )
    }

    // TODO: Validate
    @Test
    fun ignoresLinksThatAreNotArchiveDetailsPages() {
        assertNull(ArchiveOrgVideos.videoLinkFromUrl("https://www.netflix.com/watch/123"))
        assertNull(ArchiveOrgVideos.videoLinkFromUrl("https://archive.org/search?query=films"))
        assertNull(ArchiveOrgVideos.videoLinkFromUrl("https://notarchive.org/details/SomeItem"))
    }

    // TODO: Validate
    @Test
    fun playsRequestedMp4AsIs() {
        val requestedMp4Link = ArchiveVideoLink("TortureShip", "TortureShip_512kb.mp4")
        assertEquals("TortureShip_512kb.mp4", ArchiveOrgVideos.playableFileName(requestedMp4Link, aviUploadItemFiles))
    }

    // TODO: Validate
    @Test
    fun replacesUnplayableUploadWithItsBestMp4() {
        val requestedAviLink = ArchiveVideoLink("TortureShip", "TortureShip.avi")
        val requestedOggLink = ArchiveVideoLink("TortureShip", "TortureShip.ogv")
        assertEquals("TortureShip.mp4", ArchiveOrgVideos.playableFileName(requestedAviLink, aviUploadItemFiles))
        assertEquals("TortureShip.mp4", ArchiveOrgVideos.playableFileName(requestedOggLink, aviUploadItemFiles))
    }

    // TODO: Validate
    @Test
    fun picksBestMp4WhenLinkNamesOnlyTheItem() {
        assertEquals("TortureShip.mp4", ArchiveOrgVideos.playableFileName(ArchiveVideoLink("TortureShip", null), aviUploadItemFiles))
    }

    // TODO: Validate
    @Test
    fun fallsBackToRequestedFileWhenNoMp4Exists() {
        val oggOnlyItemFiles = listOf(ArchiveItemFile("Film.ogv", "Ogg Video", null))
        assertEquals("Film.ogv", ArchiveOrgVideos.playableFileName(ArchiveVideoLink("Film", "Film.ogv"), oggOnlyItemFiles))
    }

    // TODO: Validate
    @Test
    fun encodesDownloadLinkPathSegments() {
        assertEquals(
            "https://archive.org/download/SomeItem/Season%201/Episode%201%2B2.mp4",
            ArchiveOrgVideos.downloadUrl("SomeItem", "Season 1/Episode 1+2.mp4"),
        )
    }
}
