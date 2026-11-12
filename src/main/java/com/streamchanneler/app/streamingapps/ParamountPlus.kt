package com.streamchanneler.app.streamingapps

import android.view.KeyEvent
import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ContentDescriptionOnScreen
import com.streamchanneler.app.screenautomation.PressedWithOkButton
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion
import com.streamchanneler.app.screenautomation.ViewIdAndTextOnScreen
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

val paramountPlusSite = StreamingSite(
    pluginKey = "Paramount+",
    apps = listOf(
        StreamingApp(
            packageName = "com.cbs.ott",
            appLink = { video -> addWwwToUrl(video.url) },
            // Paramount+ fails to open a link when it is already running.
            isClosedBeforeOpeningLinks = true,
            playbackEndRules = { listOf(P1MediaTitleChange()) },
            skipIntroButton = PressedWithOkButton(
                ViewIdAndTextOnScreen(
                    viewId = "com.cbs.ott:id/btnSkipIntro",
                    viewText = "SKIP INTRO"
                )
            ),
            skipRecapButton = PressedWithOkButton(
                ViewIdAndTextOnScreen(
                    viewId = "com.cbs.ott:id/btnSkipIntro",
                    viewText = "SKIP RECAP"
                )
            ),
            skipAdButton = PressedWithOkButton(ContentDescriptionOnScreen(Regex("^Skip$"))),
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "com.cbs.ott:id/nextEpsBtn"),
                autoplayCancelKeyCodes = listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER),
            ),
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/paramount_plus_profile_screen.png",
                    referenceRegion = ScreenRegion(240, 112, 505, 167),
                ),
            ),
        ),

    ),
)
