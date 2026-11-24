#!/usr/bin/env bash
set -euo pipefail
expected="$(python3 -c 'import json; print(sum(x.get("enabled", True) for x in json.load(open("catalog/apps.json"))["apps"]))')"
adb install -r independent/app/build/outputs/apk/debug/app-debug.apk
# Verify actual category navigation against the embedded catalog even if emulator DNS is unavailable.
read -r expected_apps expected_games < <(python3 -c 'import json; a=[x for x in json.load(open("catalog/apps.json"))["apps"] if x.get("enabled",True)]; print(sum(x["category"]=="app" for x in a),sum(x["category"]=="game" for x in a))')
adb shell svc wifi disable
adb shell svc data disable
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
sleep 5
adb logcat -d -s YASSERAPPS_CATALOG:I > /tmp/yasser-ui.log
grep -F "UI_HOME_SECTION kind=app total=$expected_apps" /tmp/yasser-ui.log
grep -F "UI_HOME_SECTION kind=game total=$expected_games" /tmp/yasser-ui.log
tap_tab() {
  adb shell uiautomator dump /sdcard/yasser-ui.xml >/dev/null
  adb pull /sdcard/yasser-ui.xml /tmp/yasser-ui.xml >/dev/null
  read -r x y < <(python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
for node in ET.parse('/tmp/yasser-ui.xml').iter('node'):
    if node.get('text') == sys.argv[1]:
        a,b,c,d = map(int,re.findall(r'\d+',node.get('bounds')))
        print((a+c)//2,(b+d)//2)
        break
else:
    raise SystemExit("Tab not found: "+sys.argv[1])
PY
)
  adb shell input tap "$x" "$y"
  sleep 2
}
tap_tab 'التطبيقات'
adb logcat -d -s YASSERAPPS_CATALOG:I > /tmp/yasser-ui.log
grep -F "UI_LIST total=$expected_apps rendered=$(( expected_apps < 40 ? expected_apps : 40 ))" /tmp/yasser-ui.log
tap_tab 'الألعاب'
adb logcat -d -s YASSERAPPS_CATALOG:I > /tmp/yasser-ui.log
grep -F "UI_LIST total=$expected_games rendered=$(( expected_games < 40 ? expected_games : 40 ))" /tmp/yasser-ui.log
echo "PASS: Home and category tabs show $expected_apps apps and $expected_games games."
adb shell am force-stop com.yasserapps.store
# Check host connectivity and enable emulator networking before app launch.
curl -fLsS --max-time 30 'https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/apps.json' -o /tmp/yasser-live.json
python3 -c 'import json; print("Host live catalog:", len(json.load(open("/tmp/yasser-live.json"))["apps"]))'
adb shell svc wifi enable
adb shell svc data enable
for attempt in $(seq 1 20); do
  if adb shell dumpsys connectivity | grep -E 'VALIDATED' | grep -q 'INTERNET\|VALIDATED'; then break; fi
  sleep 3
done
adb shell dumpsys connectivity | grep -E 'Active default network|NetworkAgentInfo|VALIDATED' | head -30 || true
adb logcat -c
adb shell monkey -p com.yasserapps.store -c android.intent.category.LAUNCHER 1
for attempt in $(seq 1 48); do
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
