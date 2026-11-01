#!/usr/bin/env bash
# Smoke test for HackerTube TV on an Android TV emulator (run by CI inside android-emulator-runner).
# Exit code: 0 = pass, 1 = fail (CI blocks the beta release on failure).
# Usage: tests/smoke-test.sh <apk> [out-dir]
#
# Fails the build if:
#   - the APK doesn't install
#   - the app crashes or dies
#   - no HackerTube rows load from hackertube.org within 90 seconds
# Playback is checked and reported, but only warns: emulators are unreliable at video decoding.
set -uo pipefail

APK="$1"
OUT="${2:-smoke}"
PKG="org.hackertube.tv"
ACTIVITY="$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"
mkdir -p "$OUT"
SUMMARY="$OUT/summary.md"
: > "$SUMMARY"
FAIL=0

log()  { echo "$*"; echo "$*" >> "$SUMMARY"; }
pass() { log "- ✅ $*"; }
warn() { log "- ⚠️ $*"; }
fail() { log "- ❌ $*"; FAIL=1; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
alive() { [ -n "$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; }
crashed() { adb logcat -d -b crash 2>/dev/null | grep -q "Process: $PKG"; }

log "### HackerTube smoke test"
log ""

adb wait-for-device
adb shell settings put global package_verifier_enable 0 >/dev/null 2>&1 || true

if adb install -r -g "$APK" > "$OUT/install.txt" 2>&1 || adb install -r "$APK" >> "$OUT/install.txt" 2>&1; then
  pass "Installed $(basename "$APK")"
else
  fail "Install failed: $(tail -1 "$OUT/install.txt")"
  exit 1
fi

adb logcat -c
adb shell am start -n "$ACTIVITY" > /dev/null

# 1. HackerTube rows load (the app logs 'row loaded' for every row it gets from hackertube.org)
ROWS=0
for i in $(seq 1 18); do
  sleep 5
  ROWS=$(adb logcat -d -s HackerTube:I 2>/dev/null | grep -c "row loaded" || true)
  [ "$ROWS" -ge 2 ] && break
done
shot 01-home
if [ "$ROWS" -ge 2 ]; then
  pass "HackerTube home loaded ($ROWS rows from hackertube.org)"
else
  fail "HackerTube rows did not load within 90s (rows seen: $ROWS)"
fi
adb logcat -d -s HackerTube:* > "$OUT/hackertube-log.txt" 2>&1

# 2. Play the focused (first) video
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 25
shot 02-playback
if adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q "PlaybackActivity"; then
  pass "Playback screen opened"
else
  warn "Playback screen not detected (often an emulator limitation; check 02-playback.png)"
fi

# Up next from HackerTube (related videos) should load while a HackerTube video is open
if adb logcat -d -s HackerTube:I 2>/dev/null | grep -q "related loaded"; then
  pass "Up next from HackerTube loaded"
else
  warn "Up next from HackerTube not seen in the log"
fi

# 3. Back to browse, then down through a few rows (exercises paging and row loading)
adb shell input keyevent KEYCODE_BACK
sleep 3
for k in 1 2 3; do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 2; done
for k in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1; done
sleep 5
shot 03-browse

# 4. Still alive, no crash
if crashed; then
  fail "App crashed (see crash.txt)"
elif alive; then
  pass "App still running after the test, no crash"
else
  fail "App process is gone (killed or crashed)"
fi

adb logcat -d -b crash -v threadtime > "$OUT/crash.txt" 2>&1
adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1
adb shell am force-stop "$PKG" || true

log ""
[ "$FAIL" -eq 0 ] && log "**Result: PASS**" || log "**Result: FAIL**"
exit "$FAIL"
