package com.legion.switchdock.controller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.legion.switchdock.IShellService
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.service.DockReturnAccessibilityService
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Steam Controller (2026) support without Steam.
 *
 * Android only sees the controller in "lizard mode" (a keyboard and mouse). This driver reads the
 * Steam Controller Puck (or a USB-C cable) directly, switches lizard mode off, and feeds the input
 * into a standard virtual gamepad created through Shizuku, so every app (Artemis, PXPlay, games,
 * the dock hub) sees a normal controller. The right touchpad can drive a real mouse pointer.
 *
 * Protocol reference: SDL's open-source driver (src/joystick/hidapi/SDL_hidapi_steam_triton.c).
 */
object SteamControllerDriver {
    private const val TAG = "SteamController"
    private const val VALVE = 0x28de
    private const val PUCK = 0x1304
    private const val PUCK_2 = 0x1305
    private const val WIRED = 0x1302
    private const val ACTION_PERMISSION = "com.legion.switchdock.STEAM_CONTROLLER_PERMISSION"

    // The virtual gamepad presents itself as an Xbox One S controller over Bluetooth: Android's kernel
    // driver for it (hid-microsoft) adds rumble, and Android has a ready-made button layout for it.
    private const val XBOX_VID = 0x045e
    private const val XBOX_ONE_S_BT = 0x02fd
    private const val BUS_BLUETOOTH = 0x05
    // The touchpad mouse uses a pid.codes test ID so no special driver claims it
    private const val VIRTUAL_VID = 0x1209
    private const val VIRTUAL_MOUSE_PID = 0x5c03

    private val slotsByHandle = java.util.concurrent.ConcurrentHashMap<Int, Any>()
    private val uhidListener = object : com.legion.switchdock.IUhidListener.Stub() {
        override fun onOutput(handle: Int, data: ByteArray) {
            (slotsByHandle[handle] as? Slot)?.onSystemOutput(data)
        }
    }
    @Volatile private var listenerShell: IShellService? = null
    internal fun ensureListener(shell: IShellService) {
        if (listenerShell === shell) return
        runCatching { shell.uhidSetListener(uhidListener); listenerShell = shell }
    }

    private val _status = MutableStateFlow("Plug the Steam Controller Puck into the dock")
    val status: StateFlow<String> = _status

    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val opened = HashMap<String, PuckConnection>()
    private var receiverRegistered = false

    // Any Valve device: the known puck/controller IDs, plus newer revisions with other product IDs
    fun isSteamDevice(d: UsbDevice) = d.vendorId == VALVE

    private fun isCabled(d: UsbDevice) = d.productId == WIRED || (d.productId != PUCK && d.productId != PUCK_2 && d.interfaceCount < 3)

