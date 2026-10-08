package com.meetnotes.app.data.repository

import com.meetnotes.app.data.local.ActionItemDao
import com.meetnotes.app.data.local.ActionItemEntity
import com.meetnotes.app.data.local.MeetingDao
import com.meetnotes.app.data.local.MeetingEntity
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val meetingDao: MeetingDao,
    private val actionDao: ActionItemDao,
    private val json: Json,
) : MeetingRepository {

    override fun observeMeetings(): Flow<List<Meeting>> =
        meetingDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeMeeting(id: Long): Flow<Meeting?> =
        meetingDao.observe(id).distinctUntilChanged().map { it?.toDomain() }

    override fun observeActionItems(meetingId: Long): Flow<List<ActionItem>> =
        actionDao.observeForMeeting(meetingId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMeeting(id: Long): Meeting? = meetingDao.get(id)?.toDomain()

    override suspend fun getActionItems(meetingId: Long): List<ActionItem> =
        actionDao.getForMeeting(meetingId).map { it.toDomain() }

    override suspend fun createMeeting(title: String, audioPath: String, durationMs: Long, createdAt: Long): Long =
        meetingDao.insert(
            MeetingEntity(
                title = title,
                createdAt = createdAt,
                durationMs = durationMs,
                audioPath = audioPath,
                status = MeetingStatus.RECORDED,
            )
        )

    override suspend fun rename(id: Long, title: String) = meetingDao.setTitle(id, title.trim())

    override suspend fun updateTranscript(id: Long, transcript: String?) = meetingDao.setTranscript(id, transcript)

    override suspend fun updateSummary(id: Long, summary: MinutesSummary, replaceActions: Boolean) {
        meetingDao.setSummary(id, json.encodeToString(summary))
        if (replaceActions) {
            actionDao.replaceForMeeting(
                id,
                summary.actionItems.mapIndexed { index, a ->
                    ActionItemEntity(
                        meetingId = id,
                        owner = a.owner,
                        task = a.task,
                        dueDate = a.dueDate,
                        done = false,
                        position = index,
                    )
                },
            )
        }
    }

    override suspend fun updateNotesAndTags(id: Long, notes: String, tags: List<String>) =
        meetingDao.setNotesAndTags(id, notes, tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(","))

    override suspend fun setStatus(id: Long, status: MeetingStatus, progress: Float?, message: String?) =
        meetingDao.setStatus(id, status, progress, message)

    override suspend fun deleteMeeting(id: Long) {
        val entity = meetingDao.get(id) ?: return
        withContext(Dispatchers.IO) { File(entity.audioPath).delete() }
        meetingDao.delete(id) // action items are removed by the CASCADE foreign key
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        val old = meetingDao.olderThan(cutoffMillis)
        old.forEach { deleteMeeting(it.id) }
        return old.size
    }

    override suspend fun upsertActionItem(item: ActionItem): Long {
        val position = if (item.id == 0L) actionDao.nextPosition(item.meetingId) else item.position
        return actionDao.upsert(item.copy(position = position).toEntity())
    }

    override suspend fun deleteActionItem(id: Long) = actionDao.delete(id)

    // ---- mapping ----

    private fun MeetingEntity.toDomain() = Meeting(
        id = id,
        title = title,
        createdAt = createdAt,
        durationMs = durationMs,
        audioPath = audioPath,
        status = status,
        transcript = transcript,
        summary = summaryJson?.let { runCatching { json.decodeFromString<MinutesSummary>(it) }.getOrNull() },
        notes = notes,
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        errorMessage = errorMessage,
        progress = progress,
    )

    private fun ActionItemEntity.toDomain() =
        ActionItem(id, meetingId, owner, task, dueDate, done, position)

    private fun ActionItem.toEntity() =
        ActionItemEntity(id, meetingId, owner.ifBlank { "TBD" }, task.trim(), dueDate.ifBlank { "TBD" }, done, position)
}
