# Day 3 implementation plan

Written 2026-11-01 from the owner's product review. The decisions behind every step are in
[../day2/DECISIONS.md](../day2/DECISIONS.md) (marked **[to build]**). This file says **how** to build them.

---

## Part A. How to use this plan (read all of it before any code)

### A1. One chapter at a time

* Work on **one chapter per session**, in order: 0, 1, 2, 3. Do not start a chapter until the previous one
  is committed and the owner has said to continue.
* Inside a chapter, do the steps **in order**. Each step has: Goal, Files, Do, Do not, Tests, Done when.
* A step is done only when its "Done when" list is true. If one item cannot be met, stop and report it.

### A2. Before you start a chapter

1. Read `CLAUDE.md` (or `AGENTS.md`), `docs/day2/DECISIONS.md`, `docs/day2/PROGRESS.md`, and this chapter.
2. `git status` must be clean. If not, stop and ask the owner.
3. Create the branch: `git checkout -b day3/sprint-N main` (N = chapter number).
4. Run `./gradlew -p domain test` once to know the starting state. Note the number of tests.

### A3. Stop and ask the owner when

* A step seems to need a **Room schema change**. No chapter in this plan needs one. Do not bump to version 7
  without the owner's approval.
* A decision is missing or two documents disagree.
* You are about to run instrumented tests (they **wipe the app database**), use the owner's phone, push,
  delete user data, or change backup formats or the backup folder name.
* A test fails and the fix would change behaviour that a decision describes.

### A4. Rules that apply to every step

* Money is integer centimes, lengths integer thousandths. No floating point in calculations.
* No new `when (formula)` branches. Pricing stays pluggable.
* Screen text only in `app/.../ui/Strings.kt` (and `ui/Messages.kt` for error texts); printed words only in
  the domain `ArabicTerms` (`domain/format/ArabicFormat.kt`); spoken text only in `domain/speech`,
  always through `SpeechTexts.of(settings.speechLanguage)`.
* Colours only through `MaterialTheme.colorScheme` / `MaterialTheme.appPalette`. Never `Color(0x...)` in screens.
* A speaker tap only speaks. It never selects, presses or navigates.
* Every flow must work with the voice off and with no Arabic voice installed.
* Files are UTF-8 with LF line endings. Never rewrite a file with a tool that changes encoding or line endings.
* Keep internal names (`Invoice`, `InvoiceRepository`, package `ma.workshop.invoice`, table `invoices`) as they
  are. Renaming is about **what the user sees and hears**, not code identifiers.
* Do not add features that this plan does not list.

### A5. Definition of done for a chapter

1. `./gradlew -p domain test` passes.
2. `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` passes (no lint errors).
3. Instrumented suite: **ask the owner first**. Then, on one explicitly targeted device:
   `./gradlew --stop`, install both APKs, `adb -s <serial> shell am instrument -w ma.workshop.invoice.test/androidx.test.runner.AndroidJUnitRunner`
   (or `./gradlew :app:connectedDebugAndroidTest -PdeviceTests` with `ANDROID_SERIAL` set). If you cannot run
   it, say so in the hand-off. Never claim device verification you did not do.
4. `docs/day2/PROGRESS.md` updated: move the chapter from the roadmap into "What is built", update "Current
   state", add new hand checks, remove text that is no longer true.
5. `docs/day2/DECISIONS.md`: remove the **[to build]** mark from every decision you built.
6. `README.md` updated where it describes changed behaviour.
7. One commit (or a few small ones) on `day3/sprint-N`. Do not push unless the owner asks.
8. Hand-off report in the format of Part F.

---

## Part B. Chapter 0: safety (branch `day3/sprint-0`)

### Step 0.1 Prove the current code builds and passes

* **Goal:** the last commits (multi-size entry, Room v6, "another size" visibility) compile and pass.
* **Do:** run A5 items 1 and 2. Fix only compile errors and test failures caused by the last patch
  (`ui/screens/ItemScreens.kt`, `takesSizes`). Ask the owner before the device run (A5 item 3).
* **Do not:** refactor, rename or "improve" anything else.
* **Done when:** domain tests, build and lint pass; the device result (or "not run") is written in PROGRESS.md.

### Step 0.2 Show the real reason when Google sign-in fails

