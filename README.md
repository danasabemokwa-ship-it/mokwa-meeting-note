# Mokwa Meeting Note — Meeting Recorder & Minutes Generator (Android)

Record a meeting, turn it into a transcript, and get structured **minutes of meeting** with
decisions and **action points** (owner, task, due date) — then track, export and share them.

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · WorkManager · Media3 · Retrofit · DataStore
Min SDK 26 (Android 8.0) · Target SDK 35 · phones and tablets · light/dark/dynamic colour.

---

## 1. Open and run (5 minutes)

1. Install **Android Studio Ladybug (2024.2) or newer**.
2. *File → Open…* and choose this `MokwaMeetingNote` folder. Let Gradle sync (first sync downloads dependencies).
3. Press **Run ▶** on a phone (recommended — emulators have poor microphones) or emulator.

The app works immediately **with no API keys and no models**:

- Transcription engine defaults to **Demo** (inserts a sample transcript so you can try the full flow).
- Minutes default to **Offline smart extraction** (runs entirely on the phone).

Switch to a real engine in **Settings** (see sections 3–4).

Build an installable APK: *Build → Build App Bundle(s)/APK(s) → Build APK(s)*, or `./gradlew assembleDebug`
(output: `app/build/outputs/apk/debug/app-debug.apk`).

Run the unit tests: `./gradlew test`.

---

## 2. What the app does

| Area | Features |
|---|---|
| **Recording** | Pause / resume / stop, live waveform and timer, foreground service with notification controls (keeps recording with the screen off), auto-named files `Meeting_YYYY-MM-DD_HH-mm`, three quality presets, discard with confirmation. Files are kept in app-private storage. |
| **Transcription** | Pluggable engines: Demo, **on-device Whisper (whisper.cpp)**, **OpenAI Whisper**, **Google Gemini**. Progress shown, partial text where the engine supports it, editable transcript, language selection (incl. Hausa, Yoruba, French, Arabic). |
| **Minutes** | Fixed structure: Title · Date & Time · Participants · Key Discussion Points · Decisions Made · Action Items (Owner/Task/Due, "TBD" if not stated) · Next Steps. Engines: offline extraction, OpenAI, Gemini, Anthropic Claude. Tones: Formal / Concise / Detailed; regenerate any time; fully editable. **If a cloud model fails, the app automatically falls back to offline extraction** and tells you. |
| **Action items** | Checklist with done state, add / edit / delete, add to calendar (pre-filled event, no calendar permission needed), copy or share the list. |
| **History** | Search across titles, transcripts, tags, notes, participants; date filters; status chips (Recorded → Transcribed → Summarized); grid on tablets. |
| **Meeting detail** | Audio player (seek, 1×–2× speed) + tabs for Minutes / Transcript / Actions / Notes & tags. On tablets the transcript sits beside the minutes. |
| **Nigerian speech** | Default language **Nigerian English**; also Nigerian Pidgin, Hausa, Yoruba, Igbo and mixed (code-switching). Whisper gets a Nigerian context prompt, Gemini/GPT/Claude get instructions for Nigerian English idioms ("revert", "next tomorrow", "flag off"), Pidgin ("I go do am", "we don agree say") and titles (Alhaji, Hajiya, Mallam, Engr., Hon.). A **glossary** in Settings (names, LGAs, wards, acronyms) fixes spellings everywhere. Offline extraction also understands Pidgin owners ("Na Chinedu go handle am"), titles and Nigerian deadlines ("latest by Friday", "close of business Monday", "next tomorrow"), and rewrites Pidgin into standard English in the minutes. |
| **Minutes tone** | Formal, Concise, Detailed, or **Nigerian official** (public-service style: "The Chairman informed members that…", "It was resolved that…", matters arising, AOB, adjournment). |
| **Export & share** | **Download as PDF or Word (.docx)** straight to the phone (system "Save as" — Downloads, Google Drive, etc.). Word files use real styles, a formatted action-item table and page numbers, and open in Microsoft Word, WPS Office, Google Docs and LibreOffice. Share as PDF, Word, Markdown or plain text, share via WhatsApp / email / Slack through the system share sheet; share the audio file. |
| **Settings** | Engines, language, default tone, model names, encrypted API keys, audio quality, auto-process, auto-delete after 30/60/90/180 days. |

---

## 3. Cloud engines (optional)

Get a key and paste it in **Settings → API keys**. Keys are stored with `EncryptedSharedPreferences`
(Android Keystore) and are only sent to the provider they belong to.

| Provider | Used for | Get a key |
|---|---|---|
| OpenAI | Whisper transcription + GPT minutes | https://platform.openai.com/api-keys |
| Google Gemini | Audio transcription + minutes | https://aistudio.google.com/app/apikey |
| Anthropic | Claude minutes | https://console.anthropic.com/settings/keys |

**Model names** are editable in Settings (defaults: `gpt-4o-mini`, `gemini-2.5-flash`, `claude-haiku-5-5`).
Providers rename models over time — if you get a "404 / check the model name" error, put in a current
model name from the provider's docs.

**File-size limits:** OpenAI accepts up to 25 MB per file and Gemini inline audio about 15–20 MB.
For long meetings choose **Settings → Recording → Compact** (≈14 MB per hour), or use on-device Whisper,
which has no limit.

**Developer shortcut:** you can instead put keys in `local.properties`:

```
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=...
ANTHROPIC_API_KEY=...
```

They are compiled into `BuildConfig` and used only when no key is entered in Settings.
**Never distribute an APK built this way** — keys inside an APK can be extracted.

**Privacy note:** with a cloud engine, the audio (transcription) or transcript (minutes) is sent to that
provider. Choose on-device Whisper + Offline extraction for fully offline, private processing.
Always get participants' consent before recording a meeting.

