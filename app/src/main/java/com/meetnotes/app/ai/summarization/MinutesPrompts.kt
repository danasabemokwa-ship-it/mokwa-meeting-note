package com.meetnotes.app.ai.summarization

import com.meetnotes.app.ai.NigerianSpeech
import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.model.normalized
import kotlinx.serialization.json.Json

/**
 * The prompt contract shared by every LLM backend. The model must return exactly the JSON shape
 * that [MinutesSummary] deserializes, which is what forces the fixed minutes structure:
 * Title → Date & Time → Participants → Key Discussion Points → Decisions → Action Items → Next Steps.
 */
object MinutesPrompts {

    /** Long transcripts are trimmed to stay inside every provider's context window. */
    const val MAX_TRANSCRIPT_CHARS = 120_000

    fun system(tone: SummaryTone, meta: MeetingMeta? = null): String = base(tone) +
        (meta?.let { "\n\n" + NigerianSpeech.minutesGuidance(it.nigerian, it.glossary) }?.trimEnd() ?: "")

    private fun base(tone: SummaryTone): String = """
        You are an expert meeting secretary. You turn raw meeting transcripts into accurate, well-organised minutes of meeting.

        Return ONLY one JSON object — no markdown, no code fences, no text before or after it — with EXACTLY these keys:
        {
          "meeting_title": string,
          "date_time": string,
          "participants": [string],
          "key_discussion_points": [string],
          "decisions_made": [string],
          "action_items": [ { "owner": string, "task": string, "due_date": string, "priority": "High" | "Medium" | "Low" } ],
          "next_steps": [string]
        }

        Rules:
        1. Use only information in the transcript. Never invent names, numbers, dates, decisions or tasks.
        2. meeting_title: a short descriptive title based on the content (max 10 words). If the provided title is a real title (not a file name like "Meeting_2026-01-01_09-00"), keep it.
        3. date_time: use the date & time provided by the user exactly.
        4. participants: people who spoke or were stated to be present. Include the known participants supplied. Use [] if none can be identified. Never list generic labels like "Speaker 1" if a real name is known for that speaker.
        5. key_discussion_points: the main topics, findings and figures discussed, one point per string.
        6. decisions_made: only explicit agreements or decisions. Use [] if there were none.
        7. action_items: every task, commitment or assignment. "owner" is the responsible person's name, or "TBD" if unclear. "due_date" is the deadline exactly as stated (e.g. "Friday", "15 October 2026"), or "TBD" if none was mentioned. "task" starts with a verb. "priority" is High when the speakers stressed urgency or a near deadline, Low for nice-to-have items, otherwise Medium.
        8. next_steps: follow-up plans, the next meeting date/agenda, or open questions to resolve. Use [] if none.
        9. If the transcript is not in English (for example Hausa), still write the minutes in clear English, keeping names unchanged.
        10. Tone: ${toneInstruction(tone)}
    """.trimIndent()

    fun user(transcript: String, meta: MeetingMeta): String {
        val clipped = if (transcript.length > MAX_TRANSCRIPT_CHARS) {
            transcript.take(MAX_TRANSCRIPT_CHARS) + "\n[…transcript truncated…]"
        } else transcript
        return buildString {
            appendLine("Current meeting title: ${meta.title}")
            appendLine("Date & time: ${meta.dateTime}")
            appendLine("Known participants: ${meta.knownParticipants.joinToString().ifBlank { "none provided" }}")
            appendLine()
            appendLine("TRANSCRIPT:")
            appendLine("\"\"\"")
            appendLine(clipped)
            appendLine("\"\"\"")
        }
    }

    private fun toneInstruction(tone: SummaryTone) = when (tone) {
        SummaryTone.FORMAL ->
            "formal and official, third person, complete sentences suitable for filed minutes; 4–8 key discussion points."
        SummaryTone.CONCISE ->
            "very concise — short phrases, at most 5 key discussion points, merge related items."
        SummaryTone.DETAILED ->
            "detailed and thorough — keep context, figures and the reasoning behind decisions; 8–15 key discussion points."
        SummaryTone.NIGERIAN_OFFICIAL ->
            "Nigerian public-service minutes style: past tense, reported speech (e.g. \"The Chairman informed members that…\", " +
                "\"Members observed that…\"). If they occurred, record the call to order, opening prayer, adoption of previous " +
                "minutes (with mover and seconder), matters arising, AOB, adjournment and closing prayer in key_discussion_points " +
                "or next_steps, and write decisions as \"It was resolved that…\" including who moved and seconded. 5–10 key points."
    }
}

object MinutesParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Extracts and parses the JSON object even if the model wrapped it in code fences or prose. */
    fun parse(raw: String): MinutesSummary {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "The AI response did not contain structured minutes." }
        return json.decodeFromString(MinutesSummary.serializer(), raw.substring(start, end + 1)).normalized()
    }
}
