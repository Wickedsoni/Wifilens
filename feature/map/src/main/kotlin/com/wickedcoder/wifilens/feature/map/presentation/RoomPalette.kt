package com.wickedcoder.wifilens.feature.map.presentation

import androidx.compose.ui.graphics.Color

/** Room fills by roomId. Dark-theme tints are deep and saturated; light-theme ones are pastel. */
private val ROOM_PALETTE_DARK = listOf(
    Color(0xFF1A3A5C), // dark blue
    Color(0xFF2D5A27), // dark green
    Color(0xFF5C2D1A), // dark brown
    Color(0xFF3D1A5C), // dark purple
    Color(0xFF5C4A1A), // dark gold
    Color(0xFF1A4A5C), // dark teal
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
internal fun roomColor(roomId: Int, dark: Boolean): Color {
    val palette = if (dark) ROOM_PALETTE_DARK else ROOM_PALETTE_LIGHT
    return palette[roomId.mod(palette.size)]
}
