// TODO: Validate
package com.streamchanneler.app.streamingapps

import android.content.pm.PackageManager
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.PlayerWithoutMediaSession
import com.streamchanneler.app.playback.endrules.PlaybackEndRule
import com.streamchanneler.app.profiles.StreamingPinEntry
import com.streamchanneler.app.profiles.StreamingProfileScreen
import com.streamchanneler.app.screenautomation.PlayerButton
import com.streamchanneler.app.screenautomation.ScreenshotButton

// TODO: Validate
data class StreamingApp(
    val packageName: String,
    // Two entries can share a package name when versions of the app differ. Each entry then says which versions it covers.
    val isSupportedVersion: (installedVersionName: String) -> Boolean = { true },
    val launchExtras: Map<String, String> = emptyMap(),
    val appLink: (video: GuideVideo) -> String? = { null },
    val isClosedBeforeOpeningLinks: Boolean = false,
    val isLinkReopenedAfterProfilePicked: Boolean = false,
    val playbackStartConfirmationTime: Long = 0L,
    val playerWithoutMediaSession: PlayerWithoutMediaSession? = null,
    val playbackEndRules: () -> List<PlaybackEndRule>,
    val nextEpisodeCard: NextEpisodeCard? = null,
    val nextEpisodeAutoplayCancelButtonsInScreenshots: List<ScreenshotButton> = emptyList(),
    val skipIntroButton: PlayerButton? = null,
    val skipRecapButton: PlayerButton? = null,
    val skipCreditsButton: PlayerButton? = null,
    val skipPreviewButton: PlayerButton? = null,
    val skipAdButton: PlayerButton? = null,
    val profileScreen: StreamingProfileScreen? = null,
    val pinEntry: StreamingPinEntry? = null,
) {
    // TODO: Validate
    val site: StreamingSite
        get() = ALL_STREAMING_SITES.first { streamingSite ->
            streamingSite.apps.any { websiteStreamingApp -> websiteStreamingApp === this }
        }

    // TODO: Validate
    /** Returns whether the device has this app installed in a version that this entry covers. */
    fun isInstalled(packageManager: PackageManager): Boolean {
        val installedVersionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return installedVersionName != null && isSupportedVersion(installedVersionName)
    }

    // TODO: Validate
    val skipButtonTextsInScreenshots: Map<PlayerButtonKind, String>
        get() = PlayerButtonKind.entries.mapNotNull { playerButtonKind ->
            (playerButtonKind.playerButtonOfStreamingApp(this) as? ScreenshotButton)?.let { screenshotSkipButton ->
                playerButtonKind to screenshotSkipButton.buttonText
            }
        }.toMap()
}

// TODO: Validate
object StreamingApps {
    // TODO: Validate
    val all = ALL_STREAMING_SITES.flatMap { streamingSite -> streamingSite.apps }

    // TODO: Validate
    fun installedAppWithPackageName(packageName: String, packageManager: PackageManager): StreamingApp? =
        all.firstOrNull { it.packageName == packageName && it.isInstalled(packageManager) }
}
