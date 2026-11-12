// TODO: Validate
package com.streamchanneler.app.livetests

import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.playback.estimatedPosition
import com.streamchanneler.app.playback.NextEpisodeAutoplayCanceller
import com.streamchanneler.app.playback.ScreenshotSkipButtonClicker
import com.streamchanneler.app.playback.SkipButtonClicker
import com.streamchanneler.app.profiles.StreamingTestDevice
import com.streamchanneler.app.profiles.StreamingTestProfiles
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.ScreenTextReader
import com.streamchanneler.app.screenautomation.findOnScreenElementInActiveWindow
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.streamingapps.PlayerButtonKind
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.StreamingApps
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TODO: Validate
class SkipButtonClickerLiveTest {
    // TODO: Validate
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // TODO: Validate
    private val targetContext: Context = instrumentation.targetContext
    // TODO: Validate
    private val instrumentationArguments = InstrumentationRegistry.getArguments()

    // TODO: Validate
    private val skipButtonClickCheckByPackageName = mutableMapOf<String, (() -> PlayerButtonKind?)?>()

    // TODO: Validate
    private val nextEpisodeAutoplayCancellerByPackageName = mutableMapOf<String, NextEpisodeAutoplayCanceller?>()

    // TODO: Validate
    private val liveEndDetectionByPackageName = mutableMapOf<String, LiveEndDetection>()

    // TODO: Validate
    private val screenTextReaderForLog =
        if (instrumentationArguments.getString("logScreenText").toBoolean()) ScreenTextReader() else null

    // TODO: Validate
    private var lastLoggedScreenText = ""

    // TODO: Validate
    private var wasUndismissableNextEpisodeCardShowing = false

    // TODO: Validate
    private val pressesButtons = instrumentationArguments.getString("pressesButtons")?.toBoolean() ?: true

    // TODO: Validate
    private val videoDuration = instrumentationArguments.getString("videoLengthMinutes")?.toDoubleOrNull()
        ?.takeIf { videoLengthMinutes -> videoLengthMinutes > 0 }
        ?.let { videoLengthMinutes -> (videoLengthMinutes * 60_000).toLong() }

    // TODO: Validate
    private var lastShowingButtonLabels = emptySet<String>()

    // TODO: Validate
    @Test
    fun clickSkipButtonsThatShow() {
        val watchMinutes = instrumentationArguments.getString("watchMinutes")?.toLong() ?: 0L
        StreamingTestDevice.clearLog()
        StreamingTestDevice.turnOnProfilePicker(targetContext)
        StreamingTestProfiles.saveProfilesFromAsset(targetContext.streamChannelerApp.localAppStorage)
        runCatching { StreamingTestDevice.startShizukuIfNeeded(targetContext) }
            .onFailure { reportStatus("Couldn't start Shizuku, so next episode cards might not be dismissed: $it") }
        StreamingTestDevice.runOnMainThreadAndWait { targetContext.streamChannelerApp.playbackMonitor.stopMonitoring() }
        val watchLengthDescription = if (watchMinutes > 0) "for $watchMinutes minutes" else "until stopped"
        reportStatus("Watching for skip buttons $watchLengthDescription. Open a streaming app and play an episode.")

        var lastFrontPackageName: String? = null
        val recentCheckScreenshots = ArrayDeque<Bitmap>()
        val recentCheckPlaybacks = ArrayDeque<StreamingAppPlayback?>()
        var skipButtonClickCount = 0
        var clicksThatDidNotSkipCount = 0
        val stopWatchingAt = if (watchMinutes > 0) SystemClock.elapsedRealtime() + watchMinutes * 60_000L else Long.MAX_VALUE
        while (SystemClock.elapsedRealtime() < stopWatchingAt) {
            val frontPackageName = StreamingTestDevice.runOnMainThreadAndWait { ScreenAutomationService.foregroundPackageName }
            if (frontPackageName != null) {
                val isNewAppInFront = frontPackageName != lastFrontPackageName
                lastFrontPackageName = frontPackageName
                checkForEndsInFrontApp(frontPackageName)
                logScreenTextIfChanged(frontPackageName)
                if (!pressesButtons) {
                    logShowingButtonChanges(frontPackageName, isNewAppInFront)
                    Thread.sleep(CHECK_INTERVAL)
                    continue
                }
                dismissNextEpisodeCardIfShowing(frontPackageName)
                val clickShowingSkipButton = skipButtonClickCheckOfFrontApp(frontPackageName, isNewAppInFront)
                if (clickShowingSkipButton != null) {
                    keepLastTwo(recentCheckPlaybacks, streamingAppPlayback(frontPackageName))
                    takeScreenshot()?.let { keepLastTwo(recentCheckScreenshots, it) }
                    val clickedSkipButtonKind = clickShowingSkipButton()
                    if (clickedSkipButtonKind != null) {
                        Thread.sleep(PLAYBACK_AFTER_CLICK_DELAY)
                        val playbackAfterClick = streamingAppPlayback(frontPackageName)
                        val resultsDirectory = saveClickScreenshots(recentCheckScreenshots, takeScreenshot())
                        val didClickSkip = reportClickResult(
                            clickedSkipButtonKind,
                            frontPackageName,
                            recentCheckPlaybacks.first(),
                            playbackAfterClick,
                            resultsDirectory,
                        )
                        skipButtonClickCount++
                        if (didClickSkip == false) clicksThatDidNotSkipCount++
                        recentCheckScreenshots.clear()
                        recentCheckPlaybacks.clear()
                    }
                }
            }
            Thread.sleep(CHECK_INTERVAL)
        }
        if (skipButtonClickCount == 0) throw AssertionError("No skip button was clicked within $watchMinutes minutes")
        if (clicksThatDidNotSkipCount > 0) {
            throw AssertionError("$clicksThatDidNotSkipCount of $skipButtonClickCount clicks didn't skip")
        }
        reportStatus("All $skipButtonClickCount clicks skipped")
    }

