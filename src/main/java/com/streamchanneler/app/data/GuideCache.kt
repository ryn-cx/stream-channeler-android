// TODO: Validate
package com.streamchanneler.app.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// TODO: Validate
class GuideCache(
    private val guideCacheFile: File,
    private val mainThreadScope: CoroutineScope,
    private val backgroundWriteDispatcher: CoroutineDispatcher,
) {
    // TODO: Validate
    data class CachedGuide(
        val channels: List<GuideChannel>,
        val favoriteChannelIds: Set<String>,
        val guideScopeToChannelIds: Map<GuideScope, List<String>>,
        val updatedAt: Long,
    )

    // TODO: Validate
    private var pendingGuideCacheSaveJob: Job? = null

    // TODO: Validate
    fun readCachedGuide(): CachedGuide? {
        if (!guideCacheFile.exists()) return null

        val guideCacheJson = JSONObject(guideCacheFile.readText())
        val channelsJson = guideCacheJson.getJSONArray("channels")
        val favoriteChannelIdsJson = guideCacheJson.getJSONArray("favoriteChannelIds")
        val guideScopeToChannelIdsJson = guideCacheJson.getJSONObject("guideScopeToChannelIds")

        return CachedGuide(
            channels = channelsJson.toJsonObjectList().map(GuideChannel::fromJson),
            favoriteChannelIds = favoriteChannelIdsJson.toStringList().toSet(),
            guideScopeToChannelIds = GuideScope.entries.associateWith { guideScope ->
                guideScopeToChannelIdsJson.optJSONArray(guideScope.name)?.toStringList().orEmpty()
            },
            updatedAt = guideCacheJson.getLong("updatedAtMillis"),
        )
    }

    // TODO: Validate
    fun scheduleCachedGuideSave(currentCachedGuide: () -> CachedGuide) {
        pendingGuideCacheSaveJob?.cancel()
        pendingGuideCacheSaveJob = mainThreadScope.launch {
            delay(CACHE_SAVE_DELAY)
            writeCachedGuideToFile(currentCachedGuide())
        }
    }

    // TODO: Validate
    fun cancelPendingSaveAndDelete() {
        pendingGuideCacheSaveJob?.cancel()
        guideCacheFile.delete()
    }

    // TODO: Validate
    private fun writeCachedGuideToFile(cachedGuide: CachedGuide) {
        // Turning the whole guide into JSON text takes long enough to drop frames, so it runs off the main thread.
        mainThreadScope.launch(backgroundWriteDispatcher) {
            runCatching {
                val guideScopeToChannelIdsJson = JSONObject()
                for ((guideScope, channelIds) in cachedGuide.guideScopeToChannelIds) {
                    guideScopeToChannelIdsJson.put(guideScope.name, JSONArray(channelIds))
                }
                val guideCacheJson = JSONObject()
                    .put("channels", JSONArray(cachedGuide.channels.map { it.toJson() }))
                    .put("favoriteChannelIds", JSONArray(cachedGuide.favoriteChannelIds.toList()))
                    .put("guideScopeToChannelIds", guideScopeToChannelIdsJson)
                    .put("updatedAtMillis", cachedGuide.updatedAt)
                    .toString()
                guideCacheFile.writeText(guideCacheJson)
            }
                .onFailure { Log.w(TAG, "Couldn't save the guide cache", it) }
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "GuideCache"
        // TODO: Validate
        const val CACHE_SAVE_DELAY = 1000L
    }
}
