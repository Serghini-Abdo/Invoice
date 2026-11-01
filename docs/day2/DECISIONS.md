# Product decisions

The final state of every product decision. Later decisions replace earlier ones; nothing here is history.

* Day 2 design rounds: approved by the owner on 2026-10-25, entry and pricing update on 2026-10-27.
* Day 3 product review: approved on 2026-11-01.
* **[to build]** marks a decision that is not in the app yet. Everything without the mark is built.
* Mock-ups: [design-review.html](design-review.html) (Day 2).

## Scope

* For one small workshop (doors, windows, garage gates, frames, accessories). Offline, no account.
  No taxes, no inventory, no accounting.
* **[to build]** The document is a **receipt (وصل)**, not a legal invoice. Rename it everywhere: app name
  "وصولات الورشة", Home buttons, screen titles, Saved screen, printed title and spoken text.
  No data migration: the app is still in development and holds no real receipts.
* Prices are typed on every line **by design**: each job has its own size and material, so the price
  differs most of the time. No default price per product and no suggested-price chips.

## Layout and header

* The app follows Arabic **right-to-left** layout, like the other apps the owner uses.
  This replaces the forced left-to-right layout of Day 2: back arrow on the right.
  Labels stay on the right and amounts on the left. The printed receipt is right-to-left (unchanged).
* **[to build]** Buttons and cards have **no arrows**: the only arrow in the app is the back arrow in the top
  bar. Buttons keep icons that carry meaning (share, edit, delete, save check, add plus).
* One top bar on every screen: back arrow, title, and the title's speaker beside it. Item entry shows the
  product's picture centred in the bar.
* The catalogue grid order is not important; keep today's order.

## Palette (theme tokens only, no hard-coded colours)

* **[to build]** Primary **blue** (about `#1F5FAF`, white text on it at least 4.5 : 1) for buttons and
  accents, replacing green. The launcher icon becomes blue too.
* **Green `#2E7D32` means paid, and only paid.** An unpaid balance is bold ink.
* Yellow `#F2B705` for the add button and balance highlights. Never white text on yellow; yellow buttons
  get a `#A67C00` edge. On paper the balance amount is dark yellow `#A67C00` (bright yellow is unreadable).
* Error `#D32F2F`, gray `#6B7280`, surface `#F3F4F6`.
* Colour is never the only signal: every state also has an icon and a word.
* The printed receipt keeps its existing palette; the blue is for the app only.

## Speakers (TextToSpeech, offline, no recordings)

* All speakers sit behind Settings > الصوت > "إظهار أيقونات النطق", **off by default** (see the usage
  counter below before changing the default).
* A speaker tap only speaks: it never selects, presses or navigates. Tapping another speaker stops the
  current speech.
* **[to build] Nothing is spoken automatically.** Speech happens only when the owner taps a speaker or
  "تجربة الصوت". No reading on screen arrival, after a value changes, or after saving. The two automatic-totals
  switches are removed from Settings (their stored values stay, unused). No speaker on icon-only controls (back arrow, − / +, mic, photo button).
* No speakers in Settings and Backup, except the product screens (list, edit, calculation methods).
* Speech rate: 3 presets (slow / normal / fast). Spoken language: Darija by default, standard Arabic in
  Settings > الصوت > "لغة الكلام". Numbers are spoken as words generated offline. Screens and printed
  receipts are not affected by the spoken language.
* No Arabic voice installed: a clear prompt explains how to download one; everything stays usable silently.
* **[to build] Placement: one system instead of per-element rules.**
  1. **One look:** the same round speaker icon, size and colour everywhere. No tiles styled like their button.
  2. **Speaker column:** full-width elements (buttons, list rows, field labels, summary cards) have their
     speaker in one column along the **right** edge of the screen (where each Arabic line starts), outside the element, so a speaker tap can
     never press a button. A field's speaker sits beside its label, not its box. One speaker per element,
     reading the whole element as one sentence (a summary card is one sentence).
  3. **Two exceptions:** the title's speaker stays beside the title in the top bar; grid cards (Home,
     choose product) carry their speaker as their own round target in the card's top-right corner.
  4. **Built in, not placed by hand:** screens use `SpeakableText` / `SpeakableButton`, which add the
     speaker when the switch is on. The layout sweep test fails when visible Arabic text has no speaker
     and is not on a short exempt list.
  5. The column costs about 48 dp of width: check it at large font scales before finishing.
* **[to build] Usage counter:** count speaker taps per screen, on the phone only (no network), shown on a
  hidden Settings screen. After about two weeks of real use with speakers on, decide the default.

## Speak-to-fill (mic)

* Android `SpeechRecognizer`, ar-MA (retries once with plain Arabic), offline preferred. The microphone
  permission is requested on the first mic tap only. A failure message ends with the recognizer's error code.
* Mic only on **customer name** and **product name**. No notes field, no mic on search or numeric fields.
  Recognised text stays editable.

## Units

* One global setting: cm or m. Second setting: unit label script, Arabic (سم / م) or Latin (cm / m),
  labels only, digits unchanged; it applies to screens, printed receipts and re-exports.
