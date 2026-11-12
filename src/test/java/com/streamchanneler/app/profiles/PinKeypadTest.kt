// TODO: Validate
package com.streamchanneler.app.profiles

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import org.junit.Assert.assertEquals
import org.junit.Test

// TODO: Validate
class PinKeypadTest {
    // TODO: Validate
    private val netflixKeypad = PinKeypad(listOf("123", "456", "789", "0"))
    // TODO: Validate
    private val hboMaxKeypad = PinKeypad(listOf("123", "456", "789", " 0"))
    // TODO: Validate
    private val peacockKeypad = PinKeypad(listOf("1234567890"))

    // TODO: Validate
    @Test
    fun repeatedDigitIsSelectedAgainWithoutMoving() {
        assertEquals(
            listOf(
                KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_LEFT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER,
            ),
            netflixKeypad.dpadKeyCodesTypingPin("1337"),
        )
    }

    // TODO: Validate
    @Test
    fun netflixZeroIsReachedFromTheLeftColumnSoTheBackspaceIsNeverFocused() {
        assertEquals(
            listOf(
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_LEFT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_UP, KEYCODE_DPAD_UP, KEYCODE_DPAD_UP, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER,
            ),
            netflixKeypad.dpadKeyCodesTypingPin("902"),
        )
    }

    // TODO: Validate
    @Test
    fun hboMaxZeroIsReachedFromTheMiddleColumnSoTheBlankKeysAreNeverFocused() {
        assertEquals(
            listOf(
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_UP, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER,
            ),
            hboMaxKeypad.dpadKeyCodesTypingPin("09"),
        )
    }

    // TODO: Validate
    @Test
    fun peacockSingleRowOnlyMovesSideways() {
        assertEquals(
            listOf(
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT,
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_RIGHT,
                KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER,
                KEYCODE_DPAD_LEFT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER,
            ),
            peacockKeypad.dpadKeyCodesTypingPin("06"),
        )
    }
}
