package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.ui.graphics.Color

/** Room fills by roomId. Dark-theme tints are mid-tone and saturated so a 60% fill still stands out on a black canvas; light-theme ones are pastel. */
private val ROOM_PALETTE_DARK = listOf(
    Color(0xFF3B7DD8), // dark blue
    Color(0xFF3FA34D), // dark green
    Color(0xFFD9713B), // dark brown
    Color(0xFF9457D6), // dark purple
    Color(0xFFCFAE33), // dark gold
    Color(0xFF33A6C4), // dark teal
)
private val ROOM_PALETTE_LIGHT = listOf(
    Color(0xFFD0E8FF), // light blue
    Color(0xFFD0FFD8), // light green
    Color(0xFFFFE0D0), // light peach
    Color(0xFFEED0FF), // light purple
    Color(0xFFFFF0D0), // light yellow
    Color(0xFFD0F0FF), // light teal
)

/** The one place a room's colour is decided, so the 2D editor and the ISO view always agree. */
fun roomColor(roomId: Int, dark: Boolean): Color {
    val palette = if (dark) ROOM_PALETTE_DARK else ROOM_PALETTE_LIGHT
    return palette[roomId.mod(palette.size)]
}
