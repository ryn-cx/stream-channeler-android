// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.view.KeyEvent

// TODO: Validate
fun isOkKeyCode(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_BUTTON_A,
    -> true
    else -> false
}

// TODO: Validate
fun isBackKeyCode(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B