* **Goal:** today any failed Google consent shows "consent declined", even when the setup is missing. Show:
  (a) "you closed the Google screen" when the user cancelled, (b) the real Google failure otherwise.
* **Files:**
  * `app/src/main/java/ma/workshop/invoice/ui/AppNav.kt` (the `consentLauncher`, around line 296)
  * `app/src/main/java/ma/workshop/invoice/ui/backup/BackupViewModel.kt` (`onConsentResult`, around line 182)
  * `app/src/main/java/ma/workshop/invoice/platform/drive/DriveBackupClient.kt` (`tokenFromConsent`, line 112;
    the `DriveBackupApi` interface)
  * `domain/src/main/kotlin/ma/workshop/invoice/domain/backup/AutoBackup.kt` (`BackupFailure`)
  * `app/src/main/java/ma/workshop/invoice/ui/Messages.kt` / `Strings.kt` (failure texts)
  * the fake Drive used by `BackupWorkerTest` (it implements `DriveBackupApi`)
* **Do:**
  1. In `AppNav.kt`, always pass both `result.resultCode` and `result.data` to the ViewModel (today `data` is
     dropped when the result is not `RESULT_OK`).
  2. Change `tokenFromConsent(data)` to `consentResult(resultCode: Int, data: Intent?): DriveAuthResult` that
     returns `Token` or `Failed`:
     * `data != null`: call `getAuthorizationResultFromIntent(data)`. On `ApiException`, map
       `e.statusCode` with the **same classification `authorize()` already uses** (look for where it produces
       `"api 10"` / `DEVELOPER_ERROR`); `detail = "api ${e.statusCode}"`. A status of 16 or 12501 means
       cancelled: use `CONSENT_CANCELLED` (below).
     * result OK but no access token: `Failed(CONSENT_NEEDED, "no token")`.
     * `data == null` and result not OK: `Failed(CONSENT_CANCELLED, "cancelled")`.
  3. Add `CONSENT_CANCELLED(needsUser = true, retryable = false)` to `BackupFailure`, next to `CONSENT_NEEDED`.
     Add its message: "أغلقت شاشة Google قبل السماح. اضغط من جديد واختر «السماح»". Check every `when` over
     `BackupFailure` compiles and gives it a sensible branch (the home banner should **not** show for it).
  4. Update the fake Drive and `DriveBackupClient` docs. Update the troubleshooting table in
     `docs/google-drive-setup.md`: add the cancelled row; say that `api 10` also appears right after choosing
     an account when the setup is missing.
* **Do not:** change scopes, the backup format, retention, or the worker.
* **Tests:** a domain test for the status-code mapping if the classifier is in `domain`; otherwise a small
  JVM-free check in the existing Drive tests. Build passes.
* **Done when:** on a phone without the Google Cloud setup, choosing an account shows the "setup incomplete"
  message with `api 10` (owner checks by hand), and pressing Back on the Google screen shows the cancelled message.

### Step 0.3 Owner tasks (do not do these yourself; list them in the hand-off)

* Google Cloud setup from `docs/google-drive-setup.md`, then one Drive backup and one Drive restore.
* One phone-folder backup and restore (Settings > Backup).
* Push `main` when the owner wants.

---

## Part C. Chapter 1: layout, palette, name (branch `day3/sprint-1`)

### Step 1.1 Right-to-left app layout

* **Goal:** the app uses Arabic right-to-left layout on every phone, **even when the phone's language is
  French or English** (common in Morocco). Back arrow on the right, labels on the right, amounts on the left.
* **Background:** Day 2 forced LTR and then re-ordered rows by hand so labels still sat on the right. That
  commit is `e08d3cb` ("Day 2 sprint 4: foundations"). `git show e08d3cb -- <file>` shows what was re-ordered
  in each file; use it as a map of what to undo. Do not `git revert` it: it also added the theme and components.
* **Files:** `ui/theme/Theme.kt` (line ~135: `LocalLayoutDirection provides LayoutDirection.Ltr`),
  `ui/components/Foundations.kt`, `Basics.kt`, `Inputs.kt`, `MicButton.kt`, and every screen in `ui/screens`,
  `ui/history`, `ui/settings`, `ui/backup`, `ui/components/InvoiceTable.kt`. Tests: `LayoutAndBackUiTest`,
  `InvoiceTableUiTest`, `SpeakerToggleUiTest`, `SpeakerLayoutSweepTest`, `UnitsUiTest`.
