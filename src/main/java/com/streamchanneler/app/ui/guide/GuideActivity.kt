// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.streamchanneler.app.data.ChannelEpisodesAction
import com.streamchanneler.app.data.ChannelRefreshSettings
import com.streamchanneler.app.data.GuideChannel
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.GuideRepository
import com.streamchanneler.app.data.GuideScope
import com.streamchanneler.app.data.PagedChannelList
import com.streamchanneler.app.data.nextPlayableVideo
import com.streamchanneler.app.playback.ActivePlayback
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.playback.PlaybackOutcome
import com.streamchanneler.app.playback.VideoEndResult
import com.streamchanneler.app.playback.VideoLaunchResult
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.actionButton
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import com.streamchanneler.app.ui.common.fullWidthLayoutParams
import com.streamchanneler.app.ui.common.overlayMessageView
import com.streamchanneler.app.ui.common.textInput
import com.streamchanneler.app.ui.common.zoomedContext
import com.streamchanneler.app.ui.login.LoginActivity
import com.streamchanneler.app.ui.settings.SettingsActivity
import com.streamchanneler.app.ui.upnext.UpNextActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// TODO: Validate
private class ActionMenuItem(val label: String, val onChosen: () -> Unit)

// TODO: Validate
private class AnnouncedUpNextVideo(val channel: GuideChannel, val video: GuideVideo)

// TODO: Validate
private data class GuideScheduleSettings(
    val minimumVideoLength: Int,
    val sizesGuideVideosByText: Boolean,
    val showsCondensedGuide: Boolean,
    val showsGuideSourceLogos: Boolean,
)

// TODO: Validate
private class CachedChannelSchedule(
    val channelVideos: List<GuideVideo>,
    val guideScheduleSettings: GuideScheduleSettings,
    val scheduledVideos: List<ScheduledVideo>,
)

// TODO: Validate
private fun TextView.setTextIfChanged(newText: String) {
    // Setting a text view's text lays the screen out again, even when the text is the same.
    if (text.toString() != newText) text = newText
}

// TODO: Validate
private enum class GuideShortcut {
    OPEN_MENU,
    OPEN_SETTINGS,
    FOCUS_GUIDE,
    FOCUS_SEARCH,
    REFRESH_GUIDE,
}

// TODO: Validate
class GuideActivity : ComponentActivity(), GuideRepository.Listener, GuideGridView.Listener {
    // TODO: Validate
    private val app get() = streamChannelerApp
    // TODO: Validate
    private val upNextLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { upNextResult ->
        playOrDeclineUpNextVideo(upNextResult.resultCode)
    }
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val guideActivityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // TODO: Validate
    private lateinit var guideGridView: GuideGridView
    // TODO: Validate
    private lateinit var detailPanel: GuideDetailPanel
    // TODO: Validate
    private lateinit var permissionWarnings: GuidePermissionWarnings
    // TODO: Validate
    private lateinit var statusMessageText: TextView
    // TODO: Validate
    private lateinit var guideLoadProblemText: TextView
    // TODO: Validate
    private lateinit var refreshButton: Button
    // TODO: Validate
    private lateinit var searchInput: EditText
    // TODO: Validate
    private lateinit var guideScopeToButton: Map<GuideScope, Button>
    // TODO: Validate
    private lateinit var showFilterButton: Button
    // TODO: Validate
    private var shownChannelIds: List<String> = emptyList()

    // TODO: Validate
    private var hasAppliedInitialFocus = false
    // TODO: Validate
    private var announcedUpNextVideo: AnnouncedUpNextVideo? = null
    // TODO: Validate
    private var hasShownSignInExpiredDialog = false
    // TODO: Validate
    private val clearStatusMessageRunnable = Runnable { statusMessageText.visibility = View.GONE }
    // TODO: Validate
    private val searchChannelsRunnable = Runnable { searchChannels() }
    // TODO: Validate
    private var shownWindowStart = 0L
    // TODO: Validate
    private val guideClockTickRunnable = Runnable { tickGuideClock() }
    // TODO: Validate
    private var appliedZoom = 0
    // TODO: Validate
    private lateinit var lastFocusedToolbarView: View
    // TODO: Validate
    private var appliedGuideDetailsHeight = 0
    // TODO: Validate
    private val channelIdToCachedSchedule = HashMap<String, CachedChannelSchedule>()
    // TODO: Validate
    private var hasPostedGuideRowsRebuild = false
    // TODO: Validate
    private val rebuildGuideRowsRunnable = Runnable {
        hasPostedGuideRowsRebuild = false
        rebuildGuideRows()
    }

