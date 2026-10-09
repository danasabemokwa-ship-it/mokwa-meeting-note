package com.meetnotes.app.ai.summarization

import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryActionItem
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.model.normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline minutes generator. It needs no model download and no internet:
 * it reads speaker labels ("Amina: …"), then uses language patterns to pull out decisions,
 * action items (with owner and due date) and follow-ups, and ranks the remaining sentences
 * to pick key discussion points. Quality is lower than an LLM but it is instant and private,
 * and it is also used as the automatic fallback when a cloud model fails.
 */
@Singleton
class OfflineRuleSummarizer @Inject constructor() : Summarizer {

    override val type = SummarizerType.OFFLINE_RULES
    override val setupHint = ""
    override suspend fun isAvailable() = true

    override suspend fun summarize(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary =
        withContext(Dispatchers.Default) { extract(transcript, meta, tone) }

    data class Utterance(val speaker: String?, val text: String)

    fun extract(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary {
        val utterances = parseUtterances(transcript)
        val speakers = utterances.mapNotNull { it.speaker }.filterNot { GENERIC_SPEAKER.matches(it) }.distinct()
        val participants = (meta.knownParticipants + speakers + mentionedAttendees(utterances)).distinct()

        val decisions = utterances.filter { DECISION.containsMatchIn(it.text) && !isQuestion(it.text) }

        val actionUtterances = utterances.filter {
            ACTION.containsMatchIn(it.text) && !isQuestion(it.text) && wordCount(it.text) >= 4 &&
                !NEXT_STEP.containsMatchIn(it.text)
        }
        val knownNames = participants + meta.glossary
        val actionPairs = actionUtterances
            .map { it to toAction(it, participants, knownNames) }
            // Drop fragments such as "Resolve it" that carry no real task.
            .filter { (_, a) -> wordCount(a.task) >= 3 }
            // A decision without a named owner is a decision, not a task.
            .filterNot { (u, a) -> u in decisions && a.owner == "TBD" }
        val actions = actionPairs.map { it.second }.distinctBy { it.task.lowercase() }

        val nextStepUtterances = utterances.filter { NEXT_STEP.containsMatchIn(it.text) }
        val nextSteps = nextStepUtterances.map { standardise(it.text).replaceFirstChar { c -> c.uppercase() } }.toMutableList()
        if (nextSteps.isEmpty() && actions.isNotEmpty()) {
            nextSteps += "Review progress on the ${actions.size} open action item(s) at the next meeting."
        }

        val used = (decisions + actionPairs.map { it.first } + nextStepUtterances).map { it.text }.toSet()
        val limit = when (tone) {
            SummaryTone.CONCISE -> 4
            SummaryTone.FORMAL -> 6
            SummaryTone.DETAILED -> 10
            SummaryTone.NIGERIAN_OFFICIAL -> 8
        }
        val keyPoints = utterances
            .withIndex()
            .filter { (_, u) ->
                u.text !in used && !isQuestion(u.text) && wordCount(u.text) in 6..70 &&
                    !FILLER.containsMatchIn(u.text) && !ATTENDANCE.containsMatchIn(u.text)
            }
            .sortedByDescending { (_, u) -> score(u.text) }
            .take(limit)
            .sortedBy { it.index }
            .map { (_, u) -> formatPoint(u, tone) }

        return MinutesSummary(
            title = meta.title,
            dateTime = meta.dateTime,
            participants = participants,
            keyPoints = keyPoints,
            decisions = decisions.map { formatDecision(it, tone) },
            actionItems = actions,
            nextSteps = nextSteps,
            generatedBy = "Offline extraction",
        ).normalized()
    }

    // ---------------------------------------------------------------- parsing

    fun parseUtterances(transcript: String): List<Utterance> {
        val result = mutableListOf<Utterance>()
        transcript.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .forEach { line ->
                val m = SPEAKER_LINE.matchEntire(line)
                val speaker = m?.groupValues?.get(1)?.trim()
                val body = m?.groupValues?.get(2) ?: line
                SENTENCE_SPLIT.split(body)
                    .map { it.trim() }
                    .filter { it.length > 2 }
                    .forEach { result += Utterance(speaker, it) }
            }
        return result
    }

    private fun mentionedAttendees(utterances: List<Utterance>): List<String> =
        utterances.flatMap { u ->
            PRESENT.find(u.text)?.groupValues?.get(1)
                ?.split(Regex(",|\\band\\b"))
                ?.map { it.trim() }
                ?.filter { NAME.matches(it) }
                .orEmpty()
        }

    private fun toAction(u: Utterance, participants: List<String>, knownNames: List<String>): SummaryActionItem {
        val text = u.text
        val (owner, spokenName) = findOwner(text, u.speaker, participants, knownNames)
        val due = (DUE.find(text)?.groupValues?.get(2) ?: BARE_DUE.find(text)?.groupValues?.get(1))
            ?.trim()?.trimEnd('.', ',')
            ?.replaceFirstChar { it.uppercase() } ?: "TBD"
        val subject = listOfNotNull(spokenName?.let { Regex.escape(it) }, "I", "We").joinToString("|")
        val task = standardise(
            text.replace(LEADING_FILLER, "").trim()
                // "Musa will send…", "Na Chinedu go handle…", "I'll schedule…" → "Send…", "Handle…", "Schedule…"
                // (the owner has its own column)
                .replace(
                    Regex("""^(?:na\s+)?(?:$subject)(?:\s+(?:will|needs? to|should|must|is going to|am going to|are going to|is to|go)|'ll)\s+""", RegexOption.IGNORE_CASE),
                    "",
                )
                .replace(Regex("""^(?:make (?:i|we)|i dey come)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^kindly\s+""", RegexOption.IGNORE_CASE), ""),
        )
            .trimEnd('.')
            .replaceFirstChar { it.uppercase() }
            .take(220)
        val priority = when {
            URGENT.containsMatchIn(text) -> "High"
            LOW_PRIORITY.containsMatchIn(text) -> "Low"
            else -> "Medium"
        }
        return SummaryActionItem(owner = owner, task = task, dueDate = due, priority = priority)
    }

    /**
     * @return the owner as it should appear in the minutes (matched to a participant's full name and
     * title where possible) and the name exactly as spoken in this sentence (null for "I").
     */
    private fun findOwner(
        text: String,
        speaker: String?,
        participants: List<String>,
        knownNames: List<String>,
    ): Pair<String, String?> {
        fun bare(name: String) = name.replace(Regex("""^$TITLE\s+"""), "").trim()
        fun display(name: String): String {
            val first = bare(name).substringBefore(' ')
            return participants.firstOrNull { p -> p == name || bare(p) == bare(name) } ?:
                participants.firstOrNull { p -> bare(p).split(' ').contains(first) } ?: name
        }
        fun cleaned(raw: String): String? {
            // "Na Chinedu" → "Chinedu", "So Musa" → "Musa"
            val words = raw.split(Regex("\\s+")).dropWhile { it in PRONOUNS }
            return words.joinToString(" ").takeIf { it.isNotBlank() }
        }
        fun plausible(name: String): Boolean {
            val b = bare(name)
            val first = b.substringBefore(' ')
            return name != b || // has a title (Engr., Alhaji, …)
                participants.any { bare(it).split(' ').contains(first) } ||
                knownNames.any { it.equals(b, true) || it.split(' ').contains(first) }
        }

        ASSIGNED_TO.find(text)?.let { val n = it.groupValues[1]; return display(n) to n }
        NA_OWNER.find(text)?.let { m -> cleaned(m.groupValues[1])?.let { return display(it) to it } }
        NAMED_OWNER.findAll(text)
            .mapNotNull { cleaned(it.groupValues[1]) }
            .firstOrNull { it.substringBefore(' ') !in PRONOUNS && plausible(it) }
            ?.let { return display(it) to it }
        if (FIRST_PERSON.containsMatchIn(text) && speaker != null && !GENERIC_SPEAKER.matches(speaker)) return speaker to null
        return "TBD" to null
    }

    private fun formatPoint(u: Utterance, tone: SummaryTone): String {
        val sentence = standardise(u.text.replace(LEADING_FILLER, "")).replaceFirstChar { it.uppercase() }
        val named = u.speaker != null && !GENERIC_SPEAKER.matches(u.speaker)
        return when {
            tone == SummaryTone.CONCISE || !named -> sentence
            tone == SummaryTone.NIGERIAN_OFFICIAL -> "${u.speaker} informed the meeting: \"${sentence.trimEnd('.')}.\""
            else -> "${u.speaker}: $sentence"
        }
    }

    private fun formatDecision(u: Utterance, tone: SummaryTone): String {
        if (tone != SummaryTone.NIGERIAN_OFFICIAL) return formatPoint(u, tone)
        val sentence = standardise(u.text.replace(LEADING_FILLER, "")).trimEnd('.')
        val lead = DECISION_LEAD.find(sentence) ?: return "It was resolved that ${sentence.replaceFirstChar { it.lowercase() }}."
        val core = sentence.substring(lead.range.last + 1).trim().replaceFirstChar { it.lowercase() }
        // "We agreed to move…" → "It was resolved to move…"; "We agreed that X…" → "It was resolved that X…"
        val connector = if (lead.value.trimEnd().endsWith(" to", ignoreCase = true)) "to" else "that"
        return "It was resolved $connector $core."
    }

    /**
     * Light Nigerian Pidgin → standard English rewrite so the written minutes read as clear English
     * ("I go do am latest Monday" → "I will do it latest Monday"). Detection always runs on the
     * original words; only the text written into the minutes is rewritten.
     */
    fun standardise(text: String): String {
        var t = text.trim()
        t = Regex("""(?i)\babeg,?\s*""").replace(t, "")
        t = Regex("""(?i)\bno wahala\b""").replace(t, "no problem")
        t = Regex("""(?i)\b(we|una|dem|they) don (agree|decide|resolve)\b""").replace(t) { "${it.groupValues[1]} have ${it.groupValues[2]}d" }
        t = Regex("""(?i)\b(agreed?|decided?|resolved?) say\b""").replace(t) {
            val v = it.groupValues[1]
            (if (v.endsWith("d")) v else v + "d") + " that"
        }
        t = Regex("""(?i)\bmake we\b""").replace(t, "let us")
        t = Regex("""(?i)\bmake una\b""").replace(t, "please")
        t = Regex("""(?i)\bI fit\b""").replace(t, "I may")
        t = Regex("""(?i)\bwetin\b""").replace(t, "what")
        // "do am", "send am" → "do it", "send it" (but never touch "I am")
        t = Regex("""(?i)\b(\w+) am\b(?=\s*(?:$|[.,!?;]|(?:latest|by|before|tomorrow|today|now|next|on|for|quick|sharp|again|well)\b))""")
            .replace(t) { if (it.groupValues[1].equals("i", true)) it.value else "${it.groupValues[1]} it" }
        t = Regex("""(?i)\bgo (?=(?:start|begin|hold|happen|reach|be|come|send|do|call|handle|bring|submit|check|follow|pay|buy|meet|visit|share|prepare|present|collect|give|take|finish|end|tell|run|talk|write|print|distribute|revert)\b)""")
            .replace(t, "will ")
        t = Regex("""(?i)\be (?=(?:will|don|dey)\b)""").replace(t) { if (it.value[0] == 'E') "It " else "it " }
        t = Regex("""(?i)\buna\b""").replace(t, "you")
        return t.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun score(text: String): Int {
        var s = 0
        if (NUMBER.containsMatchIn(text)) s += 3
        s += KEYWORDS.findAll(text).count() * 2
        s += minOf(wordCount(text), 30) / 6
        return s
    }

    private fun isQuestion(text: String) = text.trimEnd().endsWith("?")
    private fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }

    companion object {
        private val SPEAKER_LINE = Regex("""^([A-Z][\p{L}.'\- ]{0,30}?|Speaker \d+)\s*:\s*(.+)$""")
        private val GENERIC_SPEAKER = Regex("""(?i)speaker\s*\d+|unknown|note""")
        /** Honorifics common in Nigerian meetings; kept with the name and never treated as a sentence end. */
        private const val TITLE =
            """(?:Alhaji|Alhaja|Hajiya|Hajia|Mallam|Malam|Chief|Dr|Engr|Mr|Mrs|Ms|Prof|Barr|Hon|Pharm|Arc|Pastor|Rev|Madam|Aunty|Uncle|Comrade|Comr)\.?"""
        private val SENTENCE_SPLIT =
            Regex("""(?<!\b(?:Dr|Mr|Mrs|Ms|Engr|Prof|Barr|Hon|Pharm|Arc|Rev|St|Jr|Sr|Comr)\.)(?<=[.!?])\s+(?=[A-Z0-9"'])""")
        private val NAME = Regex("""[A-Z][\p{L}'\-]+(?: [A-Z][\p{L}'\-]+)?""")
        private val PRESENT = Regex("""(?i)(?:present|attending|here|joined by)\s*(?:today)?\s*(?:are|is|:)?\s*([A-Z][^.]*?)(?: are here| is here|\.|$)""")

        private val DECISION = Regex(
            """(?i)\b(we (have )?(agreed|decided|resolved)|it was (agreed|decided|resolved|unanimously agreed)|(agreed|decided) (to|that)|decision|approved|resolved to|we will go with|let'?s go with|consensus|the house (agreed|resolved|adopted|approved)|motion (was )?(moved|seconded|adopted|carried)|was (duly )?adopted|we don agree|una don agree|it is agreed|unanimously)\b"""
        )
        /** Leading words removed when rewriting a decision as "It was resolved that …". */
        private val DECISION_LEAD = Regex(
            """(?i)^(we (have )?(agreed|decided|resolved)( last \w+)?|it was (agreed|decided|resolved|unanimously agreed)|the house (agreed|resolved)|we don agree|it is agreed)( that| to)?\s*"""
        )
        private val ACTION = Regex(
            """(?i)\b(i'll|i will|we'll|he'll|she'll|they'll|will (send|share|prepare|schedule|follow|call|draft|update|submit|organi[sz]e|check|confirm|circulate|review|contact|book|arrange|complete|finali[sz]e)|is going to|are going to|needs? to|must|should|action item|assigned to|responsible for|follow up|take care of|make sure|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|next|end of|close of business|cob)|kindly|liaise|revert|see to it|take it up|i go|we go|make i|make we|go (send|call|do|bring|check|follow|tell|run|handle)|handle am|do am|is to)\b"""
        )
        private val NEXT_STEP = Regex(
            """(?i)\b(next meeting|next week we|going forward|reconvene|we will meet|follow-up meeting|at the next|adjourned|adjournment|date of (the )?next meeting|we go meet)\b"""
        )
        private val ASSIGNED_TO = Regex("""(?i)(?:assigned to|responsible for this is|owner is)\s+([A-Z][\p{L}'\-]+)""")
        private val NAMED_OWNER =
            Regex("""((?:$TITLE\s+)?\b[A-Z][\p{L}'\-]+(?:\s+[A-Z][\p{L}'\-]+)?)\s+(?:will|is going to|needs to|need to|should|must|is to|go|to (?:send|share|prepare|follow))\b""")
        /** Pidgin focus construction: "Na Chinedu go handle am" = "Chinedu will handle it". */
        private val NA_OWNER =
            Regex("""\b[Nn]a\s+((?:$TITLE\s+)?[A-Z][\p{L}'\-]+(?:\s+[A-Z][\p{L}'\-]+)?)\s+(?:go|will|go fit|fit|suppose to|dey)\b""")
        private val FIRST_PERSON = Regex("""(?i)\b(i'll|i will|i am going to|i'm going to|i can|let me|i go|make i|i dey come|i'll revert|i will revert)\b""")
        private val PRONOUNS = setOf(
            "We", "I", "They", "You", "He", "She", "It", "This", "That", "Everyone", "Someone", "Somebody",
            "Team", "The", "So", "Then", "Also", "And", "But", "Yes", "Okay", "Ok", "Who", "What",
            "Abeg", "Oya", "Una", "Make", "Kindly", "Please", "Members", "Na", "E", "Dem", "Everybody", "Ehen",
        )
        private val DUE = Regex(
            """(?i)\b(latest by|by|before|on or before|no later than|latest|due)\s+((?:the\s+)?(?:end of (?:the )?(?:day|week|month|year)|close of business(?: (?:today|tomorrow|on \w+|\w+day))?|cob(?: \w+day)?|today|tomorrow|next tomorrow|next week (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|next \w+|this (?:week|weekend)|weekend|this \w+|(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\d{1,2}(?:st|nd|rd|th)?(?: of)? (?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*(?: \d{4})?|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w* \d{1,2}(?:st|nd|rd|th)?(?:,? \d{4})?|\d{1,2}/\d{1,2}(?:/\d{2,4})?))"""
        )
        /** Deadlines said without "by" ("call them tomorrow", "next tomorrow", "on 20th October"). */
        private val BARE_DUE = Regex(
            """(?i)\b(next tomorrow|tomorrow|today|tonight|next week(?: (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))?|(?<=on )\d{1,2}(?:st|nd|rd|th)?(?: of)? (?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*(?: \d{4})?)\b"""
        )
        private val LEADING_FILLER = Regex("""(?i)^((so|okay|ok|alright|also|and|well|yes|right|um|uh|abeg|oya|ehen|ehn|please|kindly|sir|ma)[,.!]?\s+)+""")
        private val FILLER = Regex("""(?i)^(thank you|thanks|good (morning|afternoon|evening)|hello|hi everyone|yes|okay|understood|you are welcome|god bless|amen|well done|noted)\b""")
        private val ATTENDANCE = Regex("""(?i)\b(are|is) (here|present)\b|\bpresent today\b|\bin attendance\b|\bapologies\b""")
        private val URGENT = Regex("""(?i)\b(urgent(ly)?|immediately|asap|as soon as possible|critical|top priority|without delay|today|tomorrow|next tomorrow|before close of business|cob)\b""")
        private val LOW_PRIORITY = Regex("""(?i)\b(when (you|we) (can|have time)|if possible|nice to have|later|no rush|eventually)\b""")
        private val NUMBER = Regex("""\d""")
        private val KEYWORDS = Regex(
            """(?i)\b(report|data|budget|plan|issue|problem|risk|challenge|progress|result|target|coverage|cases|training|delivery|deadline|cost|update|improv\w*|increase\w*|decrease\w*|delay\w*|percent|%)"""
        )
    }
}
