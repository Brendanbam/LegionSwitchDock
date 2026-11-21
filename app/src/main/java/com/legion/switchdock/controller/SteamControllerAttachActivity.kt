package com.legion.switchdock.controller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.legion.switchdock.service.DockMonitorService

/** Opened by Android when the Steam Controller Puck is plugged in; hands it to the driver and closes. */
class SteamControllerAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startForegroundService(Intent(this, DockMonitorService::class.java)) }
        SteamControllerDriver.start(applicationContext)
        finish()
    }
}
