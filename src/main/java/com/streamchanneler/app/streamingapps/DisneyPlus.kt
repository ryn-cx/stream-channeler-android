package com.streamchanneler.app.streamingapps

import android.view.KeyEvent
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.profiles.P1AccessibilityClickedProfileScreen
import com.streamchanneler.app.profiles.P2PinKeysOnScreen
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

val disneyPlusSite = StreamingSite(
    pluginKey = "Disney+",
    apps = listOf(
        StreamingApp(
            packageName = "com.disney.disneyplus",
            appLink = { video -> addWwwToUrl(video.url) },
            playbackEndRules = {
                listOf(
                    // P1MediaTitleChange does not work because Disney+ uses the title's name.
                    // P2MediaIdChange does not work because Disney+ does not publish a media ID.
                    P3StoppedSession(),
                )
            },
            // This will usually be displayed twice, once when the credits start and a second time synced to the end of
            // the video. It's simpler just to dismiss it both times.
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "com.disney.disneyplus:id/upNextLiteAdvanced"),
                // Any dpad click will dismiss this probably but back will not dismiss it.
                autoplayCancelKeyCodes = listOf(KeyEvent.KEYCODE_DPAD_UP),
            ),
            skipIntroButton = ViewIdOnScreen(viewId = "com.disney.disneyplus:id/skipIntro"),
            skipRecapButton = ViewIdOnScreen(viewId = "com.disney.disneyplus:id/skipRecap"),
            profileScreen = P1AccessibilityClickedProfileScreen(
                profileNameFromDescription = Regex("""^Access (.+)'s (?:PIN-protected )?profile$"""),
            ),
            pinEntry = P2PinKeysOnScreen { windowNodes ->
                windowNodes.filter { windowNode -> windowNode.viewIdResourceName == "com.disney.disneyplus:id/digitKey" }
            },
        ),

    ),
)

