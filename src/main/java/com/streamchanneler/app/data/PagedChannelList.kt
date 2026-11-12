// TODO: Validate

package com.streamchanneler.app.data

import org.json.JSONArray


// TODO: Validate
enum class ChannelListLoadStatus {
    IDLE,

    LOADING,

    FAILED,

    EXHAUSTED,
}


// TODO: Validate
data class ChannelListQuery(
    val scope: GuideScope,
    val filterOptions: JSONArray = JSONArray(),
    val titleId: String? = null,
)


// TODO: Validate
class PagedChannelList(val queries: List<ChannelListQuery>) {
    // TODO: Validate
    val channelIds = LinkedHashSet<String>()

    // TODO: Validate
    var queryIndex = 0

    // TODO: Validate
    var nextPageIndex = 0
    // TODO: Validate
    var loadStatus = ChannelListLoadStatus.IDLE

    // TODO: Validate
    var lastErrorMessage: String? = null

    // TODO: Validate
    var isDiscarded = false

    // TODO: Validate
    val hasLoadedFirstPage: Boolean
        get() = channelIds.isNotEmpty() || loadStatus == ChannelListLoadStatus.EXHAUSTED

    // TODO: Validate
    val isLoadingFirstPage: Boolean
        get() = !hasLoadedFirstPage && loadStatus == ChannelListLoadStatus.LOADING

    // TODO: Validate
    val firstPageErrorMessage: String?
        get() = lastErrorMessage.takeIf { !hasLoadedFirstPage }
}
