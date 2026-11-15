package com.legion.switchdock.data

import android.content.Context
import android.content.SharedPreferences

class DockPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("legion_switch_dock_prefs", Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var autoLaunchOnDock: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    var blackoutTabletOnDock: Boolean
        get() = prefs.getBoolean(KEY_BLACKOUT_TABLET, true)
        set(value) = prefs.edit().putBoolean(KEY_BLACKOUT_TABLET, value).apply()

    var shizukuScreenOff: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU_SCREEN_OFF, false)
        set(value) = prefs.edit().putBoolean(KEY_SHIZUKU_SCREEN_OFF, value).apply()

    var shizukuForceRelaunch: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU_FORCE_RELAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIZUKU_FORCE_RELAUNCH, value).apply()

    var uiSoundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_UI_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_UI_SOUNDS, value).apply()

    var idleDimmerEnabled: Boolean
        get() = prefs.getBoolean(KEY_IDLE_DIMMER, true)
        set(value) = prefs.edit().putBoolean(KEY_IDLE_DIMMER, value).apply()

    var thermalWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_THERMAL_WARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_THERMAL_WARNING, value).apply()

    var preferredDisplayModeId: Int
        get() = prefs.getInt(KEY_PREFERRED_DISPLAY_MODE_ID, 0)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_DISPLAY_MODE_ID, value).apply()

    var hasInitializedPins: Boolean
        get() = prefs.getBoolean(KEY_PINS_INITIALIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_PINS_INITIALIZED, value).apply()

    fun getPinnedPackages(): List<String> {
        val raw = prefs.getString(KEY_PINNED_PACKAGES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setPinnedPackages(packages: List<String>) {
        prefs.edit().putString(KEY_PINNED_PACKAGES, packages.joinToString("|")).apply()
    }

    fun togglePinnedPackage(packageName: String): Boolean {
        val current = getPinnedPackages().toMutableList()
        val nowPinned = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        setPinnedPackages(current)
        return nowPinned
    }

    fun movePinnedPackage(packageName: String, delta: Int): Boolean {
        val current = getPinnedPackages().toMutableList()
        val index = current.indexOf(packageName)
        if (index == -1) return false
        val targetIndex = (index + delta).coerceIn(0, current.lastIndex)
        if (targetIndex == index) return false
        val item = current.removeAt(index)
        current.add(targetIndex, item)
        setPinnedPackages(current)
        return true
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_LAUNCH = "auto_launch_on_dock"
        private const val KEY_BLACKOUT_TABLET = "blackout_tablet_on_dock"
        private const val KEY_SHIZUKU_SCREEN_OFF = "shizuku_screen_off"
        private const val KEY_SHIZUKU_FORCE_RELAUNCH = "shizuku_force_relaunch"
        private const val KEY_UI_SOUNDS = "ui_sounds_enabled"
        private const val KEY_IDLE_DIMMER = "idle_dimmer_enabled"
        private const val KEY_THERMAL_WARNING = "thermal_warning_enabled"
        private const val KEY_PREFERRED_DISPLAY_MODE_ID = "preferred_display_mode_id"
        private const val KEY_PINS_INITIALIZED = "pins_initialized"
        private const val KEY_PINNED_PACKAGES = "pinned_packages"
    }
}
