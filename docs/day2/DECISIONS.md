# Product decisions

The final state of every product decision. Later decisions replace earlier ones; nothing here is history.

* Day 2 design rounds: approved by the owner on 2026-10-25, entry and pricing update on 2026-10-27.
* Day 3 product review: approved on 2026-11-01.
* **[to build]** marks a decision that is not in the app yet. Everything without the mark is built.
* Mock-ups: [design-review.html](design-review.html) (Day 2).

## Scope

* For one small workshop (doors, windows, garage gates, frames, accessories). Offline, no account.
  No taxes, no inventory, no accounting.
* The document is a **فاتورة** (owner decision, 2026-11-01, reversing the earlier وصل). App
  name "فواتير الورشة". Every screen, printed title and spoken text uses فاتورة / فواتير again.
  No legal mentions (ICE etc.) are added.
* Prices are typed on every line **by design**: each job has its own size and material, so the price
  differs most of the time. No default price per product and no suggested-price chips.

## Layout and header

* The app follows Arabic **right-to-left** layout, like the other apps the owner uses.
  This replaces the forced left-to-right layout of Day 2: back arrow on the right.
  Labels stay on the right and amounts on the left. The printed invoice is right-to-left (unchanged).
* Buttons have no arrows; the only arrow is the back arrow in the top bar. This covers the Settings category
  cards and every other navigation card. Buttons keep icons that carry meaning (share, edit, delete, save
  check, add plus).
* One top bar on every screen: back arrow, title, and the title's speaker beside it. Item entry shows the
  product's picture centred in the bar.
* The catalogue grid order is not important; keep today's order.

## Palette (theme tokens only, no hard-coded colours)

* Primary **blue** `#1F5FAF` (white text on it at least 4.5 : 1) for buttons and accents; the launcher icon
  is blue too.
* **Green `#2E7D32` means paid, and only paid.** An unpaid balance is bold ink.
* **Yellow means "look at this", nothing else:** on screen only the copied-rate cue on item entry. It is no
  longer a button colour or a banner colour (it clashes with blue): the add-product button is **tonal blue**
  (light blue fill `#E6EEF9`, blue text, a white plus in a blue circle, 1 dp blue edge), and warning banners
  and the "edited" labels are tonal blue with the warning icon. Never white text on yellow. On paper the
  balance amount stays dark yellow `#A67C00`.
* Error `#D32F2F`, gray `#6B7280`, surface `#F3F4F6`.
* Colour is never the only signal: every state also has an icon and a word.
* The printed invoice keeps its existing palette; the blue is for the app only.

## Item list (the screen with the items of an invoice)

* The fixed bottom bar holds only the total (one compact line) and "متابعة": at most a quarter of
  the screen. "إضافة منتوج" moves into the list, as its first row above the newest item (and the main button
  when the list is empty). Item rows are compact: edit and delete are 48 dp buttons. A product name wraps
  only between words, never inside a word (plan step 1.9).

## Dialogs

