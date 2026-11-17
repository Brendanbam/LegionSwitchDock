package com.legion.switchdock.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import java.util.concurrent.Executors
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
            // Discard output so a chatty command can never fill the pipe and hang
            val process = ProcessBuilder("sh", "-c", command)
                .redirectOutput(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
                .redirectError(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
                .start()
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }
}

object ShizukuHelper {
    @Volatile
    private var shellService: IShellService? = null
    @Volatile
    private var isBinding = false
    private var appContext: Context? = null

    // Shell commands run off the main thread (some take 100ms+ each) and in order
    private val commandExecutor = Executors.newSingleThreadExecutor()

    // Commands issued before the shell service finished binding (e.g. first plug-in after a reboot)
    private val pendingCommands = ArrayDeque<String>()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appContext?.let { ensureBound(it) }
    }

    /** Call once (from the dock service) so we bind as soon as Shizuku starts, even after the app. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runCatching {
            Shizuku.addRequestPermissionResultListener { _, result ->
                if (result == PackageManager.PERMISSION_GRANTED) appContext?.let { ensureBound(it) }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = false
            val svc = IShellService.Stub.asInterface(service)
            shellService = svc
            val queued = synchronized(pendingCommands) {
                pendingCommands.toList().also { pendingCommands.clear() }
            }
            queued.forEach { cmd -> commandExecutor.execute { runCatching { svc.exec(cmd) } } }
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
            synchronized(pendingCommands) { pendingCommands.clear() }
            isBinding = false
        }
    }

    /**
     * Runs a shell command as Shizuku (shell UID) in the background. Returns true if the command
     * was dispatched (or queued until the shell service finishes binding).
     */
    fun runCommand(context: Context, command: String): Boolean {
        if (getStatus() != ShizukuStatus.READY) return false
        val service = shellService
        if (service == null) {
            synchronized(pendingCommands) {
                if (pendingCommands.size >= 20) pendingCommands.removeFirst()
                pendingCommands.addLast(command)
            }
            ensureBound(context)
            return true
        }
        commandExecutor.execute {
            runCatching { service.exec(command) }.onFailure {
                // Shell service died (e.g. Shizuku restarted): rebind for next time
                shellService = null
            }
        }
        return true
    }

    private fun restoreStore(context: Context) =
        context.getSharedPreferences("legion_restore_state", Context.MODE_PRIVATE)

    fun setInternalScreenBrightness(context: Context, dimToZero: Boolean) {
        val store = restoreStore(context)
        if (dimToZero) {
            // Remember the user's own brightness + auto-brightness so undocking puts them back
            if (!store.contains("brightness")) {
                val cr = context.contentResolver
                val level = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(140)
                val mode = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) }.getOrDefault(0)
                store.edit().putInt("brightness", level).putInt("brightness_mode", mode).apply()
            }
            runCommand(context, "settings put system screen_brightness_mode 0 && settings put system screen_brightness 0")
        } else {
            val level = store.getInt("brightness", 140)
            val mode = store.getInt("brightness_mode", 0)
            store.edit().remove("brightness").remove("brightness_mode").apply()
            runCommand(context, "settings put system screen_brightness $level && settings put system screen_brightness_mode $mode")
        }
    }

    /** Saves the user's Wi-Fi/Bluetooth background-scan settings before the streaming boost turns them off. */
    fun rememberScanSettings(context: Context) {
        val store = restoreStore(context)
        if (store.contains("wifi_scan")) return
        val cr = context.contentResolver
        val wifi = runCatching { Settings.Global.getInt(cr, "wifi_scan_always_enabled") }.getOrDefault(1)
        val ble = runCatching { Settings.Global.getInt(cr, "ble_scan_always_enabled") }.getOrDefault(1)
        store.edit().putInt("wifi_scan", wifi).putInt("ble_scan", ble).apply()
    }

    /** Puts the scan settings back the way the user had them (instead of forcing them on). */
    fun restoreScanSettingsCommand(context: Context): String {
        val store = restoreStore(context)
        val wifi = store.getInt("wifi_scan", 1)
        val ble = store.getInt("ble_scan", 1)
        store.edit().remove("wifi_scan").remove("ble_scan").apply()
        return listOf(
            "settings put global wifi_scan_always_enabled $wifi",
            "settings put global ble_scan_always_enabled $ble",
            "cmd wifi set-scan-always-available ${if (wifi == 1) "enabled" else "disabled"}"
        ).joinToString(" ; ")
    }

    fun forceStopPackage(context: Context, packageName: String) {
        runCommand(context, "am force-stop $packageName")
    }

    fun setExternalDisplayMode(
        context: Context,
        displayId: Int,
        width: Int,
        height: Int,
        refreshRate: Float
    ): Boolean {
        // Exact EDID rate (e.g. 119.88) so the system matches the mode instead of ignoring it
        val rate = String.format(java.util.Locale.US, "%.3f", refreshRate)
        val cmd = "cmd display set-user-preferred-display-mode $width $height $rate $displayId"
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
