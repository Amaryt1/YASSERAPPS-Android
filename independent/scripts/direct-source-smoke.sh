#!/usr/bin/env bash
set -euo pipefail
apk=independent/app/build/outputs/apk/debug/app-debug.apk
testapk=independent/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r "$apk"
adb install -r "$testapk"
adb shell run-as com.yasserapps.store mkdir -p files
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# These are temporary CI fixtures, not committed or packaged into the APK.
for id in fdroid izzyondroid; do
  if [ "$id" = fdroid ]; then base=https://f-droid.org/repo; else base=https://apt.izzysoft.de/fdroid/repo; fi
  curl -fLsS --retry 2 --max-time 120 "$base/entry.jar" -o "$tmp/$id.jar"
  path="$(unzip -p "$tmp/$id.jar" entry.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["index"]["name"].lstrip("/"))')"
  curl -fLsS --retry 2 --max-time 300 "$base/$path" -o "$tmp/$id.json"
  adb exec-in run-as com.yasserapps.store sh -c "'cat > files/$id.jar'" < "$tmp/$id.jar"
  adb exec-in run-as com.yasserapps.store sh -c "'cat > files/$id.json'" < "$tmp/$id.json"
done
adb shell am instrument -w com.yasserapps.store.test/com.yasserapps.store.DirectCatalogTest | tee "$tmp/result.txt"
grep -F 'PASS signed direct sources on Android:' "$tmp/result.txt"
adb shell run-as com.yasserapps.store rm -f files/fdroid.jar files/fdroid.json files/izzyondroid.jar files/izzyondroid.json
# Separate live-device check: report network failure honestly instead of calling it success.
adb shell svc wifi enable
adb shell svc data enable
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for n in $(seq 1 36); do
  adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/live.log"
  if grep -q 'DIRECT_CATALOG total=.* sources=2' "$tmp/live.log"; then
    cat "$tmp/live.log"
    echo 'PASS direct live network for both sources'
    exit 0
  fi
  if grep -q 'DIRECT_CATALOG total=.* sources=' "$tmp/live.log"; then break; fi
  sleep 5
done
adb logcat -d -s YASSERAPPS_CATALOG:V
echo 'FAIL: live source access was not established for both repositories.'
exit 1
