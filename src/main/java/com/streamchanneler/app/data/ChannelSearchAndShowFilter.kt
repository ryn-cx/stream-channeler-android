// TODO: Validate
package com.streamchanneler.app.data

import org.json.JSONArray
import org.json.JSONObject

// TODO: Validate
class ChannelSearchAndShowFilter {
    // TODO: Validate
    var channelSearchQuery = ""
        private set
    // TODO: Validate
    var channelSearchScope = GuideScope.PUBLIC
        private set

    // TODO: Validate
    var searchChannelList: PagedChannelList? = null
        private set

    // TODO: Validate
    var showFilterTitleId: String? = null
        private set
    // TODO: Validate
    var showFilterName = ""
        private set

    // TODO: Validate
    var showFilterChannelList: PagedChannelList? = null
        private set

    // TODO: Validate
    val isSearchingChannels: Boolean
        get() = searchChannelList?.isLoadingFirstPage == true

    // TODO: Validate
    val lastSearchErrorMessage: String?
        get() = searchChannelList?.firstPageErrorMessage

    // TODO: Validate
    val isLoadingShowFilter: Boolean
        get() = showFilterChannelList?.isLoadingFirstPage == true

    // TODO: Validate
    val lastShowFilterErrorMessage: String?
        get() = showFilterChannelList?.firstPageErrorMessage

    // TODO: Validate
    fun replaceSearch(searchQuery: String, guideScope: GuideScope): PagedChannelList? {
        searchChannelList?.isDiscarded = true
        channelSearchQuery = searchQuery
        channelSearchScope = guideScope
        searchChannelList = if (searchQuery.isEmpty()) {
            null
        } else {
            val channelNameFilterOptions = JSONArray().put(JSONObject().put("id", "name").put("value", searchQuery))
            PagedChannelList(listOf(ChannelListQuery(guideScope, channelNameFilterOptions)))
        }
        return searchChannelList
    }

    // TODO: Validate
    fun replaceShowFilter(titleId: String, showName: String, searchedGuideScopes: List<GuideScope>): PagedChannelList {
        showFilterChannelList?.isDiscarded = true
        showFilterTitleId = titleId
        showFilterName = showName
        val newShowFilterChannelList = PagedChannelList(
            searchedGuideScopes.map { guideScope -> ChannelListQuery(guideScope, titleId = titleId) },
        )
        showFilterChannelList = newShowFilterChannelList
        return newShowFilterChannelList
    }

    // TODO: Validate
    fun clearShowFilter() {
        showFilterChannelList?.isDiscarded = true
        showFilterChannelList = null
        showFilterTitleId = null
        showFilterName = ""
    }

    // TODO: Validate
    fun clearSearchAndShowFilter() {
        searchChannelList?.isDiscarded = true
        searchChannelList = null
        channelSearchQuery = ""
        clearShowFilter()
    }
}
