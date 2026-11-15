package com.legion.switchdock.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

data class ExternalDisplayMode(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int
) {
    val label: String
        get() = "${width}×${height} @ ${refreshRateHz}Hz"
}

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int,
    val activeModeId: Int = 0,
    val supportedModes: List<ExternalDisplayMode> = emptyList()
) {
    val summary: String
        get() = "${width}×${height} • ${refreshRateHz}Hz"
}

object ExternalDisplayHelper {

    fun findExternalDisplay(context: Context): ExternalDisplayInfo? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val external = displays.firstOrNull { isPhysicalExternalDisplay(it) } ?: return null
        val mode = external.mode
        val modes = external.supportedModes
            .map {
                ExternalDisplayMode(
                    modeId = it.modeId,
                    width = it.physicalWidth,
                    height = it.physicalHeight,
                    refreshRateHz = it.refreshRate.roundToInt()
                )
            }
            .distinctBy { "${it.width}x${it.height}@${it.refreshRateHz}" }
            .sortedWith(
                compareByDescending<ExternalDisplayMode> { it.refreshRateHz >= 60 }
                    .thenByDescending { it.width * it.height }
                    .thenByDescending { it.refreshRateHz }
            )

        return ExternalDisplayInfo(
            displayId = external.displayId,
            name = external.name ?: "External TV",
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRateHz = mode.refreshRate.roundToInt(),
            activeModeId = mode.modeId,
            supportedModes = modes
        )
    }

    fun isPhysicalExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        if (display.state == Display.STATE_OFF) return false
        // Ignore private/virtual presentation displays (e.g. screen recorders)
        val flags = display.flags
        val isPrivate = (flags and Display.FLAG_PRIVATE) != 0
        return !isPrivate
    }

    fun launchIntentOnDisplay(
        context: Context,
        intent: Intent,
        displayId: Int
    ): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val primarySuccess = runCatching {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }
            // Force WINDOWING_MODE_FULLSCREEN (1) on Lenovo ZUI Extended Mode so apps don't open in tiny floating boxes
            runCatching {
                val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(options, 1) // 1 = WINDOWING_MODE_FULLSCREEN
            }
            context.startActivity(intent, options.toBundle())
            true
        }.getOrDefault(false)

        if (primarySuccess) return true

        // Clean fallback without reflection/windowing-mode overrides
        return runCatching {
            val fallbackOptions = ActivityOptions.makeBasic().apply {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    launchDisplayId = displayId
                }
            }
            context.startActivity(intent, fallbackOptions.toBundle())
            true
        }.getOrDefault(false)
    }

    fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return launchIntentOnDisplay(context, launchIntent, displayId)
    }
}
