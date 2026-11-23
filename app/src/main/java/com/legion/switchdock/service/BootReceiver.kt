package com.legion.switchdock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.legion.switchdock.data.DockPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Also after every app update (Obtainium), which otherwise leaves dock detection off until the app is opened
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = DockPreferences(context)
            if (prefs.isServiceEnabled) {
                DockMonitorService.start(context)
            }
        }
    }
}
