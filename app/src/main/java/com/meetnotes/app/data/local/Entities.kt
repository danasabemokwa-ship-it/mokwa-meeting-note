package com.meetnotes.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocStatus
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.Priority

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
    val priority: Priority = Priority.MEDIUM,
    val ownerEmail: String = "",
    val dueAt: Long? = null,
    val notes: String = "",
    val completedAt: Long? = null,
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: DocKind,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val status: DocStatus,
    /** Extracted plain text is kept in a file (can be large); this is its path. */
    val textPath: String? = null,
    val meta: String = "",
    /** DocumentSummary serialized as JSON. */
    val summaryJson: String? = null,
    val errorMessage: String? = null,
)

class Converters {
    @TypeConverter fun priorityToString(p: Priority): String = p.name
    @TypeConverter fun stringToPriority(s: String): Priority =
        runCatching { Priority.valueOf(s) }.getOrDefault(Priority.MEDIUM)
    @TypeConverter fun kindToString(k: DocKind): String = k.name
    @TypeConverter fun stringToKind(s: String): DocKind = runCatching { DocKind.valueOf(s) }.getOrDefault(DocKind.TEXT)
    @TypeConverter fun docStatusToString(d: DocStatus): String = d.name
    @TypeConverter fun stringToDocStatus(s: String): DocStatus =
        runCatching { DocStatus.valueOf(s) }.getOrDefault(DocStatus.FAILED)

    @TypeConverter fun statusToString(s: MeetingStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): MeetingStatus =
        runCatching { MeetingStatus.valueOf(s) }.getOrDefault(MeetingStatus.RECORDED)
}
