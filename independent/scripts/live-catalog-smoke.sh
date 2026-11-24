#!/usr/bin/env bash
set -euo pipefail
expected="$(python3 -c 'import json; print(sum(x.get("enabled", True) for x in json.load(open("catalog/apps.json"))["apps"]))')"
adb install -r independent/app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for attempt in $(seq 1 24); do
  log="$(adb logcat -d -s YASSERAPPS_CATALOG:I)"
  count="$(printf '%s\n' "$log" | sed -n 's/.*LIVE_CATALOG_OK count=\([0-9][0-9]*\).*/\1/p' | tail -1)"
  if [ -n "$count" ] && [ "$count" -ge "$expected" ]; then
    echo "PASS: Android received $count live entries; expected at least $expected."
    exit 0
  fi
  sleep 5
done
adb logcat -d -s YASSERAPPS_CATALOG:V
echo "FAIL: Android did not confirm receipt of the current catalog ($expected entries)."
exit 1
