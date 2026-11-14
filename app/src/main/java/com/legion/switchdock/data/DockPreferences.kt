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

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_LAUNCH = "auto_launch_on_dock"
        private const val KEY_BLACKOUT_TABLET = "blackout_tablet_on_dock"
        private const val KEY_SHIZUKU_SCREEN_OFF = "shizuku_screen_off"
        private const val KEY_SHIZUKU_FORCE_RELAUNCH = "shizuku_force_relaunch"
        private const val KEY_PINS_INITIALIZED = "pins_initialized"
        private const val KEY_PINNED_PACKAGES = "pinned_packages"
    }
}
