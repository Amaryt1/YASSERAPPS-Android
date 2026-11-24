#!/usr/bin/env bash
set -euo pipefail
test -s apk/app-debug.apk
adb install -r apk/app-debug.apk
# Hidden DownloadManager notifications need the Android normal permission.
# Fail the smoke test if the OS didn't grant it; otherwise an OEM might
# display the generic system download notification alongside YASSERAPPS.
adb shell dumpsys package com.yasserapps.store > independent-package-permissions.txt
if ! grep -E 'android.permission.DOWNLOAD_WITHOUT_NOTIFICATION: granted=true' independent-package-permissions.txt; then
  echo 'FAIL: Android did not grant DOWNLOAD_WITHOUT_NOTIFICATION'
  grep -i 'download_without_notification' independent-package-permissions.txt || true
  exit 1
fi
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
sleep 8
adb shell screencap -p /sdcard/yasserapps-home.png
adb pull /sdcard/yasserapps-home.png independent-home.png

# RTL category navigation in V10.2: All / Apps / Games.
# Coordinates are for the configured Pixel 6 emulator display.
adb shell input tap 925 435
sleep 2
adb shell screencap -p /sdcard/yasserapps-all.png
adb pull /sdcard/yasserapps-all.png independent-all.png
adb shell input tap 670 435
sleep 2
adb shell screencap -p /sdcard/yasserapps-apps.png
adb pull /sdcard/yasserapps-apps.png independent-apps.png
adb shell input tap 400 435
sleep 2
adb shell screencap -p /sdcard/yasserapps-games.png
adb pull /sdcard/yasserapps-games.png independent-games.png

# Pixel 6 emulator viewport: 1080x2400. Verify each real menu can be opened
# without depending on the live catalog having any published third-party APKs.
adb shell input tap 350 2250
sleep 3
adb shell screencap -p /sdcard/yasserapps-downloads.png
adb pull /sdcard/yasserapps-downloads.png independent-downloads.png

adb shell input tap 130 2250
sleep 3
adb shell screencap -p /sdcard/yasserapps-settings.png
adb pull /sdcard/yasserapps-settings.png independent-settings.png

adb shell input tap 635 2250
sleep 3
adb shell screencap -p /sdcard/yasserapps-search.png
adb pull /sdcard/yasserapps-search.png independent-search.png

adb shell input keyevent 4
sleep 2
adb shell input tap 170 440
sleep 3
adb shell screencap -p /sdcard/yasserapps-updates.png
adb pull /sdcard/yasserapps-updates.png independent-updates.png

adb logcat -d -v time > independent-emulator-logcat.txt
adb shell pidof com.yasserapps.store > independent-pid.txt || true
if ! grep -Eq '[0-9]+' independent-pid.txt; then
  echo 'FAIL: YASSERAPPS process is not running'
  grep -A 30 -B 6 'FATAL EXCEPTION' independent-emulator-logcat.txt | tail -80 || true
  exit 1
fi
if grep -q 'FATAL EXCEPTION' independent-emulator-logcat.txt; then
  echo 'FAIL: Android reported a fatal exception'
  grep -A 30 -B 6 'FATAL EXCEPTION' independent-emulator-logcat.txt | tail -80
  exit 1
fi
echo 'PASS: YASSERAPPS store opens without fatal exception and suppress-system-download permission is granted.'
