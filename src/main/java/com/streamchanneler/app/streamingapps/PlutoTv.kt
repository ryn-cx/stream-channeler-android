// TODO: Validate
package com.streamchanneler.app.streamingapps

import android.net.Uri
import android.view.KeyEvent
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.playback.endrules.P7PositionReset
import com.streamchanneler.app.screenautomation.ViewIdOnScreen

// TODO: Validate
private fun onDemandPath(video: GuideVideo): String? =
    Uri.parse(video.url ?: return null).pathSegments.drop(1).filterNot { it == "details" }.joinToString("/")

// TODO: Validate
private fun videoAppLink(video: GuideVideo): String? = onDemandPath(video)?.let { "plutotv://pluto.tv/$it" }

// TODO: Validate
private fun leanbackVideoAppLink(video: GuideVideo): String? = onDemandPath(video)?.let { "plutotv://$it" }

// TODO: Validate
private fun isLeanbackVersion(installedVersionName: String): Boolean = installedVersionName.endsWith("-leanback")

// TODO: Validate
val plutoTvSite = StreamingSite(
    pluginKey = "Pluto TV",
    apps = listOf(
        // TODO: Validate
        StreamingApp(
            packageName = "tv.pluto.android",
            isSupportedVersion = { installedVersionName -> !isLeanbackVersion(installedVersionName) },
            appLink = ::videoAppLink,
            playbackEndRules = {
                listOf(
                    // Pluto has ads but the media title does not change.
                    P1MediaTitleChange(),
                )
            },
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "tv.pluto.android:id/nextEpsBtn"),
                autoplayCancelKeyCodes = listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER),
            ),
        ),
        StreamingApp(
            packageName = "tv.pluto.android",
            // Older version of the app.
            isSupportedVersion = ::isLeanbackVersion,
            appLink = ::leanbackVideoAppLink,
            playbackEndRules = {
                listOf(
                    // There's basically nothing for this app.
                    P7PositionReset(),
                )
            },
            nextEpisodeCard = NextEpisodeCard(
                shownElement = ViewIdOnScreen(viewId = "tv.pluto.android:id/countdown_title"),
                autoplayCancelButtons = listOf(ViewIdOnScreen(viewId = "tv.pluto.android:id/watch_credit_button")),
            ),
        ),
    ),
)