* **Do:**
  1. In `Theme.kt` provide `LayoutDirection.Rtl` (forced, not from the system locale).
  2. In RTL a `Row` places its **first** child on the right. Go screen by screen: wherever a Row lists the
     right-hand element last (the LTR trick), put it first. Wherever a `Column` uses
     `horizontalAlignment = Alignment.End` to push content right, use `Alignment.Start` (in RTL, Start is right).
     `SectionCard` does this: check it.
  3. Remove the RTL island in the catalogue grid (`CustomerAndItemsScreens.kt` ~397 and the inner LTR at ~406)
     and `reverseLayout = true` on the category chips (~370): in RTL they are no longer needed. The first
     product must still be top-right and "الكل" still first on the right.
  4. Arrows: use the `Icons.AutoMirrored.Filled.ArrowBack` / `ArrowForward` icons (they flip by themselves).
     Back sits on the right of the top bar. "متابعة" shows its arrow pointing left, placed after the word.
  5. `ArabicText` stays (RTL text direction, aligned to Start). Phone numbers, amounts and "DH / م²" must keep
     their digit order: keep the existing LTR isolates/marks.
  6. Item entry: the product picture stays centred in the top bar.
  7. The printed receipt renderer is **not** touched (already RTL).
  8. Update the layout tests: every assertion that says "left" for back/labels becomes "right" and vice versa.
     Keep the measures (48 dp targets, 4 dp gaps).
* **Do not:** change sizes, colours, texts or flow; mirror pictures (illustrations of doors stay as drawn).
* **Tests:** all UI tests above, adjusted. Add to `LayoutAndBackUiTest`: back arrow's right edge is near the
  screen's right edge; on item rows the amount is left of the name.
* **Done when:** every screen reads from the right with the phone set to French; layout tests pass;
  screenshots from `ScreenshotTourTest` look mirrored compared with today (owner checks).

### Step 1.2 Blue palette for the app, printed receipt unchanged

* **Goal:** app buttons and accents become blue. Green only means paid. The **printed receipt keeps exactly
  its current colours**.
* **Files:** `domain/.../palette/Palette.kt`, `domain/.../layout/InvoiceLayout.kt` (`InvoiceColors`, line ~84),
  `app/.../ui/theme/Theme.kt`, `docs/day2/design-review.html` is reference only (do not edit).
