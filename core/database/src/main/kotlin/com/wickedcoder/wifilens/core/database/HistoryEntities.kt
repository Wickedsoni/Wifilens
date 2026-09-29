package com.wickedcoder.wifilens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables added in schema v2 for Sprints 7-8, so those sprints need no further migration.

/** A walk-survey measurement: averaged RSSI of one access point on one tile of a plan (Sprint 7). */
@Entity(
    tableName = "measurement",
    foreignKeys = [
        ForeignKey(entity = GridPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("planId"), Index(value = ["planId", "x", "y", "bssid"], unique = true)],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val x: Int,
    val y: Int,
    val bssid: String,
    val rssiAvg: Double,
    val sampleCount: Int,
    val updatedAt: Long,
)

/** One access point seen in one scan, for signal history (Sprint 8). High volume: throttled and pruned. */
@Entity(tableName = "scan_sample", indices = [Index("timestamp"), Index("bssid")])
data class ScanSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val band: String,
    val timestamp: Long,
)

/** Hourly roll-up of channel congestion, kept after raw samples are pruned (Sprint 8). */
@Entity(tableName = "channel_congestion", primaryKeys = ["hourStart", "band", "channel"])
data class ChannelCongestionEntity(
    val hourStart: Long,
    val band: String,
    val channel: Int,
    val networkCount: Int,
    val strongestRssi: Int,
    val congestionScore: Int,
)

/** One completed speed test, for speed-test history (Sprint 8). */
@Entity(tableName = "speed_test", indices = [Index("timestamp")])
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val downloadMbps: Double,
    val linkSpeedMbps: Int?,
    val rssi: Int?,
    val frequencyMhz: Int?,
)
