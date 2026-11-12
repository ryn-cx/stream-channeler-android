// TODO: Validate
package com.streamchanneler.app.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.streamchanneler.app.screenautomation.KeyPressSwallowedUntilReleased
import com.streamchanneler.app.screenautomation.ScreenAutomationFeature
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import com.streamchanneler.app.screenautomation.StatusMessageOverlay
import com.streamchanneler.app.screenautomation.isBackKeyCode
import com.streamchanneler.app.screenautomation.isOkKeyCode

// TODO: Validate
class NextEpisodeCountdown(
    private val screenAutomationService: ScreenAutomationService,
    private val playbackMonitor: PlaybackMonitor,
) : ScreenAutomationFeature {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val countdownOverlay = StatusMessageOverlay(screenAutomationService)
    // TODO: Validate
    private var countdownEndsAt = 0L
    // TODO: Validate
    private var onCountdownFinished: (() -> Unit)? = null
    // TODO: Validate
    private var onCountdownCancelled: (() -> Unit)? = null
    // TODO: Validate
    private val okKeyPressPlayingNow = KeyPressSwallowedUntilReleased(::stopFilteringKeyEventsWhenUnused)

    // TODO: Validate
    private val backKeyPressCancelling = KeyPressSwallowedUntilReleased(::stopFilteringKeyEventsWhenUnused)

    // TODO: Validate
    private val isCountingDown: Boolean
        get() = onCountdownFinished != null

    // TODO: Validate
    override var filtersKeyEvents = false
        private set

    // TODO: Validate
    private val showSecondsLeftOrFinishRunnable = Runnable { showSecondsLeftOrFinish() }

    // TODO: Validate
    fun start(countdownLength: Int, onCountdownFinished: () -> Unit, onCountdownCancelled: () -> Unit) {
        stopCountingDown()
        this.onCountdownFinished = onCountdownFinished
        this.onCountdownCancelled = onCountdownCancelled
        val countdownMillis = countdownLength * 1_000L
        countdownEndsAt = SystemClock.elapsedRealtime() + countdownMillis
        setFiltersKeyEvents(true)
        countdownOverlay.show(
            countdownMessage(countdownLength.toLong()),
            countdownMillis + OVERLAY_GRACE,
        )
        mainHandler.post(showSecondsLeftOrFinishRunnable)
    }

    // TODO: Validate
    fun cancelSilently() {
        if (!isCountingDown) return
        stopCountingDown()
        countdownOverlay.hide()
    }

    // TODO: Validate
    private fun finishNow() {
        val finishedCallback = onCountdownFinished ?: return
        stopCountingDown()
        countdownOverlay.hide()
        finishedCallback()
    }

    // TODO: Validate
    override fun onKeyEvent(keyEvent: KeyEvent): Boolean = when {
        isOkKeyCode(keyEvent.keyCode) ->
            okKeyPressPlayingNow.onKeyEvent(keyEvent, findPressTarget = { onCountdownFinished }) { finishNow() }
        isBackKeyCode(keyEvent.keyCode) ->
            backKeyPressCancelling.onKeyEvent(keyEvent, findPressTarget = { onCountdownCancelled }) { cancelBecauseViewerAsked() }
        else -> false
    }

    // TODO: Validate
    private fun stopFilteringKeyEventsWhenUnused() {
        val isSwallowingKey = okKeyPressPlayingNow.isSwallowingKey || backKeyPressCancelling.isSwallowingKey
        if (!isCountingDown && !isSwallowingKey) setFiltersKeyEvents(false)
    }

    // TODO: Validate
    override fun onServiceConnected() = playbackMonitor.connectNextEpisodeCountdown(this)

    // TODO: Validate
    override fun onServiceDestroyed() {
        finishNow()
        playbackMonitor.disconnectNextEpisodeCountdown(this)
    }

    // TODO: Validate
    private fun setFiltersKeyEvents(countdownFiltersKeyEvents: Boolean) {
        filtersKeyEvents = countdownFiltersKeyEvents
        screenAutomationService.updateKeyEventFiltering()
    }

    // TODO: Validate
    private fun cancelBecauseViewerAsked() {
        val cancelledCallback = onCountdownCancelled ?: return
        stopCountingDown()
        countdownOverlay.show("Next episode cancelled", CANCELLED_MESSAGE_DURATION)
        cancelledCallback()
    }

    // TODO: Validate
    private fun showSecondsLeftOrFinish() {
        val millisLeftInCountdown = countdownEndsAt - SystemClock.elapsedRealtime()
        if (millisLeftInCountdown <= 0) {
            finishNow()
            return
        }
        val secondsLeftInCountdown = (millisLeftInCountdown + 999) / 1_000
        countdownOverlay.updateMessageIfShown(countdownMessage(secondsLeftInCountdown))
        mainHandler.postDelayed(
            showSecondsLeftOrFinishRunnable,
            millisLeftInCountdown - (secondsLeftInCountdown - 1) * 1_000,
        )
    }

    // TODO: Validate
    private fun stopCountingDown() {
        mainHandler.removeCallbacks(showSecondsLeftOrFinishRunnable)
        onCountdownFinished = null
        onCountdownCancelled = null
        stopFilteringKeyEventsWhenUnused()
    }

    // TODO: Validate
    private fun countdownMessage(secondsLeftInCountdown: Long): String {
        val secondsLabel = if (secondsLeftInCountdown == 1L) "1 second" else "$secondsLeftInCountdown seconds"
        return "Playing the next episode in $secondsLabel. " +
            countdownOverlay.skipInstruction("play it now") +
            countdownOverlay.cancelInstruction
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val OVERLAY_GRACE = 1_000L

        // TODO: Validate
        const val CANCELLED_MESSAGE_DURATION = 3_000L
    }
}
