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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        // Original bundled panoramic artwork: matches the approved reference and
        // doesn't require an internet connection. XR stays truly black.
        if (!isGlasses) {
            Image(
                painter = painterResource(R.drawable.orbit_cosmos_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xD9070619),
                            Color(0x82070518),
                            Color(0x1208051D),
                            Color(0x08080613)
                        )
                    )
                )
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x53040616),Color.Transparent,Color(0x8B060819))
                    )
                )
            )
        }

        val configuration = LocalConfiguration.current
        // Glasses: the edge margin, or the smaller screen size if that leaves more room around the hub
        val glassesMarginFraction = if (isGlasses) {
            maxOf(prefs.glassesEdgeMarginPct / 100f, (100 - screenSizeTick.let { prefs.glassesScreenSizePct }) / 200f)
        } else 0f
        val outerPadH = (if (isGlasses) 44.dp else 32.dp) + (configuration.screenWidthDp * glassesMarginFraction).dp
        val outerPadV = (if (isGlasses) 28.dp else 16.dp) + (configuration.screenHeightDp * glassesMarginFraction).dp
        // Fit the featured artwork and up to six launch tiles on the actual display,
        // rather than assuming TV density / usable width is identical to a tablet.
        val compactHome = !isGlasses && configuration.screenHeightDp < 610
        val visibleDockTiles = (pinnedApps.size + 2).coerceIn(2, 6)
        val availableDockDp = configuration.screenWidthDp.toFloat() - outerPadH.value * 2f
        val dockCardWidth = if (isGlasses) 158.dp else
            ((availableDockDp - 44f - (visibleDockTiles - 1) * 12f) / visibleDockTiles)
                .coerceIn(90f, 174f).dp
        val dockCardHeight = if (compactHome) 92.dp else 114.dp

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
                                text = if (isGlasses) "LEGION XR" else "LEGION SWITCH DOCK",
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
                            text = if (isGlasses) displaySummary else "ORBIT HOME  ·  $displaySummary",
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
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = if (compactHome) 5.dp else 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val heroApp = pinnedApps.getOrNull(safeTileIndex)
                val isPc = focusedPackage == AppRepository.PLAY_PC_ID
                val isArtemis = focusedPackage == AppRepository.ARTEMIS_LEGION_PACKAGE ||
                    (heroApp?.label?.contains("Artemis", ignoreCase = true) == true)
                val isStream = isPc || (focusedPackage != null && ArtemisBoostManager.isStreamingPackage(focusedPackage))
                val eyebrowText = when {
                    isPc -> "DIRECT LINK  /  ARTEMIS LEGION"
                    isArtemis -> "STREAMING MODE  /  ARTEMIS LEGION"
                    isStream -> "STREAMING  /  LOW LATENCY"
                    heroApp != null -> "LIBRARY  /  FAVORITE ${safeTileIndex + 1}"
                    safeTileIndex == pinnedApps.size -> "EXPLORE  /  INSTALLED APPS"
                    else -> "SYSTEM  /  CONTROL CENTER"
                }
                val subtitleText = when {
                    isPc -> "Your PC is one press away. Pick up exactly where you left off."
                    isArtemis -> "Stream Artemis Legion to your TV. Your games, worlds and content on the big screen."
                    isStream -> "Launch your stream on the big screen with optimized connectivity."
                    heroApp != null -> "Your next session starts here. Press A to launch on the TV."
                    safeTileIndex == pinnedApps.size -> "Browse ${allApps.size} installed apps and choose what stays in your dock."
                    else -> "Audio, controller pairing, power and display options."
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp, vertical = if (compactHome) 4.dp else 10.dp),
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
                        Crossfade(targetState = if (isArtemis) "Artemis Legion" else focusedTitle, animationSpec = tween(250), label = "orbitHeroTitle") { title ->
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
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(50),
                                        ambientColor = Color(0xFFB645FF),
                                        spotColor = Color(0xFF10DFFF)
                                    )
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(
                                        listOf(Color(0xFFB742F4),Color(0xFF7440D4),Color(0xFF00CFFF))
                                    ))
                                    .border(2.dp, Color(0xFFF5E5FF), RoundedCornerShape(50))
                                    .clickable {
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
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(28.dp)
                                            .clip(CircleShape).background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("A",color = Color(0xFF34205B),fontSize = 16.sp,fontWeight = FontWeight.Black)
                                    }
                                    Text(
                                        text = if (heroApp != null) "PLAY NOW" else "OPEN",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            Text(
                                text = if (isStream) (if (prefs.artemisWifiBoostEnabled) "● LOW LATENCY ON" else "● STREAM READY") else "● TV READY",
                                color = activeTheme.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    // Giant neon-framed showcase, matching the concept rather than a floating app icon.
                    Box(
                        modifier = Modifier
                            .weight(if (compactHome) 1.20f else 1.08f)
                            .fillMaxHeight(if (compactHome) 0.97f else 0.93f)
                            .shadow(
                                elevation = 18.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0xFFAC39FF),
                                spotColor = Color(0xFF05CBFF)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF080D2A))
                            .border(
                                width = 2.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFFE879FF),Color(0xFF9F59F3),Color(0xFF10E2FC))
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Crossfade(
                            targetState = safeTileIndex,
                            animationSpec = tween(340),
                            label = "featuredArtworkFade"
                        ) { index ->
                            val app = pinnedApps.getOrNull(index)
                            val showArtemisPoster = !isGlasses && app?.packageName == AppRepository.ARTEMIS_LEGION_PACKAGE
                            Box(Modifier.fillMaxSize()) {
                                if (showArtemisPoster) {
                                    Image(
                                        painter = painterResource(R.drawable.orbit_artemis_poster),
                                        contentDescription = "Original Artemis Legion showcase illustration",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (app?.banner != null) {
                                    Image(
                                        bitmap = app.banner,
                                        contentDescription = app.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (!isGlasses) {
                                    CosmicCityArtwork(
                                        theme = activeTheme,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(activeTheme.surfaceElevated))
                                }
                                // The Artemis poster has its own title artwork.
                                if (!showArtemisPoster) {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x32080927), Color.Transparent, Color(0xE6080920))
                                        )
                                    )
                                )
                                Row(
                                    modifier = Modifier.align(Alignment.TopStart)
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    if (app != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                                        )
                                    } else {
                                        Icon(
                                            if (index == pinnedApps.size) Icons.Rounded.Apps else Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(
                                        text = "LEGION  /  FEATURED",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.4.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = (app?.label ?: if (index == pinnedApps.size) "YOUR LIBRARY" else "QUICK MENU").uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 26.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (app != null) "READY TO PLAY" else "EXPLORE & CUSTOMIZE",
                                        color = Color(0xFFC8B4EA),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                }
                            }
                        }
                    }
                }
                // Floating carousel – the only interactive selection rail on the TV home.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (compactHome) 5.dp else 8.dp)
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
                        color = Color(0xCC0D1034),
                        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFA750FF), Color(0xFF5A4D96), Color(0xFF13D9FF))))
                    ) {
                        LazyRow(
                            state = rowListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = if (compactHome) 10.dp else 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(pinnedApps, key = { _, app -> app.packageName }) { index, app ->
                                SwitchAppTile(
                                    app = app,
                                    isSelected = safeTileIndex == index,
                                    theme = activeTheme,
                                    onClick = {
                                        selectedTileIndex = index
                                        onLaunchApp(app)
                                    },
                                    tileWidth = dockCardWidth,
                                    tileHeight = dockCardHeight
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
                                    },
                                    tileWidth = dockCardWidth,
                                    tileHeight = dockCardHeight
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
                                    },
                                    tileWidth = dockCardWidth,
                                    tileHeight = dockCardHeight
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
                Row(horizontalArrangement = Arrangement.spacedBy(if (compactHome) 8.dp else 14.dp)) {
                    ButtonPrompt("A", "Launch", activeTheme)
                    ButtonPrompt("B", "Back", activeTheme)
                    ButtonPrompt("Y", "Library", activeTheme)
                    ButtonPrompt("X", "Pin / Unpin", activeTheme)
                    if (!compactHome) ButtonPrompt("L1/R1", "Reorder", activeTheme)
                    ButtonPrompt("Start", if (compactHome) "Menu" else "Quick Menu", activeTheme)
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

/** Built-in original sci-fi vista for apps with no launcher banner.
 * A glowing portal and city skyline make every selection feel like a feature poster. */
@Composable
private fun CosmicCityArtwork(theme: HubTheme, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w=size.width
        val h=size.height
        val neon = Color(0xFFDC53FF)
        val cyan = Color(0xFF10E3FF)
        drawRect(Brush.verticalGradient(
            listOf(Color(0xFF07102D),Color(0xFF212063),Color(0xFF56147B),Color(0xFF0B123A))
        ))
        listOf(
            Triple(Offset(w*.31f,h*.28f),h*.45f,neon),
            Triple(Offset(w*.79f,h*.43f),h*.55f,cyan),
            Triple(Offset(w*.72f,h*.71f),h*.38f,neon)
        ).forEach{(p,r,color)->
            drawCircle(Brush.radialGradient(
                listOf(color.copy(alpha=.40f),color.copy(alpha=.09f),Color.Transparent),p,r
            ),radius=r,center=p)
        }
        val p=Offset(w*.66f,h*.45f)
        val planetR=h*.32f
        drawCircle(Brush.radialGradient(
            listOf(Color(0xFFB15BFF),Color(0xFF413E91),Color(0xFF071E52)),
            center=Offset(p.x-planetR*.42f,p.y-planetR*.46f),
            radius=planetR*1.64f
        ),radius=planetR,center=p)
        drawCircle(
            brush=Brush.sweepGradient(listOf(neon,cyan,neon),center=p),
            radius=planetR*.98f, center=p, style=Stroke(width=3.dp.toPx())
        )
        rotate(-23f,pivot=p){
            drawOval(
                color=neon.copy(alpha=.84f),
                topLeft=Offset(p.x-planetR*1.75f,p.y-planetR*.37f),
                size=Size(planetR*3.5f,planetR*.74f),
                style=Stroke(width=3.dp.toPx())
            )
            drawOval(
                color=cyan.copy(alpha=.56f),
                topLeft=Offset(p.x-planetR*1.65f,p.y-planetR*.31f),
                size=Size(planetR*3.3f,planetR*.62f),
                style=Stroke(width=1.5.dp.toPx())
            )
        }
        for (i in 0..85){
            val x=w*(((i*31+7)%101)/101f)
            val y=h*(((i*57+17)%97)/97f)*.68f
            drawCircle(Color.White.copy(alpha=if(i%9==0).75f else .27f),
                radius=if(i%9==0)1.2.dp.toPx() else .6.dp.toPx(),center=Offset(x,y))
        }
        // Citadel base and reflective causeway.
        val base=Path().apply{
            moveTo(w*.12f,h*.91f);lineTo(w*.31f,h*.78f)
            lineTo(w*.76f,h*.78f);lineTo(w*.95f,h*.91f)
            lineTo(w,h);lineTo(0f,h);close()
        }
        drawPath(base,Brush.verticalGradient(
            listOf(Color(0xFF312361),Color(0xFF050B21)),h*.76f,h
        ))
        drawLine(brush=Brush.horizontalGradient(
            listOf(Color.Transparent,neon,cyan,Color.Transparent)
        ),start=Offset(w*.15f,h*.88f),end=Offset(w*.95f,h*.88f),
        strokeWidth=3.dp.toPx())
        // Spires have varied heights and luminous window seams.
        for(i in 0..19){
            val centerX=w*(.17f+i*.037f)
            val towerHeight=h*(.09f+(((i*13+7)%11)/11f)*.31f)
            val tw=w*(if(i%4==0).022f else .013f)
            val top=h*.85f-towerHeight
            drawRect(
                brush=Brush.verticalGradient(listOf(Color(0xFF171F5F),Color(0xFF06102D))),
                topLeft=Offset(centerX,top),size=Size(tw,towerHeight)
            )
            drawLine(color=if(i%3==0)cyan.copy(alpha=.85f)else neon.copy(alpha=.70f),
                start=Offset(centerX+tw*.5f,top),
                end=Offset(centerX+tw*.5f,h*.85f),
                strokeWidth=1.dp.toPx())
            if(i%4==0){
                drawLine(color=cyan.copy(alpha=.64f),
                    start=Offset(centerX+tw*.5f,top-h*.07f),
                    end=Offset(centerX+tw*.5f,top),
                    strokeWidth=2.dp.toPx())
            }
        }
        // Dominant central tower reaches into the planet's halo.
        val sx=w*.61f
        val spire=Path().apply{
            moveTo(sx-w*.023f,h*.84f)
            lineTo(sx-w*.013f,h*.29f)
            lineTo(sx,h*.18f)
            lineTo(sx+w*.012f,h*.29f)
            lineTo(sx+w*.024f,h*.84f);close()
        }
        drawPath(spire,Brush.horizontalGradient(
            listOf(Color(0xFF0F245A),Color(0xFF592F8D),Color(0xFF071539))
        ))
        drawLine(color=cyan.copy(alpha=.92f),
            start=Offset(sx,h*.21f),end=Offset(sx,h*.84f),
            strokeWidth=2.dp.toPx())
        drawCircle(
            brush=Brush.radialGradient(
                listOf(cyan.copy(alpha=.63f),Color.Transparent),
                center=Offset(sx,h*.78f), radius=h*.23f
            ),
            center=Offset(sx,h*.78f),radius=h*.23f
        )
        // Foreground depth fade keeps title readable.
        drawRect(Brush.verticalGradient(
            listOf(Color.Transparent,Color(0x1B061128),Color(0xA900071B)),
            startY=h*.45f,endY=h
        ))
    }
}

/** Reusable neon tile canvas: varied cyan/violet artwork for apps without banners. */
@Composable
private fun NeonTileBackdrop(
    theme: HubTheme,
    seed: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cyan = Color(0xFF00DFFF)
        val purple = Color(0xFFB739FF)
        val flip = seed % 3 == 0
        drawRect(
            Brush.linearGradient(
                if (flip) listOf(Color(0xFF06315B),Color(0xFF10143D),Color(0xFF43145E))
                else listOf(Color(0xFF23165C),Color(0xFF140C38),Color(0xFF530C7B)),
                start = Offset(0f,0f), end = Offset(w,h)
            )
        )
        val glow = Offset(w*(if(flip).69f else .32f),h*.41f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf((if(flip)cyan else purple).copy(alpha=.64f),Color.Transparent),
                center=glow,radius=w*.54f
            ),
            center=glow,radius=w*.54f
        )
        for(i in -2..7) {
            val x = w*i/6f
            drawLine(
                color = (if (flip)cyan else theme.accent).copy(alpha=.15f),
                start = Offset(x,0f),
                end = Offset(x+w*.39f,h),
                strokeWidth = 1.dp.toPx()
            )
        }
        for(i in 0..12) {
            drawCircle(
                color = Color.White.copy(alpha=if(i%4==0).54f else .16f),
                radius=if(i%4==0)1.1.dp.toPx() else .65.dp.toPx(),
                center=Offset(w*(((i*7+5)%17)/17f),h*(((i*11+3)%19)/19f)*.72f)
            )
        }
    }
}

