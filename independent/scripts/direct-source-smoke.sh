#!/usr/bin/env bash
set -euo pipefail
adb install -r independent/app/build/outputs/apk/debug/app-debug.apk
adb install -r independent/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
adb shell am instrument -w com.yasserapps.store.test/com.yasserapps.store.DirectCatalogTest | tee "$tmp/result.txt"
grep -F 'PASS market metadata boundaries and pagination fixtures' "$tmp/result.txt"
adb shell svc wifi enable
adb shell svc data enable
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for n in $(seq 1 120); do
  adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/live.log"
  if grep -Eq 'DIRECT_CATALOG total=[0-9]+ sources=[1-4] ' "$tmp/live.log"; then break; fi
  if grep -q 'DIRECT_CATALOG total=.* sources=' "$tmp/live.log"; then break; fi
  sleep 5
done
cat "$tmp/live.log"
grep -E 'DIRECT_SOURCE_OK aptoide count=[1-9][0-9]+' "$tmp/live.log"
grep -E 'DIRECT_CATALOG total=[1-9][0-9]+ sources=[1-4] downloadable=[0-9]+ discoveryPorts=0 marketplaceListings=[1-9][0-9]+' "$tmp/live.log"
grep -E 'UI_HOME_SECTION kind=app total=[1-9][0-9]+' "$tmp/live.log"
grep -E 'UI_HOME_SECTION kind=game total=[1-9][0-9]+' "$tmp/live.log"
grep -E 'UI_FEATURED count=10 index=' "$tmp/live.log"
# After the full catalog finishes, the carousel must advance without rebuilding the home sections.
adb logcat -c
sleep 14
adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/rotation.log"
cat "$tmp/rotation.log"
test "$(grep 'UI_FEATURED count=10 index=' "$tmp/rotation.log" | sed -E 's/.*index=([0-9]+).*/\1/' | sort -u | wc -l)" -ge 2
if grep -q 'UI_HOME_SECTION' "$tmp/rotation.log"; then echo 'FAIL carousel caused home section rebuild'; exit 1; fi
if grep -q 'DIRECT_SOURCE_OK apkmirror' "$tmp/live.log"; then echo 'PASS APKMirror live RSS'; else echo 'APKMirror live RSS unavailable; not counted'; fi
for source in uptodown taptap; do
  if grep -q "DIRECT_SOURCE_OK $source " "$tmp/live.log"; then echo "PASS live metadata $source"; else echo "UNAVAILABLE $source; not counted"; fi
done
 echo 'PASS isolated featured automatic rotation and live Aptoide metadata'
