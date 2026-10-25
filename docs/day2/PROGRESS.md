# Day 2 progress

Decisions: [DECISIONS.md](DECISIONS.md). Mock-ups: [design-review.html](design-review.html).

## Sprint plan

There were no Sprint 1 or Sprint 2 branches when Sprint 3 started (2026-10-25); `day2/sprint-3` was
branched from `main`. The owner chose to build the **speech core first** and to place speakers and mics
on the screens after the layout sprint.

| Sprint | Scope | State |
|---|---|---|
| Design | Four review rounds, approved 2026-10-25 | Done |
| 3 (speech core) | TTS wrapper, voice check and prompt, Darija speech, speech rate, speech recognizer core | Done, see below |
| Layout | Theme tokens, forced LTR, new top bar, speaker-beside-button component, unit settings, fields, catalogue photos, invoice table | Not started |
| Placement | Speakers on every row/card/field/CTA; mic on customer name and product name | Not started (needs the layout sprint) |

## Sprint 3: speech core

### Delivered

* **Darija speech, offline** (`domain/speech`):
  * `DarijaTerms`: every spoken label, in Darija spelling, kept apart from screen and invoice text.
  * `DarijaNumbers`: numbers as Darija words (جوج, تلاتة, حضاش, عشرين, ميتين, تلت آلاف, مليون …),
    decimals ("جوج فاصلة تنين و خمسين"), metres ("متر و عشرين سانتي") and centimetres.
  * `DarijaSpeech`: prices (درهم / دراهم / سنتيم), quantities, measurements, and one natural sentence per
    row, per calculation card and per total + deposit + balance card. Method names come from one table,
    so no new `when (formula)` branch was added.
* **TextToSpeech wrapper** (`platform/SpeechController`, `platform/Speaker`):
  * offline voices only (a voice that needs the network counts as missing); ar-MA first, then any Arabic;
  * every speech uses `QUEUE_FLUSH`, so tapping another speaker stops the current one;
  * speech-rate setting applied on every utterance;
  * voice on/off is the existing audio switch; `Speaker` (through the pure `SpeechGate`) never speaks
    with audio off and never shows a prompt then;
  * automatic speech (totals on arrival) only plays when a voice is ready; it never prompts.
* **Missing Arabic voice**: the first speaker tap without an offline Arabic voice opens a clear dialog
  (`VoiceMissingDialog`) with "تثبيت صوت عربي" (opens the system voice-data screen) and "لاحقاً"; once per
  app run, later taps stay silent with a short toast. The flow re-checks the voice when it resumes.
  The Settings banner and install button were already there.
* **Existing speech switched to Darija**: product name on catalogue cards, item total on the confirmation
  screen, invoice summary on the review screen, "تجربة الصوت" in Settings.
* **Speech rate**: `AppSettings.speechRate` (slow 0.8 / normal 1.0 / fast 1.25), three large choices in the
  Settings audio section. Room schema **version 2** (`MIGRATION_1_2`, `app_settings.speechRate` default
  `normal`) with a migration test; backups carry the rate, and older backups restore with `normal`.
