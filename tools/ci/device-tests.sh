#!/usr/bin/env bash
# Runs Ufuq's on-device tests on the connected emulator and collects screenshots.
# Usage: tools/ci/device-tests.sh <font-scale> <out-dir>
set -uo pipefail

FONT="${1:-1.0}"
OUT="${2:-out}"
PKG=com.ahmali.ufuq
mkdir -p "$OUT/screenshots"
STATUS="$OUT/status.txt"
: > "$STATUS"

APP=$(ls apks/debug/*.apk 2>/dev/null | head -1)
TEST=$(ls apks/androidTest/debug/*.apk 2>/dev/null | head -1)
REL=$(ls apks/release/*.apk 2>/dev/null | head -1)
echo "app=$APP test=$TEST release=$REL" | tee -a "$STATUS"

adb wait-for-device
{
  echo "Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "Device: $(adb shell getprop ro.product.model | tr -d '\r')"
  adb shell wm size | tr -d '\r'
  adb shell wm density | tr -d '\r'
  echo "Font scale: $FONT"
} | tee -a "$STATUS"
adb shell settings put system font_scale "$FONT"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c || true

if ! adb install -r -t "$APP" >> "$STATUS" 2>&1; then echo "FAILED: install app" | tee -a "$STATUS"; exit 1; fi
if ! adb install -r -t "$TEST" >> "$STATUS" 2>&1; then echo "FAILED: install tests" | tee -a "$STATUS"; exit 1; fi

# Instrumented tests (keeps the app installed so screenshots can be pulled).
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$OUT/instrumentation.txt"
adb pull "/sdcard/Android/data/$PKG/files/screenshots/." "$OUT/screenshots/" >/dev/null 2>&1 || true
adb logcat -d -v brief AndroidRuntime:E ActivityManager:W "*:F" > "$OUT/logcat.txt" 2>/dev/null || true

FAILED=0
if grep -q "^OK (" "$OUT/instrumentation.txt"; then
  grep "^OK (" "$OUT/instrumentation.txt" >> "$STATUS"
else
  FAILED=1
  echo "FAILED: on-device tests" >> "$STATUS"
  # Test names and the first lines of each failure.
  grep -E "^INSTRUMENTATION_STATUS: (test|stack)=|^Tests run|^FAILURES|INSTRUMENTATION_RESULT|shortMsg|longMsg" "$OUT/instrumentation.txt" \
    | grep -vE "^\s+at (androidx|org\.junit|java\.lang\.reflect|android\.app\.Instrumentation)" | head -60 >> "$STATUS"
fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  FAILED=1
  echo "FAILED: app crashed" >> "$STATUS"
  grep -A12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -40 >> "$STATUS"
fi
if [ "$FAILED" -ne 0 ]; then
  msg=$(tail -40 "$STATUS" | cut -c1-300 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')
  echo "::error title=Device tests failed::${msg}"
fi

# Cold start time of the release build (R8-shrunk), fresh install.
if [ -n "$REL" ]; then
  adb uninstall "$PKG" >/dev/null 2>&1
  if adb install -r "$REL" >/dev/null 2>&1; then
    for i in 1 2 3; do
      adb shell am force-stop "$PKG"
      sleep 1
      adb shell am start -W -n "$PKG/.ui.MainActivity" | tr -d '\r' | grep -E "TotalTime|WaitTime" | tee -a "$OUT/startup.txt"
    done
    sleep 1
    adb shell screencap -p /sdcard/release-start.png && adb pull /sdcard/release-start.png "$OUT/screenshots/00_release_first_launch.png" >/dev/null 2>&1
    adb logcat -d -v brief AndroidRuntime:E "*:F" > "$OUT/logcat-release.txt" 2>/dev/null || true
    if grep -q "FATAL EXCEPTION" "$OUT/logcat-release.txt"; then
      FAILED=1
      echo "FAILED: release build crashed" >> "$STATUS"
      grep -A12 "FATAL EXCEPTION" "$OUT/logcat-release.txt" | head -30 >> "$STATUS"
    fi
    echo "Release cold start: $(grep TotalTime "$OUT/startup.txt" | awk '{print $2}' | tr '\n' ' ')ms" >> "$STATUS"
  else
    echo "release APK could not be installed" | tee -a "$STATUS"
  fi
fi

[ "$FAILED" -eq 0 ] && echo "DEVICE OK" >> "$STATUS"
exit $FAILED
