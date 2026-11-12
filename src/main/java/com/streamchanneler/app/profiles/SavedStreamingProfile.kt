// TODO: Validate
package com.streamchanneler.app.profiles

import com.streamchanneler.app.data.LocalAppStorage
import com.streamchanneler.app.streamingapps.StreamingApp

// TODO: Validate
class SavedStreamingProfile(val name: String, val number: Int, val pinToEnter: String) {
    // TODO: Validate
    val entersPin: Boolean
        get() = pinToEnter.isNotEmpty()
}

// TODO: Validate
fun LocalAppStorage.savedStreamingProfile(streamingApp: StreamingApp): SavedStreamingProfile {
    val streamingSiteName = streamingApp.site.pluginKey
    return SavedStreamingProfile(
        name = streamingProfileName(streamingSiteName).trim(),
        number = streamingProfileNumber(streamingSiteName),
        pinToEnter = if (streamingApp.pinEntry != null) streamingProfilePin(streamingSiteName) else "",
    )
}
