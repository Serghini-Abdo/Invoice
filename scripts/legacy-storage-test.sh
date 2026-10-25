#!/usr/bin/env bash
# Android 7 to 9 only: grant the storage permission, then start a FRESH instrumentation process so the
# public-folder tests run the way the real app does (permission granted first, work done later).
# Needs an emulator/phone, and `./gradlew :app:installDebug :app:installDebugAndroidTest` already run.
set -euo pipefail
# A real phone already has the standard Documents folder; the bare emulator image does not.
adb shell mkdir -p /sdcard/Documents
adb shell pm grant ma.workshop.invoice android.permission.WRITE_EXTERNAL_STORAGE
echo "--- diagnostics before the run"
adb shell ls -ld /sdcard /sdcard/Documents || true
adb shell dumpsys package ma.workshop.invoice | grep -i -E "WRITE_EXTERNAL_STORAGE|gids" || true
out=$(adb shell am instrument -w -e class ma.workshop.invoice.AppFolderStoreTest \
  ma.workshop.invoice.test/androidx.test.runner.AndroidJUnitRunner)
echo "$out"
if echo "$out" | grep -q "^OK ("; then
  echo "Android 7 to 9 public backup folder: VERIFIED on this emulator"
else
  # Not a blocker: the app falls back to its private folder and tells the owner. But it is NOT verified.
  echo "::warning title=Android 7 to 9 public backup folder NOT VERIFIED::The app could not write to /sdcard/Documents on this emulator even with WRITE_EXTERNAL_STORAGE granted before the process started. Treat Documents/Fawatir Warsha on Android 7 to 9 as unverified."
  echo "Android 7 to 9 public backup folder: NOT VERIFIED (see warning above)"
fi
