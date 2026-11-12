package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P7PositionReset
import com.streamchanneler.app.profiles.PinKeypad
import com.streamchanneler.app.profiles.ProfileList
import com.streamchanneler.app.profiles.ProfileListDirection
import com.streamchanneler.app.profiles.P3RemoteButtonPinEntry
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen

// Netflix sucks, it basically needs to be navigated blindly.
val netflixSite = StreamingSite(
    pluginKey = "Netflix",
    apps = listOf(
        StreamingApp(
            packageName = "com.netflix.ninja",
            // Magic parameter that is required for some reason.
            launchExtras = mapOf("source" to "30"),
            appLink = { video -> addWwwToUrl(video.url) },
            // Netflix really just sucks.
            playbackEndRules = { listOf(P7PositionReset()) },
            profileScreen = P2RemoteButtonProfileScreen(profileList = ProfileList(ProfileListDirection.TOP_TO_BOTTOM)),
            pinEntry = P3RemoteButtonPinEntry(
                typesPinWithNumberKeys = true,
                pinKeypad = PinKeypad(listOf("123", "456", "789", "0")),
            ),
        ),

    ),
)

