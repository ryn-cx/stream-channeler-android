package com.streamchanneler.app.streamingapps

import android.view.KeyEvent
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.endrules.P2MediaIdChange
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ScreenshotButton
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion

private val ASIN_PATTERN = Regex("[0-9A-Z]{10}")

private fun watchLink(video: GuideVideo): String? {
    if (video.sourceKey == "Purchase on Amazon") return null
    val asin = video.episodeKey?.takeIf(ASIN_PATTERN::matches) ?: return null
    return "https://watch.amazon.com/watch?asin=$asin"
}

// TODO: Validate
val primeVideoSite = StreamingSite(
    pluginKey = "Amazon",
    apps = listOf(
        // TODO: Validate
        StreamingApp(
            packageName = "com.amazon.amazonvideo.livingroom",
            appLink = ::watchLink,
            playbackEndRules = {
                listOf(
                    // P1MediaTitleChange does not work because media title is not used.
                    P2MediaIdChange(),
                    // P2MediaIdChange does not work for movies because nothing is automatically played.
                    P3StoppedSession(),
                )
            },
            skipIntroButton = ScreenshotButton("Skip Intro"),
            skipAdButton = ScreenshotButton("Skip"),
            nextEpisodeAutoplayCancelButtonsInScreenshots = listOf(
                ScreenshotButton(
                    buttonText = "Watch credits",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
                    textRegion = ScreenRegion(0, 0, 959, 1079),
                ),
                ScreenshotButton(
                    buttonText = "Watch credits",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER),
                    textRegion = ScreenRegion(960, 0, 1919, 1079),
                ),
            ),
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/prime_video_profile_screen.png",
                    referenceRegion = ScreenRegion(150, 40, 519, 879),
                ),
            ),
        ),
    ),
)
