package com.legion.switchdock.streaming

import android.content.Context
import android.net.wifi.WifiManager
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus

/**
 * Optimizes Wi-Fi latency and decoding frame-pacing for Artemis (com.limelight.noir)
 * and Moonlight/Steam Link while the Legion Y700 is docked to the TV:
 *
 * 1. Holds Android's official WIFI_MODE_FULL_LOW_LATENCY WifiLock so the Qualcomm Wi-Fi chip
 *    disables 802.11 power-save sleep (DTIM polling) between video packets.
 * 2. When Shizuku is active, disables background off-channel Wi-Fi/BLE scanning while docked
 *    (which otherwise causes periodic 20-60ms lag spikes every 30-120s), whitelists Artemis from
 *    Doze/App Standby, and enables Android Fixed Performance Mode for steady decode clocks.
 */
object ArtemisBoostManager {

    private val STREAMING_PACKAGES = listOf(
        "com.limelight.legion",
        "com.limelight.noir",
        "com.limelight",
        "com.valvesoftware.steamlink"
    )

    private var wifiLock: WifiManager.WifiLock? = null

    @Volatile
    var isLowLatencyLockHeld: Boolean = false
        private set

    @Synchronized
    fun setBoostActive(context: Context, active: Boolean) {
        val appCtx = context.applicationContext
        if (active) {
            acquireLowLatencyWifiLock(appCtx)
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                applyShizukuStreamingOptimizations(appCtx, enable = true)
            }
        } else {
            releaseLowLatencyWifiLock()
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                applyShizukuStreamingOptimizations(appCtx, enable = false)
            }
        }
    }

    fun onAppLaunched(context: Context, packageName: String) {
        val prefs = DockPreferences(context)
        if (!prefs.artemisWifiBoostEnabled) return
        acquireLowLatencyWifiLock(context.applicationContext)
        if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
            if (isStreamingPackage(packageName)) {
                ShizukuHelper.rememberScanSettings(context)
                val cmd = listOf(
                    "cmd deviceidle whitelist +$packageName",
                    "cmd activity set-standby-bucket $packageName active",
                    "appops set $packageName RUN_IN_BACKGROUND allow",
                    "appops set $packageName RUN_ANY_IN_BACKGROUND allow",
                    fixedPerformanceCmd(context),
                    "settings put global wifi_scan_always_enabled 0",
                    "settings put global ble_scan_always_enabled 0",
                    "cmd wifi set-scan-always-available disabled"
                ).joinToString(" ; ")
                ShizukuHelper.runCommand(context, cmd)
            }
        }
    }

    /**
     * Fixed performance mode locks the CPU/GPU clocks for steady decoding. Only on a charger: on
     * battery (e.g. XR glasses running off the tablet) it drains the battery and heats the tablet.
     */
    private fun fixedPerformanceCmd(context: Context): String =
        "cmd power set-fixed-performance-mode-enabled ${ShizukuHelper.isOnExternalPower(context)}"

    fun isStreamingPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return STREAMING_PACKAGES.any { it == lower } ||
            lower.contains("limelight") ||
            lower.contains("artemis") ||
            lower.contains("moonlight")
    }

    @Synchronized
    private fun acquireLowLatencyWifiLock(context: Context) {
        runCatching {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifiLock ?: wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "LegionSwitchDock:ArtemisLowLatencyLock"
            ).apply {
                setReferenceCounted(false)
                wifiLock = this
            }
            if (!lock.isHeld) {
                lock.acquire()
            }
            isLowLatencyLockHeld = lock.isHeld
        }
    }

    @Synchronized
    private fun releaseLowLatencyWifiLock() {
        runCatching {
            val lock = wifiLock
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        }
        isLowLatencyLockHeld = false
    }

    private fun applyShizukuStreamingOptimizations(context: Context, enable: Boolean) {
        if (enable) {
            ShizukuHelper.rememberScanSettings(context)
            val whitelistCmds = STREAMING_PACKAGES.flatMap { pkg ->
                listOf(
                    "cmd deviceidle whitelist +$pkg",
                    "cmd activity set-standby-bucket $pkg active"
                )
            }
            val cmds = whitelistCmds + listOf(
                "settings put global wifi_scan_always_enabled 0",
                "settings put global ble_scan_always_enabled 0",
                "cmd wifi set-scan-always-available disabled",
                fixedPerformanceCmd(context)
            )
            ShizukuHelper.runCommand(context, cmds.joinToString(" ; "))
        } else {
            // Restore the user's own scan settings rather than forcing them on
            val cmds = ShizukuHelper.restoreScanSettingsCommand(context) +
                " ; cmd power set-fixed-performance-mode-enabled false"
            ShizukuHelper.runCommand(context, cmds)
        }
    }
}
