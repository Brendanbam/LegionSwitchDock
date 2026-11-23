package com.legion.switchdock.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legion.switchdock.ControllerCommand
import com.legion.switchdock.data.HubTheme
import com.legion.switchdock.display.ExternalDisplayHelper
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus

/**
 * Bluetooth screen inside the TV / glasses hub, driven by the controller.
 *
 * Rows: Bluetooth on/off, "Search for devices", paired devices, then devices found by a search.
 *   A on a paired device  = connect / disconnect
 *   X on a paired device  = forget (press X twice to confirm)
 *   A on a found device   = pair
 *   Y                     = search again
 *   B                     = back
 *
 * Android only lets system apps connect, disconnect or forget a device directly. Those actions are
 * tried first; if Lenovo's build refuses them, Android's Bluetooth settings open on the same screen.
 * Pairing new devices and turning Bluetooth on use public APIs and always work.
 */
@SuppressLint("MissingPermission")
class BluetoothHub(private val activity: Activity) {

    enum class Kind(val label: String) { CONTROLLER("Controller"), AUDIO("Audio"), KEYBOARD("Keyboard"), MOUSE("Mouse"), PHONE("Phone"), COMPUTER("Computer"), OTHER("Device") }

    data class Item(val device: BluetoothDevice, val name: String, val kind: Kind, val paired: Boolean, val connected: Boolean, val pairing: Boolean)

