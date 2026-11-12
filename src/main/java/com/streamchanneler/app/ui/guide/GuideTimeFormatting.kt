// TODO: Validate
package com.streamchanneler.app.ui.guide

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TODO: Validate
fun formatSlotTime(time: Long): String =
    SimpleDateFormat("h:mma", Locale.getDefault()).format(Date(time))

// TODO: Validate
fun formatVideoDuration(duration: Double): String {
    val wholeSeconds = duration.toLong()
    val hours = wholeSeconds / 3600
    val minutes = (wholeSeconds % 3600) / 60
    val seconds = wholeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
