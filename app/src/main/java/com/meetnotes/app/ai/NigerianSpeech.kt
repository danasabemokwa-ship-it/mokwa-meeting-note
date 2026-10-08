package com.meetnotes.app.ai

/**
 * Context that helps speech and language models handle Nigerian meetings:
 * Nigerian-accented English, Nigerian Pidgin, code-switching with Hausa / Yoruba / Igbo,
 * Nigerian names and titles, and common Nigerian English meeting expressions.
 *
 * Speech models can't be retrained on the phone, but they follow context very well:
 *  - Whisper uses a short "prompt" as a hint for spelling, names and style.
 *  - Gemini / GPT / Claude follow written instructions about accents, Pidgin and idioms.
 * The user's own glossary (names, LGAs, acronyms) is the single biggest accuracy boost.
 */
object NigerianSpeech {

    /** Language-setting codes that mean "Nigerian English" or "Nigerian Pidgin". */
    const val NIGERIAN_ENGLISH = "en-NG"
    const val NIGERIAN_PIDGIN = "pcm"

    /**
     * Whisper has no Nigerian-specific codes; English is the closest model for Nigerian English and
     * Pidgin. Whisper also has no Igbo model, so Igbo falls back to auto-detect (Gemini handles Igbo
     * much better — the Settings label says so).
     */
    fun whisperLanguage(code: String): String = when (code) {
        NIGERIAN_ENGLISH, NIGERIAN_PIDGIN -> "en"
        in WHISPER_UNSUPPORTED -> "auto"
        else -> code
    }

    private val WHISPER_UNSUPPORTED = setOf("ig")

    private val COMMON_NAMES_AND_TERMS = listOf(
        "Alhaji", "Hajiya", "Mallam", "Engr.", "Pharm.", "Barr.", "Hon.", "Oga", "Madam",
        "Musa", "Aminu", "Abubakar", "Usman", "Ibrahim", "Yusuf", "Fatima", "Zainab", "Halima", "Aisha",
        "Chukwuemeka", "Chinedu", "Ngozi", "Ifeoma", "Emeka", "Adebayo", "Oluwaseun", "Funmilayo", "Babatunde", "Tunde",
        "Abuja", "Lagos", "Kano", "Kaduna", "Minna", "Ibadan", "Enugu", "Port Harcourt",
        "LGA", "naira", "NCDC", "NPHCDA", "WHO", "UNICEF",
    )

    /** Cleans a comma/newline separated glossary entered by the user. */
    fun parseGlossary(raw: String): List<String> =
        raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Whisper initial prompt. Whisper only reads roughly the last 224 tokens of a prompt, so the
     * user's own terms go first and the whole thing is kept short.
     */
    fun whisperPrompt(nigerian: Boolean, glossary: List<String>): String? {
        if (!nigerian && glossary.isEmpty()) return null
        val terms = (glossary + if (nigerian) COMMON_NAMES_AND_TERMS else emptyList()).distinct()
        val intro = if (nigerian) {
            "Minutes of a meeting in Nigeria. Speakers use Nigerian English and sometimes Pidgin, Hausa, Yoruba or Igbo."
        } else "Meeting transcript."
        return buildString {
            append(intro)
            if (terms.isNotEmpty()) append(" Names and terms: ").append(terms.joinToString(", ")).append('.')
        }.take(700)
    }

    /** Extra instructions for LLM-based transcription (Gemini). */
    fun transcriptionGuidance(nigerian: Boolean, glossary: List<String>): String = buildString {
        if (nigerian) {
            append("The speakers are Nigerian. Expect Nigerian-accented English and code-switching into Nigerian Pidgin, Hausa, Yoruba or Igbo. ")
            append("Write English and Pidgin exactly as spoken (keep words like 'abeg', 'wetin', 'dey', 'una', 'oya'). ")
            append("When a phrase is in Hausa, Yoruba or Igbo, transcribe it and add an English translation in square brackets right after it. ")
            append("Spell Nigerian names, titles (Alhaji, Hajiya, Mallam, Engr., Hon.), places and acronyms correctly. ")
        }
        if (glossary.isNotEmpty()) {
            append("Use these exact spellings when the words occur: ${glossary.joinToString(", ")}. ")
        }
    }

    /** Extra instructions for the minutes writer (all LLM providers). */
    fun minutesGuidance(nigerian: Boolean, glossary: List<String>): String = buildString {
        if (nigerian) {
            appendLine("The meeting took place in Nigeria. Interpret Nigerian English, Nigerian Pidgin and mixed-language speech correctly:")
            appendLine("- \"revert\" = reply / get back; \"on seat\" = in the office; \"flag off\" = launch; \"next tomorrow\" = the day after tomorrow;")
            appendLine("  \"the house\" = the members present; \"moved\"/\"seconded\" = a motion; \"AOB\" = any other business; \"COB\" = close of business.")
            appendLine("- Pidgin: \"I go do am\" = I will do it; \"make we\" = let us; \"we don agree\" = we have agreed; \"e don do\" = that is enough / it is done.")
            appendLine("- Hausa, Yoruba or Igbo statements: understand them and record their meaning in English.")
            appendLine("- Keep people's titles with their names (Alhaji, Hajiya, Mallam, Dr., Engr., Hon., Chief).")
            appendLine("Write the minutes in clear, standard English.")
        }
        if (glossary.isNotEmpty()) {
            appendLine("Correct spellings of names and terms used in this organisation: ${glossary.joinToString(", ")}.")
        }
    }
}
