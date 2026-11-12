// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import rikka.shizuku.Shizuku

// TODO: Validate
enum class RemoteButtonsAccess {
    SHIZUKU_NOT_RUNNING,
    NOT_ALLOWED,
    ALLOWED,
}


// TODO: Validate
object RemoteButtons {
    // TODO: Validate
    private const val TAG = "RemoteButtons"
    // TODO: Validate
    private const val PERMISSION_REQUEST_CODE = 4721

    // TODO: Validate
    private const val DELAY_BETWEEN_PIN_PRESSES = 250L

    // TODO: Validate
    private const val MIN_DELAY_BETWEEN_ACCESSIBILITY_PRESSES = 150L

    // TODO: Validate
    private val KEY_CODE_TO_GLOBAL_ACTION = mapOf(
        KeyEvent.KEYCODE_BACK to AccessibilityService.GLOBAL_ACTION_BACK,
        KeyEvent.KEYCODE_DPAD_UP to AccessibilityService.GLOBAL_ACTION_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN to AccessibilityService.GLOBAL_ACTION_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to AccessibilityService.GLOBAL_ACTION_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER to AccessibilityService.GLOBAL_ACTION_DPAD_CENTER,
    )
    // TODO: Validate
    const val START_SHIZUKU_COMMAND =
        "adb shell 'sh -c \"\$(dirname \$(pm path moe.shizuku.privileged.api | cut -d: -f2))/lib/arm/libshizuku.so\"'"

    // TODO: Validate
    val pressesDpadWithoutShizuku: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // TODO: Validate
    fun shizukuAccess(): RemoteButtonsAccess = when {
        !Shizuku.pingBinder() || Shizuku.isPreV11() -> RemoteButtonsAccess.SHIZUKU_NOT_RUNNING
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> RemoteButtonsAccess.NOT_ALLOWED
        else -> RemoteButtonsAccess.ALLOWED
    }

    // TODO: Validate
    fun requestShizukuAccess(onAccessChanged: (RemoteButtonsAccess) -> Unit) {
        val currentShizukuAccess = shizukuAccess()
        if (currentShizukuAccess != RemoteButtonsAccess.NOT_ALLOWED) {
            onAccessChanged(currentShizukuAccess)
            return
        }
        // TODO: Validate
        val permissionResultListener = object : Shizuku.OnRequestPermissionResultListener {
            // TODO: Validate
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onAccessChanged(shizukuAccess())
            }
        }
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    // TODO: Validate
    fun pressPinKeys(pinKeyCodes: List<Int>): Boolean = press(pinKeyCodes, DELAY_BETWEEN_PIN_PRESSES)

    // TODO: Validate
    fun press(keyCodes: List<Int>, delayBetweenPresses: Long): Boolean =
        if (pressesDpadWithoutShizuku && keyCodes.all { it in KEY_CODE_TO_GLOBAL_ACTION }) {
            pressWithAccessibility(keyCodes, delayBetweenPresses)
        } else {
            pressWithShizuku(keyCodes, delayBetweenPresses)
        }

    // TODO: Validate
    private fun pressWithAccessibility(keyCodes: List<Int>, delayBetweenPresses: Long): Boolean {
        val screenAutomationService = ScreenAutomationService.connectedService ?: return false
        return keyCodes.all { keyCode ->
            Thread.sleep(maxOf(delayBetweenPresses, MIN_DELAY_BETWEEN_ACCESSIBILITY_PRESSES))
            screenAutomationService.performGlobalAction(KEY_CODE_TO_GLOBAL_ACTION.getValue(keyCode))
        }
    }

    // TODO: Validate
    private fun pressWithShizuku(keyCodes: List<Int>, delayBetweenPresses: Long): Boolean {
        if (shizukuAccess() != RemoteButtonsAccess.ALLOWED) {
            Log.w(TAG, "Shizuku isn't running or hasn't allowed this app, so remote buttons can't be pressed")
            return false
        }
        val keyPressCommand = keyCodes.joinToString(" && sleep ${delayBetweenPresses / 1000.0} && ") { "input keyevent $it" }
        return startShizukuProcess(arrayOf("sh", "-c", keyPressCommand)).waitFor() == 0
    }

    // TODO: Validate
    private fun startShizukuProcess(command: Array<String>): Process {
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        newProcessMethod.isAccessible = true
        return newProcessMethod.invoke(null, command, null, null) as Process
    }
}
