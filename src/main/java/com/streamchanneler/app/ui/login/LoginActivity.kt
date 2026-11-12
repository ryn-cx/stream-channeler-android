// TODO: Validate
package com.streamchanneler.app.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.streamchanneler.app.R
import com.streamchanneler.app.StreamChannelerApp
import com.streamchanneler.app.data.ApiException
import com.streamchanneler.app.data.DeviceSignInCode
import com.streamchanneler.app.data.describeLoadError
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.actionButton
import com.streamchanneler.app.ui.common.centeredTextLabel
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import com.streamchanneler.app.ui.common.fullWidthLayoutParams
import com.streamchanneler.app.ui.common.roundedBackground
import com.streamchanneler.app.ui.common.textInput
import com.streamchanneler.app.ui.common.zoomedContext
import com.streamchanneler.app.ui.guide.GuideActivity
import java.io.IOException
import java.util.concurrent.Executors

// TODO: Validate
class LoginActivity : Activity() {
    // TODO: Validate
    private lateinit var codeSignInButton: Button
    // TODO: Validate
    private lateinit var codeSignInInstructionsText: TextView
    // TODO: Validate
    private lateinit var userCodeText: TextView
    // TODO: Validate
    private lateinit var usernameInput: EditText
    // TODO: Validate
    private lateinit var passwordInput: EditText
    // TODO: Validate
    private lateinit var signInButton: Button
    // TODO: Validate
    private lateinit var continueAnonymouslyButton: Button
    // TODO: Validate
    private lateinit var statusMessageText: TextView
    // TODO: Validate
    private val passwordSignInExecutor = Executors.newSingleThreadExecutor()
    // TODO: Validate
    private val codeSignInExecutor = Executors.newCachedThreadPool()
    // TODO: Validate
    private var isSigningInWithPassword = false

    // TODO: Validate
    @Volatile
    private var currentCodeSignInAttempt = 0

