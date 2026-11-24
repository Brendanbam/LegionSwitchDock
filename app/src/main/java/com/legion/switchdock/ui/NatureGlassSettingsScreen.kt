package com.legion.switchdock.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legion.switchdock.ControllerCommand
import com.legion.switchdock.data.DockHealthSnapshot
import com.legion.switchdock.data.HubTheme
import java.util.Locale

/**
 * Deterministic 2D focus targets for the full-canvas Nature Glass Settings Command Center.
 */
enum class SettingsFocusTarget {
    // Left Column — Row 0 (Volume Controls)
    VOL_DOWN,
    VOL_UP,
    VOL_MUTE,
    // Left Column — Row 1 (Power & Hub Actions)
    SCREENSAVER,
    SLEEP,
    EXIT_HUB,
    // Right Column — 2x4 Interactive Settings Grid
    BLUETOOTH,
    WIFI_BOOST,
    BYPASS_CHARGING,
    UI_SOUNDS,
    IDLE_SCREENSAVER,
    BLACKOUT_TABLET,
    DISPLAY_THEME,
    GLASSES_SIZE;

    /**
     * Returns the next focus target for a directional command, or null when UP should move
     * focus cleanly up to the Top Navigation Bar.
     */
    fun navigate(cmd: ControllerCommand): SettingsFocusTarget? = when (cmd) {
        ControllerCommand.LEFT -> when (this) {
            VOL_UP -> VOL_DOWN
            VOL_MUTE -> VOL_UP
            SLEEP -> SCREENSAVER
            EXIT_HUB -> SLEEP
            BLUETOOTH, BYPASS_CHARGING, IDLE_SCREENSAVER -> VOL_MUTE
            DISPLAY_THEME -> EXIT_HUB
            WIFI_BOOST -> BLUETOOTH
            UI_SOUNDS -> BYPASS_CHARGING
            BLACKOUT_TABLET -> IDLE_SCREENSAVER
            GLASSES_SIZE -> DISPLAY_THEME
            else -> this
        }
        ControllerCommand.RIGHT -> when (this) {
            VOL_DOWN -> VOL_UP
            VOL_UP -> VOL_MUTE
            VOL_MUTE -> BYPASS_CHARGING
            SCREENSAVER -> SLEEP
            SLEEP -> EXIT_HUB
            EXIT_HUB -> DISPLAY_THEME
            BLUETOOTH -> WIFI_BOOST
            BYPASS_CHARGING -> UI_SOUNDS
            IDLE_SCREENSAVER -> BLACKOUT_TABLET
            DISPLAY_THEME -> GLASSES_SIZE
            else -> this
        }
        ControllerCommand.UP -> when (this) {
            VOL_DOWN, VOL_UP, VOL_MUTE, BLUETOOTH, WIFI_BOOST -> null // Exit to TOP_BAR
            SCREENSAVER -> VOL_DOWN
            SLEEP -> VOL_UP
            EXIT_HUB -> VOL_MUTE
            BYPASS_CHARGING -> BLUETOOTH
            UI_SOUNDS -> WIFI_BOOST
            IDLE_SCREENSAVER -> BYPASS_CHARGING
            BLACKOUT_TABLET -> UI_SOUNDS
            DISPLAY_THEME -> IDLE_SCREENSAVER
            GLASSES_SIZE -> BLACKOUT_TABLET
        }
        ControllerCommand.DOWN -> when (this) {
            VOL_DOWN -> SCREENSAVER
            VOL_UP -> SLEEP
            VOL_MUTE -> EXIT_HUB
            BLUETOOTH -> BYPASS_CHARGING
            WIFI_BOOST -> UI_SOUNDS
            BYPASS_CHARGING -> IDLE_SCREENSAVER
            UI_SOUNDS -> BLACKOUT_TABLET
            IDLE_SCREENSAVER -> DISPLAY_THEME
            BLACKOUT_TABLET -> GLASSES_SIZE
            else -> this
        }
        ControllerCommand.L1_PREV -> entries[(ordinal - 1 + entries.size) % entries.size]
        ControllerCommand.R1_NEXT -> entries[(ordinal + 1) % entries.size]
        else -> this
    }
}

