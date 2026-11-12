// TODO: Validate
package com.streamchanneler.app.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


// TODO: Validate
class GuideRepository(
    private val api: StreamChannelerApi,
    private val localAppStorage: LocalAppStorage,
    private val accountSettings: AccountSettingsRepository,
    guideCacheFile: File,
) {
    // TODO: Validate
    fun interface Listener {
        // TODO: Validate
        fun onGuideChanged()
    }

    // TODO: Validate
    private val mainThreadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // TODO: Validate
    private val coordinatorMutex = Mutex()
    // TODO: Validate
    private val channelFetchDispatcher = Dispatchers.IO.limitedParallelism(CHANNEL_FETCH_THREADS)

    // TODO: Validate
    private val backgroundWriteDispatcher = Dispatchers.IO.limitedParallelism(1)
    // TODO: Validate
    private val guideCache = GuideCache(guideCacheFile, mainThreadScope, backgroundWriteDispatcher)
    // TODO: Validate
    private val channelsById = LinkedHashMap<String, GuideChannel>()
    // TODO: Validate
    private val listeners = mutableSetOf<Listener>()
    // TODO: Validate
    private val channelIdToRunningAction = mutableMapOf<String, ChannelEpisodesAction>()
    // TODO: Validate
    private val channelIdToFailedAction = mutableMapOf<String, ChannelEpisodesAction>()
    // TODO: Validate
    private val guideScopeToChannelList = mutableMapOf<GuideScope, PagedChannelList>()

    // TODO: Validate
    private var refreshGeneration = 0
    // TODO: Validate
    private var hasStartedLoading = false

    // TODO: Validate
    var favoriteChannelIds: Set<String> = emptySet()
        private set

    // TODO: Validate
    var updatedAt: Long = 0
        private set
    // TODO: Validate
    var isRefreshing = false
        private set

    // TODO: Validate
    var lastRefreshErrorMessage: String? = null
        private set

    // TODO: Validate
    var guideLoadProblems: List<String> = emptyList()
        private set

    // TODO: Validate
    var isSignInExpired = false
        private set
    // TODO: Validate
    val channelSearchAndShowFilter = ChannelSearchAndShowFilter()


    // TODO: Validate
    val isWaitingForFirstGuide: Boolean
        get() = guideScopeToChannelList.isEmpty() && lastRefreshErrorMessage == null

    // TODO: Validate
    fun guideChannelList(guideScope: GuideScope): PagedChannelList? = guideScopeToChannelList[guideScope]


    // TODO: Validate
    fun loadedChannels(channelList: PagedChannelList): List<GuideChannel> =
        channelList.channelIds.mapNotNull { channelsById[it] }

    // TODO: Validate
    fun findChannel(channelId: String): GuideChannel? = channelsById[channelId]

    // TODO: Validate
    fun runningChannelAction(channelId: String): ChannelEpisodesAction? = channelIdToRunningAction[channelId]

    // TODO: Validate
    fun failedChannelAction(channelId: String): ChannelEpisodesAction? = channelIdToFailedAction[channelId]

    // TODO: Validate
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    // TODO: Validate
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    // TODO: Validate
    fun startLoadingIfNeeded() {
        if (hasStartedLoading) return
        hasStartedLoading = true
        mainThreadScope.launch {
            val cachedGuide = runOnIoThreadHoldingCoordinatorMutex {
                runCatching { guideCache.readCachedGuide() }
                    .onFailure { Log.w(TAG, "Couldn't read the guide cache", it) }
                    .getOrNull()
            }
            if (cachedGuide != null && channelsById.isEmpty()) {
                for (cachedChannel in cachedGuide.channels) channelsById[cachedChannel.id] = cachedChannel
                favoriteChannelIds = cachedGuide.favoriteChannelIds
                for ((guideScope, cachedChannelIds) in cachedGuide.guideScopeToChannelIds) {
                    replaceGuideChannelList(guideScope, cachedChannelIds)
                }
                updatedAt = cachedGuide.updatedAt
                notifyListeners()
            }
            loadWhatChangedSinceTheGuideWasCached(hasCachedGuide = cachedGuide != null)
        }
    }

    // TODO: Validate
    private fun loadWhatChangedSinceTheGuideWasCached(hasCachedGuide: Boolean) {
        if (!localAppStorage.isSignedIn) {
            if (!hasCachedGuide) refresh()
            return
        }
        if (!hasCachedGuide) {
            refresh(runsAutomaticRefresh = true)
            mainThreadScope.launch { runOnIoThreadHoldingCoordinatorMutex { accountSettings.loadSignedInUserIfReachable() } }
            return
        }
        mainThreadScope.launch {
            val automaticRefreshResult = runOnIoThreadHoldingCoordinatorMutex {
                runCatching {
                    accountSettings.loadSignedInUserIfReachable()
                    if (localAppStorage.accountRefreshSettings?.hasAutomaticSettings == false) null else api.runAutomaticRefresh()
                }
                    .onFailure { Log.w(TAG, "Couldn't run the automatic refreshes", it) }
                    .getOrNull()
            }
            when {
                automaticRefreshResult?.refreshedChannels == true ||
                        automaticRefreshResult?.changedEveryChannelsEpisodes == true -> refresh()

                automaticRefreshResult != null && automaticRefreshResult.changedEpisodesChannelIds.isNotEmpty() ->
                    checkChannelsForNewEpisodesLoadedFromServer(automaticRefreshResult.changedEpisodesChannelIds)
            }
        }
    }

    // TODO: Validate
    private fun checkChannelsForNewEpisodesLoadedFromServer(channelIds: List<String>) {
        runChannelEpisodesAction(channelIds, ChannelEpisodesAction.CHECK_FOR_NEW_EPISODES) { channelsToReload ->
            channelsToReload.associate { channel -> channel.id to null }
        }
    }

    // TODO: Validate
    suspend fun refreshAndShuffleAll(): String? {
        val isSignedIn = localAppStorage.isSignedIn
        return changeOrderThenRefresh("refresh and shuffle everything") {
            if (isSignedIn) {
                api.refreshAndShuffleAll()
            } else {
                localAppStorage.advanceLocalMinimumEpisodeSeed()
                localAppStorage.advanceLocalChannelOrderSeed()
            }
        }
    }

    // TODO: Validate
    private suspend fun changeOrderThenRefresh(
        failedChangeDescription: String,
        changeOrderInBackground: () -> Unit,
    ): String? = runOutlivingCaller {
        try {
            runOnIoThreadHoldingCoordinatorMutex(changeOrderInBackground)
        } catch (orderChangeError: Exception) {
            Log.w(TAG, "Couldn't $failedChangeDescription", orderChangeError)
            return@runOutlivingCaller "Couldn't $failedChangeDescription: ${orderChangeError.message}"
        }
        refresh()
        null
    }

    // TODO: Validate
    private suspend fun runOutlivingCaller(repositoryWork: suspend () -> String?): String? =
        mainThreadScope.async { repositoryWork() }.await()

    // TODO: Validate
    private suspend fun <T> runOnIoThreadHoldingCoordinatorMutex(coordinatorWork: () -> T): T =
        coordinatorMutex.withLock { withContext(Dispatchers.IO) { coordinatorWork() } }

    // TODO: Validate
    private fun replaceGuideChannelList(guideScope: GuideScope, channelIds: List<String>): PagedChannelList {
        guideScopeToChannelList[guideScope]?.isDiscarded = true
        val replacementChannelList = PagedChannelList(listOf(ChannelListQuery(guideScope)))
        replacementChannelList.channelIds.addAll(channelIds)
        guideScopeToChannelList[guideScope] = replacementChannelList
        return replacementChannelList
    }

    // TODO: Validate
    fun clearForAccountChange() {
        refreshGeneration++
        channelsById.clear()
        favoriteChannelIds = emptySet()
        for (guideChannelList in guideScopeToChannelList.values) guideChannelList.isDiscarded = true
        guideScopeToChannelList.clear()
        updatedAt = 0
        isRefreshing = false
        lastRefreshErrorMessage = null
        guideLoadProblems = emptyList()
        isSignInExpired = false
        channelSearchAndShowFilter.clearSearchAndShowFilter()
        channelIdToRunningAction.clear()
        channelIdToFailedAction.clear()
        hasStartedLoading = false
        guideCache.cancelPendingSaveAndDelete()
        notifyListeners()
    }


    // TODO: Validate
    fun refresh(runsAutomaticRefresh: Boolean = false) {
        val generation = ++refreshGeneration
        isRefreshing = true
        lastRefreshErrorMessage = null
        notifyListeners()
        val watchedEpisodeIds = localAppStorage.readWatchedEpisodeIds()
        val isSignedIn = localAppStorage.isSignedIn
        mainThreadScope.launch {
            coordinatorMutex.withLock {
                val refreshLoadErrors = mutableListOf<Throwable>()
                val refreshLoadProblems = mutableListOf<String>()
                try {
                    loadRefreshedGuide(
                        generation,
                        isSignedIn,
                        runsAutomaticRefresh,
                        watchedEpisodeIds,
                        refreshLoadErrors,
                        refreshLoadProblems,
                    )
                } catch (refreshError: Exception) {
                    Log.w(TAG, "Couldn't refresh the guide", refreshError)
                    if (generation != refreshGeneration) return@launch
                    isRefreshing = false
                    lastRefreshErrorMessage = describeLoadError(refreshError)
                    guideLoadProblems = refreshLoadProblems.toList()
                    isSignInExpired = isSignedIn && (refreshLoadErrors + refreshError).any { isSignInRejectedError(it) }
                    notifyListeners()
                }
            }
        }
    }

    // TODO: Validate
    private suspend fun loadRefreshedGuide(
        generation: Int,
        isSignedIn: Boolean,
        runsAutomaticRefresh: Boolean,
        watchedEpisodeIds: Set<String>,
        refreshLoadErrors: MutableList<Throwable>,
        refreshLoadProblems: MutableList<String>,
    ) {
        val refreshedGuideScopes = guideScopesToLoad(isSignedIn)
        val sentOrderSeeds = if (isSignedIn) null else withContext(Dispatchers.IO) { localAppStorage.loadLocalSeeds() }
        val guideScopeToFirstPageResult = refreshedGuideScopes.associateWith { guideScope ->
            fetchFirstChannelPage(
                guideScope,
                sentOrderSeeds,
                runsAutomaticRefresh = runsAutomaticRefresh && isSignedIn && guideScope == refreshedGuideScopes.first(),
                refreshLoadErrors,
                refreshLoadProblems,
            )
        }
        val listItemsInGuideOrder = guideScopeToFirstPageResult.values
            .flatMap { firstPageResult -> firstPageResult.getOrDefault(emptyList()) }
            .distinctBy { it.id }
        val channelsLoad = fetchChannelsWithVideos(listItemsInGuideOrder, sentOrderSeeds, watchedEpisodeIds) { loadedChannel ->
            if (generation == refreshGeneration) {
                channelsById[loadedChannel.id] = loadedChannel
                notifyListeners()
            }
        }
        channelsLoad.failureProblem?.let { failedChannelsProblem ->
            refreshLoadErrors.addAll(channelsLoad.failedListItemToError.values)
            refreshLoadProblems.add(failedChannelsProblem)
        }
        if (generation != refreshGeneration) return
        replaceGuideWithRefreshedChannels(guideScopeToFirstPageResult, listItemsInGuideOrder, channelsLoad)
        for (droppedGuideScope in GuideScope.entries - refreshedGuideScopes.toSet()) {
            guideScopeToChannelList.remove(droppedGuideScope)?.isDiscarded = true
        }
        guideLoadProblems = refreshLoadProblems.toList()
        isSignInExpired = isSignedIn && refreshLoadErrors.any { isSignInRejectedError(it) }
        updatedAt = System.currentTimeMillis()
        isRefreshing = false
        scheduleGuideCacheSave()
        notifyListeners()
        with(channelSearchAndShowFilter) {
            if (searchChannelList != null) searchChannels(channelSearchQuery, channelSearchScope)
            showFilterTitleId?.let { filteredTitleId -> filterChannelsByShow(filteredTitleId, showFilterName) }
        }
    }

    // TODO: Validate
    private suspend fun fetchFirstChannelPage(
        guideScope: GuideScope,
        sentOrderSeeds: OrderSeeds?,
        runsAutomaticRefresh: Boolean,
        refreshLoadErrors: MutableList<Throwable>,
        refreshLoadProblems: MutableList<String>,
    ): Result<List<ChannelListItem>> {
        val firstPageResult = runCatching {
            withContext(Dispatchers.IO) {
                api.fetchChannelList(
                    guideScope,
                    offset = 0,
                    limit = CHANNEL_PAGE_SIZE,
                    channelOrderSeed = sentOrderSeeds?.channelOrderSeed,
                    runsAutomaticRefresh = runsAutomaticRefresh,
                )
            }
        }
        firstPageResult.exceptionOrNull()?.let { firstPageError ->
            if (guideScope == GuideScope.PUBLIC) throw firstPageError
            Log.w(TAG, "Couldn't load ${guideScope.name} channels", firstPageError)
            refreshLoadErrors.add(firstPageError)
            val failedListName = if (guideScope == GuideScope.FAVORITES) "favorite" else "own"
            refreshLoadProblems.add("Couldn't load your $failedListName channels: ${describeLoadError(firstPageError)}.")
        }
        return firstPageResult
    }

    // TODO: Validate
    private fun replaceGuideWithRefreshedChannels(
        guideScopeToFirstPageResult: Map<GuideScope, Result<List<ChannelListItem>>>,
        listItemsInGuideOrder: List<ChannelListItem>,
        channelsLoad: ChannelsLoad,
    ) {
        val keptChannelIdsFromFailedLists = buildSet {
            for ((guideScope, firstPageResult) in guideScopeToFirstPageResult) {
                if (firstPageResult.isFailure) guideScopeToChannelList[guideScope]?.let { addAll(it.channelIds) }
            }
        }
        val failedChannelIds = channelsLoad.failedListItemToError.keys.map { it.id }.toSet()
        val loadedChannelIds = channelsLoad.loadedChannels.map { it.id }.toSet()
        val channelIdsInGuideOrder =
            (keptChannelIdsFromFailedLists + listItemsInGuideOrder.map { it.id }).distinct()
        val refreshedChannelsById = LinkedHashMap<String, GuideChannel>()
        for (channelId in channelIdsInGuideOrder) {
            val isKeptFromBeforeRefresh =
                channelId in failedChannelIds || channelId in keptChannelIdsFromFailedLists
            if (channelId in loadedChannelIds || isKeptFromBeforeRefresh) {
                channelsById[channelId]?.let { refreshedChannelsById[channelId] = it }
            }
        }
        channelsById.clear()
        channelsById.putAll(refreshedChannelsById)
        for ((guideScope, firstPageResult) in guideScopeToFirstPageResult) {
            val firstPageListItems = firstPageResult.getOrNull() ?: continue
            val firstPageChannelIds = firstPageListItems.map { it.id }
            replaceGuideChannelList(guideScope, firstPageChannelIds).apply {
                nextPageIndex = 1
                loadStatus =
                    if (firstPageListItems.isEmpty()) ChannelListLoadStatus.EXHAUSTED else ChannelListLoadStatus.IDLE
            }
            if (guideScope == GuideScope.FAVORITES) favoriteChannelIds = firstPageChannelIds.toSet()
        }
    }

    // TODO: Validate
    fun loadMoreChannels(channelList: PagedChannelList) {
        if (channelList.isDiscarded) return
        when (channelList.loadStatus) {
            ChannelListLoadStatus.LOADING, ChannelListLoadStatus.EXHAUSTED -> return
            ChannelListLoadStatus.IDLE, ChannelListLoadStatus.FAILED -> Unit
        }
        if (isRefreshing && channelList in guideScopeToChannelList.values) return
        channelList.loadStatus = ChannelListLoadStatus.LOADING
        channelList.lastErrorMessage = null
        notifyListeners()
        val knownChannelIds = channelsById.keys.toMutableSet()
        val listedChannelIds = channelList.channelIds.toMutableSet()
        val firstQueryIndexToTry = channelList.queryIndex
        val firstPageIndexToTry = channelList.nextPageIndex
        val watchedEpisodeIds = localAppStorage.readWatchedEpisodeIds()

        mainThreadScope.launch {
            coordinatorMutex.withLock {
                try {
                    val loadMoreProblems = mutableListOf<String>()
                    val sentOrderSeeds = withContext(Dispatchers.IO) { orderSeedsToSend() }
                    var queryIndex = firstQueryIndexToTry
                    var pageIndex = firstPageIndexToTry
                    var pageAttemptCount = 0
                    while (queryIndex < channelList.queries.size && pageAttemptCount < CHANNEL_PAGE_ATTEMPTS) {
                        pageAttemptCount++
                        val channelListQuery = channelList.queries[queryIndex]
                        val pageListItems = withContext(Dispatchers.IO) {
                            api.fetchChannelList(
                                channelListQuery.scope,
                                offset = pageIndex * CHANNEL_PAGE_SIZE,
                                limit = CHANNEL_PAGE_SIZE,
                                channelOrderSeed = sentOrderSeeds?.channelOrderSeed,
                                filterOptions = channelListQuery.filterOptions,
                                titleId = channelListQuery.titleId,
                            )
                        }
                        if (pageListItems.isEmpty()) {
                            queryIndex++
                            pageIndex = 0
                            continue
                        }
                        pageIndex++
                        val pageNewlyListedItems = pageListItems.filter { listedChannelIds.add(it.id) }
                        val pageNewlyListedChannelIds = pageNewlyListedItems.map { it.id }
                        if (!channelList.isDiscarded) {
                            channelList.channelIds.addAll(pageNewlyListedChannelIds)
                            if (channelListQuery.scope == GuideScope.FAVORITES) {
                                favoriteChannelIds = favoriteChannelIds + pageNewlyListedChannelIds
                            }
                            notifyListeners()
                        }
                        val pageAlreadyLoadedCount = pageNewlyListedItems.count { it.id in knownChannelIds }
                        val pageUnloadedItems = pageNewlyListedItems.filter { knownChannelIds.add(it.id) }
                        val pageChannelsLoad = fetchChannelsWithVideos(
                            pageUnloadedItems,
                            sentOrderSeeds,
                            watchedEpisodeIds,
                        ) { loadedChannel ->
                            if (!channelList.isDiscarded && loadedChannel.id !in channelsById) {
                                channelsById[loadedChannel.id] = loadedChannel
                                notifyListeners()
                            }
                        }
                        pageChannelsLoad.failureProblem?.let { failedChannelsProblem -> loadMoreProblems.add(failedChannelsProblem) }
                        if (pageAlreadyLoadedCount + pageChannelsLoad.loadedChannels.size > 0) break
                    }
                    if (channelList.isDiscarded) return@launch
                    channelList.queryIndex = queryIndex
                    channelList.nextPageIndex = pageIndex
                    channelList.loadStatus =
                        if (queryIndex >= channelList.queries.size) ChannelListLoadStatus.EXHAUSTED else ChannelListLoadStatus.IDLE
                    guideLoadProblems = (guideLoadProblems + loadMoreProblems).distinct()
                    scheduleGuideCacheSave()
                    notifyListeners()
                } catch (loadMoreError: Exception) {
                    Log.w(TAG, "Couldn't load more channels", loadMoreError)
                    if (channelList.isDiscarded) return@launch
                    channelList.loadStatus = ChannelListLoadStatus.FAILED
                    channelList.lastErrorMessage = describeLoadError(loadMoreError)
                    notifyListeners()
                }
            }
        }
    }

    // TODO: Validate
    fun searchChannels(searchQuery: String, guideScope: GuideScope) {
        val newSearchChannelList = channelSearchAndShowFilter.replaceSearch(searchQuery, guideScope)
        if (newSearchChannelList == null) notifyListeners() else loadMoreChannels(newSearchChannelList)
    }

    // TODO: Validate
    fun filterChannelsByShow(titleId: String, showName: String) {
        loadMoreChannels(
            channelSearchAndShowFilter.replaceShowFilter(titleId, showName, guideScopesToLoad(localAppStorage.isSignedIn)),
        )
    }

    // TODO: Validate
    fun clearShowFilter() {
        channelSearchAndShowFilter.clearShowFilter()
        notifyListeners()
    }


    // TODO: Validate
    private fun guideScopesToLoad(isSignedIn: Boolean): List<GuideScope> =
        if (isSignedIn) listOf(GuideScope.FAVORITES, GuideScope.OWNED, GuideScope.PUBLIC) else listOf(GuideScope.PUBLIC)


    // TODO: Validate
    private fun orderSeedsToSend(): OrderSeeds? =
        if (localAppStorage.isSignedIn) null else localAppStorage.loadLocalSeeds()

    // TODO: Validate
    fun resortChannels(channelIds: List<String>) {
        val isSignedIn = localAppStorage.isSignedIn
        runChannelEpisodesAction(channelIds, ChannelEpisodesAction.RESORT) { channelsToResort ->
            if (isSignedIn) {
                api.advanceChannelEpisodeSeeds(channelsToResort.map { it.id })
                channelsToResort.associate { channel -> channel.id to null }
            } else {
                val orderSeeds = localAppStorage.loadLocalSeeds()
                channelsToResort.associate { channel ->
                    val rotatedEpisodeSeed = orderSeeds.channelEpisodeSeed(channel.id) + 1
                    localAppStorage.saveLocalChannelEpisodeSeed(channel.id, rotatedEpisodeSeed)
                    channel.id to rotatedEpisodeSeed
                }
            }
        }
    }

    // TODO: Validate
    fun checkChannelsForNewEpisodes(channelIds: List<String>) {
        runChannelEpisodesAction(channelIds, ChannelEpisodesAction.CHECK_FOR_NEW_EPISODES) { channelsToCheck ->
            api.forgetStoredEpisodeOrders(channelsToCheck.map { it.id })
            val sentOrderSeeds = orderSeedsToSend()
            channelsToCheck.associate { channel -> channel.id to sentOrderSeeds?.channelEpisodeSeed(channel.id) }
        }
    }

    // TODO: Validate
    suspend fun resortAllChannels(): String? =
        changeOrderThenRefresh("resort every channel") {
            if (localAppStorage.isSignedIn) {
                api.advanceEveryChannelEpisodeSeed()
            } else {
                localAppStorage.advanceLocalMinimumEpisodeSeed()
            }
        }

    // TODO: Validate
    suspend fun refreshChannelOrder(): String? {
        val isSignedIn = localAppStorage.isSignedIn
        return changeOrderThenRefresh("refresh the channel order") {
            if (isSignedIn) api.advanceChannelOrderSeed() else localAppStorage.advanceLocalChannelOrderSeed()
        }
    }

    // TODO: Validate
    suspend fun checkAllChannelsForNewEpisodes(): String? =
        changeOrderThenRefresh("check every channel for new episodes") {
            api.forgetStoredEpisodeOrders(null)
        }

    // TODO: Validate
    private fun runChannelEpisodesAction(
        channelIds: List<String>,
        action: ChannelEpisodesAction,
        prepareChannelIdToEpisodeSeed: (List<GuideChannel>) -> Map<String, Int?>,
    ) {
        val channelsForAction = channelIds
            .mapNotNull { channelsById[it] }
            .filter { it.id !in channelIdToRunningAction }
        if (channelsForAction.isEmpty()) return
        for (channel in channelsForAction) {
            channelIdToRunningAction[channel.id] = action
            channelIdToFailedAction.remove(channel.id)
        }
        notifyListeners()
        val watchedEpisodeIds = localAppStorage.readWatchedEpisodeIds()
        mainThreadScope.launch {
            val channelIdToEpisodeOrderSeed = try {
                runOnIoThreadHoldingCoordinatorMutex { prepareChannelIdToEpisodeSeed(channelsForAction) }
            } catch (seedError: Exception) {
                Log.w(TAG, "Couldn't prepare the channels' episode seeds", seedError)
                for (channel in channelsForAction) {
                    channelIdToRunningAction.remove(channel.id)
                    channelIdToFailedAction[channel.id] = action
                }
                notifyListeners()
                return@launch
            }
            for (channel in channelsForAction) {
                launch {
                    try {
                        val reloadedVideosPage = withContext(channelFetchDispatcher) {
                            api.fetchChannelVideos(channel.id, channelIdToEpisodeOrderSeed.getValue(channel.id))
                        }
                        channelsById[channel.id]?.let { currentChannel ->
                            channelsById[channel.id] = currentChannel.copy(
                                videos = withLocallyWatchedVideos(reloadedVideosPage.videos, watchedEpisodeIds),
                                hasMoreVideos = reloadedVideosPage.hasMoreVideos,
                            )
                            scheduleGuideCacheSave()
                        }
                    } catch (reloadError: Exception) {
                        Log.w(TAG, "Couldn't reload ${channel.id}'s episodes", reloadError)
                        channelIdToFailedAction[channel.id] = action
                    }
                    channelIdToRunningAction.remove(channel.id)
                    notifyListeners()
                }
            }
        }
    }

    // TODO: Validate
    fun loadMoreVideos(channelId: String) {
        val channel = channelsById[channelId] ?: return
        if (!channel.hasMoreVideos || channelId in channelIdToRunningAction) return
        channelIdToRunningAction[channelId] = ChannelEpisodesAction.LOAD_MORE_EPISODES
        channelIdToFailedAction.remove(channelId)
        notifyListeners()
        val generation = refreshGeneration
        val watchedEpisodeIds = localAppStorage.readWatchedEpisodeIds()
        val loadedVideoCount = channel.videos.size
        mainThreadScope.launch {
            val nextVideosPage = try {
                runOnIoThreadHoldingCoordinatorMutex {
                    api.fetchChannelVideos(
                        channel.id,
                        orderSeedsToSend()?.channelEpisodeSeed(channel.id),
                        loadedVideoCount,
                    )
                }
            } catch (loadMoreError: Exception) {
                Log.w(TAG, "Couldn't load more of $channelId's episodes", loadMoreError)
                channelIdToRunningAction.remove(channelId, ChannelEpisodesAction.LOAD_MORE_EPISODES)
                channelIdToFailedAction[channelId] = ChannelEpisodesAction.LOAD_MORE_EPISODES
                notifyListeners()
                return@launch
            }
            channelIdToRunningAction.remove(channelId, ChannelEpisodesAction.LOAD_MORE_EPISODES)
            if (generation != refreshGeneration) return@launch
            channelsById[channelId]?.takeIf { it.videos.size == loadedVideoCount }?.let { currentChannel ->
                val loadedEpisodeIds = currentChannel.videos.map { it.episodeId }.toSet()
                channelsById[channelId] = currentChannel.copy(
                    videos = currentChannel.videos + withLocallyWatchedVideos(
                        nextVideosPage.videos.filter { it.episodeId !in loadedEpisodeIds },
                        watchedEpisodeIds,
                    ),
                    hasMoreVideos = nextVideosPage.hasMoreVideos,
                )
                scheduleGuideCacheSave()
            }
            notifyListeners()
        }
    }

    // TODO: Validate
    suspend fun markVideoWatched(episodeId: String, channelId: String): String? {
        localAppStorage.rememberWatchedEpisode(episodeId)
        updateVideoInEveryChannel(episodeId) { it.copy(watched = true) }
        if (!localAppStorage.isSignedIn) return null
        return runOutlivingCaller {
            val incompleteWatchId = try {
                withContext(backgroundWriteDispatcher) {
                    api.fetchIncompleteWatchId(episodeId) ?: api.markEpisodeWatched(episodeId, completed = false, channelId)
                }
            } catch (markWatchedError: Exception) {
                Log.w(TAG, "Couldn't mark $episodeId watched", markWatchedError)
                if (isSignInRejectedError(markWatchedError)) {
                    isSignInExpired = true
                    notifyListeners()
                }
                return@runOutlivingCaller "Couldn't mark the last video watched on your account: ${describeLoadError(markWatchedError)}."
            }
            updateVideoInEveryChannel(episodeId) {
                it.copy(watchCompleted = false, watchId = incompleteWatchId)
            }
            null
        }
    }

    // TODO: Validate
    suspend fun markVideoStartedOrCompleted(
        episodeId: String,
        completed: Boolean,
        channelId: String,
    ): String? = runOutlivingCaller {
        val createdWatchId = try {
            withContext(backgroundWriteDispatcher) { api.markEpisodeWatched(episodeId, completed, channelId) }
        } catch (createWatchError: Exception) {
            val attemptedWatchStatus = if (completed) "completed" else "started"
            return@runOutlivingCaller "Couldn't mark the episode $attemptedWatchStatus: ${createWatchError.message}"
        }
        localAppStorage.rememberWatchedEpisode(episodeId)
        updateVideoInEveryChannel(episodeId) {
            it.copy(watched = true, watchCompleted = completed, watchId = createdWatchId)
        }
        null
    }

    // TODO: Validate
    suspend fun completeStartedVideo(startedVideo: GuideVideo, channelId: String): String? = runOutlivingCaller {
        val completedWatchId = try {
            withContext(backgroundWriteDispatcher) {
                val incompleteWatchId = startedVideo.watchId ?: api.fetchIncompleteWatchId(startedVideo.episodeId)
                if (incompleteWatchId == null) {
                    api.markEpisodeWatched(startedVideo.episodeId, completed = true, channelId)
                } else {
                    api.completeWatch(incompleteWatchId)
                    incompleteWatchId
                }
            }
        } catch (completeWatchError: Exception) {
            return@runOutlivingCaller "Couldn't mark the episode completed: ${completeWatchError.message}"
        }
        updateVideoInEveryChannel(startedVideo.episodeId) {
            it.copy(watched = true, watchCompleted = true, watchId = completedWatchId)
        }
        null
    }

    // TODO: Validate
    suspend fun deleteStartedVideoWatch(startedVideo: GuideVideo): String? = runOutlivingCaller {
        try {
            withContext(backgroundWriteDispatcher) {
                val incompleteWatchId = startedVideo.watchId ?: api.fetchIncompleteWatchId(startedVideo.episodeId)
                if (incompleteWatchId != null) api.deleteWatch(incompleteWatchId)
            }
        } catch (deleteWatchError: Exception) {
            return@runOutlivingCaller "Couldn't delete the watch: ${deleteWatchError.message}"
        }
        localAppStorage.forgetWatchedEpisode(startedVideo.episodeId)
        updateVideoInEveryChannel(startedVideo.episodeId) {
            it.copy(watched = false, watchCompleted = false, watchId = null)
        }
        null
    }


    // TODO: Validate
    suspend fun setChannelFavorite(channelId: String, shouldBeFavorite: Boolean): String? = runOutlivingCaller {
        try {
            withContext(backgroundWriteDispatcher) {
                if (shouldBeFavorite) api.favoriteChannel(channelId) else api.unfavoriteChannel(channelId)
            }
        } catch (favoriteChangeError: Exception) {
            val failedFavoriteChange = if (shouldBeFavorite) "add the channel to" else "remove the channel from"
            return@runOutlivingCaller "Couldn't $failedFavoriteChange your favorites: ${favoriteChangeError.message}"
        }
        favoriteChannelIds =
            if (shouldBeFavorite) favoriteChannelIds + channelId else favoriteChannelIds - channelId
        guideScopeToChannelList[GuideScope.FAVORITES]?.channelIds?.let { favoriteListChannelIds ->
            if (shouldBeFavorite) favoriteListChannelIds.add(channelId) else favoriteListChannelIds.remove(channelId)
        }
        scheduleGuideCacheSave()
        notifyListeners()
        null
    }

    // TODO: Validate
    private fun updateVideoInEveryChannel(episodeId: String, updateMatchingVideo: (GuideVideo) -> GuideVideo) {
        var didChangeAnyVideo = false
        for ((channelId, channel) in channelsById.entries.toList()) {
            val updatedVideos = channel.videos.map { video ->
                if (video.episodeId == episodeId) updateMatchingVideo(video) else video
            }
            if (updatedVideos == channel.videos) continue
            channelsById[channelId] = channel.copy(videos = updatedVideos)
            didChangeAnyVideo = true
        }
        if (didChangeAnyVideo) {
            scheduleGuideCacheSave()
            notifyListeners()
        }
    }


    // TODO: Validate
    private suspend fun fetchChannelsWithVideos(
        listItems: List<ChannelListItem>,
        sentOrderSeeds: OrderSeeds?,
        watchedEpisodeIds: Set<String>,
        onChannelLoaded: (GuideChannel) -> Unit,
    ): ChannelsLoad = supervisorScope {
        val channelDeferreds = listItems.map { listItem ->
            async {
                val loadedChannel = withContext(channelFetchDispatcher) {
                    val channelEpisodeSeed = sentOrderSeeds?.channelEpisodeSeed(listItem.id)
                    val channelVideosPage = listItem.bundledVideosPage
                        ?.takeIf { sentOrderSeeds == null || channelEpisodeSeed == SIGNED_OUT_BUNDLED_EPISODE_SEED }
                        ?: api.fetchChannelVideos(listItem.id, channelEpisodeSeed)
                    GuideChannel(
                        id = listItem.id,
                        name = listItem.customName ?: listItem.name,
                        number = listItem.customChannelNumber ?: listItem.channelNumber,
                        defaultOrder = listItem.defaultOrder,
                        videos = withLocallyWatchedVideos(channelVideosPage.videos, watchedEpisodeIds),
                        imageUrl = listItem.imageId
                            ?.takeIf { listItem.customName == null }
                            ?.let { api.channelImageUrl(it) },
                        hasMoreVideos = channelVideosPage.hasMoreVideos,
                        authorUsername = listItem.authorUsername,
                    )
                }
                if (loadedChannel.videos.isNotEmpty()) onChannelLoaded(loadedChannel)
                loadedChannel
            }
        }
        val failedListItemToError = LinkedHashMap<ChannelListItem, Throwable>()
        val loadedChannels = channelDeferreds.zip(listItems).mapNotNull { (channelDeferred, listItem) ->
            runCatching { channelDeferred.await() }
                .onFailure { channelError ->
                    Log.w(TAG, "Couldn't load ${listItem.id}'s videos", channelError)
                    failedListItemToError[listItem] = channelError
                }
                .getOrNull()
                ?.takeIf { it.videos.isNotEmpty() }
        }
        ChannelsLoad(loadedChannels, failedListItemToError)
    }

    // TODO: Validate
    private class ChannelsLoad(
        val loadedChannels: List<GuideChannel>,
        val failedListItemToError: Map<ChannelListItem, Throwable>,
    ) {
        // TODO: Validate
        val failureProblem: String?
            get() {
                if (failedListItemToError.isEmpty()) return null
                val failedChannelNames = failedListItemToError.keys.map { it.customName ?: it.name ?: "Untitled channel" }
                val shownFailedChannelNames = failedChannelNames.take(SHOWN_FAILED_CHANNEL_NAMES).joinToString(", ") +
                        if (failedChannelNames.size > SHOWN_FAILED_CHANNEL_NAMES) " and ${failedChannelNames.size - SHOWN_FAILED_CHANNEL_NAMES} more" else ""
                val firstChannelError = failedListItemToError.values.first()
                return "Couldn't load ${failedChannelNames.size} channel${if (failedChannelNames.size == 1) "" else "s"} " +
                        "($shownFailedChannelNames): ${describeLoadError(firstChannelError)}."
            }
    }

    // TODO: Validate
    private fun withLocallyWatchedVideos(
        videos: List<GuideVideo>,
        watchedEpisodeIds: Set<String>,
    ): List<GuideVideo> = videos.map { video ->
        if (video.episodeId in watchedEpisodeIds) video.copy(watched = true) else video
    }

    // TODO: Validate
    private fun notifyListeners() {
        for (listener in listeners.toList()) listener.onGuideChanged()
    }

    // TODO: Validate
    private fun scheduleGuideCacheSave() {
        guideCache.scheduleCachedGuideSave {
            GuideCache.CachedGuide(
                channels = channelsById.values.toList(),
                favoriteChannelIds = favoriteChannelIds,
                guideScopeToChannelIds = guideScopeToChannelList.mapValues { (_, guideChannelList) ->
                    guideChannelList.channelIds.toList()
                },
                updatedAt = updatedAt,
            )
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "GuideRepository"
        // TODO: Validate
        const val CHANNEL_FETCH_THREADS = 6
        // TODO: Validate
        const val CHANNEL_PAGE_SIZE = 20

        // TODO: Validate
        const val CHANNEL_PAGE_ATTEMPTS = 8
        // TODO: Validate
        const val SHOWN_FAILED_CHANNEL_NAMES = 3
        // TODO: Validate
        const val SIGNED_OUT_BUNDLED_EPISODE_SEED = 0
    }
}
