package com.streamchanneler.app.streamingapps

import android.net.Uri
import android.view.KeyEvent
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.profiles.PinKeypad
import com.streamchanneler.app.profiles.P3RemoteButtonPinEntry
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ScreenshotButton
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion
import org.json.JSONObject

private fun deeplink(video: GuideVideo): String? {
    val videoPathSegments = Uri.parse(video.url ?: return null).pathSegments
    val peacockDeeplinkData = JSONObject()
        .put("pvid", videoPathSegments.last())
        .put("type", if ("episodes" in videoPathSegments) "EPISODE" else "PROGRAMME")
        .put("action", "PLAY")
    return "https://www.peacocktv.com/deeplink?deeplinkData=" + Uri.encode(peacockDeeplinkData.toString())
}

val peacockSite = StreamingSite(
    pluginKey = "Peacock",
    apps = listOf(
        StreamingApp(
            packageName = "com.peacocktv.peacockandroid",
            appLink = ::deeplink,
            playbackEndRules = {
                listOf(
                    P1MediaTitleChange(),
                )
            },
            skipIntroButton = ScreenshotButton("SKIP INTRO"),
            // TODO: Why are there two different locations for the "WATCH CREDITS" button?
            nextEpisodeAutoplayCancelButtonsInScreenshots = listOf(
                ScreenshotButton(
                    buttonText = "WATCH CREDITS",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER),
                    textRegion = ScreenRegion(0, 0, 1919, 539),
                ),
                ScreenshotButton(
                    buttonText = "WATCH CREDITS",
                    keyCodesToPress = listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
                    textRegion = ScreenRegion(0, 540, 1919, 1079),
                ),
            ),
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/peacock_profile_screen.png",
                    referenceRegion = ScreenRegion(85, 60, 474, 124),
                ),
            ),
            pinEntry = P3RemoteButtonPinEntry(
                pinScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/peacock_pin_screen.png",
                    referenceRegion = ScreenRegion(515, 250, 1404, 339),
                ),
                pinKeypad = PinKeypad(listOf("1234567890")),
                typesPinWithNumberKeys = true,
            ),
        ),
    ),
)