* A receipt uses the unit of its first measured item (else the Settings unit); old mixed items stay as typed.
* Latin unit labels inside right-to-left text are wrapped as LTR isolates.

## Fields and pictures

* Small illustration beside each field: ↔ width, ↕ height, DH coin for prices (never "$"), stacked boxes
  for quantity, person and phone for the customer.
* Photo import (system photo picker) **only in product edit**, downscaled to 512 px. Each receipt item keeps
  its own copy of the picture.
* **[to build]** Backups include the photo files (today a restore shows the generic picture).

## Customer

* Customer name and phone are optional; the owner can continue without them.
* **[to build]** The customer step shows **recent customers** as one-tap choices, and suggests **saved
  customers** while typing, matched by phone number.

## Item entry

* Choose a product once from the picture grid, then enter one or more independent size lines on the same
  page and confirm them together below the fields. Each line stays separately editable later.
* "إضافة قياس آخر" (another size) appears only for products that take a width and / or a height.
* The next line copies the previous line's rate, shown in yellow with a text cue until it is changed,
  confirmed, or accepted by adding another line.
* System numeric keyboard (decimal for lengths and rates, integer for quantity, also for the deposit),
  dismissible with Done, Back or the hide-keyboard action.
* Pricing methods: per piece, area, perimeter, width × rate × quantity, height × rate × quantity, fixed
  amount. Per-piece products may show width, height or both for information only; measurements a method
  needs cannot be hidden.
* "إضافة مبلغ خاص": a custom charge (for example labour) with only a description and an amount, no
  catalogue product.
* Quantity defaults to 1. The same product added twice gives two independent items; nothing is merged.
* The entry-flow prototype of 2026-10-29 (one expanded size at a time, grouped products) was **dropped**:
  the current entry flow stays.

## Review and saving

* Review shows all items, the total, an editable deposit (default 0, never above the total), the balance,
  and a "خالص" badge when fully paid.
* **[to build]** No "حفظ الفاتورة؟" dialog: the review screen is the confirmation, and an edit can undo a save.
* Saving is separate from exporting and sharing: if export or sharing fails, the receipt is already saved.

## Printed receipt (PNG and PDF)

* Columns, left to right: Price (line total) | Measurement / Formula | Quantity | Name. The unit price sits
  inside the formula cell ("2,52 م² × 500 DH"); "DH" appears once, in the column title.
* Thin dividers between rows (none after the last). No pictures on the shared receipt; screens keep them.
* Deposit and balance appear only when a deposit was entered; otherwise only the total.
* One tall PNG; above 8,000 px (about 55 items) the PDF is offered instead.
* PDF: page numbers in the footer ("صفحة 2 / 3"); the full header on page 1 only; later pages repeat one slim
  line (workshop, number, date) above the column titles; totals on the last page only.
* **[to build] Version mark:** from the second version on, a re-shared receipt shows "نسخة N" (version N)
  beside its number. The first version shows nothing. No "edited" wording.

## Sharing

* Android share sheet (WhatsApp appears there when installed).
* **[to build]** An **option** to send straight to the customer's WhatsApp using the saved phone number,
  next to the share sheet, not instead of it.

## Settings

* Settings is a short list of **categories**, each opening its own screen: المنتوجات,
  معلومات الورشة, شكل الوصل, الوحدات, الصوت, النسخ الاحتياطي. Each card shows its current value in one line
  (for example "متوقف" or "مفعل · الدارجة · عادي"), so the owner can see the state without opening it.
* **Parent and child options:** an option that only matters when another is on is its child. When the parent
  is off, its children are **hidden** (not greyed out) and one line says why ("الصوت متوقف"). Their saved
  values are kept. Under الصوت, everything else (speaker icons, language, rate, voice test,
  dictation status) is a child of "تشغيل الصوت".
* Changes still save immediately; there is no save button. No speakers on any Settings screen except the
  product screens (unchanged).

## Accessibility

* Large type, pictures, one decision per screen. Touch targets at least 48 dp.
* **[to build]** Every screen must work at the phone's large font sizes (font scale 1.3 and 1.5): no clipped
  or overlapping text, checked on the owner's phone.

## Data rules (unchanged)

* Integer money (centimes) and exact length conversion, one final rounding per item
  ([calculation-rules.md](../calculation-rules.md)).
* Receipt items are immutable snapshots; edits keep the number and store a revision with the full previous
  version. Numbers never repeat.
* The calculation method stays pluggable: no new `when (formula)` branches.
* Old backups stay restorable.

## Agreed for later (not scheduled)

* Cancel instead of delete: a cancelled receipt keeps its number, is stamped ملغاة and hidden by default.
* Payments as a dated list ("تسجيل دفعة" on the receipt), with the balance computed from it.
* Unpaid balances: a total on Home and an "unpaid" filter in history.
* Quotes (عرض ثمن) that turn into a receipt with one tap.
* A discount or rounding down of the total.

## Open questions

None at the moment.
