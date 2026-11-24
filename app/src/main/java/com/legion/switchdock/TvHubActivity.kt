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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
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
import com.legion.switchdock.ui.HubFocusZone
import com.legion.switchdock.ui.NatureGlassBottomHintBar
import com.legion.switchdock.ui.NatureGlassColors
import com.legion.switchdock.ui.NatureGlassFeaturedPanel
import com.legion.switchdock.ui.NatureGlassGridCard
import com.legion.switchdock.ui.NatureGlassSettingsWorkspace
import com.legion.switchdock.ui.NatureGlassTopBar
import com.legion.switchdock.ui.NatureGridItem
import com.legion.switchdock.ui.NatureSettingsState
import com.legion.switchdock.ui.SettingsFocusTarget
import com.legion.switchdock.ui.TopNavTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
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
    var isIdleDimmed by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var displayProfile by remember { mutableStateOf(prefs.activeProfile) }
    val isGlasses = displayProfile == DisplayProfile.GLASSES
    var activeTheme by remember { mutableStateOf(HubTheme.fromId(prefs.getThemeId(displayProfile))) }

    // Deterministic controller selection states (never lost when display focus shifts!)
    var activeTab by remember { mutableStateOf(TopNavTab.HOME) }
    var previousBrowseTab by remember { mutableStateOf(TopNavTab.HOME) }
    var focusZone by remember { mutableStateOf(HubFocusZone.APP_GRID) }
    var focusedNavIndex by remember { mutableIntStateOf(0) }
    var selectedGridIndex by remember { mutableIntStateOf(0) }
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    var settingsFocus by remember { mutableStateOf(SettingsFocusTarget.BLUETOOTH) }
    var settingsTick by remember { mutableIntStateOf(0) }

    val homeGridState = rememberLazyGridState()
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

    val appScope = rememberCoroutineScope()

    /** Rebuilds the pinned list from the apps already loaded (instant: used after pin/move changes). */
    fun refreshPins() {
        // "Play PC" leads the pinned apps whenever Artemis Legion is installed
        pinnedApps = listOfNotNull(AppRepository.playPcTile(context)) +
            AppRepository.getOrSeedPinnedApps(context, allApps, prefs)
    }

    /** Full reload of every app and icon, off the main thread so the hub never freezes while it loads. */
    fun reloadApps() {
        appScope.launch {
            val loaded = withContext(Dispatchers.IO) { AppRepository.loadLaunchableApps(context) }
            allApps = loaded
            refreshPins()
        }
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    val pinnedPackagesSet = remember(pinnedApps) {
        pinnedApps.map { it.packageName }.toSet()
    }

    val gridItems: List<NatureGridItem> = remember(activeTab, pinnedApps, allApps) {
        when (activeTab) {
            TopNavTab.HOME -> {
                pinnedApps.map { NatureGridItem.App(it) } + NatureGridItem.AllAppsTile
            }
            TopNavTab.APPS -> {
                allApps.map { NatureGridItem.App(it) }
            }
            TopNavTab.GAMES -> {
                val playPc = pinnedApps.firstOrNull { it.packageName == AppRepository.PLAY_PC_ID }
                val games = (listOfNotNull(playPc) + allApps.filter { AppRepository.isGameApp(it) })
                    .distinctBy { it.packageName }
                games.map { NatureGridItem.App(it) }
            }
            TopNavTab.MEDIA -> {
                allApps.filter { AppRepository.isMediaApp(it) }
                    .map { NatureGridItem.App(it) }
            }
            TopNavTab.SETTINGS -> {
                pinnedApps.map { NatureGridItem.App(it) } + NatureGridItem.AllAppsTile
            }
        }
    }

    val safeGridIndex = if (gridItems.isEmpty()) 0 else selectedGridIndex.coerceIn(0, gridItems.lastIndex)
    val featuredItem: NatureGridItem? = gridItems.getOrNull(safeGridIndex)
    val featuredApp: AppEntry? = (featuredItem as? NatureGridItem.App)?.entry
    val featuredIsPinned = featuredApp != null && pinnedPackagesSet.contains(featuredApp.packageName)
    val canToggleFeaturedPin = featuredApp != null && !AppRepository.isVirtualTile(featuredApp.packageName)

    val latestPinnedApps by rememberUpdatedState(pinnedApps)
    val latestAllApps by rememberUpdatedState(allApps)
    val latestGridItems by rememberUpdatedState(gridItems)
    val latestActiveTab by rememberUpdatedState(activeTab)
    val latestPreviousBrowseTab by rememberUpdatedState(previousBrowseTab)
    val latestFocusZone by rememberUpdatedState(focusZone)
    val latestFocusedNavIndex by rememberUpdatedState(focusedNavIndex)
    val latestSettingsFocus by rememberUpdatedState(settingsFocus)

    fun openSettingsTab() {
        if (activeTab != TopNavTab.SETTINGS) {
            previousBrowseTab = activeTab
        }
        activeTab = TopNavTab.SETTINGS
        focusedNavIndex = TopNavTab.SETTINGS.ordinal
        settingsFocus = SettingsFocusTarget.BLUETOOTH
        focusZone = HubFocusZone.APP_GRID
        settingsTick++
        SwitchAudioFeedback.playMenu(context)
    }

    fun closeSettingsTab() {
        val returnTab = previousBrowseTab.takeIf { it != TopNavTab.SETTINGS } ?: TopNavTab.HOME
        activeTab = returnTab
        focusedNavIndex = returnTab.ordinal
        focusZone = HubFocusZone.APP_GRID
        SwitchAudioFeedback.playBack(context)
    }

    fun activateNavTab(tab: TopNavTab, focusGridAfter: Boolean) {
        val tabIdx = TopNavTab.entries.indexOf(tab).coerceAtLeast(0)
        focusedNavIndex = tabIdx
        if (tab == TopNavTab.SETTINGS) {
            openSettingsTab()
        } else {
            activeTab = tab
            previousBrowseTab = tab
            selectedGridIndex = 0
            if (focusGridAfter) {
                focusZone = HubFocusZone.APP_GRID
            }
            SwitchAudioFeedback.playSelect(context)
        }
    }

    fun activateGridItem(item: NatureGridItem?) {
        when (item) {
            is NatureGridItem.App -> onLaunchApp(item.entry)
            is NatureGridItem.AllAppsTile, null -> {
                SwitchAudioFeedback.playMenu(context)
                selectedDrawerIndex = 0
                showAllAppsDrawer = true
            }
        }
    }

    fun togglePinForItem(item: NatureGridItem?) {
        val app = (item as? NatureGridItem.App)?.entry ?: return
        if (AppRepository.isVirtualTile(app.packageName)) return
        prefs.togglePinnedPackage(app.packageName)
        SwitchAudioFeedback.playSelect(context)
        refreshPins()
    }

    fun triggerSleepMode() {
        isIdleDimmed = true
        onSetKeepScreenOn(false)
        if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
            ShizukuHelper.sleepDevice(context)
        }
    }

    fun performSettingsAction(target: SettingsFocusTarget) {
        settingsFocus = target
        focusZone = HubFocusZone.APP_GRID
        SwitchAudioFeedback.playSelect(context)
        when (target) {
            SettingsFocusTarget.VOL_DOWN ->
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            SettingsFocusTarget.VOL_UP ->
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            SettingsFocusTarget.VOL_MUTE ->
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            SettingsFocusTarget.SCREENSAVER -> {
                isIdleDimmed = true
            }
            SettingsFocusTarget.SLEEP -> triggerSleepMode()
            SettingsFocusTarget.EXIT_HUB -> onExitHub()
            SettingsFocusTarget.BLUETOOTH -> bluetooth.open()
            SettingsFocusTarget.WIFI_BOOST -> {
                val next = !prefs.artemisWifiBoostEnabled
                prefs.artemisWifiBoostEnabled = next
                ArtemisBoostManager.setBoostActive(context, next)
            }
            SettingsFocusTarget.BYPASS_CHARGING -> {
                if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                    val next = !prefs.bypassChargingEnabled
                    if (ShizukuHelper.setBypassCharging(context, next)) {
                        prefs.bypassChargingEnabled = next
                    }
                } else {
                    DockHealthMonitor.openBypassChargingSettings(context)
                }
            }
            SettingsFocusTarget.UI_SOUNDS -> {
                prefs.uiSoundsEnabled = !prefs.uiSoundsEnabled
            }
            SettingsFocusTarget.IDLE_SCREENSAVER -> {
                prefs.idleDimmerEnabled = !prefs.idleDimmerEnabled
            }
            SettingsFocusTarget.BLACKOUT_TABLET -> {
                prefs.blackoutTabletOnDock = !prefs.blackoutTabletOnDock
            }
            SettingsFocusTarget.DISPLAY_THEME -> {
                val currentShown = if (!isGlasses && activeTheme == HubTheme.SWITCH_DARK) {
                    HubTheme.EMERALD_PULSE
                } else {
                    activeTheme
                }
                val nextTheme = currentShown.next()
                activeTheme = nextTheme
                prefs.setThemeId(displayProfile, nextTheme.id)
            }
            SettingsFocusTarget.GLASSES_SIZE -> cycleGlassesScreenSize()
        }
        settingsTick++
    }

    val liveSettingsState = remember(settingsTick, screenSizeTick, activeTheme, health, isGlasses) {
        val displayTheme = if (isGlasses) activeTheme
        else if (activeTheme == HubTheme.SWITCH_DARK) HubTheme.EMERALD_PULSE
        else activeTheme
        NatureSettingsState(
            volumePct = readVolumePct(audioManager),
            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
            uiSoundsEnabled = prefs.uiSoundsEnabled,
            idleDimmerEnabled = prefs.idleDimmerEnabled,
            artemisWifiBoostEnabled = prefs.artemisWifiBoostEnabled,
            blackoutTabletOnDock = prefs.blackoutTabletOnDock,
            bypassChargingEnabled = prefs.bypassChargingEnabled,
            shizukuReady = ShizukuHelper.getStatus() == ShizukuStatus.READY,
            activeTheme = displayTheme,
            glassesScreenSizePct = prefs.glassesScreenSizePct
        )
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
            val currentItems = latestGridItems
            val currentTab = latestActiveTab
            val currentPrevTab = latestPreviousBrowseTab
            val currentZone = latestFocusZone
            val currentNavIdx = latestFocusedNavIndex
            val currentSettingsTarget = latestSettingsFocus
            val currentSafeIdx = if (currentItems.isEmpty()) 0 else selectedGridIndex.coerceIn(0, currentItems.lastIndex)
            val currentItem = currentItems.getOrNull(currentSafeIdx)
            val currentFocusedPkg = (currentItem as? NatureGridItem.App)?.entry?.packageName

            when {
                bluetooth.visible -> {
                    when (cmd) {
                        ControllerCommand.B_BACK, ControllerCommand.START_MENU -> SwitchAudioFeedback.playBack(context)
                        ControllerCommand.A_CONFIRM, ControllerCommand.X_ACTION, ControllerCommand.Y_DRAWER -> SwitchAudioFeedback.playSelect(context)
                        else -> SwitchAudioFeedback.playMove(context)
                    }
                    bluetooth.handle(cmd)
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
                                refreshPins()
                            }
                        }
                        ControllerCommand.B_BACK, ControllerCommand.Y_DRAWER -> {
                            showAllAppsDrawer = false
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.START_MENU -> {
                            showAllAppsDrawer = false
                            openSettingsTab()
                        }
                        else -> {}
                    }
                }

                currentZone == HubFocusZone.TOP_BAR -> {
                    val tabCount = TopNavTab.entries.size
                    when (cmd) {
                        ControllerCommand.LEFT, ControllerCommand.L1_PREV -> {
                            focusedNavIndex = (currentNavIdx - 1 + tabCount) % tabCount
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.RIGHT, ControllerCommand.R1_NEXT -> {
                            focusedNavIndex = (currentNavIdx + 1) % tabCount
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.DOWN -> {
                            val targetTab = TopNavTab.entries[currentNavIdx.coerceIn(0, tabCount - 1)]
                            if (targetTab == TopNavTab.SETTINGS) {
                                openSettingsTab()
                            } else {
                                if (targetTab != currentTab) {
                                    activeTab = targetTab
                                    previousBrowseTab = targetTab
                                    selectedGridIndex = 0
                                }
                                focusZone = if (currentItems.isEmpty() && targetTab == currentTab) {
                                    HubFocusZone.FEATURED_OPEN
                                } else {
                                    HubFocusZone.APP_GRID
                                }
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.A_CONFIRM -> {
                            val targetTab = TopNavTab.entries[currentNavIdx.coerceIn(0, tabCount - 1)]
                            activateNavTab(targetTab, focusGridAfter = true)
                        }
                        ControllerCommand.B_BACK -> {
                            focusZone = HubFocusZone.APP_GRID
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.Y_DRAWER -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedDrawerIndex = 0
                            showAllAppsDrawer = true
                        }
                        ControllerCommand.START_MENU -> {
                            if (currentTab == TopNavTab.SETTINGS) {
                                closeSettingsTab()
                            } else {
                                openSettingsTab()
                            }
                        }
                        else -> {}
                    }
                }

                currentTab == TopNavTab.SETTINGS -> {
                    when (cmd) {
                        ControllerCommand.LEFT, ControllerCommand.RIGHT,
                        ControllerCommand.UP, ControllerCommand.DOWN,
                        ControllerCommand.L1_PREV, ControllerCommand.R1_NEXT -> {
                            val next = currentSettingsTarget.navigate(cmd)
                            if (next == null) {
                                focusedNavIndex = TopNavTab.SETTINGS.ordinal
                                focusZone = HubFocusZone.TOP_BAR
                                SwitchAudioFeedback.playMove(context)
                            } else if (next != currentSettingsTarget) {
                                settingsFocus = next
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.A_CONFIRM -> performSettingsAction(currentSettingsTarget)
                        ControllerCommand.B_BACK, ControllerCommand.START_MENU -> {
                            val returnTab = currentPrevTab.takeIf { it != TopNavTab.SETTINGS } ?: TopNavTab.HOME
                            activeTab = returnTab
                            focusedNavIndex = returnTab.ordinal
                            focusZone = HubFocusZone.APP_GRID
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.Y_DRAWER -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedDrawerIndex = 0
                            showAllAppsDrawer = true
                        }
                        else -> {}
                    }
                }

                currentZone == HubFocusZone.FEATURED_OPEN -> {
                    when (cmd) {
                        ControllerCommand.RIGHT -> {
                            if (currentItems.isNotEmpty()) {
                                focusZone = HubFocusZone.APP_GRID
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.UP -> {
                            focusedNavIndex = TopNavTab.entries.indexOf(currentTab).coerceAtLeast(0)
                            focusZone = HubFocusZone.TOP_BAR
                            SwitchAudioFeedback.playMove(context)
                        }
                        ControllerCommand.A_CONFIRM -> activateGridItem(currentItem)
                        ControllerCommand.X_ACTION -> togglePinForItem(currentItem)
                        ControllerCommand.Y_DRAWER -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedDrawerIndex = 0
                            showAllAppsDrawer = true
                        }
                        ControllerCommand.B_BACK -> {
                            if (currentItems.isNotEmpty()) {
                                focusZone = HubFocusZone.APP_GRID
                            } else if (currentTab != TopNavTab.HOME) {
                                activeTab = TopNavTab.HOME
                                previousBrowseTab = TopNavTab.HOME
                                selectedGridIndex = 0
                                focusZone = HubFocusZone.APP_GRID
                            }
                            SwitchAudioFeedback.playBack(context)
                        }
                        ControllerCommand.START_MENU -> openSettingsTab()
                        else -> {}
                    }
                }

                else -> {
                    // HubFocusZone.APP_GRID — uniform 2-column grid navigation
                    val count = currentItems.size
                    val col = currentSafeIdx % 2
                    when (cmd) {
                        ControllerCommand.LEFT -> {
                            if (count == 0 || col == 0) {
                                focusZone = HubFocusZone.FEATURED_OPEN
                                SwitchAudioFeedback.playMove(context)
                            } else {
                                selectedGridIndex = currentSafeIdx - 1
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.RIGHT -> {
                            if (col == 0 && currentSafeIdx + 1 < count) {
                                selectedGridIndex = currentSafeIdx + 1
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.UP -> {
                            if (count == 0 || currentSafeIdx < 2) {
                                focusedNavIndex = TopNavTab.entries.indexOf(currentTab).coerceAtLeast(0)
                                focusZone = HubFocusZone.TOP_BAR
                                SwitchAudioFeedback.playMove(context)
                            } else {
                                selectedGridIndex = currentSafeIdx - 2
                                SwitchAudioFeedback.playMove(context)
                            }
                        }
                        ControllerCommand.DOWN -> {
                            when {
                                currentSafeIdx + 2 < count -> {
                                    selectedGridIndex = currentSafeIdx + 2
                                    SwitchAudioFeedback.playMove(context)
                                }
                                col == 1 && currentSafeIdx + 1 < count -> {
                                    selectedGridIndex = count - 1
                                    SwitchAudioFeedback.playMove(context)
                                }
                            }
                        }
                        ControllerCommand.A_CONFIRM -> activateGridItem(currentItem)
                        ControllerCommand.X_ACTION -> togglePinForItem(currentItem)
                        ControllerCommand.Y_DRAWER -> {
                            SwitchAudioFeedback.playMenu(context)
                            selectedDrawerIndex = 0
                            showAllAppsDrawer = true
                        }
                        ControllerCommand.B_BACK -> {
                            if (currentTab != TopNavTab.HOME) {
                                activeTab = TopNavTab.HOME
                                previousBrowseTab = TopNavTab.HOME
                                focusedNavIndex = 0
                                selectedGridIndex = 0
                                SwitchAudioFeedback.playBack(context)
                            }
                        }
                        ControllerCommand.START_MENU -> openSettingsTab()
                        ControllerCommand.L1_PREV -> {
                            if (currentTab == TopNavTab.HOME && currentFocusedPkg != null &&
                                !AppRepository.isVirtualTile(currentFocusedPkg) &&
                                prefs.movePinnedPackage(currentFocusedPkg, -1)
                            ) {
                                selectedGridIndex = (currentSafeIdx - 1).coerceAtLeast(0)
                                SwitchAudioFeedback.playMove(context)
                                refreshPins()
                            } else {
                                val browseTabs = listOf(TopNavTab.HOME, TopNavTab.APPS, TopNavTab.GAMES, TopNavTab.MEDIA)
                                val nextTab = browseTabs[(browseTabs.indexOf(currentTab).coerceAtLeast(0) - 1 + browseTabs.size) % browseTabs.size]
                                activateNavTab(nextTab, focusGridAfter = true)
                            }
                        }
                        ControllerCommand.R1_NEXT -> {
                            if (currentTab == TopNavTab.HOME && currentFocusedPkg != null &&
                                !AppRepository.isVirtualTile(currentFocusedPkg) &&
                                prefs.movePinnedPackage(currentFocusedPkg, +1)
                            ) {
                                selectedGridIndex = (currentSafeIdx + 1).coerceAtMost((currentPinned.size - 1).coerceAtLeast(0))
                                SwitchAudioFeedback.playMove(context)
                                refreshPins()
                            } else {
                                val browseTabs = listOf(TopNavTab.HOME, TopNavTab.APPS, TopNavTab.GAMES, TopNavTab.MEDIA)
                                val nextTab = browseTabs[(browseTabs.indexOf(currentTab).coerceAtLeast(0) + 1) % browseTabs.size]
                                activateNavTab(nextTab, focusGridAfter = true)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(safeGridIndex, activeTab) {
        if (gridItems.isNotEmpty()) {
            runCatching { homeGridState.animateScrollToItem(safeGridIndex) }
        }
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
                selectedGridIndex = 0
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

    // Normalize the TV layout to an approximately 1280x720 design canvas so 4K TVs,
    // 1080p TVs, and the Lenovo Legion Y700 tablet all preserve crisp, balanced proportions.
    // XR glasses retain native density and pure black transparency.
    val displayConfiguration = LocalConfiguration.current
    val platformDensity = LocalDensity.current
    val tvScale = if (isGlasses) 1f else minOf(
        displayConfiguration.screenWidthDp / 1280f,
        displayConfiguration.screenHeightDp / 720f
    ).coerceIn(0.62f, 3.0f)
    val hubDensity = Density(platformDensity.density * tvScale, platformDensity.fontScale)
    val modalTheme = remember(isGlasses, activeTheme) {
        if (isGlasses) activeTheme
        else if (activeTheme == HubTheme.SWITCH_DARK) HubTheme.EMERALD_PULSE
        else activeTheme
    }

    CompositionLocalProvider(LocalDensity provides hubDensity) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isGlasses) Color.Black
                    else NatureGlassColors.ForestDark
                )
        ) {
            // Full-bleed offline Nature Glass forest, mountain, aurora & waterfall landscape.
            // XR glasses stay pure black for optical transparency.
            if (!isGlasses) {
                Image(
                    painter = painterResource(R.drawable.nature_glass_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gentle emerald depth scrim when Settings Command Center is open so every toggle pops
                if (activeTab == TopNavTab.SETTINGS) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x6602120B))
                    )
                }
                // Gentle top contrast vignette so top-bar nav and status stay razor sharp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xA603120C), Color(0x4703120C), Color.Transparent)
                            )
                        )
                )
                // Gentle bottom contrast vignette behind controller hint dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x6E020E09))
                            )
                        )
                )
            }

            val configuration = LocalConfiguration.current
            val glassesMarginFraction = if (isGlasses) {
                maxOf(
                    prefs.glassesEdgeMarginPct / 100f,
                    (100 - screenSizeTick.let { prefs.glassesScreenSizePct }) / 200f
                )
            } else 0f
            val outerPadH = (if (isGlasses) 40.dp else 26.dp) + (configuration.screenWidthDp * glassesMarginFraction).dp
            val outerPadTop = (if (isGlasses) 24.dp else 14.dp) + (configuration.screenHeightDp * glassesMarginFraction).dp
            val outerPadBottom = if (isGlasses) (20.dp + (configuration.screenHeightDp * glassesMarginFraction).dp) else 0.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = outerPadTop, bottom = outerPadBottom),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Clean Top Bar: Brand left, Navigation middle, Real Device Status right
                NatureGlassTopBar(
                    activeTab = activeTab,
                    focusedTabIndex = focusedNavIndex,
                    isTopBarFocused = focusZone == HubFocusZone.TOP_BAR,
                    isGlasses = isGlasses,
                    currentTime = currentTime,
                    networkType = networkType,
                    health = health,
                    thermalWarningEnabled = prefs.thermalWarningEnabled,
                    glassesBatteryWarning = prefs.glassesBatteryWarning,
                    onSelectTab = { tab -> activateNavTab(tab, focusGridAfter = true) },
                    modifier = Modifier.padding(horizontal = outerPadH)
                )

                // 2. Main Split Workspace: Settings Command Center OR Featured Panel + 2x3 App Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            start = outerPadH,
                            end = outerPadH,
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeTab == TopNavTab.SETTINGS) {
                        NatureGlassSettingsWorkspace(
                            health = health,
                            networkType = networkType,
                            displaySummary = displaySummary,
                            state = liveSettingsState,
                            focusedTarget = settingsFocus,
                            isSettingsFocused = focusZone != HubFocusZone.TOP_BAR,
                            isGlasses = isGlasses,
                            onAction = { target -> performSettingsAction(target) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 548.dp)
                                .fillMaxHeight()
                        )
                    } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 532.dp)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Translucent dark-green organic featured panel
                        NatureGlassFeaturedPanel(
                            featuredItem = featuredItem,
                            allAppsCount = allApps.size,
                            isPinned = featuredIsPinned,
                            isOpenButtonFocused = focusZone == HubFocusZone.FEATURED_OPEN,
                            isGlasses = isGlasses,
                            onOpenClick = {
                                focusZone = HubFocusZone.FEATURED_OPEN
                                activateGridItem(featuredItem)
                            },
                            modifier = Modifier
                                .weight(1.18f)
                                .fillMaxHeight()
                                .padding(vertical = 10.dp)
                        )

                        // Right: Uniform 2-column x 3-row grid of large glass app cards
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                        ) {
                            val gridSpacing = 14.dp
                            val gridPad = 12.dp
                            val uniformCardHeight = ((maxHeight - gridPad * 2 - gridSpacing * 2) / 3f)
                                .coerceAtLeast(112.dp)

                            if (gridItems.isEmpty()) {
                                Surface(
                                    color = Color(0xA6082419),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.2.dp, NatureGlassColors.CardBorderIdle),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(gridPad)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = activeTab.icon,
                                            contentDescription = null,
                                            tint = NatureGlassColors.EmeraldBright,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            text = "No ${activeTab.label} apps detected",
                                            color = NatureGlassColors.TextPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Press Y for All Apps or switch to Home to view your pinned apps.",
                                            color = NatureGlassColors.TextSecondary,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    state = homeGridState,
                                    columns = GridCells.Fixed(2),
                                    verticalArrangement = Arrangement.spacedBy(gridSpacing),
                                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                                    contentPadding = PaddingValues(gridPad),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = gridItems,
                                        key = { _, item ->
                                            when (item) {
                                                is NatureGridItem.App -> "${activeTab.name}:${item.entry.packageName}"
                                                NatureGridItem.AllAppsTile -> "all_apps_tile"
                                            }
                                        }
                                    ) { index, item ->
                                        val isCardSelected = index == safeGridIndex
                                        val showPinnedDot = activeTab != TopNavTab.HOME &&
                                            item is NatureGridItem.App &&
                                            pinnedPackagesSet.contains(item.entry.packageName)

                                        NatureGlassGridCard(
                                            item = item,
                                            isSelected = isCardSelected,
                                            isGridFocused = focusZone == HubFocusZone.APP_GRID,
                                            showPinnedBadge = showPinnedDot,
                                            cardHeight = uniformCardHeight,
                                            onClick = {
                                                focusZone = HubFocusZone.APP_GRID
                                                selectedGridIndex = index
                                                activateGridItem(item)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // 3. Bottom Translucent Controller-Hint Bar
                val isSettingsOpen = activeTab == TopNavTab.SETTINGS
                val settingsConfirmLabel = when (settingsFocus) {
                    SettingsFocusTarget.VOL_DOWN, SettingsFocusTarget.VOL_UP -> "Adjust"
                    SettingsFocusTarget.WIFI_BOOST,
                    SettingsFocusTarget.UI_SOUNDS,
                    SettingsFocusTarget.IDLE_SCREENSAVER,
                    SettingsFocusTarget.BLACKOUT_TABLET -> "Toggle"
                    SettingsFocusTarget.BYPASS_CHARGING -> if (liveSettingsState.shizukuReady) "Toggle" else "Open"
                    SettingsFocusTarget.DISPLAY_THEME, SettingsFocusTarget.GLASSES_SIZE -> "Cycle"
                    else -> "Select"
                }
                NatureGlassBottomHintBar(
                    openLabel = when {
                        focusZone == HubFocusZone.TOP_BAR -> "Select"
                        isSettingsOpen -> settingsConfirmLabel
                        else -> "Open"
                    },
                    pinLabel = when {
                        isSettingsOpen -> null
                        !canToggleFeaturedPin -> null
                        featuredIsPinned -> "Unpin"
                        else -> "Pin"
                    },
                    showPinAction = !isSettingsOpen,
                    onOpen = {
                        when {
                            focusZone == HubFocusZone.TOP_BAR -> {
                                val tab = TopNavTab.entries[focusedNavIndex.coerceIn(0, TopNavTab.entries.lastIndex)]
                                activateNavTab(tab, focusGridAfter = true)
                            }
                            isSettingsOpen -> performSettingsAction(settingsFocus)
                            else -> activateGridItem(featuredItem)
                        }
                    },
                    onPin = { togglePinForItem(featuredItem) },
                    onAllApps = {
                        SwitchAudioFeedback.playMenu(context)
                        selectedDrawerIndex = 0
                        showAllAppsDrawer = true
                    },
                    onBack = {
                        when {
                            isSettingsOpen -> closeSettingsTab()
                            focusZone != HubFocusZone.APP_GRID -> {
                                focusZone = HubFocusZone.APP_GRID
                                SwitchAudioFeedback.playBack(context)
                            }
                            activeTab != TopNavTab.HOME -> {
                                activeTab = TopNavTab.HOME
                                previousBrowseTab = TopNavTab.HOME
                                focusedNavIndex = 0
                                selectedGridIndex = 0
                                SwitchAudioFeedback.playBack(context)
                            }
                            else -> onExitHub()
                        }
                    }
                )
            }

            // All Apps & Pin Manager Overlay
            if (showAllAppsDrawer) {
                AllAppsDrawerOverlay(
                    allApps = allApps,
                    pinnedPackages = pinnedPackagesSet,
                    selectedIndex = selectedDrawerIndex.coerceIn(0, (allApps.size - 1).coerceAtLeast(0)),
                    gridState = drawerGridState,
                    activeTheme = modalTheme,
                    onLaunchApp = { idx, app ->
                        selectedDrawerIndex = idx
                        showAllAppsDrawer = false
                        onLaunchApp(app)
                    },
                    onTogglePin = { idx, app ->
                        selectedDrawerIndex = idx
                        SwitchAudioFeedback.playSelect(context)
                        prefs.togglePinnedPackage(app.packageName)
                        refreshPins()
                    },
                    onClose = {
                        SwitchAudioFeedback.playBack(context)
                        showAllAppsDrawer = false
                    }
                )
            }

            // In-hub Bluetooth devices screen
            if (bluetooth.visible) {
                BluetoothPanel(bluetooth, modalTheme)
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
                            color = modalTheme.textSecondary,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = "Press any controller button to wake",
                            color = modalTheme.accent.copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                    }
                }
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
            .background(activeTheme.bgBottom.copy(alpha = 0.90f))
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
