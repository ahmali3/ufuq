#!/usr/bin/env bash
# Runs Ufuq's on-device tests on the connected emulator and collects screenshots.
# Usage: tools/ci/device-tests.sh <font-scale> <out-dir>
set -uo pipefail

FONT="${1:-1.0}"
OUT="${2:-out}"
PKG=com.ahmali.ufuq
mkdir -p "$OUT"

APP=$(ls apks/debug/*.apk 2>/dev/null | head -1)
TEST=$(ls apks/androidTest/debug/*.apk 2>/dev/null | head -1)
REL=$(ls apks/release/*.apk 2>/dev/null | head -1)
[ -z "$APP" ] && APP=$(find apks -name '*-debug.apk' ! -name '*androidTest*' | head -1)
[ -z "$TEST" ] && TEST=$(find apks -name '*androidTest*.apk' | head -1)
echo "app=$APP test=$TEST release=$REL"

adb wait-for-device
adb shell getprop ro.build.version.release | tee "$OUT/android-version.txt"
adb shell wm size | tee -a "$OUT/android-version.txt"
adb shell settings put system font_scale "$FONT"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb install -r -t "$APP" || exit 1
adb install -r -t "$TEST" || exit 1

# Instrumented tests (keeps the app installed so screenshots can be pulled).
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumentation.txt"
adb pull "/sdcard/Android/data/$PKG/files/screenshots" "$OUT/screenshots" >/dev/null 2>&1 || \
  adb shell "run-as $PKG ls" >/dev/null 2>&1
adb logcat -d -s AndroidRuntime:E TestRunner:* > "$OUT/logcat.txt" || true

FAILED=0
if ! grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "::error::On-device tests failed"
  FAILED=1
fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::App crashed on this device"
  FAILED=1
fi

# Cold start time of the release build (no INTERNET, R8-shrunk).
if [ -n "$REL" ]; then
  adb uninstall "$PKG" >/dev/null 2>&1
  if adb install -r "$REL" >/dev/null 2>&1; then
    for i in 1 2 3; do
      adb shell am force-stop "$PKG"
      sleep 1
      adb shell am start -W -n "$PKG/.ui.MainActivity" | grep -E "TotalTime|WaitTime" | tee -a "$OUT/startup.txt"
    done
    adb shell screencap -p /sdcard/release-start.png && adb pull /sdcard/release-start.png "$OUT/screenshots/00_release_first_launch.png" >/dev/null 2>&1
  else
    echo "release APK is unsigned; skipping cold-start check" | tee -a "$OUT/startup.txt"
  fi
fi

exit $FAILED