---

## 4. On-device Whisper (fully offline transcription)

The Kotlin side is complete (`ai/transcription/LocalWhisperEngine.kt` + `AudioDecoder.kt`, which converts
recordings to 16 kHz mono PCM). The native **whisper.cpp** library has to be compiled with the NDK:

1. In Android Studio: *SDK Manager → SDK Tools* → install **NDK (Side by side)** and **CMake**.
2. Clone whisper.cpp: `git clone https://github.com/ggerganov/whisper.cpp`.
3. Copy `whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/` into `app/src/main/cpp/` and
   follow its `CMakeLists.txt` (it points at the whisper.cpp sources — adjust the path).
4. In `jni.c`, rename every function prefix
   `Java_com_whispercpp_whisper_WhisperLib_00024Companion_` → `Java_com_meetnotes_app_ai_transcription_WhisperLib_`,
   make the library name `whisper_android`, and add `jstring language` and `jstring prompt` parameters to
   `fullTranscribe` that set `params.language` (`"auto"` lets Whisper detect it) and `params.initial_prompt`
   (the Nigerian context + glossary that improves names and accent handling).
5. In `app/build.gradle.kts` → `android { }` add:
   ```kotlin
   externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
   defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } }
   ```
6. Download a model to the phone, e.g. from https://huggingface.co/ggerganov/whisper.cpp
   - `ggml-base.bin` (~142 MB) — fast, good for English
   - `ggml-small.bin` (~466 MB) — noticeably better for Hausa and other languages
7. In the app: **Settings → Transcription → Import model**, then select **On-device Whisper**.

Until the native library is included, Settings shows "Native library not bundled" and the engine is
simply unavailable — nothing crashes.

---

## 5. Project structure

```
app/src/main/java/com/meetnotes/app/
├── MeetNotesApp.kt              Hilt application, notification channels, WorkManager config
├── MainActivity.kt              Single activity, start destination, notification deep link
├── audio/                       AudioRecorder (MediaRecorder AAC .m4a), RecordingService (foreground, mic),
│                                RecordingStateHolder (state shared with UI)
├── ai/NigerianSpeech.kt          Nigerian English / Pidgin / Hausa-Yoruba-Igbo context for every engine
├── ai/transcription/            TranscriptionEngine interface + Demo, LocalWhisper (JNI), OpenAI, Gemini,
│                                AudioDecoder (any audio → 16 kHz mono float PCM)
├── ai/summarization/            Summarizer interface, MinutesPrompts (system prompt + JSON contract),
│                                MinutesParser, OfflineRuleSummarizer, OpenAI/Gemini/Anthropic summarizers
├── data/local/                  Room entities, DAOs, database
├── data/remote/                 Retrofit APIs + DTOs, readable API errors
├── data/prefs/                  DataStore settings, encrypted API-key store
├── data/repository/             MeetingRepositoryImpl
├── domain/                      Models, repository interface, use cases (schedule/transcribe/summarize)
├── work/                        ProcessMeetingWorker (foreground job), CleanupWorker (auto-delete)
├── export/                      MinutesFormatter (MD/TXT), DocBlock, PdfExporter, DocxExporter (Word),
│                                ExportManager (download/save-as, share, calendar)
├── ui/                          Compose: home, record, detail, settings, onboarding, navigation, theme
└── util/Formatters.kt
```

**Adding another AI provider:** implement `TranscriptionEngine` or `Summarizer`, add an enum value in
`domain/model/Models.kt`, and register it in the matching factory. The minutes prompt and JSON contract
in `MinutesPrompts.kt` are shared by every LLM, so the output structure stays identical across providers.

### Processing pipeline

```
Stop recording ─► Room: meeting (RECORDED)
              └─► WorkManager ProcessMeetingWorker (if "Process automatically")
                    ├─ TranscribeMeetingUseCase ─► engine.transcribe() ─► TRANSCRIBED
                    └─ SummarizeMeetingUseCase  ─► summarizer.summarize() ─► SUMMARIZED
                                                   (cloud error ⇒ offline fallback + note)
                                                   action items ─► checklist table
```

---

## 6. Notes & known limitations

- **Not compiled in this environment.** The source was written against the dependency versions in
  `app/build.gradle.kts` (AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01). The offline minutes
  extractor was compiled and run against the sample transcript; the Android/Compose layers need the
  Android SDK, so expect a first Gradle sync and possibly small fixes if you change versions.
- The Demo engine ignores your audio — switch engines before relying on transcripts.
- Offline extraction relies on speaker labels and phrasing ("Musa will send… by Friday"); it is a
  helpful draft, not a substitute for an LLM. Edit the minutes as needed.
- Speaker diarisation: Whisper doesn't label speakers. Gemini is asked to label speakers; you can also
  edit the transcript to add `Name:` prefixes, which improves offline extraction.
- Release builds: minification is off; enable it only after testing the rules in `proguard-rules.pro`.

---

## 7. Getting the best results with Nigerian voices

- Keep **Spoken language = Nigerian English** unless the meeting is mainly Hausa/Yoruba/Igbo.
- Fill in the **Glossary** (Settings → Transcription): colleagues' names, LGAs, wards, facility names
  and acronyms (e.g. *Alhaji Sani, Hajiya Rakiya, Mokwa LGA, Kpaki ward, NPHCDA, DSNO*). This is the
  biggest single accuracy gain for every engine.
- For meetings that switch a lot between English, Pidgin and Hausa/Yoruba/Igbo, **Gemini** transcription
  handles code-switching best and adds English translations in [brackets]. Whisper is very good on
  Nigerian-accented English and Hausa/Yoruba but has no Igbo model.
- Place the phone in the middle of the table; "Standard" quality is enough for speech.
