package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.PinKeypad
import com.streamchanneler.app.profiles.P3RemoteButtonPinEntry
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion
import com.streamchanneler.app.screenautomation.ScreenshotButton
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

val hboMaxSite = StreamingSite(
    pluginKey = "HBO Max",
    apps = listOf(
        StreamingApp(
            packageName = "com.wbd.stream",
            playbackEndRules = {
                listOf(
                    P1MediaTitleChange(),
                    // P1MediaTitleChange would technically work for the last episode of a series and movies but there
                    // is a long delay because a page displays suggestions for a while before auto playing the top
                    // suggestion. So detecting the stopped session is a better user experience.
                    // P2MediaIdChange does not work because HBO Max does not use media ID.
                    P3StoppedSession(),
                )
            },
            // This is displayed when the credits start but does not automatically do anything so nothing needs to be
            // done. 
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "com.wbd.stream:id/up_next_root"),
            ),
            skipIntroButton = ScreenshotButton("Skip Intro"),
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/hbo_max_profile_screen.png",
                    referenceRegion = ScreenRegion(700, 150, 1219, 239),
                ),
            ),
            pinEntry = P3RemoteButtonPinEntry(
                pinScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/hbo_max_pin_screen.png",
                    referenceRegion = ScreenRegion(645, 150, 1274, 214),
                ),
                pinKeypad = PinKeypad(listOf("123", "456", "789", " 0")),
            ),
        ),

    ),
)
