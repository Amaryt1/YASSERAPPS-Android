#!/usr/bin/env bash
set -euo pipefail
test -s apk/app-debug.apk
adb install -r apk/app-debug.apk
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
sleep 18
adb logcat -d -v time > independent-emulator-logcat.txt
adb shell pidof com.yasserapps.store > independent-pid.txt || true
if ! grep -Eq '[0-9]+' independent-pid.txt; then
  echo 'FAIL: YASSERAPPS process is not running'
  grep -A 30 -B 6 'FATAL EXCEPTION' independent-emulator-logcat.txt | tail -80 || true
  exit 1
fi
if grep -q 'FATAL EXCEPTION' independent-emulator-logcat.txt; then
  echo 'FAIL: crash detected'
  grep -A 30 -B 6 'FATAL EXCEPTION' independent-emulator-logcat.txt | tail -80
  exit 1
fi
echo 'PASS: Independent YASSERAPPS launched and stayed running without a fatal exception.'