    /** Call from the dock service and when the puck is plugged in. Safe to call repeatedly. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!receiverRegistered) {
            receiverRegistered = true
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_PERMISSION)
            }
            ctx.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            // Clear virtual controllers left behind if the app was restarted
            main.postDelayed({ ShizukuHelper.shellServiceOrNull(ctx)?.let { runCatching { it.uhidDestroyAll() } } }, 1500)
        }
        scan()
    }

    fun rescan() = main.post { scan() }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> device?.let { close(it.deviceName) }
                else -> scan()
            }
        }
    }

    private fun scan() {
        val ctx = appContext ?: return
        val prefs = DockPreferences(ctx)
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usb.deviceList.values.filter { isSteamDevice(it) }
        // Forget connections whose device is gone
        opened.keys.filter { name -> devices.none { it.deviceName == name } }.forEach { close(it) }
        if (devices.isEmpty()) {
            val seen = usb.deviceList.values.joinToString { "%04x:%04x %s".format(it.vendorId, it.productId, it.productName ?: "") .trim() }
            _status.value = if (seen.isEmpty()) "No USB devices seen — plug the puck into the dock (or straight into the tablet)"
                else "Puck not found. USB devices seen: $seen"
            return
        }
        if (!prefs.steamControllerEnabled) {
            opened.keys.toList().forEach { close(it) }
            _status.value = "Turned off — the controller stays in mouse/keyboard mode"
            return
        }
        when (ShizukuHelper.getStatus()) {
            ShizukuStatus.NOT_RUNNING -> { _status.value = "Puck found — start Shizuku to use it as a gamepad"; retryLater(); return }
            ShizukuStatus.NEEDS_PERMISSION -> { _status.value = "Puck found — grant Shizuku permission to the dock app"; retryLater(); return }
            ShizukuStatus.READY -> {}
        }
        val shell = ShizukuHelper.shellServiceOrNull(ctx)
        if (shell == null) { _status.value = "Puck found — connecting to Shizuku…"; retryLater(); return }

        for (device in devices) {
            if (opened.containsKey(device.deviceName)) continue
            if (!usb.hasPermission(device)) {
                _status.value = "Puck found — tap Allow on the USB prompt"
                val pi = PendingIntent.getBroadcast(
                    ctx, 0, Intent(ACTION_PERMISSION).setPackage(ctx.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                runCatching { usb.requestPermission(device, pi) }
                continue
            }
            val conn = usb.openDevice(device)
            if (conn == null) { _status.value = "Couldn't open the puck — unplug it and plug it back in"; continue }
            val pc = PuckConnection(ctx, device, conn)
            if (pc.begin(shell)) {
                opened[device.deviceName] = pc
                _status.value = if (isCabled(device)) "Connected by cable" else "Puck ready — turn the controller on"
            } else {
                conn.close()
                _status.value = "Puck %04x:%04x has no controller slots I recognise (%d interfaces)".format(device.vendorId, device.productId, device.interfaceCount)
            }
        }
    }

    private fun retryLater() {
        main.removeCallbacks(retry)
        main.postDelayed(retry, 3000)
    }
    private val retry = Runnable { scan() }

    private fun close(deviceName: String) {
        opened.remove(deviceName)?.end()
        if (opened.isEmpty()) _status.value = "Plug the Steam Controller Puck into the dock"
    }

    internal fun report(msg: String) { _status.value = msg }

    /** One puck (or a cabled controller): up to four controller slots. */
    private class PuckConnection(val ctx: Context, val device: UsbDevice, val conn: UsbDeviceConnection) {
        private val slots = ArrayList<Slot>()

        private val lizard = ArrayList<UsbInterface>()

