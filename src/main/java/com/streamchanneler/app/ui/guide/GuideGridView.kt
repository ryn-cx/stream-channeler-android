// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.ui.common.ImageLoader
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// TODO: Validate
private const val MINIMUM_TIMELINE_LENGTH = 240

// TODO: Validate
private sealed interface FocusedCell {
    val guideRow: GuideRow

    // TODO: Validate
    class ChannelLabel(override val guideRow: GuideRow) : FocusedCell

    // TODO: Validate
    class Video(override val guideRow: GuideRow, val scheduledVideo: ScheduledVideo) : FocusedCell

    // TODO: Validate
    class LoadMoreVideos(override val guideRow: GuideRow) : FocusedCell
}

// TODO: Validate
@SuppressLint("ViewConstructor")
class GuideGridView(
    context: Context,
    private val listener: Listener,
    imageLoader: ImageLoader,
) : View(context) {

    // TODO: Validate
    interface Listener {
        // TODO: Validate
        fun onVideoFocused(guideRow: GuideRow, scheduledVideo: ScheduledVideo, windowStart: Long)

        // TODO: Validate
        fun onChannelLabelFocused(guideRow: GuideRow)

        // TODO: Validate
        fun onVideoChosen(guideRow: GuideRow, scheduledVideo: ScheduledVideo)

        // TODO: Validate
        fun onVideoMenuRequested(guideRow: GuideRow, scheduledVideo: ScheduledVideo)

        // TODO: Validate
        fun onChannelLabelChosen(guideRow: GuideRow)

        // TODO: Validate
        fun onLoadMoreVideosFocused(guideRow: GuideRow)

        // TODO: Validate
        fun onLoadMoreVideosChosen(guideRow: GuideRow)

        // TODO: Validate
        fun onNearLastRow()

        // TODO: Validate
        fun onUpFromFirstRow(): Boolean
    }

    // TODO: Validate
    var playingEpisodeId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    // TODO: Validate
    var footerMessage: String? = null
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }

    // TODO: Validate
    var emptyMessage: String = ""
        set(value) {
            field = value
            invalidate()
        }

    // TODO: Validate
    private var guideRows: List<GuideRow> = emptyList()
    // TODO: Validate
    private var windowStart = System.currentTimeMillis()

    // TODO: Validate
    private var timelineLength = MINIMUM_TIMELINE_LENGTH
    // TODO: Validate
    private var focusedRowIndex = 0
    // TODO: Validate
    private var focusedVideoIndex = 0
    // TODO: Validate
    private var focusAnchorMinute = 0
    // TODO: Validate
    private var gridScrollX = 0

    // TODO: Validate
    private var gridScrollY = 0
    // TODO: Validate
    private val scroller = OverScroller(context)

    // TODO: Validate
    private val metrics = GuideGridMetrics(context)
    // TODO: Validate
    private val renderer = GuideGridRenderer(context, metrics, imageLoader, ::invalidate)
    // TODO: Validate
    private val channelColumnWidth = metrics.channelColumnWidth

    // TODO: Validate
    private val headerHeight = metrics.headerHeight
    // TODO: Validate
    var isCondensed = false
        set(value) {
            if (field == value) return
            field = value
            clampScroll()
            invalidate()
        }
    // TODO: Validate
    fun videoTextFittingLength(video: GuideVideo): Int =
        renderer.videoTextFittingLength(video, isCondensed, showsSourceLogos)

    // TODO: Validate
    var showsSourceLogos = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    // TODO: Validate
    private val rowHeight: Int
        get() = metrics.rowHeight(isCondensed)
    // TODO: Validate
    private val pixelsPerMinute = metrics.pixelsPerMinute

    // TODO: Validate
    private val horizontalScrollMargin = context.dp(24)

    // TODO: Validate
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        // TODO: Validate
        override fun onDown(motionEvent: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        // TODO: Validate
        override fun onScroll(
            downEvent: MotionEvent?,
            moveEvent: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            scrollToPosition(gridScrollX + distanceX.roundToInt(), gridScrollY + distanceY.roundToInt())
            return true
        }

        // TODO: Validate
        override fun onFling(
            downEvent: MotionEvent?,
            moveEvent: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            scroller.fling(
                gridScrollX,
                gridScrollY,
                -velocityX.roundToInt(),
                -velocityY.roundToInt(),
                0,
                maxScrollX(),
                0,
                maxScrollY(),
            )
            postInvalidateOnAnimation()
            return true
        }

        // TODO: Validate
        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            focusOrChooseTappedCell(motionEvent.x, motionEvent.y)
            return true
        }

        // TODO: Validate
        override fun onLongPress(motionEvent: MotionEvent) {
            if (focusCellAt(motionEvent.x, motionEvent.y)) openFocusedVideoMenu()
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(UiColors.BACKGROUND)
    }

    // TODO: Validate
    fun setGuideRows(newGuideRows: List<GuideRow>, newWindowStart: Long) {
        val previouslyFocusedRow = focusedGuideRow()
        val previouslyFocusedEpisodeId =
            previouslyFocusedRow?.scheduledVideos?.getOrNull(focusedVideoIndex)?.video?.episodeId
        val wasLoadMoreVideosCellFocused = previouslyFocusedRow?.isLoadMoreVideosIndex(focusedVideoIndex) == true
        guideRows = newGuideRows
        windowStart = newWindowStart
        val longestScheduleLength = newGuideRows.maxOfOrNull { guideRow ->
            guideRow.scheduledLength + if (guideRow.channel.hasMoreVideos) LOAD_MORE_VIDEOS_LENGTH else 0
        } ?: 0
        timelineLength = ((max(MINIMUM_TIMELINE_LENGTH, longestScheduleLength) + SLOT_LENGTH - 1) / SLOT_LENGTH) * SLOT_LENGTH

        if (previouslyFocusedRow != null) {
            val matchingRowIndex = newGuideRows.indexOfFirst {
                it.channel.id == previouslyFocusedRow.channel.id &&
                    it.isCurrentChannelRow == previouslyFocusedRow.isCurrentChannelRow
            }
            if (matchingRowIndex >= 0) {
                focusedRowIndex = matchingRowIndex
                if (focusedVideoIndex != CHANNEL_LABEL_VIDEO_INDEX) {
                    val matchingRow = newGuideRows[matchingRowIndex]
                    val matchingVideoIndex = matchingRow.scheduledVideos
                        .indexOfFirst { it.video.episodeId == previouslyFocusedEpisodeId }
                    focusedVideoIndex = when {
                        matchingVideoIndex >= 0 -> matchingVideoIndex
                        wasLoadMoreVideosCellFocused &&
                            matchingRow.scheduledVideos.size <= previouslyFocusedRow.scheduledVideos.size ->
                            matchingRow.lastFocusableVideoIndex
                        else -> videoIndexAtMinute(matchingRow, focusAnchorMinute)
                    }
                }
            }
        }
        focusedRowIndex = focusedRowIndex.coerceIn(0, max(0, newGuideRows.lastIndex))
        val focusedRow = focusedGuideRow()
        focusedVideoIndex = if (focusedRow == null) {
            0
        } else {
            focusedVideoIndex.coerceIn(CHANNEL_LABEL_VIDEO_INDEX, focusedRow.lastFocusableVideoIndex)
        }
        clampScroll()
        invalidate()
        notifyFocusedCell()
    }

    // TODO: Validate
    fun focusEpisode(channelId: String, episodeId: String?) {
        val matchingRowIndex = guideRows.indexOfFirst { it.channel.id == channelId }
        if (matchingRowIndex < 0) return
        focusedRowIndex = matchingRowIndex
        val matchingRowVideos = guideRows[matchingRowIndex].scheduledVideos
        focusedVideoIndex = max(0, matchingRowVideos.indexOfFirst { it.video.episodeId == episodeId })
        focusAnchorMinute = matchingRowVideos.getOrNull(focusedVideoIndex)?.startMinute ?: 0
        ensureFocusedCellVisible(animate = false)
        invalidate()
        notifyFocusedCell()
    }

    // TODO: Validate
    private fun focusedGuideRow(): GuideRow? = guideRows.getOrNull(focusedRowIndex)

    // TODO: Validate
    private fun focusedCell(): FocusedCell? {
        val focusedRow = focusedGuideRow() ?: return null
        val focusedScheduledVideo = focusedRow.scheduledVideos.getOrNull(focusedVideoIndex)
        return when {
            focusedRow.isLoadMoreVideosIndex(focusedVideoIndex) -> FocusedCell.LoadMoreVideos(focusedRow)
            focusedScheduledVideo == null -> FocusedCell.ChannelLabel(focusedRow)
            else -> FocusedCell.Video(focusedRow, focusedScheduledVideo)
        }
    }

    // TODO: Validate
    fun openFocusedVideoMenu(): Boolean {
        val focusedVideoCell = focusedCell() as? FocusedCell.Video ?: return false
        listener.onVideoMenuRequested(focusedVideoCell.guideRow, focusedVideoCell.scheduledVideo)
        return true
    }

    // TODO: Validate
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (guideRows.isEmpty()) return super.onKeyDown(keyCode, event)
        if (isSelectKey(keyCode)) {
            if (event.repeatCount == 0) event.startTracking()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusHorizontally(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusHorizontally(1)
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusedRowIndex == 0) return listener.onUpFromFirstRow()
                moveFocusVertically(-1)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusedRowIndex == guideRows.lastIndex) {
                    scrollToPosition(gridScrollX, maxScrollY(), animate = true)
                    listener.onNearLastRow()
                } else {
                    moveFocusVertically(1)
                }
            }
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_BUTTON_L2,
            -> moveFocusVertically(-rowsPerPage())
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_BUTTON_R2,
            -> moveFocusVertically(rowsPerPage())
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_LEFT_BRACKET,
            -> pageTimeline(-1)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_RIGHT_BRACKET,
            -> pageTimeline(1)
            KeyEvent.KEYCODE_MOVE_HOME -> moveFocusVertically(-focusedRowIndex)
            KeyEvent.KEYCODE_MOVE_END -> moveFocusVertically(guideRows.lastIndex - focusedRowIndex)
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_C -> {
                if (event.repeatCount == 0) focusedGuideRow()?.let(listener::onChannelLabelChosen)
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // TODO: Validate
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean =
        if (isSelectKey(keyCode)) openFocusedVideoMenu() else super.onKeyLongPress(keyCode, event)

    // TODO: Validate
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isSelectKey(keyCode) || guideRows.isEmpty()) return super.onKeyUp(keyCode, event)
        if (event.isTracking && !event.isCanceled) chooseFocusedCell()
        return true
    }

    // TODO: Validate
    private fun isSelectKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        -> true
        else -> false
    }

    // TODO: Validate
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        val handledByGestureDetector = gestureDetector.onTouchEvent(motionEvent)
        if (motionEvent.actionMasked == MotionEvent.ACTION_UP && gridScrollY >= maxScrollY() - rowHeight * NEAR_LAST_ROW_COUNT) {
            listener.onNearLastRow()
        }
        return handledByGestureDetector || super.onTouchEvent(motionEvent)
    }

    // TODO: Validate
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            ensureFocusedCellVisible(animate = true)
            notifyFocusedCell()
        }
        invalidate()
    }

    // TODO: Validate
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        clampScroll()
    }

    // TODO: Validate
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            gridScrollX = scroller.currX.coerceIn(0, maxScrollX())
            gridScrollY = scroller.currY.coerceIn(0, maxScrollY())
            postInvalidateOnAnimation()
        }
    }

    // TODO: Validate
    private fun moveFocusHorizontally(direction: Int) {
        val focusedRow = focusedGuideRow() ?: return
        val nextVideoIndex = if (focusedVideoIndex == CHANNEL_LABEL_VIDEO_INDEX && direction > 0) {
            videoIndexAtMinute(focusedRow, visibleStartMinute())
        } else {
            (focusedVideoIndex + direction).coerceIn(CHANNEL_LABEL_VIDEO_INDEX, focusedRow.lastFocusableVideoIndex)
        }
        if (nextVideoIndex == focusedVideoIndex) return
        focusedVideoIndex = nextVideoIndex
        focusAnchorMinute = focusedRow.scheduledVideos.getOrNull(nextVideoIndex)?.startMinute
            ?: if (focusedRow.isLoadMoreVideosIndex(nextVideoIndex)) focusedRow.scheduledLength else visibleStartMinute()
        onFocusedCellMoved()
    }

    // TODO: Validate
    private fun moveFocusVertically(rowDelta: Int) {
        val nextRowIndex = (focusedRowIndex + rowDelta).coerceIn(0, guideRows.lastIndex)
        if (nextRowIndex == focusedRowIndex) return
        focusedRowIndex = nextRowIndex
        if (focusedVideoIndex != CHANNEL_LABEL_VIDEO_INDEX) {
            focusedVideoIndex = videoIndexAtMinute(guideRows[nextRowIndex], max(focusAnchorMinute, visibleStartMinute()))
        }
        onFocusedCellMoved()
        if (focusedRowIndex >= guideRows.size - NEAR_LAST_ROW_COUNT) listener.onNearLastRow()
    }

    // TODO: Validate
    private fun pageTimeline(direction: Int) {
        val pageWidth = (trackViewportWidth() * TIMELINE_PAGE_FRACTION).roundToInt()
        val targetScrollX = (gridScrollX + direction * pageWidth).coerceIn(0, maxScrollX())
        scrollToPosition(targetScrollX, gridScrollY, animate = true)
        val targetVisibleStartMinute = (targetScrollX / pixelsPerMinute).roundToInt()
        val focusedRow = focusedGuideRow() ?: return
        focusedVideoIndex = videoIndexAtMinute(focusedRow, targetVisibleStartMinute)
        focusAnchorMinute = targetVisibleStartMinute
        invalidate()
        notifyFocusedCell()
    }

    // TODO: Validate
    private fun onFocusedCellMoved() {
        ensureFocusedCellVisible(animate = true)
        invalidate()
        notifyFocusedCell()
    }

    // TODO: Validate
    private fun chooseFocusedCell() {
        when (val focusedCell = focusedCell()) {
            is FocusedCell.LoadMoreVideos -> listener.onLoadMoreVideosChosen(focusedCell.guideRow)
            is FocusedCell.ChannelLabel -> listener.onChannelLabelChosen(focusedCell.guideRow)
            is FocusedCell.Video -> listener.onVideoChosen(focusedCell.guideRow, focusedCell.scheduledVideo)
            null -> Unit
        }
    }

    // TODO: Validate
    private fun notifyFocusedCell() {
        when (val focusedCell = focusedCell()) {
            is FocusedCell.LoadMoreVideos -> listener.onLoadMoreVideosFocused(focusedCell.guideRow)
            is FocusedCell.ChannelLabel -> listener.onChannelLabelFocused(focusedCell.guideRow)
            is FocusedCell.Video -> listener.onVideoFocused(focusedCell.guideRow, focusedCell.scheduledVideo, windowStart)
            null -> Unit
        }
    }

    // TODO: Validate
    private fun focusOrChooseTappedCell(tapX: Float, tapY: Float) {
        val hadFocusBeforeTap = hasFocus()
        val rowIndexBeforeTap = focusedRowIndex
        val videoIndexBeforeTap = focusedVideoIndex
        if (!focusCellAt(tapX, tapY)) return
        val wasAlreadyFocused =
            hadFocusBeforeTap && focusedRowIndex == rowIndexBeforeTap && focusedVideoIndex == videoIndexBeforeTap
        if (wasAlreadyFocused) chooseFocusedCell()
    }

    // TODO: Validate
    private fun focusCellAt(touchX: Float, touchY: Float): Boolean {
        if (touchY < headerHeight) return false
        val touchedRowIndex = ((touchY - headerHeight + gridScrollY) / rowHeight).toInt()
        val touchedRow = guideRows.getOrNull(touchedRowIndex) ?: return false
        val touchedVideoIndex = if (touchX < channelColumnWidth) {
            CHANNEL_LABEL_VIDEO_INDEX
        } else {
            val touchedMinute = (touchX - channelColumnWidth + gridScrollX) / pixelsPerMinute
            val loadMoreVideosStartMinute = touchedRow.scheduledLength
            if (touchedRow.channel.hasMoreVideos &&
                touchedMinute >= loadMoreVideosStartMinute &&
                touchedMinute < loadMoreVideosStartMinute + LOAD_MORE_VIDEOS_LENGTH
            ) {
                touchedRow.scheduledVideos.size
            } else {
                touchedRow.scheduledVideos.indexOfFirst {
                    touchedMinute >= it.startMinute && touchedMinute < it.endMinute
                }.takeIf { it >= 0 } ?: return false
            }
        }
        focusedRowIndex = touchedRowIndex
        focusedVideoIndex = touchedVideoIndex
        touchedRow.scheduledVideos.getOrNull(touchedVideoIndex)?.let { focusAnchorMinute = it.startMinute }
        if (touchedRow.isLoadMoreVideosIndex(touchedVideoIndex)) focusAnchorMinute = touchedRow.scheduledLength
        if (!hasFocus()) requestFocus()
        invalidate()
        notifyFocusedCell()
        return true
    }

    // TODO: Validate
    private fun videoIndexAtMinute(guideRow: GuideRow, minute: Int): Int {
        val scheduledVideos = guideRow.scheduledVideos
        val coveringVideoIndex = scheduledVideos.indexOfFirst { minute < it.endMinute }
        return if (coveringVideoIndex >= 0) coveringVideoIndex else scheduledVideos.lastIndex
    }

    // TODO: Validate
    private fun visibleStartMinute(): Int = (gridScrollX / pixelsPerMinute).toInt()

    // TODO: Validate
    private fun rowsPerPage(): Int = max(1, rowsViewportHeight() / rowHeight - 1)

    // TODO: Validate
    private fun trackViewportWidth(): Int = metrics.trackViewportWidth(width)

    // TODO: Validate
    private fun rowsViewportHeight(): Int = metrics.rowsViewportHeight(height)

    // TODO: Validate
    private fun contentHeight(): Int = guideRows.size * rowHeight + if (footerMessage != null) rowHeight else 0

    // TODO: Validate
    private fun maxScrollX(): Int =
        max(0, (timelineLength * pixelsPerMinute).roundToInt() - trackViewportWidth() + horizontalScrollMargin)

    // TODO: Validate
    private fun maxScrollY(): Int = max(0, contentHeight() - rowsViewportHeight())

    // TODO: Validate
    private fun clampScroll() {
        gridScrollX = gridScrollX.coerceIn(0, maxScrollX())
        gridScrollY = gridScrollY.coerceIn(0, maxScrollY())
    }

    // TODO: Validate
    private fun scrollToPosition(targetScrollX: Int, targetScrollY: Int, animate: Boolean = false) {
        val clampedTargetX = targetScrollX.coerceIn(0, maxScrollX())
        val clampedTargetY = targetScrollY.coerceIn(0, maxScrollY())
        scroller.forceFinished(true)
        if (animate) {
            scroller.startScroll(
                gridScrollX,
                gridScrollY,
                clampedTargetX - gridScrollX,
                clampedTargetY - gridScrollY,
                SCROLL_ANIMATION_DURATION,
            )
            postInvalidateOnAnimation()
        } else {
            gridScrollX = clampedTargetX
            gridScrollY = clampedTargetY
            invalidate()
        }
    }

    // TODO: Validate
    private fun ensureFocusedCellVisible(animate: Boolean) {
        if (width == 0 || height == 0) {
            post { ensureFocusedCellVisible(animate = false) }
            return
        }
        val baseScrollX = if (scroller.isFinished) gridScrollX else scroller.finalX
        val baseScrollY = if (scroller.isFinished) gridScrollY else scroller.finalY
        val focusedRowTop = focusedRowIndex * rowHeight
        val targetScrollY = when {
            focusedRowTop < baseScrollY -> focusedRowTop
            focusedRowTop + rowHeight > baseScrollY + rowsViewportHeight() -> focusedRowTop + rowHeight - rowsViewportHeight()
            else -> baseScrollY
        }
        val targetScrollX = when (val focusedCell = focusedCell()) {
            is FocusedCell.Video ->
                scrollXShowingCell(baseScrollX, focusedCell.scheduledVideo.startMinute, focusedCell.scheduledVideo.length)
            is FocusedCell.LoadMoreVideos ->
                scrollXShowingCell(baseScrollX, focusedCell.guideRow.scheduledLength, LOAD_MORE_VIDEOS_LENGTH)
            is FocusedCell.ChannelLabel, null -> baseScrollX
        }
        if (targetScrollX != baseScrollX || targetScrollY != baseScrollY) {
            scrollToPosition(targetScrollX, targetScrollY, animate)
        }
    }

    // TODO: Validate
    private fun scrollXShowingCell(baseScrollX: Int, cellStartMinute: Int, cellLength: Int): Int {
        val cellLeft = (cellStartMinute * pixelsPerMinute).roundToInt()
        val cellRight = ((cellStartMinute + cellLength) * pixelsPerMinute).roundToInt()
        return when {
            cellLeft < baseScrollX -> cellLeft - horizontalScrollMargin
            cellRight > baseScrollX + trackViewportWidth() ->
                min(cellLeft - horizontalScrollMargin, cellRight - trackViewportWidth() + horizontalScrollMargin)
            else -> baseScrollX
        }
    }

    // TODO: Validate
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(
            canvas,
            GuideGridDrawState(
                guideRows = guideRows,
                windowStart = windowStart,
                timelineLength = timelineLength,
                focusedRowIndex = focusedRowIndex,
                focusedVideoIndex = focusedVideoIndex,
                isGridFocused = hasFocus(),
                gridScrollX = gridScrollX,
                gridScrollY = gridScrollY,
                viewWidth = width,
                viewHeight = height,
                playingEpisodeId = playingEpisodeId,
                footerMessage = footerMessage,
                emptyMessage = emptyMessage,
                isCondensed = isCondensed,
                showsSourceLogos = showsSourceLogos,
            ),
        )
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val NEAR_LAST_ROW_COUNT = 20
        // TODO: Validate
        const val TIMELINE_PAGE_FRACTION = 0.8f

        // TODO: Validate
        const val SCROLL_ANIMATION_DURATION = 180
    }
}