data class NatureSettingsState(
    val volumePct: Int,
    val isMuted: Boolean,
    val uiSoundsEnabled: Boolean,
    val idleDimmerEnabled: Boolean,
    val artemisWifiBoostEnabled: Boolean,
    val blackoutTabletOnDock: Boolean,
    val bypassChargingEnabled: Boolean,
    val shizukuReady: Boolean,
    val activeTheme: HubTheme,
    val glassesScreenSizePct: Int
)

/**
 * Full-canvas Nature Glass Settings & System Command Center.
 * Fits all live telemetry, media volume, power actions, and 8 core toggles/controls
 * cleanly with zero scroll clipping on Legion Y700, 1080p TV, and 4K TV.
 */
@Composable
fun NatureGlassSettingsWorkspace(
    health: DockHealthSnapshot,
    networkType: String,
    displaySummary: String,
    state: NatureSettingsState,
    focusedTarget: SettingsFocusTarget,
    isSettingsFocused: Boolean,
    isGlasses: Boolean,
    onAction: (SettingsFocusTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT COLUMN: Live Hardware Telemetry + Media Volume + Quick Power Actions
        Column(
            modifier = Modifier
                .weight(0.88f)
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live System Telemetry Card
            SystemTelemetryGlassCard(
                health = health,
                networkType = networkType,
                displaySummary = displaySummary,
                shizukuReady = state.shizukuReady,
                bypassActive = state.shizukuReady && state.bypassChargingEnabled,
                isGlasses = isGlasses,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.16f)
            )

            // 2. Hero Interactive Media Volume Card
            MediaVolumeGlassCard(
                volumePct = state.volumePct,
                isMuted = state.isMuted,
                focusedTarget = if (isSettingsFocused) focusedTarget else null,
                isGlasses = isGlasses,
                onVolDown = { onAction(SettingsFocusTarget.VOL_DOWN) },
                onVolUp = { onAction(SettingsFocusTarget.VOL_UP) },
                onMute = { onAction(SettingsFocusTarget.VOL_MUTE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.92f)
            )

            // 3. Quick Power & Hub Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PowerActionPillCard(
                    label = "Screensaver",
                    icon = Icons.Rounded.Bedtime,
                    accent = NatureGlassColors.EmeraldBright,
                    isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.SCREENSAVER,
                    isGlasses = isGlasses,
                    onClick = { onAction(SettingsFocusTarget.SCREENSAVER) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                PowerActionPillCard(
                    label = "Sleep",
                    icon = Icons.Rounded.PowerSettingsNew,
                    accent = Color(0xFF7DD3FC),
                    isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.SLEEP,
                    isGlasses = isGlasses,
                    onClick = { onAction(SettingsFocusTarget.SLEEP) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                PowerActionPillCard(
                    label = "Exit Hub",
                    icon = Icons.AutoMirrored.Rounded.ExitToApp,
                    accent = Color(0xFFFCA5A5),
                    isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.EXIT_HUB,
                    isGlasses = isGlasses,
                    onClick = { onAction(SettingsFocusTarget.EXIT_HUB) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        // RIGHT COLUMN: 2-Column x 4-Row Spacious Interactive Settings Deck
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.36f)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
        ) {
            val rowGap = 12.dp
            val colGap = 12.dp
            val cardHeight = ((maxHeight - rowGap * 3) / 4f).coerceAtLeast(94.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                // Row 0: Connectivity & Streaming
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(colGap)
                ) {
                    SettingsGlassActionCard(
                        title = "Bluetooth Devices",
                        subtitle = "Pair controllers & wireless audio",
                        icon = Icons.Rounded.Bluetooth,
                        badgeText = "OPEN",
                        badgeAccent = true,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.BLUETOOTH,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.BLUETOOTH) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    SettingsGlassToggleCard(
                        title = "Low-Latency Wi-Fi",
                        subtitle = "Boost Artemis & PC streams",
                        icon = Icons.Rounded.Speed,
                        checked = state.artemisWifiBoostEnabled,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.WIFI_BOOST,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.WIFI_BOOST) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Row 1: Power & Audio
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(colGap)
                ) {
                    if (state.shizukuReady) {
                        SettingsGlassToggleCard(
                            title = "Bypass Charging",
                            subtitle = "Power device directly while docked",
                            icon = Icons.Rounded.Bolt,
                            checked = state.bypassChargingEnabled,
                            isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.BYPASS_CHARGING,
                            isGlasses = isGlasses,
                            onClick = { onAction(SettingsFocusTarget.BYPASS_CHARGING) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        SettingsGlassActionCard(
                            title = "Bypass Charging",
                            subtitle = "Open Lenovo Game Assistant",
                            icon = Icons.Rounded.Bolt,
                            badgeText = "SETUP",
                            badgeAccent = false,
                            isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.BYPASS_CHARGING,
                            isGlasses = isGlasses,
                            onClick = { onAction(SettingsFocusTarget.BYPASS_CHARGING) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    SettingsGlassToggleCard(
                        title = "UI Sounds",
                        subtitle = "Console clicks & menu chimes",
                        icon = Icons.Rounded.GraphicEq,
                        checked = state.uiSoundsEnabled,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.UI_SOUNDS,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.UI_SOUNDS) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Row 2: Dock Behavior & Screensaver
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(colGap)
                ) {
                    SettingsGlassToggleCard(
                        title = "Idle Screensaver",
                        subtitle = "Dim to ambient clock after 5 min",
                        icon = Icons.Rounded.AutoAwesome,
                        checked = state.idleDimmerEnabled,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.IDLE_SCREENSAVER,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.IDLE_SCREENSAVER) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    SettingsGlassToggleCard(
                        title = "Tablet Blackout",
                        subtitle = "Keep tablet screen dark on TV",
                        icon = Icons.Rounded.PhonelinkErase,
                        checked = state.blackoutTabletOnDock,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.BLACKOUT_TABLET,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.BLACKOUT_TABLET) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Row 3: Display & XR Customization
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(colGap)
                ) {
                    SettingsGlassActionCard(
                        title = "Display Theme",
                        subtitle = "Overlay & cursor accent",
                        icon = Icons.Rounded.Palette,
                        badgeText = state.activeTheme.displayName,
                        badgeAccent = true,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.DISPLAY_THEME,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.DISPLAY_THEME) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    SettingsGlassActionCard(
                        title = "Glasses Size",
                        subtitle = "App window scale in XR optics",
                        icon = Icons.Rounded.ViewInAr,
                        badgeText = "${state.glassesScreenSizePct}%",
                        badgeAccent = true,
                        isFocused = isSettingsFocused && focusedTarget == SettingsFocusTarget.GLASSES_SIZE,
                        isGlasses = isGlasses,
                        onClick = { onAction(SettingsFocusTarget.GLASSES_SIZE) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemTelemetryGlassCard(
    health: DockHealthSnapshot,
    networkType: String,
    displaySummary: String,
    shizukuReady: Boolean,
    bypassActive: Boolean,
    isGlasses: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val batteryColor = when {
        health.batteryPercent <= 20 && !health.isCharging -> Color(0xFFFF6B6B)
        health.batteryPercent <= 35 && !health.isCharging -> Color(0xFFFBBF24)
        else -> NatureGlassColors.EmeraldBright
    }
    val tempColor = if (health.isThermalWarning) Color(0xFFFBBF24) else NatureGlassColors.EmeraldBright
    val isOffline = networkType == "Offline"
    val isEthernet = networkType == "Ethernet"

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isGlasses) {
                        listOf(Color(0xD9071A13), Color(0xE004120D))
                    } else {
                        listOf(Color(0xCC07261A), Color(0xD4041710))
                    }
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0x66B8FFDC), Color(0x2E47FFA3), Color(0x40FFFFFF))
                ),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header row: System Status + Display Mode badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NatureGlassColors.EmeraldBright)
                    )
                    Text(
                        text = "SYSTEM STATUS",
                        color = NatureGlassColors.EmeraldBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp
                    )
                }
                Surface(
                    color = Color(0x660B3524),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0x456AFFB5))
                ) {
                    Text(
                        text = displaySummary,
                        color = NatureGlassColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Center hero row: Circular Battery Gauge + Power / Temp / Network stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Circular Battery Ring Gauge
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        // Track
                        drawArc(
                            color = Color.White.copy(alpha = 0.12f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // Luminous progress arc
                        val sweep = (health.batteryPercent.coerceIn(0, 100) / 100f) * 360f
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(batteryColor, Color(0xFFB8FFE0), batteryColor)
                            ),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${health.batteryPercent}%",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (health.isCharging) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Charging",
                                tint = NatureGlassColors.EmeraldBright,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Telemetry stat lines
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TelemetryStatRow(
                        icon = Icons.Rounded.Bolt,
                        iconTint = if (health.isCharging || bypassActive) NatureGlassColors.EmeraldBright else NatureGlassColors.TextMuted,
                        label = "Power",
                        value = when {
                            bypassActive -> "Bypass Active"
                            health.isCharging -> "Charging • ${health.wattsEstimate}W"
                            else -> "Discharging"
                        },
                        valueColor = if (health.isCharging || bypassActive) NatureGlassColors.EmeraldBright else NatureGlassColors.TextPrimary
                    )
                    TelemetryStatRow(
                        icon = Icons.Rounded.Thermostat,
                        iconTint = tempColor,
                        label = "Temp",
                        value = "${String.format(Locale.US, "%.1f", health.temperatureC)}°C",
                        valueColor = tempColor
                    )
                    TelemetryStatRow(
                        icon = when {
                            isOffline -> Icons.Rounded.WifiOff
                            isEthernet -> Icons.Rounded.Lan
                            else -> Icons.Rounded.Wifi
                        },
                        iconTint = if (isOffline) Color(0xFFFF6B6B) else NatureGlassColors.EmeraldBright,
                        label = "Network",
                        value = networkType,
                        valueColor = if (isOffline) Color(0xFFFF8A8A) else NatureGlassColors.TextPrimary
                    )
                }
            }

            // Bottom controller & Shizuku strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ctrlText = when {
                    health.controllers.isEmpty() -> "No Controller Connected"
                    health.controllers.size == 1 -> {
                        val c = health.controllers.first()
                        val bat = c.batteryPercent?.let { " • $it%" }.orEmpty()
                        "${c.name.take(18)}$bat"
                    }
                    else -> "${health.controllers.size} Controllers Connected"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = if (health.controllers.isNotEmpty()) NatureGlassColors.EmeraldBright else NatureGlassColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = ctrlText,
                        color = if (health.controllers.isNotEmpty()) NatureGlassColors.TextPrimary else NatureGlassColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (shizukuReady) Color(0x3828E088) else Color(0x28FFFFFF),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (shizukuReady) Color(0x705CFFB0) else Color.White.copy(alpha = 0.14f)
                    )
                ) {
                    Text(
                        text = if (shizukuReady) "SHIZUKU" else "STANDARD",
                        color = if (shizukuReady) NatureGlassColors.EmeraldBright else NatureGlassColors.TextMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                color = NatureGlassColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaVolumeGlassCard(
    volumePct: Int,
    isMuted: Boolean,
    focusedTarget: SettingsFocusTarget?,
    isGlasses: Boolean,
    onVolDown: () -> Unit,
    onVolUp: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val animatedFill by animateFloatAsState(
        targetValue = if (isMuted) 0f else (volumePct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(160),
        label = "volumeBarFill"
    )
    val hasChildFocus = focusedTarget == SettingsFocusTarget.VOL_DOWN ||
        focusedTarget == SettingsFocusTarget.VOL_UP ||
        focusedTarget == SettingsFocusTarget.VOL_MUTE

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isGlasses) {
                        listOf(Color(0xD9071A13), Color(0xE004120D))
                    } else {
                        listOf(Color(0xCC07261A), Color(0xD4041710))
                    }
                )
            )
            .border(
                width = if (hasChildFocus) 1.5.dp else 1.2.dp,
                brush = Brush.linearGradient(
                    if (hasChildFocus) {
                        listOf(Color(0x99A6FFD5), Color(0x5547FFA3))
                    } else {
                        listOf(Color(0x55B8FFDC), Color(0x2847FFA3))
                    }
                ),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted || volumePct == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = if (isMuted) Color(0xFFFF6B6B) else NatureGlassColors.EmeraldBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Media Volume",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isMuted) "MUTED" else "$volumePct%",
                    color = if (isMuted) Color(0xFFFF6B6B) else NatureGlassColors.EmeraldBright,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Glowing emerald volume track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                if (animatedFill > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFill)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        NatureGlassColors.EmeraldMid,
                                        NatureGlassColors.EmeraldBright,
                                        Color(0xFFD8FFEC)
                                    )
                                )
                            )
                    )
                }
            }

            // 3 tactile focusable buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VolumeControlButton(
                    label = "Down",
                    icon = Icons.Rounded.Remove,
                    isFocused = focusedTarget == SettingsFocusTarget.VOL_DOWN,
                    onClick = onVolDown,
                    modifier = Modifier.weight(1f)
                )
                VolumeControlButton(
                    label = "Up",
                    icon = Icons.Rounded.Add,
                    isFocused = focusedTarget == SettingsFocusTarget.VOL_UP,
                    onClick = onVolUp,
                    modifier = Modifier.weight(1f)
                )
                VolumeControlButton(
                    label = if (isMuted) "Unmute" else "Mute",
                    icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    isFocused = focusedTarget == SettingsFocusTarget.VOL_MUTE,
                    highlightTint = if (isMuted) Color(0xFFFF6B6B) else null,
                    onClick = onMute,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VolumeControlButton(
    label: String,
    icon: ImageVector,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightTint: Color? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.045f else 1.0f,
        animationSpec = tween(140),
        label = "volBtnScale"
    )
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(42.dp)
            .scale(scale)
            .drawBehind {
                if (isFocused) {
                    drawSettingsGlow(
                        color = highlightTint ?: NatureGlassColors.EmeraldBright,
                        alpha = 0.72f,
                        cornerRadiusPx = 12.dp.toPx(),
                        blurRadiusPx = 14.dp.toPx()
                    )
                }
            }
            .clip(shape)
            .background(
                when {
                    isFocused -> Brush.horizontalGradient(
                        listOf(Color(0xE60F4C33), Color(0xE60A3624))
                    )
                    highlightTint != null -> Brush.horizontalGradient(
                        listOf(highlightTint.copy(alpha = 0.22f), highlightTint.copy(alpha = 0.14f))
                    )
                    else -> Brush.horizontalGradient(
                        listOf(Color(0x700B2E20), Color(0x80072016))
                    )
                }
            )
            .border(
                width = if (isFocused) 2.2.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.horizontalGradient(
                        listOf(Color.White, highlightTint ?: NatureGlassColors.EmeraldBright, Color.White)
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.12f))
                    )
                },
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.White else (highlightTint ?: NatureGlassColors.TextSecondary),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (isFocused) Color.White else (highlightTint ?: NatureGlassColors.TextPrimary),
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PowerActionPillCard(
    label: String,
    icon: ImageVector,
    accent: Color,
    isFocused: Boolean,
    isGlasses: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.045f else 1.0f,
        animationSpec = tween(140),
        label = "powerActionScale"
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .scale(scale)
            .drawBehind {
                if (isFocused) {
                    drawSettingsGlow(
                        color = accent,
                        alpha = 0.78f,
                        cornerRadiusPx = 16.dp.toPx(),
                        blurRadiusPx = 18.dp.toPx()
                    )
                }
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = when {
                        isFocused -> listOf(Color(0xE00C3E2A), Color(0xE607271A))
                        isGlasses -> listOf(Color(0xD9071A13), Color(0xE004120D))
                        else -> listOf(Color(0xC708251A), Color(0xD4051710))
                    }
                )
            )
            .border(
                width = if (isFocused) 2.4.dp else 1.1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(listOf(Color.White, accent, Color.White))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), accent.copy(alpha = 0.20f)))
                },
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.White else accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsGlassToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    isFocused: Boolean,
    isGlasses: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsGlassCardShell(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconHighlighted = checked || isFocused,
        isFocused = isFocused,
        isGlasses = isGlasses,
        onClick = onClick,
        modifier = modifier
    ) {
        NatureGlassSwitch(checked = checked, isFocused = isFocused)
    }
}