* Dialogs use the app's own full-width buttons, stacked, the main action on top, laid out
  right-to-left like every screen (not Material's button row). Each button has a meaningful icon ("فاتورة
  جديدة" gets a plus, not an X) and, with speaker icons on, its own speaker; the title's speaker reads the
  title and the message (plan step 1.10).

## Speakers (TextToSpeech, offline, no recordings)

* All speakers sit behind Settings > الصوت > "إظهار أيقونات النطق", **off by default** (see the usage
  counter below before changing the default).
* A speaker tap only speaks: it never selects, presses or navigates. Tapping another speaker stops the
  current speech.
* Nothing is spoken automatically; speech only on a speaker tap or the voice test. No reading on screen
  arrival, after a value changes, or after saving. The stored values of the two old automatic-totals settings
  stay in the database and in backups, unused. No speaker on icon-only controls (back arrow, − / +, mic, photo button).
* No speakers in Settings and Backup, except the product screens (list, edit, calculation methods).
* Speech rate: 3 presets (slow / normal / fast). Spoken language: Darija by default, standard Arabic in
  Settings > الصوت > "لغة الكلام". Numbers are spoken as words generated offline. Screens and printed
  invoices are not affected by the spoken language.
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
  labels only, digits unchanged; it applies to screens, printed invoices and re-exports.
* A invoice uses the unit of its first measured item (else the Settings unit); old mixed items stay as typed.
* Latin unit labels inside right-to-left text are wrapped as LTR isolates.

## Catalogue

* The owner's own products, **named as he says them** (Darija, or French written in Arabic) and **drawn after
  his own sketches** in black ink lines, because he recognises his drawings, not the words (2026-11-01):
  باب (per piece), شباك (area), فوكادر (perimeter), شباك بالكو (per piece, shows width and height),
  شباك بالكو بالطول (by length; its picture adds a length arrow),
  ضواية السطح, نشارات (roof clothes-drying rack), روكار (regard d'eau), كاش ريدو (per piece), باب الكراج (area). Fresh installations get them after the
  original examples; existing ones get them once at start-up, skipping any name or picture already there.
* New product pictures follow the same style: simple black line drawings like his sketches, not coloured art.

## Fields and pictures

* Small illustration beside each field: ↔ width, ↕ height, DH coin for prices (never "$"), stacked boxes
  for quantity, person and phone for the customer.
* Photo import (system photo picker) **only in product edit**, downscaled to 512 px. Each invoice item keeps
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
* Saving is separate from exporting and sharing: if export or sharing fails, the invoice is already saved.

## Printed invoice (PNG and PDF)

* Columns, left to right: Price (line total) | Measurement / Formula | Quantity | Name. The unit price sits
  inside the formula cell ("2,52 م² × 500 DH"); "DH" appears once, in the column title.
* Thin dividers between rows (none after the last). No pictures on the shared invoice; screens keep them.
* Deposit and balance appear only when a deposit was entered; otherwise only the total.
* One tall PNG; above 8,000 px (about 55 items) the PDF is offered instead.
* PDF: page numbers in the footer ("صفحة 2 / 3"); the full header on page 1 only; later pages repeat one slim
  line (workshop, number, date) above the column titles; totals on the last page only.
* **[to build] Version mark:** from the second version on, a re-shared invoice shows "نسخة N" (version N)
  beside its number. The first version shows nothing. No "edited" wording.

## Sharing

* Android share sheet (WhatsApp appears there when installed).
* **[to build]** An **option** to send straight to the customer's WhatsApp using the saved phone number,
  next to the share sheet, not instead of it.

## Settings

* Settings is a short list of **categories**, each opening its own screen: المنتوجات,
  معلومات الورشة, شكل الفاتورة, الوحدات, الصوت, النسخ الاحتياطي. Each card shows its current value in one line
  (for example "متوقف" or "مفعل · الدارجة · عادي"), so the owner can see the state without opening it.
* **Parent and child options:** an option that only matters when another is on is its child. When the parent
  is off, its children are **hidden** (not greyed out) and one line says why ("الصوت متوقف"). Their saved
  values are kept. Under الصوت, everything else (speaker icons, language, rate, voice test,
  dictation status) is a child of "تشغيل الصوت".
* Changes still save immediately; there is no save button. No speakers on any Settings screen except the
  product screens (unchanged).

## Accessibility

* **The owner cannot read.** Pictures, icons, colour plus icon, and speakers are how he uses the app; a text
  label alone is never enough.
* Large type, pictures, one decision per screen. Touch targets at least 48 dp.
* **[to build]** Every screen must work at the phone's large font sizes (font scale 1.3 and 1.5): no clipped
  or overlapping text, checked on the owner's phone.

## Data rules (unchanged)

* Integer money (centimes) and exact length conversion, one final rounding per item
  ([calculation-rules.md](../calculation-rules.md)).
* Invoice items are immutable snapshots; edits keep the number and store a revision with the full previous
  version. Numbers never repeat.
* The calculation method stays pluggable: no new `when (formula)` branches.
* Old backups stay restorable.

## Agreed for later (not scheduled)

* Cancel instead of delete: a cancelled invoice keeps its number, is stamped ملغاة and hidden by default.
* Payments as a dated list ("تسجيل دفعة" on the invoice), with the balance computed from it.
* Unpaid balances: a total on Home and an "unpaid" filter in history.
* Quotes (عرض ثمن) that turn into a invoice with one tap.
* A discount or rounding down of the total.

## Open questions

* **Window + faux-cadre pairs:** on his paper invoices every شباك is followed by a فوكادر with the same sizes
  and quantity. Offer "add the faux-cadre with the same sizes" after a window? (not decided)
* His paper says **دفع** for the deposit and **الباقي** for the balance; the app says التسبيق. Switch to دفع?
