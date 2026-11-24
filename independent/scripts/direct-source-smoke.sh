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
checked=0
mapfile -t source_rows < <(python3 -c 'import json; [print(s["id"]+" "+s["baseUrl"]) for s in json.load(open("catalog/sources.json"))["sources"] if s["enabled"]]')
for source_row in "${source_rows[@]}"; do
  read -r id base <<< "$source_row"
  echo "Checking signed repository $id"
  checked=$((checked+1))
  status="$(curl -LsS --connect-timeout 10 --max-time 90 -w '%{http_code}' "$base/entry.jar" -o "$tmp/$id.jar" || true)"
  if [ "$status" = 200 ]; then
    if ! path="$(unzip -p "$tmp/$id.jar" entry.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["index"]["name"].lstrip("/"))')"; then
      echo "FIXTURE_FETCH_FAILED $id invalid entry"; rm -f "$tmp/$id.jar"; continue
    fi
    if ! curl -fLsS --connect-timeout 10 --max-time 300 "$base/$path" -o "$tmp/$id.json"; then
      echo "FIXTURE_FETCH_FAILED $id index"; rm -f "$tmp/$id.jar" "$tmp/$id.json"; continue
    fi
  elif [ "$status" = 404 ] || [ "$status" = 410 ]; then
    if ! curl -fLsS --connect-timeout 10 --max-time 90 "$base/index-v1.jar" -o "$tmp/$id.jar"; then
      echo "FIXTURE_FETCH_FAILED $id legacy index"; rm -f "$tmp/$id.jar"; continue
    fi
  else
    echo "FIXTURE_FETCH_FAILED $id HTTP $status"; rm -f "$tmp/$id.jar"; continue
  fi
  for ext in jar json; do
    [ -f "$tmp/$id.$ext" ] || continue
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
test "$checked" -eq 15 || { echo "FAIL: only $checked of 15 source candidates processed"; exit 1; }
adb shell am instrument -w com.yasserapps.store.test/com.yasserapps.store.DirectCatalogTest | tee "$tmp/result.txt"
grep -F 'PASS signed direct sources on Android:' "$tmp/result.txt"
adb logcat -d -s YASSERAPPS_CATALOG:V | grep -E 'FIXTURE_(SOURCE|FAILED)' || true
adb shell run-as com.yasserapps.store rm -f files/fdroid.jar files/fdroid.json files/izzyondroid.jar files/izzyondroid.json files/guardian.jar files/guardian.json files/microg.jar files/microg.json
# Confirm the actual home sections and category tabs from the verified cache.
adb shell svc wifi disable
adb shell svc data disable
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for n in $(seq 1 20); do
  adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/ui.log"
  if grep -Eq 'UI_HOME_SECTION kind=app total=[1-9][0-9]+' "$tmp/ui.log" && grep -Eq 'UI_HOME_SECTION kind=game total=[1-9][0-9]+' "$tmp/ui.log"; then break; fi
  sleep 2
done
grep -E 'UI_HOME_SECTION kind=app total=[1-9][0-9]+' "$tmp/ui.log"
grep -E 'UI_HOME_SECTION kind=game total=[1-9][0-9]+' "$tmp/ui.log"
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
# Exercise the actual featured controls and verify that the selected package changes.
adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/featured.log"
grep -F 'UI_FEATURED count=10 index=0' "$tmp/featured.log"
adb shell uiautomator dump /sdcard/yasser-ui.xml >/dev/null
adb pull /sdcard/yasser-ui.xml "$tmp/ui.xml" >/dev/null
read -r x y < <(python3 - "$tmp/ui.xml" <<'PY'
import sys,re,xml.etree.ElementTree as E
for n in E.parse(sys.argv[1]).iter('node'):
    if n.get('content-desc')=='المميز التالي':
        a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
        print((a+c)//2,(b+d)//2)
        break
else: raise SystemExit('Missing featured next control')
PY
)
adb shell input tap "$x" "$y"
sleep 2
adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/featured.log"
grep -F 'UI_FEATURED count=10 index=1' "$tmp/featured.log"
echo 'PASS ten featured recommendations and next navigation'
adb logcat -c
tap_tab 'التطبيقات'
adb logcat -d -s YASSERAPPS_CATALOG:I | grep -E 'UI_LIST total=[1-9][0-9]+ rendered=40'
adb logcat -c
tap_tab 'الألعاب'
adb logcat -d -s YASSERAPPS_CATALOG:I | grep -E 'UI_LIST total=[1-9][0-9]+ rendered=[1-9][0-9]*'
echo 'PASS verified source entries in home and category tabs'
adb shell am force-stop com.yasserapps.store
# Separate live-device check: require 15 signed repositories, 2113 and PortHub discovery.
adb shell svc wifi enable
adb shell svc data enable
sleep 3
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for n in $(seq 1 120); do
  adb logcat -d -s YASSERAPPS_CATALOG:I > "$tmp/live.log"
  if grep -Eq 'DIRECT_CATALOG total=[0-9]+ sources=17 ' "$tmp/live.log"; then
    cat "$tmp/live.log"
    echo 'PASS live connection for all 17 configured sources'
    exit 0
  fi
  if grep -q 'DIRECT_CATALOG total=.* sources=' "$tmp/live.log"; then break; fi
  sleep 5
done
adb logcat -d -s YASSERAPPS_CATALOG:V
echo 'FAIL: live source access was not established for all fifteen signed repositories, 2113 and PortHub discovery.'
exit 1
