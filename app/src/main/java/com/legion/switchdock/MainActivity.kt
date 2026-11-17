package com.legion.switchdock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.legion.switchdock.data.AppEntry
import com.legion.switchdock.data.AppRepository
import com.legion.switchdock.data.DockHealthMonitor
import com.legion.switchdock.data.DockHealthSnapshot
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.data.HubTheme
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.display.ExternalDisplayInfo
import com.legion.switchdock.display.ExternalDisplayMode
import com.legion.switchdock.service.DockMonitorService
import com.legion.switchdock.service.QuickMenuOverlay
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import com.legion.switchdock.streaming.ArtemisBoostManager
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var forceDockedBlackout by mutableStateOf(false)

    private val undockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DockMonitorService.ACTION_UNDOCKED) {
                forceDockedBlackout = false
                setScreenBrightness(-1.0f)
            }
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        ShizukuHelper.ensureBound(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = DockPreferences(this)
        if (prefs.isServiceEnabled) {
            DockMonitorService.start(this)
        }

        // If MainActivity was opened on the external TV (e.g. from the TV's Extended Mode app drawer),
        // redirect straight to TvHubActivity on the TV and close MainActivity on the TV so the portrait
        // blackout remote never appears on the TV!
        val currentDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (currentDisplayId != Display.DEFAULT_DISPLAY) {
            val tvIntent = Intent(this, TvHubActivity::class.java).apply {
                putExtra(TvHubActivity.EXTRA_DISPLAY_ID, currentDisplayId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ExternalDisplayHelper.launchIntentOnDisplay(this, tvIntent, currentDisplayId)
            finish()
            return
        }

        forceDockedBlackout = intent.getBooleanExtra(EXTRA_DOCKED_MODE, false)

        registerReceiver(
            undockReceiver,
            IntentFilter(DockMonitorService.ACTION_UNDOCKED),
            RECEIVER_NOT_EXPORTED
        )
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        setContent {
            MaterialTheme {
                CompanionRoot(
                    forceDockedBlackout = forceDockedBlackout,
                    onSetBrightness = { setScreenBrightness(it) },
                    onEnterBlackout = {
                        forceDockedBlackout = true
                    },
                    onExitBlackout = {
                        forceDockedBlackout = false
                        setScreenBrightness(-1.0f)
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val quickMenu = QuickMenuOverlay.activeInstance
        if (quickMenu != null && quickMenu.isShowing) {
            if (quickMenu.handleKeyEvent(event)) return true
        }
        if (TvHubActivity.isInstanceResumed) {
            if (TvHubActivity.dispatchGlobalKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val quickMenu = QuickMenuOverlay.activeInstance
        if (quickMenu != null && quickMenu.isShowing) {
            if (quickMenu.handleMotionEvent(event)) return true
        }
        if (TvHubActivity.isInstanceResumed) {
            if (TvHubActivity.dispatchGlobalMotionEvent(event)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_DOCKED_MODE, false)) {
            forceDockedBlackout = true
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(undockReceiver) }
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun setScreenBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    companion object {
        const val EXTRA_DOCKED_MODE = "extra_docked_mode"
    }
}

@Composable
private fun CompanionRoot(
    forceDockedBlackout: Boolean,
    onSetBrightness: (Float) -> Unit,
    onEnterBlackout: () -> Unit,
    onExitBlackout: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { DockPreferences(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var externalDisplay by remember { mutableStateOf<ExternalDisplayInfo?>(ExternalDisplayHelper.findExternalDisplay(context)) }
    var preferredModeId by remember { mutableStateOf(prefs.preferredDisplayModeId) }
    var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPerm by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var shizukuStatus by remember { mutableStateOf(ShizukuHelper.getStatus()) }

    var serviceEnabled by remember { mutableStateOf(prefs.isServiceEnabled) }
    var autoLaunch by remember { mutableStateOf(prefs.autoLaunchOnDock) }
    var blackoutTablet by remember { mutableStateOf(prefs.blackoutTabletOnDock) }
    var uiSounds by remember { mutableStateOf(prefs.uiSoundsEnabled) }
    var idleDimmer by remember { mutableStateOf(prefs.idleDimmerEnabled) }
    var thermalWarning by remember { mutableStateOf(prefs.thermalWarningEnabled) }
    var autoBypassCharging by remember { mutableStateOf(prefs.autoBypassChargingOnDock) }
    var bypassChargingEnabled by remember { mutableStateOf(prefs.bypassChargingEnabled) }
    var artemisWifiBoost by remember { mutableStateOf(prefs.artemisWifiBoostEnabled) }
    var selectedThemeId by remember { mutableStateOf(prefs.hubThemeId) }
    var shizukuScreenOff by remember { mutableStateOf(prefs.shizukuScreenOff) }
    var shizukuForceRelaunch by remember { mutableStateOf(prefs.shizukuForceRelaunch) }

    var health by remember { mutableStateOf(DockHealthMonitor.readSnapshot(context)) }
    var pinnedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var bannerTargetPackage by remember { mutableStateOf<String?>(null) }

    val bannerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val pkg = bannerTargetPackage
        if (uri != null && pkg != null) {
            AppRepository.saveCustomBanner(context, pkg, uri)
            val loaded = AppRepository.loadLaunchableApps(context)
            pinnedApps = AppRepository.getOrSeedPinnedApps(context, loaded, prefs)
        }
        bannerTargetPackage = null
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val loaded = AppRepository.loadLaunchableApps(context)
        pinnedApps = AppRepository.getOrSeedPinnedApps(context, loaded, prefs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        while (true) {
            // When the tablet screen is in Docked Blackout mode during gaming, skip heavy system
            // polling so zero CPU/binder overhead competes with Artemis game streaming!
            if (forceDockedBlackout) {
                delay(3000)
                continue
            }
            externalDisplay = ExternalDisplayHelper.findExternalDisplay(context)
            hasOverlayPerm = Settings.canDrawOverlays(context)
            hasAccessibilityPerm = isAccessibilityEnabled(context)
            shizukuStatus = ShizukuHelper.getStatus()
            health = DockHealthMonitor.readSnapshot(context)
            bypassChargingEnabled = prefs.bypassChargingEnabled
            delay(1500)
        }
    }

    val showDockedMode = forceDockedBlackout

    LaunchedEffect(showDockedMode) {
        if (showDockedMode) {
            onSetBrightness(0.01f)
        } else {
            onSetBrightness(-1.0f)
        }
    }

    fun launchHubAndDim(requestedDisplayId: Int, dimTablet: Boolean) {
        val liveExternalId = ExternalDisplayHelper.findExternalDisplay(context)?.displayId
        val actualTargetId = if (requestedDisplayId != Display.DEFAULT_DISPLAY) {
            requestedDisplayId
        } else {
            liveExternalId ?: Display.DEFAULT_DISPLAY
        }
        if (dimTablet) {
            onEnterBlackout()
        }
        val tvIntent = Intent(context, TvHubActivity::class.java).apply {
            putExtra(TvHubActivity.EXTRA_DISPLAY_ID, actualTargetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        ExternalDisplayHelper.launchIntentOnDisplay(context, tvIntent, actualTargetId)
        if (actualTargetId != Display.DEFAULT_DISPLAY) {
            mainHandler.postDelayed({
                val followUpIntent = Intent(context, TvHubActivity::class.java).apply {
                    putExtra(TvHubActivity.EXTRA_DISPLAY_ID, actualTargetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                ExternalDisplayHelper.launchIntentOnDisplay(context, followUpIntent, actualTargetId)
            }, 220L)
        }
    }

    if (showDockedMode) {
        DockedBlackoutRemoteScreen(
            displayInfo = externalDisplay,
            onFocusTvHub = {
                val targetId = externalDisplay?.displayId ?: Display.DEFAULT_DISPLAY
                launchHubAndDim(targetId, dimTablet = true)
            },
            onExitBlackout = onExitBlackout
        )
    } else {
        SetupDashboardScreen(
            externalDisplay = externalDisplay,
            health = health,
            pinnedApps = pinnedApps,
            hasOverlayPerm = hasOverlayPerm,
            hasAccessibilityPerm = hasAccessibilityPerm,
            shizukuStatus = shizukuStatus,
            serviceEnabled = serviceEnabled,
            autoLaunch = autoLaunch,
            blackoutTablet = blackoutTablet,
            uiSounds = uiSounds,
            idleDimmer = idleDimmer,
            thermalWarning = thermalWarning,
            autoBypassCharging = autoBypassCharging,
            bypassChargingEnabled = bypassChargingEnabled,
            artemisWifiBoost = artemisWifiBoost,
            selectedThemeId = selectedThemeId,
            shizukuScreenOff = shizukuScreenOff,
            shizukuForceRelaunch = shizukuForceRelaunch,
            onToggleService = { enabled ->
                serviceEnabled = enabled
                prefs.isServiceEnabled = enabled
                if (enabled) DockMonitorService.start(context) else DockMonitorService.stop(context)
            },
            onToggleAutoLaunch = {
                autoLaunch = it
                prefs.autoLaunchOnDock = it
            },
            onToggleBlackout = {
                blackoutTablet = it
                prefs.blackoutTabletOnDock = it
            },
            onToggleUiSounds = {
                uiSounds = it
                prefs.uiSoundsEnabled = it
            },
            onToggleIdleDimmer = {
                idleDimmer = it
                prefs.idleDimmerEnabled = it
            },
            onToggleThermalWarning = {
                thermalWarning = it
                prefs.thermalWarningEnabled = it
            },
            onToggleAutoBypassCharging = {
                autoBypassCharging = it
                prefs.autoBypassChargingOnDock = it
                if (it && externalDisplay != null && ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                    ShizukuHelper.setBypassCharging(context, true)
                    bypassChargingEnabled = true
                    prefs.bypassChargingEnabled = true
                }
            },
            onToggleBypassChargingNow = {
                val next = !bypassChargingEnabled
                bypassChargingEnabled = next
                prefs.bypassChargingEnabled = next
                if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                    ShizukuHelper.setBypassCharging(context, next)
                } else {
                    DockHealthMonitor.openBypassChargingSettings(context)
                }
            },
            onOpenBypassCharging = {
                DockHealthMonitor.openBypassChargingSettings(context)
            },
            onToggleArtemisWifiBoost = { enabled ->
                artemisWifiBoost = enabled
                prefs.artemisWifiBoostEnabled = enabled
                if (enabled && externalDisplay != null) {
                    ArtemisBoostManager.setBoostActive(context, true)
                } else if (!enabled) {
                    ArtemisBoostManager.setBoostActive(context, false)
                }
            },
            onSelectTheme = { theme ->
                selectedThemeId = theme.id
                prefs.hubThemeId = theme.id
            },
            onPickBanner = { pkg ->
                bannerTargetPackage = pkg
                bannerPicker.launch("image/*")
            },
            onClearBanner = { pkg ->
                AppRepository.clearCustomBanner(context, pkg)
                val loaded = AppRepository.loadLaunchableApps(context)
                pinnedApps = AppRepository.getOrSeedPinnedApps(context, loaded, prefs)
            },
            onToggleShizukuScreenOff = {
                shizukuScreenOff = it
                prefs.shizukuScreenOff = it
            },
            onToggleShizukuForceRelaunch = {
                shizukuForceRelaunch = it
                prefs.shizukuForceRelaunch = it
            },
            onRequestOverlay = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            onRequestAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onRequestShizuku = {
                ShizukuHelper.requestPermission()
            },
            preferredModeId = preferredModeId,
            onSelectDisplayMode = { mode ->
                preferredModeId = mode.modeId
                prefs.preferredDisplayModeId = mode.modeId
                val ext = externalDisplay
                if (ext != null) {
                    ShizukuHelper.setExternalDisplayMode(
                        context,
                        ext.displayId,
                        mode.width,
                        mode.height,
                        mode.refreshRateHz
                    )
                    val tvIntent = Intent(context, TvHubActivity::class.java).apply {
                        putExtra(TvHubActivity.EXTRA_DISPLAY_ID, ext.displayId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    ExternalDisplayHelper.launchIntentOnDisplay(context, tvIntent, ext.displayId)
                }
            },
            onLaunchTvHub = { targetId ->
                val liveExt = ExternalDisplayHelper.findExternalDisplay(context)
                val shouldDim = (targetId != Display.DEFAULT_DISPLAY) || (liveExt != null)
                launchHubAndDim(targetId, dimTablet = shouldDim)
            },
            onDimTablet = onEnterBlackout
        )
    }
}

@Composable
private fun DockedBlackoutRemoteScreen(
    displayInfo: ExternalDisplayInfo?,
    onFocusTvHub: () -> Unit,
    onExitBlackout: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var showControls by remember { mutableStateOf(false) }
    val displaySummaryText = displayInfo?.summary ?: "TV Docked"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        if (!showControls) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DOCKED TO TV • $displaySummaryText",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap anywhere for Remote Controls",
                    color = Color.White.copy(alpha = 0.12f),
                    fontSize = 11.sp
                )
            }
        } else {
            Surface(
                color = Color(0xFF141821),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF2B3345)),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "TV Remote • $displaySummaryText",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = {
                            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = "Vol -", tint = Color.White)
                        }
                        OutlinedButton(onClick = {
                            audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeMute, contentDescription = "Mute", tint = Color.White)
                        }
                        OutlinedButton(onClick = {
                            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Vol +", tint = Color.White)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onFocusTvHub,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Show TV Hub", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onExitBlackout) {
                            Text("Tablet Settings", color = Color.White)
                        }
                    }

                    Text(
                        text = "Tap outside to dim screen again",
                        color = Color(0xFF8C96A8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupDashboardScreen(
    externalDisplay: ExternalDisplayInfo?,
    health: DockHealthSnapshot,
    pinnedApps: List<AppEntry>,
    hasOverlayPerm: Boolean,
    hasAccessibilityPerm: Boolean,
    shizukuStatus: ShizukuStatus,
    serviceEnabled: Boolean,
    autoLaunch: Boolean,
    blackoutTablet: Boolean,
    uiSounds: Boolean,
    idleDimmer: Boolean,
    thermalWarning: Boolean,
    autoBypassCharging: Boolean,
    bypassChargingEnabled: Boolean,
    artemisWifiBoost: Boolean,
    selectedThemeId: String,
    shizukuScreenOff: Boolean,
    shizukuForceRelaunch: Boolean,
    onToggleService: (Boolean) -> Unit,
    onToggleAutoLaunch: (Boolean) -> Unit,
    onToggleBlackout: (Boolean) -> Unit,
    onToggleUiSounds: (Boolean) -> Unit,
    onToggleIdleDimmer: (Boolean) -> Unit,
    onToggleThermalWarning: (Boolean) -> Unit,
    onToggleAutoBypassCharging: (Boolean) -> Unit,
    onToggleBypassChargingNow: () -> Unit,
    onOpenBypassCharging: () -> Unit,
    onToggleArtemisWifiBoost: (Boolean) -> Unit,
    onSelectTheme: (HubTheme) -> Unit,
    onPickBanner: (String) -> Unit,
    onClearBanner: (String) -> Unit,
    onToggleShizukuScreenOff: (Boolean) -> Unit,
    onToggleShizukuForceRelaunch: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestShizuku: () -> Unit,
    preferredModeId: Int,
    onSelectDisplayMode: (ExternalDisplayMode) -> Unit,
    onLaunchTvHub: (Int) -> Unit,
    onDimTablet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1117))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Legion Switch Dock",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Plug USB-C into TV for instant console mode",
                    color = Color(0xFF9BA3B4),
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val targetId = externalDisplay?.displayId ?: Display.DEFAULT_DISPLAY
                        onLaunchTvHub(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.size(6.dp))
                    Text("Open on TV", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDimTablet,
                    border = BorderStroke(1.dp, Color(0xFF374151))
                ) {
                    Text("Dim Tablet", color = Color.White)
                }
                if (externalDisplay != null) {
                    OutlinedButton(
                        onClick = { onLaunchTvHub(Display.DEFAULT_DISPLAY) },
                        border = BorderStroke(1.dp, Color(0xFF374151))
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(4.dp))
                        Text("Preview Hub", color = Color.White)
                    }
                }
            }
        }

        // Dock Status & HDMI Resolution / FPS Unlocker Card
        SectionCard(title = "Display Status & HDMI Resolution / FPS Unlocker") {
            val statusColor = if (externalDisplay != null) Color(0xFF4ADE80) else Color(0xFF9BA3B4)
            val statusText = if (externalDisplay != null) {
                "TV Connected: ${externalDisplay.name} (${externalDisplay.summary})"
            } else {
                "Undocked (or in Mirror Mode) — Tap 'Preview Hub' above for Full-Screen Mirror Mode!"
            }
            Text(text = statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)

            if (externalDisplay != null && externalDisplay.supportedModes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hardware EDID Modes Detected (Tap to Lock Resolution & FPS):",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    externalDisplay.supportedModes.take(8).forEach { mode ->
                        val isActive = (preferredModeId == mode.modeId) ||
                            (preferredModeId == 0 && externalDisplay.activeModeId == mode.modeId)
                        OutlinedButton(
                            onClick = { onSelectDisplayMode(mode) },
                            border = BorderStroke(
                                1.dp,
                                if (isActive) Color(0xFF00E5FF) else Color(0xFF374151)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isActive) "✓ ${mode.label} (Active / Locked)" else mode.label,
                                color = if (isActive) Color(0xFF00E5FF) else Color.White,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            Text(
                text = "4K@60fps Unlock Tip: On your Roku TV go to Settings → TV inputs → HDMI port → HDMI mode → select 'Standard (HDMI 2.0)' instead of 'Auto' (HDMI 1.4 caps 4K at 30fps). Also switch tablet Notification → 'Switch to Mirror Mode' to remove Lenovo's desktop taskbar and unlock full-screen 60Hz+!",
                color = Color(0xFF9BA3B4),
                fontSize = 12.sp
            )
        }

        // Permissions & Service
        SectionCard(title = "Dock Detection & Permissions") {
            ToggleRow(
                title = "Background Dock Monitor",
                subtitle = "Watches for USB-C / HDMI TV plug-in events",
                checked = serviceEnabled,
                onCheckedChange = onToggleService
            )
            if (!hasOverlayPerm) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRequestOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Grant 'Display Over Other Apps' (Required for Auto-Launch)", color = Color.Black)
                }
            } else {
                Text(
                    text = "✓ Display Over Other Apps permission granted",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp
                )
            }
            if (!hasAccessibilityPerm) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRequestAccessibility,
                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text(
                        "Enable Auto-Return to TV Hub (App Exit / Guide Button)",
                        color = Color(0xFF00E5FF)
                    )
                }
            } else {
                Text(
                    text = "✓ Auto-Return to TV Hub enabled (Guide/Home & app exit)",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp
                )
            }
        }

        // Switch-Mode Behavior
        SectionCard(title = "Switch-Like Behavior") {
            ToggleRow(
                title = "Auto-Launch TV Hub on Plug-In",
                subtitle = "Opens the controller-first TV dashboard on the external display",
                checked = autoLaunch,
                onCheckedChange = onToggleAutoLaunch
            )
            ToggleRow(
                title = "Blackout Tablet Screen When Docked",
                subtitle = "Dims the Y700 screen to pure black with a tap-to-reveal remote",
                checked = blackoutTablet,
                onCheckedChange = onToggleBlackout
            )
            ToggleRow(
                title = "Switch-Style UI Navigation Sounds",
                subtitle = "Crisp audio feedback when browsing tiles and menus",
                checked = uiSounds,
                onCheckedChange = onToggleUiSounds
            )
            ToggleRow(
                title = "5-Minute Idle TV Screensaver",
                subtitle = "Dims the TV Hub to a clean clock after 5 min without controller input",
                checked = idleDimmer,
                onCheckedChange = onToggleIdleDimmer
            )
            ToggleRow(
                title = "Artemis Low-Latency Wi-Fi Boost",
                subtitle = "Holds WIFI_MODE_FULL_LOW_LATENCY lock & pauses background Wi-Fi/BLE scans while docked to prevent lag spikes",
                checked = artemisWifiBoost,
                onCheckedChange = onToggleArtemisWifiBoost
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "TV Hub Theme",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HubTheme.entries.forEach { theme ->
                    val isCurrent = theme.id == selectedThemeId
                    OutlinedButton(
                        onClick = { onSelectTheme(theme) },
                        border = BorderStroke(
                            if (isCurrent) 2.dp else 1.dp,
                            if (isCurrent) theme.accent else Color(0xFF374151)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isCurrent) "✓ ${theme.displayName} (Active)" else theme.displayName,
                            color = if (isCurrent) theme.accent else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Y700 Battery & Thermal Protection
        SectionCard(title = "Y700 Battery & Thermal Protection") {
            val chargeText = if (health.isCharging) "Charging (${health.wattsEstimate}W)" else "Discharging"
            Text(
                text = "Battery: ${health.batteryPercent}% • ${String.format(Locale.US, "%.1f", health.temperatureC)}°C • $chargeText",
                color = if (health.isThermalWarning) Color(0xFFF59E0B) else Color(0xFFD1D5DB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            ToggleRow(
                title = "Auto-Enable Bypass Charging While Docked",
                subtitle = "Automatically keeps Bypass Charging ON while docked to TV and restores normal charging on unplug (via Shizuku)",
                checked = autoBypassCharging,
                onCheckedChange = onToggleAutoBypassCharging
            )
            ToggleRow(
                title = "TV Thermal Warning (>= 41°C)",
                subtitle = "Shows a bypass-charging reminder on the TV status bar if battery warms up",
                checked = thermalWarning,
                onCheckedChange = onToggleThermalWarning
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleBypassChargingNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bypassChargingEnabled) Color(0xFF4ADE80) else Color(0xFF00E5FF)
                    )
                ) {
                    Text(
                        text = if (bypassChargingEnabled) "✓ Bypass Charging: ON" else "Enable Bypass Charging Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onOpenBypassCharging,
                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text("Open Game Assistant", color = Color(0xFF00E5FF))
                }
            }
            Text(
                text = "Tip: Hold Guide (or press L1+R1+Start/Select) while docked to open the Quick Menu over any game.",
                color = Color(0xFF9BA3B4),
                fontSize = 12.sp
            )
        }

        // Custom 16:9 Tile Art
        if (pinnedApps.isNotEmpty()) {
            SectionCard(title = "Custom 16:9 Tile Art (Pinned Apps)") {
                pinnedApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onPickBanner(app.packageName) }) {
                                Text(if (app.banner != null) "Change Art" else "Set 16:9 Art", fontSize = 12.sp)
                            }
                            if (app.banner != null) {
                                OutlinedButton(onClick = { onClearBanner(app.packageName) }) {
                                    Text("Reset", color = Color(0xFFF87171), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Optional Shizuku Power Tweaks
        SectionCard(title = "Optional Shizuku Power Tweaks") {
            val shizukuText = when (shizukuStatus) {
                ShizukuStatus.READY -> "✓ Shizuku Connected & Ready"
                ShizukuStatus.NEEDS_PERMISSION -> "Shizuku running — tap below to grant permission"
                ShizukuStatus.NOT_RUNNING -> "Shizuku not running (optional — standard mode still works)"
            }
            val shizukuColor = when (shizukuStatus) {
                ShizukuStatus.READY -> Color(0xFF4ADE80)
                ShizukuStatus.NEEDS_PERMISSION -> Color(0xFFF59E0B)
                ShizukuStatus.NOT_RUNNING -> Color(0xFF9BA3B4)
            }
            Text(text = shizukuText, color = shizukuColor, fontSize = 13.sp)

            if (shizukuStatus == ShizukuStatus.NEEDS_PERMISSION) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = onRequestShizuku) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Grant Shizuku Permission")
                }
            }

            ToggleRow(
                title = "System Brightness 0% on Dock (Shizuku)",
                subtitle = "Drops system backlight to 0 when docked and restores on unplug",
                checked = shizukuScreenOff,
                onCheckedChange = onToggleShizukuScreenOff
            )
            ToggleRow(
                title = "Force-Move Running Apps to TV (Shizuku)",
                subtitle = "Relaunches already-open apps so they move cleanly onto the TV display",
                checked = shizukuForceRelaunch,
                onCheckedChange = onToggleShizukuForceRelaunch
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color(0xFF171B24),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF282F3E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF9BA3B4), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF00E5FF)
            )
        )
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${com.legion.switchdock.service.DockReturnAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
}
