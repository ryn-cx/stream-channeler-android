// TODO: Validate
package com.streamchanneler.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

// TODO: Validate
class AccountSettingsRepository(
    private val api: StreamChannelerApi,
    private val localAppStorage: LocalAppStorage,
) {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val accountSettingsExecutor = Executors.newSingleThreadExecutor()

    // TODO: Validate
    fun loadSignedInUserIfReachable() {
        runCatching { api.fetchSignedInUser() }
            .onSuccess(localAppStorage::saveSignedInUser)
            .onFailure { Log.w(TAG, "Couldn't load the account's refresh settings", it) }
    }

    // TODO: Validate
    fun refreshUserSettings(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        runAccountSettingsWorkInBackground(
            failureLogMessage = "Couldn't refresh the user settings",
            failureMessageForViewer = "Couldn't refresh the user settings",
            onSuccess,
            onFailure,
        ) {
            localAppStorage.saveSignedInUser(api.fetchSignedInUser())
        }
    }

    // TODO: Validate
    fun updateAccountStreamingProfile(
        pluginKey: String,
        profileName: String?,
        profileNumber: Int?,
        profilePin: String?,
        onSaved: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        runAccountSettingsWorkInBackground(
            failureLogMessage = "Couldn't save the $pluginKey profile",
            failureMessageForViewer = "Couldn't save the $pluginKey profile",
            onSaved,
            onFailure,
        ) {
            localAppStorage.replaceAccountStreamingProfiles(
                api.updatePluginProfile(pluginKey, profileName, profileNumber, profilePin),
            )
        }
    }

    // TODO: Validate
    fun updateChannelRefreshSettings(
        channelId: String,
        channelRefreshSettings: ChannelRefreshSettings,
        onSaved: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        runAccountSettingsWorkInBackground(
            failureLogMessage = "Couldn't save $channelId's refresh settings",
            failureMessageForViewer = "Couldn't save the channel's settings",
            onSaved,
            onFailure,
        ) {
            api.updateChannelRefreshSettings(channelId, channelRefreshSettings)
            localAppStorage.replaceAccountChannelRefreshSettings(channelId, channelRefreshSettings)
        }
    }

    // TODO: Validate
    fun loadSourcePreferences(
        loadsSavedDefault: Boolean,
        onLoaded: (List<SourcePreference>) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        lateinit var loadedSourcePreferences: List<SourcePreference>
        runAccountSettingsWorkInBackground(
            failureLogMessage = "Couldn't load the source preferences",
            failureMessageForViewer = "Couldn't load the sources",
            onSuccess = { onLoaded(loadedSourcePreferences) },
            onFailure,
        ) {
            loadedSourcePreferences = api.fetchSourcePreferences(loadsSavedDefault)
        }
    }

    // TODO: Validate
    fun saveSourcePreferencesAsDefault(
        sourcePreferences: List<SourcePreference>,
        onSaved: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        runAccountSettingsWorkInBackground(
            failureLogMessage = "Couldn't save the source preferences as the account default",
            failureMessageForViewer = "Couldn't save as your default",
            onSaved,
            onFailure,
        ) {
            val sourcePreferencesJson = sourcePreferences.toSourcePreferencesJson()
            api.updateAccountSourcePreferences(sourcePreferencesJson)
            localAppStorage.localSourcePreferencesJson = sourcePreferencesJson.toString()
        }
    }

    // TODO: Validate
    private fun runAccountSettingsWorkInBackground(
        failureLogMessage: String,
        failureMessageForViewer: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        accountSettingsWork: () -> Unit,
    ) {
        accountSettingsExecutor.execute {
            try {
                accountSettingsWork()
                mainHandler.post { onSuccess() }
            } catch (accountSettingsError: Exception) {
                Log.w(TAG, failureLogMessage, accountSettingsError)
                mainHandler.post { onFailure("$failureMessageForViewer: ${accountSettingsError.message}") }
            }
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "AccountSettingsRepository"
    }
}
