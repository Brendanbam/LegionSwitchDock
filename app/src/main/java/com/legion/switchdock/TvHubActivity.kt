package com.legion.switchdock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.legion.switchdock.data.HubTheme
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.media.SwitchAudioFeedback
import com.legion.switchdock.service.DockMonitorService
import com.legion.switchdock.service.DockReturnAccessibilityService
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import com.legion.switchdock.streaming.ArtemisBoostManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ControllerCommand {
    LEFT, RIGHT, UP, DOWN,
    A_CONFIRM, B_BACK, X_ACTION, Y_DRAWER,
    L1_PREV, R1_NEXT, START_MENU
}

class TvHubActivity : ComponentActivity() {

    private var targetDisplayId: Int = Display.DEFAULT_DISPLAY
    private val controllerCommands = MutableSharedFlow<ControllerCommand>(extraBufferCapacity = 16)
    private var lastNavStepMs: Long = 0L
    private var stickNeutral: Boolean = true
    private var lastMenuToggleMs: Long = 0L
    private var lastHandledDownTime: Long = -1L
    private var lastHandledEventTime: Long = -1L
    private var lastHandledKeyCode: Int = -1
    private var lastHandledAction: Int = -1

    private val undockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DockMonitorService.ACTION_UNDOCKED -> finish()
                DockReturnAccessibilityService.ACTION_TOGGLE_QUICK_MENU -> {
                    controllerCommands.tryEmit(ControllerCommand.START_MENU)
                }
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
        applyPreferredDisplayMode()

        val filter = IntentFilter().apply {
            addAction(DockMonitorService.ACTION_UNDOCKED)
            addAction(DockReturnAccessibilityService.ACTION_TOGGLE_QUICK_MENU)
        }
        registerReceiver(undockReceiver, filter, RECEIVER_NOT_EXPORTED)

