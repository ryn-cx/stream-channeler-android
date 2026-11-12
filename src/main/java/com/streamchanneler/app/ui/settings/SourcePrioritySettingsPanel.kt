// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import com.streamchanneler.app.data.SourcePreference
import com.streamchanneler.app.data.toSourcePreferencesJson
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.actionButton
import com.streamchanneler.app.ui.common.dp

// TODO: Validate
internal class SourcePrioritySettingsPanel(private val activity: Activity) {
    // TODO: Validate
    private val app get() = activity.streamChannelerApp
    // TODO: Validate
    private val sourcePriorityPanelColumn = activity.settingsPanelColumn()
    // TODO: Validate
    private val sourcePriorityStatusNote = activity.settingsNote("")
    // TODO: Validate
    private val sourceRows = mutableListOf<SourceRow>()
    // TODO: Validate
    private lateinit var settingsTabs: SettingsTabs
    // TODO: Validate
    private var editedSourcePreferences: List<SourcePreference> = emptyList()
    // TODO: Validate
    private var savedSourcePreferences: List<SourcePreference> = emptyList()

    // TODO: Validate
    private val listedSourcePreferences: List<SourcePreference>
        get() = editedSourcePreferences.filter { sourcePreference ->
            sourcePreference.episodeCount > 0 || sourcePreference.sourceKey == "Custom Media"
        }

    // TODO: Validate
    fun addTab(settingsTabs: SettingsTabs) {
        this.settingsTabs = settingsTabs
        settingsTabs.addTab("Source priority", null, buildPanelColumn())
    }

