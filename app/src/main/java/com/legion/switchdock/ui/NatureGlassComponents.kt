package com.legion.switchdock.ui

import android.content.pm.ApplicationInfo
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legion.switchdock.data.AppEntry
import com.legion.switchdock.data.AppRepository
import com.legion.switchdock.data.DockHealthSnapshot
import com.legion.switchdock.streaming.ArtemisBoostManager
import java.util.Locale
import kotlin.math.abs

enum class TopNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    APPS("Apps", Icons.Rounded.GridView),
    GAMES("Games", Icons.Rounded.SportsEsports),
    MEDIA("Media", Icons.Rounded.Slideshow),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

enum class HubFocusZone {
    TOP_BAR,
    FEATURED_OPEN,
    APP_GRID
}

sealed class NatureGridItem {
    data class App(val entry: AppEntry) : NatureGridItem()
    data object AllAppsTile : NatureGridItem()
}

object NatureGlassColors {
    val EmeraldGlow = Color(0xFF47FFA3)
    val EmeraldBright = Color(0xFF5CFFB0)
    val EmeraldMid = Color(0xFF22C97D)
    val EmeraldDeep = Color(0xFF0A2E20)
    val ForestDark = Color(0xFF061B13)
    val GlassFillTop = Color(0xA608291C)
    val GlassFillBottom = Color(0xBA04160F)
    val CardBorderIdle = Color(0x52B8FFD9)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFE0F2E8)
    val TextMuted = Color(0xFFA7C9B8)
    val OpenBtnStart = Color(0xFFF4FFF8)
    val OpenBtnEnd = Color(0xFFCFFFE4)
    val OpenBtnText = Color(0xFF08291A)
    val BadgeA = Color(0xFF28C85A)
    val BadgeX = Color(0xFF1E8EE0)
    val BadgeY = Color(0xFFD6A419)
    val BadgeB = Color(0xFFD93B3D)
}

/**
 * Computes a crisp, honest 1-2 line subtitle for the featured panel based on real app metadata.
 */
fun describeFeaturedItem(item: NatureGridItem?, allAppsCount: Int, isPinned: Boolean): String {
    if (item == null) {
        return "Press Y to browse your installed applications or switch categories above."
    }
    if (item is NatureGridItem.AllAppsTile) {
        return "Browse all $allAppsCount installed apps on your device and pin favorites to Home."
    }
    val app = (item as NatureGridItem.App).entry
    val lowerLabel = app.label.lowercase()
    val pkg = app.packageName.lowercase()
    return when {
        app.packageName == AppRepository.PLAY_PC_ID ->
            "Resume your low-latency PC game stream directly on the big screen."
        app.packageName == AppRepository.ARTEMIS_LEGION_PACKAGE || lowerLabel.contains("artemis") ->
            "A modern, low-latency game streaming launcher for your PC games."
        pkg.startsWith("com.limelight") || lowerLabel.contains("moonlight") ->
            "Stream your PC games with high frame rates and responsive controller support."
        pkg.contains("steamlink") || lowerLabel.contains("steam") ->
            "Connect to your Steam library and play PC games on your TV."
        pkg.contains("retroarch") || lowerLabel.contains("retroarch") ->
            "Multi-system classic game launcher and emulator frontend."
        pkg.contains("youtube.music") || lowerLabel.contains("yt music") || pkg.contains("spotify") ->
            "Stream music, albums, and playlists on the big screen."
        pkg.contains("youtube") || lowerLabel.contains("youtube") ->
            "Watch videos, channels, and live streams on your TV."
        pkg.contains("netflix") || pkg.contains("plex") || pkg.contains("stremio") ||
            pkg.contains("jellyfin") || pkg.contains("crunchyroll") || pkg.contains("disney") ->
            "Stream movies, series, and media on the big screen."
        pkg.contains("vlc") || lowerLabel.contains("vlc") ->
            "Play local and network video or audio media."
        ArtemisBoostManager.isStreamingPackage(app.packageName) ->
            "Low-latency streaming app ready for TV playback."
        app.category == ApplicationInfo.CATEGORY_GAME ->
            "Installed game ready to play on your TV."
        app.category == ApplicationInfo.CATEGORY_VIDEO ->
            "Video & streaming app ready for full-screen TV playback."
        app.category == ApplicationInfo.CATEGORY_AUDIO ->
            "Music & audio app ready for big-screen listening."
        isPinned ->
            "Pinned to your Home dock. Press Open to launch or X to unpin."
        else ->
            "Installed app ready to launch on your TV. Press X to pin to Home."
    }
}

