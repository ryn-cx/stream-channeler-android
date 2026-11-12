// TODO: Validate
package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

// TODO: Validate
val pbsSite = StreamingSite(
    pluginKey = "PBS",
    apps = listOf(
        // TODO: Validate
        StreamingApp(
            packageName = "com.pbs.video",
            appLink = { video -> addWwwToUrl(video.url) },
            playbackEndRules = {
                listOf(
                    P1MediaTitleChange(),
                )
            },
            nextEpisodeCard = NextEpisodeCard(shownElement = ViewIdOnScreen(viewId = "com.pbs.video:id/tv_up_next_label")),
        ),
    ),
)

