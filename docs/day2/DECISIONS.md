# Day 2 decisions

Approved by the owner on 2026-10-25 after four design rounds. The mock-ups are in
[design-review.html](design-review.html). Later rounds override earlier ones; this file is the final state.

## Layout and header

* The app layout is forced **LTR**. Arabic text stays right-aligned inside its element. The printed and
  exported invoice stays **RTL**.
* Labels on the right, amounts on the left (as on the printed invoice).
* One reusable top bar: back arrow on the **left**, title on the **right** with its speaker just left of it.
  Screens with their own icon (item details, item confirmation) show it **centred**.
* Forward arrows point **right** and sit on the right of the label ("→ متابعة").
* The catalogue grid order is not important; keep today's order.

## Palette (theme tokens only, no hard-coded colours)

* Primary green `#2E7D32` (light `#66BB6A`), secondary yellow `#F2B705`, error `#D32F2F`, gray `#6B7280`,
  surface `#F3F4F6`. Never white text on yellow (1.8 : 1); yellow buttons get a `#A67C00` edge.
* Green means "paid". An unpaid balance is bold ink, not green.
* The "+ إضافة منتوج" button is yellow, at least 48 dp (64 dp drawn), with a visible pressed state.

## Speakers (TextToSpeech, offline, free, no recordings)

* **Labelled buttons**: a speaker tile on the button's **left**, `| speaker | label/icon |`, styled like its
  button (same colour, border, radius, height), 4 dp gap, its own target of at least 48 dp.
* **Home cards and product-catalogue cards** keep the speaker **inside** the card, as its own target.
* **Content** (titles, field labels, item rows, invoice lines in previews and history): one round speaker each.
* **One speaker per card** for the calculation card ("الحساب: بالمتر المربع") and for the
  total + deposit + balance card; it reads the card as one natural sentence.
* **No speaker** on icon-only controls (back arrow, keypad keys, − / +, mic, photo button).
* **No speakers in Settings and Backup**, except the المنتوجات card in Settings and its screens
  (product list, product edit, including the calculation-method options).
* A speaker tap never selects, presses or navigates. Tapping another speaker stops the current speech.
* Voice settings: on/off (existing), speech rate as **3 presets** (slow / normal / fast).
* Spoken text is Darija spelling; numbers are spoken as Darija words generated offline.
* No Arabic voice installed: a clear prompt explains how to download the voice data; everything stays
  usable silently.

## Speak-to-fill (mic)

* Android `SpeechRecognizer`, locale ar-MA, offline preferred. The microphone permission is accepted;
  it is requested on the first mic tap only. If the offline speech pack is missing, show a hint.
* Mic only on **customer name** and **product name**. There is no notes field (none will be added) and
  no mic on search or on any numeric field. Recognised text stays editable.

## Units

* One global setting: cm or m. The per-item m/cm switch is removed; the field shows only the unit from
  Settings, where the switch was. Labels (العرض, الارتفاع, الثمن للمتر المربع, الكمية) sit on the right,
  directly above their icons.
* Second setting: unit label script, Arabic (سم / م) or Latin (cm / m); labels only, digits unchanged.
  It applies to screens, printed invoices, and re-exports of old invoices.
* Stored values keep the existing normalisation. Each invoice stores the unit it was created with; an old
  invoice that mixes m and cm takes its first item's unit, and its existing lines stay as typed.
* Latin unit labels inside RTL text must be wrapped as LTR isolates.

## Fields and pictures

* Small illustration beside each field: ↔ width, ↕ height, DH coin for the unit price (never "$"),
  stacked boxes for quantity, person and phone for the customer.
* Image import (system photo picker) **only in product edit**. The choose-product screen has no image change.
* Each invoice item keeps its **own literal copy** of the image file. Backups include the image files.

## Invoice output

* Table columns, left to right: Price (line total) | Measurement / Formula | Quantity | Name/Icon.
  **Option A**: the unit price is shown inside the Measurement / Formula cell ("2,52 م² × 500 DH").
* The quantity column has its own margins and is centred; no divider lines anywhere.
* Thumbnails 32 to 40 dp (26 pt ≈ 9 mm on A4).
* One tall PNG. If it would exceed 8,000 px (about 55 items), point the user to the PDF instead.
* PDF: page numbers in the footer ("صفحة 2 / 3"); the full header on page 1 only; pages 2+ repeat one
  slim line (workshop · invoice number · date) above the column titles; total, deposit and balance only
  on the last page. Deposit in green, slightly smaller than the total; balance in a yellow band.

## Unchanged

Quantity defaults to 1; repeated products stay separate items; confirming an item returns to the item
list; area / perimeter / per-piece calculation and rounding; immutable invoice snapshots and revisions;
no taxes. The calculation method must stay pluggable (no new `when (formula)` branches in new code).
