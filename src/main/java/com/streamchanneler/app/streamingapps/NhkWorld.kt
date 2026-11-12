package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.playback.PlayerWithoutMediaSession
import com.streamchanneler.app.playback.endrules.P4OnScreenElementAppears
import com.streamchanneler.app.screenautomation.ViewIdAndTextOnScreen

private const val TV_PACKAGE_NAME = "jp.or.nhk.nhkworld.tv"

val nhkWorldSite = StreamingSite(
    pluginKey = "NHK World",
    apps = listOf(
        StreamingApp(
            packageName = TV_PACKAGE_NAME,
            appLink = { video ->
                lastPathSegmentOfVideoUrl(video.url)?.let { videoEpisodeId ->
                    "intent:#Intent;component=$TV_PACKAGE_NAME/$TV_PACKAGE_NAME.ui.MainActivity;" +
                            "S.VideoEpisodeId=$videoEpisodeId;end"
                }
            },
            playerWithoutMediaSession = PlayerWithoutMediaSession(
                playerActivityClassName = "$TV_PACKAGE_NAME.ui.player.VodPlayerActivity",
                loadingViewId = null,
                controlsViewId = "$TV_PACKAGE_NAME:id/playback_controls_dock",
                adViewId = null,
            ),
            playbackEndRules = {
                listOf(
                    P4OnScreenElementAppears(
                        ViewIdAndTextOnScreen(viewId = "$TV_PACKAGE_NAME:id/current_time", viewText = "--"),
                        checkWhilePaused = true,
                    ),
                )
            },
        ),
    ),
)
