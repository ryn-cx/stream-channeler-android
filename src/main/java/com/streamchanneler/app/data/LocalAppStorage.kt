// TODO: Validate
package com.streamchanneler.app.data

import android.content.Context
import com.streamchanneler.app.streamingapps.StreamingSiteSettings
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.json.JSONObject

// TODO: Validate
const val DEFAULT_GUIDE_DETAILS_HEIGHT = 25

// TODO: Validate
const val DEFAULT_ZOOM = 100

// TODO: Validate
const val DEFAULT_MINIMUM_VIDEO_LENGTH = 0

// TODO: Validate
class LocalAppStorage(context: Context) {
    // TODO: Validate
    private val sharedPreferences =
        context.getSharedPreferences("stream_channeler", Context.MODE_PRIVATE)

    // TODO: Validate
    var accessToken by stringPreference(ACCESS_TOKEN_KEY)

    // TODO: Validate
    var signedInDisplayName by stringPreference(SIGNED_IN_DISPLAY_NAME_KEY)

    // TODO: Validate
    var hasChosenAnonymous by booleanPreference(HAS_CHOSEN_ANONYMOUS_KEY, defaultValue = false)

    // TODO: Validate
    val isSignedIn: Boolean
        get() = !accessToken.isNullOrEmpty()

    // TODO: Validate
    val hasFinishedLogin: Boolean
        get() = isSignedIn || hasChosenAnonymous

    // TODO: Validate
    var afterVideoEnds by enumPreference(AFTER_VIDEO_ENDS_KEY, AfterVideoEnds.PLAY_NEXT_VIDEO, AfterVideoEnds.entries)

    // TODO: Validate
    var nextEpisodeCountdown by intPreference(NEXT_EPISODE_COUNTDOWN_KEY, DEFAULT_NEXT_EPISODE_COUNTDOWN)

    // TODO: Validate
    var showsUpNextInformation by booleanPreference(SHOWS_UP_NEXT_INFORMATION_KEY, defaultValue = false)

    // TODO: Validate
    var showsCondensedGuide by booleanPreference(SHOWS_CONDENSED_GUIDE_KEY, defaultValue = false)

    // TODO: Validate
    var showsGuideSourceLogos by booleanPreference(SHOWS_GUIDE_SOURCE_LOGOS_KEY, defaultValue = true)

    // TODO: Validate
    var sizesGuideVideosByText by booleanPreference(SIZES_GUIDE_VIDEOS_BY_TEXT_KEY, defaultValue = false)

    // TODO: Validate
    var minimumVideoLength by intPreference(MINIMUM_VIDEO_LENGTH_KEY, DEFAULT_MINIMUM_VIDEO_LENGTH)

    // TODO: Validate
    var guideDetailsHeight by intPreference(GUIDE_DETAILS_HEIGHT_KEY, DEFAULT_GUIDE_DETAILS_HEIGHT)

    // TODO: Validate
    var zoom by intPreference(ZOOM_KEY, DEFAULT_ZOOM)

    // TODO: Validate
    var guideScope by enumPreference(GUIDE_SCOPE_KEY, GuideScope.PUBLIC, GuideScope.entries)

    // TODO: Validate
    var marksStartedVideosWatched by booleanPreference(MARKS_STARTED_VIDEOS_WATCHED_KEY, defaultValue = true)

    // TODO: Validate
    var marksFinishedVideosCompleted by booleanPreference(MARKS_FINISHED_VIDEOS_COMPLETED_KEY, defaultValue = true)

    // TODO: Validate
    var permissionsSeenAllowed: Set<String>
        get() = sharedPreferences.getStringSet(PERMISSIONS_SEEN_ALLOWED_KEY, null)?.toSet().orEmpty()
        set(value) = sharedPreferences.edit().putStringSet(PERMISSIONS_SEEN_ALLOWED_KEY, value).apply()

    // TODO: Validate
    var activePlaybackJson by stringPreference(ACTIVE_PLAYBACK_KEY)

    // TODO: Validate
    var localSourcePreferencesJson by stringPreference(LOCAL_SOURCE_PREFERENCES_KEY)

    // TODO: Validate
    private val accountUserJson by stringPreference(ACCOUNT_USER_KEY)

    // TODO: Validate
    val accountRefreshSettings: AccountRefreshSettings?
        get() = accountUserJson?.let { AccountRefreshSettings.fromJson(JSONObject(it)) }

    // TODO: Validate
    fun signOut() {
        sharedPreferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(SIGNED_IN_DISPLAY_NAME_KEY)
            .remove(ACCOUNT_USER_KEY)
            .putBoolean(HAS_CHOSEN_ANONYMOUS_KEY, false)
            .putString(GUIDE_SCOPE_KEY, GuideScope.PUBLIC.name)
            .apply()
    }

