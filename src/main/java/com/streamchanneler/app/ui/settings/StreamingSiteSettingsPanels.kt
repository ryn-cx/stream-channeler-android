// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.streamingapps.ALL_STREAMING_SITES
import com.streamchanneler.app.streamingapps.PlayNextEpisodeWhen
import com.streamchanneler.app.streamingapps.StreamingApp
import com.streamchanneler.app.streamingapps.StreamingApps
import com.streamchanneler.app.streamingapps.StreamingSite
import com.streamchanneler.app.streamingapps.StreamingSiteSettings
import com.streamchanneler.app.streamingapps.StreamingSiteToggleSetting
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.unfocusableTextLabel

// TODO: Validate
internal class StreamingSiteSettingsPanels(
    private val activity: Activity,
    private val settingRows: SettingRows,
    private val streamingProfileSettings: StreamingProfileSettings,
    private val afterSettingChanged: () -> Unit,
) {
    // TODO: Validate
    private val localAppStorage get() = activity.streamChannelerApp.localAppStorage
    // TODO: Validate
    private val streamingSites = ALL_STREAMING_SITES.sortedBy { streamingSite -> streamingSite.pluginKey }
    // TODO: Validate
    private val streamingAppsWithProfileScreens =
        StreamingApps.all.filter { it.profileScreen != null }.sortedBy { it.site.pluginKey }
    // TODO: Validate
    private val streamingAppsPickingProfilesByName = streamingAppsWithProfileScreens
        .filter { profileStreamingApp -> profileStreamingApp.profileScreen?.let(::picksProfilesByName) == true }
        .distinctBy { profileStreamingApp -> profileStreamingApp.site.pluginKey }
    // TODO: Validate
    private val streamingAppsTakingProfilePins = streamingAppsWithProfileScreens
        .filter { profileStreamingApp -> profileStreamingApp.pinEntry != null }
        .distinctBy { profileStreamingApp -> profileStreamingApp.site.pluginKey }
    // TODO: Validate
    private var allAppsUnsavedProfileName: String? = null
    // TODO: Validate
    private var allAppsUnsavedProfilePin: String? = null

    // TODO: Validate
    fun addTabs(settingsTabs: SettingsTabs) {
        settingsTabs.addTab("All streaming apps", null, allAppsPanelColumn())
        for (streamingSite in streamingSites) {
            settingsTabs.addTab(streamingSite.pluginKey, installedAppIcon(streamingSite.apps), sitePanelColumn(streamingSite))
        }
    }

    // TODO: Validate
    private fun allAppsPanelColumn(): LinearLayout {
        val allAppsPanelColumn = activity.settingsPanelColumn()
        allAppsPanelColumn.addView(
            activity.settingsNote(
                "A change here is not saved until you choose the Save button next to it. Saving applies it to every " +
                    "streaming app that has the feature. Each app's own tab can change it again.",
            ),
        )
        allAppsPanelColumn.addView(activity.sectionHeading("Play the next episode when"))
        for (playNextEpisodeMoment in PlayNextEpisodeWhen.entries) {
            addAllAppsSwitchRow(
                allAppsPanelColumn,
                sitesWithSetting = streamingSites.filter { it.canPlayNextEpisodeMomentBeChecked(playNextEpisodeMoment) },
                settingLabel = playNextEpisodeMoment.settingLabel,
                isOnInSite = { streamingSite -> isPlayNextEpisodeMomentChecked(streamingSite, playNextEpisodeMoment) },
                writeToSites = { sitesWithSetting, isMomentChecked ->
                    writePlayNextEpisodeMomentChecked(sitesWithSetting, playNextEpisodeMoment, isMomentChecked)
                },
            )
        }
        allAppsPanelColumn.addView(
            activity.settingsNote("Each app's last moment, usually the episode ending, is always on, so every video ends."),
            activity.rowSpacingLayoutParams(),
        )
        for (toggleSetting in StreamingSiteToggleSetting.entries) {
            addAllAppsSwitchRow(
                allAppsPanelColumn,
                sitesWithSetting = streamingSites.filter { it.doesAnyAppHaveFeature(toggleSetting) },
                settingLabel = toggleSetting.settingLabel,
                isOnInSite = { streamingSite -> isToggleSettingOn(streamingSite, toggleSetting) },
                writeToSites = { sitesWithSetting, isOn -> writeToggleSetting(sitesWithSetting, toggleSetting, isOn) },
            )
        }
        if (streamingAppsPickingProfilesByName.isNotEmpty() || streamingAppsTakingProfilePins.isNotEmpty()) {
            allAppsPanelColumn.addView(activity.sectionHeading("Streaming profile"))
            allAppsPanelColumn.addView(activity.streamingProfileWarning(), activity.rowSpacingLayoutParams())
        }
        if (streamingAppsPickingProfilesByName.isNotEmpty()) {
            settingRows.addRowWithSaveButton(
                allAppsPanelColumn,
                streamingAppIcons = streamingAppsPickingProfilesByName.mapNotNull { installedAppIcon(listOf(it)) },
                settingLabel = "Profile name in every app",
                settingRowEnd = { SettingRowEnd.Value(allAppsProfileNameLabel()) },
                hasUnsavedChange = { allAppsUnsavedProfileName != null },
                onChosen = { editAllAppsProfileName() },
                onSaved = {
                    allAppsUnsavedProfileName?.let { unsavedProfileName ->
                        streamingProfileSettings.saveProfiles(
                            streamingAppsPickingProfilesByName.map { profileStreamingApp ->
                                streamingProfileSettings.savedProfile(profileStreamingApp.site.pluginKey)
                                    .copy(profileName = unsavedProfileName)
                            },
                        )
                    }
                    allAppsUnsavedProfileName = null
                },
            )
        }
        if (streamingAppsTakingProfilePins.isNotEmpty()) {
            settingRows.addRowWithSaveButton(
                allAppsPanelColumn,
                streamingAppIcons = streamingAppsTakingProfilePins.mapNotNull { installedAppIcon(listOf(it)) },
                settingLabel = "Profile PIN in every app",
                settingRowEnd = { SettingRowEnd.Value(allAppsProfilePinLabel()) },
                hasUnsavedChange = { allAppsUnsavedProfilePin != null },
                onChosen = { editAllAppsProfilePin() },
                onSaved = {
                    allAppsUnsavedProfilePin?.let { unsavedProfilePin ->
                        streamingProfileSettings.saveProfiles(
                            streamingAppsTakingProfilePins.map { pinStreamingApp ->
                                streamingProfileSettings.savedProfile(pinStreamingApp.site.pluginKey)
                                    .copy(profilePin = unsavedProfilePin)
                            },
                        )
                    }
                    allAppsUnsavedProfilePin = null
                },
            )
        }
        return allAppsPanelColumn
    }

    // TODO: Validate
    private fun addAllAppsSwitchRow(
        allAppsPanelColumn: LinearLayout,
        sitesWithSetting: List<StreamingSite>,
        settingLabel: String,
        isOnInSite: (StreamingSite) -> Boolean,
        writeToSites: (sitesWithSetting: List<StreamingSite>, isOn: Boolean) -> Unit,
    ) {
        if (sitesWithSetting.isEmpty()) return
        var unsavedIsOn: Boolean? = null
        settingRows.addRowWithSaveButton(
            allAppsPanelColumn,
            streamingAppIcons = sitesWithSetting.mapNotNull { installedAppIcon(it.apps) },
            settingLabel = settingLabel,
            settingRowEnd = {
                SettingRowEnd.Switch(
                    unsavedIsOn?.let(::onOrOffSettingSwitchState)
                        ?: sharedOrMixedSettingSwitchState(sitesWithSetting.map(isOnInSite)),
                )
            },
            hasUnsavedChange = {
                val shownUnsavedIsOn = unsavedIsOn
                shownUnsavedIsOn != null && sitesWithSetting.any { siteWithSetting -> isOnInSite(siteWithSetting) != shownUnsavedIsOn }
            },
            onChosen = { unsavedIsOn = !(unsavedIsOn ?: sitesWithSetting.all(isOnInSite)) },
            onSaved = {
                unsavedIsOn?.let { savedIsOn -> writeToSites(sitesWithSetting, savedIsOn) }
                unsavedIsOn = null
            },
        )
    }

    // TODO: Validate
    private fun sitePanelColumn(streamingSite: StreamingSite): LinearLayout {
        val sitePanelColumn = activity.settingsPanelColumn()
        sitePanelColumn.addView(activity.sectionHeading(streamingSite.pluginKey))
        sitePanelColumn.addView(activity.sectionHeading("Play the next episode when"))
        for (playNextEpisodeMoment in streamingSite.playNextEpisodeWhenChoices()) {
            if (!streamingSite.canPlayNextEpisodeMomentBeChecked(playNextEpisodeMoment)) {
                sitePanelColumn.addView(
                    activity.unfocusableTextLabel("${playNextEpisodeMoment.settingLabel} (always on)", 15f, UiColors.TEXT_SECONDARY).apply {
                        setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
                        showSettingRowEnd(SettingRowEnd.Switch(SettingSwitchState.ON))
                    },
                    activity.rowSpacingLayoutParams(),
                )
                continue
            }
            addSiteSwitchRow(
                sitePanelColumn,
                settingLabel = playNextEpisodeMoment.settingLabel,
                isOn = { isPlayNextEpisodeMomentChecked(streamingSite, playNextEpisodeMoment) },
                write = { isMomentChecked ->
                    writePlayNextEpisodeMomentChecked(listOf(streamingSite), playNextEpisodeMoment, isMomentChecked)
                },
            )
        }
        val siteToggleSettings = StreamingSiteToggleSetting.entries.filter { streamingSite.doesAnyAppHaveFeature(it) }
        if (siteToggleSettings.isNotEmpty()) sitePanelColumn.addView(activity.sectionHeading("While watching"))
        for (toggleSetting in siteToggleSettings) {
            addSiteSwitchRow(
                sitePanelColumn,
                settingLabel = toggleSetting.settingLabel,
                isOn = { isToggleSettingOn(streamingSite, toggleSetting) },
                write = { isOn -> writeToggleSetting(listOf(streamingSite), toggleSetting, isOn) },
            )
        }
        val pluginKey = streamingSite.pluginKey
        val siteProfileStreamingApps = streamingSite.apps.filter { it.profileScreen != null }
        if (siteProfileStreamingApps.isNotEmpty()) {
            sitePanelColumn.addView(activity.sectionHeading("Streaming profile"))
            sitePanelColumn.addView(activity.streamingProfileWarning(), activity.rowSpacingLayoutParams())
        }
        for (profileStreamingApp in siteProfileStreamingApps) {
            val profileScreen = profileStreamingApp.profileScreen ?: continue
            settingRows.addRow(
                sitePanelColumn,
                settingLabel = { "$pluginKey profile" },
                settingRowEnd = { SettingRowEnd.Value(streamingProfileSettings.profileLabel(pluginKey, profileScreen)) },
                onChosen = { streamingProfileSettings.editProfile(pluginKey, profileScreen) },
            )
            if (profileStreamingApp.pinEntry != null) {
                settingRows.addRow(
                    sitePanelColumn,
                    settingLabel = { "$pluginKey profile PIN" },
                    settingRowEnd = { SettingRowEnd.Value(hiddenProfilePinLabel(localAppStorage.streamingProfilePin(pluginKey))) },
                    onChosen = { streamingProfileSettings.editProfilePin(pluginKey) },
                )
            }
        }
        return sitePanelColumn
    }

    // TODO: Validate
    private fun addSiteSwitchRow(
        sitePanelColumn: LinearLayout,
        settingLabel: String,
        isOn: () -> Boolean,
        write: (isOn: Boolean) -> Unit,
    ) {
        settingRows.addRow(
            sitePanelColumn,
            settingLabel = { settingLabel + accessibilityNeededNote(isOn()) },
            settingRowEnd = { SettingRowEnd.Switch(onOrOffSettingSwitchState(isOn())) },
            onChosen = { write(!isOn()) },
        )
    }

    // TODO: Validate
    private fun installedAppIcon(streamingApps: List<StreamingApp>): Drawable? =
        streamingApps.firstNotNullOfOrNull { streamingApp ->
            try {
                activity.packageManager.getApplicationIcon(streamingApp.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

    // TODO: Validate
    private fun siteSettings(streamingSite: StreamingSite): StreamingSiteSettings =
        localAppStorage.streamingSiteSettings(streamingSite.pluginKey)

    // TODO: Validate
    private fun isPlayNextEpisodeMomentChecked(streamingSite: StreamingSite, playNextEpisodeMoment: PlayNextEpisodeWhen): Boolean =
        playNextEpisodeMoment in siteSettings(streamingSite).checkedPlayNextEpisodeMoments

    // TODO: Validate
    private fun writePlayNextEpisodeMomentChecked(
        streamingSites: List<StreamingSite>,
        playNextEpisodeMoment: PlayNextEpisodeWhen,
        isMomentChecked: Boolean,
    ) {
        localAppStorage.writeStreamingSiteSettings(streamingSites.map { it.pluginKey }) { streamingSiteSettings ->
            val checkedPlayNextEpisodeMoments = streamingSiteSettings.checkedPlayNextEpisodeMoments
            streamingSiteSettings.copy(
                checkedPlayNextEpisodeMoments = if (isMomentChecked) {
                    checkedPlayNextEpisodeMoments + playNextEpisodeMoment
                } else {
                    checkedPlayNextEpisodeMoments - playNextEpisodeMoment
                },
            )
        }
    }

    // TODO: Validate
    private fun isToggleSettingOn(streamingSite: StreamingSite, toggleSetting: StreamingSiteToggleSetting): Boolean =
        toggleSetting.isTurnedOnInSiteSettings(siteSettings(streamingSite))

    // TODO: Validate
    private fun writeToggleSetting(streamingSites: List<StreamingSite>, toggleSetting: StreamingSiteToggleSetting, isOn: Boolean) {
        localAppStorage.writeStreamingSiteSettings(streamingSites.map { it.pluginKey }) { streamingSiteSettings ->
            toggleSetting.siteSettingsWithSettingTurnedOnOrOff(streamingSiteSettings, isOn)
        }
    }

    // TODO: Validate
    private fun accessibilityNeededNote(isFeatureOn: Boolean): String =
        if (isFeatureOn && !ScreenAutomationService.isEnabled(activity)) ", but needs accessibility allowed on the General tab" else ""

    // TODO: Validate
    private fun allAppsProfileNameLabel(): String {
        val shownProfileName = allAppsUnsavedProfileName ?: sharedSavedProfileName() ?: return "Mixed"
        return shownProfileName.ifEmpty { HIGHLIGHTED_PROFILE_LABEL }
    }

    // TODO: Validate
    private fun allAppsProfilePinLabel(): String {
        val shownProfilePin = allAppsUnsavedProfilePin ?: sharedSavedProfilePin() ?: return "Mixed"
        return hiddenProfilePinLabel(shownProfilePin)
    }

    // TODO: Validate
    private fun sharedSavedProfileName(): String? =
        streamingAppsPickingProfilesByName.map { profileStreamingApp ->
            localAppStorage.streamingProfileName(profileStreamingApp.site.pluginKey)
        }.distinct().singleOrNull()

    // TODO: Validate
    private fun sharedSavedProfilePin(): String? =
        streamingAppsTakingProfilePins.map { pinStreamingApp ->
            localAppStorage.streamingProfilePin(pinStreamingApp.site.pluginKey)
        }.distinct().singleOrNull()

    // TODO: Validate
    private fun editAllAppsProfileName() {
        val profileAppNames = streamingAppsPickingProfilesByName.joinToString { it.site.pluginKey }
        activity.showSingleInputDialog(
            title = "Profile name in every app",
            message = "When $profileAppNames asks who is watching after the guide opens it, this profile is picked for you. " +
                "Leave the name empty to pick the profile each app highlights, usually the last one used. Apps that " +
                "can't read profile names keep their own setting. Nothing is saved until you choose Save next to " +
                "the profile name.",
            input = activity.profileNameInput(
                "Profile name, exactly as the apps show it",
                allAppsUnsavedProfileName ?: sharedSavedProfileName().orEmpty(),
            ),
            confirmButtonLabel = "Done",
        ) { enteredProfileName ->
            allAppsUnsavedProfileName = enteredProfileName
            afterSettingChanged()
        }
    }

    // TODO: Validate
    private fun editAllAppsProfilePin() {
        val pinAppNames = streamingAppsTakingProfilePins.joinToString { it.site.pluginKey }
        activity.showSingleInputDialog(
            title = "Profile PIN in every app",
            message = "When $pinAppNames asks for the picked profile's PIN, this PIN is entered for you. Leave the PIN empty " +
                "to enter it yourself. Nothing is saved until you choose Save next to the profile PIN.",
            input = activity.profilePinInput(allAppsUnsavedProfilePin ?: sharedSavedProfilePin().orEmpty()),
            confirmButtonLabel = "Done",
        ) { enteredProfilePin ->
            allAppsUnsavedProfilePin = enteredProfilePin
            afterSettingChanged()
        }
    }
}

// TODO: Validate
private fun StreamingSite.playNextEpisodeWhenChoices(): List<PlayNextEpisodeWhen> =
    PlayNextEpisodeWhen.entries.filter { playNextEpisodeMoment ->
        apps.any { siteStreamingApp -> playNextEpisodeMoment.canStreamingAppDetectMoment(siteStreamingApp) }
    }

// TODO: Validate
private fun StreamingSite.canPlayNextEpisodeMomentBeChecked(playNextEpisodeMoment: PlayNextEpisodeWhen): Boolean {
    val playNextEpisodeWhenChoices = playNextEpisodeWhenChoices()
    return playNextEpisodeMoment in playNextEpisodeWhenChoices && playNextEpisodeMoment != playNextEpisodeWhenChoices.last()
}

// TODO: Validate
private fun StreamingSite.doesAnyAppHaveFeature(toggleSetting: StreamingSiteToggleSetting): Boolean =
    apps.any { siteStreamingApp -> toggleSetting.doesStreamingAppHaveFeature(siteStreamingApp) }
