package com.meetnotes.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.meetnotes.app.domain.model.MeetingStatus

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val audioPath: String,
    val status: MeetingStatus,
    val transcript: String? = null,
    /** MinutesSummary serialized as JSON. */
    val summaryJson: String? = null,
    val notes: String = "",
    /** Comma-separated tags. */
    val tags: String = "",
    val errorMessage: String? = null,
    val progress: Float? = null,
)

@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("meetingId")],
)
data class ActionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val owner: String,
    val task: String,
    val dueDate: String,
    val done: Boolean,
    val position: Int,
)

class Converters {
    @TypeConverter fun statusToString(s: MeetingStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): MeetingStatus =
        runCatching { MeetingStatus.valueOf(s) }.getOrDefault(MeetingStatus.RECORDED)
}
