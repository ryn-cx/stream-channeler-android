// TODO: Validate
package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.P1AccessibilityClickedProfileScreen
import com.streamchanneler.app.profiles.PlayButtonOnScreen
import com.streamchanneler.app.screenautomation.ContentDescriptionOnScreen
import com.streamchanneler.app.screenautomation.ViewIdAndTextOnScreen

// TODO: Validate
private val playOrResumeButtonViewId = Regex("""^button-(Play|Resume)\b.*""")

// TODO: Validate
private val focusedPlayOrResumeButton = PlayButtonOnScreen { windowNodes ->
    windowNodes.firstOrNull { windowNode ->
        val nodeViewId = windowNode.viewIdResourceName ?: return@firstOrNull false
        windowNode.isFocused && playOrResumeButtonViewId.matches(nodeViewId)
    }
}

// TODO: Validate
val hidiveSite = StreamingSite(
    pluginKey = "HIDIVE",
    apps = listOf(
        // TODO: Validate
        StreamingApp(
            packageName = "com.twentyfouri.androidtv.hidive",
            appLink = { video -> lastPathSegmentOfVideoUrl(video.url)?.let { videoId -> "hidive://video/$videoId" } },
            playbackEndRules = {
                listOf(
                    // P1MediaTitleChange and P2MediaIdChange do not work because HIDIVE does not use a media title or
                    // media ID.
                    P3StoppedSession(),
                )
            },
            nextEpisodeCard = NextEpisodeCard(shownElement = ContentDescriptionOnScreen(Regex("""^Play E\d+ - """))),
            skipIntroButton = ViewIdAndTextOnScreen(
                viewId = "com.twentyfouri.androidtv.hidive:id/skipMarkTextView",
                viewText = "Skip Intro"
            ),
            skipCreditsButton = ViewIdAndTextOnScreen(
                viewId = "com.twentyfouri.androidtv.hidive:id/skipMarkTextView",
                viewText = "Skip Credits"
            ),
            profileScreen = P1AccessibilityClickedProfileScreen(
                profileNameFromDescription = Regex("""Select Profile (.+?)(?:,|$)"""),
                playButtonOnScreen = focusedPlayOrResumeButton,
            ),
        ),
    ),
)