* **Speak-to-fill core** (`platform/Dictation`, `ui/components/MicButton`):
  * `SpeechRecognizer`, ar-MA, `EXTRA_PREFER_OFFLINE`; recognised text is tidied and limited to the
    field's length, then put in the field as ordinary editable text;
  * microphone permission (`RECORD_AUDIO`) requested on the first mic tap only, after a short
    explanation; later refusals only show a hint;
  * hint when the offline Arabic pack is missing (checked up front on Android 13+, otherwise from the
    recognizer's error), plus hints for no recogniser, nothing understood and busy;
  * `MicButton` is built but **not placed on any field yet** (placement sprint).

### Tests

| Test | Where | Result |
|---|---|---|
| Darija number words for prices, quantities, measurements; row, calculation and totals sentences | `domain` `SpeechTest` (13 tests) | Pass (JVM) |
| Voice off never speaks and never prompts; missing voice prompts once | `domain` `SpeechTest` | Pass (JVM) |
| Recognizer error mapping, recognised-text tidying | `domain` `SpeechTest` | Pass (JVM) |
| Speech rate in backups, older backups restore at normal | `domain` `SpeechTest` | Pass (JVM) |
| All existing domain tests | `./gradlew -p domain test` | Pass |
| Speaker tap on a product card never selects | `SpeakerTapTest` (instrumented) | Pass (phone) |
| Replay speaker on the confirmation never confirms or leaves | `SpeakerTapTest` (instrumented) | Pass (phone) |
| Voice off: speakers silent, no prompt, flow continues | `VoiceOffFlowTest` (instrumented) | Pass (phone) |
| Migration 1 → 2 keeps settings and adds the normal rate | `MigrationTest` (instrumented) | Pass (phone) |
| All instrumented tests | Samsung SM-S931B, Android 16 | **OK (49 tests)**: 48 pass, 1 skipped (`AppFolderStoreTest.a_…`, Android 7–9 only) |
| Build, lint | `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | Pass |
| Mic button only on text fields | (placement sprint) | Not yet testable: the mic is not placed |

### Verified on the emulator vs. must be tested on a real phone

The instrumented suite ran on the owner's phone (Samsung SM-S931B, Android 16) with the owner's approval,
after the API 37 emulator could not start the app in time on this PC (7.7 GB RAM; the app process hit an
"app not responding" timeout at startup and Gradle ran out of memory). The app on that phone holds only test
data. The suite was run with `adb shell am instrument` on the APKs built by `assembleDebug` /
`assembleDebugAndroidTest`.

**Verified automatically (JVM tests and on the phone):**

* Darija words for prices, quantities and measurements; the row, calculation-card and totals sentences.
* Speaker taps never select, confirm or navigate (product card, confirmation replay).
* With voice off, speakers are silent, no voice prompt appears, and the invoice flow continues.
* The missing-voice prompt logic (once per run, never with audio off); recognizer error to hint mapping.
* Schema 1 → 2 migration and backup compatibility of the speech rate.
* Build, lint and every existing test.

**Not verified yet; must be tested by hand on a real phone (ideally the owner's):**

* Whether an **offline Arabic voice** is installed, and what Settings → الصوت shows (ready / no Arabic voice).
* **Voice quality** and how the Darija spelling and the Darija **number words** actually sound
  ("ألفين و خمس مية و عشرين درهم", "متر و عشرين سانتي"); adjust `DarijaTerms` / `DarijaNumbers` if a voice
  mispronounces them.
* **Speech rate**: whether slow / normal / fast (0.8 / 1.0 / 1.25) feel right; "تجربة الصوت" at each.
* The **missing-voice dialog** on the first speaker tap, and the **voice download** flow through
  "تثبيت صوت عربي", then that the app picks up the new voice when it comes back.
* Tapping another speaker while one is talking stops the first.
* **Speak-to-fill** is not on any screen yet (placement sprint). When it is: the microphone permission
  explanation and system dialog on the first mic tap only, recognition of Moroccan Arabic names with the
  **offline ar-MA pack**, the hint when the pack is missing, and that the text stays editable.

### Notes for the next sprints

* `SpokenText` / `ArabicNumberWords` (standard Arabic) are no longer used by the app; their tests still
  pass. Remove them in a cleanup once nothing needs them.
* `Strings.AUDIO_TEST_SENTENCE` is no longer used (the voice test now speaks `DarijaTerms.VOICE_TEST`).
* Placement must use `Speaker.say(...)` for taps and `Speaker.sayIfReady(...)` for automatic speech, and
  `DarijaSpeech` for every sentence.
* Instrumented tests clear the app database: always target one device explicitly (`adb -s` /
  `ANDROID_SERIAL`), and never a phone with real invoices.
* On this PC the emulator and Gradle do not fit in memory together: build first, stop Gradle
  (`./gradlew --stop`), then start the emulator; or install the APKs and run `adb shell am instrument`.
