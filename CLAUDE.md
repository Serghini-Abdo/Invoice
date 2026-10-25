# Workshop Invoice: notes for Claude

Offline-first Android invoicing app for a small Moroccan metal workshop. Kotlin, Jetpack Compose,
Room. The owner reads little: large type, pictures, one decision per screen, optional speech.

## Read first

* `docs/day2/DECISIONS.md`: every product decision for the Day 2 upgrade. Do not re-ask what is there.
* `docs/day2/PROGRESS.md`: what each sprint delivered, what is verified, what is still open.
* `docs/day2/design-review.html`: the approved mock-ups (open in a browser).
* `README.md`, `docs/data-and-migrations.md`, `docs/calculation-rules.md`, `docs/testing.md`.

## Build and test

```bash
./gradlew -p domain test                  # pure JVM tests (most logic lives here)
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # needs an emulator or phone
```

Room exports its schema to `app/schemas/`; commit the new JSON whenever the version changes, and add a
migration plus a `MigrationTest` case (see `docs/data-and-migrations.md`).

## Rules that are easy to break

* Money is integer centimes, lengths integer thousandths; no floating point in calculations.
* Invoice items are immutable snapshots; catalogue edits never change saved invoices.
* Never use `fallbackToDestructiveMigration`. Older backups must stay restorable.
* User-facing screen text lives in `ui/Strings.kt`; printed invoice words in the domain `ArabicTerms`;
  spoken text in the domain `speech` package, always through `SpeechTexts.of(settings.speechLanguage)`
  (Darija by default: `DarijaTerms`, `DarijaNumbers`, `DarijaSpeech`; standard Arabic: `StandardSpeech`).
* Colour is never the only signal: every state has an icon and a word.
* A speaker tap only speaks: it never selects, presses or navigates.
* Speech is an aid. Every flow must work with voice off or with no Arabic voice installed.
* Android 7 to 9 (API 24 to 28): keep the existing legacy code, but do not spend effort on problems that
  only occur there.
* Do not add features beyond what the current sprint asks for.
