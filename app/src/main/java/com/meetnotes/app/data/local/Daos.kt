package com.meetnotes.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.meetnotes.app.domain.model.MeetingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observe(id: Long): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun get(id: Long): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MeetingEntity): Long

    @Update
    suspend fun update(entity: MeetingEntity)

    @Query("UPDATE meetings SET status = :status, progress = :progress, errorMessage = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: MeetingStatus, progress: Float?, error: String?)

    @Query("UPDATE meetings SET transcript = :transcript WHERE id = :id")
    suspend fun setTranscript(id: Long, transcript: String?)

    @Query("UPDATE meetings SET summaryJson = :json WHERE id = :id")
    suspend fun setSummary(id: Long, json: String?)

    @Query("UPDATE meetings SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("UPDATE meetings SET notes = :notes, tags = :tags WHERE id = :id")
    suspend fun setNotesAndTags(id: Long, notes: String, tags: String)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM meetings WHERE createdAt < :before")
    suspend fun olderThan(before: Long): List<MeetingEntity>
}

@Dao
abstract class ActionItemDao {
    @Query("SELECT * FROM action_items WHERE meetingId = :meetingId ORDER BY position, id")
    abstract fun observeForMeeting(meetingId: Long): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE meetingId = :meetingId ORDER BY position, id")
    abstract suspend fun getForMeeting(meetingId: Long): List<ActionItemEntity>

    @Upsert
    abstract suspend fun upsert(item: ActionItemEntity): Long

    @Insert
    abstract suspend fun insertAll(items: List<ActionItemEntity>)

    @Query("DELETE FROM action_items WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM action_items WHERE meetingId = :meetingId")
    abstract suspend fun deleteForMeeting(meetingId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM action_items WHERE meetingId = :meetingId")
    abstract suspend fun nextPosition(meetingId: Long): Int

    @Transaction
    open suspend fun replaceForMeeting(meetingId: Long, items: List<ActionItemEntity>) {
        deleteForMeeting(meetingId)
        insertAll(items)
    }
}
