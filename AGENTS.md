# Workshop Invoice: notes for coding agents (Codex, Claude Code, others)

`CLAUDE.md` carries the same rules for Claude Code; keep the two files in step when you change one.

Offline-first Android app for a small Moroccan metal workshop. Kotlin, Jetpack Compose, Room. The owner
reads little: large type, pictures, one decision per screen, optional speech. The app is still in development:
there are no real receipts on any phone yet.

## Read first

* `docs/day2/DECISIONS.md`: the final state of every product decision. **[to build]** marks what is decided
  but not built. Do not re-ask what is there; check its Open questions before guessing.
* `docs/day2/PROGRESS.md`: current state, what is verified, known issues, and the roadmap. Start there to
  find the next sprint.
* `docs/day3/PLAN.md`: how to build each Day 3 sprint, step by step. Follow it exactly, one chapter per
  session, and end with its hand-off report (Part F).
* `README.md` (the app as built), `docs/calculation-rules.md`, `docs/data-and-migrations.md`, `docs/testing.md`.
* Mock-ups: `docs/day2/design-review.html` (Day 2).

## Decided in the 2026-11-01 review

* The document is a **receipt (وصل)**, not an invoice, in screen text, printed title and speech. Internal code
  names (`Invoice*`) stay. The palette is **blue**; green means paid only (and the printed receipt keeps its
  own colours). Buttons and cards have no arrows. Nothing is spoken automatically.
* Coming: speakers move to one system (`SpeakableText` / `SpeakableButton` and a speaker column); do not add
  new hand-placed speakers in the meantime.

## Setup

* JDK 17 and the Android SDK (platform 35). `local.properties` (`sdk.dir=...`) is machine-specific and not
  committed; create it if Gradle cannot find the SDK. Use the checked-in wrapper (`./gradlew`, `gradlew.bat`).
* `domain/` is pure Kotlin/JVM (most logic and most tests); `app/` is the Android module.

## Build and test

```bash
./gradlew -p domain test                  # pure JVM tests, JDK only: run this after any domain change
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -PdeviceTests   # OPTIONAL, see below
```

* **The on-device suite is optional.** `connected*AndroidTest` tasks are skipped unless `-PdeviceTests` is
  passed. Run it when a change touches Room, migrations, export, or UI behaviour the JVM tests cannot see.
  If you cannot run it, say so instead of claiming the change is verified on a device.
* **Instrumented tests wipe the app database.** Target one device explicitly (`adb -s` / `ANDROID_SERIAL`),
  ask the owner before using his phone, and never run them on a phone with real receipts.
* The owner's PC cannot run the emulator and Gradle together (7.7 GB RAM): build first, `./gradlew --stop`,
  then install the APKs and run `adb shell am instrument`.
* Room exports its schema to `app/schemas/`; every version bump ships a migration, a `MigrationTest` case and
  the committed JSON (see `docs/data-and-migrations.md`). The current version is 6.

## Rules that are easy to break

* Money is integer centimes, lengths integer thousandths; no floating point in calculations.
* Receipt items are immutable snapshots; catalogue edits never change saved receipts.
* Never use `fallbackToDestructiveMigration`. Older backups must stay restorable.
* The calculation method stays pluggable: no new `when (formula)` branches.
* Products have no saved price, on purpose: every job has its own size and material.
* User-facing screen text lives in `ui/Strings.kt`; printed words in the domain `ArabicTerms`; spoken text in
  the domain `speech` package, always through `SpeechTexts.of(settings.speechLanguage)` (Darija by default).
* Colour is never the only signal: every state has an icon and a word.
* A speaker tap only speaks: it never selects, presses or navigates.
* Speech is an aid. Every flow must work with voice off or with no Arabic voice installed.
* Android 7 to 9 (API 24 to 28): keep the existing legacy code, but do not spend effort on problems that
  only occur there.
* Do not add features beyond what the current sprint asks for.

## Working conventions

* Files are UTF-8 with **LF line endings** (enforced by `.gitattributes`) and contain Arabic text. Do not
  rewrite them with tools that assume a legacy code page or that switch them to CRLF (for example Windows
  PowerShell 5.1 `Get-Content`/`Set-Content`); use a proper editor or patch tool.
* Branches: `main` is the working line; sprint branches are `day3/sprint-N`. Commit small, with a message that
  says what changed and why. Do not push unless the owner asks.
* Update `docs/day2/PROGRESS.md` when a sprint lands or a verification status changes, and
  `docs/day2/DECISIONS.md` when the owner decides something (move it out of Open questions, drop the
  **[to build]** mark once built). Remove text that is no longer true instead of adding notes beside it.