        fun begin(shell: IShellService): Boolean {
            val others = ArrayList<UsbInterface>()
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass != UsbConstants.USB_CLASS_HID) continue
                val isSlot = if (isCabled(device))
                    !(intf.interfaceSubclass == 1 && (intf.interfaceProtocol == 1 || intf.interfaceProtocol == 2))
                else intf.id in 2..5
                if (!isSlot) { others += intf; continue }
                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
                }
                if (epIn == null) continue
                if (!conn.claimInterface(intf, true)) continue
                slots += Slot(ctx, conn, intf, epIn, epOut, shell, wired = isCabled(device), number = slots.size + 1)
            }
            if (slots.isNotEmpty()) {
                // Take the controller's keyboard/mouse ("lizard") interfaces away from Android too, so
                // the moments before lizard mode switches off (e.g. waking the controller) can't
                // click on the tablet screen and pull focus away from the TV.
                others.forEach { if (conn.claimInterface(it, true)) lizard += it }
            }
            slots.forEach { it.start() }
            return slots.isNotEmpty()
        }

        fun end() {
            slots.forEach { it.stop() }
            slots.forEach { runCatching { conn.releaseInterface(it.intf) } }
            lizard.forEach { runCatching { conn.releaseInterface(it) } }
            runCatching { conn.close() }
        }
    }

    /** One controller slot: reads its reports and drives one virtual gamepad (+ mouse). */
    private class Slot(
        val ctx: Context,
        val conn: UsbDeviceConnection,
        val intf: UsbInterface,
        val epIn: UsbEndpoint,
        val epOut: UsbEndpoint?,
        var shell: IShellService,
        val wired: Boolean,
        val number: Int
    ) {
        @Volatile private var running = false
        private var thread: Thread? = null
        private var pad = 0
        private var mouse = 0
        private var lastMaintenance = 0L
        private var lastPad: ByteArray? = null
        private var lastButtons = 0L
        private var battery = -1
        private val prefs = DockPreferences(ctx)

        // touchpad mouse state
        private var rTouch = false; private var rx = 0; private var ry = 0
        private var lTouch = false; private var ly = 0
        private var accX = 0f; private var accY = 0f; private var accWheel = 0f
        private var mouseButtons = 0

        // rumble (0..100 from the system), resent every 40 ms: the controller stops on its own after ~50 ms
        @Volatile private var rumbleStrong = 0
        @Volatile private var rumbleWeak = 0
        @Volatile private var rumbleDirty = false
        private var lastRumbleSent = 0L
        private var lastReportAt = 0L

        fun start() {
            running = true
            thread = Thread({ loop() }, "steam-controller-$number").apply { isDaemon = true; start() }
            if (wired) connect()
        }

        fun stop() {
            running = false
            thread?.interrupt()
            disconnect()
        }

        private fun connect() {
            runCatching { lizardOff() }
            lastMaintenance = System.currentTimeMillis()
            if (pad > 0) return
            callShell { ensureListener(it) }
            pad = callShell { it.uhidCreateOnBus("Steam Controller", BUS_BLUETOOTH, XBOX_VID, XBOX_ONE_S_BT, GAMEPAD_DESCRIPTOR) } ?: 0
            if (pad <= 0) {
                val why = callShell { it.lastError() } ?: "Shizuku isn't responding"
                report("Couldn't create the virtual controller ($why)")
                pad = 0
                return
            }
            lastPad = null
            slotsByHandle[pad] = this
            report("Steam Controller connected" + if (battery >= 0) " · $battery% battery" else "")
        }

        private fun ensureMouse() {
            if (mouse > 0) return
            mouse = callShell { it.uhidCreate("Steam Controller Touchpad", VIRTUAL_VID, VIRTUAL_MOUSE_PID, MOUSE_DESCRIPTOR) } ?: 0
            if (mouse < 0) mouse = 0
        }

        /** Controller asleep/off: keep the virtual controller (so apps and focus stay put), just centre it. */
        private fun idle() {
            rumbleStrong = 0; rumbleWeak = 0
            if (pad <= 0) return
            val r = ByteArray(15); r[0] = 1; r[4] = 8
            lastPad = r
            callShell { it.uhidInput(pad, r) }
        }

        private fun disconnect() {
            slotsByHandle.remove(pad)
            if (pad > 0) callShell { it.uhidDestroy(pad) }
            if (mouse > 0) callShell { it.uhidDestroy(mouse) }
            pad = 0; mouse = 0
        }

        private fun <T> callShell(block: (IShellService) -> T): T? = try {
            block(shell)
        } catch (e: Exception) {
            // Shizuku restarted: pick up the new shell service
            ShizukuHelper.markServiceDead()
            ShizukuHelper.shellServiceOrNull(ctx)?.let { shell = it; ensureListener(it); runCatching { block(it) }.getOrNull() }
        }

        private fun lizardOff() {
            // Feature report 1: ID_SET_SETTINGS_VALUES (0x87) with SETTING_LIZARD_MODE (9) = LIZARD_MODE_OFF.
            // The controller's watchdog turns lizard mode back on unless this is resent every few seconds.
            val buf = ByteArray(64)
            buf[0] = 1; buf[1] = 0x87.toByte(); buf[2] = 3
            buf[3] = 9; buf[4] = 0; buf[5] = 0
            conn.controlTransfer(0x21, 0x09, 0x0301, intf.id, buf, buf.size, 500)
        }

        private fun loop() {
            val buf = ByteArray(maxOf(64, epIn.maxPacketSize))
            while (running) {
                val now = System.currentTimeMillis()
                if (now - lastMaintenance >= 3000) {
                    runCatching { lizardOff() }
                    lastMaintenance = now
                }
                if (rumbleDirty || ((rumbleStrong > 0 || rumbleWeak > 0) && now - lastRumbleSent >= 40)) sendRumble(now)
                val n = try { conn.bulkTransfer(epIn, buf, buf.size, 30) } catch (_: Exception) { -1 }
                if (n <= 0) continue
                // First report after a quiet spell = the controller just woke: switch lizard mode off right away
                val t = System.currentTimeMillis()
                if (t - lastReportAt > 1500) { runCatching { lizardOff() }; lastMaintenance = t }
                lastReportAt = t
                try { handle(buf, n) } catch (e: Exception) { Log.w(TAG, "bad report", e) }
            }
        }

        /** Rumble request from Android (hid-microsoft's Xbox report: id 3, magnitudes 0..100 at [4] and [5]). */
        fun onSystemOutput(data: ByteArray) {
            if (data.size < 6 || data[0].toInt() != 3) return
            rumbleStrong = (data[4].toInt() and 0xff).coerceIn(0, 100)
            rumbleWeak = (data[5].toInt() and 0xff).coerceIn(0, 100)
            rumbleDirty = true
        }

        private fun sendRumble(now: Long) {
            rumbleDirty = false
            lastRumbleSent = now
            // Output report 0x80: type, intensity, left motor (speed, gain), right motor (speed, gain)
            val left = rumbleStrong * 65535 / 100
            val right = rumbleWeak * 65535 / 100
            val r = ByteArray(10)
            r[0] = 0x80.toByte()
            // layout: [0]=id [1]=type [2,3]=intensity [4,5]=left speed [6]=left gain [7,8]=right speed [9]=right gain
            r[4] = left.toByte(); r[5] = (left shr 8).toByte(); r[6] = 0
            r[7] = right.toByte(); r[8] = (right shr 8).toByte(); r[9] = 0
            runCatching {
                val sent = if (epOut != null) conn.bulkTransfer(epOut, r, r.size, 50) else -1
                if (sent < 0) conn.controlTransfer(0x21, 0x09, 0x0280, intf.id, r, r.size, 100)
            }
        }

        private fun handle(d: ByteArray, n: Int) {
            when (d[0].toInt() and 0xff) {
                0x42, 0x45 -> if (n >= 30) state(d, padBase = 18)
                0x47 -> if (n >= 32) state(d, padBase = 20)
                0x43 -> if (n >= 3) {
                    val level = d[2].toInt() and 0xff
                    if (level != battery) {
                        battery = level
                        if (pad > 0) report("Steam Controller connected · $battery% battery")
                    }
                }
                0x46, 0x79 -> if (n >= 2) when (d[1].toInt()) {
                    2 -> connect()
                    1 -> { idle(); report("Steam Controller asleep — press the Steam button to wake it") }
                }
            }
        }

        private fun state(d: ByteArray, padBase: Int) {
            if (pad <= 0) connect()
            if (pad <= 0) return
            val b = u32(d, 2)
            val tl = s16(d, 6); val tr = s16(d, 8)
            val lx = s16(d, 10); val lyStick = s16(d, 12)
            val rxStick = s16(d, 14); val ryStick = s16(d, 16)
            val lpx = s16(d, padBase); val lpy = s16(d, padBase + 2)
            val rpx = s16(d, padBase + 6); val rpy = s16(d, padBase + 8)

            val mouseMode = prefs.steamTouchpadMouse

            // Buttons → HID usages 1..24 (Linux maps these to BTN_SOUTH, BTN_EAST, …, BTN_TRIGGER_HAPPY*)
            var out = 0
            fun set(bit: Long, idx: Int) { if ((b and bit) != 0L) out = out or (1 shl idx) }
            set(0x1, 0)          // A
            set(0x2, 1)          // B
            set(0x4, 3)          // X
            set(0x8, 4)          // Y
            set(0x80000, 6)      // L1
            set(0x200, 7)        // R1
            if (tl > 8192) out = out or (1 shl 8)   // L2 (digital)
            if (tr > 8192) out = out or (1 shl 9)   // R2 (digital)
            set(0x40, 11)        // Menu (Start)
            set(0x8000, 13)      // L3
            set(0x20, 14)        // R3
            set(0x10, 16)        // Quick access (…)
            set(0x20000, 17)     // L4
            set(0x80, 18)        // R4
            set(0x40000, 19)     // L5
            set(0x100, 20)       // R5
            if (!mouseMode) {
                set(0x4000000, 21)   // left touchpad click
                set(0x400000, 22)    // right touchpad click
            }

            // Quick access button opens the dock's quick menu
            val qam = 0x10L
            if ((b and qam) != 0L && (lastButtons and qam) == 0L) {
                ctx.sendBroadcast(Intent(DockReturnAccessibilityService.ACTION_TOGGLE_QUICK_MENU).setPackage(ctx.packageName))
            }
            lastButtons = b

            val up = (b and 0x2000) != 0L; val down = (b and 0x400) != 0L
            val left = (b and 0x1000) != 0L; val right = (b and 0x800) != 0L
            val hat = when {
                up && right -> 1; down && right -> 3; down && left -> 5; up && left -> 7
                up -> 0; right -> 2; down -> 4; left -> 6; else -> 8
            }

            val r = ByteArray(15)
            r[0] = 1
            r[1] = out.toByte(); r[2] = (out shr 8).toByte(); r[3] = (out shr 16).toByte()
            // View → "Back" key and Steam → "Home" key, as a real Xbox controller sends them
            var extra = hat
            if ((b and 0x4000L) != 0L) extra = extra or 0x20
            if ((b and 0x10000L) != 0L) extra = extra or 0x10
            r[4] = extra.toByte()
            put16(r, 5, lx); put16(r, 7, neg(lyStick))
            put16(r, 9, rxStick); put16(r, 11, neg(ryStick))
            r[13] = (tl.coerceIn(0, 32767) shr 7).toByte()
            r[14] = (tr.coerceIn(0, 32767) shr 7).toByte()
            if (!r.contentEquals(lastPad)) {
                lastPad = r
                callShell { it.uhidInput(pad, r) }
            }

            if (mouseMode) touchpadMouse(b, rpx, rpy, lpy)
        }

        private fun touchpadMouse(b: Long, rpx: Int, rpy: Int, lpy: Int) {
            val rDown = (b and 0x200000L) != 0L
            val lDown = (b and 0x2000000L) != 0L
            val sens = 1f / 40f
            var dx = 0; var dy = 0; var wheel = 0
            if (rDown) {
                if (rTouch) {
                    accX += (rpx - rx) * sens
                    accY += -(rpy - ry) * sens
                    dx = accX.toInt(); dy = accY.toInt()
                    accX -= dx; accY -= dy
                }
                rx = rpx; ry = rpy
            } else { accX = 0f; accY = 0f }
            rTouch = rDown
            if (lDown) {
                if (lTouch) {
                    accWheel += (lpy - ly) / 3000f
                    wheel = accWheel.toInt(); accWheel -= wheel
                }
                ly = lpy
            } else accWheel = 0f
            lTouch = lDown

            var buttons = 0
            if ((b and 0x400000L) != 0L) buttons = buttons or 1   // right pad click → left click
            if ((b and 0x4000000L) != 0L) buttons = buttons or 2  // left pad click → right click
            if (dx == 0 && dy == 0 && wheel == 0 && buttons == mouseButtons) return
            mouseButtons = buttons
            ensureMouse()
            if (mouse <= 0) return
            val m = byteArrayOf(
                buttons.toByte(),
                dx.coerceIn(-127, 127).toByte(),
                dy.coerceIn(-127, 127).toByte(),
                wheel.coerceIn(-127, 127).toByte()
            )
            callShell { it.uhidInput(mouse, m) }
        }
    }

    private fun u32(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xff) or ((d[o + 1].toLong() and 0xff) shl 8) or
            ((d[o + 2].toLong() and 0xff) shl 16) or ((d[o + 3].toLong() and 0xff) shl 24))

    private fun s16(d: ByteArray, o: Int): Int = ((d[o].toInt() and 0xff) or (d[o + 1].toInt() shl 8)).toShort().toInt()

    private fun neg(v: Int) = (-v).coerceIn(-32767, 32767)

    private fun put16(r: ByteArray, o: Int, v: Int) {
        val c = v.coerceIn(-32767, 32767)
        r[o] = c.toByte(); r[o + 1] = (c shr 8).toByte()
    }

    /** Gamepad: 24 buttons, hat, Home/Back keys, two sticks (X/Y, Z/Rz), brake/gas triggers (report 1); rumble out (report 3). */
    private val GAMEPAD_DESCRIPTOR = bytes(
        0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x85, 0x01,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x18, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x18, 0x81, 0x02,
        0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
        0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
        0x65, 0x00, 0x45, 0x00,
        0x05, 0x0C, 0x0A, 0x23, 0x02, 0x0A, 0x24, 0x02, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x02, 0x81, 0x02,
        0x75, 0x02, 0x95, 0x01, 0x81, 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
        0x16, 0x01, 0x80, 0x26, 0xFF, 0x7F, 0x75, 0x10, 0x95, 0x04, 0x81, 0x02,
        0x05, 0x02, 0x09, 0xC5, 0x09, 0xC4, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
        // Output report 3: rumble (filled in by the kernel's Xbox driver)
        0x85, 0x03, 0x06, 0x00, 0xFF, 0x09, 0x01, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x08, 0x91, 0x02,
        0xC0
    )

    /** Relative mouse: 3 buttons, X, Y, wheel. */
    private val MOUSE_DESCRIPTOR = bytes(
        0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01, 0xA1, 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x03, 0x81, 0x02,
        0x75, 0x05, 0x95, 0x01, 0x81, 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
        0xC0, 0xC0
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
