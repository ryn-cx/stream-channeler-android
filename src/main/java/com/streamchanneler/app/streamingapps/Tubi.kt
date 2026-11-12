package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

val tubiSite = StreamingSite(
    pluginKey = "Tubi",
    apps = listOf(
        StreamingApp(
            packageName = "com.tubitv",
            playbackEndRules = {
                listOf(
                    P1MediaTitleChange(),
                )
            },
            nextEpisodeCard = NextEpisodeCard(shownElement = ViewIdOnScreen(viewId = "com.tubitv:id/up_next_text")),
        ),
    ),
)

