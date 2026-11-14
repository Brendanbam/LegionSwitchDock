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
import androidx.core.app.NotificationCompat
import com.legion.switchdock.MainActivity
import com.legion.switchdock.TvHubActivity
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.shizuku.ShizukuHelper

class DockMonitorService : Service() {

    private lateinit var displayManager: DisplayManager
    private lateinit var prefs: DockPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeExternalDisplayId: Int? = null

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
        startForeground(NOTIFICATION_ID, buildNotification("Watching for TV / USB-C display"))
        displayManager.registerDisplayListener(displayListener, mainHandler)
        ShizukuHelper.ensureBound(this)
        evaluateDisplays(autoLaunch = false)
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
        displayManager.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun evaluateDisplays(autoLaunch: Boolean) {
        val external = ExternalDisplayHelper.findExternalDisplay(this)
        if (external != null) {
            val isNewConnection = activeExternalDisplayId != external.displayId
            activeExternalDisplayId = external.displayId
            updateNotification("Docked to TV (${external.summary})")
            if ((isNewConnection || autoLaunch) && prefs.autoLaunchOnDock) {
                launchDockedExperience(external.displayId)
            }
        } else if (activeExternalDisplayId != null) {
            handleUndocked()
        } else {
            updateNotification("Ready • Plug USB-C to dock to TV")
        }
    }

    private fun launchDockedExperience(externalDisplayId: Int) {
        val tvIntent = Intent(this, TvHubActivity::class.java).apply {
            putExtra(TvHubActivity.EXTRA_DISPLAY_ID, externalDisplayId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        ExternalDisplayHelper.launchIntentOnDisplay(this, tvIntent, externalDisplayId)

        if (prefs.blackoutTabletOnDock) {
            val tabletIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DOCKED_MODE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ExternalDisplayHelper.launchIntentOnDisplay(this, tabletIntent, Display.DEFAULT_DISPLAY)
        }

        if (prefs.shizukuScreenOff) {
            ShizukuHelper.setInternalScreenBrightness(this, dimToZero = true)
        }
    }

    private fun handleUndocked() {
        activeExternalDisplayId = null
        updateNotification("Ready • Plug USB-C to dock to TV")

        if (prefs.shizukuScreenOff) {
            ShizukuHelper.setInternalScreenBrightness(this, dimToZero = false)
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
