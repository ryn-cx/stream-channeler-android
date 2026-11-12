// TODO: Validate
package com.streamchanneler.app.ui.upnext

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.playback.UpNextAnnouncer
import com.streamchanneler.app.streamChannelerApp
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.actionButton
import com.streamchanneler.app.ui.common.centeredTextLabel
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.fullWidthLayoutParams
import com.streamchanneler.app.ui.common.zoomedContext

// TODO: Validate
class UpNextActivity : ComponentActivity() {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private var hasFinishedUpNext = false
    // TODO: Validate
    private val playUpNextRunnable = Runnable { finishUpNext(RESULT_OK) }
    // TODO: Validate
    private lateinit var cancelButton: Button
    // TODO: Validate
    private val nextVideo by lazy {
        AnnouncedVideo(
            title = intent.getStringExtra(EXTRA_VIDEO_TITLE),
            subtitle = intent.getStringExtra(EXTRA_VIDEO_SUBTITLE),
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL),
        )
    }
    // TODO: Validate
    private val followingVideo by lazy {
        if (intent.hasExtra(EXTRA_FOLLOWING_VIDEO_TITLE)) {
            AnnouncedVideo(
                title = intent.getStringExtra(EXTRA_FOLLOWING_VIDEO_TITLE),
                subtitle = intent.getStringExtra(EXTRA_FOLLOWING_VIDEO_SUBTITLE),
                thumbnailUrl = intent.getStringExtra(EXTRA_FOLLOWING_THUMBNAIL_URL),
            )
        } else {
            null
        }
    }

    // TODO: Validate
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.zoomedContext())
    }

    // TODO: Validate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUpNextLayout())
        onBackPressedDispatcher.addCallback(this, cancelUpNextOnBackCallback)
        schedulePlayingUpNextVideo(LONGEST_ANNOUNCEMENT_DURATION)
        val announcementText = upNextAnnouncement(
            nextVideo.title,
            nextVideo.subtitle.orEmpty(),
            followingVideo?.title,
            followingVideo?.subtitle,
        )
        UpNextAnnouncer.announce(this, announcementText) { wasAnnouncementSpoken ->
            schedulePlayingUpNextVideo(if (wasAnnouncementSpoken) PAUSE_AFTER_ANNOUNCEMENT else SILENT_ANNOUNCEMENT_DURATION)
        }
    }

    // TODO: Validate
    private fun buildUpNextLayout(): View {
        val upNextVideosRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            addView(videoCard("UP NEXT", UiColors.ACCENT, nextVideo, thumbnailWidth = 400, titleTextSize = 30f))
            followingVideo?.let { followingVideo ->
                addView(
                    videoCard("THEN", UiColors.TEXT_MUTED, followingVideo, thumbnailWidth = 288, titleTextSize = 22f),
                    LinearLayout.LayoutParams(dp(288), WRAP_CONTENT).apply {
                        marginStart = dp(48)
                        topMargin = dp(10)
                    },
                )
            }
        }
        val upNextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            intent.getStringExtra(EXTRA_CHANNEL_NAME)?.let { channelName ->
                addView(twoLineCenteredLabel(channelName.uppercase(), 18f, UiColors.TEXT_MUTED, isBold = true))
            }
            addView(upNextVideosRow, fullWidthLayoutParams(24))
            addView(twoLineCenteredLabel("Select to play now · Back to cancel", 14f, UiColors.TEXT_MUTED), fullWidthLayoutParams(24))
            cancelButton = actionButton("Cancel") { finishUpNext(RESULT_CANCELED) }
            addView(
                LinearLayout(this@UpNextActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(actionButton("Play now") { finishUpNext(RESULT_OK) })
                    addView(cancelButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(12) })
                },
                fullWidthLayoutParams(24),
            )
        }
        return FrameLayout(this).apply {
            setBackgroundColor(UiColors.BACKGROUND)
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(upNextColumn, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        }
    }

    // TODO: Validate
    private fun videoCard(
        headingText: String,
        headingColor: Int,
        announcedVideo: AnnouncedVideo,
        thumbnailWidth: Int,
        titleTextSize: Float,
    ): LinearLayout {
        val videoThumbnailImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(UiColors.SURFACE)
        }
        streamChannelerApp.imageLoader.showImageInView(videoThumbnailImage, announcedVideo.thumbnailUrl)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(thumbnailWidth), WRAP_CONTENT)
            addView(twoLineCenteredLabel(headingText, titleTextSize, headingColor, isBold = true))
            addView(
                videoThumbnailImage,
                LinearLayout.LayoutParams(dp(thumbnailWidth), dp(thumbnailWidth * 9 / 16)).apply { topMargin = dp(10) },
            )
            addView(
                twoLineCenteredLabel(announcedVideo.title.orEmpty(), titleTextSize, UiColors.TEXT, isBold = true),
                fullWidthLayoutParams(12),
            )
            if (!announcedVideo.subtitle.isNullOrEmpty()) {
                addView(
                    twoLineCenteredLabel(announcedVideo.subtitle, titleTextSize * 0.65f, UiColors.TEXT_SECONDARY),
                    fullWidthLayoutParams(4),
                )
            }
        }
    }

    // TODO: Validate
    private fun twoLineCenteredLabel(text: String, textSize: Float, color: Int, isBold: Boolean = false): TextView =
        centeredTextLabel(text, textSize, color, isBold).apply { maxLines = 2 }

    // TODO: Validate
    private fun schedulePlayingUpNextVideo(delay: Long) {
        mainHandler.removeCallbacks(playUpNextRunnable)
        mainHandler.postDelayed(playUpNextRunnable, delay)
    }

    // TODO: Validate
    private val cancelUpNextOnBackCallback = object : OnBackPressedCallback(true) {
        // TODO: Validate
        override fun handleOnBackPressed() {
            finishUpNext(RESULT_CANCELED)
        }
    }

    // TODO: Validate
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isKeyReleased = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_B -> if (isKeyReleased) onBackPressedDispatcher.onBackPressed()
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> if (isKeyReleased) finishUpNext(if (cancelButton.isFocused) RESULT_CANCELED else RESULT_OK)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    // TODO: Validate
    private fun finishUpNext(upNextResultCode: Int) {
        if (hasFinishedUpNext) return
        hasFinishedUpNext = true
        mainHandler.removeCallbacks(playUpNextRunnable)
        UpNextAnnouncer.stopAnnouncement()
        setResult(upNextResultCode)
        finish()
    }

    // TODO: Validate
    override fun onStop() {
        if (!isFinishing) finishUpNext(RESULT_CANCELED)
        super.onStop()
    }

    // TODO: Validate
    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // TODO: Validate
    companion object {
        // TODO: Validate
        private const val EXTRA_CHANNEL_NAME = "com.streamchanneler.app.UP_NEXT_CHANNEL_NAME"
        // TODO: Validate
        private const val EXTRA_VIDEO_TITLE = "com.streamchanneler.app.UP_NEXT_VIDEO_TITLE"
        // TODO: Validate
        private const val EXTRA_VIDEO_SUBTITLE = "com.streamchanneler.app.UP_NEXT_VIDEO_SUBTITLE"
        // TODO: Validate
        private const val EXTRA_THUMBNAIL_URL = "com.streamchanneler.app.UP_NEXT_THUMBNAIL_URL"
        // TODO: Validate
        private const val EXTRA_FOLLOWING_VIDEO_TITLE = "com.streamchanneler.app.UP_NEXT_FOLLOWING_VIDEO_TITLE"
        // TODO: Validate
        private const val EXTRA_FOLLOWING_VIDEO_SUBTITLE = "com.streamchanneler.app.UP_NEXT_FOLLOWING_VIDEO_SUBTITLE"
        // TODO: Validate
        private const val EXTRA_FOLLOWING_THUMBNAIL_URL = "com.streamchanneler.app.UP_NEXT_FOLLOWING_THUMBNAIL_URL"

        // TODO: Validate
        private const val PAUSE_AFTER_ANNOUNCEMENT = 1_000L

        // TODO: Validate
        private const val SILENT_ANNOUNCEMENT_DURATION = 5_000L

        // TODO: Validate
        private const val LONGEST_ANNOUNCEMENT_DURATION = 30_000L

        // TODO: Validate
        fun upNextAnnouncementIntent(context: Context, channelName: String?, nextVideo: GuideVideo, followingVideo: GuideVideo?): Intent {
            val upNextIntent = Intent(context, UpNextActivity::class.java)
                .putExtra(EXTRA_CHANNEL_NAME, channelName)
                .putExtra(EXTRA_VIDEO_TITLE, nextVideo.title)
                .putExtra(EXTRA_VIDEO_SUBTITLE, nextVideo.subtitle)
                .putExtra(EXTRA_THUMBNAIL_URL, nextVideo.thumbnailUrl)
            if (followingVideo != null) {
                upNextIntent
                    .putExtra(EXTRA_FOLLOWING_VIDEO_TITLE, followingVideo.title.orEmpty())
                    .putExtra(EXTRA_FOLLOWING_VIDEO_SUBTITLE, followingVideo.subtitle)
                    .putExtra(EXTRA_FOLLOWING_THUMBNAIL_URL, followingVideo.thumbnailUrl)
            }
            return upNextIntent
        }
    }
}