    var visible by mutableStateOf(false); private set
    var enabled by mutableStateOf(false); private set
    var scanning by mutableStateOf(false); private set
    var paired by mutableStateOf<List<Item>>(emptyList()); private set
    var found by mutableStateOf<List<Item>>(emptyList()); private set
    var selected by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null); private set
    var confirmForget by mutableStateOf<String?>(null); private set
    var hasPermission by mutableStateOf(false); private set

    private val manager = activity.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val connectedAddresses = mutableSetOf<String>()
    private val foundDevices = linkedMapOf<String, BluetoothDevice>()
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private var receiverRegistered = false

    /** 0 = power, 1 = search, then paired devices, then found devices. */
    val rowCount: Int get() = if (!hasPermission) 1 else 2 + paired.size + found.size

    fun open() {
        visible = true
        selected = 0
        message = null
        confirmForget = null
        hasPermission = checkPermission()
        if (!hasPermission) return
        start()
    }

    fun close() {
        visible = false
        stop()
    }

    private fun start() {
        registerReceiver()
        openProfileProxies()
        refresh()
        if (enabled && found.isEmpty()) startScan()
    }

    private fun stop() {
        runCatching { if (hasPermission) adapter?.cancelDiscovery() }
        scanning = false
        if (receiverRegistered) { runCatching { activity.unregisterReceiver(receiver) }; receiverRegistered = false }
        proxies.forEach { (profile, proxy) -> runCatching { adapter?.closeProfileProxy(profile, proxy) } }
        proxies.clear()
    }

    private fun checkPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    fun onPermissionResult() {
        hasPermission = checkPermission()
        if (hasPermission && visible) start()
        else if (!hasPermission) message = "Bluetooth access was not allowed. Allow it in the dock app's settings on the tablet."
    }

    /* ---------------- Controller ---------------- */

    fun handle(cmd: ControllerCommand) {
        val count = rowCount.coerceAtLeast(1)
        when (cmd) {
            ControllerCommand.UP, ControllerCommand.L1_PREV -> { selected = (selected - 1 + count) % count; confirmForget = null }
            ControllerCommand.DOWN, ControllerCommand.R1_NEXT -> { selected = (selected + 1) % count; confirmForget = null }
            ControllerCommand.B_BACK, ControllerCommand.START_MENU -> close()
            ControllerCommand.Y_DRAWER -> if (hasPermission) startScan()
            ControllerCommand.A_CONFIRM -> confirm()
            ControllerCommand.X_ACTION -> forgetSelected()
            else -> {}
        }
    }

    private fun confirm() {
        message = null
        if (!hasPermission) {
            activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), PERMISSION_REQUEST)
            return
        }
        when {
            selected == 0 -> togglePower()
            selected == 1 -> startScan()
            selected - 2 < paired.size -> toggleConnection(paired[selected - 2])
            else -> found.getOrNull(selected - 2 - paired.size)?.let { pair(it) }
        }
    }

    private fun togglePower() {
        val a = adapter ?: return
        val shizuku = ShizukuHelper.getStatus() == ShizukuStatus.READY
        if (shizuku) {
            ShizukuHelper.runCommand(activity, if (a.isEnabled) "cmd bluetooth_manager disable" else "cmd bluetooth_manager enable")
            message = if (a.isEnabled) "Turning Bluetooth off…" else "Turning Bluetooth on…"
        } else if (!a.isEnabled) {
            // Public "turn on Bluetooth?" prompt
            runCatching { activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        } else {
            openSystemSettings("Turn Bluetooth off in Android's Bluetooth settings (or set up Shizuku to do it from here).")
        }
    }

    private fun startScan() {
        val a = adapter ?: return
        if (!a.isEnabled) { message = "Turn Bluetooth on first."; return }
        foundDevices.clear()
        found = emptyList()
        runCatching { a.cancelDiscovery(); scanning = a.startDiscovery() }
        message = if (scanning) "Searching… put your controller or headphones in pairing mode." else "Couldn't start a search."
    }

    private fun pair(item: Item) {
        runCatching { adapter?.cancelDiscovery() }
        val ok = runCatching { item.device.createBond() }.getOrDefault(false)
        message = if (ok) "Pairing with ${item.name}… confirm on the device or tablet if asked." else "Couldn't start pairing with ${item.name}."
        refresh()
    }

    private fun toggleConnection(item: Item) {
        val method = if (item.connected) "disconnect" else "connect"
        val worked = runCatching {
            val result = BluetoothDevice::class.java.getMethod(method).invoke(item.device)
            result == true || result == BluetoothStatusCodes_SUCCESS
        }.getOrDefault(false)
        if (worked) {
            message = if (item.connected) "Disconnecting ${item.name}…" else "Connecting ${item.name}…"
            return
        }
        if (!item.connected && item.kind == Kind.CONTROLLER) {
            message = "Press the home or PS button on ${item.name} to reconnect it."
        } else {
            openSystemSettings(
                if (item.connected) "Android doesn't let this app disconnect devices. Tap ${item.name} in Bluetooth settings."
                else "Android doesn't let this app connect devices. Turn ${item.name} on, or tap it in Bluetooth settings."
            )
        }
    }

    private fun forgetSelected() {
        val item = paired.getOrNull(selected - 2) ?: return
        val address = item.device.address
        if (confirmForget != address) {
            confirmForget = address
            message = "Press X again to forget ${item.name}."
            return
        }
        confirmForget = null
        val worked = runCatching { BluetoothDevice::class.java.getMethod("removeBond").invoke(item.device) == true }.getOrDefault(false)
        if (worked) {
            message = "Forgot ${item.name}."
            refresh()
        } else {
            openSystemSettings("Android doesn't let this app forget devices. Choose ${item.name} → Forget in Bluetooth settings.")
        }
    }

    private fun openSystemSettings(why: String) {
        message = why
        val displayId = activity.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        ExternalDisplayHelper.launchIntentOnDisplay(activity, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), displayId)
    }

    /* ---------------- State ---------------- */

    private fun refresh() {
        val a = adapter
        enabled = a?.isEnabled == true
        if (!hasPermission || a == null) return
        proxies.values.forEach { p -> runCatching { p.connectedDevices.forEach { connectedAddresses.add(it.address) } } }
        val bonded = runCatching { a.bondedDevices.toList() }.getOrDefault(emptyList())
        paired = bonded.map { toItem(it) }.sortedWith(compareByDescending<Item> { it.connected }.thenBy { it.name.lowercase() })
        found = foundDevices.values.filter { d -> bonded.none { it.address == d.address } }.map { toItem(it) }
        selected = selected.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
    }

    private fun toItem(d: BluetoothDevice): Item {
        val connected = d.address in connectedAddresses ||
            runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(d) == true }.getOrDefault(false)
        return Item(
            device = d,
            name = runCatching { d.alias ?: d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: d.address,
            kind = kindOf(d),
            paired = d.bondState == BluetoothDevice.BOND_BONDED,
            connected = connected,
            pairing = d.bondState == BluetoothDevice.BOND_BONDING
        )
    }

    private fun kindOf(d: BluetoothDevice): Kind {
        val cls = runCatching { d.bluetoothClass }.getOrNull() ?: return Kind.OTHER
        return when (cls.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> Kind.AUDIO
            BluetoothClass.Device.Major.PHONE -> Kind.PHONE
            BluetoothClass.Device.Major.COMPUTER -> Kind.COMPUTER
            BluetoothClass.Device.Major.PERIPHERAL -> {
                val minor = cls.deviceClass and 0xFF
                when {
                    (minor and 0x0C) != 0 -> Kind.CONTROLLER // joystick / gamepad
                    (minor and 0x40) != 0 && (minor and 0x80) == 0 -> Kind.KEYBOARD
                    (minor and 0x80) != 0 -> Kind.MOUSE
                    else -> Kind.CONTROLLER
                }
            }
            else -> Kind.OTHER
        }
    }

    private fun openProfileProxies() {
        val a = adapter ?: return
        // A2DP, headset, HID host (controllers/keyboards), LE audio
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4, 22).forEach { profile ->
            runCatching {
                a.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        proxies[p] = proxy
                        activity.runOnUiThread { refresh() }
                    }
                    override fun onServiceDisconnected(p: Int) { proxies.remove(p) }
                }, profile)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> device?.let { foundDevices[it.address] = it }
                BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { connectedAddresses.add(it.address) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connectedAddresses.remove(it.address) }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    if (state == BluetoothDevice.BOND_BONDED) message = "Paired with ${runCatching { device?.alias ?: device?.name }.getOrNull() ?: "device"}."
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { scanning = false; if (message?.startsWith("Searching") == true) message = null }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (adapter?.isEnabled == true) { message = null; startScan() } else connectedAddresses.clear()
                }
            }
            refresh()
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        activity.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    companion object {
        const val PERMISSION_REQUEST = 4201
        private const val BluetoothStatusCodes_SUCCESS = 0
    }
}

