// TODO: Validate
package com.streamchanneler.app.data

import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

/** Flattened episode information from the API. */
data class GuideVideo(
    val episodeId: String,
    val title: String?,
    val subtitle: String,
    val url: String?,
    val episodeKey: String?,
    val duration: Double?,
    val thumbnailUrl: String?,
    val faviconUrl: String?,
    val pluginKey: String,
    val watched: Boolean,
    val watchCompleted: Boolean = false,
    val watchId: String? = null,
    val titleId: String,
    val description: String? = null,
    val sourceKey: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("episode_id", episodeId)
        .put("title", title ?: JSONObject.NULL)
        .put("subtitle", subtitle)
        .put("url", url)
        .put("episode_key", episodeKey ?: JSONObject.NULL)
        .put("duration", duration ?: JSONObject.NULL)
        .put("thumbnail_url", thumbnailUrl ?: JSONObject.NULL)
        .put("favicon_url", faviconUrl ?: JSONObject.NULL)
        .put("plugin_key", pluginKey)
        .put("watched", watched)
        .put("watch_completed", watchCompleted)
        .put("watch_id", watchId ?: JSONObject.NULL)
        .put("title_id", titleId)
        .put("description", description ?: JSONObject.NULL)
        .put("source_key", sourceKey)

    companion object {
        fun fromJson(videoJson: JSONObject): GuideVideo = GuideVideo(
            episodeId = videoJson.getString("episode_id"),
            title = videoJson.optNullableString("title"),
            subtitle = videoJson.getString("subtitle"),
            url = videoJson.optNullableString("url"),
            episodeKey = videoJson.optNullableString("episode_key"),
            duration = videoJson.optNullableDouble("duration"),
            thumbnailUrl = videoJson.optNullableString("thumbnail_url"),
            faviconUrl = videoJson.optNullableString("favicon_url"),
            pluginKey = videoJson.getString("plugin_key"),
            watched = videoJson.getBoolean("watched"),
            watchCompleted = videoJson.getBoolean("watch_completed"),
            watchId = videoJson.optNullableString("watch_id"),
            description = videoJson.optNullableString("description"),
            titleId = videoJson.getString("title_id"),
            sourceKey = videoJson.getString("source_key"),
        )
    }
}

/** Flattened channel information from the API. */
data class GuideChannel(
    val id: String,
    val name: String?,
    val number: Double?,
    val defaultOrder: String?,
    val videos: List<GuideVideo>,
    val imageUrl: String? = null,
    val hasMoreVideos: Boolean = false,
    val authorUsername: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name ?: JSONObject.NULL)
        .put("number", number ?: JSONObject.NULL)
        .put("default_order", defaultOrder ?: JSONObject.NULL)
        .put("videos", JSONArray(videos.map { it.toJson() }))
        .put("image_url", imageUrl ?: JSONObject.NULL)
        .put("has_more_videos", hasMoreVideos)
        .put("author_username", authorUsername ?: JSONObject.NULL)

    companion object {
        fun fromJson(channelJson: JSONObject): GuideChannel = GuideChannel(
            id = channelJson.getString("id"),
            name = channelJson.optNullableString("name"),
            number = channelJson.optNullableDouble("number"),
            defaultOrder = channelJson.optNullableString("default_order"),
            videos = channelJson.getJSONArray("videos").toJsonObjectList().map(GuideVideo::fromJson),
            imageUrl = channelJson.optNullableString("image_url"),
            hasMoreVideos = channelJson.getBoolean("has_more_videos"),
            authorUsername = channelJson.optNullableString("author_username"),
        )
    }
}

// TODO: Validate
data class GuideVideosPage(
    val videos: List<GuideVideo>,
    val hasMoreVideos: Boolean,
)


// TODO: Validate
data class ChannelListItem(
    val id: String,
    val name: String?,
    val channelNumber: Double?,
    val defaultOrder: String?,
    val customName: String?,
    val customChannelNumber: Double?,
    val imageId: String?,
    val authorUsername: String?,
    val bundledVideosPage: GuideVideosPage?,
)

// TODO: Validate
data class OrderSeeds(
    val minimumEpisodeOrderSeed: Int,
    val channelOrderSeed: Int,
    val channelIdToEpisodeOrderSeed: Map<String, Int>,
) {
    // TODO: Validate
    fun channelEpisodeSeed(channelId: String): Int =
        maxOf(channelIdToEpisodeOrderSeed[channelId] ?: 0, minimumEpisodeOrderSeed)
}

// TODO: Validate
data class SignedInUser(
    val username: String,
    val userJson: JSONObject,
)

// TODO: Validate
data class DeviceSignInCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val pollInterval: Int,
)

