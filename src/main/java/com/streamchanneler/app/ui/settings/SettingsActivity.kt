// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.streamchanneler.app.BuildConfig
import com.streamchanneler.app.data.AfterVideoEnds
import com.streamchanneler.app.data.DEFAULT_GUIDE_DETAILS_HEIGHT
import com.streamchanneler.app.data.DEFAULT_ZOOM
import com.streamchanneler.app.playback.ForegroundReturner
import com.streamchanneler.app.playback.PlaybackWatcherService
import com.streamchanneler.app.playback.UpNextAnnouncer
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.RemoteButtonsAccess
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.streamingapps.StreamingApps
import com.streamchanneler.app.ui.common.AppPermission
import com.streamchanneler.app.ui.common.PermissionState
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.currentPermissionStates
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.textInput
import com.streamchanneler.app.ui.common.unfocusableTextLabel
import com.streamchanneler.app.ui.common.zoomedContext
import com.streamchanneler.app.data.DEFAULT_MINIMUM_VIDEO_LENGTH
import com.streamchanneler.app.ui.login.LoginActivity
import kotlin.reflect.KMutableProperty0

// TODO: Validate
class SettingsActivity : Activity() {
    // TODO: Validate
    private val app get() = streamChannelerApp
    // TODO: Validate
    private val settingRows by lazy { SettingRows(this, ::renderSettings) }
    // TODO: Validate
    private lateinit var firstGeneralSettingButton: Button
    // TODO: Validate
    private lateinit var remoteButtonsButton: Button
    // TODO: Validate
    private lateinit var permissionHelpText: TextView
    // TODO: Validate
    private var permissionStates: Map<AppPermission, PermissionState> = emptyMap()

