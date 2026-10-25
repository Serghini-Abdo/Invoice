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
echo "$out" | grep -q "^OK (" || { echo "legacy storage tests did not pass"; exit 1; }
