package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.ReferenceImageScreenSignature
import com.streamchanneler.app.screenautomation.ScreenRegion

val youTubeSite = StreamingSite(
    pluginKey = "YouTube",
    apps = listOf(
        StreamingApp(
            packageName = "com.google.android.youtube.tv",
            isLinkReopenedAfterProfilePicked = true,
            playbackStartConfirmationTime = 5_000L,
            playbackEndRules = {
                listOf(
                    // YouTube is slow to update the title for some reason so it needs a small wait.
                    P1MediaTitleChange(realTitleWaitTime = 5_000L),
                )
            },
            profileScreen = P2RemoteButtonProfileScreen(
                profileScreenSignature = ReferenceImageScreenSignature(
                    referenceImageResourcePath = "/screen_signatures/youtube_profile_screen.png",
                    referenceRegion = ScreenRegion(700, 120, 1219, 214),
                ),
            ),
        ),
    ),
)