/**
 * Stylized geometric Legion-inspired split-Y emblem matching the top-left concept mark.
 */
@Composable
fun LegionEmblemLogo(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Left sweeping wing
        val leftWing = Path().apply {
            moveTo(w * 0.12f, h * 0.12f)
            lineTo(w * 0.36f, h * 0.25f)
            lineTo(w * 0.45f, h * 0.50f)
            lineTo(w * 0.45f, h * 0.88f)
            lineTo(w * 0.30f, h * 0.74f)
            lineTo(w * 0.30f, h * 0.54f)
            close()
        }
        // Right sweeping wing
        val rightWing = Path().apply {
            moveTo(w * 0.88f, h * 0.12f)
            lineTo(w * 0.64f, h * 0.25f)
            lineTo(w * 0.55f, h * 0.50f)
            lineTo(w * 0.55f, h * 0.88f)
            lineTo(w * 0.70f, h * 0.74f)
            lineTo(w * 0.70f, h * 0.54f)
            close()
        }
        // Center diamond crest
        val centerCrest = Path().apply {
            moveTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.58f, h * 0.29f)
            lineTo(w * 0.50f, h * 0.42f)
            lineTo(w * 0.42f, h * 0.29f)
            close()
        }
        drawPath(leftWing, color = tint)
        drawPath(rightWing, color = tint)
        drawPath(centerCrest, color = tint.copy(alpha = 0.92f))
    }
}

/**
 * Custom horizontal battery indicator matching the crisp green-filled battery icon in the concept.
 */
@Composable
fun NatureBatteryIndicator(
    percent: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val fillColor = when {
        percent <= 20 && !isCharging -> Color(0xFFFF5C5C)
        percent <= 35 && !isCharging -> Color(0xFFFFC145)
        else -> NatureGlassColors.EmeraldBright
    }
    Canvas(modifier = modifier.size(width = 30.dp, height = 16.dp)) {
        val bodyW = size.width * 0.87f
        val bodyH = size.height
        val strokeW = 1.7.dp.toPx()
        val corner = 3.5.dp.toPx()

        // Outer shell
        drawRoundRect(
            color = Color.White.copy(alpha = 0.90f),
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(bodyW - strokeW, bodyH - strokeW),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeW)
        )
        // Positive battery terminal cap on the right
        val capW = size.width * 0.10f
        val capH = bodyH * 0.42f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.90f),
            topLeft = Offset(bodyW + 1.dp.toPx(), (bodyH - capH) / 2f),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
        // Real proportional charge bar
        val pad = strokeW + 1.6.dp.toPx()
        val innerMaxW = (bodyW - pad * 2f).coerceAtLeast(0f)
        val fillW = innerMaxW * (percent.coerceIn(0, 100) / 100f)
        if (fillW > 0.5f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(pad, pad),
                size = Size(fillW, (bodyH - pad * 2f).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
            )
        }
        // Charging bolt accent
        if (isCharging) {
            val cx = bodyW * 0.50f
            val bolt = Path().apply {
                moveTo(cx + 1.5.dp.toPx(), bodyH * 0.18f)
                lineTo(cx - 2.6.dp.toPx(), bodyH * 0.54f)
                lineTo(cx + 0.4.dp.toPx(), bodyH * 0.54f)
                lineTo(cx - 1.5.dp.toPx(), bodyH * 0.84f)
                lineTo(cx + 2.8.dp.toPx(), bodyH * 0.46f)
                lineTo(cx - 0.3.dp.toPx(), bodyH * 0.46f)
                close()
            }
            drawPath(bolt, color = Color(0xFF041A10))
        }
    }
}

