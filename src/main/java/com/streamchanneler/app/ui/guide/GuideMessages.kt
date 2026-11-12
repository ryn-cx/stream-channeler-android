// TODO: Validate
package com.streamchanneler.app.ui.guide

import com.streamchanneler.app.data.ChannelEpisodesAction
import com.streamchanneler.app.data.ChannelListLoadStatus
import com.streamchanneler.app.data.GuideRepository
import com.streamchanneler.app.data.GuideScope
import com.streamchanneler.app.data.PagedChannelList

// TODO: Validate
internal fun channelStatusText(
    runningChannelAction: ChannelEpisodesAction?,
    failedChannelAction: ChannelEpisodesAction?,
): String? = when (runningChannelAction) {
    ChannelEpisodesAction.RESORT -> "Resorting episodes…"
    ChannelEpisodesAction.CHECK_FOR_NEW_EPISODES -> "Checking for new episodes…"
    ChannelEpisodesAction.LOAD_MORE_EPISODES -> "Loading more episodes…"
    null -> when (failedChannelAction) {
        ChannelEpisodesAction.RESORT -> "Couldn't resort the episodes"
        ChannelEpisodesAction.CHECK_FOR_NEW_EPISODES -> "Couldn't check for new episodes"
        ChannelEpisodesAction.LOAD_MORE_EPISODES -> "Couldn't load more episodes"
        null -> null
    }
}

// TODO: Validate
internal fun loadMoreChannelsFooterMessage(
    shownChannelList: PagedChannelList?,
    hasGuideRows: Boolean,
    isRefreshing: Boolean,
): String? {
    if (shownChannelList == null || !hasGuideRows || isRefreshing) return null
    return when (shownChannelList.loadStatus) {
        ChannelListLoadStatus.IDLE -> "Press Down for more channels"
        ChannelListLoadStatus.LOADING -> "Loading more channels…"
        ChannelListLoadStatus.FAILED -> "Couldn't load more channels. Press Down to retry."
        ChannelListLoadStatus.EXHAUSTED -> "No more channels"
    }
}

// TODO: Validate
internal fun emptyGuideMessage(guideRepository: GuideRepository, guideScope: GuideScope, searchQuery: String): String {
    val channelSearchAndShowFilter = guideRepository.channelSearchAndShowFilter
    val showFilterName = channelSearchAndShowFilter.showFilterName
    val lastShowFilterErrorMessage = channelSearchAndShowFilter.lastShowFilterErrorMessage
    val isShowFilterActive = channelSearchAndShowFilter.showFilterTitleId != null
    return when {
        isShowFilterActive && channelSearchAndShowFilter.isLoadingShowFilter -> "Finding channels with $showFilterName…"
        isShowFilterActive && lastShowFilterErrorMessage != null ->
            "Couldn't find channels with $showFilterName: $lastShowFilterErrorMessage. Select All Channels with $showFilterName to go back."
        isShowFilterActive -> "No channels with $showFilterName found."
        searchQuery.isNotEmpty() &&
            (channelSearchAndShowFilter.isSearchingChannels || searchQuery != channelSearchAndShowFilter.channelSearchQuery) ->
            "Searching channels…"
        searchQuery.isNotEmpty() && channelSearchAndShowFilter.lastSearchErrorMessage != null ->
            "Couldn't search channels: ${channelSearchAndShowFilter.lastSearchErrorMessage}. Select Refresh to try again."
        guideRepository.isRefreshing || guideRepository.isWaitingForFirstGuide -> "Loading the guide…"
        guideRepository.lastRefreshErrorMessage != null ->
            "Couldn't load the guide: ${guideRepository.lastRefreshErrorMessage}. Select Refresh to try again."
        searchQuery.isNotEmpty() -> "No channels match your search."
        guideRepository.isSignInExpired && guideScope != GuideScope.PUBLIC ->
            "Your sign-in has expired. Press Menu and sign in again to see these channels."
        guideScope == GuideScope.FAVORITES -> "No favorite channels yet. Favorite channels on streamchanneler.com."
        guideScope == GuideScope.OWNED -> "No channels of your own yet. Make one on streamchanneler.com."
        else -> "No channels with videos yet."
    }
}

// TODO: Validate
internal fun guideLoadProblemLines(guideRepository: GuideRepository, hasGuideRows: Boolean): List<String> = buildList {
    if (guideRepository.isSignInExpired) {
        add("Your sign-in has expired, so your favorites, your own channels and your episode shuffles can't load. Press Menu and sign in again.")
    }
    val lastRefreshErrorMessage = guideRepository.lastRefreshErrorMessage
    if (lastRefreshErrorMessage != null && hasGuideRows && !guideRepository.isRefreshing) {
        val savedGuideTime = guideRepository.updatedAt.takeIf { it > 0 }?.let { " from ${formatSlotTime(it)}" } ?: ""
        add("Couldn't refresh the guide: $lastRefreshErrorMessage. Showing the saved guide$savedGuideTime. Select Refresh to try again.")
    }
    addAll(
        guideRepository.guideLoadProblems.filter { guideLoadProblem ->
            !guideRepository.isSignInExpired || !guideLoadProblem.contains("your sign-in has expired")
        },
    )
}
