// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.streamchanneler.app.data.formatNumberWithoutTrailingZero
import com.streamchanneler.app.playback.StreamingAppLauncher
import com.streamchanneler.app.ui.common.ImageLoader
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import com.streamchanneler.app.ui.common.fullWidthLayoutParams
import com.streamchanneler.app.ui.common.roundedBackground
import com.streamchanneler.app.ui.common.unfocusableTextLabel

// TODO: Validate
private sealed interface ShownGuideDetails {
    // TODO: Validate
    data class Video(val scheduledVideo: ScheduledVideo, val windowStart: Long) : ShownGuideDetails

    // TODO: Validate
    data class Channel(val guideRow: GuideRow) : ShownGuideDetails

    // TODO: Validate
    data class LoadMoreVideos(val guideRow: GuideRow) : ShownGuideDetails
}

// TODO: Validate
internal class GuideDetailPanel(context: Context, private val imageLoader: ImageLoader, thumbnailHeight: Int) {
    // TODO: Validate
    private var shownGuideDetails: ShownGuideDetails? = null

    // TODO: Validate
    private fun isAlreadyShowing(guideDetailsToShow: ShownGuideDetails): Boolean {
        // The guide shows the focused cell again after every rebuild. Filling the panel lays the screen out
        // again, so the panel skips details it already shows.
        if (shownGuideDetails == guideDetailsToShow) return true
        shownGuideDetails = guideDetailsToShow
        return false
    }

