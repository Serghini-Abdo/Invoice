# Workshop Invoice: notes for coding agents (Codex, Claude Code, others)

Offline-first Android invoicing app for a small Moroccan metal workshop. Kotlin, Jetpack Compose,
Room. The owner reads little: large type, pictures, one decision per screen, optional speech.

`CLAUDE.md` carries the same rules for Claude Code; keep the two files in step when you change one.

## Read first

* `docs/day2/DECISIONS.md`: every product decision for the Day 2 upgrade. Do not re-ask what is there.
* `docs/day2/PROGRESS.md`: what each sprint delivered, what is verified, what is still open. Start here to
  find the next sprint; `docs/day2/` also holds the step-by-step plan for sprints 4 to 7.
* `docs/day2/design-review.html`: the approved mock-ups (open in a browser).
* `README.md`, `docs/data-and-migrations.md`, `docs/calculation-rules.md`, `docs/testing.md`.

## Setup

* JDK 17 and the Android SDK (platform 35). `local.properties` (`sdk.dir=...`) is machine-specific and not
  committed; create it if Gradle cannot find the SDK. Use the checked-in wrapper (`./gradlew`, `gradlew.bat`).
* Layout: `domain/` is pure Kotlin/JVM (most logic and most tests); `app/` is the Android module.

## Build and test

```bash
./gradlew -p domain test                  # pure JVM tests, JDK only: run this after any domain change
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -PdeviceTests   # OPTIONAL, see below
```

**The on-device suite is optional.** `connected*AndroidTest` tasks are skipped unless `-PdeviceTests` is
passed, so a sandbox or machine with no emulator or phone never needs it. Run it only when a change touches
Room, migrations, export, or UI behaviour that the JVM tests cannot see, and a device is attached. CI always
passes the flag. If you cannot run it, say so in your hand-off rather than claiming the change is verified
on a device.

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

## Working conventions

* Files are UTF-8 and contain Arabic text. Do not rewrite them with tools that assume a legacy code page
  (for example Windows PowerShell 5.1 `Get-Content`/`Set-Content` without `-Encoding utf8`); use a proper
  editor or patch tool.
* Branches: `main` is the working line; sprint branches are `day2/sprint-N`. Commit small, with a message that
  says what changed and why.
* Update `docs/day2/PROGRESS.md` when a sprint deliverable lands or its verification status changes.
