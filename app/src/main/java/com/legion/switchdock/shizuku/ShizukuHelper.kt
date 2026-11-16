package com.legion.switchdock.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.annotation.Keep
import com.legion.switchdock.IShellService
import rikka.shizuku.Shizuku
import kotlin.system.exitProcess

enum class ShizukuStatus {
    NOT_RUNNING,
    NEEDS_PERMISSION,
    READY
}

@Keep
class ShizukuShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }
}

object ShizukuHelper {
    private var shellService: IShellService? = null
    private var isBinding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = false
            shellService = IShellService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
            shellService = null
        }
    }

    fun getStatus(): ShizukuStatus {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.NEEDS_PERMISSION
            } else {
                ShizukuStatus.READY
            }
        } catch (_: Exception) {
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    fun ensureBound(context: Context) {
        if (getStatus() != ShizukuStatus.READY) return
        if (shellService != null || isBinding) return

        isBinding = true
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .debuggable(false)
            .version(1)

        runCatching {
            Shizuku.bindUserService(args, connection)
        }.onFailure {
            isBinding = false
        }
    }

    fun runCommand(context: Context, command: String): Boolean {
        if (getStatus() != ShizukuStatus.READY) return false
        ensureBound(context)
        val service = shellService ?: return false
        return runCatching {
            service.exec(command) == 0
        }.getOrDefault(false)
    }

    fun setInternalScreenBrightness(context: Context, dimToZero: Boolean) {
        if (dimToZero) {
            runCommand(context, "settings put system screen_brightness_mode 0 && settings put system screen_brightness 0")
        } else {
            runCommand(context, "settings put system screen_brightness 140")
        }
    }

    fun forceStopPackage(context: Context, packageName: String) {
        runCommand(context, "am force-stop $packageName")
    }

    fun setExternalDisplayMode(
        context: Context,
        displayId: Int,
        width: Int,
        height: Int,
        refreshRateHz: Int
    ): Boolean {
        val cmd = "cmd display set-user-preferred-display-mode $width $height ${refreshRateHz}.0 $displayId"
        return runCommand(context, cmd)
    }

    /**
     * Enables or disables Lenovo Legion Y700 (ZUI 15/16) Bypass Charging across all known
     * Lenovo system/secure/global settings keys via Shizuku (shell UID 2000).
     */
    fun setBypassCharging(context: Context, enabled: Boolean): Boolean {
        val flag = if (enabled) "1" else "0"
        val cmd = listOf(
            "settings put system bypass_charging_enable $flag",
            "settings put secure bypass_charging_enable $flag",
            "settings put global bypass_charging_enable $flag",
            "settings put system game_bypass_charging $flag",
            "settings put secure game_bypass_charging $flag",
            "settings put system zui_bypass_charging $flag",
            "settings put secure zui_bypass_charging $flag"
        ).joinToString(" ; ")
        return runCommand(context, cmd)
    }

    /**
     * Puts the tablet display/system to sleep (KEYCODE_SLEEP = 223), which also stops HDMI output
     * to the TV and lets paired Bluetooth controllers enter idle sleep.
     */
    fun sleepDevice(context: Context): Boolean {
        return runCommand(context, "input keyevent 223")
    }
}
