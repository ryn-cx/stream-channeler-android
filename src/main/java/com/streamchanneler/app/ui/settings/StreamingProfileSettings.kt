// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.PasswordTransformationMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.EditText
import android.widget.TextView
import com.streamchanneler.app.profiles.P1AccessibilityClickedProfileScreen
import com.streamchanneler.app.profiles.P2RemoteButtonProfileScreen
import com.streamchanneler.app.profiles.StreamingProfileScreen
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.textInput

// TODO: Validate
internal const val HIGHLIGHTED_PROFILE_LABEL = "The highlighted one"

// TODO: Validate
private const val PROFILE_WARNING_RED_SENTENCES =
    "Profile names and PINs are not encrypted. They are stored and sent as plain text, and any device signed in " +
        "to your account can read them."

// TODO: Validate
private const val PROFILE_WARNING_REST =
    " Streaming service PINs are not treated as real account security because " +
        "almost every service lets you remove a forgotten PIN from your account. If a PIN here is the same as one " +
        "you use for anything that matters, like a credit card, don't save it here, and consider using different " +
        "PINs for your credit card and your streaming services."

// TODO: Validate
private val PROFILE_WARNING_RED = Color.parseColor("#FF2B2B")

// TODO: Validate
internal data class SiteStreamingProfile(
    val pluginKey: String,
    val profileName: String,
    val profileNumber: Int,
    val profilePin: String,
)

// TODO: Validate
internal fun hiddenProfilePinLabel(profilePin: String): String = if (profilePin.isEmpty()) "None, you enter it" else "Set, hidden"

// TODO: Validate
internal fun picksProfilesByName(profileScreen: StreamingProfileScreen): Boolean = when (profileScreen) {
    is P1AccessibilityClickedProfileScreen -> true
    is P2RemoteButtonProfileScreen -> profileScreen.readsProfileNamesOnScreen
}

// TODO: Validate
internal fun Context.profileNameInput(hint: String, shownProfileName: String): EditText =
    textInput(hint).apply { setText(shownProfileName) }

// TODO: Validate
internal fun Context.profilePinInput(shownProfilePin: String): EditText = textInput(
    "PIN, if the profile has one",
    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
).apply {
    transformationMethod = PasswordTransformationMethod.getInstance()
    setText(shownProfilePin)
}

// TODO: Validate
internal fun Context.showSingleInputDialog(
    title: String,
    message: String,
    input: EditText,
    confirmButtonLabel: String,
    onConfirmed: (enteredText: String) -> Unit,
) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setView(dialogInputsColumn(listOf(input)))
        .setPositiveButton(confirmButtonLabel) { _, _ -> onConfirmed(input.text.toString().trim()) }
        .setNegativeButton("Cancel", null)
        .show()
}