        setContent {
            MaterialTheme {
                SwitchTvHubScreen(
                    targetDisplayId = targetDisplayId,
                    commands = controllerCommands,
                    onLaunchApp = { app -> launchAppOnTv(app) },
                    onSetKeepScreenOn = { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                    onExitHub = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this
        isInstanceResumed = true
        hideSystemBars()
        window.decorView.requestFocus()
    }

    override fun onPause() {
        isInstanceResumed = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activeInstance = this
        targetDisplayId = intent.getIntExtra(
            EXTRA_DISPLAY_ID,
            display?.displayId ?: Display.DEFAULT_DISPLAY
        )
        applyPreferredDisplayMode()
        hideSystemBars()
        window.decorView.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            window.decorView.requestFocus()
        }
    }

    fun handleControllerKeyEvent(event: KeyEvent): Boolean {
        if (event.downTime == lastHandledDownTime &&
            event.eventTime == lastHandledEventTime &&
            event.keyCode == lastHandledKeyCode &&
            event.action == lastHandledAction
        ) {
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val cmd = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> ControllerCommand.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerCommand.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> ControllerCommand.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> ControllerCommand.DOWN
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> ControllerCommand.A_CONFIRM
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> ControllerCommand.B_BACK
                KeyEvent.KEYCODE_BUTTON_X -> ControllerCommand.X_ACTION
                KeyEvent.KEYCODE_BUTTON_Y -> ControllerCommand.Y_DRAWER
                KeyEvent.KEYCODE_BUTTON_L1 -> ControllerCommand.L1_PREV
                KeyEvent.KEYCODE_BUTTON_R1 -> ControllerCommand.R1_NEXT
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_MENU -> ControllerCommand.START_MENU
                else -> null
            }
            if (cmd != null) {
                lastHandledDownTime = event.downTime
                lastHandledEventTime = event.eventTime
                lastHandledKeyCode = event.keyCode
                lastHandledAction = event.action

                val isDirectional = cmd == ControllerCommand.LEFT ||
                    cmd == ControllerCommand.RIGHT ||
                    cmd == ControllerCommand.UP ||
                    cmd == ControllerCommand.DOWN
                val now = SystemClock.uptimeMillis()

                if (isDirectional) {
                    // Unified cooldown prevents KeyEvent + MotionEvent (AXIS_HAT) double-firing
                    // and slows held-key repeats to a comfortable 300ms step rate.
                    val minDelay = if (event.repeatCount > 0) 300L else 140L
                    if (now - lastNavStepMs < minDelay) return true
                    lastNavStepMs = now
                } else {
                    if (event.repeatCount > 0) return true
                    if (cmd == ControllerCommand.START_MENU) {
                        if (now - lastMenuToggleMs < 350L) return true
                        lastMenuToggleMs = now
                    }
                }
                controllerCommands.tryEmit(cmd)
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_MENU -> {
                    lastHandledDownTime = event.downTime
                    lastHandledEventTime = event.eventTime
                    lastHandledKeyCode = event.keyCode
                    lastHandledAction = event.action
                    return true
                }
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleControllerKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    fun handleControllerMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

            val x = if (abs(hatX) > abs(stickX)) hatX else stickX
            val y = if (abs(hatY) > abs(stickY)) hatY else stickY

            val threshold = 0.55f
            val now = SystemClock.uptimeMillis()
            if (abs(x) < 0.25f && abs(y) < 0.25f) {
                stickNeutral = true
            } else if ((stickNeutral && now - lastNavStepMs >= 140L) || now - lastNavStepMs >= 300L) {
                val cmd = when {
                    abs(x) >= abs(y) && x <= -threshold -> ControllerCommand.LEFT
                    abs(x) >= abs(y) && x >= threshold -> ControllerCommand.RIGHT
                    abs(y) > abs(x) && y <= -threshold -> ControllerCommand.UP
                    abs(y) > abs(x) && y >= threshold -> ControllerCommand.DOWN
                    else -> null
                }
                if (cmd != null) {
                    stickNeutral = false
                    lastNavStepMs = now
                    controllerCommands.tryEmit(cmd)
                    return true
                }
            }
        }
        return false
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (handleControllerMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun applyPreferredDisplayMode() {
        val prefs = DockPreferences(this)
        val ext = ExternalDisplayHelper.findExternalDisplay(this)
        val desiredModeId = if (prefs.preferredDisplayModeId != 0) {
            prefs.preferredDisplayModeId
        } else {
            ext?.supportedModes?.firstOrNull()?.modeId ?: 0
        }
        if (desiredModeId != 0) {
            val lp = window.attributes
            lp.preferredDisplayModeId = desiredModeId
            window.attributes = lp
        }
    }

    override fun onDestroy() {
        isInstanceResumed = false
        if (activeInstance === this) {
            activeInstance = null
        }
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

        // Apply low-latency Wi-Fi lock & Shizuku streaming boost before launching
        ArtemisBoostManager.onAppLaunched(this, app.packageName)

        // Never force-stop streaming apps (e.g. Artemis / Moonlight / Steam Link) when launching
        // from the TV Hub so an active game stream is resumed cleanly instead of killed!
        if (prefs.shizukuForceRelaunch &&
            externalId != Display.DEFAULT_DISPLAY &&
            !ArtemisBoostManager.isStreamingPackage(app.packageName)
        ) {
            ShizukuHelper.forceStopPackage(this, app.packageName)
        }
        ExternalDisplayHelper.launchPackageOnDisplay(this, app.packageName, externalId)
    }

    fun toggleMenu() {
        val now = SystemClock.uptimeMillis()
        if (now - lastMenuToggleMs < 350L) return
        lastMenuToggleMs = now
        controllerCommands.tryEmit(ControllerCommand.START_MENU)
    }

    companion object {
        const val EXTRA_DISPLAY_ID = "extra_display_id"
        @Volatile
        var isInstanceResumed: Boolean = false
        @Volatile
        var activeInstance: TvHubActivity? = null

        fun dispatchGlobalKeyEvent(event: KeyEvent): Boolean {
            val instance = activeInstance ?: return false
            if (!isInstanceResumed) return false
            return instance.handleControllerKeyEvent(event)
        }

        fun dispatchGlobalMotionEvent(event: MotionEvent): Boolean {
            val instance = activeInstance ?: return false
            if (!isInstanceResumed) return false
            return instance.handleControllerMotionEvent(event)
        }

        fun toggleQuickMenuFromService(): Boolean {
            val instance = activeInstance ?: return false
            if (!isInstanceResumed) return false
            instance.toggleMenu()
            return true
        }
    }
}

@Composable
private fun SwitchTvHubScreen(
    targetDisplayId: Int,
    commands: MutableSharedFlow<ControllerCommand>,
    onLaunchApp: (AppEntry) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onExitHub: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { DockPreferences(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinnedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var showAllAppsDrawer by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var isIdleDimmed by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var activeTheme by remember { mutableStateOf(HubTheme.fromId(prefs.hubThemeId)) }

    // Deterministic controller selection states (never lost when display focus shifts!)
    var selectedTileIndex by remember { mutableIntStateOf(0) }
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    var selectedMenuIndex by remember { mutableIntStateOf(0) }
    var menuStateTick by remember { mutableIntStateOf(0) }

    val rowListState = rememberLazyListState()
    val drawerGridState = rememberLazyGridState()

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

    fun reloadApps() {
        val loaded = AppRepository.loadLaunchableApps(context)
        allApps = loaded
        pinnedApps = AppRepository.getOrSeedPinnedApps(context, loaded, prefs)
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    val totalRowTiles = pinnedApps.size + 2 // pinnedApps + "All Apps & Pin" + "Quick Menu"
    val safeTileIndex = selectedTileIndex.coerceIn(0, (totalRowTiles - 1).coerceAtLeast(0))
    val focusedTitle = when {
        safeTileIndex < pinnedApps.size -> pinnedApps[safeTileIndex].label
        safeTileIndex == pinnedApps.size -> "All Apps & Pin Manager"
        else -> "System Quick Menu"
    }
    val focusedPackage = pinnedApps.getOrNull(safeTileIndex)?.packageName

    val latestPinnedApps by rememberUpdatedState(pinnedApps)
    val latestAllApps by rememberUpdatedState(allApps)

    fun triggerSleepMode() {
        showQuickMenu = false
        isIdleDimmed = true
        onSetKeepScreenOn(false)
        if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
            ShizukuHelper.sleepDevice(context)
        }
    }

    LaunchedEffect(isIdleDimmed) {
        onSetKeepScreenOn(!isIdleDimmed)
    }

    // Handle all controller commands deterministically using live state (never stale vals!)
    LaunchedEffect(Unit) {
        commands.collect { cmd ->
            lastInteractionMs = System.currentTimeMillis()
            if (isIdleDimmed) {
                isIdleDimmed = false
                onSetKeepScreenOn(true)
                SwitchAudioFeedback.playMenu(context)
                return@collect
            }

            val currentPinned = latestPinnedApps
            val currentAll = latestAllApps
            val currentTotalTiles = currentPinned.size + 2
            val currentSafeTileIdx = selectedTileIndex.coerceIn(0, (currentTotalTiles - 1).coerceAtLeast(0))
            val currentFocusedPkg = currentPinned.getOrNull(currentSafeTileIdx)?.packageName

            when {
                showQuickMenu -> {
                    val menuCount = 11
                    when (cmd) {
                        ControllerCommand.UP, ControllerCommand.LEFT, ControllerCommand.L1_PREV -> {
                            selectedMenuIndex = (selectedMenuIndex - 1 + menuCount) % menuCount
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.DOWN, ControllerCommand.RIGHT, ControllerCommand.R1_NEXT -> {
                            selectedMenuIndex = (selectedMenuIndex + 1) % menuCount
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.B_BACK, ControllerCommand.START_MENU -> {
                            showQuickMenu = false
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.A_CONFIRM -> {
                            SwitchAudioFeedback.playSelect(context)
                            when (selectedMenuIndex) {
                                0 -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                                1 -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                                2 -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                                3 -> {
                                    showQuickMenu = false
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                4 -> prefs.uiSoundsEnabled = !prefs.uiSoundsEnabled
                                5 -> prefs.idleDimmerEnabled = !prefs.idleDimmerEnabled
                                6 -> {
                                    showQuickMenu = false
                                    isIdleDimmed = true
                                }
                                7 -> {
                                    if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                                        val next = !prefs.bypassChargingEnabled
                                        prefs.bypassChargingEnabled = next
                                        ShizukuHelper.setBypassCharging(context, next)
                                    } else {
                                        showQuickMenu = false
                                        DockHealthMonitor.openBypassChargingSettings(context)
                                    }
                                }
                                8 -> {
                                    val nextTheme = activeTheme.next()
                                    activeTheme = nextTheme
                                    prefs.hubThemeId = nextTheme.id
                                }
                                9 -> triggerSleepMode()
                                10 -> showQuickMenu = false
                            }
                            menuStateTick++
                        }
                        else -> {}
                    }
                }

                showAllAppsDrawer -> {
                    val count = currentAll.size.coerceAtLeast(1)
                    val cols = 5
                    when (cmd) {
                        ControllerCommand.LEFT -> {
                            selectedDrawerIndex = (selectedDrawerIndex - 1 + count) % count
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.RIGHT -> {
                            selectedDrawerIndex = (selectedDrawerIndex + 1) % count
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.UP -> {
                            selectedDrawerIndex = (selectedDrawerIndex - cols).coerceAtLeast(0)
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.DOWN -> {
                            selectedDrawerIndex = (selectedDrawerIndex + cols).coerceAtMost(count - 1)
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.A_CONFIRM -> {
                            currentAll.getOrNull(selectedDrawerIndex)?.let { app ->
                                showAllAppsDrawer = false
                                onLaunchApp(app)
                            }
                        }
                        ControllerCommand.X_ACTION -> {
                            currentAll.getOrNull(selectedDrawerIndex)?.let { app ->
                                SwitchAudioFeedback.playSelect(context)
                                prefs.togglePinnedPackage(app.packageName)
                                reloadApps()
                            }
                        }
                        ControllerCommand.B_BACK, ControllerCommand.Y_DRAWER -> {
                            showAllAppsDrawer = false
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.START_MENU -> {
                            showAllAppsDrawer = false
                            selectedMenuIndex = 0
                            showQuickMenu = true
                            SwitchAudioFeedback.playMenu(context)
                        }
                        else -> {}
                    }
                }

                else -> {
                    when (cmd) {
                        ControllerCommand.LEFT, ControllerCommand.UP -> {
                            selectedTileIndex = (currentSafeTileIdx - 1 + currentTotalTiles) % currentTotalTiles
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.RIGHT, ControllerCommand.DOWN -> {
                            selectedTileIndex = (currentSafeTileIdx + 1) % currentTotalTiles
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.A_CONFIRM -> {
                            when {
                                currentSafeTileIdx < currentPinned.size -> onLaunchApp(currentPinned[currentSafeTileIdx])
                                currentSafeTileIdx == currentPinned.size -> {
                                    SwitchAudioFeedback.playMenu(context)
                                    selectedDrawerIndex = 0
                                    showAllAppsDrawer = true
                                }
                                else -> {
                                    SwitchAudioFeedback.playMenu(context)
                                    selectedMenuIndex = 0
                                    showQuickMenu = true
                                }
                            }
                        }
                        ControllerCommand.Y_DRAWER -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedDrawerIndex = 0
                            showAllAppsDrawer = true
                        }
                        ControllerCommand.START_MENU -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedMenuIndex = 0
                            showQuickMenu = true
                        }
                        ControllerCommand.X_ACTION -> {
                            if (currentFocusedPkg != null) {
                                prefs.togglePinnedPackage(currentFocusedPkg)
                                SwitchAudioFeedback.playSelect(context)
                                reloadApps()
                            }
                        }
                        ControllerCommand.L1_PREV -> {
                            if (currentFocusedPkg != null && prefs.movePinnedPackage(currentFocusedPkg, -1)) {
                                selectedTileIndex = (currentSafeTileIdx - 1).coerceAtLeast(0)
                                SwitchAudioFeedback.playMove(context)
                                reloadApps()
                            }
                        }
                        ControllerCommand.R1_NEXT -> {
                            if (currentFocusedPkg != null && prefs.movePinnedPackage(currentFocusedPkg, +1)) {
                                selectedTileIndex = (currentSafeTileIdx + 1).coerceAtMost((currentPinned.size - 1).coerceAtLeast(0))
                                SwitchAudioFeedback.playMove(context)
                                reloadApps()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    LaunchedEffect(safeTileIndex) {
        runCatching { rowListState.animateScrollToItem(safeTileIndex) }
    }

    LaunchedEffect(selectedDrawerIndex, showAllAppsDrawer) {
        if (showAllAppsDrawer && allApps.isNotEmpty()) {
            runCatching { drawerGridState.animateScrollToItem(selectedDrawerIndex.coerceIn(0, allApps.lastIndex)) }
        }
    }

    // Live status polling (clock, battery/thermals, controllers, network, display mode, idle timer)
    // Automatically pauses when TvHubActivity is in the background (e.g. during Artemis streaming)
    // so zero CPU/binder overhead occurs while gaming!
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val idleTimeoutMs = 5 * 60 * 1000L

        if (prefs.artemisWifiBoostEnabled) {
            ArtemisBoostManager.setBoostActive(context, true)
        }

        while (true) {
            if (!TvHubActivity.isInstanceResumed) {
                delay(2500)
                continue
            }
            val nowDate = Date()
            currentTime = timeFormat.format(nowDate)
            currentDate = dateFormat.format(nowDate)

            health = DockHealthMonitor.readSnapshot(context)

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val baseNet = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                else -> "Offline"
            }
            networkType = if (baseNet == "Wi-Fi" && ArtemisBoostManager.isLowLatencyLockHeld) {
                "Wi-Fi • Low-Latency"
            } else {
                baseNet
            }

            val ext = ExternalDisplayHelper.findExternalDisplay(context)
            displaySummary = ext?.summary ?: "Mirror / TV Mode"
            activeTheme = HubTheme.fromId(prefs.hubThemeId)

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
                    colors = listOf(activeTheme.bgTop, activeTheme.bgMid, activeTheme.bgBottom)
                )
            )
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
                        color = activeTheme.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = activeTheme.accent,
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

                    val lowPad = health.controllers.firstOrNull { (it.batteryPercent ?: 100) <= 20 }
                    if (lowPad != null) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.20f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Gamepad,
                                    contentDescription = "Low Controller Battery",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Controller Low (${lowPad.batteryPercent}%)",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

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
                        val padPct = firstCtrl.batteryPercent
                        val padTint = when {
                            padPct != null && padPct <= 20 -> Color(0xFFF87171)
                            padPct != null && padPct <= 40 -> Color(0xFFFBBF24)
                            else -> Color(0xFF4ADE80)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Gamepad,
                                contentDescription = "Controller Connected",
                                tint = padTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(ctrlLabel, color = padTint, fontSize = 13.sp)
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
                    state = rowListState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    itemsIndexed(pinnedApps, key = { _, app -> app.packageName }) { index, app ->
                        SwitchAppTile(
                            app = app,
                            isSelected = (safeTileIndex == index),
                            onClick = {
                                selectedTileIndex = index
                                onLaunchApp(app)
                            }
                        )
                    }

                    item(key = "add_more_apps_tile") {
                        val idx = pinnedApps.size
                        ActionTile(
                            title = "All Apps & Pin",
                            icon = Icons.Rounded.Add,
                            accent = Color(0xFF00E5FF),
                            isSelected = (safeTileIndex == idx),
                            onClick = {
                                selectedTileIndex = idx
                                SwitchAudioFeedback.playMenu(context)
                                selectedDrawerIndex = 0
                                showAllAppsDrawer = true
                            }
                        )
                    }

                    item(key = "quick_menu_tile") {
                        val idx = pinnedApps.size + 1
                        ActionTile(
                            title = "Quick Menu",
                            icon = Icons.Rounded.Settings,
                            accent = Color(0xFF4ADE80),
                            isSelected = (safeTileIndex == idx),
                            onClick = {
                                selectedTileIndex = idx
                                SwitchAudioFeedback.playMenu(context)
                                selectedMenuIndex = 0
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
                selectedIndex = selectedDrawerIndex.coerceIn(0, (allApps.size - 1).coerceAtLeast(0)),
                gridState = drawerGridState,
                onLaunchApp = { idx, app ->
                    selectedDrawerIndex = idx
                    showAllAppsDrawer = false
                    onLaunchApp(app)
                },
                onTogglePin = { idx, app ->
                    selectedDrawerIndex = idx
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
                activeTheme = activeTheme,
                selectedIndex = selectedMenuIndex,
                stateTick = menuStateTick,
                onSelectIndex = { selectedMenuIndex = it },
                onCycleTheme = {
                    val nextTheme = activeTheme.next()
                    activeTheme = nextTheme
                    prefs.hubThemeId = nextTheme.id
                },
                onTriggerScreensaver = {
                    showQuickMenu = false
                    isIdleDimmed = true
                },
                onTriggerSleepMode = {
                    triggerSleepMode()
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
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(140),
        label = "tileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
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
                    width = if (isSelected) 4.dp else 1.5.dp,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E3545),
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
            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFB8C0D0),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(140),
        label = "actionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF191D27))
                .border(
                    width = if (isSelected) 4.dp else 1.5.dp,
                    color = if (isSelected) accent else Color(0xFF2E3545),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) accent else Color(0xFF9BA3B4),
                modifier = Modifier.size(58.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = if (isSelected) accent else Color(0xFFB8C0D0),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun InHubQuickMenuOverlay(
    health: DockHealthSnapshot,
    prefs: DockPreferences,
    activeTheme: HubTheme,
    selectedIndex: Int,
    stateTick: Int,
    onSelectIndex: (Int) -> Unit,
    onCycleTheme: () -> Unit,
    onTriggerScreensaver: () -> Unit,
    onTriggerSleepMode: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumePct by remember { mutableIntStateOf(readVolumePct(audioManager)) }
    var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }
    var uiSounds by remember { mutableStateOf(prefs.uiSoundsEnabled) }
    var idleDimmer by remember { mutableStateOf(prefs.idleDimmerEnabled) }
    var bypassEnabled by remember { mutableStateOf(prefs.bypassChargingEnabled) }
    val shizukuReady = remember(selectedIndex, stateTick) { ShizukuHelper.getStatus() == ShizukuStatus.READY }

    LaunchedEffect(selectedIndex, stateTick) {
        volumePct = readVolumePct(audioManager)
        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        uiSounds = prefs.uiSoundsEnabled
        idleDimmer = prefs.idleDimmerEnabled
        bypassEnabled = prefs.bypassChargingEnabled
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
                        isSelected = (selectedIndex == 0),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectIndex(0)
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                    QuickMenuButton(
                        label = "Vol +",
                        isSelected = (selectedIndex == 1),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectIndex(1)
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                    QuickMenuButton(
                        label = if (isMuted) "Unmute" else "Mute",
                        isSelected = (selectedIndex == 2),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectIndex(2)
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                            volumePct = readVolumePct(audioManager)
                            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                        }
                    )
                }

                QuickMenuButton(
                    label = "Audio Output / Bluetooth Settings",
                    isSelected = (selectedIndex == 3),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(3)
                        onClose()
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                QuickMenuButton(
                    label = "UI Navigation Sounds: ${if (uiSounds) "ON" else "OFF"}",
                    isSelected = (selectedIndex == 4),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(4)
                        uiSounds = !uiSounds
                        prefs.uiSoundsEnabled = uiSounds
                    }
                )
                QuickMenuButton(
                    label = "5-Min Idle Screensaver: ${if (idleDimmer) "ON" else "OFF"}",
                    isSelected = (selectedIndex == 5),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(5)
                        idleDimmer = !idleDimmer
                        prefs.idleDimmerEnabled = idleDimmer
                    }
                )
                QuickMenuButton(
                    label = "Screensaver Now",
                    isSelected = (selectedIndex == 6),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(6)
                        onTriggerScreensaver()
                    }
                )
                QuickMenuButton(
                    label = if (shizukuReady) {
                        "Bypass Charging: ${if (bypassEnabled) "ON" else "OFF"} (Press A to Toggle)"
                    } else {
                        "Bypass Charging / Game Assistant"
                    },
                    isSelected = (selectedIndex == 7),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(7)
                        if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                            bypassEnabled = !bypassEnabled
                            prefs.bypassChargingEnabled = bypassEnabled
                            ShizukuHelper.setBypassCharging(context, bypassEnabled)
                        } else {
                            onClose()
                            DockHealthMonitor.openBypassChargingSettings(context)
                        }
                    }
                )
                QuickMenuButton(
                    label = "Theme: ${activeTheme.displayName} (Press A to Cycle)",
                    isSelected = (selectedIndex == 8),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(8)
                        onCycleTheme()
                    }
                )
                QuickMenuButton(
                    label = "Sleep Mode (Tablet & TV)",
                    isSelected = (selectedIndex == 9),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(9)
                        onTriggerSleepMode()
                    }
                )
                QuickMenuButton(
                    label = "Close Menu (B)",
                    isSelected = (selectedIndex == 10),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelectIndex(10)
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickMenuButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = if (isSelected) Color(0xFF004456) else Color(0xFF1F2430),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E3545)
        ),
        modifier = modifier
            .clickable {
                SwitchAudioFeedback.playSelect(context)
                onClick()
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
    selectedIndex: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onLaunchApp: (Int, AppEntry) -> Unit,
    onTogglePin: (Int, AppEntry) -> Unit,
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
                        text = "• Press A to Launch, X to Pin/Unpin, B to Close",
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
                state = gridState,
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(allApps, key = { _, app -> app.packageName }) { index, app ->
                    val isPinned = pinnedPackages.contains(app.packageName)
                    val isSelected = (index == selectedIndex)

                    Surface(
                        color = if (isSelected) Color(0xFF232B3A) else Color(0xFF171B24),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF282F3E)
                        ),
                        modifier = Modifier
                            .clickable { onLaunchApp(index, app) }
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
                                            .clickable { onTogglePin(index, app) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = app.label,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
