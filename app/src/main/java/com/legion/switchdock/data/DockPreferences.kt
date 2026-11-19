package com.legion.switchdock.data

import android.content.Context
import android.content.SharedPreferences
import com.legion.switchdock.display.DisplayProfile
import com.legion.switchdock.display.ExternalDisplayHelper

class DockPreferences(private val context: Context) {
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
        get() = prefs.getBoolean(KEY_SHIZUKU_FORCE_RELAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_SHIZUKU_FORCE_RELAUNCH, value).apply()

    var artemisWifiBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_ARTEMIS_WIFI_BOOST, true)
        set(value) = prefs.edit().putBoolean(KEY_ARTEMIS_WIFI_BOOST, value).apply()

    var uiSoundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_UI_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_UI_SOUNDS, value).apply()

    var idleDimmerEnabled: Boolean
        get() = prefs.getBoolean(KEY_IDLE_DIMMER, true)
        set(value) = prefs.edit().putBoolean(KEY_IDLE_DIMMER, value).apply()

    var thermalWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_THERMAL_WARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_THERMAL_WARNING, value).apply()

    var autoBypassChargingOnDock: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BYPASS_CHARGING, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BYPASS_CHARGING, value).apply()

    var bypassChargingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_CHARGING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BYPASS_CHARGING_ENABLED, value).apply()

    var hubThemeId: String
        get() = prefs.getString(KEY_HUB_THEME_ID, HubTheme.SWITCH_DARK.id) ?: HubTheme.SWITCH_DARK.id
        set(value) = prefs.edit().putString(KEY_HUB_THEME_ID, value).apply()

    var preferredDisplayModeId: Int
        get() = prefs.getInt(KEY_PREFERRED_DISPLAY_MODE_ID, 0)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_DISPLAY_MODE_ID, value).apply()

    // ---- Per-profile (TV vs XR Glasses) settings ----

    /** Profile of whatever is plugged in right now (TV when nothing is connected). */
    val activeProfile: DisplayProfile
        get() = ExternalDisplayHelper.currentProfile(context)

    private fun profileKey(base: String, profile: DisplayProfile): String =
        if (profile == DisplayProfile.TV) base else "${base}_${profile.id}"

    fun getPreferredModeKey(profile: DisplayProfile): String =
        prefs.getString(profileKey(KEY_PREFERRED_MODE_KEY, profile), "") ?: ""

    fun setPreferredModeKey(profile: DisplayProfile, key: String) {
        prefs.edit().putString(profileKey(KEY_PREFERRED_MODE_KEY, profile), key).apply()
    }

    fun getThemeId(profile: DisplayProfile): String =
        if (profile == DisplayProfile.GLASSES) {
            prefs.getString(KEY_GLASSES_THEME_ID, HubTheme.GLASSES_FLOAT.id) ?: HubTheme.GLASSES_FLOAT.id
        } else {
            hubThemeId
        }

    fun setThemeId(profile: DisplayProfile, id: String) {
        if (profile == DisplayProfile.GLASSES) {
            prefs.edit().putString(KEY_GLASSES_THEME_ID, id).apply()
        } else {
            hubThemeId = id
        }
    }

    /** Manual "this display is a TV / Glasses" choice, remembered per display name. Null = auto-detect. */
    fun getDisplayTypeOverride(displayName: String): String? =
        prefs.getString(KEY_DISPLAY_TYPE_PREFIX + displayName, null)

    fun setDisplayTypeOverride(displayName: String, profile: DisplayProfile?) {
        val key = KEY_DISPLAY_TYPE_PREFIX + displayName
        if (profile == null) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, profile.id).apply()
    }

    /** Percent of the glasses view kept empty around the edges (edges are the blurriest part of the optics). */
    var glassesEdgeMarginPct: Int
        get() = prefs.getInt(KEY_GLASSES_EDGE_MARGIN, 8)
        set(value) = prefs.edit().putInt(KEY_GLASSES_EDGE_MARGIN, value.coerceIn(0, 20)).apply()

    var glassesBatteryWarning: Boolean
        get() = prefs.getBoolean(KEY_GLASSES_BATTERY_WARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_GLASSES_BATTERY_WARNING, value).apply()

    /**
     * "Second-screen" apps open on the tablet instead of the TV/glasses. These apps (e.g. PXPlay)
     * have their own multi-display mode: they keep their touch controls on the screen they run on
     * and send the game picture to the external display. Launched onto the external display they
     * do the reverse (stream on the tablet, touch buttons in the glasses), so they start here.
     */
    fun getSecondScreenPackages(): Set<String> =
        prefs.getStringSet(KEY_SECOND_SCREEN_PACKAGES, DEFAULT_SECOND_SCREEN_PACKAGES)?.toSet()
            ?: DEFAULT_SECOND_SCREEN_PACKAGES

    fun isSecondScreenApp(packageName: String): Boolean = getSecondScreenPackages().contains(packageName)

    fun setSecondScreenApp(packageName: String, enabled: Boolean) {
        val next = getSecondScreenPackages().toMutableSet()
        if (enabled) next.add(packageName) else next.remove(packageName)
        prefs.edit().putStringSet(KEY_SECOND_SCREEN_PACKAGES, next).apply()
    }

    fun hasInitializedPins(profile: DisplayProfile = activeProfile): Boolean =
        prefs.getBoolean(profileKey(KEY_PINS_INITIALIZED, profile), false)

    fun setInitializedPins(profile: DisplayProfile = activeProfile, value: Boolean) {
        prefs.edit().putBoolean(profileKey(KEY_PINS_INITIALIZED, profile), value).apply()
    }

    fun getPinnedPackages(profile: DisplayProfile = activeProfile): List<String> {
        val raw = prefs.getString(profileKey(KEY_PINNED_PACKAGES, profile), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setPinnedPackages(packages: List<String>, profile: DisplayProfile = activeProfile) {
        prefs.edit().putString(profileKey(KEY_PINNED_PACKAGES, profile), packages.joinToString("|")).apply()
    }

    fun togglePinnedPackage(packageName: String): Boolean {
        val profile = activeProfile
        val current = getPinnedPackages(profile).toMutableList()
        val nowPinned = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        setPinnedPackages(current, profile)
        return nowPinned
    }

    fun movePinnedPackage(packageName: String, delta: Int): Boolean {
        val profile = activeProfile
        val current = getPinnedPackages(profile).toMutableList()
        val index = current.indexOf(packageName)
        if (index == -1) return false
        val targetIndex = (index + delta).coerceIn(0, current.lastIndex)
        if (targetIndex == index) return false
        val item = current.removeAt(index)
        current.add(targetIndex, item)
        setPinnedPackages(current, profile)
        return true
    }

    companion object {
        const val PXPLAY_PACKAGE = "psplay.grill.com"
        val DEFAULT_SECOND_SCREEN_PACKAGES = setOf(PXPLAY_PACKAGE)
        private const val KEY_SECOND_SCREEN_PACKAGES = "second_screen_packages"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_LAUNCH = "auto_launch_on_dock"
        private const val KEY_BLACKOUT_TABLET = "blackout_tablet_on_dock"
        private const val KEY_SHIZUKU_SCREEN_OFF = "shizuku_screen_off"
        private const val KEY_SHIZUKU_FORCE_RELAUNCH = "shizuku_force_relaunch"
        private const val KEY_ARTEMIS_WIFI_BOOST = "artemis_wifi_boost_enabled"
        private const val KEY_UI_SOUNDS = "ui_sounds_enabled"
        private const val KEY_IDLE_DIMMER = "idle_dimmer_enabled"
        private const val KEY_THERMAL_WARNING = "thermal_warning_enabled"
        private const val KEY_AUTO_BYPASS_CHARGING = "auto_bypass_charging_on_dock"
        private const val KEY_BYPASS_CHARGING_ENABLED = "bypass_charging_enabled"
        private const val KEY_HUB_THEME_ID = "hub_theme_id"
        private const val KEY_PREFERRED_DISPLAY_MODE_ID = "preferred_display_mode_id"
        private const val KEY_PINS_INITIALIZED = "pins_initialized"
        private const val KEY_PINNED_PACKAGES = "pinned_packages"
        private const val KEY_PREFERRED_MODE_KEY = "preferred_display_mode_key"
        private const val KEY_GLASSES_THEME_ID = "glasses_theme_id"
        private const val KEY_DISPLAY_TYPE_PREFIX = "display_type_override::"
        private const val KEY_GLASSES_EDGE_MARGIN = "glasses_edge_margin_pct"
        private const val KEY_GLASSES_BATTERY_WARNING = "glasses_battery_warning"
    }
}