@Composable
private fun SwitchAppTile(
    app: AppEntry,
    isSelected: Boolean,
    theme: HubTheme,
    onClick: () -> Unit,
    tileWidth: androidx.compose.ui.unit.Dp = 158.dp,
    tileHeight: androidx.compose.ui.unit.Dp = 114.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.055f else .97f,
        animationSpec = tween(200),
        label = "showcaseTileFocus"
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .size(width = tileWidth, height = tileHeight)
            .scale(scale)
            .shadow(
                elevation = if (isSelected) 17.dp else 3.dp,
                shape = shape,
                ambientColor = Color(0xFFB63DFF),
                spotColor = Color(0xFF0FCDFB)
            )
            .clip(shape)
            .background(theme.surfaceElevated)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                brush = if (isSelected) Brush.linearGradient(
                    listOf(Color.White,Color(0xFFCB46FF),Color(0xFF12E0FF))
                ) else Brush.linearGradient(listOf(theme.borderSubtle,theme.borderSubtle)),
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            NeonTileBackdrop(
                theme = theme,
                seed = app.packageName.hashCode(),
                modifier = Modifier.fillMaxSize()
            )
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 14.dp)
                    .size(if (tileWidth < 112.dp) 42.dp else 57.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent,Color(0xF0070822))
                    )
                )
        )
        Text(
            text = app.label,
            color = Color.White,
            fontSize = if (tileWidth < 112.dp) 11.sp else 13.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().padding(horizontal = 7.dp, vertical = 9.dp)
        )
        if (isSelected) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 3.dp)
                    .width(50.dp).height(3.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(
                        listOf(Color(0xFFBB4EFF),Color(0xFF00DFFF))
                    ))
            )
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: HubTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    tileWidth: androidx.compose.ui.unit.Dp = 158.dp,
    tileHeight: androidx.compose.ui.unit.Dp = 114.dp
) {
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.055f else .97f,
        animationSpec = tween(200),
        label = "systemTileFocus"
    )
    Box(
        modifier = Modifier
            .size(width = tileWidth, height = tileHeight)
            .scale(scale)
            .shadow(
                elevation = if (isSelected) 17.dp else 3.dp,
                shape = shape,
                ambientColor = Color(0xFFA83AFF),
                spotColor = Color(0xFF07D5F9)
            )
            .clip(shape)
            .background(theme.surfaceElevated)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                brush = if(isSelected) Brush.linearGradient(
                    listOf(Color.White,Color(0xFFBC48FE),Color(0xFF08DAF9))
                ) else Brush.linearGradient(listOf(theme.borderSubtle,theme.borderSubtle)),
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NeonTileBackdrop(theme = theme, seed = title.hashCode(), modifier = Modifier.fillMaxSize())
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFECE3FF),
            modifier = Modifier.align(Alignment.Center).padding(bottom = 12.dp).size(if (tileWidth < 112.dp) 33.dp else 47.dp)
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xED090A26))))
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = if (tileWidth < 112.dp) 11.sp else 13.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().padding(horizontal = 7.dp, vertical = 9.dp)
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
    val keyColor = when (button) {
        "A" -> Color(0xFF4DF29A)
        "B" -> Color(0xFFF3F1FF)
        "X" -> Color(0xFF22D6FF)
        "Y" -> Color(0xFFFFCE51)
        "L1/R1" -> Color(0xFFCF9DFF)
        else -> Color(0xFFEEE4FF)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Surface(
            color = Color(0xE0101029),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.4.dp, keyColor.copy(alpha = .82f)),
            modifier = Modifier.height(25.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = button,
                    color = keyColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = label,
            color = theme.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
