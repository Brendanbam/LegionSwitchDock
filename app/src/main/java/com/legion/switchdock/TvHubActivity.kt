package com.legion.switchdock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.legion.switchdock.data.AppEntry
import com.legion.switchdock.data.AppRepository
import com.legion.switchdock.data.DockHealthMonitor
import com.legion.switchdock.data.DockHealthSnapshot
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.media.SwitchAudioFeedback
import com.legion.switchdock.service.DockMonitorService
import com.legion.switchdock.shizuku.ShizukuHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TvHubActivity : ComponentActivity() {

    private var targetDisplayId: Int = Display.DEFAULT_DISPLAY

    private val undockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DockMonitorService.ACTION_UNDOCKED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        targetDisplayId = intent.getIntExtra(
            EXTRA_DISPLAY_ID,
            display?.displayId ?: Display.DEFAULT_DISPLAY
        )

        registerReceiver(
            undockReceiver,
            IntentFilter(DockMonitorService.ACTION_UNDOCKED),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            MaterialTheme {
                SwitchTvHubScreen(
                    targetDisplayId = targetDisplayId,
                    onLaunchApp = { app -> launchAppOnTv(app) },
                    onExitHub = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        targetDisplayId = intent.getIntExtra(
            EXTRA_DISPLAY_ID,
            display?.displayId ?: Display.DEFAULT_DISPLAY
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(undockReceiver) }
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun launchAppOnTv(app: AppEntry) {
        SwitchAudioFeedback.playSelect(this)
        val prefs = DockPreferences(this)
        val externalId = ExternalDisplayHelper.findExternalDisplay(this)?.displayId ?: targetDisplayId

        if (prefs.shizukuForceRelaunch && externalId != Display.DEFAULT_DISPLAY) {
            ShizukuHelper.forceStopPackage(this, app.packageName)
        }
        ExternalDisplayHelper.launchPackageOnDisplay(this, app.packageName, externalId)
    }

    companion object {
        const val EXTRA_DISPLAY_ID = "extra_display_id"
    }
}

@Composable
private fun SwitchTvHubScreen(
    targetDisplayId: Int,
    onLaunchApp: (AppEntry) -> Unit,
    onExitHub: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { DockPreferences(context) }

    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinnedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var showAllAppsDrawer by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var isIdleDimmed by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var focusedTitle by remember { mutableStateOf("Legion Switch Hub") }
    var focusedPackage by remember { mutableStateOf<String?>(null) }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var networkType by remember { mutableStateOf("Wi-Fi") }
    var displaySummary by remember { mutableStateOf("TV Mode") }
    var health by remember {
        mutableStateOf(
            DockHealthSnapshot(
                batteryPercent = 100,
                isCharging = false,
                temperatureC = 30.0f,
                wattsEstimate = 0f,
                isThermalWarning = false,
                controllers = emptyList()
            )
        )
    }

    val firstCardFocus = remember { FocusRequester() }

    fun reloadApps() {
        val loaded = AppRepository.loadLaunchableApps(context)
        allApps = loaded
        pinnedApps = AppRepository.getOrSeedPinnedApps(context, loaded, prefs)
    }

    LaunchedEffect(Unit) {
        reloadApps()
        delay(120)
        runCatching { firstCardFocus.requestFocus() }
    }

    // Live status polling (clock, battery/thermals, controllers, network, display mode, idle timer)
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val idleTimeoutMs = 5 * 60 * 1000L

        while (true) {
            val nowDate = Date()
            currentTime = timeFormat.format(nowDate)
            currentDate = dateFormat.format(nowDate)

            health = DockHealthMonitor.readSnapshot(context)

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            networkType = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                else -> "Offline"
            }

            val ext = ExternalDisplayHelper.findExternalDisplay(context)
            displaySummary = ext?.summary ?: "Preview ($targetDisplayId)"

            if (prefs.idleDimmerEnabled && !isIdleDimmed &&
                System.currentTimeMillis() - lastInteractionMs >= idleTimeoutMs
            ) {
                isIdleDimmed = true
            }

            delay(2000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1117), Color(0xFF171B24), Color(0xFF0C0E13))
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionMs = System.currentTimeMillis()
                    if (isIdleDimmed) {
                        isIdleDimmed = false
                        SwitchAudioFeedback.playMenu(context)
                        return@onPreviewKeyEvent true
                    }

                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_MENU -> {
                            showQuickMenu = !showQuickMenu
                            if (showQuickMenu) SwitchAudioFeedback.playMenu(context)
                            else SwitchAudioFeedback.playBack(context)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            if (!showQuickMenu) {
                                showAllAppsDrawer = !showAllAppsDrawer
                                if (showAllAppsDrawer) SwitchAudioFeedback.playMenu(context)
                                else SwitchAudioFeedback.playBack(context)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            val pkg = focusedPackage
                            if (pkg != null && !showAllAppsDrawer && !showQuickMenu) {
                                prefs.togglePinnedPackage(pkg)
                                SwitchAudioFeedback.playSelect(context)
                                reloadApps()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            val pkg = focusedPackage
                            if (pkg != null && !showAllAppsDrawer && !showQuickMenu) {
                                if (prefs.movePinnedPackage(pkg, -1)) {
                                    SwitchAudioFeedback.playMove(context)
                                    reloadApps()
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            val pkg = focusedPackage
                            if (pkg != null && !showAllAppsDrawer && !showQuickMenu) {
                                if (prefs.movePinnedPackage(pkg, +1)) {
                                    SwitchAudioFeedback.playMove(context)
                                    reloadApps()
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            when {
                                showQuickMenu -> {
                                    showQuickMenu = false
                                    SwitchAudioFeedback.playBack(context)
                                    true
                                }
                                showAllAppsDrawer -> {
                                    showAllAppsDrawer = false
                                    SwitchAudioFeedback.playBack(context)
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Switch-Style Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "LEGION SWITCH",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                    Text(
                        text = displaySummary,
                        color = Color(0xFF9BA3B4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (health.isThermalWarning && prefs.thermalWarningEnabled) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Thermostat,
                                    contentDescription = "High Temp",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", health.temperatureC)}°C • Bypass Charging Recommended",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (health.controllers.isNotEmpty()) {
                        val firstCtrl = health.controllers.first()
                        val ctrlLabel = buildString {
                            if (health.controllers.size > 1) {
                                append("${health.controllers.size} Pads")
                            } else {
                                append("Pad")
                            }
                            if (firstCtrl.batteryPercent != null) {
                                append(" ${firstCtrl.batteryPercent}%")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Gamepad,
                                contentDescription = "Controller Connected",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(ctrlLabel, color = Color(0xFF4ADE80), fontSize = 13.sp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (networkType == "Ethernet") Icons.Rounded.Lan else Icons.Rounded.Wifi,
                            contentDescription = networkType,
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(19.dp)
                        )
                        Text(networkType, color = Color(0xFFD1D5DB), fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (health.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                            contentDescription = "Battery",
                            tint = if (health.isCharging) Color(0xFF4ADE80) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${health.batteryPercent}% (${String.format(Locale.US, "%.0f", health.temperatureC)}°C)",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Center Focused Title + Horizontal 16:9 Switch Card Row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = focusedTitle,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    itemsIndexed(pinnedApps, key = { _, app -> app.packageName }) { index, app ->
                        SwitchAppTile(
                            app = app,
                            modifier = if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier,
                            onFocused = {
                                if (focusedPackage != app.packageName) {
                                    SwitchAudioFeedback.playMove(context)
                                }
                                focusedTitle = app.label
                                focusedPackage = app.packageName
                            },
                            onClick = { onLaunchApp(app) }
                        )
                    }

                    item(key = "add_more_apps_tile") {
                        ActionTile(
                            title = "All Apps & Pin",
                            icon = Icons.Rounded.Add,
                            accent = Color(0xFF00E5FF),
                            onFocused = {
                                SwitchAudioFeedback.playMove(context)
                                focusedTitle = "All Apps & Pin Manager"
                                focusedPackage = null
                            },
                            onClick = {
                                SwitchAudioFeedback.playMenu(context)
                                showAllAppsDrawer = true
                            }
                        )
                    }

                    item(key = "quick_menu_tile") {
                        ActionTile(
                            title = "Quick Menu",
                            icon = Icons.Rounded.Settings,
                            accent = Color(0xFF4ADE80),
                            onFocused = {
                                SwitchAudioFeedback.playMove(context)
                                focusedTitle = "System Quick Menu"
                                focusedPackage = null
                            },
                            onClick = {
                                SwitchAudioFeedback.playMenu(context)
                                showQuickMenu = true
                            }
                        )
                    }
                }
            }

            // Bottom Switch-Style Controller Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ButtonPrompt("A", "Launch")
                    ButtonPrompt("L/R", "Reorder")
                    ButtonPrompt("X", "Unpin")
                    ButtonPrompt("Y", "All Apps")
                    ButtonPrompt("Start", "Quick Menu")
                }

                Text(
                    text = "Exit Hub",
                    color = Color(0xFF9BA3B4),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onExitHub() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // All Apps & Pin Manager Overlay
        if (showAllAppsDrawer) {
            AllAppsDrawerOverlay(
                allApps = allApps,
                pinnedPackages = pinnedApps.map { it.packageName }.toSet(),
                onLaunchApp = { app ->
                    showAllAppsDrawer = false
                    onLaunchApp(app)
                },
                onTogglePin = { app ->
                    SwitchAudioFeedback.playSelect(context)
                    prefs.togglePinnedPackage(app.packageName)
                    reloadApps()
                },
                onClose = {
                    SwitchAudioFeedback.playBack(context)
                    showAllAppsDrawer = false
                }
            )
        }

        // In-Hub Switch Quick Menu Overlay
        if (showQuickMenu) {
            InHubQuickMenuOverlay(
                health = health,
                prefs = prefs,
                onTriggerScreensaver = {
                    showQuickMenu = false
                    isIdleDimmed = true
                },
                onClose = {
                    SwitchAudioFeedback.playBack(context)
                    showQuickMenu = false
                }
            )
        }

        // 5-Minute Idle Dimmer / Screensaver Clock
        AnimatedVisibility(
            visible = isIdleDimmed,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .clickable {
                        lastInteractionMs = System.currentTimeMillis()
                        isIdleDimmed = false
                        SwitchAudioFeedback.playMenu(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$currentDate • ${health.batteryPercent}% (${String.format(Locale.US, "%.1f", health.temperatureC)}°C)",
                        color = Color(0xFF9BA3B4),
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Press any controller button to wake",
                        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchAppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(140),
        label = "tileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(236.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF283042), Color(0xFF1A1F2B))
                    )
                )
                .border(
                    width = if (isFocused) 4.dp else 1.5.dp,
                    color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF2E3545),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = app.label,
            color = if (isFocused) Color(0xFF00E5FF) else Color(0xFFB8C0D0),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(226.dp)
        )
    }
}

@Composable
private fun ActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(140),
        label = "actionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF191D27))
                .border(
                    width = if (isFocused) 4.dp else 1.5.dp,
                    color = if (isFocused) accent else Color(0xFF2E3545),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isFocused) accent else Color(0xFF9BA3B4),
                modifier = Modifier.size(58.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = if (isFocused) accent else Color(0xFFB8C0D0),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun InHubQuickMenuOverlay(
    health: DockHealthSnapshot,
    prefs: DockPreferences,
    onTriggerScreensaver: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumePct by remember { mutableIntStateOf(readVolumePct(audioManager)) }
    var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }
    var uiSounds by remember { mutableStateOf(prefs.uiSoundsEnabled) }
    var idleDimmer by remember { mutableStateOf(prefs.idleDimmerEnabled) }
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstItemFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8080A0E))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF141821),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFF00E5FF)),
            modifier = Modifier
                .width(500.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "LEGION QUICK MENU",
                    color = Color(0xFF00E5FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                val chargeLabel = if (health.isCharging) "Charging ${health.wattsEstimate}W" else "Discharging"
                Text(
                    text = "${health.batteryPercent}% • ${String.format(Locale.US, "%.1f", health.temperatureC)}°C • $chargeLabel",
                    color = if (health.isThermalWarning) Color(0xFFF59E0B) else Color(0xFF9BA3B4),
                    fontSize = 13.sp
                )

                Text(
                    text = if (isMuted) "Media Volume: MUTED ($volumePct%)" else "Media Volume: $volumePct%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickMenuButton(
                        label = "Vol -",
                        modifier = Modifier.weight(1f).focusRequester(firstItemFocus),
                        onClick = {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                    QuickMenuButton(
                        label = "Vol +",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                    QuickMenuButton(
                        label = if (isMuted) "Unmute" else "Mute",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                }

                QuickMenuButton(
                    label = "Audio Output / Bluetooth Settings",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onClose()
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                QuickMenuButton(
                    label = "UI Navigation Sounds: ${if (uiSounds) "ON" else "OFF"}",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        uiSounds = !uiSounds
                        prefs.uiSoundsEnabled = uiSounds
                    }
                )
                QuickMenuButton(
                    label = "5-Min Idle Screensaver: ${if (idleDimmer) "ON" else "OFF"}",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        idleDimmer = !idleDimmer
                        prefs.idleDimmerEnabled = idleDimmer
                    }
                )
                QuickMenuButton(
                    label = "Screensaver Now",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onTriggerScreensaver
                )
                QuickMenuButton(
                    label = "Bypass Charging / Game Assistant",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onClose()
                        DockHealthMonitor.openBypassChargingSettings(context)
                    }
                )
                QuickMenuButton(
                    label = "Close Menu (B)",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun QuickMenuButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isFocused) Color(0xFF004456) else Color(0xFF1F2430),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF2E3545)
        ),
        modifier = modifier
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) SwitchAudioFeedback.playMove(context)
            }
            .focusable()
            .clickable {
                SwitchAudioFeedback.playSelect(context)
                onClick()
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

private fun readVolumePct(audioManager: AudioManager): Int {
    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return ((cur.toFloat() / max) * 100f).roundToInt()
}

@Composable
private fun AllAppsDrawerOverlay(
    allApps: List<AppEntry>,
    pinnedPackages: Set<String>,
    onLaunchApp: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB0B0D12))
            .padding(36.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = null, tint = Color(0xFF00E5FF))
                    Text(
                        text = "All Installed Apps",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Press A to Launch, X to Pin/Unpin",
                        color = Color(0xFF9BA3B4),
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = Color(0xFF242A38),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { onClose() }
                ) {
                    Text(
                        text = "Close (B)",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 175.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allApps, key = { it.packageName }) { app ->
                    val isPinned = pinnedPackages.contains(app.packageName)
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        color = if (isFocused) Color(0xFF232B3A) else Color(0xFF171B24),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isFocused) 3.dp else 1.dp,
                            color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF282F3E)
                        ),
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { key ->
                                if (key.type == KeyEventType.KeyDown &&
                                    key.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_X
                                ) {
                                    onTogglePin(app)
                                    true
                                } else {
                                    false
                                }
                            }
                            .clickable { onLaunchApp(app) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .align(Alignment.Center)
                                )
                                if (isPinned) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Pinned",
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .clickable { onTogglePin(app) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonPrompt(button: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = button,
                color = Color(0xFF101216),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }
        Text(text = label, color = Color(0xFFD1D5DB), fontSize = 14.sp)
    }
}
