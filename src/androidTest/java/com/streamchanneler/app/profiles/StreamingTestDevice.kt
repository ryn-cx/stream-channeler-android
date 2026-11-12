// TODO: Validate
package com.streamchanneler.app.profiles

import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.streamchanneler.app.screenautomation.RemoteButtons
import com.streamchanneler.app.screenautomation.ScreenAutomationService
import java.io.OutputStream
import java.util.concurrent.FutureTask

// TODO: Validate
object StreamingTestDevice {
    // TODO: Validate
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    // TODO: Validate
    private const val PROFILE_PICKER_TURN_ON_ATTEMPTS = 5

    // TODO: Validate
    private const val PROFILE_PICKER_TURN_ON_WAIT_TIME = 3_000L

    // TODO: Validate
    private val LOG_TAGS = listOf("ProfilePicker", "RemoteButtons", "StreamingAppLauncher", "PlaybackMonitor", "AndroidRuntime")

    // TODO: Validate
    private val uiAutomationKeepingAccessibilityServices: UiAutomation
        get() = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    // TODO: Validate
    fun runShellCommand(shellCommand: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomationKeepingAccessibilityServices.executeShellCommand(shellCommand))
            .bufferedReader()
            .use { it.readText() }

    // TODO: Validate
    fun <T> runOnMainThreadAndWait(mainThreadWork: () -> T): T {
        val mainThreadTask = FutureTask<T> { mainThreadWork() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mainThreadTask)
        return mainThreadTask.get()
    }

    // TODO: Validate
    fun turnOnProfilePicker(context: Context) {
        repeat(PROFILE_PICKER_TURN_ON_ATTEMPTS) {
            if (ScreenAutomationService.connectedService != null) return
            val otherEnabledServiceNames = ScreenAutomationService.otherEnabledAccessibilityServiceNames(context)
            runShellCommand(
                if (otherEnabledServiceNames.isEmpty()) {
                    "settings delete secure enabled_accessibility_services"
                } else {
                    "settings put secure enabled_accessibility_services ${otherEnabledServiceNames.joinToString(":")}"
                },
            )
            runShellCommand(ScreenAutomationService.enableCommand(context).removePrefix("adb shell "))
            Thread.sleep(PROFILE_PICKER_TURN_ON_WAIT_TIME)
        }
        if (ScreenAutomationService.connectedService == null) throw AssertionError("The profile picker accessibility service didn't turn on")
    }

    // TODO: Validate
    fun startShizukuIfNeeded(context: Context) {
        if (RemoteButtons.pressesDpadWithoutShizuku) return
        if (runShellCommand("pidof shizuku_server").isNotBlank()) return
        val shizukuNativeLibraryDirectory = context.packageManager.getApplicationInfo(SHIZUKU_PACKAGE_NAME, 0).nativeLibraryDir
        runShellCommand("$shizukuNativeLibraryDirectory/libshizuku.so")
    }

    // TODO: Validate
    fun assertInstalled(context: Context, packageName: String) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw AssertionError("$packageName isn't installed")
        }
    }

    // TODO: Validate
    fun wakeUpAndForceStop(streamingPackageName: String) {
        runShellCommand("input keyevent ${KeyEvent.KEYCODE_WAKEUP}")
        runShellCommand("am force-stop $streamingPackageName")
    }

    // TODO: Validate
    fun clearLog() {
        runShellCommand("logcat -c")
    }

    // TODO: Validate
    fun profilePickerLog(): String = runShellCommand("logcat -d -v brief -s ${LOG_TAGS.joinToString(" ")}")

    // TODO: Validate
    fun saveScreenshot(testOutputFileName: String) {
        val screenshot = uiAutomationKeepingAccessibilityServices.takeScreenshot() ?: return
        saveTestOutput(testOutputFileName) { testOutputStream -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, testOutputStream) }
    }

    // TODO: Validate
    fun saveTestOutput(testOutputFileName: String, writeTestOutput: (OutputStream) -> Unit) {
        TestStorage().openOutputFile(testOutputFileName).use(writeTestOutput)
    }
}
