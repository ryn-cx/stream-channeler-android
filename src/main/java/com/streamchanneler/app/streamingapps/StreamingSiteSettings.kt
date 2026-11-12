// TODO: Validate
package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.screenautomation.OnScreenElement
import com.streamchanneler.app.screenautomation.PlayerButton
import org.json.JSONArray
import org.json.JSONObject

// TODO: Validate
enum class PlayerButtonKind(val label: String, val canBeClicked: Boolean, val canPlayNextEpisode: Boolean) {
    SKIP_INTRO("Skip Intro", canBeClicked = true, canPlayNextEpisode = false),
    SKIP_RECAP("Skip Recap", canBeClicked = true, canPlayNextEpisode = false),
    SKIP_CREDITS("Skip Credits", canBeClicked = true, canPlayNextEpisode = true),
    SKIP_PREVIEW("Skip Preview", canBeClicked = true, canPlayNextEpisode = false),
    SKIP_AD("Skip Ad", canBeClicked = true, canPlayNextEpisode = false),
    NEXT_EPISODE("Next Episode", canBeClicked = false, canPlayNextEpisode = true),
    ;

    // TODO: Validate
    fun playerButtonOfStreamingApp(streamingApp: StreamingApp): PlayerButton? = when (this) {
        SKIP_INTRO -> streamingApp.skipIntroButton
        SKIP_RECAP -> streamingApp.skipRecapButton
        SKIP_CREDITS -> streamingApp.skipCreditsButton
        SKIP_PREVIEW -> streamingApp.skipPreviewButton
        SKIP_AD -> streamingApp.skipAdButton
        NEXT_EPISODE -> streamingApp.nextEpisodeCard?.shownElement
    }

    // TODO: Validate
    fun onScreenElementOfStreamingApp(streamingApp: StreamingApp): OnScreenElement? =
        playerButtonOfStreamingApp(streamingApp) as? OnScreenElement
}

// TODO: Validate
enum class PlayNextEpisodeWhen(val playerButtonKind: PlayerButtonKind?) {
    SKIP_CREDITS_APPEARS(PlayerButtonKind.SKIP_CREDITS),
    NEXT_EPISODE_BUTTON_APPEARS(PlayerButtonKind.NEXT_EPISODE),
    EPISODE_ENDS(null),
    ;

    // TODO: Validate
    val settingLabel: String
        get() = playerButtonKind?.let { "The ${it.label} button appears" } ?: "The episode ends"

    // TODO: Validate
    fun canStreamingAppDetectMoment(streamingApp: StreamingApp): Boolean =
        playerButtonKind?.let { it.onScreenElementOfStreamingApp(streamingApp) != null }
            ?: streamingApp.playbackEndRules().isNotEmpty()
}

// TODO: Validate
data class StreamingSiteSettings(
    val checkedPlayNextEpisodeMoments: Set<PlayNextEpisodeWhen> = emptySet(),
    val clickedPlayerButtons: Set<PlayerButtonKind> = emptySet(),
) {
    // TODO: Validate
    fun toJson(): JSONObject = JSONObject()
        .put("checkedPlayNextEpisodeMoments", JSONArray(checkedPlayNextEpisodeMoments.map { it.name }))
        .put("clickedPlayerButtons", JSONArray(clickedPlayerButtons.map { it.name }))

    // TODO: Validate
    companion object {
        // TODO: Validate
        fun fromJson(streamingSiteSettingsJson: JSONObject): StreamingSiteSettings = StreamingSiteSettings(
            checkedPlayNextEpisodeMoments = streamingSiteSettingsJson.enumEntriesNamedInArray("checkedPlayNextEpisodeMoments"),
            clickedPlayerButtons = streamingSiteSettingsJson.enumEntriesNamedInArray("clickedPlayerButtons"),
        )
    }
}

// TODO: Validate
private inline fun <reified EnumType : Enum<EnumType>> JSONObject.enumEntriesNamedInArray(arrayKey: String): Set<EnumType> {
    val entryNamesJson = optJSONArray(arrayKey) ?: return emptySet()
    val entryNames = (0 until entryNamesJson.length()).map(entryNamesJson::optString)
    return enumValues<EnumType>().filter { enumEntry -> enumEntry.name in entryNames }.toSet()
}

// TODO: Validate
sealed class StreamingSiteToggleSetting(val settingLabel: String) {
    // TODO: Validate
    abstract fun isTurnedOnInSiteSettings(streamingSiteSettings: StreamingSiteSettings): Boolean

    // TODO: Validate
    abstract fun siteSettingsWithSettingTurnedOnOrOff(streamingSiteSettings: StreamingSiteSettings, isOn: Boolean): StreamingSiteSettings

    // TODO: Validate
    abstract fun doesStreamingAppHaveFeature(streamingApp: StreamingApp): Boolean

    // TODO: Validate
    data class ClicksPlayerButton(val playerButtonKind: PlayerButtonKind) :
        StreamingSiteToggleSetting("Click the ${playerButtonKind.label} button") {
        // TODO: Validate
        override fun isTurnedOnInSiteSettings(streamingSiteSettings: StreamingSiteSettings): Boolean =
            playerButtonKind in streamingSiteSettings.clickedPlayerButtons

        // TODO: Validate
        override fun siteSettingsWithSettingTurnedOnOrOff(streamingSiteSettings: StreamingSiteSettings, isOn: Boolean): StreamingSiteSettings =
            streamingSiteSettings.copy(
                clickedPlayerButtons = if (isOn) {
                    streamingSiteSettings.clickedPlayerButtons + playerButtonKind
                } else {
                    streamingSiteSettings.clickedPlayerButtons - playerButtonKind
                },
            )

        // TODO: Validate
        override fun doesStreamingAppHaveFeature(streamingApp: StreamingApp): Boolean =
            playerButtonKind.playerButtonOfStreamingApp(streamingApp) != null
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        val entries: List<StreamingSiteToggleSetting> by lazy {
            PlayerButtonKind.entries.filter { it.canBeClicked }.map(::ClicksPlayerButton)
        }
    }
}

// TODO: Validate
fun StreamingApp.playNextEpisodeWhenChoices(): List<PlayNextEpisodeWhen> =
    PlayNextEpisodeWhen.entries.filter { playNextEpisodeWhen -> playNextEpisodeWhen.canStreamingAppDetectMoment(this) }

// TODO: Validate
private fun StreamingApp.alwaysOnPlayNextEpisodeMoment(): PlayNextEpisodeWhen? = playNextEpisodeWhenChoices().lastOrNull()

// TODO: Validate
fun StreamingApp.activePlayNextEpisodeMoments(checkedPlayNextEpisodeMoments: Set<PlayNextEpisodeWhen>): List<PlayNextEpisodeWhen> {
    val alwaysOnMoment = alwaysOnPlayNextEpisodeMoment()
    return playNextEpisodeWhenChoices().filter { playNextEpisodeMoment ->
        playNextEpisodeMoment == alwaysOnMoment || playNextEpisodeMoment in checkedPlayNextEpisodeMoments
    }
}