// TODO: Validate
internal fun Context.streamingProfileWarning(): TextView {
    val profileWarningText = SpannableString(PROFILE_WARNING_RED_SENTENCES + PROFILE_WARNING_REST)
    val redSentencesEnd = PROFILE_WARNING_RED_SENTENCES.length
    profileWarningText.setSpan(ForegroundColorSpan(PROFILE_WARNING_RED), 0, redSentencesEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    profileWarningText.setSpan(StyleSpan(Typeface.BOLD), 0, redSentencesEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return settingsNote(profileWarningText)
}

// TODO: Validate
internal class StreamingProfileSettings(private val activity: Activity, private val afterProfilesSaved: () -> Unit) {
    // TODO: Validate
    private val app get() = activity.streamChannelerApp

    // TODO: Validate
    fun savedProfile(pluginKey: String): SiteStreamingProfile {
        val localAppStorage = app.localAppStorage
        return SiteStreamingProfile(
            pluginKey = pluginKey,
            profileName = localAppStorage.streamingProfileName(pluginKey),
            profileNumber = localAppStorage.streamingProfileNumber(pluginKey),
            profilePin = localAppStorage.streamingProfilePin(pluginKey),
        )
    }

    // TODO: Validate
    fun profileLabel(pluginKey: String, profileScreen: StreamingProfileScreen): String {
        val savedProfile = savedProfile(pluginKey)
        return when (profileScreen) {
            is P1AccessibilityClickedProfileScreen -> savedProfile.profileName.ifEmpty { HIGHLIGHTED_PROFILE_LABEL }
            is P2RemoteButtonProfileScreen -> when {
                savedProfile.profileName.isNotEmpty() && profileScreen.readsProfileNamesOnScreen -> savedProfile.profileName
                savedProfile.profileNumber > 0 && profileScreen.profileList != null -> "Number ${savedProfile.profileNumber}"
                else -> HIGHLIGHTED_PROFILE_LABEL
            }
        }
    }

    // TODO: Validate
    fun editProfile(pluginKey: String, profileScreen: StreamingProfileScreen) {
        when (profileScreen) {
            is P1AccessibilityClickedProfileScreen -> editAccessibilityClickedProfile(pluginKey)
            is P2RemoteButtonProfileScreen -> editRemoteButtonProfile(pluginKey, profileScreen)
        }
    }

    // TODO: Validate
    fun editProfilePin(pluginKey: String) {
        val savedProfile = savedProfile(pluginKey)
        activity.showSingleInputDialog(
            title = "$pluginKey profile PIN",
            message = "When $pluginKey asks for the picked profile's PIN, this PIN is entered for you. " +
                "Leave the PIN empty to enter it yourself.",
            input = activity.profilePinInput(savedProfile.profilePin),
            confirmButtonLabel = "Save",
        ) { enteredProfilePin -> saveProfiles(listOf(savedProfile.copy(profilePin = enteredProfilePin))) }
    }

    // TODO: Validate
    fun saveProfiles(profilesToSave: List<SiteStreamingProfile>) {
        val localAppStorage = app.localAppStorage
        if (!localAppStorage.isSignedIn) {
            for (profileToSave in profilesToSave) {
                with(profileToSave) {
                    localAppStorage.writeNumberedStreamingProfile(pluginKey, profileNumber, profilePin)
                    localAppStorage.writeStreamingProfile(pluginKey, profileName, profilePin)
                }
            }
            afterProfilesSaved()
            return
        }
        val profileSaveFailureMessages = mutableListOf<String>()
        var unfinishedProfileSaveCount = profilesToSave.size

        // TODO: Validate
        fun finishOneProfileSave() {
            unfinishedProfileSaveCount -= 1
            if (unfinishedProfileSaveCount > 0 || activity.isFinishing) return
            afterProfilesSaved()
            if (profileSaveFailureMessages.isEmpty()) return
            AlertDialog.Builder(activity).setMessage(profileSaveFailureMessages.joinToString(" ")).setPositiveButton("OK", null).show()
        }

        for (profileToSave in profilesToSave) {
            app.accountSettings.updateAccountStreamingProfile(
                profileToSave.pluginKey,
                profileName = profileToSave.profileName.ifEmpty { null },
                profileNumber = profileToSave.profileNumber.takeIf { it > 0 },
                profilePin = profileToSave.profilePin.ifEmpty { null },
                onSaved = { finishOneProfileSave() },
                onFailure = { failureMessage ->
                    profileSaveFailureMessages.add(failureMessage)
                    finishOneProfileSave()
                },
            )
        }
    }

    // TODO: Validate
    private fun editAccessibilityClickedProfile(pluginKey: String) {
        val savedProfile = savedProfile(pluginKey)
        activity.showSingleInputDialog(
            title = "$pluginKey profile",
            message = "When $pluginKey asks who is watching after the guide opens it, " +
                "this profile is picked for you. Leave the name empty to pick the profile it highlights, usually the last one used.",
            input = activity.profileNameInput("Profile name, exactly as the app shows it", savedProfile.profileName),
            confirmButtonLabel = "Save",
        ) { enteredProfileName -> saveProfiles(listOf(savedProfile.copy(profileName = enteredProfileName))) }
    }

    // TODO: Validate
    private fun editRemoteButtonProfile(pluginKey: String, profileScreen: P2RemoteButtonProfileScreen) {
        val savedProfile = savedProfile(pluginKey)
        val picksProfilesByNumber = profileScreen.profileList != null
        val readsProfileNamesOnScreen = profileScreen.readsProfileNamesOnScreen
        val profileNameInput = activity.profileNameInput("Profile name, exactly as the app shows it", savedProfile.profileName)
        val profileNumberInput = activity.textInput(
            "Profile number, counting from the first, or empty for the highlighted one",
            InputType.TYPE_CLASS_NUMBER,
        ).apply {
            setText(savedProfile.profileNumber.takeIf { it > 0 }?.toString().orEmpty())
        }
        val profileDialogInputs = listOfNotNull(
            profileNameInput.takeIf { readsProfileNamesOnScreen },
            profileNumberInput.takeIf { picksProfilesByNumber },
        )
        val profileDialogBuilder = AlertDialog.Builder(activity)
            .setTitle("$pluginKey profile")
            .setMessage(
                if (readsProfileNamesOnScreen) {
                    "When $pluginKey asks who is watching after the guide opens it, the name is " +
                        "read off the screen and that profile is picked for you. If the name can't be found, or is left " +
                        "empty, the profile it highlights, usually the last one used, is picked instead."
                } else {
                    "$pluginKey doesn't let other apps read its profile names, so when it asks who is " +
                        "watching after the guide opens it, " +
                        if (picksProfilesByNumber) {
                            "the profile at this number in the list is picked for you. " +
                                "Leave the number empty to pick the profile it highlights, usually the last one used."
                        } else {
                            "the profile it highlights, usually the last one used, is picked for you."
                        }
                },
            )
        if (profileDialogInputs.isEmpty()) {
            profileDialogBuilder.setPositiveButton("OK", null).show()
            return
        }
        profileDialogBuilder
            .setView(activity.dialogInputsColumn(profileDialogInputs))
            .setPositiveButton("Save") { _, _ ->
                saveProfiles(
                    listOf(
                        savedProfile.copy(
                            profileName = if (readsProfileNamesOnScreen) profileNameInput.text.toString().trim() else "",
                            profileNumber = if (picksProfilesByNumber) profileNumberInput.text.toString().trim().toIntOrNull() ?: 0 else 0,
                        ),
                    ),
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
