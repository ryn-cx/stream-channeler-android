// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// TODO: Validate
class ScreenTextReader {
    // TODO: Validate
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // TODO: Validate
    fun centerOfText(screenPixels: ScreenPixels, wantedText: String): Point? {
        val normalizedWantedText = normalizedForComparison(wantedText)
        if (normalizedWantedText.isEmpty()) return null
        val recognizedLines = recognizedLinesOfScreenshot(screenPixels) ?: return null
        Log.i(TAG, "Read on screen: ${recognizedLines.joinToString(" | ") { recognizedLine -> recognizedLine.text }}")
        return recognizedLines
            .firstNotNullOfOrNull { recognizedLine -> matchingWordRun(recognizedLine.elements, normalizedWantedText) }
            ?.let { matchingWordRun -> Point(matchingWordRun.bounds.centerX(), matchingWordRun.bounds.centerY()) }
    }

    // TODO: Validate
    fun boundsOfShownButtonTexts(screenPixels: ScreenPixels, buttonTexts: Collection<String>): Map<String, Rect> {
        val recognizedLines = recognizedLinesOfScreenshot(screenPixels) ?: return emptyMap()
        val buttonTextsLongestFirst = buttonTexts.distinct().sortedByDescending { buttonText -> buttonText.length }
        val shownButtonTextBounds = mutableMapOf<String, Rect>()
        for (recognizedLine in recognizedLines) {
            val claimedWordIndices = mutableSetOf<Int>()
            for (buttonText in buttonTextsLongestFirst) {
                if (buttonText in shownButtonTextBounds) continue
                val matchingWordRun =
                    matchingWordRun(recognizedLine.elements, normalizedForComparison(buttonText), claimedWordIndices) ?: continue
                claimedWordIndices += matchingWordRun.wordIndices
                shownButtonTextBounds[buttonText] = matchingWordRun.bounds
            }
        }
        return shownButtonTextBounds
    }

    // TODO: Validate
    fun close() {
        textRecognizer.close()
    }

    // TODO: Validate
    internal fun recognizedTextOfScreenshot(screenPixels: ScreenPixels): Text? {
        val screenBitmap = Bitmap.createBitmap(
            screenPixels.pixels,
            screenPixels.width,
            screenPixels.height,
            Bitmap.Config.ARGB_8888,
        )
        return try {
            Tasks.await(
                textRecognizer.process(InputImage.fromBitmap(screenBitmap, 0)),
                RECOGNITION_TIMEOUT,
                TimeUnit.MILLISECONDS,
            )
        } catch (recognitionFailure: ExecutionException) {
            Log.w(TAG, "Couldn't read the text on the screen", recognitionFailure)
            null
        } catch (_: TimeoutException) {
            Log.w(TAG, "Reading the text on the screen took too long")
            null
        }
    }

    // TODO: Validate
    private fun recognizedLinesOfScreenshot(screenPixels: ScreenPixels): List<Text.Line>? =
        recognizedTextOfScreenshot(screenPixels)?.textBlocks?.flatMap { textBlock -> textBlock.lines }

    // TODO: Validate
    private fun matchingWordRun(
        lineWords: List<Text.Element>,
        normalizedWantedText: String,
        claimedWordIndices: Set<Int> = emptySet(),
    ): MatchingWordRun? {
        val wantedWordCount = normalizedWantedText.split(" ").size
        for (firstWordIndex in 0..lineWords.size - wantedWordCount) {
            val wordRunIndices = firstWordIndex until firstWordIndex + wantedWordCount
            if (wordRunIndices.any { wordIndex -> wordIndex in claimedWordIndices }) continue
            val wordRun = lineWords.slice(wordRunIndices)
            val normalizedWordRunText = normalizedForComparison(wordRun.joinToString(" ") { lineWord -> lineWord.text })
            if (normalizedWordRunText != normalizedWantedText) continue
            val wordRunBounds = Rect()
            wordRun.forEach { lineWord -> lineWord.boundingBox?.let(wordRunBounds::union) }
            if (wordRunBounds.isEmpty) continue
            return MatchingWordRun(wordRunIndices, wordRunBounds)
        }
        return null
    }

    // TODO: Validate
    private class MatchingWordRun(val wordIndices: IntRange, val bounds: Rect)

    // TODO: Validate
    private fun normalizedForComparison(text: String): String =
        text.trim().lowercase().replace(WHITESPACE_RUN, " ")

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val TAG = "ScreenTextReader"

        // TODO: Validate
        const val RECOGNITION_TIMEOUT = 5_000L

        // TODO: Validate
        val WHITESPACE_RUN = Regex("""\s+""")
    }
}
