// TODO: Validate
package com.streamchanneler.app.data

import com.streamchanneler.app.BuildConfig
import com.streamchanneler.app.streamingapps.findStreamingSite
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

// TODO: Validate
class ApiException(
    val statusCode: Int,
    message: String,
    val isSignInRejected: Boolean = false,
) : IOException(message)

// TODO: Validate
class StreamChannelerApi(
    private val readAccessToken: () -> String?,
    private val readLocalSourcePreferencesJson: () -> String?,
) {

    // TODO: Validate
    fun logIn(usernameOrEmail: String, password: String): String {
        val formBody =
            encodedKeyValuePairs(listOf("grant_type" to "password", "username" to usernameOrEmail, "password" to password))
        val responseText = sendApiRequest(
            method = "POST",
            url = apiUrl("/login/access-token"),
            authorization = AuthorizationMode.NONE,
            body = formBody,
            contentType = "application/x-www-form-urlencoded",
        )
        return JSONObject(responseText).getString("access_token")
    }

    // TODO: Validate
    fun requestDeviceSignInCode(): DeviceSignInCode {
        val deviceCodeJson = JSONObject(sendApiRequest("POST", apiUrl("/login/device-code"), AuthorizationMode.NONE))
        return DeviceSignInCode(
            deviceCode = deviceCodeJson.getString("device_code"),
            userCode = deviceCodeJson.getString("user_code"),
            verificationUri = deviceCodeJson.getString("verification_uri"),
            expiresIn = deviceCodeJson.getInt("expires_in"),
            pollInterval = deviceCodeJson.getInt("interval"),
        )
    }

    // TODO: Validate
    fun exchangeDeviceSignInCode(deviceCode: String): String? {
        val deviceTokenJson = JSONObject(
            sendApiRequest(
                method = "POST",
                url = apiUrl("/login/device-token"),
                authorization = AuthorizationMode.NONE,
                body = JSONObject().put("device_code", deviceCode).toString(),
            ),
        )
        return deviceTokenJson.optNullableString("access_token")
    }

    // TODO: Validate
    fun signOutDevice(signedOutAccessToken: String) {
        sendHttpRequestAndReadBody("DELETE", apiUrl("/users/me/devices/current"), signedOutAccessToken, null, "application/json")
    }

    // TODO: Validate
    fun fetchSignedInUser(): SignedInUser {
        val userJson = JSONObject(sendApiRequest("GET", apiUrl("/users/me"), AuthorizationMode.REQUIRED))
        return SignedInUser(
            username = userJson.getString("username"),
            userJson = userJson,
        )
    }

    // TODO: Validate
    fun updateChannelRefreshSettings(channelId: String, channelRefreshSettings: ChannelRefreshSettings) {
        sendApiRequest(
            method = "PUT",
            url = apiUrl("/users/me/channel-refresh-settings/${urlEncoded(channelId)}"),
            authorization = AuthorizationMode.REQUIRED,
            body = channelRefreshSettings.toJson().toString(),
        )
    }

    // TODO: Validate
    fun updatePluginProfile(
        pluginKey: String,
        profileName: String?,
        profileNumber: Int?,
        profilePin: String?,
    ): JSONObject {
        val pluginProfileJson = JSONObject()
            .put("profile_name", profileName ?: JSONObject.NULL)
            .put("profile_number", profileNumber ?: JSONObject.NULL)
            .put("pin", profilePin ?: JSONObject.NULL)
        val updatedPreferencesJson = JSONObject(
            sendApiRequest(
                method = "PUT",
                url = apiUrl("/users/me/preferences"),
                authorization = AuthorizationMode.REQUIRED,
                body = JSONObject()
                    .put("plugin_key_to_profile", JSONObject().put(pluginKey, pluginProfileJson))
                    .toString(),
            ),
        )
        return updatedPreferencesJson.getJSONObject("user").getJSONObject("plugin_key_to_profile")
    }

    // TODO: Validate
    fun fetchSourcePreferences(fetchesSavedDefault: Boolean): List<SourcePreference> {
        val query = if (fetchesSavedDefault) listOf("saved_default" to "true") else emptyList()
        val sourcePreferencesJson = JSONArray(
            sendApiRequest("GET", apiUrl("/users/me/source-preferences", query), AuthorizationMode.REQUIRED),
        )
        return sourcePreferencesJson.toJsonObjectList().map { sourcePreferenceJson ->
            SourcePreference(
                sourceKey = sourcePreferenceJson.getString("source_key"),
                enabled = sourcePreferenceJson.getBoolean("enabled"),
                episodeCount = sourcePreferenceJson.getInt("episode_count"),
            )
        }
    }

    // TODO: Validate
    fun updateAccountSourcePreferences(sourcePreferencesJson: JSONArray) {
        sendApiRequest(
            method = "PUT",
            url = apiUrl("/users/me/preferences"),
            authorization = AuthorizationMode.REQUIRED,
            body = JSONObject().put("source_preferences", sourcePreferencesJson).toString(),
        )
    }

    // TODO: Validate
    fun runAutomaticRefresh(): AutomaticRefreshResult {
        val automaticRefreshJson = JSONObject(
            sendApiRequest(
                "POST",
                apiUrl("/users/me/automatic-refresh", listOf("opens_channel_list" to "true")),
                AuthorizationMode.REQUIRED,
            ),
        )
        return AutomaticRefreshResult(
            refreshedChannels = automaticRefreshJson.getBoolean("refreshed_channels"),
            changedEveryChannelsEpisodes = automaticRefreshJson.getBoolean("changed_every_channels_episodes"),
            changedEpisodesChannelIds = automaticRefreshJson.getJSONArray("changed_episodes_channel_ids").toStringList(),
        )
    }

    // TODO: Validate
    fun refreshAndShuffleAll() {
        sendApiRequest("POST", apiUrl("/users/me/refresh-and-shuffle-all"), AuthorizationMode.REQUIRED)
    }

    // TODO: Validate
    fun fetchChannelList(
        scope: GuideScope,
        offset: Int,
        limit: Int,
        channelOrderSeed: Int?,
        filterOptions: JSONArray = JSONArray(),
        titleId: String? = null,
        runsAutomaticRefresh: Boolean = false,
    ): List<ChannelListItem> {
        val minimumTitleAndEpisodeCount = if (scope == GuideScope.PUBLIC) "1" else "0"
        val query = listOfNotNull(
            "scope" to scope.queryValue,
            "offset" to offset.toString(),
            "limit" to limit.toString(),
            "min_title_count" to minimumTitleAndEpisodeCount,
            "min_episode_count" to minimumTitleAndEpisodeCount,
            channelOrderSeed?.let { "random_seed" to it.toString() },
            "sort_options" to JSONArray().toString(),
            "filter_options" to filterOptions.toString(),
            titleId?.let { "title_id" to it },
        ) + if (runsAutomaticRefresh) {
            listOf("runs_automatic_refresh" to "true", "automatic_refresh_opens_channel_list" to "true")
        } else {
            emptyList()
        }
        val authorization =
            if (scope == GuideScope.PUBLIC) AuthorizationMode.OPTIONAL else AuthorizationMode.REQUIRED
        val responseJson = JSONObject(sendApiRequest("GET", apiUrl("/channels/with-episodes", query), authorization))
        val channelIdToEpisodesJson = responseJson.getJSONObject("episodes")
        return responseJson.getJSONArray("data").toJsonObjectList().map { channelJson ->
            parseChannelListItem(channelJson, channelIdToEpisodesJson.optJSONObject(channelJson.getString("id")))
        }
    }

    // TODO: Validate
    private fun parseChannelListItem(channelJson: JSONObject, channelEpisodesJson: JSONObject?): ChannelListItem =
        ChannelListItem(
            id = channelJson.getString("id"),
            name = channelJson.optNullableString("name"),
            channelNumber = channelJson.optNullableDouble("channel_number"),
            defaultOrder = channelJson.optNullableString("default_order"),
            customName = channelJson.optNullableString("custom_name"),
            customChannelNumber = channelJson.optNullableDouble("custom_channel_number"),
            imageId = channelJson.optNullableString("image_id"),
            authorUsername = channelJson.optNullableString("username"),
            bundledVideosPage = channelEpisodesJson?.let(::parseGuideVideosPage),
        )

    // TODO: Validate
    fun fetchChannelVideos(
        channelId: String,
        episodeSeed: Int?,
        videoOffset: Int = 0,
    ): GuideVideosPage {
        val query = listOfNotNull(
            "limit" to GUIDE_VIDEO_LIMIT.toString(),
            "offset" to videoOffset.toString(),
            episodeSeed?.let { "randomSeed" to it.toString() },
            "userCurrentTime" to OffsetDateTime.now().format(USER_CURRENT_TIME_FORMATTER),
        )
        val channelEpisodesJson = JSONObject(
            sendApiRequest(
                "GET",
                apiUrl("/channels/${urlEncoded(channelId)}/default-order-episodes", query),
                AuthorizationMode.OPTIONAL,
            ),
        )
        return parseGuideVideosPage(channelEpisodesJson)
    }

    // TODO: Validate
    private fun parseGuideVideosPage(channelEpisodesJson: JSONObject): GuideVideosPage {
        val episodesJson = channelEpisodesJson.getJSONArray("episodes")
        val seasonIdToSeasonJson = channelEpisodesJson.getJSONObject("seasons")
        val titleIdToTitleJson = channelEpisodesJson.getJSONObject("titles")
        val sourceIdToSourceJson = channelEpisodesJson.getJSONObject("sources")
        val pluginIdToPluginJson = channelEpisodesJson.getJSONObject("plugins")
        return GuideVideosPage(
            videos = episodesJson.toJsonObjectList().map { episodeJson ->
                parseGuideVideo(
                    episodeJson,
                    seasonIdToSeasonJson,
                    titleIdToTitleJson,
                    sourceIdToSourceJson,
                    pluginIdToPluginJson,
                )
            }.filterNot { guideVideo ->
                findStreamingSite(guideVideo)?.isVideoUnplayable?.invoke(guideVideo) == true
            },
            hasMoreVideos = channelEpisodesJson.getBoolean("has_more"),
        )
    }

    // TODO: Validate
    private fun parseGuideVideo(
        episodeJson: JSONObject,
        seasonIdToSeasonJson: JSONObject,
        titleIdToTitleJson: JSONObject,
        sourceIdToSourceJson: JSONObject,
        pluginIdToPluginJson: JSONObject,
    ): GuideVideo {
        val seasonJson = seasonIdToSeasonJson.getJSONObject(episodeJson.getString("season_id"))
        val titleJson = titleIdToTitleJson.getJSONObject(seasonJson.getString("title_id"))
        val sourceJson = sourceIdToSourceJson.getJSONObject(titleJson.getString("source_id"))
        val pluginJson = pluginIdToPluginJson.getJSONObject(sourceJson.getString("plugin_id"))
        return GuideVideo(
            episodeId = episodeJson.getString("id"),
            title = titleJson.optNullableString("name"),
            subtitle = videoSubtitle(episodeJson, seasonJson, titleJson),
            url = episodeJson.optNullableString("url"),
            episodeKey = episodeJson.optNullableString("key"),
            duration = episodeJson.optNullableDouble("duration"),
            thumbnailUrl = absoluteAssetUrl(videoThumbnailUrl(episodeJson)),
            faviconUrl = absoluteAssetUrl(sourceJson.optNullableString("favicon_url")),
            pluginKey = pluginJson.getString("key"),
            watched = !episodeJson.isNull("watch_date"),
            watchCompleted = episodeJson.optBoolean("completed", false),
            watchId = episodeJson.optNullableString("episode_watch_id"),
            titleId = seasonJson.getString("title_id"),
            description = episodeJson.optNullableString("description"),
            sourceKey = sourceJson.getString("key"),
        )
    }

    // TODO: Validate
    private fun videoSubtitle(episodeJson: JSONObject, seasonJson: JSONObject, titleJson: JSONObject): String {
        if (titleJson.optNullableString("media_type") == "Movie") return "Movie"
        val tmdbEpisodeId = episodeJson.optNullableString("tmdb_episode_id")
        val isLinkedToTmdbEpisode = tmdbEpisodeId != null && tmdbEpisodeId != episodeJson.getString("id")
        val (seasonNumber, episodeNumber) = if (isLinkedToTmdbEpisode) {
            episodeJson.optNullableDouble("tmdb_season_number") to episodeJson.optNullableDouble("tmdb_episode_number")
        } else {
            seasonJson.optNullableDouble("season_number") to episodeJson.optNullableDouble("episode_number")
        }
        val seasonAndEpisodeNumbering = when {
            seasonNumber != null && episodeNumber != null ->
                "S${formatNumberWithoutTrailingZero(seasonNumber)} Ep${formatNumberWithoutTrailingZero(episodeNumber)}"
            episodeNumber != null -> "Ep${formatNumberWithoutTrailingZero(episodeNumber)}"
            else -> null
        }
        return listOfNotNull(seasonAndEpisodeNumbering, episodeJson.optNullableString("name")).joinToString(" · ")
    }

    // TODO: Validate
    private fun videoThumbnailUrl(episodeJson: JSONObject): String? {
        val imagesJson = episodeJson.getJSONArray("images").toJsonObjectList()
        val thumbnailImageJson = THUMBNAIL_IMAGE_KINDS_BY_PREFERENCE.firstNotNullOfOrNull { imageKind ->
            imagesJson.firstOrNull { imageJson -> imageJson.getString("kind") == imageKind }
        }
        return thumbnailImageJson?.getJSONObject("urls")?.getString("w300")
    }

    // TODO: Validate
    fun advanceChannelEpisodeSeeds(channelIds: List<String>) {
        sendApiRequest(
            method = "POST",
            url = apiUrl("/users/me/episode-seeds/channels/advance"),
            authorization = AuthorizationMode.REQUIRED,
            body = JSONObject().put("channel_ids", JSONArray(channelIds)).toString(),
        )
    }

    // TODO: Validate
    fun advanceEveryChannelEpisodeSeed() {
        sendApiRequest("POST", apiUrl("/users/me/episode-seeds/advance"), AuthorizationMode.REQUIRED)
    }

    // TODO: Validate
    fun advanceChannelOrderSeed() {
        sendApiRequest("POST", apiUrl("/users/me/episode-seeds/channel-order/advance"), AuthorizationMode.REQUIRED)
    }

    // TODO: Validate
    fun forgetStoredEpisodeOrders(channelIds: List<String>?) {
        sendApiRequest(
            method = "POST",
            url = apiUrl("/users/me/stored-episode-orders/forget"),
            authorization = AuthorizationMode.REQUIRED,
            body = JSONObject()
                .put("channel_ids", channelIds?.let { JSONArray(it) } ?: JSONObject.NULL)
                .toString(),
        )
    }

    
    // TODO: Validate
    fun favoriteChannel(channelId: String) {
        sendApiRequest("POST", apiUrl("/channels/${urlEncoded(channelId)}/favorite"), AuthorizationMode.REQUIRED)
    }

    
    // TODO: Validate
    fun unfavoriteChannel(channelId: String) {
        sendApiRequest("DELETE", apiUrl("/channels/${urlEncoded(channelId)}/favorite"), AuthorizationMode.REQUIRED)
    }

    // TODO: Validate
    fun markEpisodeWatched(episodeId: String, completed: Boolean, channelId: String): String {
        val responseText = sendApiRequest(
            method = "POST",
            url = apiUrl("/episodes/${urlEncoded(episodeId)}/watches"),
            authorization = AuthorizationMode.REQUIRED,
            body = JSONObject()
                .put("watch_date", Instant.now().toString())
                .put("completed", completed)
                .put("channel_id", channelId)
                .toString(),
        )
        return JSONObject(responseText).getString("id")
    }

    // TODO: Validate
    fun fetchIncompleteWatchId(episodeId: String): String? {
        val watchesJson = JSONArray(
            sendApiRequest("GET", apiUrl("/episodes/${urlEncoded(episodeId)}/watches"), AuthorizationMode.REQUIRED),
        )
        return watchesJson.toJsonObjectList()
            .firstOrNull { !it.optBoolean("completed", false) }
            ?.getString("id")
    }

    // TODO: Validate
    fun completeWatch(watchId: String) {
        sendApiRequest(
            method = "PATCH",
            url = apiUrl("/watches/${urlEncoded(watchId)}"),
            authorization = AuthorizationMode.REQUIRED,
            body = JSONObject().put("completed", true).toString(),
        )
    }

    // TODO: Validate
    fun deleteWatch(watchId: String) {
        sendApiRequest("DELETE", apiUrl("/watches/${urlEncoded(watchId)}"), AuthorizationMode.REQUIRED)
    }

    // TODO: Validate
    private enum class AuthorizationMode {
        NONE,
        OPTIONAL,
        REQUIRED,
    }

    // TODO: Validate
    private fun sendApiRequest(
        method: String,
        url: String,
        authorization: AuthorizationMode,
        body: String? = null,
        contentType: String = "application/json",
    ): String {
        val accessToken = readAccessToken()
        if (authorization == AuthorizationMode.REQUIRED && accessToken.isNullOrEmpty()) {
            throw ApiException(401, "Not signed in")
        }
        val sentAccessToken = accessToken?.takeIf { it.isNotEmpty() && authorization != AuthorizationMode.NONE }
        return try {
            sendHttpRequestAndReadBody(method, url, sentAccessToken, body, contentType)
        } catch (rejectedSignInError: ApiException) {
            if (!rejectedSignInError.isSignInRejected || authorization != AuthorizationMode.OPTIONAL) throw rejectedSignInError
            sendHttpRequestAndReadBody(method, url, null, body, contentType)
        }
    }

    // TODO: Validate
    private fun sendHttpRequestAndReadBody(
        method: String,
        url: String,
        sentAccessToken: String?,
        body: String?,
        contentType: String,
    ): String {
        val connection = openHttpConnection(url)
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (sentAccessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $sentAccessToken")
            }
            readLocalSourcePreferencesJson()?.let { localSourcePreferencesJson ->
                connection.setRequestProperty("X-Source-Preferences", localSourcePreferencesJson)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val isSuccessful = statusCode in SUCCESSFUL_STATUS_CODES
            val responseStream = if (isSuccessful) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (!isSuccessful) {
                val responseErrorDetail = parseErrorDetail(responseText)
                val isSignInRejected = sentAccessToken != null &&
                    (statusCode == 401 || (statusCode == 403 && responseErrorDetail == "Could not validate credentials"))
                throw ApiException(
                    statusCode,
                    responseErrorDetail ?: "$method $url failed: $statusCode",
                    isSignInRejected,
                )
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    // TODO: Validate
    private fun parseErrorDetail(responseText: String): String? =
        when (val detail = runCatching { JSONObject(responseText).opt("detail") }.getOrNull()) {
            is String -> detail
            is JSONArray -> detail.optJSONObject(0)?.optNullableString("msg")
            else -> null
        }

    // TODO: Validate
    fun channelImageUrl(imageId: String): String = apiUrl("/channel-images/${urlEncoded(imageId)}/w300")

    // TODO: Validate
    private fun apiUrl(path: String, query: List<Pair<String, String>> = emptyList()): String {
        val queryString = encodedKeyValuePairs(query)
        return "$API_ORIGIN/api/v1$path" + if (queryString.isEmpty()) "" else "?$queryString"
    }

    // TODO: Validate
    private fun encodedKeyValuePairs(keyValuePairs: List<Pair<String, String>>): String =
        keyValuePairs.joinToString("&") { (pairKey, pairValue) -> "${urlEncoded(pairKey)}=${urlEncoded(pairValue)}" }

    // TODO: Validate
    private fun urlEncoded(value: String): String = URLEncoder.encode(value, "UTF-8")

    // TODO: Validate
    private fun absoluteAssetUrl(assetUrl: String?): String? =
        assetUrl?.let { relativeOrAbsoluteAssetUrl -> URI("$API_ORIGIN/").resolve(relativeOrAbsoluteAssetUrl).toString() }

    // TODO: Validate
    companion object {
        // TODO: Validate
        const val API_ORIGIN = "https://api.streamchanneler.com"
        // TODO: Validate
        private const val GUIDE_VIDEO_LIMIT = 20
        // TODO: Validate
        private val USER_CURRENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        // TODO: Validate
        private val SUCCESSFUL_STATUS_CODES = 200..299
        // TODO: Validate
        private val THUMBNAIL_IMAGE_KINDS_BY_PREFERENCE = listOf("still", "backdrop", "poster")
    }
}

// TODO: Validate
fun openHttpConnection(url: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", "StreamChanneler/${BuildConfig.VERSION_NAME} (Android)")
    return connection
}
