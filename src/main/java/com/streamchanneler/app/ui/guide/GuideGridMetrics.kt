// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.content.Context
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import kotlin.math.max

// TODO: Validate
internal const val SLOT_LENGTH = 30

// TODO: Validate
internal const val LOAD_MORE_VIDEOS_LENGTH = 30

// TODO: Validate
internal const val CHANNEL_LABEL_VIDEO_INDEX = -1

// TODO: Validate
internal const val MILLISECONDS_PER_MINUTE = 60_000L

// TODO: Validate
internal class GuideGridMetrics(context: Context) {
    // TODO: Validate
    val channelColumnWidth = context.dp(184)

    // TODO: Validate
    val headerHeight = context.dp(36)
    // TODO: Validate
    val pixelsPerMinute = context.dpFloat(6f)

    // TODO: Validate
    private val regularRowHeight = context.dp(48)

    // TODO: Validate
    private val condensedRowHeight = context.dp(32)

    // TODO: Validate
    fun rowHeight(isCondensed: Boolean): Int = if (isCondensed) condensedRowHeight else regularRowHeight

    // TODO: Validate
    fun trackViewportWidth(viewWidth: Int): Int = max(0, viewWidth - channelColumnWidth)

    // TODO: Validate
    fun rowsViewportHeight(viewHeight: Int): Int = max(0, viewHeight - headerHeight)
}
