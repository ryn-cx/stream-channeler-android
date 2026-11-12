// TODO: Validate
package com.streamchanneler.app.playback

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.findStreamingSite

// TODO: Validate
sealed class VideoLaunchResult {
    // TODO: Validate
    data class Launched(
        val launchedPackageName: String?,
        val launchedStreamingApp: StreamingApp?,
    ) : VideoLaunchResult()

    // TODO: Validate
    data class Failed(val reason: String) : VideoLaunchResult()
}

// TODO: Validate
object StreamingAppLauncher {
    // TODO: Validate
    private const val TAG = "StreamingAppLauncher"

    // TODO: Validate
    const val NO_VIDEO_LINK_MESSAGE = "This video has no link to play."

    // TODO: Validate
    fun launchVideo(context: Context, video: GuideVideo): VideoLaunchResult {
        val videoUrl = video.url ?: return VideoLaunchResult.Failed(NO_VIDEO_LINK_MESSAGE)
        val streamingSite = findStreamingSite(video)
            ?: return VideoLaunchResult.Failed("No streaming app is set up to open $videoUrl")
        val installedStreamingApp = streamingSite.apps.firstOrNull { it.isInstalled(context.packageManager) }
            ?: return VideoLaunchResult.Failed("Install ${streamingSite.pluginKey} to play this video.")
        return launchVideoInStreamingApp(context, installedStreamingApp, video, Uri.parse(videoUrl))
            ?: VideoLaunchResult.Failed("${streamingSite.pluginKey} could not open this video.")
    }

    // TODO: Validate
    private fun launchVideoInStreamingApp(
        context: Context,
        installedStreamingApp: StreamingApp,
        video: GuideVideo,
        videoWebUri: Uri,
    ): VideoLaunchResult.Launched? {
        val videoAppLink = installedStreamingApp.appLink(video)
        val isAppLinkIntentUrl = videoAppLink?.startsWith("intent:") == true
        if (videoAppLink != null && isAppLinkIntentUrl) {
            launchAppIntentUrl(context, videoAppLink, installedStreamingApp)?.let { launchedPackageName ->
                return VideoLaunchResult.Launched(launchedPackageName, installedStreamingApp)
            }
        }
        val videoUri = videoAppLink?.takeUnless { isAppLinkIntentUrl }?.let(Uri::parse) ?: videoWebUri
        val installedPackageName = installedStreamingApp.packageName
        val appIntent = viewIntent(videoUri, installedStreamingApp).setPackage(installedPackageName)
        if (installedStreamingApp.isClosedBeforeOpeningLinks) {
            context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(installedPackageName)
            Log.i(TAG, "Closed $installedPackageName so it starts fresh with the link")
        }
        if (!startActivityLoggingRefusals(context, appIntent, installedPackageName, videoUri.toString())) return null
        return VideoLaunchResult.Launched(installedPackageName, installedStreamingApp)
    }

    // TODO: Validate
    private fun launchAppIntentUrl(context: Context, appIntentUrl: String, streamingApp: StreamingApp): String? {
        val appIntent = Intent.parseUri(appIntentUrl, Intent.URI_INTENT_SCHEME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appIntent.selector = null
        val targetPackageName = appIntent.component?.packageName ?: appIntent.`package`
        if (targetPackageName != streamingApp.packageName) {
            Log.w(TAG, "$appIntentUrl opens $targetPackageName, which isn't a ${streamingApp.site.pluginKey} app")
            return null
        }
        return if (startActivityLoggingRefusals(context, appIntent, targetPackageName, appIntentUrl)) targetPackageName else null
    }

    // TODO: Validate
    private fun startActivityLoggingRefusals(
        context: Context,
        appIntent: Intent,
        targetPackageName: String?,
        openedLinkText: String,
    ): Boolean = try {
        context.startActivity(appIntent)
        true
    } catch (appNotFound: ActivityNotFoundException) {
        Log.w(TAG, "$targetPackageName can't open $openedLinkText", appNotFound)
        false
    } catch (appRefused: SecurityException) {
        Log.w(TAG, "$targetPackageName refused $openedLinkText", appRefused)
        false
    }

    // TODO: Validate
    private fun viewIntent(videoUri: Uri, streamingApp: StreamingApp?): Intent =
        Intent(Intent.ACTION_VIEW, videoUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { streamingApp?.launchExtras?.forEach { (extraName, extraValue) -> putExtra(extraName, extraValue) } }
}
