#!/usr/bin/env bash
# CI build: city DB, reference data, unit tests, lint, APKs, size/permission checks.
# Writes logs and reports to out/build and the APKs to apks/.
set -uo pipefail

OUT=out/build
mkdir -p "$OUT" apks
LOG="$OUT/build.log"
: > "$LOG"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

step() {
  local name="$1"; shift
  echo "::group::$name"
  echo "===== $name" >> "$LOG"
  "$@" 2>&1 | tee -a "$LOG"
  local rc=${PIPESTATUS[0]}
  echo "::endgroup::"
  if [ "$rc" -ne 0 ]; then
    echo "FAILED: $name (exit $rc)" | tee -a "$OUT/status.txt"
    # Short, public error summary (annotations are visible without signing in).
    local msg
    msg=$(grep -E "^e: |error:|Error:|FAILED|What went wrong|Exception|expected:<|AssertionError" "$LOG" | tail -40 | cut -c1-300 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')
    echo "::error title=$name failed::${msg}"
    collect
    exit "$rc"
  fi
  echo "OK: $name" >> "$OUT/status.txt"
}

collect() {
  cp -r app/build/reports "$OUT/reports" 2>/dev/null || true
  cp -r app/build/test-results "$OUT/test-results" 2>/dev/null || true
  # Failed unit tests, one per line.
  if ls app/build/test-results/testDebugUnitTest/*.xml >/dev/null 2>&1; then
    python3 - "$OUT/unit-test-failures.txt" app/build/test-results/testDebugUnitTest/*.xml <<'PY'
import sys, xml.etree.ElementTree as ET
out = open(sys.argv[1], "w")
total = fails = 0
for f in sys.argv[2:]:
    r = ET.parse(f).getroot()
    for tc in r.iter("testcase"):
        total += 1
        for fl in list(tc.findall("failure")) + list(tc.findall("error")):
            fails += 1
            out.write(f"{tc.get('classname')}.{tc.get('name')}: {(fl.get('message') or '')[:400]}\n")
out.write(f"\n{total} tests, {fails} failed\n")
PY
  fi
}

step "City database" python3 tools/build_cities.py
step "Reference data" bash -c "cd tools/reference && npm install --no-audit --no-fund && npm run gen"
step "Unit tests" gradle :app:testDebugUnitTest --stacktrace --console=plain
collect
step "Lint" gradle :app:lintDebug --console=plain
step "Assemble" gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain
collect

mkdir -p apks/debug apks/androidTest/debug apks/release
cp app/build/outputs/apk/debug/*.apk apks/debug/
cp app/build/outputs/apk/androidTest/debug/*.apk apks/androidTest/debug/
cp app/build/outputs/apk/release/*.apk apks/release/

REL=$(ls apks/release/*.apk | head -1)
SIZE=$(stat -c%s "$REL")
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)
"$AAPT" dump permissions "$REL" > "$OUT/permissions.txt"
"$AAPT" dump badging "$REL" | grep -E "^package:|sdkVersion|targetSdkVersion" > "$OUT/badging.txt" || true
ls -l apks/*/*.apk apks/*/*/*.apk 2>/dev/null > "$OUT/apk-sizes.txt"
{
  echo "Release APK: $(echo "scale=2; $SIZE/1048576" | bc) MB"
  echo '```'; cat "$OUT/permissions.txt"; cat "$OUT/badging.txt"; echo '```'
} | tee -a "$SUMMARY" > "$OUT/release.txt"
echo "::notice title=Release APK::$(echo "scale=2; $SIZE/1048576" | bc) MB"

if grep -q "android.permission.INTERNET" "$OUT/permissions.txt"; then
  echo "::error::INTERNET permission found in the release APK"
  echo "FAILED: INTERNET permission present" >> "$OUT/status.txt"
  exit 1
fi
if [ "$SIZE" -gt 3670016 ]; then
  echo "::warning::Release APK is over 3.5 MB"
fi
echo "BUILD OK" >> "$OUT/status.txt"