@Composable
private fun SettingsGlassActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    badgeAccent: Boolean,
    isFocused: Boolean,
    isGlasses: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsGlassCardShell(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconHighlighted = badgeAccent || isFocused,
        isFocused = isFocused,
        isGlasses = isGlasses,
        onClick = onClick,
        modifier = modifier
    ) {
        Surface(
            color = when {
                isFocused -> Color(0xF0E6FFF0)
                badgeAccent -> Color(0x3838F29A)
                else -> Color(0x28FFFFFF)
            },
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                width = 1.1.dp,
                color = when {
                    isFocused -> Color.White
                    badgeAccent -> NatureGlassColors.EmeraldBright.copy(alpha = 0.65f)
                    else -> Color.White.copy(alpha = 0.24f)
                }
            )
        ) {
            Text(
                text = badgeText,
                color = when {
                    isFocused -> NatureGlassColors.OpenBtnText
                    badgeAccent -> NatureGlassColors.EmeraldBright
                    else -> NatureGlassColors.TextSecondary
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SettingsGlassCardShell(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconHighlighted: Boolean,
    isFocused: Boolean,
    isGlasses: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.024f else 1.0f,
        animationSpec = tween(140),
        label = "settingsCardScale"
    )
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .drawBehind {
                if (isFocused) {
                    drawSettingsGlow(
                        color = NatureGlassColors.EmeraldBright,
                        alpha = 0.78f,
                        cornerRadiusPx = 18.dp.toPx(),
                        blurRadiusPx = 20.dp.toPx()
                    )
                    drawSettingsGlow(
                        color = Color.White,
                        alpha = 0.36f,
                        cornerRadiusPx = 18.dp.toPx(),
                        blurRadiusPx = 8.dp.toPx()
                    )
                }
            }
            .clip(shape)
            .clickable { onClick() }
    ) {
        // Translucent dark-emerald glass + flowing emerald wave matching Home cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bg = when {
                isFocused -> listOf(Color(0xDB083A26), Color(0xE0052619), Color(0xD40A422B))
                isGlasses -> listOf(Color(0xD6071A13), Color(0xDE04120D))
                else -> listOf(Color(0xBF09261B), Color(0xCF051811))
            }
            drawRect(Brush.linearGradient(bg, start = Offset.Zero, end = Offset(w, h)))

            // Subtle sweeping bottom emerald wave
            val wave = Path().apply {
                moveTo(0f, h * 0.84f)
                cubicTo(w * 0.35f, h * 0.62f, w * 0.70f, h * 0.94f, w, h * 0.56f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = wave,
                brush = Brush.horizontalGradient(
                    if (isFocused) {
                        listOf(Color(0x1E3AF29B), Color(0x5245FFA6), Color(0x36127A4D))
                    } else {
                        listOf(Color(0x10248A5E), Color(0x1E2FBF7E), Color(0x120B3B27))
                    }
                )
            )
        }

        // Crisp border stroke
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isFocused) 2.5.dp else 1.1.dp,
                    brush = if (isFocused) {
                        Brush.linearGradient(
                            listOf(Color.White, Color(0xFFC2FFE2), NatureGlassColors.EmeraldBright, Color.White)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.24f), Color(0x385AE8A8))
                        )
                    },
                    shape = shape
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Luminous glass icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFocused) {
                            Brush.linearGradient(listOf(Color(0x555CFFB0), Color(0x3820C978)))
                        } else if (iconHighlighted) {
                            Brush.linearGradient(listOf(Color(0x3647FFA3), Color(0x1F188F56)))
                        } else {
                            Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x12FFFFFF)))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isFocused) Color(0xAAFFFFFF) else if (iconHighlighted) Color(0x665CFFB0) else Color.White.copy(alpha = 0.16f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isFocused) Color.White else if (iconHighlighted) NatureGlassColors.EmeraldBright else NatureGlassColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Title & subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (isFocused) NatureGlassColors.TextSecondary else NatureGlassColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            trailing()
        }
    }
}

/**
 * Tactile glowing emerald pill toggle switch designed for 10-foot TV legibility.
 */
@Composable
private fun NatureGlassSwitch(
    checked: Boolean,
    isFocused: Boolean
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> Color(0xFF28D984)
            isFocused -> Color(0x66163C2B)
            else -> Color(0x4D0D261C)
        },
        animationSpec = tween(160),
        label = "switchTrack"
    )
    val thumbOffset: Dp by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = tween(160),
        label = "switchThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(
                width = 1.2.dp,
                color = if (checked) Color(0xFFB8FFE0) else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Color(0xFFD0E4D8))
        )
    }
}

private fun DrawScope.drawSettingsGlow(
    color: Color,
    alpha: Float,
    cornerRadiusPx: Float,
    blurRadiusPx: Float
) {
    val steps = 5
    for (i in steps downTo 1) {
        val fraction = i.toFloat() / steps.toFloat()
        val spread = blurRadiusPx * fraction * 0.65f
        val layerAlpha = (alpha * (1f - fraction * 0.78f) * 0.34f).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius(cornerRadiusPx + spread, cornerRadiusPx + spread)
        )
    }
}