    // TODO: Validate
    private fun logScreenTextIfChanged(frontPackageName: String) {
        val screenTextReader = screenTextReaderForLog ?: return
        val screenPixels = ScreenAutomationService.connectedService?.screenshots?.takeScreenshotNow() ?: return
        val screenText = screenTextReader.describeScreenTextWithPositions(screenPixels)
        if (screenText == lastLoggedScreenText) return
        lastLoggedScreenText = screenText
        reportStatus("SCREEN TEXT in $frontPackageName at ${describePlayback(streamingAppPlayback(frontPackageName))}: ${screenText.ifEmpty { "none" }}")
    }

    // TODO: Validate
    private fun logShowingButtonChanges(frontPackageName: String, isNewAppInFront: Boolean) {
        val frontStreamingApp = StreamingApps.installedAppWithPackageName(frontPackageName, targetContext.packageManager) ?: return
        if (isNewAppInFront) reportStatus("$frontPackageName is in front. Logging which of its buttons show, without pressing them.")
        val buttonByLabel = PlayerButtonKind.entries.mapNotNull { playerButtonKind ->
            playerButtonKind.onScreenElementOfStreamingApp(frontStreamingApp)?.let { playerButtonKind.label to it }
        }.toMap()
        val showingButtonLabels = StreamingTestDevice.runOnMainThreadAndWait {
            buttonByLabel.filterValues { findOnScreenElementInActiveWindow(frontPackageName, it) != null }.keys
        }
        val playback = describePlayback(streamingAppPlayback(frontPackageName))
        for (appearedButtonLabel in showingButtonLabels - lastShowingButtonLabels) {
            reportStatus("BUTTON SHOWING: $appearedButtonLabel in $frontPackageName at $playback")
        }
        for (goneButtonLabel in lastShowingButtonLabels - showingButtonLabels) {
            reportStatus("BUTTON GONE: $goneButtonLabel in $frontPackageName at $playback")
        }
        lastShowingButtonLabels = showingButtonLabels
    }

    // TODO: Validate
    private fun checkForEndsInFrontApp(frontPackageName: String) {
        val frontStreamingApp = StreamingApps.installedAppWithPackageName(frontPackageName, targetContext.packageManager) ?: return
        val frontAppEndDetection = liveEndDetectionByPackageName.getOrPut(frontPackageName) {
            LiveEndDetection(targetContext, frontStreamingApp, frontPackageName, videoDuration, ::reportStatus)
        }
        StreamingTestDevice.runOnMainThreadAndWait { frontAppEndDetection.checkForEnds() }
    }

