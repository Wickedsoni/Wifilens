package com.wickedcoder.wifilens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wickedcoder.wifilens.core.rf.CellType

@Entity(
    tableName = "cell",
    primaryKeys = ["planId", "x", "y"],
    foreignKeys = [
        ForeignKey(
            entity = GridPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
/** One grid cell, one row — the whole plan is `width * height` of these. [cellTypeJson] is a
 * [CellType], stored as its JSON encoding via [CellTypeConverter] (Room needs a primitive column
 * type, not a sealed interface). */
data class CellEntity(
    val planId: Long,
    val x: Int,
    val y: Int,
    val cellTypeJson: CellType,
)