    // TODO: Validate
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.zoomedContext())
    }

    // TODO: Validate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.localAppStorage.hasFinishedLogin) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(buildContentView())
        appliedZoom = app.localAppStorage.zoom
        appliedGuideDetailsHeight = app.localAppStorage.guideDetailsHeight
        onBackPressedDispatcher.addCallback(this, leaveGridOnBackCallback)
        app.guideRepository.startLoadingIfNeeded()
        if (savedInstanceState == null) {
            endPlaybackFromOutcomeExtra(intent)
        }
    }

    // TODO: Validate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        endPlaybackFromOutcomeExtra(intent)
    }

    // TODO: Validate
    override fun onRestart() {
        super.onRestart()
        val localAppStorage = app.localAppStorage
        if (localAppStorage.zoom != appliedZoom || localAppStorage.guideDetailsHeight != appliedGuideDetailsHeight) recreate()
    }

    // TODO: Validate
    override fun onStart() {
        super.onStart()
        app.guideRepository.addListener(this)
        rebuildGuideRows()
        mainHandler.postDelayed(guideClockTickRunnable, GUIDE_CLOCK_TICK_INTERVAL)
    }

    // TODO: Validate
    override fun onResume() {
        super.onResume()
        app.videoPlayer.stopFollowingAfterUserReturned()
        permissionWarnings.renderPermissionWarnings()
        permissionWarnings.testPermissionsAndShowBrokenWarnings()
    }

    // TODO: Validate
    override fun onStop() {
        app.guideRepository.removeListener(this)
        mainHandler.removeCallbacks(guideClockTickRunnable)
        mainHandler.removeCallbacks(rebuildGuideRowsRunnable)
        hasPostedGuideRowsRebuild = false
        super.onStop()
    }

    // TODO: Validate
    private fun tickGuideClock() {
        if (startOfCurrentHalfHourSlot(System.currentTimeMillis()) != shownWindowStart) {
            rebuildGuideRows()
        } else {
            guideGridView.invalidate()
        }
        mainHandler.postDelayed(guideClockTickRunnable, GUIDE_CLOCK_TICK_INTERVAL)
    }

    // TODO: Validate
    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        guideActivityScope.cancel()
        super.onDestroy()
    }

    // TODO: Validate
    override fun onGuideChanged() {
        // A guide refresh reports every loaded channel separately. Posting the rebuild lets the reports that arrive
        // together share one rebuild.
        if (hasPostedGuideRowsRebuild) return
        hasPostedGuideRowsRebuild = true
        mainHandler.post(rebuildGuideRowsRunnable)
    }

    // TODO: Validate
    private fun buildContentView(): View {
        val overlayMaximumWidth = resources.displayMetrics.widthPixels * 2 / 5
        statusMessageText = overlayMessageView("").apply {
            visibility = View.GONE
            maxWidth = overlayMaximumWidth
            elevation = dpFloat(16f)
        }
        val permissionWarningText = warningOverlayText(overlayMaximumWidth)
        guideLoadProblemText = warningOverlayText(overlayMaximumWidth)
        permissionWarnings = GuidePermissionWarnings(this, mainHandler, permissionWarningText, ::openSettings)
        val warningColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dpFloat(16f)
            addView(permissionWarningText, fullWidthLayoutParams())
            addView(guideLoadProblemText, fullWidthLayoutParams(8))
        }
        return FrameLayout(this).apply {
            addView(buildGuideLayout(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(statusMessageText, overlayLayoutParams(Gravity.BOTTOM or Gravity.END))
            addView(warningColumn, overlayLayoutParams(Gravity.BOTTOM or Gravity.START))
        }
    }

    // TODO: Validate
    private fun warningOverlayText(overlayMaximumWidth: Int): TextView = overlayMessageView("").apply {
        setTextColor(UiColors.STAR)
        maxWidth = overlayMaximumWidth
        visibility = View.GONE
    }

    // TODO: Validate
    private fun overlayLayoutParams(overlayGravity: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, overlayGravity).apply {
            setMargins(dp(24), dp(24), dp(24), dp(24))
        }

    // TODO: Validate
    private fun buildGuideLayout(): View {
        val detailPanelVerticalPadding = dp(28)
        val detailPanelHeight = resources.displayMetrics.heightPixels * app.localAppStorage.guideDetailsHeight / 100
        val detailThumbnailHeight = maxOf(dp(48), detailPanelHeight - detailPanelVerticalPadding)
        detailPanel = GuideDetailPanel(this, app.imageLoader, detailThumbnailHeight)
        guideGridView = GuideGridView(this, listener = this, imageLoader = app.imageLoader).apply {
            id = View.generateViewId()
        }
        val toolbar = buildToolbar()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiColors.BACKGROUND)
            addView(detailPanel.view, LinearLayout.LayoutParams(MATCH_PARENT, detailThumbnailHeight + detailPanelVerticalPadding))
            addView(View(this@GuideActivity).apply { setBackgroundColor(UiColors.BORDER) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
            addView(toolbar, fullWidthLayoutParams())
            addView(guideGridView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
    }

    // TODO: Validate
    private fun buildToolbar(): View {
        searchInput = textInput("Search channels").apply {
            doAfterTextChanged { editable ->
                mainHandler.removeCallbacks(searchChannelsRunnable)
                if (editable.toString().isBlank()) {
                    searchChannels()
                } else {
                    mainHandler.postDelayed(searchChannelsRunnable, SEARCH_DELAY)
                    rebuildGuideRows()
                }
            }
            setOnEditorActionListener { _, editorAction, _ ->
                if (editorAction != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                guideGridView.requestFocus()
                true
            }
        }
        guideScopeToButton = mapOf(
            GuideScope.PUBLIC to "Public Channels",
            GuideScope.FAVORITES to "★ Favorites",
            GuideScope.OWNED to "Owned",
        ).mapValues { (guideScope, buttonLabel) -> actionButton(buttonLabel) { selectGuideScope(guideScope) } }
        showFilterButton = actionButton("") { clearShowFilter() }
        refreshButton = actionButton("Refresh") { showRefreshMenu() }
        val settingsButton = actionButton("Settings") { openSettings() }
        val toolbarButtons = guideScopeToButton.values + listOf(showFilterButton, refreshButton, settingsButton)
        val toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(8))
            addView(searchInput, LinearLayout.LayoutParams(dp(260), WRAP_CONTENT).apply { marginEnd = dp(8) })
            for (toolbarButton in toolbarButtons) {
                addView(toolbarButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            }
        }
        lastFocusedToolbarView = refreshButton
        for (toolbarView in listOf(searchInput) + toolbarButtons) {
            toolbarView.nextFocusDownId = guideGridView.id
            toolbarView.onFocusChangeListener = View.OnFocusChangeListener { focusedToolbarView, hasFocus ->
                if (hasFocus) lastFocusedToolbarView = focusedToolbarView
            }
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(toolbarRow)
        }
    }

    // TODO: Validate
    private fun shownGuideScope(): GuideScope =
        if (app.localAppStorage.isSignedIn) app.localAppStorage.guideScope else GuideScope.PUBLIC

    // TODO: Validate
    private fun trimmedSearchQuery(): String = searchInput.text.toString().trim()

    // TODO: Validate
    private fun searchChannels() {
        app.guideRepository.searchChannels(trimmedSearchQuery(), shownGuideScope())
    }

    // TODO: Validate
    private fun shownChannelList(guideScope: GuideScope, searchQuery: String): PagedChannelList? {
        val channelSearchAndShowFilter = app.guideRepository.channelSearchAndShowFilter
        return when {
            channelSearchAndShowFilter.showFilterTitleId != null -> channelSearchAndShowFilter.showFilterChannelList
            searchQuery.isEmpty() -> app.guideRepository.guideChannelList(guideScope)
            searchQuery == channelSearchAndShowFilter.channelSearchQuery && guideScope == channelSearchAndShowFilter.channelSearchScope ->
                channelSearchAndShowFilter.searchChannelList
            else -> null
        }
    }

    // TODO: Validate
    private fun rebuildGuideRows() {
        val guideRepository = app.guideRepository
        val localAppStorage = app.localAppStorage
        val guideScope = shownGuideScope()
        val searchQuery = trimmedSearchQuery()
        val shownChannelList = shownChannelList(guideScope, searchQuery)
        val orderedChannels = shownChannelList?.let { guideRepository.loadedChannels(it) }.orEmpty()
            .filter { it.videos.isNotEmpty() }
        shownChannelIds = orderedChannels.map { it.id }
        val activePlayback = app.playbackMonitor.readActivePlayback()
        val currentChannel = orderedChannels.firstOrNull { it.id == activePlayback?.channelId }
        val guideScheduleSettings = GuideScheduleSettings(
            minimumVideoLength = localAppStorage.minimumVideoLength,
            sizesGuideVideosByText = localAppStorage.sizesGuideVideosByText,
            showsCondensedGuide = localAppStorage.showsCondensedGuide,
            showsGuideSourceLogos = localAppStorage.showsGuideSourceLogos,
        )
        guideGridView.isCondensed = guideScheduleSettings.showsCondensedGuide
        guideGridView.showsSourceLogos = guideScheduleSettings.showsGuideSourceLogos
        val guideRows = buildList {
            if (currentChannel != null) add(buildChannelGuideRow(currentChannel, isCurrentChannelRow = true, guideScheduleSettings))
            for (channel in orderedChannels) add(buildChannelGuideRow(channel, isCurrentChannelRow = false, guideScheduleSettings))
        }
        channelIdToCachedSchedule.keys.retainAll(shownChannelIds.toSet())
        shownWindowStart = startOfCurrentHalfHourSlot(System.currentTimeMillis())
        guideGridView.playingEpisodeId = activePlayback?.playingEpisodeId
        guideGridView.footerMessage = loadMoreChannelsFooterMessage(
            shownChannelList,
            hasGuideRows = orderedChannels.isNotEmpty(),
            isRefreshing = guideRepository.isRefreshing,
        )
        guideGridView.emptyMessage = emptyGuideMessage(guideRepository, guideScope, searchQuery)
        guideGridView.setGuideRows(guideRows, shownWindowStart)

        renderToolbar(guideScope)
        renderGuideLoadProblems(hasGuideRows = guideRows.isNotEmpty())
        showSignInExpiredDialogOnce()

        if (!hasAppliedInitialFocus && guideRows.isNotEmpty()) {
            hasAppliedInitialFocus = true
            if (activePlayback != null) guideGridView.focusEpisode(activePlayback.channelId, activePlayback.playingEpisodeId)
            guideGridView.requestFocus()
        }
    }

    // TODO: Validate
    private fun renderToolbar(guideScope: GuideScope) {
        val guideRepository = app.guideRepository
        refreshButton.setTextIfChanged(
            when {
                guideRepository.isRefreshing -> "Refreshing…"
                guideRepository.updatedAt > 0 -> "Refresh · Updated ${formatSlotTime(guideRepository.updatedAt)}"
                else -> "Refresh"
            },
        )
        val isSignedIn = app.localAppStorage.isSignedIn
        for ((buttonGuideScope, guideScopeButton) in guideScopeToButton) {
            guideScopeButton.isActivated = buttonGuideScope == guideScope
            guideScopeButton.isVisible = isSignedIn
        }
        val channelSearchAndShowFilter = guideRepository.channelSearchAndShowFilter
        val isShowFilterActive = channelSearchAndShowFilter.showFilterTitleId != null
        showFilterButton.setTextIfChanged("All Channels with ${channelSearchAndShowFilter.showFilterName}")
        showFilterButton.isActivated = isShowFilterActive
        showFilterButton.isVisible = isShowFilterActive
    }

    // TODO: Validate
    private fun buildChannelGuideRow(
        channel: GuideChannel,
        isCurrentChannelRow: Boolean,
        guideScheduleSettings: GuideScheduleSettings,
    ): GuideRow {
        val guideRepository = app.guideRepository
        val runningChannelAction = guideRepository.runningChannelAction(channel.id)
        return GuideRow(
            channel = channel,
            scheduledVideos = scheduleChannelVideosOrReuseCached(channel, guideScheduleSettings),
            isCurrentChannelRow = isCurrentChannelRow,
            isFavorite = channel.id in guideRepository.favoriteChannelIds,
            channelStatusText = channelStatusText(runningChannelAction, guideRepository.failedChannelAction(channel.id)),
            isLoadingMoreVideos = runningChannelAction == ChannelEpisodesAction.LOAD_MORE_EPISODES,
        )
    }

    // TODO: Validate
    private fun scheduleChannelVideosOrReuseCached(
        channel: GuideChannel,
        guideScheduleSettings: GuideScheduleSettings,
    ): List<ScheduledVideo> {
        val cachedChannelSchedule = channelIdToCachedSchedule[channel.id]
        // The repository makes a new video list whenever a channel's videos change, so the same list means the
        // same videos.
        if (cachedChannelSchedule != null &&
            cachedChannelSchedule.channelVideos === channel.videos &&
            cachedChannelSchedule.guideScheduleSettings == guideScheduleSettings
        ) {
            return cachedChannelSchedule.scheduledVideos
        }
        val scheduledVideos = scheduleChannelVideos(
            channel.videos,
            guideScheduleSettings.minimumVideoLength,
            textFittingLength = if (guideScheduleSettings.sizesGuideVideosByText) {
                { video -> guideGridView.videoTextFittingLength(video) }
            } else {
                null
            },
        )
        channelIdToCachedSchedule[channel.id] = CachedChannelSchedule(channel.videos, guideScheduleSettings, scheduledVideos)
        return scheduledVideos
    }

    // TODO: Validate
    private fun renderGuideLoadProblems(hasGuideRows: Boolean) {
        val shownGuideLoadProblemLines = guideLoadProblemLines(app.guideRepository, hasGuideRows)
        guideLoadProblemText.setTextIfChanged(shownGuideLoadProblemLines.joinToString("\n"))
        guideLoadProblemText.isVisible = shownGuideLoadProblemLines.isNotEmpty()
    }

    // TODO: Validate
    private fun showSignInExpiredDialogOnce() {
        if (!app.guideRepository.isSignInExpired) {
            hasShownSignInExpiredDialog = false
            return
        }
        if (hasShownSignInExpiredDialog) return
        hasShownSignInExpiredDialog = true
        AlertDialog.Builder(this)
            .setTitle("Sign-in expired")
            .setMessage(
                "Your Stream Channeler sign-in has expired, so your favorites, your own channels and your " +
                    "episode shuffles can't load. Public channels still work. Sign in again to get them back.",
            )
            .setPositiveButton("Sign in again") { _, _ -> LoginActivity.signOutAndShowLogin(this) }
            .setNegativeButton("Later", null)
            .show()
    }

    // TODO: Validate
    private fun selectGuideScope(guideScope: GuideScope) {
        app.localAppStorage.guideScope = guideScope
        if (searchInput.text.isNotBlank()) searchChannels()
        rebuildGuideRows()
    }

    // TODO: Validate
    private fun showRefreshMenu() {
        val guideRepository = app.guideRepository
        val refreshMenuItems = buildList {
            add(ActionMenuItem("Refresh and Shuffle All") { showErrorPopupIfGuideActionFails { guideRepository.refreshAndShuffleAll() } })
            add(ActionMenuItem("Refresh Channels") { guideRepository.refresh() })
            if (app.localAppStorage.isSignedIn) {
                add(ActionMenuItem("Refresh Episodes: Shown Channels") { guideRepository.checkChannelsForNewEpisodes(shownChannelIds) })
                add(
                    ActionMenuItem("Refresh Episodes: Every Channel") {
                        showErrorPopupIfGuideActionFails { guideRepository.checkAllChannelsForNewEpisodes() }
                    },
                )
                add(
                    ActionMenuItem("Refresh User Settings") {
                        app.accountSettings.refreshUserSettings(
                            onSuccess = { showStatusMessage("User settings refreshed.") },
                            onFailure = ::showErrorPopup,
                        )
                    },
                )
            }
            add(ActionMenuItem("Shuffle Channel Order") { showErrorPopupIfGuideActionFails { guideRepository.refreshChannelOrder() } })
            add(ActionMenuItem("Shuffle Episodes: Shown Channels") { guideRepository.resortChannels(shownChannelIds) })
            add(ActionMenuItem("Shuffle Episodes: Every Channel") { showErrorPopupIfGuideActionFails { guideRepository.resortAllChannels() } })
        }
        showActionMenu("Refresh", refreshMenuItems)
    }

    // TODO: Validate
    private fun showActionMenu(menuTitle: CharSequence, menuItems: List<ActionMenuItem>) {
        AlertDialog.Builder(this)
            .setTitle(menuTitle)
            .setItems(menuItems.map { it.label }.toTypedArray()) { _, chosenIndex -> menuItems[chosenIndex].onChosen() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // TODO: Validate
    private fun showChannelSettings(channel: GuideChannel) {
        val accountRefreshSettings = app.localAppStorage.accountRefreshSettings ?: return
        val channelRefreshSettings = accountRefreshSettings.channelIdToRefreshSettings[channel.id]
            ?: ChannelRefreshSettings(refreshesEpisodesAutomatically = null, shufflesEpisodesAutomatically = null)
        val settingLines = listOf(
            "Refresh Episodes: " + channelSettingLabel(
                channelRefreshSettings.refreshesEpisodesAutomatically,
                accountRefreshSettings.refreshesEpisodesAutomatically,
            ),
            "Shuffle Episodes: " + channelSettingLabel(
                channelRefreshSettings.shufflesEpisodesAutomatically,
                accountRefreshSettings.shufflesEpisodesAutomatically,
            ),
        )
        AlertDialog.Builder(this)
            .setTitle("My Settings for ${channel.name ?: "this channel"}")
            .setItems(settingLines.toTypedArray()) { _, chosenIndex ->
                val updatedChannelRefreshSettings = if (chosenIndex == 0) {
                    channelRefreshSettings.copy(
                        refreshesEpisodesAutomatically = nextChannelSetting(channelRefreshSettings.refreshesEpisodesAutomatically),
                    )
                } else {
                    channelRefreshSettings.copy(
                        shufflesEpisodesAutomatically = nextChannelSetting(channelRefreshSettings.shufflesEpisodesAutomatically),
                    )
                }
                app.accountSettings.updateChannelRefreshSettings(
                    channel.id,
                    updatedChannelRefreshSettings,
                    onSaved = { showChannelSettings(channel) },
                    onFailure = ::showErrorPopup,
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // TODO: Validate
    private fun automaticOrManualLabel(isAutomatic: Boolean): String = if (isAutomatic) "Automatic" else "Manual"

    // TODO: Validate
    private fun channelSettingLabel(channelSetting: Boolean?, accountSetting: Boolean): String =
        channelSetting?.let(::automaticOrManualLabel) ?: "My setting (${automaticOrManualLabel(accountSetting)})"

    // TODO: Validate
    private fun nextChannelSetting(channelSetting: Boolean?): Boolean? = when (channelSetting) {
        null -> true
        true -> false
        false -> null
    }

    // TODO: Validate
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // TODO: Validate
    private fun showStatusMessage(message: String) {
        statusMessageText.text = message
        statusMessageText.visibility = View.VISIBLE
        mainHandler.removeCallbacks(clearStatusMessageRunnable)
        mainHandler.postDelayed(clearStatusMessageRunnable, STATUS_MESSAGE_DURATION)
    }

    // TODO: Validate
    private fun showErrorPopup(errorMessage: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Something went wrong")
            .setMessage(errorMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    // TODO: Validate
    private fun showErrorPopupIfGuideActionFails(guideRepositoryAction: suspend () -> String?) {
        guideActivityScope.launch { guideRepositoryAction()?.let(::showErrorPopup) }
    }

    // TODO: Validate
    override fun onVideoFocused(guideRow: GuideRow, scheduledVideo: ScheduledVideo, windowStart: Long) {
        detailPanel.showVideo(scheduledVideo, windowStart)
    }

    // TODO: Validate
    override fun onChannelLabelFocused(guideRow: GuideRow) {
        detailPanel.showChannel(guideRow)
    }

    // TODO: Validate
    override fun onVideoChosen(guideRow: GuideRow, scheduledVideo: ScheduledVideo) {
        launchVideo(guideRow.channel, scheduledVideo.video)
    }

    // TODO: Validate
    override fun onVideoMenuRequested(guideRow: GuideRow, scheduledVideo: ScheduledVideo) {
        val video = scheduledVideo.video
        val showName = video.title ?: "this show"
        val findShowMenuItems = listOf(
            ActionMenuItem("Find All Channels with $showName") { showChannelsWithShow(video.titleId, showName) },
        )
        val testBumperMenuItem = ActionMenuItem("Test Bumper") { previewUpNextBumper(guideRow.channel, video) }
        showActionMenu(
            listOfNotNull(video.title ?: "Untitled episode", video.subtitle.takeIf { it.isNotEmpty() }).joinToString(" · "),
            watchStatusMenuItems(video, guideRow.channel.id) + findShowMenuItems + testBumperMenuItem,
        )
    }

    // TODO: Validate
    private fun watchStatusMenuItems(video: GuideVideo, channelId: String): List<ActionMenuItem> {
        val guideRepository = app.guideRepository
        val isVideoCompleted = video.watched && video.watchCompleted
        return when {
            !app.localAppStorage.isSignedIn || isVideoCompleted -> emptyList()
            video.watched -> listOf(
                ActionMenuItem("Mark as Completed") {
                    showErrorPopupIfGuideActionFails { guideRepository.completeStartedVideo(video, channelId) }
                },
                ActionMenuItem("Delete Watch") {
                    showErrorPopupIfGuideActionFails { guideRepository.deleteStartedVideoWatch(video) }
                },
            )
            else -> listOf("Mark as Started" to false, "Mark as Completed" to true).map { (watchStatusMenuItemLabel, marksVideoCompleted) ->
                ActionMenuItem(watchStatusMenuItemLabel) {
                    showErrorPopupIfGuideActionFails {
                        guideRepository.markVideoStartedOrCompleted(
                            video.episodeId,
                            completed = marksVideoCompleted,
                            channelId,
                        )
                    }
                }
            }
        }
    }

    // TODO: Validate
    private fun showChannelsWithShow(titleId: String, showName: String) {
        if (searchInput.text.isNotEmpty()) searchInput.setText("")
        app.guideRepository.filterChannelsByShow(titleId, showName)
        guideGridView.requestFocus()
    }

    // TODO: Validate
    private fun clearShowFilter() {
        app.guideRepository.clearShowFilter()
        guideGridView.requestFocus()
    }


    // TODO: Validate
    override fun onChannelLabelChosen(guideRow: GuideRow) {
        val guideRepository = app.guideRepository
        val channelId = guideRow.channel.id
        val channelIds = listOf(channelId)
        if (!app.localAppStorage.isSignedIn) {
            guideRepository.resortChannels(channelIds)
            return
        }
        val channelMenuItems = listOf(
            ActionMenuItem("Shuffle Episodes") { guideRepository.resortChannels(channelIds) },
            ActionMenuItem("Refresh Episodes") { guideRepository.checkChannelsForNewEpisodes(channelIds) },
            ActionMenuItem(if (guideRow.isFavorite) "Remove from Favorites" else "Add to Favorites") {
                showErrorPopupIfGuideActionFails {
                    guideRepository.setChannelFavorite(channelId, shouldBeFavorite = !guideRow.isFavorite)
                }
            },
            ActionMenuItem("My Settings for This Channel") { showChannelSettings(guideRow.channel) },
        )
        showActionMenu(guideRow.channel.displayName, channelMenuItems)
    }

    // TODO: Validate
    override fun onLoadMoreVideosFocused(guideRow: GuideRow) {
        detailPanel.showLoadMoreVideos(guideRow)
    }

    // TODO: Validate
    override fun onLoadMoreVideosChosen(guideRow: GuideRow) {
        app.guideRepository.loadMoreVideos(guideRow.channel.id)
    }

    // TODO: Validate
    override fun onUpFromFirstRow(): Boolean {
        val toolbarViewToFocus = lastFocusedToolbarView.takeIf { it.isShown } ?: refreshButton
        return toolbarViewToFocus.requestFocus()
    }

    // TODO: Validate
    override fun onNearLastRow() {
        val shownChannelList = shownChannelList(shownGuideScope(), trimmedSearchQuery()) ?: return
        app.guideRepository.loadMoreChannels(shownChannelList)
    }

    // TODO: Validate
    private fun launchVideo(channel: GuideChannel, video: GuideVideo) {
        when (val launchResult = app.videoPlayer.playVideo(this, channel, video)) {
            is VideoLaunchResult.Failed -> showStatusMessage(launchResult.reason)
            is VideoLaunchResult.Launched -> markStartedVideoWatchedIfEnabled(channel, video)
        }
    }

    // TODO: Validate
    private fun markStartedVideoWatchedIfEnabled(channel: GuideChannel, video: GuideVideo) {
        if (!app.localAppStorage.marksStartedVideosWatched) return
        showErrorPopupIfGuideActionFails { app.guideRepository.markVideoWatched(video.episodeId, channel.id) }
    }

    // TODO: Validate
    private fun markFinishedVideoCompletedIfEnabled(finishedPlayback: ActivePlayback) {
        if (!app.localAppStorage.marksFinishedVideosCompleted || !app.localAppStorage.isSignedIn) return
        val finishedVideo = finishedPlayback.playingVideo ?: return
        showErrorPopupIfGuideActionFails {
            app.guideRepository.completeStartedVideo(finishedVideo.copy(watchId = null), finishedPlayback.channelId)
        }
    }

    // TODO: Validate
    private fun endPlaybackFromOutcomeExtra(outcomeIntent: Intent) {
        val playbackOutcome = PlaybackOutcome.fromName(outcomeIntent.getStringExtra(PlaybackMonitor.EXTRA_PLAYBACK_OUTCOME)) ?: return
        outcomeIntent.removeExtra(PlaybackMonitor.EXTRA_PLAYBACK_OUTCOME)
        val videoEndResult = app.videoPlayer.endPlayback(playbackOutcome) ?: return
        val endedPlayback = videoEndResult.endedPlayback
        if (videoEndResult !is VideoEndResult.StoppedEarly) markFinishedVideoCompletedIfEnabled(endedPlayback)
        val endedVideoTitle = endedPlayback.playingVideo?.title ?: "the video"
        hasAppliedInitialFocus = true
        rebuildGuideRows()
        guideGridView.focusEpisode(endedPlayback.channelId, endedPlayback.playingEpisodeId)
        guideGridView.requestFocus()

        when (videoEndResult) {
            is VideoEndResult.StoppedEarly -> showStatusMessage("Stopped watching $endedVideoTitle.")
            is VideoEndResult.Finished -> showStatusMessage("Finished $endedVideoTitle.")
            is VideoEndResult.PlayNext ->
                playNextVideo(videoEndResult.nextVideo.channel, videoEndResult.nextVideo.video)
            is VideoEndResult.NoMoreVideos ->
                showStatusMessage("That was the last video loaded for ${endedPlayback.channelName ?: "this channel"}.")
        }
    }

    // TODO: Validate
    private fun playNextVideo(channel: GuideChannel, nextVideo: GuideVideo) {
        guideGridView.focusEpisode(channel.id, nextVideo.episodeId)
        if (!app.localAppStorage.showsUpNextInformation) {
            launchVideo(channel, nextVideo)
            return
        }
        announcedUpNextVideo = AnnouncedUpNextVideo(channel, nextVideo)
        upNextLauncher.launch(upNextAnnouncementIntent(channel, nextVideo))
    }

    // TODO: Validate
    private fun previewUpNextBumper(channel: GuideChannel, video: GuideVideo) {
        startActivity(upNextAnnouncementIntent(channel, video))
    }

    // TODO: Validate
    private fun upNextAnnouncementIntent(channel: GuideChannel, announcedVideo: GuideVideo): Intent {
        val followingVideo = nextPlayableVideo(channel, announcedVideo.episodeId)
        return UpNextActivity.upNextAnnouncementIntent(this, channel.name, announcedVideo, followingVideo)
    }

    // TODO: Validate
    private fun playOrDeclineUpNextVideo(upNextResultCode: Int) {
        val upNextVideo = announcedUpNextVideo ?: return
        announcedUpNextVideo = null
        if (upNextResultCode == RESULT_OK) {
            launchVideo(upNextVideo.channel, upNextVideo.video)
        } else {
            app.videoPlayer.declineNextVideo()
            showStatusMessage("Didn't play ${upNextVideo.video.title ?: "the next video"}.")
        }
    }

    // TODO: Validate
    private val leaveGridOnBackCallback = object : OnBackPressedCallback(true) {
        // TODO: Validate
        override fun handleOnBackPressed() {
            if (guideGridView.hasFocus() || searchInput.hasFocus()) {
                refreshButton.requestFocus()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    // TODO: Validate
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
            return true
        }
        val guideShortcut = REMOTE_KEY_CODE_TO_SHORTCUT[event.keyCode]
            ?: KEYBOARD_KEY_CODE_TO_SHORTCUT[event.keyCode]?.takeUnless { searchInput.hasFocus() }
            ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) runGuideShortcut(guideShortcut)
        return true
    }

    // TODO: Validate
    private fun runGuideShortcut(guideShortcut: GuideShortcut) {
        when (guideShortcut) {
            GuideShortcut.OPEN_MENU -> {
                val didOpenVideoMenu = guideGridView.hasFocus() && guideGridView.openFocusedVideoMenu()
                if (!didOpenVideoMenu) openSettings()
            }
            GuideShortcut.OPEN_SETTINGS -> openSettings()
            GuideShortcut.FOCUS_GUIDE -> guideGridView.requestFocus()
            GuideShortcut.FOCUS_SEARCH -> searchInput.requestFocus()
            GuideShortcut.REFRESH_GUIDE -> app.guideRepository.refresh()
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        val REMOTE_KEY_CODE_TO_SHORTCUT = mapOf(
            KeyEvent.KEYCODE_MENU to GuideShortcut.OPEN_MENU,
            KeyEvent.KEYCODE_BUTTON_START to GuideShortcut.OPEN_MENU,
            KeyEvent.KEYCODE_SETTINGS to GuideShortcut.OPEN_SETTINGS,
            KeyEvent.KEYCODE_GUIDE to GuideShortcut.FOCUS_GUIDE,
            KeyEvent.KEYCODE_BUTTON_X to GuideShortcut.FOCUS_SEARCH,
            KeyEvent.KEYCODE_SEARCH to GuideShortcut.FOCUS_SEARCH,
            KeyEvent.KEYCODE_BUTTON_SELECT to GuideShortcut.REFRESH_GUIDE,
        )

        // TODO: Validate
        val KEYBOARD_KEY_CODE_TO_SHORTCUT = mapOf(
            KeyEvent.KEYCODE_M to GuideShortcut.OPEN_MENU,
            KeyEvent.KEYCODE_S to GuideShortcut.OPEN_SETTINGS,
            KeyEvent.KEYCODE_G to GuideShortcut.FOCUS_GUIDE,
            KeyEvent.KEYCODE_SLASH to GuideShortcut.FOCUS_SEARCH,
            KeyEvent.KEYCODE_R to GuideShortcut.REFRESH_GUIDE,
        )

        // TODO: Validate
        const val STATUS_MESSAGE_DURATION = 8_000L

        // TODO: Validate
        const val GUIDE_CLOCK_TICK_INTERVAL = 60_000L

        // TODO: Validate
        const val SEARCH_DELAY = 400L
    }
}