    // TODO: Validate
    private fun dismissNextEpisodeCardIfShowing(frontPackageName: String) {
        val frontStreamingApp = StreamingApps.installedAppWithPackageName(frontPackageName, targetContext.packageManager)
        val nextEpisodeAutoplayCanceller = nextEpisodeAutoplayCancellerByPackageName.getOrPut(frontPackageName) {
            frontStreamingApp?.let(NextEpisodeAutoplayCanceller::ofStreamingApp)
        }
        if (nextEpisodeAutoplayCanceller != null) {
            val hasPressedCancelButton = StreamingTestDevice.runOnMainThreadAndWait {
                nextEpisodeAutoplayCanceller.cancelWhenShowing(frontPackageName)
            }
            if (hasPressedCancelButton) reportStatus("Dismissed the next episode card in $frontPackageName")
            return
        }
        val nextEpisodeCard = frontStreamingApp?.nextEpisodeCard?.shownElement ?: return
        val isNextEpisodeCardShowing = StreamingTestDevice.runOnMainThreadAndWait {
            findOnScreenElementInActiveWindow(frontPackageName, nextEpisodeCard) != null
        }
        if (isNextEpisodeCardShowing && !wasUndismissableNextEpisodeCardShowing) {
            reportStatus("$frontPackageName is showing its next episode card. Stream Channeler can't dismiss it in this app.")
        }
        wasUndismissableNextEpisodeCardShowing = isNextEpisodeCardShowing
    }

    // TODO: Validate
    private fun reportClickResult(
        clickedSkipButtonKind: PlayerButtonKind,
        clickedPackageName: String,
        playbackBeforeClick: StreamingAppPlayback?,
        playbackAfterClick: StreamingAppPlayback?,
        resultsDirectory: File,
    ): Boolean? {
        val clickDescription = "Clicked ${clickedSkipButtonKind.label} in $clickedPackageName. " +
            "Before: ${describePlayback(playbackBeforeClick)}. ${PLAYBACK_AFTER_CLICK_DELAY / 1000} s after: " +
            "${describePlayback(playbackAfterClick)}. Screenshots: ${resultsDirectory.name}"
        if (playbackBeforeClick == null || playbackAfterClick == null) {
            reportStatus("$clickDescription. Whether it skipped can't be checked without a media session.")
            return null
        }
        val positionJump = playbackAfterClick.position - playbackBeforeClick.position
        val didClickSkip = positionJump > PLAYBACK_AFTER_CLICK_DELAY + CHECK_INTERVAL + MINIMUM_SKIP_LENGTH
        val wasPausedByClick = playbackBeforeClick.isPlaying && !playbackAfterClick.isPlaying
        val clickResultDescription = when {
            didClickSkip -> "SKIPPED %.1f s".format(positionJump / 1000.0)
            wasPausedByClick -> "DIDN'T SKIP: the click paused the episode"
            else -> "DIDN'T SKIP: the episode moved %.1f s".format(positionJump / 1000.0)
        }
        reportStatus("$clickDescription. $clickResultDescription")
        return didClickSkip
    }

    // TODO: Validate
    private fun <T> keepLastTwo(lastTwoValues: ArrayDeque<T>, newestValue: T) {
        lastTwoValues.addLast(newestValue)
        if (lastTwoValues.size > 2) lastTwoValues.removeFirst()
    }

    // TODO: Validate
    private fun takeScreenshot(): Bitmap? =
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()

    // TODO: Validate
    private fun saveClickScreenshots(screenshotsBeforeClick: Collection<Bitmap>, screenshotAfterClick: Bitmap?): File {
        val resultsDirectory = File(
            targetContext.getExternalFilesDir(RESULTS_DIRECTORY_NAME),
            SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date()),
        )
        resultsDirectory.mkdirs()
        val screenshotByFileName = screenshotsBeforeClick.mapIndexed { checkIndex, screenshot ->
            "${checkIndex + 1} before click.png" to screenshot
        } + listOfNotNull(screenshotAfterClick?.let { "${screenshotsBeforeClick.size + 1} after click.png" to it })
        for ((screenshotFileName, screenshot) in screenshotByFileName) {
            FileOutputStream(File(resultsDirectory, screenshotFileName)).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return resultsDirectory
    }

