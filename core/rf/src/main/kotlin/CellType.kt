package com.wickedcoder.wifilens.core.rf

/**
 * What one grid cell is. This is the whole floor plan model — there is no separate "wall" type:
 * a wall is just an [Empty] cell sitting where a [Floor] cell isn't. [roomId] of `0` means an
 * unassigned floor tile (painted but not yet added to a room).
 */
sealed interface CellType {
    /** Walkable floor belonging to room [roomId]. */
    data class Floor(val roomId: Int) : CellType

    /** A wall tile carrying a [Material] — the RF model reads its [Material.lossDb] when a
     * signal path crosses it. Not walkable. */
    data class Empty(val material: Material) : CellType

    /** A walkable opening between rooms; unlike [Empty] it contributes no wall loss. */
    data object Door : CellType
}
