// TODO: Validate
package com.streamchanneler.app.playback.archive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamchanneler.app.playback.PlaybackMonitor
import com.streamchanneler.app.playback.PlaybackOutcome
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.overlayMessageView
import com.streamchanneler.app.ui.guide.GuideActivity
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// TODO: Validate
class ArchivePlayerActivity : ComponentActivity(), Player.Listener {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val itemFilesExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    // TODO: Validate
    private lateinit var playerView: PlayerView
    // TODO: Validate
    private lateinit var playerMessageText: TextView
    // TODO: Validate
    private var archivePlayer: ExoPlayer? = null
    // TODO: Validate
    private var isPlayerControllerShowing = false
    // TODO: Validate
    private var hasReturnedToGuide = false

    // TODO: Validate
    private val videoTitle: String
        get() = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "the video"

    // TODO: Validate
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { controllerVisibility ->
                    isPlayerControllerShowing = controllerVisibility == View.VISIBLE
                },
            )
        }
        playerMessageText = overlayMessageView("Loading $videoTitle…")
        onBackPressedDispatcher.addCallback(this, hideControlsOrLeaveOnBackCallback)
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(
                    playerMessageText,
                    FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                        setMargins(dp(24), dp(24), dp(24), dp(24))
                    },
                )
            },
        )
        val archiveIdentifier = intent.getStringExtra(EXTRA_IDENTIFIER)
        if (archiveIdentifier == null) {
            showPlaybackProblem("This video has no archive.org video.")
            return
        }
        val archiveVideoLink = ArchiveVideoLink(archiveIdentifier, intent.getStringExtra(EXTRA_REQUESTED_FILE_NAME))
        itemFilesExecutor.execute { findVideoUrlAndPlay(archiveVideoLink) }
    }

    // TODO: Validate
    private fun findVideoUrlAndPlay(archiveVideoLink: ArchiveVideoLink) {
        val videoUrlResult = runCatching {
            val playableFileName = archiveVideoLink.requestedFileName?.takeIf { ArchiveOrgVideos.isDirectlyPlayable(it) }
                ?: ArchiveOrgVideos.playableFileName(
                    archiveVideoLink,
                    ArchiveOrgVideos.fetchItemFiles(archiveVideoLink.identifier),
                )
                ?: throw IOException("${archiveVideoLink.identifier} has no video file")
            ArchiveOrgVideos.downloadUrl(archiveVideoLink.identifier, playableFileName)
        }
        mainHandler.post {
            if (isDestroyed) return@post
            videoUrlResult.fold(
                onSuccess = { videoUrl -> startPlayback(videoUrl) },
                onFailure = { videoUrlError -> showPlaybackProblem("Couldn't find $videoTitle on archive.org: ${videoUrlError.message}") },
            )
        }
    }

    // TODO: Validate
    private fun startPlayback(videoUrl: String) {
        val newArchivePlayer = ExoPlayer.Builder(this).build()
        archivePlayer = newArchivePlayer
        newArchivePlayer.addListener(this)
        playerView.player = newArchivePlayer
        newArchivePlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        newArchivePlayer.playWhenReady = true
        newArchivePlayer.prepare()
        playerView.requestFocus()
    }

    // TODO: Validate
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> playerMessageText.visibility = View.GONE
            Player.STATE_ENDED -> returnToGuide(PlaybackOutcome.FINISHED)
        }
    }

    // TODO: Validate
    override fun onPlayerError(error: PlaybackException) {
        showPlaybackProblem("Couldn't play $videoTitle: ${error.errorCodeName}")
    }

    // TODO: Validate
    private fun showPlaybackProblem(problemMessage: String) {
        playerMessageText.text = "$problemMessage\nPress Back to return to the guide."
        playerMessageText.visibility = View.VISIBLE
    }

    // TODO: Validate
    private val hideControlsOrLeaveOnBackCallback = object : OnBackPressedCallback(true) {
        // TODO: Validate
        @OptIn(UnstableApi::class)
        override fun handleOnBackPressed() {
            if (isPlayerControllerShowing) {
                playerView.hideController()
            } else {
                returnToGuide(PlaybackOutcome.LEFT_EARLY)
            }
        }
    }

    // TODO: Validate
    @OptIn(UnstableApi::class)
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
            return true
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    // TODO: Validate
    private fun returnToGuide(playbackOutcome: PlaybackOutcome) {
        if (hasReturnedToGuide) return
        hasReturnedToGuide = true
        archivePlayer?.pause()
        startActivity(
            Intent(this, GuideActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(PlaybackMonitor.EXTRA_PLAYBACK_OUTCOME, playbackOutcome.name),
        )
        finish()
    }

    // TODO: Validate
    override fun onStop() {
        archivePlayer?.pause()
        super.onStop()
    }

    // TODO: Validate
    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        itemFilesExecutor.shutdownNow()
        archivePlayer?.release()
        archivePlayer = null
        super.onDestroy()
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val EXTRA_IDENTIFIER = "com.streamchanneler.app.ARCHIVE_IDENTIFIER"
        // TODO: Validate
        private const val EXTRA_REQUESTED_FILE_NAME = "com.streamchanneler.app.ARCHIVE_REQUESTED_FILE_NAME"
        // TODO: Validate
        private const val EXTRA_VIDEO_TITLE = "com.streamchanneler.app.ARCHIVE_VIDEO_TITLE"

        // TODO: Validate
        fun playArchiveVideoIntent(context: Context, archiveVideoLink: ArchiveVideoLink, videoTitle: String?): Intent =
            Intent(context, ArchivePlayerActivity::class.java)
                .putExtra(EXTRA_IDENTIFIER, archiveVideoLink.identifier)
                .putExtra(EXTRA_REQUESTED_FILE_NAME, archiveVideoLink.requestedFileName)
                .putExtra(EXTRA_VIDEO_TITLE, videoTitle)
    }
}
