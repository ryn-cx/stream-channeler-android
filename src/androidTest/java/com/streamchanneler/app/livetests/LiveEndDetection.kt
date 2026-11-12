// TODO: Validate
package com.streamchanneler.app.livetests

import android.content.Context
import com.streamchanneler.app.playback.LastReportedMediaTitleListener
import com.streamchanneler.app.playback.MediaSessionObservation
import com.streamchanneler.app.playback.PlaybackEndDetector
import com.streamchanneler.app.playback.WatchStatus
import com.streamchanneler.app.playback.mediaSessionObservationOfController
import com.streamchanneler.app.playback.endrules.P2MediaIdChange
import com.streamchanneler.app.playback.endrules.P1MediaTitleChange
import com.streamchanneler.app.playback.endrules.P6NextPlayerVideo
import com.streamchanneler.app.playback.endrules.P7PositionReset
import com.streamchanneler.app.playback.endrules.P8GuideLength
import com.streamchanneler.app.playback.endrules.PlaybackEndRule
import com.streamchanneler.app.playback.endrules.P9ReportedDuration
import com.streamchanneler.app.playback.endrules.P3StoppedSession
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
class LiveEndDetection(
    context: Context,
    private val streamingApp: StreamingApp,
    private val packageName: String,
    private val videoDuration: Long?,
    private val reportStatus: (String) -> Unit,
) {
    // TODO: Validate
    private val streamChannelerApp = context.streamChannelerApp
    // TODO: Validate
    private val playbackMonitor = streamChannelerApp.playbackMonitor

    // TODO: Validate
    private val lastReportedMediaTitleListener =
        LastReportedMediaTitleListener(streamChannelerApp.mediaSessions).apply { start(packageName) }

    // TODO: Validate
    private var monitorEndDetector = endDetectorWithMonitorRules()

    // TODO: Validate
    private var singleRuleEndDetectors = singleRuleEndDetectors()

    // TODO: Validate
    private var otherRuleEndDetectorByRuleName = otherRuleEndDetectors()

    // TODO: Validate
    fun checkForEnds() {
        val now = System.currentTimeMillis()
        val observation = currentObservation()
        for ((otherRuleName, otherRuleEndDetector) in otherRuleEndDetectorByRuleName.toList()) {
            if (otherRuleEndDetector.watchStatusAfterPoll(now, observation) != WatchStatus.FINISHED) continue
            reportStatus("OTHER RULE: $otherRuleName, which the app does not use, sees an end in $packageName. ${describeObservation(observation)}")
            otherRuleEndDetectorByRuleName = otherRuleEndDetectorByRuleName + (otherRuleName to otherRuleEndDetectorOf(otherRuleName))
        }
        val hadStartedPlaying = monitorEndDetector.hasStartedPlaying
        val monitorWatchStatus = monitorEndDetector.watchStatusAfterPoll(now, observation)
        if (!hadStartedPlaying && monitorEndDetector.hasStartedPlaying) {
            reportStatus("Playback started in $packageName, so the end detectors are running")
        }
        for ((ruleIndex, singleRuleEndDetector) in singleRuleEndDetectors.withIndex()) {
            if (singleRuleEndDetector.watchStatusAfterPoll(now, observation) != WatchStatus.FINISHED) continue
            val ruleName = ruleNameOf(playbackMonitor.playbackEndRulesWithCheckedMoments(streamingApp)[ruleIndex])
            reportStatus("END SIGNAL: $ruleName sees an end in $packageName. ${describeObservation(observation)}")
            singleRuleEndDetectors[ruleIndex] = singleRuleEndDetectorOf(ruleIndex)
        }
        if (monitorWatchStatus == WatchStatus.FINISHED) {
            reportStatus("ENDED: the playback monitor would end the video here. ${describeObservation(observation)}")
            monitorEndDetector = endDetectorWithMonitorRules()
            singleRuleEndDetectors = singleRuleEndDetectors()
        }
    }

    // TODO: Validate
    private fun otherRuleEndDetectors(): Map<String, PlaybackEndDetector> {
        val usedRuleNames = playbackMonitor.playbackEndRulesWithCheckedMoments(streamingApp).map(::ruleNameOf).toSet()
        val generalRuleNames = GENERAL_RULE_BUILDERS.keys + if (videoDuration != null) GUIDE_LENGTH_RULE_BUILDERS.keys else emptySet()
        return generalRuleNames.filter { it !in usedRuleNames }.associateWith(::otherRuleEndDetectorOf)
    }

    // TODO: Validate
    private fun otherRuleEndDetectorOf(otherRuleName: String): PlaybackEndDetector = PlaybackEndDetector(
        launchedAt = System.currentTimeMillis(),
        videoDuration = videoDuration,
        playbackEndRules = listOf((GENERAL_RULE_BUILDERS + GUIDE_LENGTH_RULE_BUILDERS).getValue(otherRuleName)()),
    )

    // TODO: Validate
    private fun currentObservation(): MediaSessionObservation? {
        streamingApp.playerWithoutMediaSession?.let { playerWithoutMediaSession ->
            return playbackMonitor.playerWithoutMediaSessionPlayback.mediaSessionObservationOfPlayer(packageName, playerWithoutMediaSession)
        }
        val streamingAppController = streamChannelerApp.mediaSessions.activeMediaControllers()
            ?.firstOrNull { it.packageName == packageName }
            ?: return null
        return lastReportedMediaTitleListener.observationWithLastReportedMediaTitle(
            mediaSessionObservationOfController(streamingAppController),
        )
    }

    // TODO: Validate
    private fun endDetectorWithMonitorRules(): PlaybackEndDetector = PlaybackEndDetector(
        launchedAt = System.currentTimeMillis(),
        videoDuration = videoDuration,
        playbackEndRules = playbackMonitor.playbackEndRulesWithCheckedMoments(streamingApp),
    )

    // TODO: Validate
    private fun singleRuleEndDetectors(): MutableList<PlaybackEndDetector> =
        playbackMonitor.playbackEndRulesWithCheckedMoments(streamingApp).indices.map(::singleRuleEndDetectorOf).toMutableList()

    // TODO: Validate
    private fun singleRuleEndDetectorOf(ruleIndex: Int): PlaybackEndDetector = PlaybackEndDetector(
        launchedAt = System.currentTimeMillis(),
        videoDuration = videoDuration,
        playbackEndRules = listOf(playbackMonitor.playbackEndRulesWithCheckedMoments(streamingApp)[ruleIndex]),
    )

    // TODO: Validate
    private fun ruleNameOf(playbackEndRule: Any): String = playbackEndRule::class.simpleName ?: playbackEndRule.toString()

    // TODO: Validate
    private fun describeObservation(observation: MediaSessionObservation?): String {
        if (observation == null) return "The app has no media session."
        return "Status ${observation.playbackStatus}, position %.1f s, media ID ${observation.mediaId}, title ${observation.mediaTitle}."
            .format(observation.position / 1000.0)
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        val GENERAL_RULE_BUILDERS: Map<String, () -> PlaybackEndRule> = mapOf(
            "P3StoppedSession" to { P3StoppedSession() },
            "P2MediaIdChange" to ::P2MediaIdChange,
            "P1MediaTitleChange" to { P1MediaTitleChange() },
            "P9ReportedDuration" to ::P9ReportedDuration,
            "P6NextPlayerVideo" to ::P6NextPlayerVideo,
        )

        // TODO: Validate
        val GUIDE_LENGTH_RULE_BUILDERS: Map<String, () -> PlaybackEndRule> = mapOf(
            "P7PositionReset" to ::P7PositionReset,
            "P8GuideLength" to ::P8GuideLength,
        )
    }
}