    // TODO: Validate
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.zoomedContext())
    }

    // TODO: Validate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildSettingsLayout())
        firstGeneralSettingButton.requestFocus()
    }

    // TODO: Validate
    override fun onResume() {
        super.onResume()
        renderSettings()
        ForegroundReturner.testOverlayWindow(this) { if (!isDestroyed) renderSettings() }
    }

    // TODO: Validate
    private fun buildSettingsLayout(): View {
        val settingsTabs = SettingsTabs(this)
        settingsTabs.addTab("General", null, generalPanelColumn())
        SourcePrioritySettingsPanel(this).addTab(settingsTabs)
        StreamingSiteSettingsPanels(this, settingRows, StreamingProfileSettings(this, ::renderSettings), ::renderSettings)
            .addTabs(settingsTabs)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiColors.BACKGROUND)
            setPadding(dp(32), dp(24), dp(32), 0)
            addView(unfocusableTextLabel("Settings", 24f, UiColors.TEXT, isBold = true))
            addView(settingsTabs.tabsAndPanelsView(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(16) })
        }
    }

    // TODO: Validate
    private fun generalPanelColumn(): LinearLayout = settingsPanelColumn().also { generalPanelColumn ->
        addPlaybackRows(generalPanelColumn)
        addGuideRows(generalPanelColumn)
        addPermissionRows(generalPanelColumn)
        addAccountAndDebuggingRows(generalPanelColumn)
    }

    // TODO: Validate
    private fun addPlaybackRows(generalPanelColumn: LinearLayout) {
        val localAppStorage = app.localAppStorage
        generalPanelColumn.addView(sectionHeading("Playback"))
        firstGeneralSettingButton = settingRows.addRow(
            generalPanelColumn,
            settingLabel = { "Play the next video automatically (when off, the guide opens when a video ends)" },
            settingRowEnd = {
                SettingRowEnd.Switch(onOrOffSettingSwitchState(localAppStorage.afterVideoEnds == AfterVideoEnds.PLAY_NEXT_VIDEO))
            },
            onChosen = ::toggleAfterVideoEnds,
        )
        addSwitchRow(
            generalPanelColumn,
            "Up next information",
            localAppStorage::showsUpNextInformation,
            afterTurningOff = { UpNextAnnouncer.shutdown() },
        )
        addNumberRow(
            generalPanelColumn,
            "Countdown before the next episode, in seconds (0 turns it off, and Back cancels it)",
            NumberSetting(
                "Countdown before the next episode",
                "Enter a number of seconds from 0 to 60. 0 turns the countdown off, and Back cancels it.",
                localAppStorage::nextEpisodeCountdown,
                valueRange = 0..60,
                defaultValue = 0,
            ),
        )
        addSwitchRow(generalPanelColumn, "Mark started videos as watching", localAppStorage::marksStartedVideosWatched)
        addSwitchRow(generalPanelColumn, "Mark finished videos as completed", localAppStorage::marksFinishedVideosCompleted)
    }

    // TODO: Validate
    private fun addGuideRows(generalPanelColumn: LinearLayout) {
        val localAppStorage = app.localAppStorage
        generalPanelColumn.addView(sectionHeading("Guide"))
        addSwitchRow(generalPanelColumn, "Condensed guide (titles only, more channels)", localAppStorage::showsCondensedGuide)
        addSwitchRow(generalPanelColumn, "Website logos on videos", localAppStorage::showsGuideSourceLogos)
        addSwitchRow(
            generalPanelColumn,
            "Size videos in the guide to fit their text instead of their length",
            localAppStorage::sizesGuideVideosByText,
        )
        addNumberRow(
            generalPanelColumn,
            "Stretch videos shorter than this many minutes so their titles fit in the guide " +
                "(0 turns it off, default $DEFAULT_MINIMUM_VIDEO_LENGTH)",
            NumberSetting(
                "Shortest video length shown",
                "Videos shorter than this are drawn this long in the guide so their titles fit. Enter a number " +
                    "of minutes from 0 to $MAXIMUM_MINIMUM_VIDEO_LENGTH. 0 turns it off.",
                localAppStorage::minimumVideoLength,
                valueRange = 0..MAXIMUM_MINIMUM_VIDEO_LENGTH,
                defaultValue = DEFAULT_MINIMUM_VIDEO_LENGTH,
            ),
        )
        addNumberRow(
            generalPanelColumn,
            "Height of the details above the guide, in percent of the screen (default $DEFAULT_GUIDE_DETAILS_HEIGHT)",
            NumberSetting(
                "Height of the details above the guide",
                "Enter a percent of the screen's height from 5 to 60.",
                localAppStorage::guideDetailsHeight,
                valueRange = 5..60,
                defaultValue = DEFAULT_GUIDE_DETAILS_HEIGHT,
            ),
        )
        addNumberRow(
            generalPanelColumn,
            "Zoom, in percent (default $DEFAULT_ZOOM)",
            NumberSetting(
                "Zoom",
                "Enter a percent from 25 to 500. Every screen of the app grows or shrinks by it.",
                localAppStorage::zoom,
                valueRange = 25..500,
                defaultValue = DEFAULT_ZOOM,
            ),
            afterSaving = { recreate() },
        )
    }

    // TODO: Validate
    private fun addPermissionRows(generalPanelColumn: LinearLayout) {
        generalPanelColumn.addView(sectionHeading("Permissions"))
        addPermissionRow(
            generalPanelColumn,
            "Detect when videos end (notification access)",
            AppPermission.END_DETECTION,
            ::openNotificationAccessSettings,
        )
        addPermissionRow(
            generalPanelColumn,
            "Come back to the guide (display over other apps)",
            AppPermission.RETURN_TO_GUIDE,
            ::openOverlaySettings,
        )
        addPermissionRow(
            generalPanelColumn,
            "Pick streaming profiles and click skip buttons (accessibility)",
            AppPermission.PROFILE_PICKER,
            ::openAccessibilitySettings,
        )
        val remoteButtonAppNames = StreamingApps.all
            .filter { it.profileScreen is P2RemoteButtonProfileScreen }
            .map { it.site.pluginKey }
            .sorted()
            .joinToString()
        remoteButtonsButton = settingRows.addRow(
            generalPanelColumn,
            settingLabel = { "Press remote buttons for $remoteButtonAppNames (Shizuku)" },
            settingRowEnd = {
                SettingRowEnd.Value(
                    when (RemoteButtons.shizukuAccess()) {
                        RemoteButtonsAccess.SHIZUKU_NOT_RUNNING -> "Shizuku isn't running"
                        RemoteButtonsAccess.NOT_ALLOWED -> "Not allowed, select to allow"
                        RemoteButtonsAccess.ALLOWED -> "Allowed"
                    },
                )
            },
            onChosen = ::requestRemoteButtonsAccess,
        )
        permissionHelpText = settingsNote("").apply { visibility = View.GONE }
        generalPanelColumn.addView(permissionHelpText, rowSpacingLayoutParams())
    }

    // TODO: Validate
    private fun addAccountAndDebuggingRows(generalPanelColumn: LinearLayout) {
        val localAppStorage = app.localAppStorage
        generalPanelColumn.addView(sectionHeading("Account"))
        settingRows.addRow(
            generalPanelColumn,
            settingLabel = {
                if (localAppStorage.isSignedIn) {
                    "Signed in as ${localAppStorage.signedInDisplayName ?: "your account"}. Select to sign out."
                } else {
                    "Not signed in. Select to sign in."
                }
            },
            settingRowEnd = { SettingRowEnd.Empty },
            onChosen = { LoginActivity.signOutAndShowLogin(this) },
        )
        generalPanelColumn.addView(sectionHeading("Debugging"))
        settingRows.addRow(
            generalPanelColumn,
            settingLabel = { "Show debug information" },
            settingRowEnd = { SettingRowEnd.Empty },
            onChosen = { showDebugInformationDialog() },
        )
        generalPanelColumn.addView(
            unfocusableTextLabel("Version ${BuildConfig.VERSION_NAME}", 12f, UiColors.TEXT_MUTED),
            rowSpacingLayoutParams(),
        )
    }

    // TODO: Validate
    private fun addSwitchRow(
        generalPanelColumn: LinearLayout,
        settingLabel: String,
        isOnProperty: KMutableProperty0<Boolean>,
        afterTurningOff: () -> Unit = {},
    ) {
        settingRows.addRow(
            generalPanelColumn,
            settingLabel = { settingLabel },
            settingRowEnd = { SettingRowEnd.Switch(onOrOffSettingSwitchState(isOnProperty.get())) },
            onChosen = {
                isOnProperty.set(!isOnProperty.get())
                if (!isOnProperty.get()) afterTurningOff()
            },
        )
    }

    // TODO: Validate
    private fun addNumberRow(
        generalPanelColumn: LinearLayout,
        settingLabel: String,
        numberSetting: NumberSetting,
        afterSaving: () -> Unit = {},
    ) {
        settingRows.addRow(
            generalPanelColumn,
            settingLabel = { settingLabel },
            settingRowEnd = { SettingRowEnd.Value(numberSetting.savedValueProperty.get().toString()) },
            onChosen = { editNumberSetting(numberSetting, afterSaving) },
        )
    }

    // TODO: Validate
    private fun addPermissionRow(
        generalPanelColumn: LinearLayout,
        settingLabel: String,
        appPermission: AppPermission,
        openPermissionSettings: () -> Unit,
    ) {
        settingRows.addRow(
            generalPanelColumn,
            settingLabel = { settingLabel },
            settingRowEnd = { SettingRowEnd.Value(permissionStateLabel(permissionStates.getValue(appPermission))) },
            onChosen = openPermissionSettings,
        )
    }

    // TODO: Validate
    private fun renderSettings() {
        permissionStates = currentPermissionStates(this, ForegroundReturner.lastOverlayTestResult)
        settingRows.writeRows()
        remoteButtonsButton.visibility = if (RemoteButtons.pressesDpadWithoutShizuku) View.GONE else View.VISIBLE
        val adbCommands = permissionAdbCommands()
        permissionHelpText.text =
            "If this device has no settings screen for these, run from a computer:\n" + adbCommands.joinToString("\n")
        permissionHelpText.visibility = if (adbCommands.isEmpty()) View.GONE else View.VISIBLE
    }

    // TODO: Validate
    private fun permissionAdbCommands(): List<String> {
        val watcherComponent = ComponentName(this, PlaybackWatcherService::class.java).flattenToString()
        val overlayAppOpCommand = "adb shell appops set $packageName SYSTEM_ALERT_WINDOW"
        val enableAccessibilityCommand = ScreenAutomationService.enableCommand(this)
        return buildList {
            if (permissionStates[AppPermission.END_DETECTION] == PermissionState.TURNED_OFF) {
                add("adb shell cmd notification allow_listener $watcherComponent")
            }
            when (permissionStates.getValue(AppPermission.RETURN_TO_GUIDE)) {
                PermissionState.TURNED_OFF -> add("$overlayAppOpCommand allow")
                PermissionState.ALLOWED_BUT_NOT_WORKING -> {
                    add("$overlayAppOpCommand deny")
                    add("$overlayAppOpCommand allow")
                }
                PermissionState.WORKING -> Unit
            }
            if (permissionStates[AppPermission.PROFILE_PICKER] == PermissionState.TURNED_OFF) add(enableAccessibilityCommand)
            if (!RemoteButtons.pressesDpadWithoutShizuku && RemoteButtons.shizukuAccess() == RemoteButtonsAccess.SHIZUKU_NOT_RUNNING) {
                add("Install Shizuku, then after every restart of this device run: ${RemoteButtons.START_SHIZUKU_COMMAND}")
            }
        }
    }

    // TODO: Validate
    private fun permissionStateLabel(permissionState: PermissionState): String = when (permissionState) {
        PermissionState.WORKING -> "Allowed"
        PermissionState.TURNED_OFF -> "Not allowed, select to allow"
        PermissionState.ALLOWED_BUT_NOT_WORKING -> "Allowed but not working, select to turn it off and on again"
    }

    // TODO: Validate
    private fun toggleAfterVideoEnds() {
        val localAppStorage = app.localAppStorage
        localAppStorage.afterVideoEnds = when (localAppStorage.afterVideoEnds) {
            AfterVideoEnds.PLAY_NEXT_VIDEO -> AfterVideoEnds.RETURN_TO_GUIDE
            AfterVideoEnds.RETURN_TO_GUIDE -> AfterVideoEnds.PLAY_NEXT_VIDEO
        }
    }

    // TODO: Validate
    private fun editNumberSetting(numberSetting: NumberSetting, afterSaving: () -> Unit) {
        val numberSettingInput = textInput(numberSetting.defaultValue.toString(), InputType.TYPE_CLASS_NUMBER).apply {
            setText(numberSetting.savedValueProperty.get().toString())
        }
        // TODO: Validate
        fun saveNumberSetting(enteredValue: Int?) {
            numberSetting.savedValueProperty.set(
                enteredValue?.coerceIn(numberSetting.valueRange) ?: numberSetting.defaultValue,
            )
            renderSettings()
            afterSaving()
        }
        AlertDialog.Builder(this)
            .setTitle(numberSetting.title)
            .setMessage("${numberSetting.explanation} The default is ${numberSetting.defaultValue}.")
            .setView(dialogInputsColumn(listOf(numberSettingInput)))
            .setPositiveButton("Save") { _, _ -> saveNumberSetting(numberSettingInput.text.toString().trim().toIntOrNull()) }
            .setNeutralButton("Use default") { _, _ -> saveNumberSetting(null) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // TODO: Validate
    private fun openNotificationAccessSettings() {
        val watcherComponent = ComponentName(this, PlaybackWatcherService::class.java)
        val candidateIntents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, watcherComponent.flattenToString()),
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        openFirstAvailableSettings(candidateIntents)
    }

    // TODO: Validate
    private fun openOverlaySettings() {
        openFirstAvailableSettings(
            listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            ),
        )
    }

    // TODO: Validate
    private fun openAccessibilitySettings() {
        openFirstAvailableSettings(listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
    }

    // TODO: Validate
    private fun requestRemoteButtonsAccess() {
        if (RemoteButtons.shizukuAccess() == RemoteButtonsAccess.SHIZUKU_NOT_RUNNING) return
        RemoteButtons.requestShizukuAccess { runOnUiThread { if (!isFinishing) renderSettings() } }
    }

    // TODO: Validate
    private fun openFirstAvailableSettings(candidateIntents: List<Intent>) {
        for (candidateIntent in candidateIntents) {
            try {
                startActivity(candidateIntent)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val MAXIMUM_MINIMUM_VIDEO_LENGTH = 120
    }
}

// TODO: Validate
private data class NumberSetting(
    val title: String,
    val explanation: String,
    val savedValueProperty: KMutableProperty0<Int>,
    val valueRange: IntRange,
    val defaultValue: Int,
)
