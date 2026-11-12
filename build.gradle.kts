// TODO: Validate

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.streamchanneler.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.streamchanneler.app"
        minSdk = 26
        targetSdk = 37
        // The release workflow passes versionCode and versionName as Gradle properties; local builds use these defaults.
        versionCode = (providers.gradleProperty("versionCode").orNull ?: "1").toInt()
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Lets instrumentation tests save screenshots and logs through TestStorage.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    signingConfigs {
        // When the keystore environment variables are set, release builds are signed with that keystore.
        // Otherwise they are signed with the debug key.
        val keystore = System.getenv("ANDROID_KEYSTORE_PATH")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.opencv)
    implementation(libs.androidx.activity)
    implementation(libs.kotlinx.coroutines.android)
    // Reads profile names off the screen for apps that don't give them to accessibility. The model is bundled in the
    // app, so it works without Google Play services.
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.services.storage)
    // Installs the test services app that TestStorage writes through.
    androidTestUtil(libs.androidx.test.services)
}

val profilePickerServiceName = "com.streamchanneler.app/com.streamchanneler.app.screenautomation.ScreenAutomationService"
val playbackWatcherServiceName = "com.streamchanneler.app/com.streamchanneler.app.playback.PlaybackWatcherService"

val reconnectTvServices by tasks.registering {
    description = "Turns the profile picker and playback watcher back on after installing or testing on a device stops the app."
    val adbExecutable = androidComponents.sdkComponents.adb
    doLast {
        val adbPath = adbExecutable.get().asFile.absolutePath
        // TODO: Validate
        fun adbShellOutput(vararg shellArguments: String): String {
            val adbProcess = ProcessBuilder(listOf(adbPath, "shell") + shellArguments).redirectErrorStream(true).start()
            val adbOutput = adbProcess.inputStream.bufferedReader().readText()
            adbProcess.waitFor()
            return adbOutput
        }
        // Removing the profile picker from the enabled accessibility services and adding it back
        // makes Android rebind it.
        // `settings get` prints "null" when the setting has never been set, so that value is dropped too.
        val otherEnabledAccessibilityServiceNames = adbShellOutput("settings", "get", "secure", "enabled_accessibility_services")
            .trim()
            .split(':')
            .filter { it.isNotBlank() && it != "null" && it != profilePickerServiceName }
        if (otherEnabledAccessibilityServiceNames.isEmpty()) {
            adbShellOutput("settings", "delete", "secure", "enabled_accessibility_services")
        } else {
            adbShellOutput("settings", "put", "secure", "enabled_accessibility_services", otherEnabledAccessibilityServiceNames.joinToString(":"))
        }
        adbShellOutput(
            "settings", "put", "secure", "enabled_accessibility_services",
            (otherEnabledAccessibilityServiceNames + profilePickerServiceName).joinToString(":"),
        )
        adbShellOutput("settings", "put", "secure", "accessibility_enabled", "1")
        // Taking notification access away and granting it again makes Android rebind the playback watcher.
        adbShellOutput("cmd", "notification", "disallow_listener", playbackWatcherServiceName)
        adbShellOutput("cmd", "notification", "allow_listener", playbackWatcherServiceName)
    }
}

tasks.matching { it.name == "installDebug" || it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy(reconnectTvServices)
}
