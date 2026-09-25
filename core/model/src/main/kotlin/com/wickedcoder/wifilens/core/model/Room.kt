package com.wickedcoder.wifilens.core.model

/** A named room. [id] is the small integer [com.wickedcoder.wifilens.core.rf.CellType.Floor] cells
 * store to say which room they belong to — not a database row id, see `RoomEntity`. */
data class Room(val id: Int, val name: String)
