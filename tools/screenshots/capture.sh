#!/usr/bin/env bash
# Captures the store/README screenshots from a running emulator (google_apis image, so `adb root`
# works) with the debug or release APK passed as $1. Output: docs/play/screenshots/*.png
# (phone, 1080x1920), docs/play/tablet-screenshots/*.png (landscape tablet, 2560x1440), both copied to fastlane/,
# and half-scale README copies in docs/screenshots/. The date, size and status bar are pinned so a rerun differs
# only when the app's look does.
set -euo pipefail
APK=${1:?usage: capture.sh app.apk}
cd "$(dirname "$0")/../.."
PKG=com.joebywan.daybook
OUT=docs/play/screenshots
TABLET=docs/play/tablet-screenshots
mkdir -p $OUT $TABLET
rm -f $OUT/*.png $TABLET/*.png   # a shot that was renamed or dropped must not linger
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
demo_bar() {
  demo enter; demo clock -e hhmm 0900; demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e fully true -e mobile hide -e nosim hide; demo notifications -e visible false
}
demo_bar

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
# Opens a puzzle from the home grid. Its tile can be below the fold, or under the pinned difficulty row once
# scrolled, so this swipes until the name sits in the lower part of the screen (30-92% of the height) and taps it.
open_puzzle() {
  local found x y h w
  read -r w h < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
  for _ in $(seq 15); do
    found=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import re, sys, xml.etree.ElementTree as ET
raw = sys.stdin.read()
if "<hierarchy" not in raw: sys.exit(1)
for n in ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>") + 12]).iter("node"):
    if sys.argv[1] in (n.get("text"), n.get("content-desc")):
        x0, y0, x1, y1 = map(int, re.findall(r"\d+", n.get("bounds")))
        if y1 > y0: print((x0 + x1) // 2, (y0 + y1) // 2); sys.exit(0)
print("none")' "$1") || { sleep 2; continue; }
    if [ "$found" = none ]; then
      adb shell input swipe $((w / 2)) $((h * 75 / 100)) $((w / 2)) $((h * 40 / 100)) 400; sleep 1; continue
    fi
    read -r x y <<<"$found"
    if [ "$y" -lt $((h * 30 / 100)) ]; then
      adb shell input swipe $((w / 2)) $((h * 45 / 100)) $((w / 2)) $((h * 65 / 100)) 400; sleep 1
    elif [ "$y" -gt $((h * 92 / 100)) ]; then
      adb shell input swipe $((w / 2)) $((h * 65 / 100)) $((w / 2)) $((h * 45 / 100)) 400; sleep 1
    else
      adb shell input tap "$x" "$y"; sleep 1; return 0
    fi
  done
  echo "no '$1' on the home grid" >&2; return 1
}
DIR=$OUT
shot() { adb exec-out screencap -p > "$DIR/$1.png"; }
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

# The set, in store order. A Hard Cage Sudoku with a hint open shows the cages best; the tier is a saved
# setting, so it goes back to Standard straight after.
shots() {
  open_puzzle Sudoku; sleep 2; tap Hint; tap "Why?"; sleep 3; shot 2-sudoku-hint; back
  tap Hard; open_puzzle "Cage Sudoku"; sleep 2; tap Hint; tap "Why?"; sleep 3; shot 3-cage-sudoku-hint; back; tap Standard
  open_puzzle "Word Wheel"; sleep 2; shot 4-word-wheel; back
  open_puzzle Kings; sleep 2; shot 5-kings; back
  open_puzzle Mosaic; sleep 2; shot 6-mosaic; back
  open_puzzle Snap; sleep 2; shot 7-snap; back
}
shots

# Tablet pass: the same set in landscape, where the board sits beside the controls. 2560x1440 at 320 dpi is a
# 1280x720 dp window (a 10-inch tablet's shape) and fits both of Play's tablet slots (16:9, sides 1080-3840).
# A window this wide gets the launcher's taskbar along the bottom, so the launcher is switched off for the pass;
# the size change also restarts the status bar, which drops demo mode until it is set again.
LAUNCHERS="com.google.android.apps.nexuslauncher com.android.launcher3"
for l in $LAUNCHERS; do adb shell pm disable-user --user 0 $l >/dev/null 2>&1 || true; done
DIR=$TABLET
adb shell wm size 2560x1440
adb shell wm density 320
sleep 5; demo_bar
back; sleep 2; shot 1-home
shots

demo exit
adb shell wm size reset
adb shell wm density reset
for l in $LAUNCHERS; do adb shell pm enable $l >/dev/null 2>&1 || true; done
# README copies are half scale: home, sudoku, cage sudoku, word wheel, and the tablet layout
for p in 1-home:home 2-sudoku-hint:sudoku 3-cage-sudoku-hint:cage-sudoku 4-word-wheel:word-wheel; do
  convert "$OUT/${p%%:*}.png" -resize 50% "docs/screenshots/${p##*:}.png"
done
convert "$TABLET/3-cage-sudoku-hint.png" -resize 50% docs/screenshots/tablet-cage-sudoku.png
# F-Droid reads its listing images from the repo (fastlane layout), so they travel with the Play set.
F=fastlane/metadata/android/en-US/images
rm -f $F/phoneScreenshots/*.png $F/sevenInchScreenshots/*.png $F/tenInchScreenshots/*.png
mkdir -p $F/sevenInchScreenshots $F/tenInchScreenshots
cp "$OUT"/*.png $F/phoneScreenshots/
cp "$TABLET"/*.png $F/sevenInchScreenshots/
cp "$TABLET"/*.png $F/tenInchScreenshots/
