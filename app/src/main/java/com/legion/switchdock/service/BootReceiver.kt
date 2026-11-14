package com.legion.switchdock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.legion.switchdock.data.DockPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = DockPreferences(context)
            if (prefs.isServiceEnabled) {
                DockMonitorService.start(context)
            }
        }
    }
}