    // TODO: Validate
    fun loadLocalSeeds(): OrderSeeds {
        val channelIdToSeedJson = jsonObjectPreference(LOCAL_CHANNEL_EPISODE_ORDER_SEED_KEY)
        return OrderSeeds(
            minimumEpisodeOrderSeed = sharedPreferences.getInt(LOCAL_MINIMUM_EPISODE_ORDER_SEED_KEY, 0),
            channelOrderSeed = sharedPreferences.getInt(LOCAL_CHANNEL_ORDER_SEED_KEY, 0),
            channelIdToEpisodeOrderSeed = channelIdToSeedJson.keys().asSequence()
                .associateWith { channelIdToSeedJson.getInt(it) },
        )
    }

    // TODO: Validate
    fun saveLocalChannelEpisodeSeed(channelId: String, episodeSeed: Int) {
        val channelIdToSeedJson = jsonObjectPreference(LOCAL_CHANNEL_EPISODE_ORDER_SEED_KEY)
        channelIdToSeedJson.put(channelId, episodeSeed)
        writeJsonObjectPreference(LOCAL_CHANNEL_EPISODE_ORDER_SEED_KEY, channelIdToSeedJson)
    }

    // TODO: Validate
    fun advanceLocalChannelOrderSeed() {
        val advancedChannelOrderSeed = sharedPreferences.getInt(LOCAL_CHANNEL_ORDER_SEED_KEY, 0) + 1
        sharedPreferences.edit().putInt(LOCAL_CHANNEL_ORDER_SEED_KEY, advancedChannelOrderSeed).apply()
    }

    // TODO: Validate
    fun advanceLocalMinimumEpisodeSeed() {
        val orderSeeds = loadLocalSeeds()
        sharedPreferences.edit()
            .putInt(
                LOCAL_MINIMUM_EPISODE_ORDER_SEED_KEY,
                (orderSeeds.channelIdToEpisodeOrderSeed.values + orderSeeds.minimumEpisodeOrderSeed).max() + 1,
            )
            .remove(LOCAL_CHANNEL_EPISODE_ORDER_SEED_KEY)
            .apply()
    }

    // TODO: Validate
    fun readWatchedEpisodeIds(): Set<String> = jsonObjectPreference(WATCHED_EPISODES_KEY).keys().asSequence().toSet()

    // TODO: Validate
    fun rememberWatchedEpisode(episodeId: String) {
        val episodeIdToWatchedAtJson = jsonObjectPreference(WATCHED_EPISODES_KEY)
        episodeIdToWatchedAtJson.put(episodeId, System.currentTimeMillis())
        val newestWatchedEntries = episodeIdToWatchedAtJson.keys().asSequence()
            .map { it to episodeIdToWatchedAtJson.getLong(it) }
            .sortedByDescending { it.second }
            .take(WATCHED_EPISODES_LIMIT)
        writeJsonObjectPreference(WATCHED_EPISODES_KEY, JSONObject(newestWatchedEntries.toMap()))
    }

    // TODO: Validate
    fun forgetWatchedEpisode(episodeId: String) {
        val episodeIdToWatchedAtJson = jsonObjectPreference(WATCHED_EPISODES_KEY)
        episodeIdToWatchedAtJson.remove(episodeId)
        writeJsonObjectPreference(WATCHED_EPISODES_KEY, episodeIdToWatchedAtJson)
    }

    // TODO: Validate
    private fun accountStreamingProfileJson(pluginKey: String): JSONObject? {
        if (!isSignedIn) return null
        return jsonObjectPreference(ACCOUNT_USER_KEY)
            .optJSONObject("plugin_key_to_profile")
            ?.optJSONObject(pluginKey)
    }

    // TODO: Validate
    fun replaceAccountStreamingProfiles(pluginKeyToProfileJson: JSONObject) {
        updateAccountUserJson { accountUserJson -> accountUserJson.put("plugin_key_to_profile", pluginKeyToProfileJson) }
    }

    // TODO: Validate
    fun saveSignedInUser(signedInUser: SignedInUser) {
        sharedPreferences.edit()
            .putString(ACCOUNT_USER_KEY, signedInUser.userJson.toString())
            .putString(SIGNED_IN_DISPLAY_NAME_KEY, signedInUser.username)
            .apply()
    }

