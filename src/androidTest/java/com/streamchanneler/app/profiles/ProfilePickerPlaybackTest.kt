// TODO: Validate
package com.streamchanneler.app.profiles

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.ActivePlayback
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.playback.VideoLaunchResult
import com.streamchanneler.app.playback.StreamingAppLauncher
import com.streamchanneler.app.profiles.ProfilePickerStageTimeline.Companion.APP_OPENED_STAGE_NAME
import com.streamchanneler.app.profiles.ProfilePickerStageTimeline.Companion.LINK_OPENED_STAGE_NAME
import com.streamchanneler.app.profiles.ProfilePickerStageTimeline.Companion.PIN_ENTERED_STAGE_NAME
import com.streamchanneler.app.profiles.ProfilePickerStageTimeline.Companion.PLAYBACK_STARTED_STAGE_NAME
import com.streamchanneler.app.profiles.ProfilePickerStageTimeline.Companion.PROFILE_PICKED_STAGE_NAME
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.streamingapps.StreamingApps
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// TODO: Validate
@RunWith(Parameterized::class)
class ProfilePickerPlaybackTest(private val streamingAppKey: String) {
    // TODO: Validate
    @get:Rule
    val testName = TestName()

    // TODO: Validate
    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    // TODO: Validate
    private lateinit var streamingAppTestSecrets: StreamingAppTestSecrets
    // TODO: Validate
    private lateinit var streamingPackageName: String
    // TODO: Validate
    private lateinit var stageTimeline: ProfilePickerStageTimeline
    // TODO: Validate
    private var testedStreamingAppHasProfileScreen = true
    // TODO: Validate
    private var testedProfilePin = ""

    // TODO: Validate
    @Before
    fun prepareDeviceAndKillStreamingApp() {
        val settingsKey = SETTINGS_KEY_BY_STREAMING_APP_KEY[streamingAppKey] ?: throw AssertionError(
            "$streamingAppKey isn't a streaming app these tests know. Use one of ${SETTINGS_KEY_BY_STREAMING_APP_KEY.keys.joinToString(", ")}",
        )
        val testedStreamingApp = StreamingApps.all.first { it.site.pluginKey == settingsKey }
        testedStreamingAppHasProfileScreen = testedStreamingApp.profileScreen != null
        streamingAppTestSecrets = StreamingAppTestSecrets.readSecretsOfStreamingApp(streamingAppKey)
        StreamingTestDevice.clearLog()
        if (!PlaybackMonitor.hasNotificationListenerAccess(targetContext)) {
            throw AssertionError("Stream Channeler doesn't have notification access, so it can't tell when playback starts")
        }
        StreamingTestDevice.turnOnProfilePicker(targetContext)
        StreamingTestDevice.startShizukuIfNeeded(targetContext)
        streamingPackageName = testedStreamingApp.packageName
        StreamingTestDevice.assertInstalled(targetContext, streamingPackageName)
        val localAppStorage = targetContext.streamChannelerApp.localAppStorage
        StreamingTestProfiles.saveProfilesFromAsset(localAppStorage)
        testedProfilePin = localAppStorage.streamingProfilePin(settingsKey)
        StreamingTestDevice.wakeUpAndForceStop(streamingPackageName)
        stageTimeline = ProfilePickerStageTimeline(
            streamingPackageName,
            targetContext.streamChannelerApp.playbackMonitor,
            targetContext.streamChannelerApp.profilePicker,
        )
    }

    // TODO: Validate
    @After
    fun saveScreenshotAndLogThenStopFollowingPlayback() {
        val testOutputName = testName.methodName
        StreamingTestDevice.saveScreenshot("$testOutputName.png")
        val stageDescription = if (::stageTimeline.isInitialized) stageTimeline.describeStages() else ""
        val profilePickerLog = StreamingTestDevice.profilePickerLog()
        StreamingTestDevice.saveTestOutput("$testOutputName.txt") { testOutputStream ->
            testOutputStream.write("$stageDescription\n\n$profilePickerLog".toByteArray())
        }
        StreamingTestDevice.runOnMainThreadAndWait {
            targetContext.streamChannelerApp.playbackMonitor.stopMonitoring()
            targetContext.streamChannelerApp.profilePicker.disarm()
        }
    }

    // TODO: Validate
    @Test
    fun killThenOpenUrl() {
        openEpisodeLinkThroughTheApp()
        assertPlaybackStartedAfterPickingProfile("The episode didn't start playing within ${PLAYBACK_TIMEOUT / 1000} seconds")
    }