    // TODO: Validate
    private fun skipButtonClickCheckOfFrontApp(frontPackageName: String, isNewAppInFront: Boolean): (() -> PlayerButtonKind?)? {
        val frontStreamingApp = StreamingApps.installedAppWithPackageName(frontPackageName, targetContext.packageManager)
        val clickShowingSkipButton = skipButtonClickCheckByPackageName.getOrPut(frontPackageName) {
            frontStreamingApp?.let { skipButtonClickCheck(it, frontPackageName) }
        }
        if (isNewAppInFront) {
            val nextEpisodeCardDescription = when {
                frontStreamingApp == null -> ""
                NextEpisodeAutoplayCanceller.ofStreamingApp(frontStreamingApp) != null -> " Its next episode card is dismissed."
                frontStreamingApp.nextEpisodeAutoplayCancelButtonsInScreenshots.isNotEmpty() ->
                    " Its next episode cards are dismissed with " +
                        frontStreamingApp.nextEpisodeAutoplayCancelButtonsInScreenshots.map { it.buttonText }.distinct().joinToString() + "."
                frontStreamingApp.nextEpisodeCard != null -> " Its next episode card can't be dismissed."
                else -> ""
            }
            reportStatus(
                when {
                    frontStreamingApp == null -> "$frontPackageName is in front. It isn't a streaming app that Stream Channeler knows."
                    clickShowingSkipButton == null -> "$frontPackageName is in front. Stream Channeler can't click any of its skip buttons."
                    else -> "$frontPackageName is in front. Watching it for " +
                        clickableSkipButtonKindsOfStreamingApp(frontStreamingApp).joinToString { it.label } + "."
                } + nextEpisodeCardDescription,
            )
        }
        return clickShowingSkipButton
    }

    // TODO: Validate
    private fun reportPlaybackSoon(streamingPackageName: String) {
        val mediaIdWhenDismissed = streamingAppMediaId(streamingPackageName)
        val playbackWhenDismissed = streamingAppPlayback(streamingPackageName)
        val stopWaitingAt = SystemClock.elapsedRealtime() + DISMISS_CHECK_DELAY
        while (SystemClock.elapsedRealtime() < stopWaitingAt) {
            checkForEndsInFrontApp(streamingPackageName)
            Thread.sleep(CHECK_INTERVAL)
        }
        val mediaIdAfterDismissing = streamingAppMediaId(streamingPackageName)
        val playbackAfterDismissing = streamingAppPlayback(streamingPackageName)
        val didPositionGoBack = playbackWhenDismissed != null && playbackAfterDismissing != null &&
            playbackAfterDismissing.position < playbackWhenDismissed.position
        val dismissResultDescription = when {
            mediaIdWhenDismissed != mediaIdAfterDismissing || didPositionGoBack -> "DIDN'T DISMISS: the app started another video"
            playbackAfterDismissing?.isPlaying == true -> "DISMISSED: the same episode is still playing"
            else -> "DIDN'T DISMISS: the episode is ${describePlayback(playbackAfterDismissing)}"
        }
        reportStatus(
            "${DISMISS_CHECK_DELAY / 1000} s after dismissing: ${describePlayback(playbackAfterDismissing)}. " +
                dismissResultDescription,
        )
    }

    // TODO: Validate
    private fun streamingAppMediaId(streamingPackageName: String): String? =
        withStreamingAppMediaController(streamingPackageName) { streamingAppMediaController ->
            streamingAppMediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        }

    // TODO: Validate
    private fun clickableSkipButtonKindsOfStreamingApp(streamingApp: StreamingApp): List<PlayerButtonKind> =
        PlayerButtonKind.entries.filter { playerButtonKind ->
            playerButtonKind.canBeClicked && playerButtonKind.playerButtonOfStreamingApp(streamingApp) != null
        }