    // TODO: Validate
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.zoomedContext())
    }

    // TODO: Validate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val localAppStorage = streamChannelerApp.localAppStorage
        if (localAppStorage.hasFinishedLogin && !intent.getBooleanExtra(EXTRA_SHOW_LOGIN, false)) {
            openGuide()
            return
        }
        setContentView(buildLoginLayout())
        codeSignInButton.requestFocus()
    }

    // TODO: Validate
    override fun onDestroy() {
        passwordSignInExecutor.shutdownNow()
        codeSignInExecutor.shutdownNow()
        super.onDestroy()
    }

    // TODO: Validate
    private fun buildLoginLayout(): View {
        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
        }
        val titleText = centeredTextLabel("Stream Channeler", 28f, UiColors.TEXT, isBold = true)
        val subtitleText = centeredTextLabel(
            "Sign in to see your favorite and owned channels, or watch public channels without an account.",
            15f,
            UiColors.TEXT_MUTED,
        )

        codeSignInButton = actionButton(CODE_SIGN_IN_BUTTON_LABEL) { startCodeSignIn() }
        codeSignInInstructionsText = centeredTextLabel("", 15f, UiColors.TEXT).apply { visibility = View.GONE }
        userCodeText = centeredTextLabel("", 34f, UiColors.ACCENT, isBold = true).apply {
            letterSpacing = 0.15f
            visibility = View.GONE
        }
        val passwordSignInHeading = centeredTextLabel("Or sign in with your email and password", 14f, UiColors.TEXT_MUTED)
        usernameInput = textInput("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        passwordInput = textInput("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            setOnEditorActionListener { _, actionId, keyEvent ->
                val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                    (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN)
                if (isDoneAction) signInWithPassword()
                isDoneAction
            }
        }
        signInButton = actionButton("Sign in") { signInWithPassword() }
        continueAnonymouslyButton = actionButton("Continue without signing in") { continueAnonymously() }
        statusMessageText = centeredTextLabel("", 14f, UiColors.ERROR).apply { visibility = View.GONE }

        val formColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
            background = roundedBackground(UiColors.SURFACE, UiColors.BORDER, dpFloat(12f), dp(1))
            addView(logoImage, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(titleText, fullWidthLayoutParams(12))
            addView(subtitleText, fullWidthLayoutParams(4))
            addView(codeSignInButton, fullWidthLayoutParams(20))
            addView(codeSignInInstructionsText, fullWidthLayoutParams(12))
            addView(userCodeText, fullWidthLayoutParams(4))
            addView(passwordSignInHeading, fullWidthLayoutParams(20))
            addView(usernameInput, fullWidthLayoutParams(8))
            addView(passwordInput, fullWidthLayoutParams(10))
            addView(signInButton, fullWidthLayoutParams(16))
            addView(continueAnonymouslyButton, fullWidthLayoutParams(10))
            addView(statusMessageText, fullWidthLayoutParams(12))
        }
        val centeredContainer = FrameLayout(this).apply {
            addView(formColumn, FrameLayout.LayoutParams(dp(460), WRAP_CONTENT, Gravity.CENTER))
        }
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(UiColors.BACKGROUND)
            addView(centeredContainer, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    // TODO: Validate
    private fun signInWithPassword() {
        if (isSigningInWithPassword) return
        val enteredUsername = usernameInput.text.toString().trim()
        val enteredPassword = passwordInput.text.toString()
        if (enteredUsername.isEmpty() || enteredPassword.isEmpty()) {
            showStatusMessage("Enter your email and password.", isError = true)
            return
        }
        cancelCodeSignIn()
        setSigningInWithPassword(true)
        showStatusMessage("Signing in…", isError = false)
        val app = streamChannelerApp
        passwordSignInExecutor.execute {
            val signInResult = runCatching {
                saveAccessTokenAndReadDisplayName(app, app.api.logIn(enteredUsername, enteredPassword)) ?: enteredUsername
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setSigningInWithPassword(false)
                signInResult
                    .onSuccess { signedInDisplayName -> finishSignIn(signedInDisplayName) }
                    .onFailure { signInError ->
                        app.localAppStorage.accessToken = null
                        showStatusMessage(signInError.message ?: "Couldn't sign in.", isError = true)
                    }
            }
        }
    }

    // TODO: Validate
    private fun startCodeSignIn() {
        val codeSignInAttempt = ++currentCodeSignInAttempt
        statusMessageText.visibility = View.GONE
        codeSignInInstructionsText.text = "Getting a code…"
        codeSignInInstructionsText.visibility = View.VISIBLE
        userCodeText.visibility = View.GONE
        val app = streamChannelerApp
        codeSignInExecutor.execute {
            val deviceSignInCode = try {
                app.api.requestDeviceSignInCode()
            } catch (codeRequestError: Exception) {
                showCodeSignInFailure(codeSignInAttempt, "Couldn't get a sign-in code: ${describeLoadError(codeRequestError)}.")
                return@execute
            }
            runIfCodeSignInAttemptIsCurrent(codeSignInAttempt) { showSignInCode(deviceSignInCode) }
            val signedInDisplayName = try {
                val enteredCodeAccessToken =
                    waitForCodeToBeEntered(app, deviceSignInCode, codeSignInAttempt) ?: return@execute
                saveAccessTokenAndReadDisplayName(app, enteredCodeAccessToken)
            } catch (_: InterruptedException) {
                return@execute
            } catch (codeCheckError: Exception) {
                showCodeSignInFailure(
                    codeSignInAttempt,
                    "Signing in with the code didn't finish: ${describeLoadError(codeCheckError)}. Get a new code to try again.",
                )
                return@execute
            }
            runIfCodeSignInAttemptIsCurrent(codeSignInAttempt) { finishSignIn(signedInDisplayName) }
        }
    }

    // TODO: Validate
    private fun waitForCodeToBeEntered(
        app: StreamChannelerApp,
        deviceSignInCode: DeviceSignInCode,
        codeSignInAttempt: Int,
    ): String? {
        val codeExpiresAt = SystemClock.elapsedRealtime() + deviceSignInCode.expiresIn * 1000L
        while (SystemClock.elapsedRealtime() < codeExpiresAt) {
            Thread.sleep(deviceSignInCode.pollInterval * 1000L)
            if (codeSignInAttempt != currentCodeSignInAttempt) return null
            val accessToken = try {
                app.api.exchangeDeviceSignInCode(deviceSignInCode.deviceCode)
            } catch (codeCheckError: ApiException) {
                if (codeCheckError.statusCode in 400..499) throw codeCheckError
                null
            } catch (_: IOException) {
                null
            }
            if (accessToken == null) continue
            if (codeSignInAttempt != currentCodeSignInAttempt) return null
            return accessToken
        }
        showCodeSignInFailure(codeSignInAttempt, "The code expired. Select $CODE_SIGN_IN_BUTTON_LABEL to get a new one.")
        return null
    }

    // TODO: Validate
    private fun showSignInCode(deviceSignInCode: DeviceSignInCode) {
        val verificationAddress = deviceSignInCode.verificationUri.removePrefix("https://").removePrefix("http://")
        codeSignInInstructionsText.text = "On your phone or computer, go to $verificationAddress and enter this code:"
        userCodeText.text = deviceSignInCode.userCode
        userCodeText.visibility = View.VISIBLE
        codeSignInButton.text = "Get a new code"
    }

    // TODO: Validate
    private fun showCodeSignInFailure(codeSignInAttempt: Int, failureMessage: String) {
        runIfCodeSignInAttemptIsCurrent(codeSignInAttempt) {
            hideSignInCode()
            showStatusMessage(failureMessage, isError = true)
        }
    }

    // TODO: Validate
    private fun runIfCodeSignInAttemptIsCurrent(codeSignInAttempt: Int, action: () -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed || codeSignInAttempt != currentCodeSignInAttempt) return@runOnUiThread
            action()
        }
    }

    // TODO: Validate
    private fun cancelCodeSignIn() {
        currentCodeSignInAttempt++
        hideSignInCode()
    }

    // TODO: Validate
    private fun hideSignInCode() {
        codeSignInInstructionsText.visibility = View.GONE
        userCodeText.visibility = View.GONE
        codeSignInButton.text = CODE_SIGN_IN_BUTTON_LABEL
    }

    // TODO: Validate
    private fun saveAccessTokenAndReadDisplayName(app: StreamChannelerApp, accessToken: String): String? {
        app.localAppStorage.accessToken = accessToken
        val signedInUser = runCatching { app.api.fetchSignedInUser() }.getOrNull()
        signedInUser?.let(app.localAppStorage::saveSignedInUser)
        return signedInUser?.username
    }

    // TODO: Validate
    private fun finishSignIn(signedInDisplayName: String?) {
        val app = streamChannelerApp
        app.localAppStorage.signedInDisplayName = signedInDisplayName
        app.localAppStorage.hasChosenAnonymous = false
        app.guideRepository.clearForAccountChange()
        openGuide()
    }

    // TODO: Validate
    private fun continueAnonymously() {
        cancelCodeSignIn()
        val app = streamChannelerApp
        val wasSignedIn = app.localAppStorage.isSignedIn
        signOutOfDeviceAndForgetAccount(app)
        app.localAppStorage.hasChosenAnonymous = true
        if (wasSignedIn) app.guideRepository.clearForAccountChange()
        openGuide()
    }

    // TODO: Validate
    private fun setSigningInWithPassword(isSigningIn: Boolean) {
        isSigningInWithPassword = isSigningIn
        for (signInControl in listOf(codeSignInButton, signInButton, continueAnonymouslyButton, usernameInput, passwordInput)) {
            signInControl.isEnabled = !isSigningIn
        }
    }

    // TODO: Validate
    private fun showStatusMessage(message: String, isError: Boolean) {
        statusMessageText.text = message
        statusMessageText.setTextColor(if (isError) UiColors.ERROR else UiColors.TEXT_MUTED)
        statusMessageText.visibility = View.VISIBLE
    }

    // TODO: Validate
    private fun openGuide() {
        startActivity(Intent(this, GuideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val EXTRA_SHOW_LOGIN = "com.streamchanneler.app.SHOW_LOGIN"

        // TODO: Validate
        private const val CODE_SIGN_IN_BUTTON_LABEL = "Sign in with a code"

        // TODO: Validate
        fun signOutAndShowLogin(currentActivity: Activity) {
            val app = currentActivity.streamChannelerApp
            if (app.localAppStorage.isSignedIn) {
                app.playbackMonitor.stopMonitoring()
                signOutOfDeviceAndForgetAccount(app)
                app.guideRepository.clearForAccountChange()
            }
            currentActivity.startActivity(
                Intent(currentActivity, LoginActivity::class.java)
                    .putExtra(EXTRA_SHOW_LOGIN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            currentActivity.finish()
        }

        // TODO: Validate
        private fun signOutOfDeviceAndForgetAccount(app: StreamChannelerApp) {
            val signedOutAccessToken = app.localAppStorage.accessToken
            app.localAppStorage.signOut()
            if (signedOutAccessToken.isNullOrEmpty()) return
            Thread {
                runCatching { app.api.signOutDevice(signedOutAccessToken) }
                    .onFailure { Log.w(TAG, "Couldn't sign this device out on the server", it) }
            }.start()
        }

        // TODO: Validate
        private const val TAG = "LoginActivity"
    }
}
