package com.legion.switchdock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.legion.switchdock.MainActivity
import com.legion.switchdock.TvHubActivity
import com.legion.switchdock.data.DockHealthMonitor
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.display.DisplayProfile
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.streaming.ArtemisBoostManager

class DockMonitorService : Service() {

    private lateinit var displayManager: DisplayManager
    private lateinit var prefs: DockPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeExternalDisplayId: Int? = null
    private var lastLowControllerAlertMs: Long = 0L
    private var lastGlassesBatteryAlertPct: Int = 101
    private var activeProfile: DisplayProfile? = null
    private var lastAnnouncedMode: String? = null

    // Display-mode auto-set. Sends the saved resolution / refresh rate ONCE per plug-in. If Shizuku
    // isn't running yet when the cable goes in (e.g. after a reboot), it waits for Shizuku and then
    // sends it once. It never re-sends: repeated mode switches made some glasses reconnect in a loop
    // and could crash the tablet.
    private var modeCheckStartedMs = 0L
    private var modePending = false
    private val lastModeSetMs = mutableMapOf<String, Long>()
    private val modeCheckRunnable = object : Runnable {
        override fun run() {
            if (!modePending) return
            val external = ExternalDisplayHelper.findExternalDisplay(this@DockMonitorService) ?: run { modePending = false; return }
            if (System.currentTimeMillis() - modeCheckStartedMs > 10 * 60_000L) { modePending = false; return }
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                modePending = false
                sendModeOnce(external)
                return
            }
            mainHandler.postDelayed(this, 3_000L)
        }
    }

    /** Sends the saved mode, at most once a minute per display (however often it reconnects). */
    private fun sendModeOnce(external: com.legion.switchdock.display.ExternalDisplayInfo): Boolean {
        val target = ExternalDisplayHelper.resolvePreferredMode(this, external) ?: return false
        if (target.width == external.width && target.height == external.height && target.refreshRateHz == external.refreshRateHz) return false
        val now = System.currentTimeMillis()
        val last = lastModeSetMs[external.name] ?: 0L
        if (now - last < 60_000L) return false
        lastModeSetMs[external.name] = now
        return ShizukuHelper.setExternalDisplayMode(this, external.displayId, target.width, target.height, target.refreshRateExact)
    }

    private fun startModeCheck() {
        mainHandler.removeCallbacks(modeCheckRunnable)
        modeCheckStartedMs = System.currentTimeMillis()
        modePending = true
        mainHandler.postDelayed(modeCheckRunnable, 3_000L)
    }

    private val controllerPollRunnable = object : Runnable {
        override fun run() {
            if (activeExternalDisplayId != null) {
                val snap = DockHealthMonitor.readSnapshot(this@DockMonitorService)
                val lowPad = snap.controllers.firstOrNull { (it.batteryPercent ?: 100) <= 15 }
                val now = System.currentTimeMillis()
                if (lowPad != null && now - lastLowControllerAlertMs >= 10 * 60 * 1000L) {
                    lastLowControllerAlertMs = now
                    Toast.makeText(
                        this@DockMonitorService,
                        "Controller battery low (${lowPad.batteryPercent}%): ${lowPad.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateNotification("Controller Low (${lowPad.batteryPercent}%) • Docked")
                }
                // Glasses draw power from the tablet: warn at 20% and 10%
                if (activeProfile == DisplayProfile.GLASSES && prefs.glassesBatteryWarning && !snap.isCharging) {
                    val pct = snap.batteryPercent
                    val threshold = if (pct <= 10) 10 else if (pct <= 20) 20 else 101
                    if (threshold < lastGlassesBatteryAlertPct) {
                        lastGlassesBatteryAlertPct = threshold
                        Toast.makeText(
                            this@DockMonitorService,
                            "Tablet battery $pct% — your XR glasses run on tablet power",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (snap.isCharging) {
                    lastGlassesBatteryAlertPct = 101
                }
            }
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            mainHandler.postDelayed({ evaluateDisplays(autoLaunch = true) }, 450L)
        }

        override fun onDisplayChanged(displayId: Int) {
            mainHandler.post { evaluateDisplays(autoLaunch = false) }
        }

        override fun onDisplayRemoved(displayId: Int) {
            mainHandler.post {
                if (activeExternalDisplayId == displayId) {
                    handleUndocked()
                } else {
                    evaluateDisplays(autoLaunch = false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DockPreferences(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Watching for TV / XR glasses"))
        displayManager.registerDisplayListener(displayListener, mainHandler)
        ShizukuHelper.init(this)
        ShizukuHelper.ensureBound(this)
        com.legion.switchdock.controller.SteamControllerDriver.start(this)
        registerReceiver(powerReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }, Context.RECEIVER_NOT_EXPORTED)
        // Safety: never leave bypass charging on without a charger (e.g. after an older version turned it on)
        if (!ShizukuHelper.isOnExternalPower(this)) mainHandler.postDelayed({ forceBypassOff() }, 1500L)
        evaluateDisplays(autoLaunch = false)
        mainHandler.postDelayed(controllerPollRunnable, 30_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        evaluateDisplays(autoLaunch = false)
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(controllerPollRunnable)
        mainHandler.removeCallbacks(modeCheckRunnable)
        runCatching { unregisterReceiver(powerReceiver) }
        displayManager.unregisterDisplayListener(displayListener)
        ArtemisBoostManager.setBoostActive(this, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Undo a system setting v1.0.45 turned on for the glasses screen-size feature
     * ("Force activities to be resizable"). Left on, it can make Lenovo's external display mode unstable.
     */
    private fun undoForcedResizableOnce() {
        val store = getSharedPreferences("legion_restore_state", Context.MODE_PRIVATE)
        if (store.getBoolean("undid_force_resizable", false)) return
        if (ShizukuHelper.getStatus() != ShizukuStatus.READY) return
        if (ShizukuHelper.runCommand(this, "settings put global force_resizable_activities 0")) {
            store.edit().putBoolean("undid_force_resizable", true).apply()
        }
    }

    private fun evaluateDisplays(autoLaunch: Boolean) {
        undoForcedResizableOnce()
        val external = ExternalDisplayHelper.findExternalDisplay(this)
        if (external != null) {
            val isNewConnection = activeExternalDisplayId != external.displayId ||
                activeProfile != external.profile
            activeExternalDisplayId = external.displayId
            activeProfile = external.profile
            if (isNewConnection) {
                lastGlassesBatteryAlertPct = 101
                applyProfileDisplayMode(external)
            }
            if (prefs.artemisWifiBoostEnabled && !ArtemisBoostManager.isLowLatencyLockHeld) {
                ArtemisBoostManager.setBoostActive(this, true)
            }
            val boostTag = if (ArtemisBoostManager.isLowLatencyLockHeld) " • Low-Latency Wi-Fi" else ""
            updateNotification("Docked to ${external.profile.label} (${external.summary})$boostTag")
            if ((isNewConnection || autoLaunch) && prefs.autoLaunchOnDock) {
                launchDockedExperience(external.displayId)
            }
        } else if (activeExternalDisplayId != null) {
            handleUndocked()
        } else {
            updateNotification("Ready • Plug in your TV dock or XR glasses")
        }
    }

    /**
     * Switches the external screen to this profile's resolution + refresh rate
     * (e.g. 4K@60 for the TV, 1080p@120 for the glasses). With Shizuku this is system-wide so
     * it sticks inside games/streaming apps too; without it the hub requests it for its own window.
     */
    private fun applyProfileDisplayMode(external: com.legion.switchdock.display.ExternalDisplayInfo) {
        val target = ExternalDisplayHelper.resolvePreferredMode(this, external) ?: return
        val alreadyActive = target.modeId == external.activeModeId
        if (!alreadyActive) {
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) sendModeOnce(external)
            else startModeCheck() // send once when Shizuku starts
        }
        val announceKey = "${external.name}|${target.key}"
        if (announceKey != lastAnnouncedMode) {
            lastAnnouncedMode = announceKey
            Toast.makeText(
                this,
                "${external.profile.label} detected • ${target.label}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchDockedExperience(externalDisplayId: Int) {
        if (prefs.artemisWifiBoostEnabled) {
            ArtemisBoostManager.setBoostActive(this, true)
        }
        if (prefs.blackoutTabletOnDock) {
            val tabletIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DOCKED_MODE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ExternalDisplayHelper.launchIntentOnDisplay(this, tabletIntent, Display.DEFAULT_DISPLAY)
        }

        // Launch TvHubActivity LAST (after a brief delay) so the TV display receives controller/D-pad priority focus
        mainHandler.postDelayed({
            val tvIntent = Intent(this, TvHubActivity::class.java).apply {
                putExtra(TvHubActivity.EXTRA_DISPLAY_ID, externalDisplayId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            ExternalDisplayHelper.launchIntentOnDisplay(this, tvIntent, externalDisplayId)
        }, 250L)

        if (prefs.shizukuScreenOff) {
            ShizukuHelper.setInternalScreenBrightness(this, dimToZero = true)
        }
        // Only with a charger connected (setBypassCharging refuses otherwise)
        if (prefs.autoBypassChargingOnDock && ShizukuHelper.isOnExternalPower(this)) {
            if (ShizukuHelper.setBypassCharging(this, enabled = true)) {
                prefs.bypassChargingEnabled = true
            }
        }
    }

    /** Charger unplugged → bypass off at once (the battery must power the tablet and glasses). */
    private val powerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> forceBypassOff()
                Intent.ACTION_POWER_CONNECTED -> if (activeExternalDisplayId != null && prefs.autoBypassChargingOnDock &&
                    ShizukuHelper.getStatus() == ShizukuStatus.READY && ShizukuHelper.setBypassCharging(this@DockMonitorService, true)
                ) prefs.bypassChargingEnabled = true
            }
        }
    }

    private fun forceBypassOff() {
        if (ShizukuHelper.getStatus() == ShizukuStatus.READY) ShizukuHelper.setBypassCharging(this, enabled = false)
        prefs.bypassChargingEnabled = false
    }

    private fun handleUndocked() {
        mainHandler.removeCallbacks(modeCheckRunnable)
        activeExternalDisplayId = null
        activeProfile = null
        lastAnnouncedMode = null
        updateNotification("Ready • Plug in your TV dock or XR glasses")
        ArtemisBoostManager.setBoostActive(this, false)

        if (prefs.shizukuScreenOff) {
            ShizukuHelper.setInternalScreenBrightness(this, dimToZero = false)
        }
        if (prefs.autoBypassChargingOnDock) {
            ShizukuHelper.setBypassCharging(this, enabled = false)
            prefs.bypassChargingEnabled = false
        }

        sendBroadcast(Intent(ACTION_UNDOCKED).setPackage(packageName))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Legion Switch Dock Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Detects USB-C / HDMI TV connections to launch the Switch-style TV Hub"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Legion Switch Dock")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    companion object {
        const val CHANNEL_ID = "legion_switch_dock_channel"
        const val NOTIFICATION_ID = 7001
        const val ACTION_STOP = "com.legion.switchdock.ACTION_STOP"
        const val ACTION_UNDOCKED = "com.legion.switchdock.ACTION_UNDOCKED"

        fun start(context: Context) {
            val intent = Intent(context, DockMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DockMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
