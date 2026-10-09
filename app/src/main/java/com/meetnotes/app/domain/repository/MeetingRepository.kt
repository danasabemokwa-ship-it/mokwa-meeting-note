package com.meetnotes.app.domain.repository

import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.ActionWithMeeting
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.MinutesSummary
import kotlinx.coroutines.flow.Flow

interface MeetingRepository {
    fun observeMeetings(): Flow<List<Meeting>>
    fun observeMeeting(id: Long): Flow<Meeting?>
    fun observeActionItems(meetingId: Long): Flow<List<ActionItem>>

    suspend fun getMeeting(id: Long): Meeting?
    suspend fun getActionItems(meetingId: Long): List<ActionItem>

    suspend fun createMeeting(title: String, audioPath: String, durationMs: Long, createdAt: Long): Long
    suspend fun rename(id: Long, title: String)
    suspend fun updateTranscript(id: Long, transcript: String?)
    /** @param replaceActions when true the action-item checklist is rebuilt from [summary]. */
    suspend fun updateSummary(id: Long, summary: MinutesSummary, replaceActions: Boolean)
    suspend fun updateNotesAndTags(id: Long, notes: String, tags: List<String>)
    suspend fun setStatus(id: Long, status: MeetingStatus, progress: Float? = null, message: String? = null)
    /** Deletes the meeting, its action items and its audio file. */
    suspend fun deleteMeeting(id: Long)
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    suspend fun upsertActionItem(item: ActionItem): Long
    suspend fun deleteActionItem(id: Long)
    suspend fun setActionDone(id: Long, done: Boolean)

    /** Every action point across all meetings, open first and soonest due first. */
    fun observeAllActions(): Flow<List<ActionWithMeeting>>
    fun observeOpenActionCount(): Flow<Int>
}
