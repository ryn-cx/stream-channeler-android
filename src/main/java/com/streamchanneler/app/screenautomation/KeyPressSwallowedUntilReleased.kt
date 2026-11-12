// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.view.KeyEvent

// TODO: Validate
class KeyPressSwallowedUntilReleased(private val onKeyReleased: () -> Unit) {
    // TODO: Validate
    @Volatile
    var isSwallowingKey = false
        private set

    // TODO: Validate
    fun <PressTarget : Any> onKeyEvent(
        keyEvent: KeyEvent,
        findPressTarget: () -> PressTarget?,
        onKeyPressed: (PressTarget) -> Unit,
    ): Boolean = when (keyEvent.action) {
        KeyEvent.ACTION_DOWN -> {
            val pressTarget = if (isSwallowingKey) null else findPressTarget()
            if (pressTarget != null) {
                isSwallowingKey = true
                onKeyPressed(pressTarget)
            }
            isSwallowingKey
        }
        KeyEvent.ACTION_UP -> {
            val wasSwallowingKey = isSwallowingKey
            if (wasSwallowingKey) {
                isSwallowingKey = false
                onKeyReleased()
            }
            wasSwallowingKey
        }
        else -> isSwallowingKey
    }
}
