// TODO: Validate
package com.streamchanneler.app.playback

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File

// TODO: Validate
object UpNextAnnouncer {
    // TODO: Validate
    private const val WARM_UP_UTTERANCE_ID = "upNextWarmUp"
    // TODO: Validate
    private const val ANNOUNCEMENT_UTTERANCE_ID = "upNextAnnouncement"
    // TODO: Validate
    private const val WARM_UP_TEXT = "Up next"
    // TODO: Validate
    private const val WARM_UP_FILE_NAME = "up-next-warm-up.wav"

    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private var upNextTextToSpeech: TextToSpeech? = null
    // TODO: Validate
    private var isTextToSpeechReady = false
    // TODO: Validate
    private var warmUpFile: File? = null
    // TODO: Validate
    private var pendingAnnouncementText: String? = null
    // TODO: Validate
    private var onAnnouncementFinished: ((Boolean) -> Unit)? = null

    // TODO: Validate
    fun warmUp(context: Context) {
        if (upNextTextToSpeech != null) return
        warmUpFile = File(context.cacheDir, WARM_UP_FILE_NAME)
        isTextToSpeechReady = false
        lateinit var createdTextToSpeech: TextToSpeech
        createdTextToSpeech = TextToSpeech(context.applicationContext) { textToSpeechStatus ->
            mainHandler.post { onTextToSpeechInitialized(createdTextToSpeech, textToSpeechStatus) }
        }
        upNextTextToSpeech = createdTextToSpeech
    }

    // TODO: Validate
    private fun onTextToSpeechInitialized(initializedTextToSpeech: TextToSpeech, textToSpeechStatus: Int) {
        if (initializedTextToSpeech !== upNextTextToSpeech) return
        if (textToSpeechStatus != TextToSpeech.SUCCESS) {
            finishAnnouncement(wasAnnouncementSpoken = false)
            shutdown()
            return
        }
        isTextToSpeechReady = true
        initializedTextToSpeech.setOnUtteranceProgressListener(UpNextUtteranceListener)
        val waitingAnnouncementText = pendingAnnouncementText
        if (waitingAnnouncementText != null) {
            speakAnnouncement(initializedTextToSpeech, waitingAnnouncementText)
        } else {
            warmUpFile?.let { initializedTextToSpeech.synthesizeToFile(WARM_UP_TEXT, Bundle(), it, WARM_UP_UTTERANCE_ID) }
        }
    }

    // TODO: Validate
    fun announce(context: Context, announcementText: String, onFinished: (Boolean) -> Unit) {
        onAnnouncementFinished = onFinished
        warmUp(context)
        val readyTextToSpeech = upNextTextToSpeech
        if (isTextToSpeechReady && readyTextToSpeech != null) {
            speakAnnouncement(readyTextToSpeech, announcementText)
        } else {
            pendingAnnouncementText = announcementText
        }
    }

    // TODO: Validate
    private fun speakAnnouncement(readyTextToSpeech: TextToSpeech, announcementText: String) {
        pendingAnnouncementText = null
        val speakResult = readyTextToSpeech.speak(announcementText, TextToSpeech.QUEUE_FLUSH, null, ANNOUNCEMENT_UTTERANCE_ID)
        if (speakResult != TextToSpeech.SUCCESS) finishAnnouncement(wasAnnouncementSpoken = false)
    }

    // TODO: Validate
    private fun finishAnnouncement(wasAnnouncementSpoken: Boolean) {
        val finishedAnnouncementCallback = onAnnouncementFinished ?: return
        onAnnouncementFinished = null
        pendingAnnouncementText = null
        finishedAnnouncementCallback(wasAnnouncementSpoken)
    }

    // TODO: Validate
    fun stopAnnouncement() {
        onAnnouncementFinished = null
        pendingAnnouncementText = null
        upNextTextToSpeech?.stop()
    }

    // TODO: Validate
    fun shutdown() {
        stopAnnouncement()
        upNextTextToSpeech?.shutdown()
        upNextTextToSpeech = null
        isTextToSpeechReady = false
        warmUpFile?.delete()
    }

    // TODO: Validate
    private object UpNextUtteranceListener : UtteranceProgressListener() {
        // TODO: Validate
        override fun onStart(utteranceId: String?) = Unit

        // TODO: Validate
        override fun onDone(utteranceId: String?) {
            if (utteranceId == ANNOUNCEMENT_UTTERANCE_ID) {
                mainHandler.post { finishAnnouncement(wasAnnouncementSpoken = true) }
            }
        }

        // TODO: Validate
        @Deprecated("UtteranceProgressListener.onError(String) is deprecated in API 21")
        override fun onError(utteranceId: String?) {
            if (utteranceId == ANNOUNCEMENT_UTTERANCE_ID) {
                mainHandler.post { finishAnnouncement(wasAnnouncementSpoken = false) }
            }
        }
    }
}
