// TODO: Validate
package com.streamchanneler.app.profiles

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.streamchanneler.app.screenautomation.AccessibilityScreenshots
import com.streamchanneler.app.screenautomation.ScreenSignature
import kotlin.math.abs


// TODO: Validate
enum class ProfileListDirection(val towardFirstProfileKeyCode: Int, val towardLastProfileKeyCode: Int) {
    LEFT_TO_RIGHT(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
    TOP_TO_BOTTOM(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN),
}

// TODO: Validate
class PinKeypad(private val keyRows: List<String>, private val firstFocusedDigit: Char = '1') {
    // TODO: Validate
    fun dpadKeyCodesTypingPin(pin: String): List<Int> {
        var focusedPosition = keypadPositionOfDigit(firstFocusedDigit)
        return pin.flatMap { pinDigit ->
            val digitPosition = keypadPositionOfDigit(pinDigit)
            val rowMoves = List(abs(digitPosition.row - focusedPosition.row)) {
                if (digitPosition.row > focusedPosition.row) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            }
            val columnMoves = List(abs(digitPosition.column - focusedPosition.column)) {
                if (digitPosition.column > focusedPosition.column) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            }
            val movesToDigit = if (digitPosition.row < focusedPosition.row) rowMoves + columnMoves else columnMoves + rowMoves
            focusedPosition = digitPosition
            movesToDigit + KeyEvent.KEYCODE_DPAD_CENTER
        }
    }

    // TODO: Validate
    private fun keypadPositionOfDigit(digit: Char): KeypadPosition {
        val row = keyRows.indexOfFirst { digit in it }
        return KeypadPosition(row, keyRows[row].indexOf(digit))
    }

    // TODO: Validate
    private data class KeypadPosition(val row: Int, val column: Int)
}

// TODO: Validate
sealed interface StreamingProfileScreen

// TODO: Validate
sealed interface StreamingPinEntry

// TODO: Validate
data class P1AccessibilityClickedProfileScreen(
    val profileNameFromDescription: Regex,
    val playButtonOnScreen: PlayButtonOnScreen? = null,
) : StreamingProfileScreen

// TODO: Validate
data class P2RemoteButtonProfileScreen(
    val profileList: ProfileList? = null,
    val profileScreenSignature: ScreenSignature? = null,
) : StreamingProfileScreen {
    // TODO: Validate
    val readsProfileNamesOnScreen: Boolean
        get() = profileScreenSignature != null && AccessibilityScreenshots.canTakeScreenshots
}

// TODO: Validate
data class P3RemoteButtonPinEntry(
    val pinScreenSignature: ScreenSignature? = null,
    val pinKeypad: PinKeypad? = null,
    val typesPinWithNumberKeys: Boolean = false,
) : StreamingPinEntry {
    init {
        require(pinKeypad != null || typesPinWithNumberKeys) { "A PIN entry needs a pinKeypad or typesPinWithNumberKeys" }
    }
}

// TODO: Validate
class ProfileList(private val direction: ProfileListDirection) {
    // TODO: Validate
    fun keyCodesToPick(profileNumber: Int): List<Int> =
        List(MAX_PROFILES_IN_LIST) { direction.towardFirstProfileKeyCode } +
            List(profileNumber - 1) { direction.towardLastProfileKeyCode } +
            KeyEvent.KEYCODE_DPAD_CENTER

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val MAX_PROFILES_IN_LIST = 8
    }
}

// TODO: Validate
fun interface P2PinKeysOnScreen : StreamingPinEntry {
    // TODO: Validate
    fun pinKeyNodes(windowNodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo>
}

// TODO: Validate
fun interface PlayButtonOnScreen {
    // TODO: Validate
    fun playButtonNode(windowNodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo?
}