    // TODO: Validate
    private fun skipButtonClickCheck(streamingApp: StreamingApp, packageName: String): (() -> PlayerButtonKind?)? {
        val skipButtonClickerByKind = PlayerButtonKind.entries
            .filter { it.canBeClicked }
            .mapNotNull { playerButtonKind ->
                playerButtonKind.onScreenElementOfStreamingApp(streamingApp)?.let { playerButtonKind to SkipButtonClicker(it) }
            }
            .toMap()
        val screenshotSkipButtonKinds = streamingApp.skipButtonTextsInScreenshots.keys.filter { it.canBeClicked }.toSet()
        val nextEpisodeAutoplayCancelButtons = streamingApp.nextEpisodeAutoplayCancelButtonsInScreenshots
        if (skipButtonClickerByKind.isEmpty() && screenshotSkipButtonKinds.isEmpty()) return null
        val screenshotSkipButtonClicker = streamingApp
            .takeIf { screenshotSkipButtonKinds.isNotEmpty() || nextEpisodeAutoplayCancelButtons.isNotEmpty() }
            ?.let { ScreenshotSkipButtonClicker(it.skipButtonTextsInScreenshots, nextEpisodeAutoplayCancelButtons) }
        val seenScreenshotClickLogLines = mutableSetOf<String>()
        return check@{
            val clickedAccessibilitySkipButtonKind = StreamingTestDevice.runOnMainThreadAndWait {
                skipButtonClickerByKind.entries.firstOrNull { (_, skipButtonClicker) ->
                    skipButtonClicker.clickWhenShowing(packageName, System.currentTimeMillis())
                }?.key
            }
            if (clickedAccessibilitySkipButtonKind != null || screenshotSkipButtonClicker == null) {
                return@check clickedAccessibilitySkipButtonKind
            }
            StreamingTestDevice.runOnMainThreadAndWait {
                screenshotSkipButtonClicker.startClickingShownButtons(
                    packageName,
                    screenshotSkipButtonKinds,
                    clicksNextEpisodeAutoplayCancelButton = true,
                    isAppPlaying = streamingAppPlayback(packageName)?.isPlaying == true,
                )
            }
            val newScreenshotClickLogLines = StreamingTestDevice
                .runShellCommand("logcat -d -v time -s $SCREENSHOT_CLICKER_LOG_TAG")
                .lines()
                .filter { " in $packageName" in it && seenScreenshotClickLogLines.add(it) }
            val clickedCancelButton = nextEpisodeAutoplayCancelButtons.firstOrNull { cancelButton ->
                newScreenshotClickLogLines.any { "Clicking ${cancelButton.buttonText} in $packageName" in it }
            }
            if (clickedCancelButton != null) {
                reportStatus("Clicked ${clickedCancelButton.buttonText} to dismiss the next episode card in $packageName")
                reportPlaybackSoon(packageName)
            }
            screenshotSkipButtonKinds.firstOrNull { screenshotSkipButtonKind ->
                newScreenshotClickLogLines.any { "Clicking ${screenshotSkipButtonKind.label} in $packageName" in it }
            }
        }
    }

    // TODO: Validate
    private fun streamingAppPlayback(streamingPackageName: String): StreamingAppPlayback? =
        withStreamingAppMediaController(streamingPackageName) { streamingAppMediaController ->
            val playbackState = streamingAppMediaController?.playbackState ?: return@withStreamingAppMediaController null
            val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING
            StreamingAppPlayback(isPlaying, estimatedPosition(playbackState))
        }

    // TODO: Validate
    private data class StreamingAppPlayback(val isPlaying: Boolean, val position: Long)

    // TODO: Validate
    private fun <T> withStreamingAppMediaController(
        streamingPackageName: String,
        useMediaController: (MediaController?) -> T,
    ): T {
        val uiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL_PERMISSION)
        try {
            val mediaSessionManager = targetContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val streamingAppMediaController =
                mediaSessionManager.getActiveSessions(null).firstOrNull { it.packageName == streamingPackageName }
            return useMediaController(streamingAppMediaController)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    // TODO: Validate
    private fun describePlayback(streamingAppPlayback: StreamingAppPlayback?): String =
        streamingAppPlayback?.let {
            "%s at %.1f s".format(if (it.isPlaying) "playing" else "paused", it.position / 1000.0)
        } ?: "unknown"

    // TODO: Validate
    private fun reportStatus(statusMessage: String) {
        Log.i(TAG, statusMessage)
        instrumentation.sendStatus(STATUS_CODE, Bundle().apply { putString(STATUS_KEY, statusMessage) })
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "SkipButtonClickerLive"
        // TODO: Validate
        const val SCREENSHOT_CLICKER_LOG_TAG = "ScreenshotSkipButtons"
        // TODO: Validate
        const val MEDIA_CONTENT_CONTROL_PERMISSION = "android.permission.MEDIA_CONTENT_CONTROL"

        // TODO: Validate
        const val STATUS_KEY = "skipButtonClicker"

        // TODO: Validate
        const val STATUS_CODE = 100

        // TODO: Validate
        const val CHECK_INTERVAL = 1_000L

        // TODO: Validate
        const val PLAYBACK_AFTER_CLICK_DELAY = 3_000L

        // TODO: Validate
        const val DISMISS_CHECK_DELAY = 5_000L

        // TODO: Validate
        const val MINIMUM_SKIP_LENGTH = 5_000L

        // TODO: Validate
        const val RESULTS_DIRECTORY_NAME = "live_tests"
    }
}