    // TODO: Validate
    private val thumbnailImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundedBackground(UiColors.SURFACE, UiColors.BORDER, context.dpFloat(4f), 0)
        clipToOutline = true
    }
    // TODO: Validate
    private val channelNameText =
        context.unfocusableTextLabel("", 12f, UiColors.ACCENT, isBold = true).apply { isAllCaps = true }
    // TODO: Validate
    private val titleText = context.unfocusableTextLabel("", 19f, UiColors.TEXT, isBold = true).apply { maxLines = 2 }
    // TODO: Validate
    private val subtitleText = context.unfocusableTextLabel("", 14f, UiColors.TEXT_MUTED).apply { maxLines = 2 }
    // TODO: Validate
    private val timingText = context.unfocusableTextLabel("", 14f, UiColors.TEXT_MUTED)
    // TODO: Validate
    private val sourceFaviconImage = ImageView(context)
    // TODO: Validate
    private val sourceText = context.unfocusableTextLabel("", 14f, UiColors.TEXT_MUTED)
    // TODO: Validate
    private val actionHintText = context.unfocusableTextLabel("", 14f, UiColors.TEXT_SECONDARY)
    // TODO: Validate
    private val descriptionText = context.unfocusableTextLabel("", 14f, UiColors.TEXT_SECONDARY).apply {
        gravity = Gravity.START or Gravity.TOP
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
        addOnLayoutChangeListener { descriptionView, _, top, _, bottom, _, _, _, _ ->
            val fittingLineCount = maxOf(1, (bottom - top) / (descriptionView as TextView).lineHeight)
            if (descriptionView.maxLines != fittingLineCount) descriptionView.post { descriptionView.maxLines = fittingLineCount }
        }
    }

    // TODO: Validate
    val view: View

    init {
        val sourceRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sourceFaviconImage, LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply { marginEnd = context.dp(8) })
            addView(sourceText)
        }
        val infoColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(channelNameText)
            addView(titleText, context.fullWidthLayoutParams(4))
            addView(subtitleText, context.fullWidthLayoutParams(2))
            addView(timingText, context.fullWidthLayoutParams(4))
            addView(sourceRow, context.fullWidthLayoutParams(4))
            addView(actionHintText, context.fullWidthLayoutParams(8))
        }
        val thumbnailAndInfoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(20), context.dp(16), context.dp(16), context.dp(12))
            addView(
                thumbnailImage,
                LinearLayout.LayoutParams(thumbnailHeight * 16 / 9, thumbnailHeight).apply { marginEnd = context.dp(16) },
            )
            addView(infoColumn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val descriptionColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(16), context.dp(20), context.dp(12))
            addView(descriptionText, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(thumbnailAndInfoRow, LinearLayout.LayoutParams(0, MATCH_PARENT, 0.58f))
            addView(descriptionColumn, LinearLayout.LayoutParams(0, MATCH_PARENT, 0.42f))
        }
    }

    // TODO: Validate
    fun showVideo(scheduledVideo: ScheduledVideo, windowStart: Long) {
        if (isAlreadyShowing(ShownGuideDetails.Video(scheduledVideo, windowStart))) return
        val video = scheduledVideo.video
        val videoStart = windowStart + scheduledVideo.startMinute * MILLISECONDS_PER_MINUTE
        val videoEnd = windowStart + scheduledVideo.endMinute * MILLISECONDS_PER_MINUTE
        channelNameText.isVisible = false
        titleText.text = video.title ?: ""
        val videoDescription = video.description?.trim().orEmpty()
        descriptionText.text = videoDescription
        descriptionText.isVisible = videoDescription.isNotEmpty()
        subtitleText.text = video.subtitle
        subtitleText.isVisible = video.subtitle.isNotEmpty()
        timingText.text = listOfNotNull(
            "${formatSlotTime(videoStart)} - ${formatSlotTime(videoEnd)}",
            video.duration?.let { formatVideoDuration(it) },
            when {
                !video.watched -> null
                video.watchCompleted -> "Completed"
                else -> "Started"
            },
        ).joinToString(" · ")
        sourceText.text = video.pluginKey
        imageLoader.showImageInView(sourceFaviconImage, video.faviconUrl)
        imageLoader.showImageInView(thumbnailImage, video.thumbnailUrl)
        actionHintText.text = if (video.url == null) StreamingAppLauncher.NO_VIDEO_LINK_MESSAGE else ""
        actionHintText.isVisible = video.url == null
    }

    // TODO: Validate
    fun showChannel(guideRow: GuideRow) {
        if (isAlreadyShowing(ShownGuideDetails.Channel(guideRow))) return
        channelNameText.text = formatNumberWithoutTrailingZero(guideRow.channel.number)?.let { "Channel $it" } ?: "Channel"
        channelNameText.isVisible = true
        descriptionText.isVisible = false
        val channelName = guideRow.channel.displayName
        titleText.text = if (guideRow.isFavorite) {
            SpannableString("★ $channelName").apply {
                setSpan(ForegroundColorSpan(UiColors.STAR), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            channelName
        }
        val channelAuthorUsername = guideRow.channel.authorUsername
        subtitleText.text = channelAuthorUsername?.let { "by $it" } ?: ""
        subtitleText.isVisible = channelAuthorUsername != null
        timingText.text = ""
        sourceText.text = ""
        sourceFaviconImage.isVisible = false
        imageLoader.showImageInView(thumbnailImage, guideRow.scheduledVideos.firstOrNull()?.video?.thumbnailUrl)
        val channelStatusText = guideRow.channelStatusText
        actionHintText.text = channelStatusText ?: ""
        actionHintText.isVisible = channelStatusText != null
    }

    // TODO: Validate
    fun showLoadMoreVideos(guideRow: GuideRow) {
        if (isAlreadyShowing(ShownGuideDetails.LoadMoreVideos(guideRow))) return
        channelNameText.text = guideRow.channel.displayName
        channelNameText.isVisible = true
        descriptionText.isVisible = false
        titleText.text = "Load more episodes"
        subtitleText.text = "${guideRow.scheduledVideos.size} videos loaded"
        subtitleText.isVisible = true
        timingText.text = ""
        sourceText.text = ""
        sourceFaviconImage.isVisible = false
        thumbnailImage.isVisible = false
        actionHintText.isVisible = true
        actionHintText.text = guideRow.channelStatusText ?: "Press Select to load more of this channel's episodes"
    }
}
