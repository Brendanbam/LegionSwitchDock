package com.legion.switchdock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.legion.switchdock.TvHubActivity
import com.legion.switchdock.display.ExternalDisplayHelper

class DockReturnAccessibilityService : AccessibilityService() {

    private var lastReturnMs = 0L
    private var lastComboToggleMs = 0L
    private var lastForegroundPackage: String? = null
    private lateinit var quickMenuOverlay: QuickMenuOverlay

    private val handler = Handler(Looper.getMainLooper())
    private var guideDownTimeMs = 0L
    private var guideLongPressHandled = false
    private var l1Pressed = false
    private var r1Pressed = false

    private val guideLongPressRunnable = Runnable {
        guideLongPressHandled = true
        toggleQuickMenuBestEffort()
    }

    private val quickMenuReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_QUICK_MENU) {
                toggleQuickMenuBestEffort()
            }
        }
    }

    private fun toggleQuickMenuBestEffort() {
        if (::quickMenuOverlay.isInitialized && quickMenuOverlay.isShowing) {
            quickMenuOverlay.dismiss()
            return
        }
        if (TvHubActivity.isInstanceResumed && TvHubActivity.toggleQuickMenuFromService()) {
            return
        }
        val target = resolveTargetDisplay() ?: return
        if (::quickMenuOverlay.isInitialized) {
            quickMenuOverlay.toggle(target, lastForegroundPackage)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        quickMenuOverlay = QuickMenuOverlay(this) { displayId ->
            returnToTvHub(displayId)
        }
        registerReceiver(
            quickMenuReceiver,
            IntentFilter(ACTION_TOGGLE_QUICK_MENU),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::quickMenuOverlay.isInitialized) {
            quickMenuOverlay.dismiss()
        }
        runCatching { unregisterReceiver(quickMenuReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val external = ExternalDisplayHelper.findExternalDisplay(this) ?: return

        // Ignore window state changes on the internal tablet screen (Display 0) so touching
        // or waking the tablet never interrupts an active Artemis stream on the TV!
        val eventDisplayId = event.displayId
        if (eventDisplayId != Display.INVALID_DISPLAY && eventDisplayId != external.displayId) {
            return
        }

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
        } else if (pkg != "com.android.systemui" && pkg != "android") {
            lastForegroundPackage = pkg
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Track L1 + R1 + Start / Select combo anywhere (Extended Mode or Mirror Mode)
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> l1Pressed = event.action == KeyEvent.ACTION_DOWN
            KeyEvent.KEYCODE_BUTTON_R1 -> r1Pressed = event.action == KeyEvent.ACTION_DOWN
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (event.action == KeyEvent.ACTION_DOWN && l1Pressed && r1Pressed) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastComboToggleMs >= 400L) {
                        lastComboToggleMs = now
                        toggleQuickMenuBestEffort()
                    }
                    return true
                }
            }
        }

        // 1. If the Quick Menu overlay is currently open over an external game/app,
        // intercept D-pad/A/B/L1/R1 directly so the controller always operates it!
        if (::quickMenuOverlay.isInitialized && quickMenuOverlay.isShowing) {
            if (quickMenuOverlay.handleKeyEvent(event)) {
                return true
            }
        }

        // 2. If TvHubActivity is currently open, prioritize routing controller keys
        // directly to TvHubActivity so Lenovo's built-in launcher can never steal them!
        if (TvHubActivity.isInstanceResumed) {
            if (TvHubActivity.dispatchGlobalKeyEvent(event)) {
                return true
            }
        }

        // Fast-path during gameplay (e.g. Artemis streaming): avoid DisplayManager binder IPC
        // on normal controller buttons when neither QuickMenu nor TvHub is active!
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_MODE && event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        val external = resolveExternalDisplay()
        if (external == null) {
            return super.onKeyEvent(event)
        }

        // Guide button: tap = return to TV Hub, hold (>=450ms) = toggle Quick Menu
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        guideDownTimeMs = System.currentTimeMillis()
                        guideLongPressHandled = false
                        handler.removeCallbacks(guideLongPressRunnable)
                        handler.postDelayed(guideLongPressRunnable, 450L)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(guideLongPressRunnable)
                    if (!guideLongPressHandled) {
                        if (quickMenuOverlay.isShowing) {
                            quickMenuOverlay.dismiss()
                        } else {
                            returnToTvHub(external.displayId)
                        }
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (quickMenuOverlay.isShowing) {
                quickMenuOverlay.dismiss()
            } else {
                returnToTvHub(external.displayId)
            }
            return true
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun resolveExternalDisplay(): Display? {
        val info = ExternalDisplayHelper.findExternalDisplay(this) ?: return null
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(info.displayId)
    }

    private fun resolveTargetDisplay(): Display? {
        val ext = resolveExternalDisplay()
        if (ext != null) return ext
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(Display.DEFAULT_DISPLAY)
    }

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

    companion object {
        const val ACTION_TOGGLE_QUICK_MENU = "com.legion.switchdock.ACTION_TOGGLE_QUICK_MENU"
    }
}