@Composable
fun NatureGlassTopBar(
    activeTab: TopNavTab,
    focusedTabIndex: Int,
    isTopBarFocused: Boolean,
    isGlasses: Boolean,
    currentTime: String,
    networkType: String,
    health: DockHealthSnapshot,
    thermalWarningEnabled: Boolean,
    glassesBatteryWarning: Boolean,
    onSelectTab: (TopNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val textShadow = Shadow(
        color = Color(0xB8000000),
        offset = Offset(0f, 1.5f),
        blurRadius = 6f
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Legion Switch Dock Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LegionEmblemLogo(
                    modifier = Modifier.size(34.dp),
                    tint = Color.White
                )
                Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
                    Text(
                        text = "LEGION",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.0.sp,
                        style = TextStyle(shadow = textShadow),
                        maxLines = 1
                    )
                    Text(
                        text = if (isGlasses) "XR SWITCH DOCK" else "SWITCH DOCK",
                        color = Color(0xFFE0F2E8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.1.sp,
                        style = TextStyle(shadow = textShadow),
                        maxLines = 1
                    )
                }
            }

            // Subtle vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            )

            // Middle: Navigation tabs (Home, Apps, Games, Media, Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopNavTab.entries.forEachIndexed { idx, tab ->
                    val isActive = tab == activeTab
                    val isFocused = isTopBarFocused && idx == focusedTabIndex
                    NatureNavTabItem(
                        tab = tab,
                        isActive = isActive,
                        isFocused = isFocused,
                        onClick = { onSelectTab(tab) }
                    )
                }
            }

            // Subtle vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            )

            // Right: Real clock, connection, battery, and controller status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                // Clock with crisp AM/PM hierarchy matching "10:24 PM"
                val timeParts = currentTime.trim().split(" ")
                val clockDisplay = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(timeParts.firstOrNull().orEmpty())
                    }
                    if (timeParts.size > 1) {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFFDDEEE4),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(" ${timeParts.drop(1).joinToString(" ")}")
                        }
                    }
                }
                Text(
                    text = clockDisplay,
                    style = TextStyle(shadow = textShadow),
                    maxLines = 1
                )

                // Connection status icon (real ConnectivityManager state)
                val isOffline = networkType == "Offline"
                val isEthernet = networkType == "Ethernet"
                val isLowLatency = networkType.contains("Low-Latency", ignoreCase = true)
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = when {
                            isOffline -> Icons.Rounded.WifiOff
                            isEthernet -> Icons.Rounded.Lan
                            else -> Icons.Rounded.Wifi
                        },
                        contentDescription = networkType,
                        tint = if (isOffline) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(22.dp)
                    )
                    if (isLowLatency && !isOffline) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NatureGlassColors.EmeraldBright)
                        )
                    }
                }

                // Battery icon (real BatteryManager level + charging state)
                NatureBatteryIndicator(
                    percent = health.batteryPercent,
                    isCharging = health.isCharging
                )

                // Controller indicator (real InputManager gamepad detection)
                val hasController = health.controllers.isNotEmpty()
                val firstPadBattery = health.controllers.firstOrNull()?.batteryPercent
                val padTint = when {
                    !hasController -> Color.White.copy(alpha = 0.36f)
                    firstPadBattery != null && firstPadBattery <= 20 -> Color(0xFFFF6B6B)
                    else -> Color.White.copy(alpha = 0.96f)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = if (hasController) "Controller Connected" else "No Controller",
                        tint = padTint,
                        modifier = Modifier.size(25.dp)
                    )
                    if (health.controllers.size > 1) {
                        Text(
                            text = "${health.controllers.size}",
                            color = NatureGlassColors.EmeraldBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Preserve important real hardware safety warnings ONLY when triggered
        val lowPad = health.controllers.firstOrNull { (it.batteryPercent ?: 100) <= 20 }
        val showThermal = health.isThermalWarning && thermalWarningEnabled
        val showGlassesLowBat = isGlasses && glassesBatteryWarning && !health.isCharging && health.batteryPercent <= 20
        if (lowPad != null || showThermal || showGlassesLowBat) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lowPad != null) {
                    CompactAlertPill(
                        text = "Controller Low (${lowPad.batteryPercent}%)",
                        icon = Icons.Rounded.Gamepad,
                        color = Color(0xFFFF6B6B)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (showThermal) {
                    CompactAlertPill(
                        text = "${String.format(Locale.US, "%.1f", health.temperatureC)}°C • Bypass Recommended",
                        icon = Icons.Rounded.Thermostat,
                        color = Color(0xFFFBBF24)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (showGlassesLowBat) {
                    CompactAlertPill(
                        text = "Tablet ${health.batteryPercent}%",
                        icon = Icons.Rounded.Thermostat,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAlertPill(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = Color(0xB8091A13),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NatureNavTabItem(
    tab: TopNavTab,
    isActive: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(50)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(140),
        label = "navTabScale"
    )
    val showPill = isActive || isFocused
    Box(
        modifier = Modifier
            .scale(scale)
            .drawBehind {
                if (isActive || isFocused) {
                    drawGlowRoundedRect(
                        color = if (isFocused) NatureGlassColors.EmeraldBright else NatureGlassColors.EmeraldGlow,
                        alpha = if (isFocused) 0.55f else 0.36f,
                        cornerRadiusPx = size.height / 2f,
                        blurRadiusPx = if (isFocused) 16.dp.toPx() else 12.dp.toPx()
                    )
                }
            }
            .clip(pillShape)
            .background(
                when {
                    isFocused -> Brush.horizontalGradient(
                        listOf(Color(0xCC124C34), Color(0xCC0C3826))
                    )
                    isActive -> Brush.horizontalGradient(
                        listOf(Color(0xAD0D3E29), Color(0xAD082B1D))
                    )
                    else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .then(
                if (showPill) {
                    Modifier.border(
                        width = if (isFocused) 2.dp else 1.5.dp,
                        brush = Brush.horizontalGradient(
                            if (isFocused) {
                                listOf(Color.White, NatureGlassColors.EmeraldBright, Color.White)
                            } else {
                                listOf(
                                    NatureGlassColors.EmeraldBright.copy(alpha = 0.85f),
                                    Color(0xFFB5FFE0).copy(alpha = 0.90f),
                                    NatureGlassColors.EmeraldBright.copy(alpha = 0.85f)
                                )
                            }
                        ),
                        shape = pillShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = if (showPill) 16.dp else 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (isActive || isFocused) Color.White else Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = tab.label,
                color = if (isActive || isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                fontSize = 14.5.sp,
                fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xAA000000),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 5f
                    )
                )
            )
        }
    }
}

/**
 * Organic liquid-leaf glass silhouette matching the left featured panel in the Nature Glass concept.
 */
private fun buildOrganicFeaturedPath(w: Float, h: Float): Path {
    return Path().apply {
        // Start upper-left shoulder
        moveTo(w * 0.08f, h * 0.06f)
        // Gently rising then dipping organic top wave toward the upper-right rounded corner
        cubicTo(
            w * 0.30f, -h * 0.02f,
            w * 0.62f, h * 0.14f,
            w * 0.91f, h * 0.09f
        )
        // Smooth right-top corner
        cubicTo(
            w * 0.985f, h * 0.08f,
            w * 1.00f, h * 0.16f,
            w * 1.00f, h * 0.27f
        )
        // Right edge down to soft bottom-right corner
        lineTo(w * 1.00f, h * 0.84f)
        cubicTo(
            w * 1.00f, h * 0.95f,
            w * 0.94f, h * 0.995f,
            w * 0.84f, h * 0.99f
        )
        // Sweeping organic lower glass wave across the bottom
        cubicTo(
            w * 0.62f, h * 0.98f,
            w * 0.44f, h * 0.91f,
            w * 0.14f, h * 0.955f
        )
        // Smooth bottom-left corner
        cubicTo(
            w * 0.04f, h * 0.97f,
            0f, h * 0.89f,
            0f, h * 0.76f
        )
        // Left edge rising smoothly back to upper-left shoulder
        lineTo(0f, h * 0.25f)
        cubicTo(
            0f, h * 0.13f,
            w * 0.03f, h * 0.08f,
            w * 0.08f, h * 0.06f
        )
        close()
    }
}

private val OrganicFeaturedShape = GenericShape { size, _ ->
    addPath(buildOrganicFeaturedPath(size.width, size.height))
}

@Composable
fun NatureGlassFeaturedPanel(
    featuredItem: NatureGridItem?,
    allAppsCount: Int,
    isPinned: Boolean,
    isOpenButtonFocused: Boolean,
    isGlasses: Boolean,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openScale by animateFloatAsState(
        targetValue = if (isOpenButtonFocused) 1.055f else 1.0f,
        animationSpec = tween(150),
        label = "openBtnScale"
    )
    val titleShadow = Shadow(
        color = Color(0xCC02100A),
        offset = Offset(0f, 3f),
        blurRadius = 10f
    )
    val bodyShadow = Shadow(
        color = Color(0xCC02100A),
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(OrganicFeaturedShape)
    ) {
        // Translucent organic dark-green glass surface + refractive rim highlights
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val path = buildOrganicFeaturedPath(w, h)

            // Frosted emerald-forest translucent gradient (darker on left for text contrast,
            // clearer on lower-right so the waterfall & pine trees glow through the glass)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = if (isGlasses) {
                        listOf(Color(0xD6081A14), Color(0xB806140F), Color(0x9604100C))
                    } else {
                        listOf(
                            Color(0xB8062418),
                            Color(0x8C082A1C),
                            Color(0x4A0A2A1E),
                            Color(0x2E061B13)
                        )
                    },
                    start = Offset(0f, h * 0.15f),
                    end = Offset(w * 0.98f, h * 0.85f)
                )
            )

            // Soft radial dark-emerald readability pool behind title & description
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x88031A11), Color(0x3D041C12), Color.Transparent),
                    center = Offset(w * 0.30f, h * 0.45f),
                    radius = w * 0.56f
                ),
                radius = w * 0.56f,
                center = Offset(w * 0.30f, h * 0.45f)
            )

            // Soft top-left aurora glass reflection blended smoothly along the organic rim
            val topRibbon = Path().apply {
                moveTo(0f, h * 0.24f)
                cubicTo(w * 0.08f, h * 0.07f, w * 0.34f, h * 0.04f, w * 0.56f, h * 0.13f)
                cubicTo(w * 0.34f, h * 0.12f, w * 0.14f, h * 0.16f, 0f, h * 0.36f)
                close()
            }
            drawPath(
                path = topRibbon,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x2899FFD0),
                        Color(0x1447FFA3),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.16f, h * 0.14f),
                    radius = w * 0.42f
                )
            )

            // Subtle lower sweeping translucent glass wave (smooth organic sheen along bottom-left)
            val bottomRibbon = Path().apply {
                moveTo(0f, h * 0.68f)
                cubicTo(w * 0.18f, h * 0.85f, w * 0.38f, h * 0.85f, w * 0.58f, h * 0.96f)
                lineTo(w * 0.14f, h * 0.96f)
                cubicTo(w * 0.04f, h * 0.95f, 0f, h * 0.88f, 0f, h * 0.74f)
                close()
            }
            drawPath(
                path = bottomRibbon,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E80FFBE), Color(0x1233E08C), Color.Transparent),
                    center = Offset(w * 0.20f, h * 0.88f),
                    radius = w * 0.44f
                )
            )

            // Delicate inner specular wave line along bottom-left curve
            val innerArc = Path().apply {
                moveTo(w * 0.01f, h * 0.70f)
                cubicTo(w * 0.18f, h * 0.85f, w * 0.38f, h * 0.85f, w * 0.58f, h * 0.965f)
            }
            drawPath(
                path = innerArc,
                brush = Brush.horizontalGradient(
                    listOf(Color(0x52B8FFDC), Color(0x2E5CFFB0), Color.Transparent)
                ),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )

            // Crisp refractive glass outer outline
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xB8C4FFE2),
                        Color(0x664DFFAA),
                        Color(0x38FFFFFF),
                        Color(0x5245E89B),
                        Color(0x94B5FFDC)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Foreground Content inside the organic glass silhouette
        Crossfade(
            targetState = featuredItem,
            animationSpec = tween(180),
            label = "featuredPanelCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { item ->
            val title = when (item) {
                is NatureGridItem.App -> item.entry.label
                is NatureGridItem.AllAppsTile -> "All Apps"
                null -> "No Apps Here"
            }
            val description = describeFeaturedItem(item, allAppsCount, isPinned)
            val buttonLabel = when (item) {
                is NatureGridItem.AllAppsTile -> "BROWSE"
                null -> "ALL APPS"
                else -> "OPEN"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 42.dp, end = 34.dp, top = 40.dp, bottom = 38.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Icon + Large App Title side by side (matching the concept image)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        FeaturedItemIcon(
                            item = item,
                            size = 86.dp
                        )
                        Text(
                            text = title,
                            color = NatureGlassColors.TextPrimary,
                            fontSize = if (title.length > 14) 38.sp else 50.sp,
                            lineHeight = if (title.length > 14) 42.sp else 54.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = titleShadow)
                        )
                    }

                    // Clean 2-line description constrained to left ~76% so waterfall stays unobstructed
                    Text(
                        text = description,
                        color = NatureGlassColors.TextSecondary,
                        fontSize = 18.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = bodyShadow),
                        modifier = Modifier.fillMaxWidth(0.76f)
                    )
                }

                Spacer(modifier = Modifier.height(34.dp))

                // Prominent glowing OPEN pill button
                Box(
                    modifier = Modifier
                        .scale(openScale)
                        .drawBehind {
                            drawGlowRoundedRect(
                                color = NatureGlassColors.EmeraldBright,
                                alpha = if (isOpenButtonFocused) 0.92f else 0.65f,
                                cornerRadiusPx = size.height / 2f,
                                blurRadiusPx = if (isOpenButtonFocused) 26.dp.toPx() else 18.dp.toPx()
                            )
                            drawGlowRoundedRect(
                                color = Color.White,
                                alpha = if (isOpenButtonFocused) 0.55f else 0.25f,
                                cornerRadiusPx = size.height / 2f,
                                blurRadiusPx = 10.dp.toPx()
                            )
                        }
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    NatureGlassColors.OpenBtnStart,
                                    Color(0xFFE7FFF0),
                                    NatureGlassColors.OpenBtnEnd
                                )
                            )
                        )
                        .border(
                            width = if (isOpenButtonFocused) 3.dp else 1.8.dp,
                            brush = Brush.horizontalGradient(
                                if (isOpenButtonFocused) {
                                    listOf(Color.White, NatureGlassColors.EmeraldBright, Color.White)
                                } else {
                                    listOf(Color.White, Color(0xFFD6FFE8), Color.White)
                                }
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onOpenClick() }
                        .widthIn(min = 210.dp)
                        .height(58.dp)
                        .padding(horizontal = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = NatureGlassColors.OpenBtnText,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = buttonLabel,
                            color = NatureGlassColors.OpenBtnText,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedItemIcon(item: NatureGridItem?, size: Dp) {
    val shape = RoundedCornerShape(20.dp)
    when (item) {
        is NatureGridItem.App -> {
            Image(
                bitmap = item.entry.icon,
                contentDescription = item.entry.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        }
        is NatureGridItem.AllAppsTile -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x9911422E), Color(0x99072116))
                        )
                    )
                    .border(1.4.dp, NatureGlassColors.EmeraldBright.copy(alpha = 0.55f), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = "All Apps",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.58f)
                )
            }
        }
        null -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(Color(0x700B2E20))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(size * 0.54f)
                )
            }
        }
    }
}

