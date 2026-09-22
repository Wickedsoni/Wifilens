package com.wickedcoder.wifilens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_pin",
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
/** One placed device pin (e.g. "Laptop"). Unlike [RouterPinEntity] there's no unique-per-plan
 * constraint — a plan can have any number of device pins, one per Wi-Fi client the user cares
 * about checking coverage for. */
data class DevicePinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val x: Int,
    val y: Int,
    val name: String,
)