* **Do:**
  1. **First protect the receipt.** `InvoiceColors.ACCENT`, `PAID` and `PAID_BG` point at `Palette.PRIMARY` /
     `PRIMARY_CONTAINER`. Add `Palette.PAID = 0xFF2E7D32`, `PAID_CONTAINER = 0xFFE8F5E9`,
     `ON_PAID_CONTAINER = 0xFF1B5E20` (today's green values) and make `InvoiceColors` use them. Add a domain
     test that pins every `InvoiceColors` value to today's ARGB, so a palette change can never reach paper.
  2. Change `PRIMARY` to blue `0xFF1F5FAF`, `PRIMARY_LIGHT` to `0xFF6FA0E0`, `PRIMARY_CONTAINER` to
     `0xFFE6EEF9`, `ON_PRIMARY_CONTAINER` to `0xFF123A6B`. Check: white on `PRIMARY` must be at least 4.5 : 1
     (it is about 6.3 : 1); write the check as a domain test with the WCAG formula.
  3. In `Theme.kt`: `appPalette.paid` / `paidContainer` use the new `Palette.PAID*`. `tertiary*` currently
     reuse `PRIMARY` (green): point them at `PAID*` only if they are used for paid states; otherwise blue.
     Search every use of `colorScheme.tertiary` and `appPalette.paid` to decide, and list them in the hand-off.
  4. "Success" buttons (`ButtonKind.Success`, for example share) become blue primary; green stays only for the
     paid badge, the paid balance and the "خالص" banner.
  5. Update the Palette doc comment (rules) to say: blue = action, green = paid only.
* **Do not:** touch yellow, error, gray, surface; change the printed receipt.
* **Tests:** the two new domain tests; `ExportInstrumentedTest` must still pass unchanged.
* **Done when:** no green appears on any screen except paid states (owner checks), and the receipt PNG is
  identical in colour to before.

### Step 1.3 Blue launcher icon

* **Files:** `app/src/main/res/drawable/ic_launcher_background.xml` (fill `#B8541A`, rust),
  `ic_launcher_foreground.xml`.
* **Do:** background fill `#1F5FAF`. Check the foreground still reads well on blue; if the `#37474F` dark parts
  disappear, lighten them (for example `#ECEFF1` details). Keep the drawing.
* **Done when:** the icon on the home screen is blue and readable (owner checks).

### Step 1.4 Rename invoice to receipt (وصل)

* **Goal:** the user never sees or hears "فاتورة / فواتير". No data migration (no real receipts exist).
* **Arabic grammar trap:** فاتورة is feminine, **وصل is masculine**. Adjectives, pronouns and verbs must change
  too. Plural: **وصولات**. Use the table below exactly; for any other string, apply the same rules and list it
  in the hand-off for the owner to check.

| Where | Key | Now | New |
|---|---|---|---|
| `res/values/strings.xml` | `app_name` | فواتير الورشة | وصولات الورشة |
| Strings.kt | NEW_INVOICE, START_NEW | فاتورة جديدة | وصل جديد |
| Strings.kt | PREVIOUS_INVOICES, HISTORY_TITLE | الفواتير السابقة | الوصولات السابقة |
| Strings.kt | REMOVE_ITEM_MESSAGE | سيتم حذفه من الفاتورة | سيتم حذفه من الوصل |
| Strings.kt | REVIEW_TITLE | مراجعة الفاتورة | مراجعة الوصل |
| Strings.kt | INVOICE_TOTAL | مجموع الفاتورة | مجموع الوصل |
| Strings.kt | FULLY_PAID_LONG | الفاتورة مدفوعة بالكامل | الوصل مدفوع بالكامل |
| Strings.kt | SAVE_INVOICE | حفظ الفاتورة | حفظ الوصل |
| Strings.kt | CONFIRM_SAVE_TITLE | حفظ الفاتورة؟ | (deleted in step 2.1; rename now anyway: حفظ الوصل؟) |
| Strings.kt | SAVE_FAILED | تعذر حفظ الفاتورة. بياناتك ما زالت هنا، حاول مرة أخرى | تعذر حفظ الوصل. بياناتك ما زالت هنا، حاول مرة أخرى |
| Strings.kt | SAVED_TITLE | تم حفظ الفاتورة | تم حفظ الوصل |
| Strings.kt | INVOICE_NUMBER | فاتورة رقم | وصل رقم |
| Strings.kt | EXPORT_FAILED | الفاتورة محفوظة، لكن تعذر إنشاء الملف | الوصل محفوظ، لكن تعذر إنشاء الملف |
| Strings.kt | EXPORT_PREPARING | جارٍ تجهيز الفاتورة... | جارٍ تجهيز الوصل... |
| Strings.kt | PREVIEW | معاينة الفاتورة | معاينة الوصل |
| Strings.kt | INVOICE_TOO_LONG_FOR_IMAGE | هذه الفاتورة طويلة على صورة واحدة. شاركها كملف PDF | هذا الوصل طويل على صورة واحدة. شاركه كملف PDF |
| Strings.kt | PREPARE_SHARE | معاينة ومشاركة الفاتورة | معاينة ومشاركة الوصل |
| Strings.kt | RESUME_DRAFT_TITLE | فاتورة غير مكتملة | وصل غير مكتمل |
| Strings.kt | RESUME_DRAFT_MESSAGE | لديك فاتورة لم تحفظها. هل تريد متابعتها؟ | لديك وصل لم تحفظه. هل تريد متابعته؟ |
| Strings.kt | LEAVE_TITLE | الخروج من الفاتورة؟ | الخروج من الوصل؟ |
| Strings.kt | LEAVE_NEW_MESSAGE | لم تحفظ الفاتورة بعد. يمكنك تركها كمسودة والعودة إليها لاحقاً | لم تحفظ الوصل بعد. يمكنك تركه كمسودة والعودة إليه لاحقاً |
| Strings.kt | NO_INVOICES | لا توجد فواتير بعد | لا توجد وصولات بعد |
| Strings.kt | INVOICE_DETAILS | تفاصيل الفاتورة | تفاصيل الوصل |
| Strings.kt | DELETE_INVOICE_TITLE | حذف الفاتورة؟ | حذف الوصل؟ |
| Strings.kt | DELETE_INVOICE_MESSAGE | سيتم حذف الفاتورة نهائياً ولا يمكن التراجع | سيتم حذف الوصل نهائياً ولا يمكن التراجع |
| Strings.kt | DELETE_FAILED | تعذر حذف الفاتورة. لم يتغير أي شيء | تعذر حذف الوصل. لم يتغير أي شيء |
| Strings.kt | INVOICE_NOT_FOUND | الفاتورة غير موجودة | الوصل غير موجود |
| Strings.kt | INVOICE_UNREADABLE | تعذر قراءة هذه الفاتورة | تعذر قراءة هذا الوصل |
| Strings.kt | EXPORT_FORMAT | شكل الفاتورة | شكل الوصل |
| Strings.kt | AUDIO_INVOICE_TOTALS | نطق مجموع الفاتورة والباقي | نطق مجموع الوصل والباقي |
| Strings.kt | NO_PRICE_NOTE | ... داخل الفاتورة | ... داخل الوصل |
| Strings.kt | DELETE_PRODUCT_MESSAGE | الفواتير القديمة لن تتغير... | الوصولات القديمة لن تتغير... |
| Strings.kt | FORMULA_LOCKED_NOTE | الفواتير القديمة تحتفظ... | الوصولات القديمة تحتفظ... |
| Strings.kt | BACKUP_ONLINE_NOTE | ... الفواتير لا تحتاج أياً منهما | ... الوصولات لا تحتاج أياً منهما |
| Strings.kt | RESTORE_CONFIRM_MESSAGE | (الفواتير والمنتوجات والإعدادات) | (الوصولات والمنتوجات والإعدادات) |
| Strings.kt | BACKUP_SUMMARY | فواتير | وصولات |
| Messages.kt | EXCEEDS_TOTAL | أكبر من مجموع الفاتورة | أكبر من مجموع الوصل |
| ArabicFormat.kt (`ArabicTerms`) | INVOICE | فاتورة | وصل |
| ArabicFormat.kt (`ArabicTerms`) | UPDATED | فاتورة معدلة | وصل معدل |
| SpokenText.kt (line ~105) | sentence | ، الفاتورة … | ، الوصل … (check `ArabicTerms.FULLY_PAID` agrees: masculine) |
| DarijaTerms.kt | INVOICE_TOTAL | المجموع ديال الفاتورة | المجموع ديال الوصل |
| DarijaTerms.kt | FULLY_PAID | الفاتورة خالصة | الوصل خالص |

* **Also:**
  1. Search the whole `app/src/main` and `domain/src/main` for `فاتور` and `فواتير` afterwards: zero hits
     (code comments may stay in English).
  2. Shared file names (`InvoiceDocument.kt` ~97, `invoice-0001-2026-11-01`) are visible to the customer in
     WhatsApp: change the prefix to `wasl-`. Keep ASCII.
  3. **Keep** the backup folder `Documents/Fawatir Warsha` and backup file names exactly as they are: restore
     looks for them, and the folder is only seen in a file picker.
  4. Update tests that look for the old texts (4 test files mention فاتورة); prefer using `Strings.*`
     constants in tests instead of literal Arabic.
  5. README and DECISIONS: user-facing names in examples become وصل.
* **Do not:** rename Kotlin classes, packages, tables, routes or backup JSON keys.
* **Done when:** the grep is clean, all tests pass, and the owner hears "الوصل خالص" in Darija.

### Step 1.5 Small fixes

1. `ui/settings/CatalogScreens.kt` line ~187 uses `Strings.INVOICE_NOT_FOUND` for a missing product. Add
   `PRODUCT_NOT_FOUND = "المنتوج غير موجود"` and use it.
2. Delete `Strings.AUDIO_TEST_SENTENCE` (unused; confirm with a search first).
* **Done when:** build passes and the search for `AUDIO_TEST_SENTENCE` is empty.

---

## Part D. Chapter 2: fewer taps (branch `day3/sprint-2`)

### Step 2.1 Remove the save confirmation dialog

* **Goal:** pressing "حفظ الوصل" on review saves directly (when the deposit is valid).
* **Files:** `domain/.../flow/InvoiceFlow.kt` (`confirmingSave`, `savePressed`, `saveConfirmed`, `back`,
  events `SaveDismissed`, `SaveConfirmed`), `domain/.../flow/DraftCodec.kt` (check whether it stores
  `confirmingSave`), `ui/screens/ReviewAndSavedScreens.kt` (dialog at ~207), `ui/flow/*`, `Strings.kt`
  (`CONFIRM_SAVE_TITLE`), tests `InvoiceFlowTest`, `InvoiceFlowUiTest`, `ScreenshotTourTest`,
  `SpeakerLayoutSweepTest` ("save dialog" step).
* **Do:**
  1. In the state machine merge `savePressed` and `saveConfirmed`: on `SavePressed` with a valid deposit, build
     the `InvoiceDraft` and emit `FlowEffect.Save` with `saving = true`. Invalid deposit: show the problem, no save.
  2. Remove `confirmingSave`, `SaveDismissed`, `SaveConfirmed` and their branches. If `DraftCodec` reads
     `confirmingSave` from old drafts, make it ignore the field (old drafts must still load).
  3. Guard double taps: while `saving` is true, `SavePressed` does nothing (already true; keep a test).
  4. Delete the dialog UI and `CONFIRM_SAVE_TITLE`.
* **Tests:** domain: `SavePressed` with valid deposit emits `Save` once; second press while saving does nothing;
  invalid deposit does not save; an old draft JSON containing `confirmingSave` still decodes. UI tests: remove
  dialog taps.
* **Done when:** review → one tap → Saved screen; all tests pass.

### Step 2.2 Recent customers and suggestions

* **Goal:** on the customer step, show up to **5 recent customers** as one-tap chips; while typing a name or
  phone (2+ characters), show up to **3 matching past customers** under the fields. Tapping one fills both
  fields. No new table.
* **Files:** `data/db/Daos.kt`, `data/Repositories.kt`, the customer screen in
  `ui/screens/CustomerAndItemsScreens.kt`, `ui/flow/InvoiceFlowViewModel.kt` (or the route), `Strings.kt`,
  a new pure helper in `domain` (for matching).
* **Do:**
  1. DAO query on `invoices`: distinct customers (name + phone), newest first, ignoring rows where both are
     blank. Group by the normalised phone when there is one, else by the trimmed name. Keep the newest name
     for a phone. Limit 50 (the screen filters further).
  2. Domain helper `CustomerSuggestions`: `recent(list, 5)` and `matching(list, query, 3)`. Matching: a query of
     digits matches phones by digits only (ignore spaces, `+212` vs `0`); text matches names, case-insensitive,
     ignoring Arabic diacritics and alef forms (أ إ آ → ا), "contains" match. JVM tests for each rule.
  3. UI: chips row under the title "زبناء سابقين" (new string), each chip shows the name (or the phone when no
     name). Suggestions appear as tappable rows under the fields. Tapping sends the existing
     `FlowEvent.CustomerEdited(name, phone)`; the flow itself does not change.
  4. The customer step must still allow "continue" with nothing typed. Hide the chips when there are none.
  5. Edit mode (editing a saved receipt) shows the same suggestions.
* **Do not:** add a customers table, change `InvoiceFlow` state, or store anything new.
* **Tests:** domain tests for the helper; a DAO test in `DatabaseTest` (two receipts for the same phone give one
  customer with the newest name); a UI test: tap a chip, both fields are filled.
* **Done when:** with 3 saved receipts, the chips appear and fill the fields in one tap.

### Step 2.3 Send straight to the customer's WhatsApp (an option)

* **Goal:** next to the existing share button, show "إرسال للزبون في واتساب" when the receipt has a customer
  phone **and** WhatsApp (or WhatsApp Business) is installed. The share sheet stays as it is.
* **Files:** `platform/export/InvoiceExporter.kt` (share intent at ~124), `AndroidManifest.xml` (`<queries>`
  already exists at line 15: add the WhatsApp packages), Saved screen and receipt detail screen,
  `Strings.kt`, a new domain helper for phone numbers.
* **Do:**
  1. Domain `MoroccanPhone.toWhatsAppId(raw: String): String?`: keep digits; `0XXXXXXXXX` (10 digits, starting
     05/06/07) → `212XXXXXXXXX`; `+212…` / `00212…` → `212…`; anything else → null. JVM tests for each form.
  2. Manifest `<queries>`: `<package android:name="com.whatsapp" />` and `com.whatsapp.w4b`.
  3. Intent: `ACTION_SEND`, the same file URI and MIME type as today, `setPackage(<installed package>)`,
     `putExtra("jid", "<id>@s.whatsapp.net")`, `FLAG_GRANT_READ_URI_PERMISSION`. Prefer `com.whatsapp`, else
     `com.whatsapp.w4b`. If the launch throws, fall back to the normal share sheet and show
     `SHARE_UNAVAILABLE`-style text. For "both formats", send the PNG (WhatsApp shows it as an image).
  4. Hide the button when the phone is missing/invalid or WhatsApp is not installed.
* **Do not:** use `wa.me` links (they cannot attach a file); remove the share sheet.
* **Tests:** domain tests for the phone helper. By hand (owner): the chat opens with the right customer and the
  image attached.
* **Done when:** the button appears only when it can work and opens the customer's chat with the file.

### Step 2.4 Version mark on re-shared receipts

* **Goal:** a receipt edited after saving prints "نسخة N" beside its number, N = its revision (2, 3, ...).
  The first version prints nothing extra. No "edited" wording on paper.
* **Files:** `domain/.../format/InvoiceDocument.kt` (document model, `number` at ~38, builder ~63),
  `domain/.../format/ArabicFormat.kt` (`ArabicTerms`: add `VERSION = "نسخة"`),
  `domain/.../layout/InvoiceLayout.kt` (header line ~282, and the slim line of PDF pages 2+),
  `domain/.../invoice/Models.kt` (`revision`, `isEdited`).
* **Do:**
  1. Add `version: Int?` to the document: `invoice.revision` when `revision >= 2`, else null.
  2. Header: "وصل رقم 0012 · نسخة 2" (use the existing separator style). Same on the PDF slim line.
  3. Screens are unchanged (the detail screen already shows edits).
* **Tests:** `FormatAndDocumentTest`: revision 1 → null, 3 → 3; `InvoiceLayoutTest`: the header text contains
  "نسخة 2" only for an edited receipt; the header still fits on one line at the narrowest width (wrap check).
* **Done when:** edit a receipt, re-share, and the image says "نسخة 2".

---

## Part E. Chapter 3: speakers and large fonts (branch `day3/sprint-3`)

Read DECISIONS.md "Speakers" first. Today's speakers are placed by hand in many styles (`SpeakerTile`,
`SpokenButton`, round speakers in `AppTopBar`, rows, cards). This chapter replaces them with one system.

