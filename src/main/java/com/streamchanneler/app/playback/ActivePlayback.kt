// TODO: Validate
package com.streamchanneler.app.playback

import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.optNullableString
import com.streamchanneler.app.data.toJsonObjectList
import org.json.JSONArray
import org.json.JSONObject

// TODO: Validate
data class ActivePlayback(
    val channelId: String,
    val channelName: String?,
    val channelVideos: List<GuideVideo>,
    val playingEpisodeId: String,
    val targetPackageName: String?,
    val launchedAt: Long,
    val isMonitoring: Boolean,
) {
    // TODO: Validate
    val playingVideo: GuideVideo?
        get() = channelVideos.firstOrNull { it.episodeId == playingEpisodeId }

    // TODO: Validate
    fun toJson(): JSONObject = JSONObject()
        .put("channelId", channelId)
        .put("channelName", channelName ?: JSONObject.NULL)
        .put("channelVideos", JSONArray(channelVideos.map { it.toJson() }))
        .put("playingEpisodeId", playingEpisodeId)
        .put("targetPackageName", targetPackageName ?: JSONObject.NULL)
        .put("launchedAtMillis", launchedAt)
        .put("isMonitoring", isMonitoring)

    // TODO: Validate
    companion object {
        // TODO: Validate
        fun fromJson(activePlaybackJson: JSONObject): ActivePlayback = ActivePlayback(
            channelId = activePlaybackJson.getString("channelId"),
            channelName = activePlaybackJson.optNullableString("channelName"),
            channelVideos = activePlaybackJson.getJSONArray("channelVideos").toJsonObjectList().map(GuideVideo::fromJson),
            playingEpisodeId = activePlaybackJson.getString("playingEpisodeId"),
            targetPackageName = activePlaybackJson.optNullableString("targetPackageName"),
            launchedAt = activePlaybackJson.getLong("launchedAtMillis"),
            isMonitoring = activePlaybackJson.getBoolean("isMonitoring"),
        )
    }
}
