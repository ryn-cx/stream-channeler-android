// TODO: Validate
package com.streamchanneler.app.profiles

import androidx.test.platform.app.InstrumentationRegistry
import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.data.optNullableString
import org.json.JSONObject
import java.io.FileNotFoundException

// TODO: Validate
object StreamingTestProfiles {
    // TODO: Validate
    const val PROFILES_ASSET_NAME = "streaming_profiles.json"

    // TODO: Validate
    /**
     * Saves each streaming app's profile from [PROFILES_ASSET_NAME] in [localAppStorage], so the profile picker picks
     * that profile and enters its PIN. The asset has the same shape as the account's `plugin_key_to_profile`.
     */
    fun saveProfilesFromAsset(localAppStorage: LocalAppStorage) {
        val profilesJsonText = try {
            InstrumentationRegistry.getInstrumentation().context.assets.open(PROFILES_ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            throw AssertionError(
                "src/androidTest/assets/$PROFILES_ASSET_NAME is missing. " +
                    "Copy src/androidTest/streaming_profiles.example.json there and fill it in.",
            )
        }
        val pluginKeyToProfileJson = JSONObject(profilesJsonText).getJSONObject("plugin_key_to_profile")
        for (pluginKey in pluginKeyToProfileJson.keys()) {
            val profileJson = pluginKeyToProfileJson.getJSONObject(pluginKey)
            val profilePin = profileJson.optNullableString("pin").orEmpty()
            // A profile screen is matched either by name or by position, so both are saved.
            localAppStorage.writeStreamingProfile(pluginKey, profileJson.optNullableString("profile_name").orEmpty(), profilePin)
            localAppStorage.writeNumberedStreamingProfile(pluginKey, profileJson.optInt("profile_number", 0), profilePin)
        }
    }
}
