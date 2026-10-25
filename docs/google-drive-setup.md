# Google Drive backup: one-time Google Cloud configuration

Invoicing never needs any of this. It is only for the optional *Backup > Google Drive* buttons. The code
(`platform/drive/DriveBackupClient.kt`) contains **no client id, secret or API key**; Google identifies the
app by its package name and signing certificate.

## What the app asks for

* Scope: `https://www.googleapis.com/auth/drive.appdata` only. The backup lives in a hidden,
  app-private folder of the user's Drive; the app cannot read any other Drive file. This is a
  non-sensitive scope, so no Google verification review is needed.
* API: Google Identity `AuthorizationClient` (play-services-auth) for the one-time consent screen, then
  the plain Drive v3 REST API.
* Needs: internet, a Google account on the phone, and Google Play Services. Without them the screen
  shows a clear message and offers the file backup instead.

## Setup (once per signing key)

1. Open <https://console.cloud.google.com/>, create (or pick) a project.
2. **APIs & Services > Library**: enable **Google Drive API**.
3. **APIs & Services > OAuth consent screen**: choose *External*, fill in the app name, add the scope
   `.../auth/drive.appdata`, and add the workshop's Google account(s) as *test users* while the app is in
   *Testing*. (Publish it to remove the test-user limit; no review is required for this scope.)
4. **APIs & Services > Credentials > Create credentials > OAuth client ID**, type **Android**:
   * Package name: `ma.workshop.invoice`
   * SHA-1 certificate fingerprint of the key that signs the build:
     * debug build: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
     * release build: the SHA-1 of your release keystore. If you distribute through Google Play with Play
       App Signing, register Play's *app signing key* SHA-1 as well.
5. Nothing is copied into the source tree. Install the build signed with that key and tap
   *Backup > Google Drive*.

## Verification status

The Drive client has not been exercised against a real Google account in this repository's automated
tests (they need a Google account, Play Services and network). The pieces that decide whether a
restore is safe (parsing, validation, the atomic database transaction) are fully tested without Drive.
