package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A plan's pins, bundled together for the map to render in one read. */
data class PlanPins(val router: RouterPinEntity?, val devices: List<DevicePinEntity>)

/** CRUD for the router pin (0 or 1 per plan) and device pins (0 or more per plan). */
@Dao
interface PinDao {

    /** Hard constraint: one router pin — [RouterPinEntity]'s unique index on planId enforces it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouterPin(pin: RouterPinEntity): Long

    @Delete
    suspend fun deleteRouterPin(pin: RouterPinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevicePin(pin: DevicePinEntity): Long

    @Delete
    suspend fun deleteDevicePin(pin: DevicePinEntity)

    @Query("SELECT * FROM router_pin WHERE planId = :planId LIMIT 1")
    fun observeRouterPin(planId: Long): Flow<RouterPinEntity?>

    @Query("SELECT * FROM device_pin WHERE planId = :planId")
    fun observeDevicePins(planId: Long): Flow<List<DevicePinEntity>>

    /** Combines router + device pins for a plan. Not a @Query — Room only processes abstract
     * DAO members, so this default method passes through untouched. */
    fun getPinsForPlan(planId: Long): Flow<PlanPins> =
        observeRouterPin(planId).combine(observeDevicePins(planId)) { router, devices ->
            PlanPins(router, devices)
        }
}