/**
 * Uniform Nature Glass card for the 2x3 right-hand grid.
 * Selected state has the bright white-and-emerald border and radiant outer glow matching the concept.
 */
@Composable
fun NatureGlassGridCard(
    item: NatureGridItem,
    isSelected: Boolean,
    isGridFocused: Boolean,
    showPinnedBadge: Boolean,
    cardHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGlow = isSelected && isGridFocused
    val passiveSelected = isSelected && !isGridFocused
    val scale by animateFloatAsState(
        targetValue = if (activeGlow) 1.028f else 1.0f,
        animationSpec = tween(150),
        label = "natureCardScale"
    )
    val shape = RoundedCornerShape(18.dp)
    val seed = when (item) {
        is NatureGridItem.App -> abs(item.entry.packageName.hashCode())
        is NatureGridItem.AllAppsTile -> 42
    }
    val label = when (item) {
        is NatureGridItem.App -> item.entry.label
        is NatureGridItem.AllAppsTile -> "All Apps"
    }
    val customBanner = (item as? NatureGridItem.App)?.entry?.banner

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .scale(scale)
            .drawBehind {
                if (activeGlow) {
                    drawGlowRoundedRect(
                        color = NatureGlassColors.EmeraldBright,
                        alpha = 0.78f,
                        cornerRadiusPx = 18.dp.toPx(),
                        blurRadiusPx = 22.dp.toPx()
                    )
                    drawGlowRoundedRect(
                        color = Color.White,
                        alpha = 0.42f,
                        cornerRadiusPx = 18.dp.toPx(),
                        blurRadiusPx = 9.dp.toPx()
                    )
                } else if (passiveSelected) {
                    drawGlowRoundedRect(
                        color = NatureGlassColors.EmeraldGlow,
                        alpha = 0.35f,
                        cornerRadiusPx = 18.dp.toPx(),
                        blurRadiusPx = 12.dp.toPx()
                    )
                }
            }
            .clip(shape)
            .clickable { onClick() }
    ) {
        // Optional user-assigned custom banner softly blended behind Nature Glass scrim
        if (customBanner != null) {
            Image(
                bitmap = customBanner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (activeGlow) 0.42f else 0.30f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Translucent dark-green atmospheric glass backdrop + subtle flowing emerald ribbons
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val variant = seed % 3

            val bgColors = when {
                activeGlow -> listOf(Color(0xD4073623), Color(0xDC052418), Color(0xCC0A402A))
                passiveSelected -> listOf(Color(0xC2092E20), Color(0xCC061E15))
                variant == 1 -> listOf(Color(0xAD0D2A2C), Color(0xC7081C1E))
                variant == 2 -> listOf(Color(0xAD0B2C1F), Color(0xC7061C14))
                else -> listOf(Color(0xAD0A281E), Color(0xC7061A14))
            }
            drawRect(
                brush = Brush.linearGradient(
                    colors = bgColors,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Soft atmospheric light glow inside card
            val glowCenter = if (activeGlow) Offset(w * 0.72f, h * 0.78f) else Offset(w * 0.50f, h * 0.32f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (activeGlow) Color(0x5E3DFFA0) else Color(0x243FE89B),
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = glowCenter
            )

            // Smooth sweeping emerald wave ribbon in lower portion (signature look of reference cards)
            val wave1 = Path().apply {
                moveTo(0f, h * (if (variant == 1) 0.76f else 0.82f))
                cubicTo(
                    w * 0.32f, h * (if (variant == 1) 0.56f else 0.62f),
                    w * 0.66f, h * 0.92f,
                    w, h * (if (activeGlow) 0.44f else 0.58f)
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = wave1,
                brush = Brush.linearGradient(
                    colors = if (activeGlow) {
                        listOf(Color(0x2238F29A), Color(0x6640FFA6), Color(0x4512784C))
                    } else {
                        listOf(Color(0x16248A5E), Color(0x2B2FBF7E), Color(0x180B3B27))
                    },
                    start = Offset(0f, h * 0.55f),
                    end = Offset(w, h)
                )
            )

            // Second subtle ridge ribbon for depth
            val wave2 = Path().apply {
                moveTo(w * 0.18f, h)
                cubicTo(
                    w * 0.48f, h * 0.68f,
                    w * 0.76f, h * 0.74f,
                    w, h * 0.52f
                )
                lineTo(w, h)
                close()
            }
            drawPath(
                path = wave2,
                brush = Brush.horizontalGradient(
                    colors = if (activeGlow) {
                        listOf(Color.Transparent, Color(0x4468FFB8), Color(0x6645FFA3))
                    } else {
                        listOf(Color.Transparent, Color(0x223CD990), Color(0x2E1D7A52))
                    }
                )
            )

            // Soft luminous highlight tracing the emerald wave when selected
            if (activeGlow) {
                val ridgeStroke = Path().apply {
                    moveTo(w * 0.26f, h)
                    cubicTo(w * 0.56f, h * 0.70f, w * 0.78f, h * 0.68f, w, h * 0.48f)
                }
                drawPath(
                    path = ridgeStroke,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x4D7CFFC0), Color(0x70D8FFEC))
                    ),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Border stroke: Bright crisp white-and-emerald double-feel border when selected!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = when {
                        activeGlow -> 2.8.dp
                        passiveSelected -> 1.8.dp
                        else -> 1.1.dp
                    },
                    brush = when {
                        activeGlow -> Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFC2FFE2),
                                NatureGlassColors.EmeraldBright,
                                Color.White
                            )
                        )
                        passiveSelected -> Brush.linearGradient(
                            colors = listOf(
                                NatureGlassColors.EmeraldBright.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.65f)
                            )
                        )
                        else -> Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color(0x4064E8AA)
                            )
                        )
                    },
                    shape = shape
                )
        )

        // Centered icon + bold white label
        val iconSize = (cardHeight * 0.44f).coerceIn(46.dp, 64.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (item) {
                is NatureGridItem.App -> {
                    Image(
                        bitmap = item.entry.icon,
                        contentDescription = item.entry.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
                is NatureGridItem.AllAppsTile -> {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = "All Apps",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize * 0.92f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xCC02100A),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 6f
                    )
                )
            )
        }

        // Subtle emerald pinned indicator on browse tabs (Apps / Games / Media)
        if (showPinnedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(NatureGlassColors.EmeraldBright)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
        }
    }
}

