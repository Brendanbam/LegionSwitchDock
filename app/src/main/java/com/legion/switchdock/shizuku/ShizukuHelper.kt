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
}