    // TODO: Validate
    fun replaceAccountChannelRefreshSettings(channelId: String, channelRefreshSettings: ChannelRefreshSettings) {
        updateAccountUserJson { accountUserJson ->
            val channelIdToRefreshSettingsJson =
                accountUserJson.optJSONObject("channel_id_to_refresh_settings") ?: JSONObject()
            if (channelRefreshSettings.followsAccountDefaults) {
                channelIdToRefreshSettingsJson.remove(channelId)
            } else {
                channelIdToRefreshSettingsJson.put(channelId, channelRefreshSettings.toJson())
            }
            accountUserJson.put("channel_id_to_refresh_settings", channelIdToRefreshSettingsJson)
        }
    }

    // TODO: Validate
    private fun updateAccountUserJson(updateSavedAccountUserJson: (JSONObject) -> Unit) {
        val savedAccountUserJson = jsonObjectPreference(ACCOUNT_USER_KEY)
        updateSavedAccountUserJson(savedAccountUserJson)
        writeJsonObjectPreference(ACCOUNT_USER_KEY, savedAccountUserJson)
    }

    // TODO: Validate
    fun streamingProfileName(pluginKey: String): String =
        accountStreamingProfileJson(pluginKey)?.let { it.optNullableString("profile_name").orEmpty() }
            ?: sharedPreferences.getString(STREAMING_PROFILE_NAME_KEY_PREFIX + pluginKey, null).orEmpty()

    // TODO: Validate
    fun streamingProfilePin(pluginKey: String): String =
        accountStreamingProfileJson(pluginKey)?.let { it.optNullableString("pin").orEmpty() }
            ?: sharedPreferences.getString(STREAMING_PROFILE_PIN_KEY_PREFIX + pluginKey, null).orEmpty()

    // TODO: Validate
    fun streamingProfileNumber(pluginKey: String): Int =
        accountStreamingProfileJson(pluginKey)?.optInt("profile_number", 0)
            ?: sharedPreferences.getInt(STREAMING_PROFILE_NUMBER_KEY_PREFIX + pluginKey, 0)

    // TODO: Validate
    fun writeNumberedStreamingProfile(pluginKey: String, profileNumber: Int, profilePin: String) {
        sharedPreferences.edit()
            .putString(STREAMING_PROFILE_PIN_KEY_PREFIX + pluginKey, profilePin)
            .putInt(STREAMING_PROFILE_NUMBER_KEY_PREFIX + pluginKey, profileNumber)
            .apply()
    }

    // TODO: Validate
    fun writeStreamingProfile(pluginKey: String, profileName: String, profilePin: String) {
        sharedPreferences.edit()
            .putString(STREAMING_PROFILE_NAME_KEY_PREFIX + pluginKey, profileName)
            .putString(STREAMING_PROFILE_PIN_KEY_PREFIX + pluginKey, profilePin)
            .apply()
    }

    // TODO: Validate
    fun streamingSiteSettings(pluginKey: String): StreamingSiteSettings =
        savedStreamingSiteSettings(jsonObjectPreference(PLUGIN_KEY_TO_STREAMING_SITE_SETTINGS_KEY), pluginKey)

    // TODO: Validate
    private fun savedStreamingSiteSettings(
        pluginKeyToStreamingSiteSettingsJson: JSONObject,
        pluginKey: String,
    ): StreamingSiteSettings =
        StreamingSiteSettings.fromJson(pluginKeyToStreamingSiteSettingsJson.optJSONObject(pluginKey) ?: JSONObject())

    // TODO: Validate
    fun writeStreamingSiteSettings(
        pluginKeys: Collection<String>,
        changeSettings: (StreamingSiteSettings) -> StreamingSiteSettings,
    ) {
        val pluginKeyToStreamingSiteSettingsJson = jsonObjectPreference(PLUGIN_KEY_TO_STREAMING_SITE_SETTINGS_KEY)
        for (pluginKey in pluginKeys) {
            val changedStreamingSiteSettings =
                changeSettings(savedStreamingSiteSettings(pluginKeyToStreamingSiteSettingsJson, pluginKey))
            pluginKeyToStreamingSiteSettingsJson.put(pluginKey, changedStreamingSiteSettings.toJson())
        }
        writeJsonObjectPreference(PLUGIN_KEY_TO_STREAMING_SITE_SETTINGS_KEY, pluginKeyToStreamingSiteSettingsJson)
    }

    // TODO: Validate
    private fun jsonObjectPreference(key: String): JSONObject = JSONObject(sharedPreferences.getString(key, null) ?: "{}")

    // TODO: Validate
    private fun writeJsonObjectPreference(key: String, preferenceJson: JSONObject) {
        sharedPreferences.edit().putString(key, preferenceJson.toString()).apply()
    }

    // TODO: Validate
    private fun stringPreference(key: String): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            // TODO: Validate
            override fun getValue(thisRef: Any?, property: KProperty<*>): String? = sharedPreferences.getString(key, null)

