package com.legion.switchdock.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.legion.switchdock.TvHubActivity
import com.legion.switchdock.data.DockHealthMonitor
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.media.SwitchAudioFeedback
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import kotlin.math.abs
import kotlin.math.roundToInt

class QuickMenuOverlay(
    private val serviceContext: Context,
    private val onReturnToHub: (Int) -> Unit
) {
    private var rootView: View? = null
    private var attachedWindowManager: WindowManager? = null
    private var volumeLabel: TextView? = null
    private var soundsButton: TextView? = null
    private var menuButtons: List<TextView> = emptyList()
    private var focusedIndex: Int = 3

    private var shownAtMs: Long = 0L
    private var lastNavStepMs: Long = 0L
    private var stickNeutral: Boolean = true
    private var lastHandledDownTime: Long = -1L
    private var lastHandledEventTime: Long = -1L
    private var lastHandledKeyCode: Int = -1
    private var lastHandledAction: Int = -1

    val isShowing: Boolean
        get() = rootView != null

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isShowing) return false
        if (event.downTime == lastHandledDownTime &&
            event.eventTime == lastHandledEventTime &&
            event.keyCode == lastHandledKeyCode &&
            event.action == lastHandledAction
        ) {
            return true
        }
        val relevantKeys = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT
        )
        if (event.keyCode !in relevantKeys) return false

        lastHandledDownTime = event.downTime
        lastHandledEventTime = event.eventTime
        lastHandledKeyCode = event.keyCode
        lastHandledAction = event.action

        if (event.action != KeyEvent.ACTION_DOWN) {
            return true
        }
        val buttons = menuButtons
        if (buttons.isEmpty()) return false
        val now = SystemClock.uptimeMillis()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                val minDelay = if (event.repeatCount > 0) 300L else 140L
                if (now - lastNavStepMs >= minDelay) {
                    lastNavStepMs = now
                    setFocusedIndex((focusedIndex - 1 + buttons.size) % buttons.size)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                val minDelay = if (event.repeatCount > 0) 300L else 140L
                if (now - lastNavStepMs >= minDelay) {
                    lastNavStepMs = now
                    setFocusedIndex((focusedIndex + 1) % buttons.size)
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    buttons.getOrNull(focusedIndex)?.performClick()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                if (event.repeatCount == 0) {
                    SwitchAudioFeedback.playBack(serviceContext)
                    dismiss()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (event.repeatCount == 0 && now - shownAtMs >= 400L) {
                    SwitchAudioFeedback.playBack(serviceContext)
                    dismiss()
                }
                true
            }
            else -> false
        }
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!isShowing) return false
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (!isJoystick || event.action != MotionEvent.ACTION_MOVE) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

        val x = if (abs(hatX) > abs(stickX)) hatX else stickX
        val y = if (abs(hatY) > abs(stickY)) hatY else stickY

        val threshold = 0.55f
        val now = SystemClock.uptimeMillis()
        val buttons = menuButtons
        if (buttons.isEmpty()) return false

        if (abs(x) < 0.25f && abs(y) < 0.25f) {
            stickNeutral = true
        } else if ((stickNeutral && now - lastNavStepMs >= 140L) || now - lastNavStepMs >= 300L) {
            val delta = when {
                abs(x) >= abs(y) && x <= -threshold -> -1
                abs(x) >= abs(y) && x >= threshold -> 1
                abs(y) > abs(x) && y <= -threshold -> -1
                abs(y) > abs(x) && y >= threshold -> 1
                else -> 0
            }
            if (delta != 0) {
                stickNeutral = false
                lastNavStepMs = now
                setFocusedIndex((focusedIndex + delta + buttons.size) % buttons.size)
                return true
            }
        }
        return false
    }

    private fun setFocusedIndex(index: Int) {
        val buttons = menuButtons
        if (buttons.isEmpty()) return
        focusedIndex = index.coerceIn(0, buttons.lastIndex)
        buttons.forEachIndexed { idx, btn ->
            val active = idx == focusedIndex
            btn.isSelected = active
            if (active) btn.requestFocus()
        }
        SwitchAudioFeedback.playMove(serviceContext)
    }

    fun toggle(display: Display, lastForegroundPackage: String?) {
        if (isShowing) {
            dismiss()
        } else {
            show(display, lastForegroundPackage)
        }
    }

    fun show(display: Display, lastForegroundPackage: String?) {
        dismiss()

        val displayContext = serviceContext.createDisplayContext(display)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = DockPreferences(serviceContext)
        val audioManager = serviceContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val health = DockHealthMonitor.readSnapshot(serviceContext)

        val backdrop = object : FrameLayout(displayContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (handleKeyEvent(event)) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if (handleMotionEvent(event)) {
                    return true
                }
                return super.dispatchGenericMotionEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.argb(185, 8, 10, 14))
            setOnClickListener { dismiss() }
        }

        val card = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(displayContext, 24)
            setPadding(pad, pad, pad, pad)
            background = roundedBg(
                fillColor = Color.rgb(20, 24, 33),
                strokeColor = Color.rgb(0, 229, 255),
                strokeWidthPx = dp(displayContext, 2),
                radiusPx = dp(displayContext, 20).toFloat()
            )
            isClickable = true
        }

        // Title + Health Row
        val titleView = TextView(displayContext).apply {
            text = "LEGION QUICK MENU"
            setTextColor(Color.rgb(0, 229, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            paint.isFakeBoldText = true
        }
        card.addView(titleView)

        val tempColor = if (health.isThermalWarning) Color.rgb(245, 158, 11) else Color.rgb(155, 163, 180)
        val chargeTag = if (health.isCharging) "Charging ${health.wattsEstimate}W" else "Discharging"
        val thermalSuffix = if (health.isThermalWarning) " • HIGH TEMP (Enable Bypass Charging)" else ""
        val healthView = TextView(displayContext).apply {
            text = "${health.batteryPercent}% • ${String.format("%.1f", health.temperatureC)}°C • $chargeTag$thermalSuffix"
            setTextColor(tempColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(displayContext, 4), 0, dp(displayContext, 14))
        }
        card.addView(healthView)

        // Volume Control Row
        val volStatus = TextView(displayContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(displayContext, 8))
        }
        volumeLabel = volStatus
        updateVolumeLabel(audioManager)
        card.addView(volStatus)

        val volRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(displayContext, 12) }
            layoutParams = lp
        }

        val volDownBtn = createMenuButton(displayContext, "Vol -") {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            updateVolumeLabel(audioManager)
        }
        val volUpBtn = createMenuButton(displayContext, "Vol +") {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            updateVolumeLabel(audioManager)
        }
        val muteBtn = createMenuButton(displayContext, "Mute") {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            updateVolumeLabel(audioManager)
        }

        listOf(volDownBtn, volUpBtn, muteBtn).forEachIndexed { idx, btn ->
            val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx > 0) marginStart = dp(displayContext, 8)
            }
            volRow.addView(btn, btnLp)
        }
        card.addView(volRow)

        // Action Buttons
        val returnBtn = createMenuButton(displayContext, "Return to TV Hub") {
            dismiss()
            onReturnToHub(display.displayId)
        }
        addMenuRow(displayContext, card, returnBtn)

        val targetPkg = lastForegroundPackage?.takeIf { it != serviceContext.packageName }
        val shizukuReady = ShizukuHelper.getStatus() == ShizukuStatus.READY
        val forceCloseLabel = if (targetPkg != null) {
            "Force-Close App ($targetPkg)" + if (!shizukuReady) " [Needs Shizuku]" else ""
        } else {
            "Force-Close Current App (None active)"
        }
        val forceCloseBtn = createMenuButton(displayContext, forceCloseLabel) {
            if (targetPkg != null && ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                ShizukuHelper.forceStopPackage(serviceContext, targetPkg)
                dismiss()
                onReturnToHub(display.displayId)
            } else {
                Toast.makeText(
                    serviceContext,
                    if (targetPkg == null) "No active game/app to force-close" else "Connect Shizuku in Legion Switch Dock to force-close apps",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        addMenuRow(displayContext, card, forceCloseBtn)

        val audioOutBtn = createMenuButton(displayContext, "Audio Output / Bluetooth Settings") {
            dismiss()
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ExternalDisplayHelper.launchIntentOnDisplay(serviceContext, btIntent, display.displayId)
        }
        addMenuRow(displayContext, card, audioOutBtn)

        val soundToggleBtn = createMenuButton(
            displayContext,
            "UI Sounds: ${if (prefs.uiSoundsEnabled) "ON" else "OFF"}"
        ) {
            val next = !prefs.uiSoundsEnabled
            prefs.uiSoundsEnabled = next
            soundsButton?.text = "UI Sounds: ${if (next) "ON" else "OFF"}"
            if (next) SwitchAudioFeedback.playSelect(serviceContext)
        }
        soundsButton = soundToggleBtn
        addMenuRow(displayContext, card, soundToggleBtn)

        var bypassButtonRef: TextView? = null
        val bypassLabel = if (shizukuReady) {
            "Bypass Charging: ${if (prefs.bypassChargingEnabled) "ON" else "OFF"} (Tap to Toggle)"
        } else {
            "Bypass Charging / Game Assistant"
        }
        val bypassBtn = createMenuButton(displayContext, bypassLabel) {
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                val next = !prefs.bypassChargingEnabled
                prefs.bypassChargingEnabled = next
                ShizukuHelper.setBypassCharging(serviceContext, next)
                bypassButtonRef?.text = "Bypass Charging: ${if (next) "ON" else "OFF"} (Tap to Toggle)"
            } else {
                dismiss()
                DockHealthMonitor.openBypassChargingSettings(serviceContext)
            }
        }
        bypassButtonRef = bypassBtn
        addMenuRow(displayContext, card, bypassBtn)

        val sleepBtn = createMenuButton(displayContext, "Sleep Mode (Tablet & TV)") {
            dismiss()
            if (ShizukuHelper.getStatus() == ShizukuStatus.READY) {
                ShizukuHelper.sleepDevice(serviceContext)
            } else {
                Toast.makeText(
                    serviceContext,
                    "Connect Shizuku for instant Sleep Mode, or return to TV Hub",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        addMenuRow(displayContext, card, sleepBtn)

        val closeBtn = createMenuButton(displayContext, "Close Menu (B)") {
            dismiss()
        }
        addMenuRow(displayContext, card, closeBtn)

        val cardLp = FrameLayout.LayoutParams(
            dp(displayContext, 480),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        backdrop.addView(card, cardLp)

        menuButtons = listOf(
            volDownBtn,
            volUpBtn,
            muteBtn,
            returnBtn,
            forceCloseBtn,
            audioOutBtn,
            soundToggleBtn,
            bypassBtn,
            sleepBtn,
            closeBtn
        )
        focusedIndex = 3 // Default to "Return to TV Hub"

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        runCatching {
            wm.addView(backdrop, params)
            rootView = backdrop
            attachedWindowManager = wm
            activeInstance = this
            shownAtMs = SystemClock.uptimeMillis()
            backdrop.post {
                backdrop.requestFocus()
                setFocusedIndex(3)
            }
            SwitchAudioFeedback.playMenu(serviceContext)
        }
    }

    fun dismiss() {
        val view = rootView ?: return
        val wm = attachedWindowManager
        rootView = null
        attachedWindowManager = null
        if (activeInstance === this) {
            activeInstance = null
        }
        volumeLabel = null
        soundsButton = null
        menuButtons = emptyList()
        runCatching { wm?.removeViewImmediate(view) }
    }

    private fun updateVolumeLabel(audioManager: AudioManager) {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val pct = ((cur.toFloat() / max) * 100f).roundToInt()
        val muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        volumeLabel?.text = if (muted) "Media Volume: MUTED ($pct%)" else "Media Volume: $pct%"
    }

    private fun addMenuRow(ctx: Context, parent: LinearLayout, view: View) {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(ctx, 8) }
        parent.addView(view, lp)
    }

    private fun createMenuButton(ctx: Context, label: String, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dp(ctx, 16)
            val vPad = dp(ctx, 12)
            setPadding(hPad, vPad, hPad, vPad)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = focusableBg(ctx)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) SwitchAudioFeedback.playMove(serviceContext)
            }
            setOnClickListener {
                SwitchAudioFeedback.playSelect(serviceContext)
                onClick()
            }
        }
    }

    private fun focusableBg(ctx: Context): StateListDrawable {
        val focused = roundedBg(
            fillColor = Color.rgb(0, 68, 86),
            strokeColor = Color.rgb(0, 229, 255),
            strokeWidthPx = dp(ctx, 2),
            radiusPx = dp(ctx, 12).toFloat()
        )
        val normal = roundedBg(
            fillColor = Color.rgb(31, 36, 48),
            strokeColor = Color.rgb(46, 53, 69),
            strokeWidthPx = dp(ctx, 1),
            radiusPx = dp(ctx, 12).toFloat()
        )
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), focused)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun roundedBg(
        fillColor: Int,
        strokeColor: Int,
        strokeWidthPx: Int,
        radiusPx: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            setStroke(strokeWidthPx, strokeColor)
            cornerRadius = radiusPx
        }
    }

    private fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).roundToInt()
    }

    companion object {
        @Volatile
        var activeInstance: QuickMenuOverlay? = null
    }
}