// TODO: Validate
data class ChannelRefreshSettings(
    val refreshesEpisodesAutomatically: Boolean?,
    val shufflesEpisodesAutomatically: Boolean?,
) {
    // TODO: Validate
    val followsAccountDefaults: Boolean
        get() = refreshesEpisodesAutomatically == null && shufflesEpisodesAutomatically == null

    // TODO: Validate
    fun toJson(): JSONObject = JSONObject()
        .put("refreshes_episodes_automatically", refreshesEpisodesAutomatically ?: JSONObject.NULL)
        .put("shuffles_episodes_automatically", shufflesEpisodesAutomatically ?: JSONObject.NULL)

    // TODO: Validate
    companion object {
        // TODO: Validate
        fun fromJson(channelRefreshSettingsJson: JSONObject): ChannelRefreshSettings = ChannelRefreshSettings(
            refreshesEpisodesAutomatically = channelRefreshSettingsJson.optNullableBoolean("refreshes_episodes_automatically"),
            shufflesEpisodesAutomatically = channelRefreshSettingsJson.optNullableBoolean("shuffles_episodes_automatically"),
        )
    }
}

// TODO: Validate
data class AccountRefreshSettings(
    private val refreshesChannelsAutomatically: Boolean,
    val refreshesEpisodesAutomatically: Boolean,
    val shufflesEpisodesAutomatically: Boolean,
    val channelIdToRefreshSettings: Map<String, ChannelRefreshSettings>,
) {
    // TODO: Validate
    val hasAutomaticSettings: Boolean
        get() = refreshesChannelsAutomatically ||
            refreshesEpisodesAutomatically ||
            shufflesEpisodesAutomatically ||
            channelIdToRefreshSettings.values.any {
                it.refreshesEpisodesAutomatically == true || it.shufflesEpisodesAutomatically == true
            }

    // TODO: Validate
    companion object {
        // TODO: Validate
        fun fromJson(userJson: JSONObject): AccountRefreshSettings {
            val channelIdToRefreshSettingsJson = userJson.getJSONObject("channel_id_to_refresh_settings")
            return AccountRefreshSettings(
                refreshesChannelsAutomatically = userJson.getBoolean("refreshes_channels_automatically"),
                refreshesEpisodesAutomatically = userJson.getBoolean("refreshes_episodes_automatically"),
                shufflesEpisodesAutomatically = userJson.getBoolean("shuffles_episodes_automatically"),
                channelIdToRefreshSettings = channelIdToRefreshSettingsJson.keys().asSequence().associateWith { channelId ->
                    ChannelRefreshSettings.fromJson(channelIdToRefreshSettingsJson.getJSONObject(channelId))
                },
            )
        }
    }
}

// TODO: Validate
data class AutomaticRefreshResult(
    val refreshedChannels: Boolean,
    val changedEveryChannelsEpisodes: Boolean,
    val changedEpisodesChannelIds: List<String>,
)

// TODO: Validate
data class SourcePreference(
    val sourceKey: String,
    val enabled: Boolean,
    val episodeCount: Int,
)

// TODO: Validate
fun List<SourcePreference>.toSourcePreferencesJson(): JSONArray = JSONArray(
    map { sourcePreference ->
        JSONObject().put("source_key", sourcePreference.sourceKey).put("enabled", sourcePreference.enabled)
    },
)

// TODO: Validate
enum class ChannelEpisodesAction {
    RESORT,
    CHECK_FOR_NEW_EPISODES,
    LOAD_MORE_EPISODES,
}

// TODO: Validate
enum class AfterVideoEnds {
    PLAY_NEXT_VIDEO,
    RETURN_TO_GUIDE,
}

// TODO: Validate
enum class GuideScope(val queryValue: String) {
    PUBLIC("public"),
    FAVORITES("favorites"),
    OWNED("owned"),
}

// TODO: Validate
fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

// TODO: Validate
fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

// TODO: Validate
private fun JSONObject.optNullableBoolean(key: String): Boolean? =
    if (isNull(key)) null else optBoolean(key)

// TODO: Validate
fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }

// TODO: Validate
fun JSONArray.toJsonObjectList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

// TODO: Validate
fun formatNumberWithoutTrailingZero(number: Double?): String? = when {
    number == null -> null
    number == floor(number) -> number.toLong().toString()
    else -> number.toString()
}

// TODO: Validate
fun nextPlayableVideo(channel: GuideChannel, afterEpisodeId: String?): GuideVideo? {
    val playingVideoIndex = channel.videos.indexOfFirst { it.episodeId == afterEpisodeId }
    return channel.videos
        .drop(playingVideoIndex + 1)
        .firstOrNull { it.episodeId != afterEpisodeId && it.url != null }
}
