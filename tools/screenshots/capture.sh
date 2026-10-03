#!/usr/bin/env bash
# Captures the store/README screenshots from a running emulator (google_apis image, so `adb root`
# works) with the debug or release APK passed as $1. Output: docs/play/screenshots/*.png (1080x1920, copied to fastlane/)
# and half-scale README copies in docs/screenshots/. The date, size and status bar are pinned so a
# rerun differs only when the app's look does.
set -euo pipefail
APK=${1:?usage: capture.sh app.apk}
cd "$(dirname "$0")/../.."
PKG=com.joebywan.daybook
OUT=docs/play/screenshots
adb wait-for-device
adb root >/dev/null || true; sleep 2; adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = 1 ]; do sleep 1; done

adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell setprop persist.sys.timezone UTC
adb shell date 092412002026.00 >/dev/null     # MMDDhhmmYYYY.ss: Thu 24 Sep 2026, noon
adb shell settings put global hide_error_dialogs 1   # a slow CI emulator ANRs the launcher after the clock jump
sleep 10
adb shell wm size 1080x1920
adb shell wm density 420
adb shell settings put secure show_ime_with_hard_keyboard 0

# demo-mode status bar: 9:00, full wifi, full battery, no notifications
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 0900; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true -e mobile hide -e nosim hide; demo notifications -e visible false

adb uninstall $PKG >/dev/null 2>&1 || true
adb install -r "$APK" >/dev/null

# Taps the centre of the first node whose text or description equals $1, retrying for ~30 s because a
# slow emulator sometimes returns no dump or has not drawn the screen yet; fails if it never appears.
tap() {
  local xy
  for _ in $(seq 15); do
    xy=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import re, sys, xml.etree.ElementTree as ET
raw = sys.stdin.read()
if "<hierarchy" not in raw: sys.exit(1)
for n in ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>") + 12]).iter("node"):
    if sys.argv[1] in (n.get("text"), n.get("content-desc")):
        x0, y0, x1, y1 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x0 + x1) // 2, (y0 + y1) // 2); sys.exit(0)
sys.exit(1)' "$1") && { adb shell input tap $xy; sleep 1; return 0; }
    sleep 2
  done
  echo "no '$1' on screen" >&2; return 1
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
# A cold start lands on the home grid whatever the board or hint popover was doing.
back() { adb shell am start -S -n $PKG/.MainActivity >/dev/null; sleep 4; }

adb shell am start -n $PKG/.MainActivity >/dev/null
# A cold CI emulator can take a while to draw; the date text doubles as the check that the pin took.
for _ in $(seq 20); do
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q "Thursday 24 September" && break
  sleep 2
done
adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q "Thursday 24 September" || {
  echo "date pin failed; device date: $(adb shell date)" >&2
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'text="[^"]\+"' | head -20 >&2
  exit 1
}
shot 1-home

tap Sudoku; sleep 2; tap Hint; tap "Why?"; sleep 3; shot 2-sudoku-hint; back
tap Kings; sleep 2; shot 3-kings; back
tap Mosaic; sleep 2; shot 4-mosaic; back
tap Snap; sleep 2; shot 5-snap; back

demo exit
# README copies are half scale: home, sudoku, mosaic, snap
for p in 1-home:home 2-sudoku-hint:sudoku 4-mosaic:mosaic 5-snap:snap; do
  convert "$OUT/${p%%:*}.png" -resize 50% "docs/screenshots/${p##*:}.png"
done
# F-Droid reads its listing images from the repo (fastlane layout), so they travel with the Play set.
cp "$OUT"/*.png fastlane/metadata/android/en-AU/images/phoneScreenshots/