### Step 3.1 The speakable components

* **Files:** `ui/components/Foundations.kt` (today `SpeakerTile`, `SpokenButton`, `AppTopBar`), a new
  `ui/components/Speakable.kt`.
* **Do:** create:
  1. `SpeakerIcon(spoken: String, ...)`: the one round speaker (48 dp target, same icon, size and colour
     everywhere). It calls the existing `Speaker.say(...)`; content description `Strings.SPEAK_LABEL`.
  2. `Speakable(spoken: String, modifier, content)`: a `Row` (RTL) with `SpeakerIcon` first (so it sits on the
     **right**, in the speaker column) and `content` taking the rest. When the speakers switch is **off**, it
     renders only `content`, full width (no empty column). Tag it `Modifier.testTag("speakable")`.
  3. `SpeakableButton(label, spoken = label, ...)`: `Speakable` around the existing `BigButton`; the speaker is
     outside the button, so a speaker tap can never press it.
  4. `SpeakableField(label, spoken, ...)`: the speaker beside the field's **label**, not its box.
  5. Grid cards: `CardSpeaker(spoken)` placed in the card's **top-right** corner as its own target.
  6. Title: `AppTopBar` keeps its speaker beside the title (exception), but uses `SpeakerIcon`.
  7. `Modifier.speakExempt()`: a semantics marker for text that needs no speaker (numbers inside a field box,
     chip labels that are part of a spoken card, etc.).
