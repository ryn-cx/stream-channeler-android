// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.formatNumberWithoutTrailingZero
import com.streamchanneler.app.ui.common.ImageLoader
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import com.streamchanneler.app.ui.common.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// TODO: Validate
internal class GuideGridDrawState(
    val guideRows: List<GuideRow>,
    val windowStart: Long,
    val timelineLength: Int,
    val focusedRowIndex: Int,
    val focusedVideoIndex: Int,
    val isGridFocused: Boolean,
    val gridScrollX: Int,
    val gridScrollY: Int,
    val viewWidth: Int,
    val viewHeight: Int,
    val playingEpisodeId: String?,
    val footerMessage: String?,
    val emptyMessage: String,
    val isCondensed: Boolean,
    val showsSourceLogos: Boolean,
)

// TODO: Validate
private class ChannelLabelArea(
    val left: Float,
    val rowTop: Float,
    val width: Int,
    val tagText: String?,
    val isHighlighted: Boolean,
)

// TODO: Validate
private data class EllipsizedTextKey(val text: String, val textPaint: TextPaint, val availableWidth: Float)

// TODO: Validate
private data class ChannelNameLayoutKey(val channelNameText: String, val width: Int, val maxLines: Int)

// TODO: Validate
internal class GuideGridRenderer(
    private val context: Context,
    private val metrics: GuideGridMetrics,
    private val imageLoader: ImageLoader,
    private val requestRedraw: () -> Unit,
) {
    // TODO: Validate
    private val channelColumnWidth = metrics.channelColumnWidth

    // TODO: Validate
    private val headerHeight = metrics.headerHeight
    // TODO: Validate
    private val pixelsPerMinute = metrics.pixelsPerMinute

    // TODO: Validate
    private val cellPadding = context.dpFloat(8f)

    // TODO: Validate
    private val faviconTextGap = cellPadding * 0.75f

    // TODO: Validate
    private val channelLabelPadding = cellPadding * 1.5f

    // TODO: Validate
    private val faviconSize = context.dp(20)

    // TODO: Validate
    private val regularChannelLogoMaxHeight = context.dpFloat(28f)

    // TODO: Validate
    private val condensedChannelLogoMaxHeight = context.dpFloat(16f)

    // TODO: Validate
    private val channelLogoLightBackgroundPaddingX = context.dpFloat(6f)

    // TODO: Validate
    private val channelLogoLightBackgroundPaddingY = context.dpFloat(2f)

    // TODO: Validate
    private val cornerRadius = context.dpFloat(4f)

    // TODO: Validate
    private val focusStrokeWidth = context.dpFloat(2f)

    // TODO: Validate
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // TODO: Validate
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // TODO: Validate
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // TODO: Validate
    private val videoTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(14f)
        typeface = Typeface.DEFAULT_BOLD
    }
    // TODO: Validate
    private val videoSubtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = context.sp(12f) }
    // TODO: Validate
    private val channelNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(14f)
        typeface = Typeface.DEFAULT_BOLD
        color = UiColors.TEXT
    }
    // TODO: Validate
    private val channelNumberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(14f)
        color = UiColors.TEXT_MUTED
    }
    // TODO: Validate
    private val smallLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(11f)
        typeface = Typeface.DEFAULT_BOLD
    }
    // TODO: Validate
    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(12f)
        color = UiColors.TEXT_MUTED
    }
    // TODO: Validate
    private val messagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(16f)
        color = UiColors.TEXT_MUTED
        textAlign = Paint.Align.CENTER
    }
    // TODO: Validate
    private val slotTimeFormat = SimpleDateFormat("M/d h:mma", Locale.getDefault())
    // TODO: Validate
    private val reusableRect = RectF()
    // TODO: Validate
    private val reusableBitmapDestination = Rect()
    // TODO: Validate
    private val channelLogoUrlToNeedsLightBackground = mutableMapOf<String, Boolean>()
    // TODO: Validate
    private val redrawWhenBitmapLoads: (Bitmap) -> Unit = { requestRedraw() }
    // TODO: Validate
    private val ellipsizedTextCache = LruCache<EllipsizedTextKey, String>(ELLIPSIZED_TEXT_CACHE_SIZE)
    // TODO: Validate
    private val channelNameLayoutCache = LruCache<ChannelNameLayoutKey, StaticLayout>(CHANNEL_NAME_LAYOUT_CACHE_SIZE)
    // TODO: Validate
    private val slotStartToTimeLabel = LruCache<Long, String>(SLOT_TIME_LABEL_CACHE_SIZE)

    // TODO: Validate
    private val GuideGridDrawState.rowHeight: Int
        get() = metrics.rowHeight(isCondensed)

    // TODO: Validate
    fun draw(canvas: Canvas, drawState: GuideGridDrawState) {
        val guideRows = drawState.guideRows
        if (guideRows.isEmpty()) {
            drawCenteredEmptyMessage(canvas, drawState)
            return
        }
        val rowHeight = drawState.rowHeight
        val gridScrollY = drawState.gridScrollY
        val firstVisibleRowIndex = max(0, gridScrollY / rowHeight)
        val lastVisibleRowIndex =
            min(guideRows.lastIndex, (gridScrollY + metrics.rowsViewportHeight(drawState.viewHeight)) / rowHeight)

        canvas.save()
        canvas.clipRect(channelColumnWidth, headerHeight, drawState.viewWidth, drawState.viewHeight)
        for (rowIndex in firstVisibleRowIndex..lastVisibleRowIndex) drawRowTrack(canvas, drawState, rowIndex)
        drawNowLine(canvas, drawState)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0, headerHeight, channelColumnWidth, drawState.viewHeight)
        for (rowIndex in firstVisibleRowIndex..lastVisibleRowIndex) drawRowLabel(canvas, drawState, rowIndex)
        canvas.restore()

        drawFooter(canvas, drawState)
        drawHeader(canvas, drawState)
    }

    // TODO: Validate
    private fun topOfRow(drawState: GuideGridDrawState, rowIndex: Int): Float =
        (headerHeight + rowIndex * drawState.rowHeight - drawState.gridScrollY).toFloat()

    // TODO: Validate
    private fun drawRowTrack(canvas: Canvas, drawState: GuideGridDrawState, rowIndex: Int) {
        val guideRow = drawState.guideRows[rowIndex]
        val viewWidth = drawState.viewWidth.toFloat()
        val rowTop = topOfRow(drawState, rowIndex)
        val rowBottom = rowTop + drawState.rowHeight
        fillPaint.color = if (guideRow.isCurrentChannelRow) UiColors.ACCENT_TINT else UiColors.SURFACE
        canvas.drawRect(channelColumnWidth.toFloat(), rowTop, viewWidth, rowBottom, fillPaint)

        val visibleStartMinute = drawState.gridScrollX / pixelsPerMinute
        val visibleEndMinute =
            (drawState.gridScrollX + metrics.trackViewportWidth(drawState.viewWidth)) / pixelsPerMinute
        val scheduledVideos = guideRow.scheduledVideos
        for (videoIndex in firstVideoIndexEndingAtOrAfter(scheduledVideos, visibleStartMinute) until scheduledVideos.size) {
            val scheduledVideo = scheduledVideos[videoIndex]
            if (scheduledVideo.startMinute > visibleEndMinute) break
            drawVideoCell(canvas, drawState, rowIndex, videoIndex, scheduledVideo, rowTop)
        }
        val loadMoreVideosStartMinute = guideRow.scheduledLength
        if (guideRow.channel.hasMoreVideos &&
            loadMoreVideosStartMinute + LOAD_MORE_VIDEOS_LENGTH >= visibleStartMinute &&
            loadMoreVideosStartMinute <= visibleEndMinute
        ) {
            drawLoadMoreVideosCell(canvas, drawState, rowIndex, guideRow, loadMoreVideosStartMinute, rowTop)
        }
        fillPaint.color = if (guideRow.isCurrentChannelRow) UiColors.ACCENT else UiColors.BORDER
        val dividerHeight = if (guideRow.isCurrentChannelRow) focusStrokeWidth else 1f
        canvas.drawRect(channelColumnWidth.toFloat(), rowBottom - dividerHeight, viewWidth, rowBottom, fillPaint)
    }

    // TODO: Validate
    private fun firstVideoIndexEndingAtOrAfter(scheduledVideos: List<ScheduledVideo>, minute: Float): Int {
        var lowestCandidateIndex = 0
        var indexAfterLastCandidate = scheduledVideos.size
        while (lowestCandidateIndex < indexAfterLastCandidate) {
            val middleIndex = (lowestCandidateIndex + indexAfterLastCandidate) ushr 1
            if (scheduledVideos[middleIndex].endMinute >= minute) {
                indexAfterLastCandidate = middleIndex
            } else {
                lowestCandidateIndex = middleIndex + 1
            }
        }
        return lowestCandidateIndex
    }

    // TODO: Validate
    private fun drawLoadMoreVideosCell(
        canvas: Canvas,
        drawState: GuideGridDrawState,
        rowIndex: Int,
        guideRow: GuideRow,
        loadMoreVideosStartMinute: Int,
        rowTop: Float,
    ) {
        setReusableRectToCell(drawState, loadMoreVideosStartMinute, LOAD_MORE_VIDEOS_LENGTH, rowTop)

        val isFocusedCell =
            rowIndex == drawState.focusedRowIndex && guideRow.isLoadMoreVideosIndex(drawState.focusedVideoIndex)
        val isHighlightedCell = isFocusedCell && drawState.isGridFocused
        fillPaint.color = if (isHighlightedCell) UiColors.ACCENT else UiColors.SURFACE
        canvas.drawRect(reusableRect, fillPaint)
        if (isFocusedCell) {
            drawCellOutline(canvas, focusOutlineColor(isHighlightedCell), focusStrokeWidth, outlineAlpha = 255)
        } else {
            drawCellRightBorder(canvas, borderAlpha = 255)
        }

        videoTitlePaint.color = if (isHighlightedCell) UiColors.ON_ACCENT else UiColors.ACCENT
        videoTitlePaint.alpha = 255
        val loadMoreVideosText = ellipsize(
            if (guideRow.isLoadingMoreVideos) "Loading…" else "Load more",
            videoTitlePaint,
            reusableRect.width() - cellPadding * 2,
        )
        val textLeft = reusableRect.centerX() - videoTitlePaint.measureText(loadMoreVideosText) / 2f
        val textBaseline = textBaselineCenteredOn(reusableRect.centerY(), videoTitlePaint)
        canvas.save()
        canvas.clipRect(reusableRect)
        canvas.drawText(loadMoreVideosText, textLeft, textBaseline, videoTitlePaint)
        canvas.restore()
    }

    // TODO: Validate
    private fun setReusableRectToCell(drawState: GuideGridDrawState, cellStartMinute: Int, cellLength: Int, rowTop: Float) {
        val cellLeft = channelColumnWidth + cellStartMinute * pixelsPerMinute - drawState.gridScrollX
        val cellRight = channelColumnWidth + (cellStartMinute + cellLength) * pixelsPerMinute - drawState.gridScrollX
        reusableRect.set(cellLeft, rowTop, cellRight, rowTop + drawState.rowHeight)
    }

    // TODO: Validate
    private fun focusOutlineColor(isHighlighted: Boolean): Int = if (isHighlighted) UiColors.TEXT else UiColors.ACCENT

    // TODO: Validate
    private fun drawCellOutline(canvas: Canvas, outlineColor: Int, outlineWidth: Float, outlineAlpha: Int) {
        strokePaint.color = outlineColor
        strokePaint.strokeWidth = outlineWidth
        strokePaint.alpha = outlineAlpha
        val outlineInset = outlineWidth / 2f
        canvas.drawRect(
            reusableRect.left + outlineInset,
            reusableRect.top + outlineInset,
            reusableRect.right - outlineInset,
            reusableRect.bottom - outlineInset,
            strokePaint,
        )
    }

    // TODO: Validate
    private fun drawCellRightBorder(canvas: Canvas, borderAlpha: Int) {
        fillPaint.color = UiColors.BORDER
        fillPaint.alpha = borderAlpha
        canvas.drawRect(reusableRect.right - 1f, reusableRect.top, reusableRect.right, reusableRect.bottom, fillPaint)
    }

    // TODO: Validate
    private fun textBaselineCenteredOn(centerY: Float, textPaint: Paint): Float =
        centerY - (textPaint.ascent() + textPaint.descent()) / 2f

    // TODO: Validate
    private fun cachedBitmapOrStartLoading(imageUrl: String?): Bitmap? {
        val cachedBitmap = imageLoader.cachedBitmap(imageUrl)
        if (cachedBitmap == null && imageUrl != null) imageLoader.loadBitmap(imageUrl, redrawWhenBitmapLoads)
        return cachedBitmap
    }

    // TODO: Validate
    fun videoTextFittingLength(video: GuideVideo, isCondensed: Boolean, showsSourceLogos: Boolean): Int {
        var widestTextWidth = videoTitlePaint.measureText(video.title ?: "")
        if (!isCondensed) widestTextWidth = max(widestTextWidth, videoSubtitlePaint.measureText(video.subtitle))
        val faviconWidth = if (showsSourceLogos && video.faviconUrl != null) faviconSize + faviconTextGap else 0f
        val cellWidth = cellPadding * 2 + faviconWidth + widestTextWidth + 1
        return max(1, ceil(cellWidth / pixelsPerMinute).toInt())
    }

    // TODO: Validate
    private fun drawVideoCell(
        canvas: Canvas,
        drawState: GuideGridDrawState,
        rowIndex: Int,
        videoIndex: Int,
        scheduledVideo: ScheduledVideo,
        rowTop: Float,
    ) {
        val video = scheduledVideo.video
        setReusableRectToCell(drawState, scheduledVideo.startMinute, scheduledVideo.length, rowTop)

        val isFocusedCell = rowIndex == drawState.focusedRowIndex && videoIndex == drawState.focusedVideoIndex
        val isHighlightedCell = isFocusedCell && drawState.isGridFocused
        val isPlaying = video.episodeId == drawState.playingEpisodeId
        val hasAccentFill = isHighlightedCell || isPlaying
        val cellAlpha = if (video.watched && !isFocusedCell) WATCHED_ALPHA else 255
        val fillAlpha = if (isPlaying && !isHighlightedCell) PLAYING_FILL_ALPHA else 255
        fillPaint.color = if (hasAccentFill) UiColors.ACCENT else UiColors.SURFACE_RAISED
        fillPaint.alpha = fillAlpha * cellAlpha / 255
        canvas.drawRect(reusableRect, fillPaint)
        if (isFocusedCell || isPlaying) {
            val outlineWidth = if (isFocusedCell) focusStrokeWidth else 1f
            drawCellOutline(canvas, focusOutlineColor(isHighlightedCell), outlineWidth, cellAlpha)
        } else {
            drawCellRightBorder(canvas, cellAlpha)
        }

        canvas.save()
        canvas.clipRect(reusableRect)
        var textLeft = max(reusableRect.left, channelColumnWidth.toFloat()) + cellPadding
        val faviconBitmap = if (drawState.showsSourceLogos) cachedBitmapOrStartLoading(video.faviconUrl) else null
        if (faviconBitmap != null && reusableRect.right - textLeft > faviconSize * 2) {
            val faviconTop = (reusableRect.centerY() - faviconSize / 2f).roundToInt()
            reusableBitmapDestination.set(textLeft.roundToInt(), faviconTop, textLeft.roundToInt() + faviconSize, faviconTop + faviconSize)
            bitmapPaint.alpha = cellAlpha
            canvas.drawBitmap(faviconBitmap, null, reusableBitmapDestination, bitmapPaint)
            textLeft += faviconSize + faviconTextGap
        }
        val availableTextWidth = reusableRect.right - textLeft - cellPadding
        if (availableTextWidth > 0) {
            val textColor = if (hasAccentFill) UiColors.ON_ACCENT else UiColors.TEXT
            videoTitlePaint.color = textColor
            videoTitlePaint.alpha = cellAlpha
            videoSubtitlePaint.color = textColor
            videoSubtitlePaint.alpha = cellAlpha * SUBTITLE_ALPHA / 255
            val titleBaseline = if (drawState.isCondensed) {
                textBaselineCenteredOn(reusableRect.centerY(), videoTitlePaint)
            } else {
                reusableRect.centerY() - context.dpFloat(3f)
            }
            canvas.drawText(ellipsize(video.title ?: "", videoTitlePaint, availableTextWidth), textLeft, titleBaseline, videoTitlePaint)
            if (!drawState.isCondensed) {
                val subtitleBaseline = reusableRect.centerY() + context.dpFloat(13f)
                canvas.drawText(ellipsize(video.subtitle, videoSubtitlePaint, availableTextWidth), textLeft, subtitleBaseline, videoSubtitlePaint)
            }
        }
        canvas.restore()
    }

    // TODO: Validate
    private fun drawRowLabel(canvas: Canvas, drawState: GuideGridDrawState, rowIndex: Int) {
        val guideRow = drawState.guideRows[rowIndex]
        val rowHeight = drawState.rowHeight
        val rowTop = topOfRow(drawState, rowIndex)
        val isFocusedLabel =
            rowIndex == drawState.focusedRowIndex && drawState.focusedVideoIndex == CHANNEL_LABEL_VIDEO_INDEX
        val isLabelHighlighted = isFocusedLabel && drawState.isGridFocused
        drawRowLabelBackground(canvas, drawState, guideRow, rowTop, isFocusedLabel)

        var textLeft = channelLabelPadding
        val channelNumberText = formatNumberWithoutTrailingZero(guideRow.channel.number)
        if (channelNumberText != null) {
            channelNumberPaint.color = if (isLabelHighlighted) UiColors.ON_ACCENT else UiColors.TEXT_MUTED
            canvas.drawText(channelNumberText, textLeft, rowTop + rowHeight / 2f + context.dpFloat(5f), channelNumberPaint)
            textLeft += channelNumberPaint.measureText(channelNumberText) + cellPadding
        }
        val nameWidth = (channelColumnWidth - textLeft - cellPadding).toInt()
        if (nameWidth <= 0) return

        val channelLabelArea = ChannelLabelArea(
            left = textLeft,
            rowTop = rowTop,
            width = nameWidth,
            tagText = guideRow.channelStatusText ?: "Current channel".takeIf { guideRow.isCurrentChannelRow },
            isHighlighted = isLabelHighlighted,
        )
        val channelLogoUrl = guideRow.channel.imageUrl
        val channelLogoBitmap = cachedBitmapOrStartLoading(channelLogoUrl)
        if (channelLogoUrl != null && channelLogoBitmap != null) {
            drawChannelLogoLabel(canvas, drawState, guideRow, channelLogoUrl, channelLogoBitmap, channelLabelArea)
        } else {
            drawChannelNameLabel(canvas, drawState, guideRow, channelLabelArea)
        }
    }

    // TODO: Validate
    private fun drawRowLabelBackground(
        canvas: Canvas,
        drawState: GuideGridDrawState,
        guideRow: GuideRow,
        rowTop: Float,
        isFocusedLabel: Boolean,
    ) {
        val rowBottom = rowTop + drawState.rowHeight
        fillPaint.color = when {
            isFocusedLabel && drawState.isGridFocused -> UiColors.ACCENT
            guideRow.isCurrentChannelRow -> UiColors.HEADER
            else -> UiColors.SURFACE
        }
        canvas.drawRect(0f, rowTop, channelColumnWidth.toFloat(), rowBottom, fillPaint)
        fillPaint.color = UiColors.BORDER
        canvas.drawRect(channelColumnWidth - 1f, rowTop, channelColumnWidth.toFloat(), rowBottom, fillPaint)
        canvas.drawRect(0f, rowBottom - 1f, channelColumnWidth.toFloat(), rowBottom, fillPaint)
        if (isFocusedLabel) {
            strokePaint.color = focusOutlineColor(drawState.isGridFocused)
            strokePaint.strokeWidth = focusStrokeWidth
            strokePaint.alpha = 255
            reusableRect.set(1f, rowTop + 1f, channelColumnWidth - 1f, rowBottom - 1f)
            canvas.drawRect(reusableRect, strokePaint)
        }
    }

    // TODO: Validate
    private fun drawChannelNameLabel(
        canvas: Canvas,
        drawState: GuideGridDrawState,
        guideRow: GuideRow,
        channelLabelArea: ChannelLabelArea,
    ) {
        val labelTextColor = if (channelLabelArea.isHighlighted) UiColors.ON_ACCENT else UiColors.TEXT
        channelNamePaint.color = labelTextColor
        val tagText = channelLabelArea.tagText
        val channelNameText = (if (guideRow.isFavorite) FAVORITE_STAR_PREFIX else "") + guideRow.channel.displayName
        val channelNameMaxLines = if (tagText == null && !drawState.isCondensed) 2 else 1
        val channelNameLayoutKey = ChannelNameLayoutKey(channelNameText, channelLabelArea.width, channelNameMaxLines)
        // A cached layout still draws in the current colour, because it reads channelNamePaint when it draws.
        val channelNameLayout = channelNameLayoutCache.get(channelNameLayoutKey)
            ?: StaticLayout.Builder
                .obtain(channelNameText, 0, channelNameText.length, channelNamePaint, channelLabelArea.width)
                .setMaxLines(channelNameMaxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
                .also { channelNameLayoutCache.put(channelNameLayoutKey, it) }
        val tagHeight = if (tagText == null) 0f else smallLabelPaint.textSize + context.dpFloat(2f)
        val textBlockTop = channelLabelArea.rowTop + (drawState.rowHeight - channelNameLayout.height - tagHeight) / 2f
        if (tagText != null) drawChannelStatusTag(canvas, tagText, channelLabelArea, textBlockTop)
        canvas.save()
        canvas.translate(channelLabelArea.left, textBlockTop + tagHeight)
        channelNameLayout.draw(canvas)
        if (guideRow.isFavorite && !channelLabelArea.isHighlighted) {
            channelNamePaint.color = UiColors.STAR
            canvas.drawText(FAVORITE_STAR, 0f, channelNameLayout.getLineBaseline(0).toFloat(), channelNamePaint)
            channelNamePaint.color = labelTextColor
        }
        canvas.restore()
    }

    // TODO: Validate
    private fun drawChannelLogoLabel(
        canvas: Canvas,
        drawState: GuideGridDrawState,
        guideRow: GuideRow,
        channelLogoUrl: String,
        channelLogoBitmap: Bitmap,
        channelLabelArea: ChannelLabelArea,
    ) {
        val tagText = channelLabelArea.tagText
        val tagHeight = if (tagText == null) 0f else smallLabelPaint.textSize + context.dpFloat(4f)
        val needsLightBackground = channelLogoUrlToNeedsLightBackground.getOrPut(channelLogoUrl) {
            channelLogoNeedsLightBackground(channelLogoBitmap)
        }
        val logoPaddingX = if (needsLightBackground) channelLogoLightBackgroundPaddingX else 0f
        val logoPaddingY = if (needsLightBackground) channelLogoLightBackgroundPaddingY else 0f
        val favoriteStarWidth = if (guideRow.isFavorite) channelNamePaint.measureText(FAVORITE_STAR_PREFIX) else 0f
        val maxLogoWidth = channelLabelArea.width - favoriteStarWidth - logoPaddingX * 2
        if (maxLogoWidth <= 0f || channelLogoBitmap.width <= 0 || channelLogoBitmap.height <= 0) return
        val channelLogoMaxHeight =
            if (drawState.isCondensed) condensedChannelLogoMaxHeight else regularChannelLogoMaxHeight
        val logoScale = minOf(
            maxLogoWidth / channelLogoBitmap.width,
            channelLogoMaxHeight / channelLogoBitmap.height,
        )
        val logoWidth = channelLogoBitmap.width * logoScale
        val logoHeight = channelLogoBitmap.height * logoScale
        val logoBoxHeight = logoHeight + logoPaddingY * 2
        val labelBlockTop = channelLabelArea.rowTop + (drawState.rowHeight - logoBoxHeight - tagHeight) / 2f
        if (tagText != null) drawChannelStatusTag(canvas, tagText, channelLabelArea, labelBlockTop)
        val logoBoxTop = labelBlockTop + tagHeight
        var logoBoxLeft = channelLabelArea.left
        if (guideRow.isFavorite) {
            channelNamePaint.color = if (channelLabelArea.isHighlighted) UiColors.ON_ACCENT else UiColors.STAR
            val favoriteStarBaseline = textBaselineCenteredOn(logoBoxTop + logoBoxHeight / 2f, channelNamePaint)
            canvas.drawText(FAVORITE_STAR_PREFIX, logoBoxLeft, favoriteStarBaseline, channelNamePaint)
            logoBoxLeft += favoriteStarWidth
        }
        if (needsLightBackground) {
            fillPaint.color = Color.WHITE
            reusableRect.set(logoBoxLeft, logoBoxTop, logoBoxLeft + logoWidth + logoPaddingX * 2, logoBoxTop + logoBoxHeight)
            canvas.drawRoundRect(reusableRect, cornerRadius, cornerRadius, fillPaint)
        }
        val logoLeft = logoBoxLeft + logoPaddingX
        val logoTop = logoBoxTop + logoPaddingY
        reusableBitmapDestination.set(
            logoLeft.roundToInt(),
            logoTop.roundToInt(),
            (logoLeft + logoWidth).roundToInt(),
            (logoTop + logoHeight).roundToInt(),
        )
        bitmapPaint.alpha = 255
        canvas.drawBitmap(channelLogoBitmap, null, reusableBitmapDestination, bitmapPaint)
    }

    // TODO: Validate
    private fun drawChannelStatusTag(canvas: Canvas, tagText: String, channelLabelArea: ChannelLabelArea, tagTop: Float) {
        smallLabelPaint.color = if (channelLabelArea.isHighlighted) UiColors.ON_ACCENT else UiColors.ACCENT
        canvas.drawText(
            ellipsize(tagText, smallLabelPaint, channelLabelArea.width.toFloat()),
            channelLabelArea.left,
            tagTop + smallLabelPaint.textSize,
            smallLabelPaint,
        )
    }

    // TODO: Validate
    private fun drawNowLine(canvas: Canvas, drawState: GuideGridDrawState) {
        val minutesSinceWindowStart = (System.currentTimeMillis() - drawState.windowStart) / MILLISECONDS_PER_MINUTE.toFloat()
        val nowLineX = channelColumnWidth + minutesSinceWindowStart * pixelsPerMinute - drawState.gridScrollX
        if (nowLineX < channelColumnWidth || nowLineX > drawState.viewWidth) return
        fillPaint.color = UiColors.NOW_LINE
        canvas.drawRect(
            nowLineX,
            headerHeight.toFloat(),
            nowLineX + context.dpFloat(1.5f),
            drawState.viewHeight.toFloat(),
            fillPaint,
        )
    }

    // TODO: Validate
    private fun drawHeader(canvas: Canvas, drawState: GuideGridDrawState) {
        val viewWidth = drawState.viewWidth
        fillPaint.color = UiColors.HEADER
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), headerHeight.toFloat(), fillPaint)
        fillPaint.color = UiColors.BORDER
        canvas.drawRect(0f, headerHeight - 1f, viewWidth.toFloat(), headerHeight.toFloat(), fillPaint)
        canvas.drawRect(channelColumnWidth - 1f, 0f, channelColumnWidth.toFloat(), headerHeight.toFloat(), fillPaint)
        val headerBaseline = headerHeight / 2f + headerTextPaint.textSize / 3f
        headerTextPaint.color = UiColors.TEXT
        headerTextPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Channel", channelLabelPadding, headerBaseline, headerTextPaint)
        headerTextPaint.color = UiColors.TEXT_MUTED
        headerTextPaint.typeface = Typeface.DEFAULT

        canvas.save()
        canvas.clipRect(channelColumnWidth, 0, viewWidth, headerHeight)
        val gridScrollX = drawState.gridScrollX
        val slotWidth = SLOT_LENGTH * pixelsPerMinute
        val firstVisibleSlotIndex = max(0, (gridScrollX / slotWidth).toInt())
        val lastVisibleSlotIndex = min(
            drawState.timelineLength / SLOT_LENGTH - 1,
            ((gridScrollX + metrics.trackViewportWidth(viewWidth)) / slotWidth).toInt(),
        )
        for (slotIndex in firstVisibleSlotIndex..lastVisibleSlotIndex) {
            val slotLeft = channelColumnWidth + slotIndex * slotWidth - gridScrollX
            fillPaint.color = UiColors.BORDER
            canvas.drawRect(slotLeft, 0f, slotLeft + 1f, headerHeight.toFloat(), fillPaint)
            val slotStart = drawState.windowStart + slotIndex * SLOT_LENGTH * MILLISECONDS_PER_MINUTE
            val slotTimeLabel = slotStartToTimeLabel.get(slotStart)
                ?: slotTimeFormat.format(Date(slotStart)).also { slotStartToTimeLabel.put(slotStart, it) }
            canvas.drawText(slotTimeLabel, slotLeft + cellPadding, headerBaseline, headerTextPaint)
        }
        canvas.restore()
    }

    // TODO: Validate
    private fun drawFooter(canvas: Canvas, drawState: GuideGridDrawState) {
        val footerText = drawState.footerMessage ?: return
        val rowHeight = drawState.rowHeight
        val viewWidth = drawState.viewWidth
        val footerTop = topOfRow(drawState, drawState.guideRows.size)
        if (footerTop > drawState.viewHeight || footerTop + rowHeight < headerHeight) return
        canvas.save()
        canvas.clipRect(0, headerHeight, viewWidth, drawState.viewHeight)
        fillPaint.color = UiColors.BACKGROUND
        canvas.drawRect(0f, footerTop, viewWidth.toFloat(), footerTop + rowHeight, fillPaint)
        canvas.drawText(footerText, viewWidth / 2f, footerTop + rowHeight / 2f + messagePaint.textSize / 3f, messagePaint)
        canvas.restore()
    }

    // TODO: Validate
    private fun drawCenteredEmptyMessage(canvas: Canvas, drawState: GuideGridDrawState) {
        val emptyMessage = drawState.emptyMessage
        val viewWidth = drawState.viewWidth
        if (emptyMessage.isEmpty() || viewWidth == 0) return
        val messageLayout = StaticLayout.Builder
            .obtain(emptyMessage, 0, emptyMessage.length, messagePaint, max(1, viewWidth - context.dp(64)))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(viewWidth / 2f, (drawState.viewHeight - messageLayout.height) / 2f)
        messageLayout.draw(canvas)
        canvas.restore()
    }

    // TODO: Validate
    private fun ellipsize(text: String, textPaint: TextPaint, availableWidth: Float): String {
        if (availableWidth <= 0) return ""
        val ellipsizedTextKey = EllipsizedTextKey(text, textPaint, availableWidth)
        return ellipsizedTextCache.get(ellipsizedTextKey)
            ?: TextUtils.ellipsize(text, textPaint, availableWidth, TextUtils.TruncateAt.END).toString()
                .also { ellipsizedTextCache.put(ellipsizedTextKey, it) }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val ELLIPSIZED_TEXT_CACHE_SIZE = 1024
        // TODO: Validate
        const val CHANNEL_NAME_LAYOUT_CACHE_SIZE = 128
        // TODO: Validate
        const val SLOT_TIME_LABEL_CACHE_SIZE = 128
        // TODO: Validate
        const val WATCHED_ALPHA = 153
        // TODO: Validate
        const val PLAYING_FILL_ALPHA = 204
        // TODO: Validate
        const val SUBTITLE_ALPHA = 178
        // TODO: Validate
        const val FAVORITE_STAR = "★"
        // TODO: Validate
        const val FAVORITE_STAR_PREFIX = "★ "
    }
}
