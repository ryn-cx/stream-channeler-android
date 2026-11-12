// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.actionButton
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.fullWidthLayoutParams
import com.streamchanneler.app.ui.common.unfocusableTextLabel

// TODO: Validate
internal sealed interface SettingRowEnd {
    // TODO: Validate
    data class Switch(val switchState: SettingSwitchState) : SettingRowEnd

    // TODO: Validate
    data class Value(val valueText: String) : SettingRowEnd

    // TODO: Validate
    data object Empty : SettingRowEnd
}

// TODO: Validate
internal fun Context.settingButton(onClick: () -> Unit): Button = actionButton("", onClick).apply {
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
    setPadding(dp(16), dp(12), dp(16), dp(12))
}

// TODO: Validate
internal fun Context.sectionHeading(headingText: String): TextView =
    unfocusableTextLabel(headingText, 13f, UiColors.ACCENT, isBold = true).apply {
        isAllCaps = true
        layoutParams = fullWidthLayoutParams(24)
    }

// TODO: Validate
internal fun Context.settingsNote(noteText: CharSequence): TextView =
    unfocusableTextLabel("", 13f, UiColors.TEXT_MUTED).apply { text = noteText }

// TODO: Validate
internal fun Context.rowSpacingLayoutParams(): LinearLayout.LayoutParams = fullWidthLayoutParams(8)

// TODO: Validate
internal fun Context.settingsPanelColumn(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, 0, 0, dp(24))
}

// TODO: Validate
internal fun Context.dialogInputsColumn(dialogInputs: List<EditText>): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(24), dp(8), dp(24), 0)
    dialogInputs.forEachIndexed { dialogInputIndex, dialogInput ->
        addView(dialogInput, if (dialogInputIndex == 0) fullWidthLayoutParams() else rowSpacingLayoutParams())
    }
}

// TODO: Validate
internal fun TextView.showSettingRowEnd(settingRowEnd: SettingRowEnd) {
    val settingRowEndDrawable = when (settingRowEnd) {
        is SettingRowEnd.Switch -> SettingSwitchDrawable(context, settingRowEnd.switchState)
        is SettingRowEnd.Value -> SettingValueDrawable(context, settingRowEnd.valueText)
        SettingRowEnd.Empty -> null
    }
    settingRowEndDrawable?.setBounds(0, 0, settingRowEndDrawable.intrinsicWidth, settingRowEndDrawable.intrinsicHeight)
    setCompoundDrawablesRelative(null, null, settingRowEndDrawable, null)
    compoundDrawablePadding = context.dp(16)
}

// TODO: Validate
internal class SettingRows(private val context: Context, private val afterSettingChanged: () -> Unit) {
    // TODO: Validate
    private val settingRowWriters = mutableListOf<() -> Unit>()

    // TODO: Validate
    fun addRow(
        panelColumn: LinearLayout,
        settingLabel: () -> CharSequence,
        settingRowEnd: () -> SettingRowEnd,
        onChosen: () -> Unit,
    ): Button {
        val settingButton = context.settingButton {
            onChosen()
            afterSettingChanged()
        }
        panelColumn.addView(settingButton, context.rowSpacingLayoutParams())
        settingRowWriters.add {
            settingButton.text = settingLabel()
            settingButton.showSettingRowEnd(settingRowEnd())
        }
        return settingButton
    }

    // TODO: Validate
    fun addRowWithSaveButton(
        panelColumn: LinearLayout,
        streamingAppIcons: List<Drawable>,
        settingLabel: String,
        settingRowEnd: () -> SettingRowEnd,
        hasUnsavedChange: () -> Boolean,
        onChosen: () -> Unit,
        onSaved: () -> Unit,
    ) {
        val settingButton = context.settingButton {
            onChosen()
            afterSettingChanged()
        }
        val saveButton = context.actionButton("Save") {
            onSaved()
            settingButton.requestFocus()
            afterSettingChanged()
        }
        val settingAndSaveRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(settingButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(saveButton, LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { marginStart = context.dp(8) })
        }
        val streamingAppIconsText = streamingAppIconsText(streamingAppIcons)
        panelColumn.addView(settingAndSaveRow, context.rowSpacingLayoutParams())
        settingRowWriters.add {
            val isChangeUnsaved = hasUnsavedChange()
            settingButton.text = SpannableStringBuilder(settingLabel + if (isChangeUnsaved) " (not saved)" else "")
                .append(streamingAppIconsText)
            settingButton.showSettingRowEnd(settingRowEnd())
            saveButton.isEnabled = isChangeUnsaved
            saveButton.isFocusable = isChangeUnsaved
            saveButton.alpha = if (isChangeUnsaved) 1f else 0.4f
        }
    }

    // TODO: Validate
    fun writeRows() {
        for (writeSettingRow in settingRowWriters) writeSettingRow()
    }

    // TODO: Validate
    private fun streamingAppIconsText(streamingAppIcons: List<Drawable>): CharSequence {
        val streamingAppIconsText = SpannableStringBuilder()
        val streamingAppIconSize = context.dp(20)
        for (streamingAppIcon in streamingAppIcons) {
            streamingAppIcon.setBounds(0, 0, streamingAppIconSize, streamingAppIconSize)
            streamingAppIconsText.append("  ")
            streamingAppIconsText.setSpan(
                ImageSpan(streamingAppIcon, DynamicDrawableSpan.ALIGN_BOTTOM),
                streamingAppIconsText.length - 1,
                streamingAppIconsText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return streamingAppIconsText
    }
}
