package com.forge.workshop.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The forge palette — the same tokens as the published artifact, so app, docs and mockup are one
 * visual system. Executor hues carry meaning (Tool = steel, Press = bronze, Master = violet,
 * Smith = ember).
 */
data class ForgeColors(
    val ground: Color,
    val surface1: Color,
    val surface2: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val border: Color,
    val borderStrong: Color,
    val ember: Color,
    val emberHot: Color,
    val tool: Color,
    val press: Color,
    val master: Color,
    val smith: Color,
    val good: Color,
    val warn: Color,
    val crit: Color,
)

val ForgeDarkColors = ForgeColors(
    ground = Color(0xFF14110F),
    surface1 = Color(0xFF1C1815),
    surface2 = Color(0xFF241F1A),
    ink = Color(0xFFECE4DB),
    inkMuted = Color(0xFFA99D90),
    inkFaint = Color(0xFF6E655C),
    border = Color(0xFF332C27),
    borderStrong = Color(0xFF4A423B),
    ember = Color(0xFFFF7A34),
    emberHot = Color(0xFFFFB347),
    tool = Color(0xFF5FA8D6),
    press = Color(0xFFCB965A),
    master = Color(0xFFA493D4),
    smith = Color(0xFFFF7A34),
    good = Color(0xFF56B473),
    warn = Color(0xFFE0A63B),
    crit = Color(0xFFE4665A),
)

val ForgeLightColors = ForgeColors(
    ground = Color(0xFFE7E3DD),
    surface1 = Color(0xFFF1EEE9),
    surface2 = Color(0xFFFBFAF7),
    ink = Color(0xFF211C18),
    inkMuted = Color(0xFF5B524A),
    inkFaint = Color(0xFF8B8078),
    border = Color(0xFFD3CCC3),
    borderStrong = Color(0xFFB7AEA4),
    ember = Color(0xFFC64C16),
    emberHot = Color(0xFFD9871F),
    tool = Color(0xFF2C74A2),
    press = Color(0xFF9E6428),
    master = Color(0xFF64539C),
    smith = Color(0xFFC64C16),
    good = Color(0xFF3E8B58),
    warn = Color(0xFFB7791F),
    crit = Color(0xFFC0392B),
)

val LocalForgeColors = staticCompositionLocalOf { ForgeDarkColors }
