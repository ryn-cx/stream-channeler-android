// TODO: Validate
package com.streamchanneler.app.livetests

import com.streamchanneler.app.screenautomation.ScreenPixels
import com.streamchanneler.app.screenautomation.ScreenTextReader

// TODO: Validate
internal fun ScreenTextReader.describeScreenTextWithPositions(screenPixels: ScreenPixels): String {
    val recognizedLines = recognizedTextOfScreenshot(screenPixels)?.textBlocks?.flatMap { it.lines }.orEmpty()
    return recognizedLines.joinToString(" | ") { recognizedLine ->
        val lineBounds = recognizedLine.boundingBox
        "${recognizedLine.text} @(${lineBounds?.centerX()},${lineBounds?.centerY()})"
    }
}
