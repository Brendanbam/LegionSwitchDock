package com.legion.switchdock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewInAr
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.legion.switchdock.bluetooth.BluetoothHub
import com.legion.switchdock.bluetooth.BluetoothPanel
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
import com.legion.switchdock.display.DisplayProfile
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

    private val bluetoothHub by lazy { BluetoothHub(this) }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BluetoothHub.PERMISSION_REQUEST) bluetoothHub.onPermissionResult()
    }

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
                val initialPrefs = remember { DockPreferences(this) }
                var cursorAccent by remember {
                    mutableStateOf(HubTheme.fromId(initialPrefs.getThemeId(initialPrefs.activeProfile)).accent)
                }
                Box(Modifier.fillMaxSize()) {
                    SwitchTvHubScreen(
                        targetDisplayId = targetDisplayId,
                        bluetooth = bluetoothHub,
                        commands = controllerCommands,
                        onLaunchApp = { app -> launchAppOnTv(app) },
                        onSetKeepScreenOn = { keepOn ->
                            if (keepOn) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        },
                        onThemeChanged = { cursorAccent = it.accent },
                        onExitHub = { finish() }
                    )
                    if (cursorVisible.value) TouchpadCursor(cursorPos.value, cursorAccent)
                }
            }
        }
        window.decorView.setOnCapturedPointerListener { _, e -> handleCapturedPointer(e) }
    }

    /* ---------------- DualSense touchpad cursor ----------------
       While the hub has focus it captures the controller touchpad (and any mouse) and draws its own
       cursor on the TV / glasses. Swipe to move; tap or click the pad to select what's under it. */

    private val cursorPos = mutableStateOf(Offset(-1f, -1f))
    private val cursorVisible = mutableStateOf(false)
    private val cursorHandler = Handler(Looper.getMainLooper())
    private val hideCursor = Runnable { cursorVisible.value = false }
    private var padLastX = 0f
    private var padLastY = 0f
    private var padDownTime = 0L
    private var padTravel = 0f
    private var padPressed = false

    private fun moveCursor(dx: Float, dy: Float) {
        val root = window.decorView
        if (root.width == 0) return
        var p = cursorPos.value
        if (p.x < 0) p = Offset(root.width / 2f, root.height / 2f)
        cursorPos.value = Offset((p.x + dx).coerceIn(0f, root.width - 1f), (p.y + dy).coerceIn(0f, root.height - 1f))
        showCursor()
    }

    private fun showCursor() {
        cursorVisible.value = true
        cursorHandler.removeCallbacks(hideCursor)
        cursorHandler.postDelayed(hideCursor, 3500L)
    }

    /** Taps the hub at the cursor, so whatever tile or button is under it reacts like a touch. */
    private fun clickAtCursor() {
        val p = cursorPos.value
        if (p.x < 0) return
        showCursor()
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, p.x, p.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, p.x, p.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        window.decorView.dispatchTouchEvent(down)
        window.decorView.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
    }

    private fun handleCapturedPointer(e: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        // Mouse: relative movement
        if (e.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) {
            if (e.actionMasked == MotionEvent.ACTION_MOVE) moveCursor(e.x, e.y)
            if (e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && e.actionButton == MotionEvent.BUTTON_PRIMARY) clickAtCursor()
            return true
        }
        if (!e.isFromSource(InputDevice.SOURCE_TOUCHPAD)) return false
        val range = e.device?.getMotionRange(MotionEvent.AXIS_X, e.source)?.range?.takeIf { it > 0 } ?: 1920f
        // Crossing the whole pad moves the cursor about 1.3 screen widths
        val scale = window.decorView.width / range * 1.3f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                padLastX = e.x; padLastY = e.y
                padDownTime = e.eventTime; padTravel = 0f; padPressed = false
                showCursor()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Re-anchor so adding / lifting a finger doesn't make the cursor jump
                padLastX = e.getX(0); padLastY = e.getY(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.getX(0) - padLastX) * scale
                val dy = (e.getY(0) - padLastY) * scale
                padLastX = e.getX(0); padLastY = e.getY(0)
                padTravel += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                moveCursor(dx, dy)
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                // Pressing the pad down clicks
                if (e.actionButton == MotionEvent.BUTTON_PRIMARY) { padPressed = true; clickAtCursor() }
            }
            MotionEvent.ACTION_UP -> {
                // A quick light tap also clicks (unless the pad was pressed down during this touch)
                if (!padPressed && e.eventTime - padDownTime < 220 && padTravel < 14 * density) clickAtCursor()
            }
        }
        return true
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
            // Touchpad cursor: the hub owns the controller touchpad while it's on screen
            window.decorView.post { runCatching { window.decorView.requestPointerCapture() } }
        } else {
            cursorVisible.value = false
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
        val ext = ExternalDisplayHelper.findExternalDisplay(this) ?: return
        // TV and Glasses each get their own resolution / refresh rate
        val desiredModeId = ExternalDisplayHelper.resolvePreferredMode(this, ext)?.modeId ?: 0
        if (desiredModeId != 0 && window.attributes.preferredDisplayModeId != desiredModeId) {
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
        if (bluetoothHub.visible) bluetoothHub.close()
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

        if (app.packageName == AppRepository.PLAY_PC_ID) {
            ArtemisBoostManager.onAppLaunched(this, AppRepository.ARTEMIS_LEGION_PACKAGE)
            ExternalDisplayHelper.launchAppOnDisplay(this, AppRepository.playPcIntent(), externalId)
            return
        }

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

        /** Lets the tablet touchpad (glasses mode) drive the hub like a controller. */
        fun sendRemoteCommand(cmd: ControllerCommand): Boolean {
            val instance = activeInstance ?: return false
            // Only while the hub is on screen, so touchpad swipes never launch apps over a movie/game
            if (!isInstanceResumed) return false
            return instance.controllerCommands.tryEmit(cmd)
        }

        fun toggleQuickMenuFromService(): Boolean {
            val instance = activeInstance ?: return false
            if (!isInstanceResumed) return false
            instance.toggleMenu()
            return true
        }
    }
}