/* ---------------- UI ---------------- */

@Composable
fun BluetoothPanel(hub: BluetoothHub, activeTheme: HubTheme = HubTheme.SWITCH_DARK) {
    val listState = rememberLazyListState()
    LaunchedEffect(hub.selected) { if (hub.rowCount > 0) listState.animateScrollToItem((hub.selected - 2).coerceAtLeast(0)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC06080D)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = activeTheme.surfaceModal,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.45f)),
            modifier = Modifier.width(580.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "BLUETOOTH",
                            color = activeTheme.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = "Controllers & Audio Devices",
                            color = activeTheme.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    val statusBadgeText = when {
                        !hub.hasPermission -> "PERMISSION"
                        hub.scanning -> "SCANNING…"
                        hub.enabled -> "ON"
                        else -> "OFF"
                    }
                    val statusColor = when {
                        !hub.hasPermission -> Color(0xFFF59E0B)
                        hub.scanning -> activeTheme.accent
                        hub.enabled -> Color(0xFF4ADE80)
                        else -> activeTheme.textSecondary
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = statusBadgeText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!hub.hasPermission) {
                    BtRow(
                        title = "Allow Bluetooth access",
                        subtitle = "Needed to list and pair devices",
                        badge = "GRANT",
                        selected = hub.selected == 0,
                        theme = activeTheme
                    )
                } else {
                    BtRow(
                        title = "Bluetooth Radio",
                        subtitle = if (hub.enabled) "Press A to turn off" else "Press A to turn on",
                        badge = if (hub.enabled) "ON" else "OFF",
                        selected = hub.selected == 0,
                        isPositive = hub.enabled,
                        theme = activeTheme
                    )
                    BtRow(
                        title = if (hub.scanning) "Searching for nearby devices…" else "Scan for Devices",
                        subtitle = "Put controller or headphones in pairing mode, then press A or Y",
                        badge = if (hub.scanning) "ACTIVE" else "SCAN",
                        selected = hub.selected == 1,
                        isPositive = hub.scanning,
                        theme = activeTheme
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hub.paired.isNotEmpty()) item { SectionLabel("PAIRED DEVICES", activeTheme) }
                        itemsIndexed(hub.paired) { i, d ->
                            val sel = hub.selected == i + 2
                            val actionHint = if (sel) {
                                if (d.connected) " • A disconnect • X forget" else " • A connect • X forget"
                            } else ""
                            BtRow(
                                title = d.name,
                                subtitle = "${d.kind.label}$actionHint",
                                badge = if (d.connected) "CONNECTED" else "PAIRED",
                                selected = sel,
                                isPositive = d.connected,
                                theme = activeTheme
                            )
                        }
                        if (hub.found.isNotEmpty()) item { SectionLabel("AVAILABLE TO PAIR", activeTheme) }
                        itemsIndexed(hub.found) { i, d ->
                            val sel = hub.selected == i + 2 + hub.paired.size
                            BtRow(
                                title = d.name,
                                subtitle = "${d.kind.label} • Press A to pair",
                                badge = if (d.pairing) "PAIRING…" else "PAIR",
                                selected = sel,
                                isPositive = d.pairing,
                                theme = activeTheme
                            )
                        }
                    }
                }

                hub.message?.let {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                Text(
                    text = "A Select  •  X Forget  •  Y Search  •  B Back",
                    color = activeTheme.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, theme: HubTheme) {
    Text(
        text = text,
        color = theme.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun BtRow(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    isPositive: Boolean = false,
    theme: HubTheme = HubTheme.SWITCH_DARK
) {
    Surface(
        color = if (selected) theme.focusGlow.copy(alpha = 0.30f) else theme.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) theme.accent else theme.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = if (isPositive && !selected) Color(0xFF4ADE80) else theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val badgeTint = if (isPositive) Color(0xFF4ADE80) else theme.textSecondary
            Surface(
                color = if (isPositive) Color(0xFF4ADE80).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    if (isPositive) Color(0xFF4ADE80).copy(alpha = 0.45f) else theme.borderSubtle
                )
            ) {
                Text(
                    text = badge,
                    color = badgeTint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
