package com.streamchanneler.app.streamingapps

import android.view.KeyEvent
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ScreenshotButton
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion

val huluSite = StreamingSite(
    pluginKey = "Hulu",
    apps = listOf(
        StreamingApp(
            packageName = "com.hulu.livingroomplus",
            appLink = { video ->
                lastPathSegmentOfVideoUrl(video.url)?.let { huluContentId -> "hulu://video/?contentId=$huluContentId&play=true" }
            },
            isClosedBeforeOpeningLinks = true,
            playbackEndRules = {
                listOf(
                    // P1MediaTitleChange and P2MediaIdChange do not work because Hulu does not use a media title or
                    // media ID.
                    P3StoppedSession(),
                )
            },
            skipIntroButton = ScreenshotButton("SKIP INTRO"),
            skipRecapButton = ScreenshotButton("SKIP RECAP"),
            nextEpisodeAutoplayCancelButtonsInScreenshots = listOf(
                // Displayed at the end of a series or movie.
                ScreenshotButton(
                    buttonText = "More to Watch",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_BACK),
                    // "More to Watch" text is also displayed on the screen if you click keypad down while a video is
                    // playing so it needs to specifically detect the one shown when a video is near its end.
                    textRegion = ScreenRegion(100, 330, 400, 460),
                ),
                // Displayed at the end of an episode in the middle of a series.
                ScreenshotButton(
                    buttonText = "WATCH CREDITS",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_BACK),
                ),
            ),
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/hulu_profile_screen.png",
                    referenceRegion = ScreenRegion(100, 72, 1799, 144),
                ),
            ),
        ),
    ),
)
