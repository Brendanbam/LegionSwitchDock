package com.legion.switchdock.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.provider.Settings
import android.view.InputDevice
import kotlin.math.abs
import kotlin.math.roundToInt

data class ControllerStatus(
    val name: String,
    val batteryPercent: Int? // null if controller doesn't report battery over HID
)

data class DockHealthSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val temperatureC: Float,
    val wattsEstimate: Float,
    val isThermalWarning: Boolean,
    val controllers: List<ControllerStatus>
)

object DockHealthMonitor {
    private const val THERMAL_WARN_THRESHOLD_C = 41.0f

    fun readSnapshot(context: Context): DockHealthSnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val isCharging = bm.isCharging

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempC = tempTenths / 10.0f

        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850
        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Some Android kernels report mA instead of uA; normalize heuristically
        val currentMa = if (abs(currentUa) > 20_000) abs(currentUa) / 1000f else abs(currentUa).toFloat()
        val watts = ((voltageMv / 1000f) * (currentMa / 1000f) * 10f).roundToInt() / 10f

        val controllers = readConnectedControllers(context)

        return DockHealthSnapshot(
            batteryPercent = pct,
            isCharging = isCharging,
            temperatureC = tempC,
            wattsEstimate = watts,
            isThermalWarning = tempC >= THERMAL_WARN_THRESHOLD_C,
            controllers = controllers
        )
    }

    fun readConnectedControllers(context: Context): List<ControllerStatus> {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val list = mutableListOf<ControllerStatus>()
        for (id in im.inputDeviceIds) {
            val dev = im.getInputDevice(id) ?: continue
            val src = dev.sources
            val isGamepad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isGamepad) continue

            val batteryState = runCatching { dev.batteryState }.getOrNull()
            val batteryPct = if (batteryState != null && batteryState.isPresent && batteryState.capacity >= 0f) {
                (batteryState.capacity * 100f).roundToInt().coerceIn(0, 100)
            } else {
                null
            }
            list.add(ControllerStatus(name = dev.name ?: "Controller", batteryPercent = batteryPct))
        }
        return list.distinctBy { it.name }
    }

    /**
     * Tries to open Lenovo Game Assistant / Battery settings so the user can enable Bypass Charging
     * for docked apps on the Legion Y700.
     */
    fun openBypassChargingSettings(context: Context) {
        val candidatePackages = listOf(
            "com.lenovo.gameassistant",
            "com.zui.gameassistant",
            "com.lenovo.legionzone"
        )
        val pm = context.packageManager
        for (pkg in candidatePackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching {
                    context.startActivity(launch)
                    return
                }
            }
        }
        val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(batteryIntent) }
            .onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }
}