            // TODO: Validate
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) =
                sharedPreferences.edit().putString(key, value).apply()
        }

    // TODO: Validate
    private fun booleanPreference(key: String, defaultValue: Boolean): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            // TODO: Validate
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
                sharedPreferences.getBoolean(key, defaultValue)

            // TODO: Validate
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
                sharedPreferences.edit().putBoolean(key, value).apply()
        }

    // TODO: Validate
    private fun intPreference(key: String, defaultValue: Int): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            // TODO: Validate
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sharedPreferences.getInt(key, defaultValue)

            // TODO: Validate
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
                sharedPreferences.edit().putInt(key, value).apply()
        }

    // TODO: Validate
    private fun <T : Enum<T>> enumPreference(
        key: String,
        defaultValue: T,
        enumEntries: List<T>,
    ): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            // TODO: Validate
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                val storedName = sharedPreferences.getString(key, null) ?: return defaultValue
                return enumEntries.firstOrNull { it.name == storedName } ?: defaultValue
            }

            // TODO: Validate
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
                sharedPreferences.edit().putString(key, value.name).apply()
        }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val ACCESS_TOKEN_KEY = "accessToken"

        // TODO: Validate
        const val SIGNED_IN_DISPLAY_NAME_KEY = "signedInDisplayName"

        // TODO: Validate
        const val HAS_CHOSEN_ANONYMOUS_KEY = "hasChosenAnonymous"

        // TODO: Validate
        const val AFTER_VIDEO_ENDS_KEY = "afterVideoEnds"

        // TODO: Validate
        const val NEXT_EPISODE_COUNTDOWN_KEY = "nextEpisodeCountdownSeconds"

        // TODO: Validate
        const val DEFAULT_NEXT_EPISODE_COUNTDOWN = 0

        // TODO: Validate
        const val SHOWS_UP_NEXT_INFORMATION_KEY = "showsUpNextInformation"

        // TODO: Validate
        const val SHOWS_CONDENSED_GUIDE_KEY = "showsCondensedGuide"

        // TODO: Validate
        const val MINIMUM_VIDEO_LENGTH_KEY = "minimumVideoMinutes"

        // TODO: Validate
        const val SHOWS_GUIDE_SOURCE_LOGOS_KEY = "showsGuideSourceLogos"

        // TODO: Validate
        const val SIZES_GUIDE_VIDEOS_BY_TEXT_KEY = "sizesGuideVideosByText"

        // TODO: Validate
        const val GUIDE_DETAILS_HEIGHT_KEY = "guideDetailsHeightPercent"

        // TODO: Validate
        const val ZOOM_KEY = "zoomPercent"

        // TODO: Validate
        const val GUIDE_SCOPE_KEY = "guideScope"



        // TODO: Validate
        const val PLUGIN_KEY_TO_STREAMING_SITE_SETTINGS_KEY = "pluginKeyToStreamingSiteSettings"

        // TODO: Validate
        const val MARKS_STARTED_VIDEOS_WATCHED_KEY = "marksStartedVideosWatched"

        // TODO: Validate
        const val MARKS_FINISHED_VIDEOS_COMPLETED_KEY = "marksFinishedVideosCompleted"

        // TODO: Validate
        const val ACTIVE_PLAYBACK_KEY = "activePlayback"

        // TODO: Validate
        const val LOCAL_SOURCE_PREFERENCES_KEY = "localSourcePreferences"

        // TODO: Validate
        const val ACCOUNT_USER_KEY = "accountRefreshSettings"

        // TODO: Validate
        const val LOCAL_MINIMUM_EPISODE_ORDER_SEED_KEY = "localChannelListSeed"

        // TODO: Validate
        const val LOCAL_CHANNEL_ORDER_SEED_KEY = "localChannelOrderSeed"

        // TODO: Validate
        const val LOCAL_CHANNEL_EPISODE_ORDER_SEED_KEY = "localChannelIdToEpisodeSeed"

        // TODO: Validate
        const val WATCHED_EPISODES_KEY = "watchedEpisodes"

        // TODO: Validate
        const val PERMISSIONS_SEEN_ALLOWED_KEY = "permissionsSeenAllowed"

        // TODO: Validate
        const val WATCHED_EPISODES_LIMIT = 2000

        // TODO: Validate
        const val STREAMING_PROFILE_NAME_KEY_PREFIX = "streamingProfileName."

        // TODO: Validate
        const val STREAMING_PROFILE_PIN_KEY_PREFIX = "streamingProfilePin."

        // TODO: Validate
        const val STREAMING_PROFILE_NUMBER_KEY_PREFIX = "streamingProfileNumber."
    }
}