* **Do not:** change what any speaker says; remove `Speaker`, `SpeechTexts` or the voice-missing dialog.
* **Done when:** the components compile and a small UI test shows: switch off → no speaker and full-width
  content; switch on → speaker on the right; speaker tap does not trigger the content's click.

### Step 3.2 Move every screen to the components

* **Do:** screen by screen (Home, customer, item list, catalogue, item entry, review, saved, history, detail,
  product list, product edit, dialogs), replace hand-placed speakers with the components. One speaker per
  element; a summary card (total, deposit, balance) is **one** `Speakable` reading one sentence (the existing
  `SpeechText` totals sentence). Remove `SpeakerTile` and `SpokenButton` when nothing uses them.
* **Keep:** no speakers in Settings and Backup, except the product screens.
* **Done when:** a search for `SpeakerTile(` and `SpokenButton(` finds nothing, and the existing speaker tests
  pass after updating their locators.

### Step 3.3 The sweep test enforces the rules, at three font sizes

* **Files:** `app/src/androidTest/.../SpeakerLayoutSweepTest.kt`, `UiTestSupport.kt`.
* **Do:**
  1. With the switch on, on every screen the sweep visits: every node with text must satisfy
     `hasAnyAncestor(hasTestTag("speakable"))`, be inside the top bar, or be marked `speakExempt`. Fail with the
     screen name and the text.
  2. Add the screens the sweep misses today: product list, product edit, the voice dialogs, an item list with
     5 items, a customer with a long name (40 characters).
  3. Run the whole sweep at font scale **1.0, 1.3 and 1.5**. Set it with
     `adb shell settings put system font_scale 1.3` through `InstrumentationRegistry.getInstrumentation()
     .uiAutomation.executeShellCommand(...)`, relaunch the activity, and **always reset to 1.0 in `@After`**.
  4. Keep the existing "no word broken in the middle" check at every scale; add "no text node is clipped"
     (its bounds are inside its parent's bounds and the screen width).
* **Done when:** the sweep runs on all screens at the three scales (it may fail at 1.3 / 1.5 until step 3.4).

### Step 3.4 Fix what breaks at large fonts

* **Rules:** no fixed heights on anything holding text (use `heightIn(min = ...)`); buttons grow vertically;
  two side-by-side buttons stack when they do not fit (the stacked layout already exists for speakers on:
  reuse it); `FitText` stays for single-line labels but must not shrink below 70 %; table rows may wrap the
  name and formula onto two lines; amounts never wrap mid-number.
* **Do:** fix screen by screen until the sweep passes at all three scales. List every screen you changed.
* **Done when:** the sweep passes at 1.0, 1.3 and 1.5; the owner checks 3 screens at his phone's largest font.

### Step 3.5 Speaker usage counter

* **Goal:** know whether the owner uses the speakers before choosing the default.
* **Do:**
  1. Count speaker taps per screen in `SharedPreferences` (file `speaker_usage`, key = screen route). No Room
     change, no network, not in backups.
  2. Provide the current screen name with a `CompositionLocal` set in `AppNav.kt`; `SpeakerIcon` increments.
  3. Hidden screen: a **3-second long press on the Settings title** opens "استعمال أيقونات النطق": one row per
     screen with its count, the date counting started, and a "مسح" (reset) button with a confirmation. No
     speakers on this screen.
* **Tests:** a unit-level test of the counter (increment, reset); a UI test: long press opens the screen.
* **Done when:** taps are counted and visible on the hidden screen.

---

## Part F. Hand-off report (end of every chapter)

Write this in the chat, in this order:

1. **Branch and commits:** branch name, commit hashes, pushed or not.
2. **Built:** one line per step, with its number.
3. **Verified:** what you ran and the result (domain tests count, build, lint, device suite with device name,
   or "device suite not run" and why).
4. **Owner must check by hand:** a numbered list, concrete ("open X, tap Y, expect Z").
5. **Deviations:** anything done differently from this plan, and why.
6. **Open questions:** decisions you need from the owner.
7. **Strings to review:** every new or changed Arabic text that is not in this plan's tables.

---

## Part G. Later (do not start without the owner)

Agreed in principle, not designed yet. Each needs a short design pass with the owner first.

* **Cancel instead of delete:** status column on `invoices` (Room v7), "ملغى" stamp on paper and screen,
  hidden by default in history with a filter to show.
* **Dated payments:** `payments` table (Room v7 or v8), "تسجيل دفعة" on the receipt detail, balance computed
  from payments, the deposit becomes the first payment (migration).
* **Unpaid balances:** total on Home, "غير خالص" filter in history (depends on dated payments).
* **Quotes (عرض ثمن):** a document type that turns into a receipt with one tap.
* **Discount / round down** of the total.
* **Photos in backups.**
