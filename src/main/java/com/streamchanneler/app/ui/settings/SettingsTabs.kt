// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.fullWidthLayoutParams

// TODO: Validate
internal class SettingsTabs(private val context: Context) {
    // TODO: Validate
    private val settingsTabs = mutableListOf<SettingsTab>()

    // TODO: Validate
    fun addTab(tabName: String, tabIcon: Drawable?, panelColumn: LinearLayout) {
        val tabIndex = settingsTabs.size
        val tabIconSize = context.dp(24)
        val tabIconPadding = context.dp(10)
        val tabButton = context.settingButton { showTabAndFocusFirstRow(tabIndex) }.apply {
            id = View.generateViewId()
            text = tabName
            if (tabIcon != null) {
                tabIcon.setBounds(0, 0, tabIconSize, tabIconSize)
                setCompoundDrawablesRelative(tabIcon, null, null, null)
                compoundDrawablePadding = tabIconPadding
            }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showTab(tabIndex) }
        }
        val tabPanel = ScrollView(context).apply {
            isFocusable = false
            addView(panelColumn, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        settingsTabs.add(SettingsTab(tabButton, tabPanel, panelColumn))
    }

    // TODO: Validate
    fun tabsAndPanelsView(): View {
        linkTabFocus()
        val tabList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            settingsTabs.forEachIndexed { tabIndex, settingsTab ->
                addView(
                    settingsTab.tabButton,
                    if (tabIndex == 0) context.fullWidthLayoutParams() else context.rowSpacingLayoutParams(),
                )
            }
        }
        val tabListScroller = ScrollView(context).apply {
            isFocusable = false
            addView(tabList, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val tabPanelFrame = FrameLayout(context).apply {
            for (settingsTab in settingsTabs) addView(settingsTab.tabPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val tabListWidth = context.dp(240)
        val tabPanelMarginStart = context.dp(24)
        showTab(0)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tabListScroller, LinearLayout.LayoutParams(tabListWidth, MATCH_PARENT))
            addView(tabPanelFrame, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = tabPanelMarginStart })
        }
    }

    // TODO: Validate
    fun linkTabFocus() {
        for (settingsTab in settingsTabs) {
            val tabButton = settingsTab.tabButton
            val panelColumn = settingsTab.panelColumn
            val panelFocusableViews = focusableDescendants(panelColumn)
            for (panelFocusableView in panelFocusableViews) {
                if (panelFocusableView.id == View.NO_ID) panelFocusableView.id = View.generateViewId()
            }
            panelFocusableViews.firstOrNull()?.let { firstPanelFocusableView ->
                tabButton.nextFocusRightId = firstPanelFocusableView.id
            }
            val firstPanelRow = panelFocusableViews.firstOrNull()?.let { panelRowContainingView(it, panelColumn) }
            val lastPanelRow = panelFocusableViews.lastOrNull()?.let { panelRowContainingView(it, panelColumn) }
            for (panelFocusableView in panelFocusableViews) {
                val panelRow = panelRowContainingView(panelFocusableView, panelColumn)
                if (panelRow === panelFocusableView || (panelRow as? ViewGroup)?.indexOfChild(panelFocusableView) == 0) {
                    panelFocusableView.nextFocusLeftId = tabButton.id
                }
                panelFocusableView.nextFocusUpId = if (panelRow === firstPanelRow) panelFocusableView.id else View.NO_ID
                panelFocusableView.nextFocusDownId = if (panelRow === lastPanelRow) panelFocusableView.id else View.NO_ID
            }
        }
        settingsTabs.lastOrNull()?.tabButton?.let { lastTabButton -> lastTabButton.nextFocusDownId = lastTabButton.id }
    }

    // TODO: Validate
    private fun panelRowContainingView(panelView: View, panelColumn: LinearLayout): View {
        var panelRow = panelView
        while (panelRow.parent !== panelColumn) panelRow = panelRow.parent as View
        return panelRow
    }

    // TODO: Validate
    private fun focusableDescendants(viewGroup: ViewGroup): List<View> = (0 until viewGroup.childCount).flatMap { childIndex ->
        when (val childView = viewGroup.getChildAt(childIndex)) {
            is ViewGroup -> focusableDescendants(childView)
            else -> listOfNotNull(childView.takeIf { it.isFocusable })
        }
    }

    // TODO: Validate
    private fun showTab(shownTabIndex: Int) {
        settingsTabs.forEachIndexed { tabIndex, settingsTab ->
            settingsTab.tabButton.isActivated = tabIndex == shownTabIndex
            settingsTab.tabPanel.visibility = if (tabIndex == shownTabIndex) View.VISIBLE else View.GONE
        }
    }

    // TODO: Validate
    private fun showTabAndFocusFirstRow(shownTabIndex: Int) {
        showTab(shownTabIndex)
        focusableDescendants(settingsTabs[shownTabIndex].panelColumn)
            .firstOrNull { panelFocusableView -> panelFocusableView.visibility == View.VISIBLE }
            ?.requestFocus()
    }

    // TODO: Validate
    private class SettingsTab(val tabButton: Button, val tabPanel: ScrollView, val panelColumn: LinearLayout)
}
