package com.meetnotes.app

import com.meetnotes.app.ai.summarization.MeetingMeta
import com.meetnotes.app.ai.summarization.MinutesParser
import com.meetnotes.app.ai.summarization.OfflineRuleSummarizer
import com.meetnotes.app.ai.transcription.DemoTranscriptionEngine
import com.meetnotes.app.domain.model.SummaryTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSummarizerTest {

    private val summarizer = OfflineRuleSummarizer()
    private val meta = MeetingMeta(title = "Weekly review", dateTime = "Thu, 8 Oct 2026 · 10:00")

    @Test
    fun `extracts participants, decisions and owned action items from demo transcript`() {
        val s = summarizer.extract(DemoTranscriptionEngine.SAMPLE, meta, SummaryTone.FORMAL)

        assertTrue(s.participants.containsAll(listOf("Amina", "Musa", "Grace", "David")))
        assertTrue("decision about Thursdays", s.decisions.any { it.contains("Thursdays") })

        val musa = s.actionItems.first { it.task.contains("timeliness report") }
        assertEquals("Musa", musa.owner)
        assertEquals("Friday", musa.dueDate)

        val grace = s.actionItems.first { it.task.contains("refresher training") }
        assertEquals("Grace", grace.owner)
        assertEquals("Next Tuesday", grace.dueDate)

        val david = s.actionItems.first { it.task.contains("supplier") }
        assertEquals("David", david.owner)
        assertEquals("15 October", david.dueDate)

        assertTrue(s.nextSteps.any { it.contains("Next meeting", ignoreCase = true) })
        assertTrue(s.keyPoints.isNotEmpty())
    }

    @Test
    fun `concise tone limits key points`() {
        val s = summarizer.extract(DemoTranscriptionEngine.SAMPLE, meta, SummaryTone.CONCISE)
        assertTrue(s.keyPoints.size <= 4)
    }

    private val nigerianMeeting = """
        Alhaji Sani: Good morning everybody. Engr. Bello will present the borehole report by Wednesday.
        Hajiya Rakiya: Na Chinedu go handle the vaccine pickup from Minna. E go reach before Friday.
        Dr. Ngozi Okafor: We don agree say the outreach go start on 20th October.
        Alhaji Sani: It was resolved that each ward must submit its tally sheet.
        Mallam Garba: Mallam Garba will call the ward heads tomorrow.
    """.trimIndent()

    @Test
    fun `understands Nigerian titles, Pidgin owners and bare deadlines`() {
        val s = summarizer.extract(nigerianMeeting, meta, SummaryTone.NIGERIAN_OFFICIAL)

        assertTrue(s.participants.contains("Dr. Ngozi Okafor"))
        val bello = s.actionItems.first { it.task.contains("borehole report") }
        assertEquals("Engr. Bello", bello.owner)
        assertEquals("Wednesday", bello.dueDate)

        val chinedu = s.actionItems.first { it.task.contains("vaccine pickup") }
        assertEquals("Chinedu", chinedu.owner)
        assertTrue(chinedu.task.startsWith("Handle"))

        val garba = s.actionItems.first { it.task.contains("ward heads") }
        assertEquals("Mallam Garba", garba.owner)
        assertEquals("Tomorrow", garba.dueDate)

        assertTrue(s.decisions.contains("It was resolved that the outreach will start on 20th October."))
    }

    @Test
    fun `rewrites Pidgin into standard English for the minutes`() {
        assertEquals("I will do it latest Monday", summarizer.standardise("Abeg, I go do am latest Monday"))
        assertEquals("We have agreed that it will start", summarizer.standardise("We don agree say e go start"))
        assertEquals("I am going now", summarizer.standardise("I am going now"))
    }

    @Test
    fun `parser accepts fenced JSON with nulls and blanks`() {
        val raw = """
            Here are the minutes:
            ```json
            {"meeting_title":"Budget sync","date_time":"Today","participants":["Ali", ""],
             "key_discussion_points":["Budget is on track"],"decisions_made":[],
             "action_items":[{"owner":"","task":"Send report","due_date":null},{"owner":"Ali","task":""}],
             "next_steps":["Meet again"]}
            ```
        """.trimIndent()
        val s = MinutesParser.parse(raw)
        assertEquals("Budget sync", s.title)
        assertEquals(listOf("Ali"), s.participants)
        assertEquals(1, s.actionItems.size)
        assertEquals("TBD", s.actionItems[0].owner)
        assertEquals("TBD", s.actionItems[0].dueDate)
    }
}