    // TODO: Validate
    private fun buildPanelColumn(): LinearLayout {
        if (!app.localAppStorage.isSignedIn) {
            sourcePriorityPanelColumn.addView(activity.settingsNote("Sign in to choose which sources your channels show."))
            return sourcePriorityPanelColumn
        }
        sourcePriorityPanelColumn.addView(
            activity.settingsNote(
                "When the same episode is available from more than one source, the source highest in this list is " +
                    "shown and the duplicates are hidden. A source that is turned off has its episodes hidden from " +
                    "all of your channels. Only sources with at least one episode are listed. This applies on top " +
                    "of each channel's own source filtering.",
            ),
        )
        sourcePriorityPanelColumn.addView(
            activity.settingsNote(
                "Save keeps these settings on this device only, and this device always uses them. Save as default " +
                    "stores them on your account as well, so any device can load them with Load saved default.",
            ),
            activity.rowSpacingLayoutParams(),
        )
        val sourcePriorityButtonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(activity.actionButton("Save", ::saveOnThisDevice))
            addView(
                activity.actionButton("Save as default", ::saveAsDefault),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = activity.dp(8) },
            )
            addView(
                activity.actionButton("Load saved default", ::loadSavedDefault),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = activity.dp(8) },
            )
        }
        sourcePriorityPanelColumn.addView(sourcePriorityButtonsRow, activity.rowSpacingLayoutParams())
        sourcePriorityPanelColumn.addView(sourcePriorityStatusNote, activity.rowSpacingLayoutParams())
        sourcePriorityStatusNote.text = "Loading the sources..."
        app.accountSettings.loadSourcePreferences(
            loadsSavedDefault = false,
            onLoaded = { loadedSourcePreferences ->
                savedSourcePreferences = loadedSourcePreferences
                editedSourcePreferences = loadedSourcePreferences
                sourcePriorityStatusNote.text = ""
                showSourceRows()
            },
            onFailure = { failureMessage -> sourcePriorityStatusNote.text = failureMessage },
        )
        return sourcePriorityPanelColumn
    }

    // TODO: Validate
    private fun showSourceRows() {
        for (sourceRow in sourceRows) sourcePriorityPanelColumn.removeView(sourceRow.rowView)
        sourceRows.clear()
        listedSourcePreferences.indices.forEach { listedSourceIndex ->
            val sourceRow = SourceRow(
                enabledButton = activity.settingButton { toggleEnabled(listedSourceIndex) },
                moveToTopButton = activity.actionButton("Top") { moveToTop(listedSourceIndex) },
                moveUpButton = activity.actionButton("Up") { moveUp(listedSourceIndex) },
                moveDownButton = activity.actionButton("Down") { moveDown(listedSourceIndex) },
                moveToBottomButton = activity.actionButton("Bottom") { moveToBottom(listedSourceIndex) },
                rowView = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL },
            )
            with(sourceRow) {
                rowView.addView(enabledButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                for (moveButton in listOf(moveToTopButton, moveUpButton, moveDownButton, moveToBottomButton)) {
                    rowView.addView(
                        moveButton,
                        LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { marginStart = activity.dp(8) },
                    )
                }
            }
            sourcePriorityPanelColumn.addView(sourceRow.rowView, activity.rowSpacingLayoutParams())
            sourceRows.add(sourceRow)
        }
        writeSourceRows()
        settingsTabs.linkTabFocus()
    }

    // TODO: Validate
    private fun writeSourceRows() {
        val listedSourcePreferences = listedSourcePreferences
        listedSourcePreferences.forEachIndexed { listedSourceIndex, listedSourcePreference ->
            val sourceRow = sourceRows[listedSourceIndex]
            sourceRow.enabledButton.text = sourceLabel(listedSourcePreference.sourceKey)
            sourceRow.enabledButton.showSettingRowEnd(
                SettingRowEnd.Switch(onOrOffSettingSwitchState(listedSourcePreference.enabled)),
            )
            val canMoveUp = listedSourceIndex > 0
            val canMoveDown = listedSourceIndex < listedSourcePreferences.lastIndex
            sourceRow.moveToTopButton.alpha = if (canMoveUp) 1f else 0.4f
            sourceRow.moveUpButton.alpha = if (canMoveUp) 1f else 0.4f
            sourceRow.moveDownButton.alpha = if (canMoveDown) 1f else 0.4f
            sourceRow.moveToBottomButton.alpha = if (canMoveDown) 1f else 0.4f
        }
    }

    // TODO: Validate
    private fun writeEditedSourceRows() {
        writeSourceRows()
        val hasUnsavedChange = editedSourcePreferences.map { it.sourceKey to it.enabled } !=
            savedSourcePreferences.map { it.sourceKey to it.enabled }
        sourcePriorityStatusNote.text = if (hasUnsavedChange) "Not saved yet." else ""
    }

    // TODO: Validate
    private fun sourceLabel(sourceKey: String): String = when (sourceKey) {
        "Other" -> "Other (custom media)"
        "TMDB" -> "TMDB (This will enable showing episodes even if there is no way to play them back)"
        else -> sourceKey
    }

    // TODO: Validate
    private fun toggleEnabled(listedSourceIndex: Int) {
        val toggledSourceKey = listedSourcePreferences[listedSourceIndex].sourceKey
        editedSourcePreferences = editedSourcePreferences.map { sourcePreference ->
            if (sourcePreference.sourceKey == toggledSourceKey) {
                sourcePreference.copy(enabled = !sourcePreference.enabled)
            } else {
                sourcePreference
            }
        }
        writeEditedSourceRows()
    }

    // TODO: Validate
    private fun moveToTop(listedSourceIndex: Int) {
        if (listedSourceIndex == 0) return
        moveSource(listedSourceIndex, targetIndex = 0)
        sourceRows.first().moveToTopButton.requestFocus()
    }

    // TODO: Validate
    private fun moveUp(listedSourceIndex: Int) {
        if (listedSourceIndex == 0) return
        val sourceKeyAbove = listedSourcePreferences[listedSourceIndex - 1].sourceKey
        moveSource(listedSourceIndex, editedSourcePreferences.indexOfFirst { it.sourceKey == sourceKeyAbove })
        sourceRows[listedSourceIndex - 1].moveUpButton.requestFocus()
    }

    // TODO: Validate
    private fun moveDown(listedSourceIndex: Int) {
        if (listedSourceIndex == sourceRows.lastIndex) return
        val sourceKeyBelow = listedSourcePreferences[listedSourceIndex + 1].sourceKey
        moveSource(listedSourceIndex, editedSourcePreferences.indexOfFirst { it.sourceKey == sourceKeyBelow })
        sourceRows[listedSourceIndex + 1].moveDownButton.requestFocus()
    }

    // TODO: Validate
    private fun moveToBottom(listedSourceIndex: Int) {
        if (listedSourceIndex == sourceRows.lastIndex) return
        moveSource(listedSourceIndex, targetIndex = editedSourcePreferences.lastIndex)
        sourceRows.last().moveToBottomButton.requestFocus()
    }

    // TODO: Validate
    private fun moveSource(listedSourceIndex: Int, targetIndex: Int) {
        val movedSourcePreference = listedSourcePreferences[listedSourceIndex]
        val reorderedSourcePreferences = editedSourcePreferences.toMutableList()
        reorderedSourcePreferences.remove(movedSourcePreference)
        reorderedSourcePreferences.add(targetIndex, movedSourcePreference)
        editedSourcePreferences = reorderedSourcePreferences
        writeEditedSourceRows()
    }

    // TODO: Validate
    private fun saveOnThisDevice() {
        app.localAppStorage.localSourcePreferencesJson = editedSourcePreferences.toSourcePreferencesJson().toString()
        savedSourcePreferences = editedSourcePreferences
        sourcePriorityStatusNote.text = "Saved on this device. The guide is refreshing."
        app.guideRepository.refresh()
    }

    // TODO: Validate
    private fun saveAsDefault() {
        val sourcePreferencesBeingSaved = editedSourcePreferences
        sourcePriorityStatusNote.text = "Saving..."
        app.accountSettings.saveSourcePreferencesAsDefault(
            sourcePreferencesBeingSaved,
            onSaved = {
                savedSourcePreferences = sourcePreferencesBeingSaved
                sourcePriorityStatusNote.text = "Saved on this device and as your default. The guide is refreshing."
                app.guideRepository.refresh()
            },
            onFailure = { failureMessage -> sourcePriorityStatusNote.text = failureMessage },
        )
    }

    // TODO: Validate
    private fun loadSavedDefault() {
        sourcePriorityStatusNote.text = "Loading your saved default..."
        app.accountSettings.loadSourcePreferences(
            loadsSavedDefault = true,
            onLoaded = { savedDefaultSourcePreferences ->
                editedSourcePreferences = savedDefaultSourcePreferences
                showSourceRows()
                sourcePriorityStatusNote.text = "Loaded your saved default. Choose Save to use it on this device."
            },
            onFailure = { failureMessage -> sourcePriorityStatusNote.text = failureMessage },
        )
    }

    // TODO: Validate
    private class SourceRow(
        val enabledButton: Button,
        val moveToTopButton: Button,
        val moveUpButton: Button,
        val moveDownButton: Button,
        val moveToBottomButton: Button,
        val rowView: LinearLayout,
    )
}