    // TODO: Validate
    @Test
    fun openUrlFromHome() {
        openStreamingAppAndArmProfilePicker()
        val profileFinishedStageName =
            if (testedProfilePin.isNotEmpty()) PIN_ENTERED_STAGE_NAME else PROFILE_PICKED_STAGE_NAME
        if (testedStreamingAppHasProfileScreen) stageTimeline.followUntilStage(profileFinishedStageName, PROFILE_PICKER_TIMEOUT)
        Thread.sleep(HOME_PAGE_LOAD_TIME)
        openEpisodeLinkThroughTheApp()
        assertPlaybackStartedAfterPickingProfile(
            "The episode didn't start playing within ${PLAYBACK_TIMEOUT / 1000} seconds of opening its link",
        )
    }

    // TODO: Validate
    private fun openStreamingAppAndArmProfilePicker() {
        StreamingTestDevice.runOnMainThreadAndWait {
            val streamingAppLaunchIntent = targetContext.packageManager.getLeanbackLaunchIntentForPackage(streamingPackageName)
                ?: targetContext.packageManager.getLaunchIntentForPackage(streamingPackageName)
            targetContext.startActivity(streamingAppLaunchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            targetContext.streamChannelerApp.profilePicker.arm(StreamingApps.installedAppWithPackageName(streamingPackageName, targetContext.packageManager))
        }
        stageTimeline.recordStage(APP_OPENED_STAGE_NAME)
    }

    // TODO: Validate
    private fun openEpisodeLinkThroughTheApp() {
        val episodeVideo = GuideVideo(
            episodeId = TEST_EPISODE_ID,
            title = null,
            subtitle = "",
            url = streamingAppTestSecrets.episodeUrl,
            episodeKey = streamingAppTestSecrets.episodeKey,
            duration = null,
            thumbnailUrl = null,
            faviconUrl = null,
            pluginKey = SETTINGS_KEY_BY_STREAMING_APP_KEY.getValue(streamingAppKey),
            watched = false,
            titleId = "test",
            sourceKey = "test",
        )
        val episodeLaunchFailureReason = StreamingTestDevice.runOnMainThreadAndWait {
            when (val episodeLaunchResult = StreamingAppLauncher.launchVideo(targetContext, episodeVideo)) {
                is VideoLaunchResult.Failed -> episodeLaunchResult.reason
                is VideoLaunchResult.Launched -> {
                    val testActivePlayback = ActivePlayback(
                        channelId = TEST_EPISODE_ID,
                        channelName = TEST_EPISODE_ID,
                        channelVideos = listOf(episodeVideo),
                        playingEpisodeId = episodeVideo.episodeId,
                        targetPackageName = episodeLaunchResult.launchedPackageName,
                        launchedAt = System.currentTimeMillis(),
                        isMonitoring = true,
                    )
                    targetContext.streamChannelerApp.videoPlayer.followLaunchedVideo(episodeLaunchResult, testActivePlayback)
                    null
                }
            }
        }
        if (episodeLaunchFailureReason != null) {
            throw stageTimeline.assertionErrorWithStageTimings("The app couldn't open the episode link: $episodeLaunchFailureReason")
        }
        stageTimeline.recordStage(LINK_OPENED_STAGE_NAME)
    }

    // TODO: Validate
    private fun assertPlaybackStartedAfterPickingProfile(playbackNotStartedFailureReason: String) {
        if (!stageTimeline.followUntilStage(PLAYBACK_STARTED_STAGE_NAME, PLAYBACK_TIMEOUT)) {
            throw stageTimeline.assertionErrorWithStageTimings(playbackNotStartedFailureReason)
        }
        if (!testedStreamingAppHasProfileScreen) return
        assumeTrue(
            "Played without picking a profile\n${stageTimeline.describeStages()}",
            stageTimeline.hasReached(PROFILE_PICKED_STAGE_NAME) || stageTimeline.hasReached(PIN_ENTERED_STAGE_NAME),
        )
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val TEST_EPISODE_ID = "ProfilePickerPlaybackTest"

        // TODO: Validate
        private const val PROFILE_PICKER_TIMEOUT = 45_000L

        // TODO: Validate
        private const val PLAYBACK_TIMEOUT = 90_000L

        // TODO: Validate
        private const val HOME_PAGE_LOAD_TIME = 15_000L

        // TODO: Validate
        private val SETTINGS_KEY_BY_STREAMING_APP_KEY = mapOf(
            "crunchyroll" to "Crunchyroll",
            "disney_plus" to "Disney+",
            "hbo_max" to "HBO Max",
            "hidive" to "HIDIVE",
            "hulu" to "Hulu",
            "netflix" to "Netflix",
            "peacock" to "Peacock",
            "paramount_plus" to "Paramount+",
            "prime_video" to "Amazon",
            "pluto_tv" to "Pluto TV",
            "tubi" to "Tubi",
            "youtube" to "YouTube",
        )

        // TODO: Validate
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun streamingAppKeysToTest(): List<String> =
            InstrumentationRegistry.getArguments().getString("streamingApps")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: SETTINGS_KEY_BY_STREAMING_APP_KEY.keys.toList()
    }
}
