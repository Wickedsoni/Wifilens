package com.wickedcoder.wifilens.core.rf

/**
 * The floor plan: a [width] x [height] grid of [CellType].
 *
 * Backed by a [ByteArray] of cell kinds plus an [IntArray] of room ids (used only for floor cells)
 * rather than a `List<CellType>` of boxed objects — a fraction of the memory, and copying it is a
 * plain array copy. The public API is unchanged: [cellAt], [isWalkable], [cells]. Instances are
 * immutable; edits go through [withCell], which returns a new plan (the same value semantics the old
 * data class had, so undo snapshots and StateFlow equality keep working).
 *
 * Nothing about this is persisted directly: the database stores one row per cell through its own
 * converter, so this in-memory layout can change without touching the on-disk format.
 */
class GridPlan private constructor(
    val width: Int,
    val height: Int,
    private val kinds: ByteArray,
    private val roomIds: IntArray,
) {

    /** Same shape as the old data-class constructor: [cells] in row-major order, size == width*height. */
    constructor(width: Int, height: Int, cells: List<CellType>) : this(
        width,
        height,
        encodeKinds(width, height, cells),
        encodeRoomIds(cells),
    )

    fun cellAt(x: Int, y: Int): CellType {
        require(x in 0 until width && y in 0 until height) {
            "($x, $y) is out of bounds for a ${width}x$height grid"
        }
        return decode(y * width + x)
    }

    fun isWalkable(x: Int, y: Int): Boolean = when (cellAt(x, y)) {
        is CellType.Floor, CellType.Door -> true
        is CellType.Empty -> false
    }

    /** Row-major read-only view; nothing is copied or decoded until an element is read. */
    val cells: List<CellType>
        get() = object : AbstractList<CellType>() {
            override val size: Int get() = kinds.size
            override fun get(index: Int): CellType = decode(index)
        }

    /** A copy of this plan with one cell replaced (this plan is returned unchanged if it already matches). */
    fun withCell(x: Int, y: Int, cell: CellType): GridPlan {
        require(x in 0 until width && y in 0 until height) {
            "($x, $y) is out of bounds for a ${width}x$height grid"
        }
        val index = y * width + x
        val kind = kindOf(cell)
        val roomId = (cell as? CellType.Floor)?.roomId ?: 0
        if (kinds[index] == kind && roomIds[index] == roomId) return this

        val newKinds = kinds.copyOf()
        val newRoomIds = roomIds.copyOf()
        newKinds[index] = kind
        newRoomIds[index] = roomId
        return GridPlan(width, height, newKinds, newRoomIds)
    }

    private fun decode(index: Int): CellType {
        val kind = kinds[index]
        return when {
            kind < KIND_FLOOR -> EMPTY_CELLS[kind.toInt()]
            kind == KIND_FLOOR -> CellType.Floor(roomIds[index])
            else -> CellType.Door
        }
    }

    override fun equals(other: Any?): Boolean =
        other is GridPlan &&
            width == other.width &&
            height == other.height &&
            kinds.contentEquals(other.kinds) &&
            roomIds.contentEquals(other.roomIds)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + kinds.contentHashCode()
        result = 31 * result + roomIds.contentHashCode()
        return result
    }

    override fun toString(): String = "GridPlan(${width}x$height)"

    private companion object {
        // Cell kinds. 0..5 are Empty(material) in this order; 6 = Floor (room id in roomIds); 7 = Door.
        const val KIND_FLOOR: Byte = 6
        const val KIND_DOOR: Byte = 7

        val MATERIALS: Array<Material> = arrayOf(
            Material.Drywall, Material.Wood, Material.Glass, Material.Brick, Material.Concrete, Material.Metal,
        )

        /** Shared instances: decoding an Empty cell never allocates. */
        val EMPTY_CELLS: Array<CellType> = Array(MATERIALS.size) { CellType.Empty(MATERIALS[it]) }

        fun kindOf(cell: CellType): Byte = when (cell) {
            is CellType.Empty -> MATERIALS.indexOf(cell.material).also { check(it >= 0) { "Unknown material ${cell.material}" } }.toByte()
            is CellType.Floor -> KIND_FLOOR
            CellType.Door -> KIND_DOOR
        }

        fun encodeKinds(width: Int, height: Int, cells: List<CellType>): ByteArray {
            require(cells.size == width * height) {
                "cells.size (${cells.size}) must equal width*height (${width * height})"
            }
            return ByteArray(cells.size) { kindOf(cells[it]) }
        }

        fun encodeRoomIds(cells: List<CellType>): IntArray =
            IntArray(cells.size) { (cells[it] as? CellType.Floor)?.roomId ?: 0 }
    }
}
