// TODO: Validate
package com.streamchanneler.app.profiles

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileNotFoundException
import java.util.Properties

// TODO: Validate
data class StreamingAppTestSecrets(
    val episodeUrl: String,
    val episodeKey: String?,
) {
    // TODO: Validate
    companion object {
        // TODO: Validate
        const val SECRETS_ASSET_NAME = "streaming_app_test_secrets.properties"

        // TODO: Validate
        fun readSecretsOfStreamingApp(streamingAppKey: String): StreamingAppTestSecrets {
            val secretsProperties = Properties()
            try {
                InstrumentationRegistry.getInstrumentation().context.assets.open(SECRETS_ASSET_NAME).reader().use(secretsProperties::load)
            } catch (_: FileNotFoundException) {
                throw AssertionError(
                    "src/androidTest/assets/$SECRETS_ASSET_NAME is missing. " +
                        "Copy src/androidTest/streaming_app_test_secrets.example.properties there and fill it in.",
                )
            }

            // TODO: Validate
            fun optionalSecret(secretName: String): String? =
                secretsProperties.getProperty("$streamingAppKey.$secretName")?.takeIf { it.isNotBlank() }

            // TODO: Validate
            fun requiredSecret(secretName: String): String = optionalSecret(secretName)
                ?: throw AssertionError("$SECRETS_ASSET_NAME has no $streamingAppKey.$secretName")

            return StreamingAppTestSecrets(
                episodeUrl = requiredSecret("episode_url"),
                episodeKey = optionalSecret("episode_key"),
            )
        }
    }
}
