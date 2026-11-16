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
        bgTop = Color(0xFF0E1117),
        bgMid = Color(0xFF171B24),
        bgBottom = Color(0xFF0C0E13),
        accent = Color(0xFF00E5FF),
        cardBg = Color(0xFF171C26)
    ),
    OLED_BLACK(
        id = "oled_black",
        displayName = "OLED Pure Black",
        bgTop = Color(0xFF000000),
        bgMid = Color(0xFF05070A),
        bgBottom = Color(0xFF000000),
        accent = Color(0xFF38BDF8),
        cardBg = Color(0xFF0D1017)
    ),
    LEGION_CYAN(
        id = "legion_cyan",
        displayName = "Legion Neon Cyan",
        bgTop = Color(0xFF06151C),
        bgMid = Color(0xFF0A2430),
        bgBottom = Color(0xFF050E14),
        accent = Color(0xFF00F0FF),
        cardBg = Color(0xFF0F2B38)
    ),
    CRIMSON_RED(
        id = "crimson_red",
        displayName = "Switch Crimson",
        bgTop = Color(0xFF1B090D),
        bgMid = Color(0xFF2B0E15),
        bgBottom = Color(0xFF100608),
        accent = Color(0xFFFF3B5C),
        cardBg = Color(0xFF281219)
    ),
    EMERALD_PULSE(
        id = "emerald_pulse",
        displayName = "Emerald Pulse",
        bgTop = Color(0xFF071710),
        bgMid = Color(0xFF0D281C),
        bgBottom = Color(0xFF050E0A),
        accent = Color(0xFF34D399),
        cardBg = Color(0xFF102A1F)
    );

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
