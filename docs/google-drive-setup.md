# Google Drive backup: one-time setup (about 10 minutes)

Invoicing never needs any of this. It is only for backing up to Google Drive, by hand or automatically.
The app contains **no client id, secret or API key**: Google recognises the app by its **package name**
and the **SHA-1 of the key that signed it**. If no OAuth client exists for that pair, Google refuses and
the app shows "Google Drive setup is incomplete" (technical code `api 10`).

## Facts you need

| | |
| --- | --- |
| Package name | `ma.workshop.invoice` |
| SHA-1 of the debug build (the APK from CI, signed with the committed key `app/debug.keystore`) | see the line below |
| Scope the app asks for | `https://www.googleapis.com/auth/drive.appdata` (a hidden folder only this app can see) |

SHA-1: C4:81:89:66:A2:24:74:67:1A:81:40:14:6F:9C:10:45:B4:B7:47:DA

CI checks on every build that the APK really is signed with this key and fails if this line stops matching,
so the number above is the one to type into Google Cloud.

## Steps (Google Cloud Console)

Use the Google account that will own the project (any account; it does not need to be the workshop's).

1. Open <https://console.cloud.google.com/>. At the top, click the project picker, then **New project**.
   Name it `Fawatir Warsha` and click **Create**. Make sure it is the selected project.
2. Menu **APIs & Services > Library**. Search `Google Drive API`, open it, click **Enable**.
   (If you forget this, the app shows "Google Drive is not enabled in the setup project", `HTTP 403 accessNotConfigured`.)
3. Menu **Google Auth Platform** (older consoles: **APIs & Services > OAuth consent screen**). Click **Get started**.
   * App name: `Fawatir Warsha`. User support email: your email. Click **Next**.
   * Audience: **External**. Click **Next**.
   * Contact email: your email. Click **Next**, tick the agreement, click **Continue**, then **Create**.
4. Left menu **Data Access** > **Add or remove scopes**. In "Manually add scopes" paste
   `https://www.googleapis.com/auth/drive.appdata`, click **Add to table**, **Update**, then **Save**.
5. Left menu **Audience**. Under **Publishing status** click **Publish app** and confirm.
   It must say **In production**.
   * **Do not leave it on "Testing".** In Testing status Google expires the owner's permission after 7 days,
     so automatic backups would silently stop after a week and the app would ask to reconnect.
   * `drive.appdata` is a non-sensitive scope, so publishing needs **no Google review or verification**.
     If Google ever shows a "Google hasn't verified this app" page, tap **Advanced**, then continue.
6. Left menu **Clients** > **Create client**.
   * Application type: **Android**. Name: `Fawatir Warsha debug`.
   * Package name: `ma.workshop.invoice`.
   * SHA-1 certificate fingerprint: the SHA-1 line above.
   * Click **Create**. Nothing has to be copied from the result into the app.
7. Wait a few minutes (Google needs time to apply the new client). Install the CI APK on the phone, open
   **Settings > Backup**, switch on **Google Drive**, choose the Google account, tap **Allow**. The app takes a
   first backup immediately and shows the result. From then on automatic backups reach Drive silently.

If you later build a **release** APK signed with your own key, create a second Android client (same steps)
with that key's SHA-1. If you publish through Google Play with Play App Signing, register Play's app signing
key SHA-1 as well.

## IMPORTANT: the first install of the pinned key cannot update the old app

Earlier CI builds were signed with a throw-away key that GitHub created fresh on every run, so each build had
a different signature. Android refuses to install an app over one with a different signature. The first build
signed with the pinned key is therefore also a different signature from the app now on the phone. **Installing it
requires uninstalling the old app, which deletes all invoices on the phone.** Do this once, in this order:

1. In the **old** app: **Settings > Backup > حفظ نسخة في ملف**. Save the file, then copy it somewhere safe
   (send it to yourself by WhatsApp or email, or copy it to a computer). Check that the file exists.
2. Uninstall the old app.
3. Install the new APK from CI.
4. **Settings > Backup > استرجاع من الهاتف**, pick the file, confirm. Check that your invoices are back.
5. Do the Google Drive steps above (switch on Google Drive in the app).

From this build on, every CI build has the same signature, so a newer APK installs over an older one and
keeps all data.

## What the app tells you when it fails

The backup screen and the home screen show the reason in Arabic, with what to do, and a small technical code
for whoever is helping.

| Code shown | Meaning | What to do |
| --- | --- | --- |
| `api 10` | Google found no OAuth client for this package + SHA-1 (or it is still being applied) | Check steps 6 and 7; confirm the SHA-1 of the installed APK (below); wait a few minutes |
| `HTTP 403 accessNotConfigured` | The Drive API is not enabled in the project | Step 2 |
| `HTTP 403 storageQuotaExceeded` | The Drive is full | Free space on Drive |
| `HTTP 401` | Google rejected the permission | Tap "reconnect" |
| consent screen required | The permission is gone (revoked, account removed, or the project was in Testing) | Tap "reconnect"; make sure step 5 says In production |
| `play services N` | Google Play services missing, disabled or out of date | Update Google Play services |
| no internet | Offline | Nothing; the app retries when a connection returns |

Check the SHA-1 of an installed or downloaded APK:
`apksigner verify --print-certs app-debug.apk` (from Android build-tools), or
`keytool -list -v -keystore app/debug.keystore -alias androiddebugkey -storepass android`.

## About the committed debug key

`app/debug.keystore` uses the standard public debug credentials (store password `android`, alias
`androiddebugkey`, key password `android`). It protects nothing: it only gives every build the same identity.
Anyone could sign an APK with it, which is acceptable for the workshop's own debug build and is not something
to publish to other people. Releases must use a private key (see the README).

## Why not "pick a Drive file with the system file picker" (no Google Cloud setup)?

This was evaluated and not chosen. The idea: the owner picks a location inside Google Drive with
`ACTION_CREATE_DOCUMENT`, the app keeps the permission (`takePersistableUriPermission`) and overwrites the same
file each time. It needs no Google Cloud project and no consent screen.

What is solid: the picker, the persistable permission and writing through `ContentResolver` are standard Android,
and the Drive app does expose Drive as a document provider.

Why it is a poor base for **unattended** backups, as far as can be said without testing:

* Writes go through the Drive app's local cache and upload later; whether a background write reports a failed
  upload, or leaves a stale or duplicated file, depends on the Drive app version and cannot be observed from
  the writing app.
* The grant depends on the Drive app staying installed and signed in; if it is not, the failure is a generic
  `FileNotFoundException` with no actionable reason.
* Overwrite modes (`"wt"` vs `"w"`) behave differently between providers; a truncate that succeeds and an upload
  that fails would destroy the only copy, which conflicts with the "never delete the only good copy" rule.
* It cannot be verified in CI: there is no Drive provider with a Google account on the emulators used here.

The REST route in this repository is verifiable in its parts (error mapping, retention, scheduling, the worker
with a scripted fake Drive) and reports exact reasons. The owner chose that route.

## Verification status

Everything around Drive is unit tested (error classification, retention, the worker's behaviour with a scripted
Drive including "consent gone", "no network", "upload failed so nothing is deleted"). **The live Drive path
(real consent screen, token, upload, listing, deletion against a real Google account) has not been run by anyone
yet.** It is unverified until the owner completes the steps above and presses **Back up now**.