/**
 * Curved translucent dark-green glass controller-hint bar along the bottom edge,
 * matching the tapered glass arch and colored A/X/Y/B pills in the concept image.
 */
@Composable
fun NatureGlassBottomHintBar(
    openLabel: String,
    pinLabel: String?,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onAllApps: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Tapered curved glass dock backdrop spanning ~72% width
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val archPath = Path().apply {
                    moveTo(0f, h)
                    cubicTo(w * 0.06f, h * 0.14f, w * 0.11f, 0f, w * 0.18f, 0f)
                    lineTo(w * 0.82f, 0f)
                    cubicTo(w * 0.89f, 0f, w * 0.94f, h * 0.14f, w, h)
                    close()
                }
                drawPath(
                    path = archPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xB809291C), Color(0xDE04150E))
                    )
                )
                // Luminous translucent top rim arc
                val topRim = Path().apply {
                    moveTo(0f, h)
                    cubicTo(w * 0.06f, h * 0.14f, w * 0.11f, 0f, w * 0.18f, 0f)
                    lineTo(w * 0.82f, 0f)
                    cubicTo(w * 0.89f, 0f, w * 0.94f, h * 0.14f, w, h)
                }
                drawPath(
                    path = topRim,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x6682FFBE),
                            Color(0x99C4FFE2),
                            Color(0x6682FFBE),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                ControllerHintChip(
                    key = "A",
                    label = openLabel,
                    badgeColor = NatureGlassColors.BadgeA,
                    enabled = true,
                    onClick = onOpen
                )
                HintDivider()
                ControllerHintChip(
                    key = "X",
                    label = pinLabel ?: "Pin",
                    badgeColor = NatureGlassColors.BadgeX,
                    enabled = pinLabel != null,
                    onClick = onPin
                )
                HintDivider()
                ControllerHintChip(
                    key = "Y",
                    label = "All Apps",
                    badgeColor = NatureGlassColors.BadgeY,
                    enabled = true,
                    onClick = onAllApps
                )
                HintDivider()
                ControllerHintChip(
                    key = "B",
                    label = "Back",
                    badgeColor = NatureGlassColors.BadgeB,
                    enabled = true,
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun HintDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.24f))
    )
}

@Composable
private fun ControllerHintChip(
    key: String,
    label: String,
    badgeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.42f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = alpha))
                .border(1.3.dp, Color.White.copy(alpha = 0.72f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key,
                color = Color.White.copy(alpha = alpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.96f * alpha),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Lightweight, allocation-free, GPU-consistent soft outer glow using concentric feathered layers.
 * Renders identically on Lenovo Legion Y700 hardware, 4K TVs, and Android emulators.
 */
private fun DrawScope.drawGlowRoundedRect(
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
