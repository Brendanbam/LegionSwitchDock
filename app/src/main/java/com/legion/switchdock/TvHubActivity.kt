package com.legion.switchdock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.service.DockMonitorService
import com.legion.switchdock.shizuku.ShizukuHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var focusedTitle by remember { mutableStateOf("Legion Switch Hub") }
    var focusedPackage by remember { mutableStateOf<String?>(null) }

    var currentTime by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var hasController by remember { mutableStateOf(false) }
    var networkType by remember { mutableStateOf("Wi-Fi") }
    var displaySummary by remember { mutableStateOf("TV Mode") }

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

    // Live status polling (clock, battery, controller, network, display mode)
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (true) {
            currentTime = timeFormat.format(Date())

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            isCharging = bm.isCharging

            val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            hasController = im.inputDeviceIds.any { id ->
                val dev = im.getInputDevice(id) ?: return@any false
                val src = dev.sources
                (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            networkType = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                else -> "Offline"
            }

            val ext = ExternalDisplayHelper.findExternalDisplay(context)
            displaySummary = ext?.summary ?: "Preview (${targetDisplayId})"

            delay(2000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        showAllAppsDrawer = !showAllAppsDrawer
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_X -> {
                        val pkg = focusedPackage
                        if (pkg != null && !showAllAppsDrawer) {
                            prefs.togglePinnedPackage(pkg)
                            reloadApps()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                        if (showAllAppsDrawer) {
                            showAllAppsDrawer = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (hasController) {
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
                            Text("Controller", color = Color(0xFF4ADE80), fontSize = 13.sp)
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
                            if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                            contentDescription = "Battery",
                            tint = if (isCharging) Color(0xFF4ADE80) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("$batteryPct%", color = Color.White, fontSize = 14.sp)
                    }

                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Center Focused Title + Horizontal Switch Card Row
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
                                focusedTitle = "All Apps & Pin Manager"
                                focusedPackage = null
                            },
                            onClick = { showAllAppsDrawer = true }
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
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ButtonPrompt("A", "Launch")
                    ButtonPrompt("X", "Unpin Tile")
                    ButtonPrompt("Y", "All Apps")
                    ButtonPrompt("B", "Back")
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
                    prefs.togglePinnedPackage(app.packageName)
                    reloadApps()
                },
                onClose = { showAllAppsDrawer = false }
            )
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
        targetValue = if (isFocused) 1.09f else 1.0f,
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
                .size(196.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1F2430))
                .border(
                    width = if (isFocused) 4.dp else 1.5.dp,
                    color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF2E3545),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = app.label,
            color = if (isFocused) Color(0xFF00E5FF) else Color(0xFFB8C0D0),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(190.dp)
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
        targetValue = if (isFocused) 1.09f else 1.0f,
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
                .size(196.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF191D27))
                .border(
                    width = if (isFocused) 4.dp else 1.5.dp,
                    color = if (isFocused) accent else Color(0xFF2E3545),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isFocused) accent else Color(0xFF9BA3B4),
                modifier = Modifier.size(64.dp)
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
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = button,
                color = Color(0xFF101216),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }
        Text(text = label, color = Color(0xFFD1D5DB), fontSize = 14.sp)
    }
}
