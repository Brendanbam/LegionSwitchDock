package com.legion.switchdock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.legion.switchdock.TvHubActivity
import com.legion.switchdock.display.ExternalDisplayHelper

class DockReturnAccessibilityService : AccessibilityService() {

    private var lastReturnMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val external = ExternalDisplayHelper.findExternalDisplay(this) ?: return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // If ZUI desktop/launcher/systemui takes focus on the external display after closing an app,
        // bring TvHubActivity back to the front on the TV.
        val isLauncherOrDesktop = pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("desktop", ignoreCase = true) ||
            pkg == "com.zui.launcher" ||
            pkg == "com.android.launcher3"

        if (isLauncherOrDesktop) {
            returnToTvHub(external.displayId)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_HOME)
        ) {
            val external = ExternalDisplayHelper.findExternalDisplay(this)
            if (external != null) {
                returnToTvHub(external.displayId)
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    private fun returnToTvHub(externalDisplayId: Int) {
        val now = System.currentTimeMillis()
        if (now - lastReturnMs < 700L) return
        lastReturnMs = now

        val tvIntent = Intent(this, TvHubActivity::class.java).apply {
            putExtra(TvHubActivity.EXTRA_DISPLAY_ID, externalDisplayId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        ExternalDisplayHelper.launchIntentOnDisplay(this, tvIntent, externalDisplayId)
    }
}
