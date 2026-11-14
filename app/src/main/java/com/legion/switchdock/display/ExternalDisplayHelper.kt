package com.legion.switchdock.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int
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
        return ExternalDisplayInfo(
            displayId = external.displayId,
            name = external.name ?: "External TV",
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRateHz = mode.refreshRate.roundToInt()
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
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().apply {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    launchDisplayId = displayId
                }
            }
            context.startActivity(intent, options.toBundle())
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
