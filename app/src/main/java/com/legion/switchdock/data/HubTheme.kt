package com.legion.switchdock.data

import androidx.compose.ui.graphics.Color

enum class HubTheme(
    val id: String,
    val displayName: String,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val accent: Color,
    val cardBg: Color
) {
    SWITCH_DARK(
        id = "switch_dark",
        displayName = "Switch Dark",
        bgTop = Color(0xFF0A0D14),
        bgMid = Color(0xFF101622),
        bgBottom = Color(0xFF07090E),
        accent = Color(0xFF00E5FF),
        cardBg = Color(0xFF131926)
    ),
    OLED_BLACK(
        id = "oled_black",
        displayName = "OLED Pure Black",
        bgTop = Color(0xFF000000),
        bgMid = Color(0xFF030508),
        bgBottom = Color(0xFF000000),
        accent = Color(0xFF38BDF8),
        cardBg = Color(0xFF0B0E14)
    ),
    LEGION_CYAN(
        id = "legion_cyan",
        displayName = "Legion Neon Cyan",
        bgTop = Color(0xFF061117),
        bgMid = Color(0xFF0A1D27),
        bgBottom = Color(0xFF040A0E),
        accent = Color(0xFF00F0FF),
        cardBg = Color(0xFF0E232E)
    ),
    CRIMSON_RED(
        id = "crimson_red",
        displayName = "Switch Crimson",
        bgTop = Color(0xFF14080C),
        bgMid = Color(0xFF210D14),
        bgBottom = Color(0xFF0D0508),
        accent = Color(0xFFFF4769),
        cardBg = Color(0xFF1F1016)
    ),
    EMERALD_PULSE(
        id = "emerald_pulse",
        displayName = "Emerald Pulse",
        bgTop = Color(0xFF06120D),
        bgMid = Color(0xFF0B2017),
        bgBottom = Color(0xFF040A08),
        accent = Color(0xFF34D399),
        cardBg = Color(0xFF0E231B)
    ),
    // Pure black reads as see-through in XR glasses, so tiles appear to float in the room
    GLASSES_FLOAT(
        id = "glasses_float",
        displayName = "XR Float (Glasses)",
        bgTop = Color(0xFF000000),
        bgMid = Color(0xFF000000),
        bgBottom = Color(0xFF000000),
        accent = Color(0xFF7DF9FF),
        cardBg = Color(0xFF0A0E14)
    );

    val surfaceGlass: Color
        get() = cardBg.copy(alpha = 0.82f)

    val surfaceModal: Color
        get() = if (this == OLED_BLACK || this == GLASSES_FLOAT) Color(0xFF0B0E14) else Color(0xFF10141D)

    val surfaceElevated: Color
        get() = cardBg

    val borderSubtle: Color
        get() = Color.White.copy(alpha = 0.09f)

    val borderFocused: Color
        get() = accent

    val focusGlow: Color
        get() = accent.copy(alpha = 0.18f)

    val textPrimary: Color
        get() = Color(0xFFF3F5F8)

    val textSecondary: Color
        get() = Color(0xFF94A0B8)

    val textMuted: Color
        get() = Color(0xFF647087)

    fun next(): HubTheme {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromId(id: String?): HubTheme {
            return entries.firstOrNull { it.id == id } ?: SWITCH_DARK
        }
    }
}
