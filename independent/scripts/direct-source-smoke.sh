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
  for ext in jar json; do
    remote="/data/local/tmp/yasser-$id.$ext"
    adb push "$tmp/$id.$ext" "$remote"
    adb shell chmod 644 "$remote"
    adb shell run-as com.yasserapps.store cp "$remote" "files/$id.$ext"
    adb shell run-as com.yasserapps.store ls -l "files/$id.$ext"
    actual="$(adb shell run-as com.yasserapps.store sha256sum "files/$id.$ext" | awk '{print $1}' | tr -d '\r')"
    expected="$(sha256sum "$tmp/$id.$ext" | awk '{print $1}')"
    test "$actual" = "$expected"
    adb shell rm "$remote"
  done
done
adb shell am instrument -w com.yasserapps.store.test/com.yasserapps.store.DirectCatalogTest | tee "$tmp/result.txt"
grep -F 'PASS signed direct sources on Android:' "$tmp/result.txt"
adb shell run-as com.yasserapps.store rm -f files/fdroid.jar files/fdroid.json files/izzyondroid.jar files/izzyondroid.json
# Confirm the actual home sections and category tabs from the verified cache.
adb shell svc wifi disable
adb shell svc data disable
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for n in $(seq 1 20); do
  adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/ui.log"
  if grep -Eq 'UI_HOME_SECTION kind=app total=[1-9][0-9]+' "$tmp/ui.log" && grep -Eq 'UI_HOME_SECTION kind=game total=[1-9][0-9]*' "$tmp/ui.log"; then break; fi
  sleep 2
done
grep -E 'UI_HOME_SECTION kind=app total=[1-9][0-9]+' "$tmp/ui.log"
grep -E 'UI_HOME_SECTION kind=game total=[1-9][0-9]*' "$tmp/ui.log"
tap_tab() {
  adb shell uiautomator dump /sdcard/yasser-ui.xml >/dev/null
  adb pull /sdcard/yasser-ui.xml "$tmp/ui.xml" >/dev/null
  read -r x y < <(python3 - "$tmp/ui.xml" "$1" <<'PY'
import sys,re,xml.etree.ElementTree as E
for n in E.parse(sys.argv[1]).iter('node'):
    if n.get('text')==sys.argv[2]:
        a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
        print((a+c)//2,(b+d)//2)
        break
else: raise SystemExit('Missing tab '+sys.argv[2])
PY
)
  adb shell input tap "$x" "$y"
  sleep 2
}
adb logcat -c
tap_tab 'التطبيقات'
adb logcat -d -s YASSERAPPS_CATALOG:I | grep -E 'UI_LIST total=[1-9][0-9]+ rendered=40'
adb logcat -c
tap_tab 'الألعاب'
adb logcat -d -s YASSERAPPS_CATALOG:I | grep -E 'UI_LIST total=[1-9][0-9]* rendered=[1-9][0-9]*'
echo 'PASS verified source entries in home and category tabs'
adb shell am force-stop com.yasserapps.store
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
