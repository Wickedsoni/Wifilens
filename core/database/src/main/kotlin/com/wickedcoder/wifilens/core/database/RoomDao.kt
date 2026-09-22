package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** CRUD for a plan's rooms. [replaceRooms] is the one most callers want — see its doc. */
@Dao
interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity): Long

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    @Query("DELETE FROM room WHERE planId = :planId")
    suspend fun deleteRoomsForPlan(planId: Long)

    /**
     * Swaps a plan's whole room list atomically. [RoomEntity] has an auto-generated key, so a plain
     * re-insert of the same rooms would pile up duplicates on every autosave.
     */
    @Transaction
    suspend fun replaceRooms(planId: Long, rooms: List<RoomEntity>) {
        deleteRoomsForPlan(planId)
        rooms.forEach { insertRoom(it.copy(planId = planId)) }
    }

    @Query("SELECT * FROM room WHERE planId = :planId")
    fun getRoomsForPlan(planId: Long): Flow<List<RoomEntity>>
}
