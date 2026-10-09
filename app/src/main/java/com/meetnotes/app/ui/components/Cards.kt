@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.domain.model.startOfToday
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.util.DueDates
import kotlin.math.abs

// ------------------------------------------------------------------ bottom navigation

enum class TopDestination(val label: String, val icon: ImageVector) {
    HOME("Meetings", Icons.Default.Home),
    ACTIONS("Actions", Icons.Default.Checklist),
    DOCUMENTS("Documents", Icons.Default.Description),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun AppBottomBar(current: TopDestination, openActions: Int = 0, onNavigate: (TopDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
        TopDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = dest == current,
                onClick = { if (dest != current) onNavigate(dest) },
                icon = {
                    Box {
                        Icon(dest.icon, contentDescription = null)
                        if (dest == TopDestination.ACTIONS && openActions > 0) {
                            Surface(
                                color = Brand.Red,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(16.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        if (openActions > 99) "99" else "$openActions",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                },
                label = { Text(dest.label) },
            )
        }
    }
}

// ------------------------------------------------------------------ people

private val AVATAR_COLORS = listOf(
    Color(0xFF0B6B4F), Color(0xFF3B6FD6), Color(0xFF9C4DCC), Color(0xFFD9822B),
    Color(0xFF138A9E), Color(0xFFC2185B), Color(0xFF5D6D2B), Color(0xFF6D4C41),
)

fun initials(name: String): String {
    val words = name.replace(Regex("^(Alhaji|Alhaja|Hajiya|Mallam|Malam|Chief|Dr|Engr|Mr|Mrs|Ms|Prof|Barr|Hon|Pharm)\\.?\\s+", RegexOption.IGNORE_CASE), "")
        .split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 32.dp) {
    val color = if (name.equals("TBD", true) || name.isBlank()) MaterialTheme.colorScheme.outline
    else AVATAR_COLORS[abs(name.lowercase().hashCode()) % AVATAR_COLORS.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (name.equals("TBD", true)) "?" else initials(name),
            color = Color.White,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Overlapping avatars for a meeting's participants. */
@Composable
fun AvatarStack(names: List<String>, max: Int = 5, size: Dp = 28.dp) {
    val shown = names.take(max)
    val overlap = size.value * 0.3f
    Box {
        shown.forEachIndexed { i, n ->
            Box(
                Modifier
                    .offset(x = ((size.value - overlap) * i).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
            ) { Avatar(n, size) }
        }
        if (names.size > max) {
            Box(
                Modifier
                    .offset(x = ((size.value - overlap) * shown.size).dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text("+${names.size - max}", style = MaterialTheme.typography.labelSmall) }
        }
        // Reserve the full width so following content isn't overlapped.
        Spacer(Modifier.width(((size.value - overlap) * (shown.size + if (names.size > max) 1 else 0) + overlap).dp).height(size))
    }
}

// ------------------------------------------------------------------ chips

@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun PriorityPill(priority: Priority) {
    val (bg, fg) = when (priority) {
        Priority.HIGH -> Brand.Red.copy(alpha = 0.14f) to Brand.Red
        Priority.MEDIUM -> Brand.Amber.copy(alpha = 0.18f) to Color(0xFF9A5B00)
        Priority.LOW -> Brand.Slate.copy(alpha = 0.15f) to Brand.Slate
    }
    Pill(priority.label, bg, fg, Icons.Default.Flag)
}

@Composable
fun DuePill(item: ActionItem) {
    val now = System.currentTimeMillis()
    val dueAt = item.dueAt
    val text = when {
        dueAt != null -> {
            val days = ((dueAt - startOfToday(now)) / 86_400_000L).toInt()
            when {
                item.done -> DueDates.label(dueAt)
                days < 0 -> "Overdue · ${DueDates.label(dueAt)}"
                days == 0 -> "Due today"
                days == 1 -> "Due tomorrow"
                days < 7 -> "Due ${DueDates.label(dueAt)}"
                else -> DueDates.label(dueAt)
            }
        }
        item.dueDate.isBlank() || item.dueDate.equals("TBD", true) -> "No date"
        else -> item.dueDate
    }
    val overdue = item.isOverdue(now)
    val soon = !item.done && dueAt != null && !overdue && dueAt - startOfToday(now) <= 2 * 86_400_000L
    val (bg, fg) = when {
        overdue -> Brand.Red.copy(alpha = 0.14f) to Brand.Red
        soon -> Brand.Amber.copy(alpha = 0.18f) to Color(0xFF9A5B00)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(text, bg, fg, if (overdue) Icons.Default.WarningAmber else Icons.Default.Schedule)
}

// ------------------------------------------------------------------ action card

/**
 * One action point: tick-box, task, owner, due date, priority, and quick actions.
 * Used both inside a meeting and in the all-meetings tracker.
 */
@Composable
fun ActionCard(
    item: ActionItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meetingTitle: String? = null,
    onMeetingClick: (() -> Unit)? = null,
    onEmail: (() -> Unit)? = null,
    onCalendar: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val overdue = item.isOverdue()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (overdue) Brand.Red.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (item.done) "Mark as not done" else "Mark as done",
                    tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    item.task,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (meetingTitle != null) {
                    Text(
                        meetingTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .then(if (onMeetingClick != null) Modifier.clickable(role = Role.Button, onClick = onMeetingClick) else Modifier),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(item.owner, 22.dp)
                    Text(
                        item.owner,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.ownerEmail.isNotBlank()) {
                        Icon(
                            Icons.Default.Email, contentDescription = "Has email",
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuePill(item)
                    PriorityPill(item.priority)
                }
                if (onEmail != null || onCalendar != null || onDelete != null) {
                    Row(Modifier.padding(top = 2.dp)) {
                        if (onEmail != null) {
                            TextButton(onClick = onEmail) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Remind")
                            }
                        }
                        if (onCalendar != null) {
                            TextButton(onClick = onCalendar) {
                                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Calendar")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (onDelete != null) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete action point", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ stats & sections

@Composable
fun StatTile(value: String, label: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/** White rounded card with an icon header, used for each section of the minutes. */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { contentDescription = title })
                if (count != null) {
                    Pill("$count", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Numbered list (used for decisions). */
@Composable
fun NumberedList(items: List<String>, emptyText: String = "None recorded.") {
    if (items.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Text(item, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ headers & banners

/** Brand gradient header used at the top of the main screens. */
@Composable
fun GradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brand.headerGradient),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
                actions?.invoke()
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(end = 8.dp)) { content() }
            }
        }
    }
}

/** Shown while the Demo engine is selected — explains why the minutes are sample text. */
@Composable
fun DemoModeBanner(onFix: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Brand.Amber.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brand.Amber.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFF9A5B00))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Demo mode is on", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Minutes show sample text, not your meeting. Choose Gemini or OpenAI to transcribe real recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

/** Coloured file-type badge: blue Word, green Excel, red PDF. */
@Composable
fun FileKindBadge(kind: com.meetnotes.app.domain.model.DocKind, size: Dp = 44.dp) {
    val (color, label) = when (kind) {
        com.meetnotes.app.domain.model.DocKind.PDF -> Brand.PdfRed to "PDF"
        com.meetnotes.app.domain.model.DocKind.WORD -> Brand.Blue to "DOC"
        com.meetnotes.app.domain.model.DocKind.EXCEL -> Brand.ExcelGreen to "XLS"
        com.meetnotes.app.domain.model.DocKind.TEXT -> Brand.Slate to "TXT"
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Description, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.42f))
            Text(label, color = color, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Linear "3 of 5 done" bar. */
@Composable
fun ProgressLine(done: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    val fraction = done.toFloat() / total
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(50))
                    .background(if (done == total) MaterialTheme.colorScheme.primary else Brand.Gold),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$done/$total done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
