#!/usr/bin/env bash
set -euo pipefail
PACKAGE="com.yasserapps.store"
APK="apk/app-debug.apk"
test -s "$APK"
adb install -r "$APK"
adb logcat -c
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 18
adb logcat -d -v time > emulator-logcat.txt
echo "Process information:"
adb shell pidof "$PACKAGE" > app-pid.txt || true
cat app-pid.txt
if ! grep -Eq '[0-9]+' app-pid.txt; then
  echo "Application process disappeared after launching."
  grep -A 35 -B 7 'FATAL EXCEPTION' emulator-logcat.txt | tail -100 || true
  exit 1
fi
if grep -q 'FATAL EXCEPTION' emulator-logcat.txt; then
  echo "A fatal Android exception occurred during startup:"
  grep -A 35 -B 7 'FATAL EXCEPTION' emulator-logcat.txt | tail -100
  exit 1
fi
echo "PASS: $PACKAGE remained running on Android 15, no fatal exception after 18 seconds."
