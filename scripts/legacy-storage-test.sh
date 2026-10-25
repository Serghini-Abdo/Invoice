#!/usr/bin/env bash
# Android 7 to 9 only: grant the storage permission, then start a FRESH instrumentation process so the
# public-folder tests run the way the real app does (permission granted first, work done later).
# Needs an emulator/phone, and `./gradlew :app:installDebug :app:installDebugAndroidTest` already run.
#
# The tests run twice so that CI shows which way of granting works on this image:
#   run 1: the WRITE grant only, as the first version of this script did
#   run 2: after `am force-stop`, with READ and WRITE both granted
set -euo pipefail
PKG=ma.workshop.invoice
# A real phone already has the standard Documents folder; the bare emulator image does not.
adb shell mkdir -p /sdcard/Documents

diagnostics() {
  echo "--- diagnostics ($1)"
  adb shell ls -ld /sdcard /sdcard/Documents || true
  adb shell dumpsys package "$PKG" | grep -i -E "EXTERNAL_STORAGE|gids" || true
  echo "appops:"
  adb shell appops get "$PKG" || true
}

run_tests() {
  adb shell am instrument -w -e class ma.workshop.invoice.AppFolderStoreTest \
    ma.workshop.invoice.test/androidx.test.runner.AndroidJUnitRunner
}

adb shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE
diagnostics "run 1: WRITE granted"
out1=$(run_tests)
echo "=== run 1 output"
echo "$out1"

adb shell am force-stop "$PKG"
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE
diagnostics "run 2: force-stopped, READ and WRITE granted"
out2=$(run_tests)
echo "=== run 2 output"
echo "$out2"

if echo "$out1" | grep -q "^OK ("; then
  echo "Android 7 to 9 public backup folder: VERIFIED on this emulator (run 1, WRITE granted)"
elif echo "$out2" | grep -q "^OK ("; then
  echo "Android 7 to 9 public backup folder: VERIFIED on this emulator (run 2 only: needed force-stop and READ+WRITE)"
else
  # Not a blocker: the app falls back to its private folder and tells the owner. But it is NOT verified.
  echo "::warning title=Android 7 to 9 public backup folder NOT VERIFIED::The app could not write to /sdcard/Documents on this emulator even with WRITE_EXTERNAL_STORAGE granted before the process started. Treat Documents/Fawatir Warsha on Android 7 to 9 as unverified."
  echo "Android 7 to 9 public backup folder: NOT VERIFIED (see warning above)"
fi