// TODO: Validate
private class AnnouncedVideo(val title: String?, val subtitle: String?, val thumbnailUrl: String?)

// TODO: Validate
private val SEASON_AND_EPISODE_PREFIX = Regex("""^S\d+\s*Ep\d+\s*(·\s*)?""")

// TODO: Validate
fun upNextAnnouncement(
    nextVideoTitle: String?,
    nextVideoSubtitle: String,
    followingVideoTitle: String? = null,
    followingVideoSubtitle: String? = null,
): String {
    val spokenNextVideoName = spokenVideoName(nextVideoTitle, nextVideoSubtitle)
    val nextVideoAnnouncement = if (spokenNextVideoName.isEmpty()) "Up next." else "Up next: $spokenNextVideoName."
    if (followingVideoTitle == null && followingVideoSubtitle == null) return nextVideoAnnouncement
    val isFollowingVideoSameShow = followingVideoTitle?.trim() == nextVideoTitle?.trim()
    val spokenFollowingVideoName = spokenVideoName(
        followingVideoTitle.takeUnless { isFollowingVideoSameShow },
        followingVideoSubtitle.orEmpty(),
    ).ifEmpty { spokenVideoName(followingVideoTitle, "") }
    if (spokenFollowingVideoName.isEmpty()) return nextVideoAnnouncement
    return "$nextVideoAnnouncement Then: $spokenFollowingVideoName."
}

// TODO: Validate
private fun spokenVideoName(videoTitle: String?, videoSubtitle: String): String {
    val spokenEpisodeName = videoSubtitle.replace(SEASON_AND_EPISODE_PREFIX, "").trim()
    return listOfNotNull(
        videoTitle?.trim()?.takeIf { it.isNotEmpty() },
        spokenEpisodeName.takeIf { it.isNotEmpty() },
    ).joinToString(". ")
}