/** The hub's touchpad cursor: a crisp dot with a theme-accent ring, drawn above everything. */
@Composable
private fun TouchpadCursor(pos: Offset, accent: Color = Color(0xFF00E5FF)) {
    Box(
        modifier = Modifier
            .offset { val r = 14.dp.roundToPx(); IntOffset(pos.x.roundToInt() - r, pos.y.roundToInt() - r) }
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.94f))
            .border(BorderStroke(2.5.dp, accent), CircleShape)
    )
}

@Composable
private fun SwitchTvHubScreen(
    targetDisplayId: Int,
    bluetooth: BluetoothHub,
    commands: MutableSharedFlow<ControllerCommand>,
    onLaunchApp: (AppEntry) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onThemeChanged: (HubTheme) -> Unit,
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
    var displayProfile by remember { mutableStateOf(prefs.activeProfile) }
    val isGlasses = displayProfile == DisplayProfile.GLASSES
    var activeTheme by remember { mutableStateOf(HubTheme.fromId(prefs.getThemeId(displayProfile))) }

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
        // "Play PC" leads the row whenever Artemis Legion is installed
        pinnedApps = listOfNotNull(AppRepository.playPcTile(context)) +
            AppRepository.getOrSeedPinnedApps(context, loaded, prefs)
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

    LaunchedEffect(activeTheme) {
        onThemeChanged(activeTheme)
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
                bluetooth.visible -> {
                    when (cmd) {
                        ControllerCommand.B_BACK, ControllerCommand.START_MENU -> SwitchAudioFeedback.playBack(context)
                        ControllerCommand.A_CONFIRM, ControllerCommand.X_ACTION, ControllerCommand.Y_DRAWER -> SwitchAudioFeedback.playSelect(context)
                        else -> SwitchAudioFeedback.playMove(context)
                    }
                    bluetooth.handle(cmd)
                }

                showQuickMenu -> {
                    val menuCount = 12
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
                                    bluetooth.open()
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
                                        if (ShizukuHelper.setBypassCharging(context, next)) prefs.bypassChargingEnabled = next
                                    } else {
                                        showQuickMenu = false
                                        DockHealthMonitor.openBypassChargingSettings(context)
                                    }
                                }
                                8 -> {
                                    val nextTheme = activeTheme.next()
                                    activeTheme = nextTheme
                                    prefs.setThemeId(displayProfile, nextTheme.id)
                                }
                                9 -> triggerSleepMode()
                                10 -> cycleGlassesScreenSize()
                                11 -> showQuickMenu = false
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
                            if (currentFocusedPkg != null && !AppRepository.isVirtualTile(currentFocusedPkg)) {
                                prefs.togglePinnedPackage(currentFocusedPkg)
                                SwitchAudioFeedback.playSelect(context)
                                reloadApps()
                            }
                        }
                        ControllerCommand.L1_PREV -> {
                            if (currentFocusedPkg != null && !AppRepository.isVirtualTile(currentFocusedPkg) &&
                                prefs.movePinnedPackage(currentFocusedPkg, -1)) {
                                selectedTileIndex = (currentSafeTileIdx - 1).coerceAtLeast(0)
                                SwitchAudioFeedback.playMove(context)
                                reloadApps()
                            }
                        }
                        ControllerCommand.R1_NEXT -> {
                            if (currentFocusedPkg != null && !AppRepository.isVirtualTile(currentFocusedPkg) &&
                                prefs.movePinnedPackage(currentFocusedPkg, +1)) {
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
            val liveProfile = ext?.profile ?: DisplayProfile.TV
            if (liveProfile != displayProfile) {
                // Switched between TV and glasses: swap to that menu's pins and theme
                displayProfile = liveProfile
                selectedTileIndex = 0
                reloadApps()
            }
            activeTheme = HubTheme.fromId(prefs.getThemeId(liveProfile))

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
        ConsoleAmbientBackdrop(
            theme = activeTheme,
            isGlasses = isGlasses,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle ambient banner crossfade behind the hero canvas
        Crossfade(
            targetState = safeTileIndex,
            animationSpec = tween(320),
            label = "heroBackdrop"
        ) { index ->
            val app = pinnedApps.getOrNull(index)
            if (app?.banner != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = app.banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = if (isGlasses) 0.22f else 0.34f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        activeTheme.bgTop.copy(alpha = 0.96f),
                                        activeTheme.bgMid.copy(alpha = 0.78f),
                                        activeTheme.bgTop.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        activeTheme.bgTop.copy(alpha = 0.60f),
                                        Color.Transparent,
                                        activeTheme.bgBottom.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        val configuration = LocalConfiguration.current
        // Glasses: the edge margin, or the smaller screen size if that leaves more room around the hub
        val glassesMarginFraction = if (isGlasses) {
            maxOf(prefs.glassesEdgeMarginPct / 100f, (100 - screenSizeTick.let { prefs.glassesScreenSizePct }) / 200f)
        } else 0f
        val outerPadH = (if (isGlasses) 44.dp else 32.dp) + (configuration.screenWidthDp * glassesMarginFraction).dp
        val outerPadV = (if (isGlasses) 28.dp else 20.dp) + (configuration.screenHeightDp * glassesMarginFraction).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerPadH, vertical = outerPadV),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Console Telemetry Pill Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = activeTheme.accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isGlasses) Icons.Rounded.ViewInAr else Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = activeTheme.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isGlasses) "LEGION XR" else "LEGION DOCK",
                                color = activeTheme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.4.sp
                            )
                        }
                    }

                    Surface(
                        color = activeTheme.surfaceGlass,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.borderSubtle)
                    ) {
                        Text(
                            text = displaySummary,
                            color = activeTheme.textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    val lowPad = health.controllers.firstOrNull { (it.batteryPercent ?: 100) <= 20 }
                    if (lowPad != null) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Gamepad,
                                    contentDescription = "Low Controller Battery",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Pad Low (${lowPad.batteryPercent}%)",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isGlasses && prefs.glassesBatteryWarning && !health.isCharging && health.batteryPercent <= 20) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = "Tablet ${health.batteryPercent}% • Glasses powered by tablet",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (health.isThermalWarning && prefs.thermalWarningEnabled) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Thermostat,
                                    contentDescription = "High Temp",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", health.temperatureC)}°C • Bypass Recommended",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Right Cluster: Unified Glass HUD Pill
                Surface(
                    color = activeTheme.surfaceGlass,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, activeTheme.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(padTint)
                                )
                                Icon(
                                    Icons.Rounded.Gamepad,
                                    contentDescription = "Controller Connected",
                                    tint = padTint,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = ctrlLabel,
                                    color = padTint,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(activeTheme.borderSubtle)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (networkType == "Ethernet") Icons.Rounded.Lan else Icons.Rounded.Wifi,
                                contentDescription = networkType,
                                tint = if (networkType == "Offline") Color(0xFFF87171) else activeTheme.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = networkType,
                                color = if (networkType == "Offline") Color(0xFFF87171) else activeTheme.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(activeTheme.borderSubtle)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (health.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                                contentDescription = "Battery",
                                tint = if (health.isCharging) Color(0xFF4ADE80) else activeTheme.textPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "${health.batteryPercent}% · ${String.format(Locale.US, "%.0f", health.temperatureC)}°C",
                                color = activeTheme.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(activeTheme.borderSubtle)
                        )

                        Text(
                            text = currentTime,
                            color = activeTheme.textPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ORBIT HOME: visual-first command deck with controller-owned selection.
            // No extra focus layer: existing controller indices still drive the whole screen.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp, bottom = 7.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val heroApp = pinnedApps.getOrNull(safeTileIndex)
                val isPc = focusedPackage == AppRepository.PLAY_PC_ID
                val isStream = isPc || (focusedPackage != null && ArtemisBoostManager.isStreamingPackage(focusedPackage))
                val eyebrowText = when {
                    isPc -> "DIRECT LINK  /  ARTEMIS LEGION"
                    isStream -> "STREAMING  /  LOW LATENCY"
                    heroApp != null -> "LIBRARY  /  FAVORITE ${safeTileIndex + 1}"
                    safeTileIndex == pinnedApps.size -> "EXPLORE  /  INSTALLED APPS"
                    else -> "SYSTEM  /  CONTROL CENTER"
                }
                val subtitleText = when {
                    isPc -> "Your PC is one press away. Pick up exactly where you left off."
                    isStream -> "Launch your stream on the big screen with optimized connectivity."
                    heroApp != null -> "Your next session starts here. Press A to launch on the TV."
                    safeTileIndex == pinnedApps.size -> "Browse ${allApps.size} installed apps and choose what stays in your dock."
                    else -> "Audio, controller pairing, power and display options."
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title and action sit directly on the ambient canvas, not inside a card.
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(if (configuration.screenHeightDp < 550) 7.dp else 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(activeTheme.accent))
                            Text(
                                text = eyebrowText,
                                color = activeTheme.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                        Crossfade(targetState = focusedTitle, animationSpec = tween(250), label = "orbitHeroTitle") { title ->
                            Text(
                                text = title,
                                color = activeTheme.textPrimary,
                                fontSize = if (configuration.screenWidthDp < 850) 37.sp else 54.sp,
                                lineHeight = if (configuration.screenWidthDp < 850) 42.sp else 57.sp,
                                letterSpacing = (-1.8).sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = subtitleText,
                            color = activeTheme.textSecondary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = activeTheme.accent,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable {
                                    val app = pinnedApps.getOrNull(safeTileIndex)
                                    if (app != null) onLaunchApp(app)
                                    else if (safeTileIndex == pinnedApps.size) {
                                        selectedDrawerIndex = 0
                                        showAllAppsDrawer = true
                                    } else {
                                        selectedMenuIndex = 0
                                        showQuickMenu = true
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 19.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Icon(
                                        if (heroApp == null) Icons.Rounded.Apps else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = activeTheme.bgTop,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        if (heroApp != null) "PLAY NOW" else "OPEN",
                                        color = activeTheme.bgTop,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                            Text(
                                text = if (isStream) (if (prefs.artemisWifiBoostEnabled) "● WI-FI BOOST" else "● STANDARD NETWORK") else "● EXTERNAL DISPLAY",
                                color = activeTheme.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    // Featured visual uses real artwork when the app supplies it; otherwise
                    // an original theme-tinted orbital composition, not an empty icon box.
                    Box(
                        modifier = Modifier.weight(0.95f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        OrbitArtwork(
                            theme = activeTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                        Crossfade(
                            targetState = safeTileIndex,
                            animationSpec = tween(320),
                            label = "orbitShowcase"
                        ) { index ->
                            val app = pinnedApps.getOrNull(index)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (app?.banner != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.84f)
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(21.dp))
                                            .border(2.dp, activeTheme.accent.copy(alpha = 0.65f), RoundedCornerShape(21.dp))
                                    ) {
                                        Image(
                                            bitmap = app.banner,
                                            contentDescription = app.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, activeTheme.bgTop.copy(alpha = 0.45f)))))
                                    }
                                } else if (app != null) {
                                    Box(
                                        Modifier.size(132.dp).clip(RoundedCornerShape(33.dp))
                                            .background(activeTheme.surfaceGlass)
                                            .border(2.dp, activeTheme.accent.copy(alpha = 0.7f), RoundedCornerShape(33.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(90.dp))
                                    }
                                } else {
                                    Icon(
                                        if (index == pinnedApps.size) Icons.Rounded.Apps else Icons.Rounded.Settings,
                                        contentDescription = focusedTitle,
                                        tint = activeTheme.accent,
                                        modifier = Modifier.size(90.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // Floating carousel – the only interactive selection rail on the TV home.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(activeTheme.accent))
                            Text(
                                "YOUR COLLECTION",
                                color = activeTheme.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.8.sp
                            )
                        }
                        Text(
                            "${pinnedApps.size} PINNED  ·  L1/R1 REORDER",
                            color = activeTheme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = activeTheme.surfaceGlass.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.19f))
                    ) {
                        LazyRow(
                            state = rowListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(pinnedApps, key = { _, app -> app.packageName }) { index, app ->
                                SwitchAppTile(
                                    app = app,
                                    isSelected = safeTileIndex == index,
                                    theme = activeTheme,
                                    onClick = {
                                        selectedTileIndex = index
                                        onLaunchApp(app)
                                    }
                                )
                            }
                            item(key = "add_more_apps_tile") {
                                val idx = pinnedApps.size
                                ActionTile(
                                    title = "All Apps",
                                    icon = Icons.Rounded.Add,
                                    theme = activeTheme,
                                    isSelected = safeTileIndex == idx,
                                    onClick = {
                                        selectedTileIndex = idx
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
                                    theme = activeTheme,
                                    isSelected = safeTileIndex == idx,
                                    onClick = {
                                        selectedTileIndex = idx
                                        selectedMenuIndex = 0
                                        showQuickMenu = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Console Controller Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ButtonPrompt("A", "Launch", activeTheme)
                    ButtonPrompt("Y", "Library", activeTheme)
                    ButtonPrompt("X", "Pin / Unpin", activeTheme)
                    ButtonPrompt("L1/R1", "Reorder", activeTheme)
                    ButtonPrompt("Start", "Quick Menu", activeTheme)
                }

                Surface(
                    color = activeTheme.surfaceGlass,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, activeTheme.borderSubtle),
                    modifier = Modifier.clickable { onExitHub() }
                ) {
                    Text(
                        text = "Exit Hub",
                        color = activeTheme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // All Apps & Pin Manager Overlay
        if (showAllAppsDrawer) {
            AllAppsDrawerOverlay(
                allApps = allApps,
                pinnedPackages = pinnedApps.map { it.packageName }.toSet(),
                selectedIndex = selectedDrawerIndex.coerceIn(0, (allApps.size - 1).coerceAtLeast(0)),
                gridState = drawerGridState,
                activeTheme = activeTheme,
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
                    prefs.setThemeId(displayProfile, nextTheme.id)
                },
                onTriggerScreensaver = {
                    showQuickMenu = false
                    isIdleDimmed = true
                },
                onTriggerSleepMode = {
                    triggerSleepMode()
                },
                onOpenBluetooth = {
                    showQuickMenu = false
                    bluetooth.open()
                },
                onClose = {
                    SwitchAudioFeedback.playBack(context)
                    showQuickMenu = false
                }
            )
        }

        // In-hub Bluetooth devices screen
        if (bluetooth.visible) {
            BluetoothPanel(bluetooth, activeTheme)
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
                    .background(Color.Black.copy(alpha = 0.92f))
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
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$currentDate • ${health.batteryPercent}% (${String.format(Locale.US, "%.1f", health.temperatureC)}°C)",
                        color = activeTheme.textSecondary,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Press any controller button to wake",
                        color = activeTheme.accent.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/** Theme-aware cinematic horizon, kept completely black for see-through XR mode. */
@Composable
private fun ConsoleAmbientBackdrop(
    theme: HubTheme,
    isGlasses: Boolean,
    modifier: Modifier = Modifier
) {
    if (isGlasses) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val glowCenter = Offset(w * 0.76f, h * 0.43f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.accent.copy(alpha = 0.22f), theme.accent.copy(alpha = 0.07f), Color.Transparent),
                center = glowCenter,
                radius = w * 0.43f
            ),
            center = glowCenter,
            radius = w * 0.43f
        )
        // A wide vanishing-point horizon creates cinematic depth on a TV.
        val horizon = h * 0.80f
        for (i in 0..14) {
            val fraction = i / 14f
            val endX = w * fraction
            drawLine(
                color = theme.accent.copy(alpha = 0.035f + fraction * 0.02f),
                start = Offset(w * 0.58f, horizon),
                end = Offset(endX, h),
                strokeWidth = 1.dp.toPx()
            )
        }
        for (i in 1..7) {
            val t = i / 7f
            val y = horizon + (h - horizon) * t * t
            drawLine(
                color = theme.accent.copy(alpha = 0.04f + t * 0.045f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        for (i in 0..55) {
            val x = w * (((i * 71 + 13) % 101) / 101f)
            val y = h * (((i * 41 + 19) % 97) / 97f) * 0.78f
            drawCircle(
                color = Color.White.copy(alpha = if (i % 7 == 0) 0.42f else 0.10f),
                radius = if (i % 7 == 0) 1.3.dp.toPx() else 0.65.dp.toPx(),
                center = Offset(x, y)
            )
        }
        drawLine(
            color = theme.accent.copy(alpha = 0.20f),
            start = Offset(0f, horizon),
            end = Offset(w, horizon),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Original abstract orbital artwork. Lightweight Canvas, no network images or bitmap resources. */
@Composable
private fun OrbitArtwork(theme: HubTheme, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.53f, size.height * 0.50f)
        val radius = minOf(size.width, size.height) * 0.38f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.accent.copy(alpha = 0.30f), theme.accent.copy(alpha = 0.05f), Color.Transparent),
                center = center,
                radius = radius * 1.30f
            ),
            radius = radius * 1.30f,
            center = center
        )
        for (ring in 0..3) {
            drawCircle(
                color = theme.accent.copy(alpha = 0.32f - ring * 0.065f),
                radius = radius * (0.57f + ring * 0.22f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (ring == 1) 2.dp else 1.dp).toPx())
            )
        }
        drawLine(
            color = theme.accent.copy(alpha = 0.40f),
            start = Offset(center.x - radius * 1.2f, center.y),
            end = Offset(center.x + radius * 1.2f, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = theme.accent.copy(alpha = 0.25f),
            start = Offset(center.x, center.y - radius * 1.15f),
            end = Offset(center.x, center.y + radius * 1.15f),
            strokeWidth = 1.dp.toPx()
        )
        for (i in 1..28) {
            val x = size.width * (((i * 37) % 97) / 97f)
            val y = size.height * (((i * 61) % 89) / 89f)
            drawCircle(
                color = Color.White.copy(alpha = if (i % 5 == 0) 0.65f else 0.20f),
                radius = if (i % 5 == 0) 1.6.dp.toPx() else 0.8.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun SwitchAppTile(
    app: AppEntry,
    isSelected: Boolean,
    theme: HubTheme,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.095f else 0.96f,
        animationSpec = tween(210),
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
                .width(148.dp)
                .height(82.dp)
                .shadow(if (isSelected) 17.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = theme.accent, spotColor = theme.accent)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = if (isSelected) {
                            listOf(theme.focusGlow.copy(alpha = 0.30f), theme.surfaceElevated)
                        } else {
                            listOf(theme.surfaceElevated, theme.bgBottom)
                        }
                    )
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) theme.accent else theme.borderSubtle,
                    shape = RoundedCornerShape(18.dp)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f))
                            )
                        )
                )
            } else {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .width(40.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(theme.accent)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            color = if (isSelected) theme.textPrimary else theme.textSecondary,
            fontSize = if (isSelected) 13.sp else 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(144.dp)
        )
    }
}

@Composable
private fun ActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: HubTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.095f else 0.96f,
        animationSpec = tween(210),
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
                .width(148.dp)
                .height(82.dp)
                .shadow(if (isSelected) 17.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = theme.accent, spotColor = theme.accent)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isSelected) theme.focusGlow.copy(alpha = 0.25f) else theme.surfaceElevated
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) theme.accent else theme.borderSubtle,
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) theme.accent else theme.textSecondary,
                modifier = Modifier.size(30.dp)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .width(34.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(theme.accent)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = if (isSelected) theme.textPrimary else theme.textSecondary,
            fontSize = if (isSelected) 13.sp else 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

private data class QuickMenuRowSpec(
    val index: Int,
    val title: String,
    val badge: String,
    val badgePositive: Boolean = false,
    val onClick: () -> Unit
)

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
    onOpenBluetooth: () -> Unit,
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
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, stateTick) {
        volumePct = readVolumePct(audioManager)
        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        uiSounds = prefs.uiSoundsEnabled
        idleDimmer = prefs.idleDimmerEnabled
        bypassEnabled = prefs.bypassChargingEnabled
        if (selectedIndex >= 3) {
            runCatching { listState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0)) }
        }
    }

    val rows = listOf(
        QuickMenuRowSpec(
            index = 3,
            title = "Bluetooth Devices",
            badge = "OPEN",
            onClick = {
                onSelectIndex(3)
                onOpenBluetooth()
            }
        ),
        QuickMenuRowSpec(
            index = 4,
            title = "UI Navigation Sounds",
            badge = if (uiSounds) "ON" else "OFF",
            badgePositive = uiSounds,
            onClick = {
                onSelectIndex(4)
                uiSounds = !uiSounds
                prefs.uiSoundsEnabled = uiSounds
            }
        ),
        QuickMenuRowSpec(
            index = 5,
            title = "5-Min Idle Screensaver",
            badge = if (idleDimmer) "ON" else "OFF",
            badgePositive = idleDimmer,
            onClick = {
                onSelectIndex(5)
                idleDimmer = !idleDimmer
                prefs.idleDimmerEnabled = idleDimmer
            }
        ),
        QuickMenuRowSpec(
            index = 6,
            title = "Screensaver",
            badge = "START NOW",
            onClick = {
                onSelectIndex(6)
                onTriggerScreensaver()
            }
        ),
        QuickMenuRowSpec(
            index = 7,
            title = if (shizukuReady) "Bypass Charging" else "Bypass Charging / Game Assistant",
            badge = if (shizukuReady) (if (bypassEnabled) "ON" else "OFF") else "SETTINGS",
            badgePositive = shizukuReady && bypassEnabled,
            onClick = {
                onSelectIndex(7)
                if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                    val next = !bypassEnabled
                    if (ShizukuHelper.setBypassCharging(context, next)) {
                        bypassEnabled = next
                        prefs.bypassChargingEnabled = next
                    }
                } else {
                    onClose()
                    DockHealthMonitor.openBypassChargingSettings(context)
                }
            }
        ),
        QuickMenuRowSpec(
            index = 8,
            title = "Display Theme",
            badge = activeTheme.displayName,
            badgePositive = true,
            onClick = {
                onSelectIndex(8)
                onCycleTheme()
            }
        ),
        QuickMenuRowSpec(
            index = 9,
            title = "Sleep Mode (Tablet & TV)",
            badge = "SLEEP",
            onClick = {
                onSelectIndex(9)
                onTriggerSleepMode()
            }
        ),
        QuickMenuRowSpec(
            index = 10,
            title = "Glasses Screen Size",
            badge = "${screenSizeTick.let { prefs.glassesScreenSizePct }}%",
            badgePositive = true,
            onClick = {
                onSelectIndex(10)
                cycleGlassesScreenSize()
            }
        ),
        QuickMenuRowSpec(
            index = 11,
            title = "Close Menu",
            badge = "B",
            onClick = {
                onSelectIndex(11)
                onClose()
            }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC06080D))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = activeTheme.surfaceModal,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.45f)),
            modifier = Modifier
                .width(560.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "QUICK MENU",
                            color = activeTheme.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = "System & Display Controls",
                            color = activeTheme.textMuted,
                            fontSize = 12.sp
                        )
                    }
                    val chargeLabel = if (health.isCharging) "Charging ${health.wattsEstimate}W" else "Discharging"
                    Surface(
                        color = activeTheme.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (health.isThermalWarning) Color(0xFFF59E0B).copy(alpha = 0.6f) else activeTheme.borderSubtle
                        )
                    ) {
                        Text(
                            text = "${health.batteryPercent}% • ${String.format(Locale.US, "%.1f", health.temperatureC)}°C • $chargeLabel",
                            color = if (health.isThermalWarning) Color(0xFFFBBF24) else activeTheme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Media Volume section with live progress bar
                Surface(
                    color = activeTheme.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, activeTheme.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media Volume",
                                color = activeTheme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isMuted) "MUTED ($volumePct%)" else "$volumePct%",
                                color = if (isMuted) Color(0xFFF87171) else activeTheme.accent,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(activeTheme.borderSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = (volumePct / 100f).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isMuted) activeTheme.textMuted else activeTheme.accent)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickMenuButton(
                                label = "Vol -",
                                badge = null,
                                isSelected = (selectedIndex == 0),
                                theme = activeTheme,
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
                                badge = null,
                                isSelected = (selectedIndex == 1),
                                theme = activeTheme,
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
                                badge = null,
                                isSelected = (selectedIndex == 2),
                                theme = activeTheme,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSelectIndex(2)
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                                    volumePct = readVolumePct(audioManager)
                                    isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 370.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    itemsIndexed(rows, key = { _, item -> item.index }) { _, spec ->
                        QuickMenuButton(
                            label = spec.title,
                            badge = spec.badge,
                            badgePositive = spec.badgePositive,
                            isSelected = (selectedIndex == spec.index),
                            theme = activeTheme,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = spec.onClick
                        )
                    }
                }

                Text(
                    text = "D-Pad / L1-R1 Navigate  •  A Select  •  B Close",
                    color = activeTheme.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Glasses screen size: 100 → 90 → 80 → 70 → 60 → 100. Applies to apps opened from now on. */
private var screenSizeTick by mutableIntStateOf(0)
private fun cycleGlassesScreenSize() {
    val context = TvHubActivity.activeInstance ?: return
    val prefs = DockPreferences(context)
    val sizes = DockPreferences.GLASSES_SCREEN_SIZES
    val next = sizes[(sizes.indexOf(prefs.glassesScreenSizePct).coerceAtLeast(0) + 1) % sizes.size]
    prefs.glassesScreenSizePct = next
    screenSizeTick++
}

@Composable
private fun QuickMenuButton(
    label: String,
    badge: String?,
    isSelected: Boolean,
    theme: HubTheme,
    modifier: Modifier = Modifier,
    badgePositive: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = if (isSelected) theme.focusGlow.copy(alpha = 0.30f) else theme.surfaceElevated,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) theme.accent else theme.borderSubtle
        ),
        modifier = modifier.clickable {
            SwitchAudioFeedback.playSelect(context)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (badge == null) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = if (isSelected) theme.textPrimary else theme.textPrimary.copy(alpha = 0.90f),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (badge != null) Modifier.weight(1f).padding(end = 10.dp) else Modifier
            )
            if (badge != null) {
                Surface(
                    color = when {
                        badgePositive -> theme.accent.copy(alpha = 0.16f)
                        isSelected -> Color.White.copy(alpha = 0.12f)
                        else -> Color.White.copy(alpha = 0.06f)
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (badgePositive) theme.accent.copy(alpha = 0.50f) else theme.borderSubtle
                    )
                ) {
                    Text(
                        text = badge,
                        color = if (badgePositive) theme.accent else theme.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
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
    activeTheme: HubTheme,
    onLaunchApp: (Int, AppEntry) -> Unit,
    onTogglePin: (Int, AppEntry) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07090E))
            .padding(34.dp)
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
                    Surface(
                        color = activeTheme.accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = activeTheme.accent,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Text(
                        text = "Application Library",
                        color = activeTheme.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = activeTheme.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, activeTheme.borderSubtle)
                    ) {
                        Text(
                            text = "${allApps.size} Apps",
                            color = activeTheme.textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ButtonPrompt("A", "Launch", activeTheme)
                    ButtonPrompt("X", "Pin / Unpin", activeTheme)
                    Surface(
                        color = activeTheme.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.borderSubtle),
                        modifier = Modifier.clickable { onClose() }
                    ) {
                        Text(
                            text = "Close (B)",
                            color = activeTheme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (allApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = activeTheme.surfaceModal,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, activeTheme.borderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = activeTheme.accent,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No launchable apps found",
                                color = activeTheme.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Installed apps will appear here once loaded.",
                                color = activeTheme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(allApps, key = { _, app -> app.packageName }) { index, app ->
                        val isPinned = pinnedPackages.contains(app.packageName)
                        val isSelected = (index == selectedIndex)

                        Surface(
                            color = if (isSelected) activeTheme.focusGlow.copy(alpha = 0.28f) else activeTheme.surfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) activeTheme.accent else activeTheme.borderSubtle
                            ),
                            modifier = Modifier
                                .scale(if (isSelected) 1.03f else 1.0f)
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
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .align(Alignment.Center)
                                    )
                                    if (isPinned) {
                                        Surface(
                                            color = Color(0xFF4ADE80).copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.55f)),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .clickable { onTogglePin(index, app) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = "Pinned",
                                                    tint = Color(0xFF4ADE80),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "PIN",
                                                    color = Color(0xFF4ADE80),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = app.label,
                                    color = if (isSelected) activeTheme.accent else activeTheme.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
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
}

@Composable
private fun ButtonPrompt(button: String, label: String, theme: HubTheme) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Surface(
            color = theme.surfaceElevated,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, theme.borderSubtle),
            modifier = Modifier.height(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = button,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = label,
            color = theme.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
