package com.wickedcoder.wifilens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Hard constraint: one router pin (see DEVELOPMENT.md) — [Index] on planId is unique to enforce it. */
@Entity(
    tableName = "router_pin",
    foreignKeys = [
        ForeignKey(
            entity = GridPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId", unique = true)],
)
data class RouterPinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val x: Int,
    val y: Int,
    val band: String,
)
