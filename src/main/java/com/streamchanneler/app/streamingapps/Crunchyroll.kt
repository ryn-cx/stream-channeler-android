package com.streamchanneler.app.streamingapps

import android.net.Uri
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.endrules.P2MediaIdChange
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.P1AccessibilityClickedProfileScreen
import com.streamchanneler.app.profiles.P2PinKeysOnScreen
import com.streamchanneler.app.screenautomation.ViewIdAndTextOnScreen
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

private fun episodeAppLink(video: GuideVideo): String? {
    // The backend will return more uniform URLs but this extra check makes it easier to copy and paste URLs with or
    // without a slug for testing purposes.
    val episodeId = video.episodeKey ?: Uri.parse(video.url ?: return null).pathSegments[1]
    return "crunchyroll://episode/$episodeId"
}

private fun isMusicUrl(videoUrl: String?): Boolean {
    // The backend will return more uniform URLs but this extra check makes it easier to copy and paste URLs with or
    // without a slug for testing purposes.
    return Uri.parse(videoUrl ?: return false).pathSegments[1] in setOf("musicvideo", "concert")
}

private val pinScreenFromDescription = Regex("""^Enter your \d+-digit PIN\.""")

private val pinEntry = P2PinKeysOnScreen { windowNodes ->
    val isOnPinScreen = windowNodes.any { windowNode ->
        windowNode.contentDescription?.let { nodeDescription -> pinScreenFromDescription.containsMatchIn(nodeDescription) } == true
    }

    if (!isOnPinScreen) return@P2PinKeysOnScreen emptyList()

    windowNodes.filter { windowNode ->
        windowNode.text?.toString()?.trim()?.singleOrNull()?.isDigit() == true
    }
}

// TODO: Validate
val crunchyrollSite = StreamingSite(
    pluginKey = "Crunchyroll",
    // Crunchyroll's app does not support music.
    isVideoUnplayable = { video -> isMusicUrl(video.url) },
    apps = listOf(
        StreamingApp(
            packageName = "com.crunchyroll.crunchyroid",
            appLink = ::episodeAppLink,
            playbackEndRules = {
                listOf(
                    // P1MediaTitleChange does not work because Crunchyroll uses the title's name instead of the episode
                    // name so it does not change between episodes.
                    P2MediaIdChange(),
                    // P2MediaIdChange does not work for movies and the last episode of a series because it does not
                    // autoplay anything afterwards.
                    P3StoppedSession(),
                )
            },
            // During the last 10 seconds of a video in the middle of a series it shrink the video and display
            // information about the next episode. This will force the playing video to stay in full screen until it is
            // completed.
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "upNextCardLabelText"),
                autoplayCancelButtons = listOf(ViewIdOnScreen(viewId = "upNextCurrentEpisodeTitleText")),
            ),
            skipIntroButton = ViewIdAndTextOnScreen(viewId = "skipIntroButtonTextTestTag", viewText = "SKIP INTRO"),
            skipCreditsButton = ViewIdAndTextOnScreen(viewId = "skipIntroButtonTextTestTag", viewText = "SKIP CREDITS"),
            profileScreen = P1AccessibilityClickedProfileScreen(
                profileNameFromDescription = Regex("""^(.+) profile\. Button\."""),
            ),
            pinEntry = pinEntry,
        ),
    ),
)
