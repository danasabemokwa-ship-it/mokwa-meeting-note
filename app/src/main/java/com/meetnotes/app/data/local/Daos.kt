package com.meetnotes.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.meetnotes.app.domain.model.DocStatus
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

    /** Every action point with its meeting: open first, then by due date (no date last), then priority. */
    @Query(
        """
        SELECT a.*, m.title AS meetingTitle, m.createdAt AS meetingDate
        FROM action_items a INNER JOIN meetings m ON m.id = a.meetingId
        ORDER BY a.done ASC,
                 CASE WHEN a.dueAt IS NULL THEN 1 ELSE 0 END, a.dueAt ASC,
                 CASE a.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
                 m.createdAt DESC
        """
    )
    abstract fun observeAllWithMeeting(): Flow<List<ActionWithMeetingRow>>

    @Query("SELECT COUNT(*) FROM action_items WHERE done = 0")
    abstract fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM action_items WHERE id = :id")
    abstract suspend fun get(id: Long): ActionItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM action_items WHERE meetingId = :meetingId")
    abstract suspend fun nextPosition(meetingId: Long): Int

    @Transaction
    open suspend fun replaceForMeeting(meetingId: Long, items: List<ActionItemEntity>) {
        deleteForMeeting(meetingId)
        insertAll(items)
    }
}

data class ActionWithMeetingRow(
    @Embedded val item: ActionItemEntity,
    val meetingTitle: String,
    val meetingDate: Long,
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(entity: DocumentEntity): Long

    @Update
    suspend fun update(entity: DocumentEntity)

    @Query("UPDATE documents SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: DocStatus, error: String?)

    @Query("UPDATE documents SET textPath = :textPath, meta = :meta WHERE id = :id")
    suspend fun setText(id: Long, textPath: String, meta: String)

    @Query("UPDATE documents SET summaryJson = :json WHERE id = :id")
    suspend fun setSummary(id: Long, json: String?)

    @Query("UPDATE documents SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}
