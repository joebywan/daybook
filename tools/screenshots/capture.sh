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
adb shell date 100712002026.00 >/dev/null     # MMDDhhmmYYYY.ss: Wed 7 Oct 2026, noon
# Today's three come from the date (core/TodaysThree.kt; a clean install gets the starter three). A chip's name can
# match before its tile's, so a puzzle may open from that row; it is the same daily either way.
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
      adb shell input tap "$x" "$y"; sleep 3
      # A puzzle's first board opens on its walkthrough (autoWalkthrough); Skip leaves it for the board.
      if adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q 'text="Skip"'; then tap Skip; fi
      return 0
    fi
  done
  echo "no '$1' on the home grid" >&2; return 1
}
DIR=$OUT
shot() { adb exec-out screencap -p > "$DIR/$1.png"; }
# A cold start lands on the home grid whatever the board or hint popover was doing.
back() { adb shell am start -S -n $PKG/.MainActivity >/dev/null; sleep 4; }

# A clean install, opened past the welcome (ui/home/Welcome.kt) in the look $1: Start waits for a look to be picked.
fresh() {
  adb uninstall $PKG >/dev/null 2>&1 || true
  adb install -r "$APK" >/dev/null
  adb shell am start -n $PKG/.MainActivity >/dev/null
  tap "$1"; tap Start
  # A cold CI emulator can take a while to draw; the date text doubles as the check that the pin took.
  for _ in $(seq 20); do
    adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q "Wednesday 7 October" && return 0
    sleep 2
  done
  echo "date pin failed; device date: $(adb shell date)" >&2
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'text="[^"]\+"' | head -20 >&2
  exit 1
}
# Types $1 on the on-screen keyboard, then Enter. A key's letter can also be on a tile above, so each tap takes the
# lowest node with that text.
type_keys() {
  local k xy
  for k in $(echo "$1" | grep -o .) Enter; do
    xy=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import re, sys, xml.etree.ElementTree as ET
raw = sys.stdin.read()
hits = [list(map(int, re.findall(r"\d+", n.get("bounds")))) for n in
        ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>") + 12]).iter("node") if n.get("text") == sys.argv[1]]
x0, y0, x1, y1 = max(hits, key=lambda b: b[1])
print((x0 + x1) // 2, (y0 + y1) // 2)' "$k")
    adb shell input tap $xy
  done
  sleep 2
}
# The hint's move made: Hint, Why?, Show me.
show_me() { tap Hint; tap "Why?"; tap "Show me"; sleep 1; }
FONT=app/src/main/res/font/fredoka_bold.ttf
# The home screen in Classic over the same screen in Arcade, "New Look!" across the seam.
new_look() {
  local w h band
  read -r w h < <(identify -format '%w %h\n' "$DIR/home.png")
  band=$((h / 11))
  convert \( "$DIR/classic.png" -crop "${w}x$((h / 2))+0+0" +repage \) \
          \( "$DIR/home.png" -crop "${w}x$((h - h / 2))+0+$((h / 2))" +repage \) -append \
    -fill '#000a' -draw "rectangle 0,$((h / 2 - band / 2 + band / 12)) $w,$((h / 2 + band / 2 + band / 12))" \
    -fill '#2b2140' -draw "rectangle 0,$((h / 2 - band / 2)) $w,$((h / 2 + band / 2))" \
    -font "$FONT" -pointsize $((band * 6 / 10)) -fill white -gravity center -annotate +0+0 'New Look!' \
    "$DIR/1-new-look.png"
  rm "$DIR/classic.png"
}

# Two games in one store screenshot: each board cropped to a white-edged card, the two fanned a few degrees apart on
# the dark page, each game's name large in its own corner (A top-left, B bottom-right).
# Usage: DIR=... FONT=.../fredoka_bold.ttf; pair A B OUT LABEL_A LABEL_B
# Phone (1080x1920) or tablet (2560x1440) is read from game-A's width. Needs ImageMagick 6 only.
pair() {
  local A=$1 B=$2 OUT=$3 LA=$4 LB=$5 T W H g cw ch pt ga gb
  T=$(mktemp -d)
  if [ "$(identify -format %w "$DIR/game-$A.png")" -gt 2000 ]; then
    W=2560 H=1440 g=1860x1370+700+70 cw=1060 ch=930 pt=130 ga=-580+60 gb=+580-70
  else
    W=1080 H=1920 g=1080x1430+0+250 cw=860 ch=760 pt=120 ga=-60-390 gb=+60+430
  fi
  _card() { # game degrees out: crop past header/panel/toolbar, trim the page, round white-edged card, shadow, turn
    local page w h pad=24
    page=$(convert "$DIR/game-$1.png" -format '%[pixel:p{6,400}]' info:)
    convert "$DIR/game-$1.png" -crop $g +repage -fuzz 4% -trim +repage -bordercolor "$page" -border $pad \
      -resize "${cw}x${ch}" "$T/b.png"
    w=$(identify -format %w "$T/b.png"); h=$(identify -format %h "$T/b.png")
    convert "$T/b.png" \( -size ${w}x${h} xc:none -fill white -draw "roundrectangle 0,0 $((w-1)),$((h-1)) 40,40" \) \
      -alpha set -compose DstIn -composite \
      \( -size ${w}x${h} xc:none -fill none -stroke white -strokewidth 12 -draw "roundrectangle 6,6 $((w-7)),$((h-7)) 36,36" \) \
      -compose Over -composite "$T/c.png"
    convert "$T/c.png" \( +clone -background '#05000f' -shadow 60x30+0+24 \) +swap -background none -layers merge +repage \
      -background none -rotate "$2" "$3"
  }
  _card "$A" -4 "$T/ca.png"; _card "$B" 4 "$T/cb.png"
  local R=$(( (W<H?W:H) * 2 / 3 ))
  convert -size ${W}x${H} gradient:'#2b2140'-'#130b24' \
    -fill '#961bf0' -draw "circle $W,0 $W,$R" -fill '#d6119b' -draw "circle 0,$H 0,$((H-R))" -blur 0x$((R/4)) \
    "$T/ca.png" -gravity center -geometry $ga -composite \
    "$T/cb.png" -gravity center -geometry $gb -composite \
    -font "$FONT" -pointsize $pt -fill white \
    -gravity northwest -annotate +$((pt/2))+$((pt/3)) "$LA" \
    -gravity southeast -annotate +$((pt/2))+$((pt/3)) "$LB" \
    -alpha off -strip PNG24:"$DIR/$OUT.png"
  rm -rf "$T"
}

# Games in Arcade, two to a shot, the genres with the biggest apps on Play first (docs/play/LISTING.md). No hint
# is left open: a Show me makes its move and closes, which only puts some progress on the board.
games() {
  open_puzzle Sudoku; sleep 2; shot game-sudoku; back
  open_puzzle "Word Wheel"; sleep 2; shot game-wordwheel; back
  open_puzzle Arrows; sleep 2; shot game-arrows; back
  open_puzzle Links; sleep 2; for _ in 1 2 3 4 5; do show_me; done; sleep 2; shot game-links; back
  open_puzzle Sand; sleep 2; for _ in 1 2; do show_me; done; sleep 2; shot game-sand; back
  open_puzzle Nonogram; sleep 2; for _ in 1 2 3 4; do show_me; done; sleep 2; shot game-nonogram; back
  open_puzzle Unblock; sleep 2; for _ in 1 2; do show_me; done; sleep 2; shot game-unblock; back
  open_puzzle Lexicon; sleep 2; type_keys RAISE; type_keys CLOUT; shot game-lexicon; back
  open_puzzle Crossword; sleep 2; shot game-crossword; back
  open_puzzle "Cage Sudoku"; sleep 2; shot game-cages; back
  open_puzzle Tally; sleep 2; shot game-tally; back
  open_puzzle Tents; sleep 2; shot game-tents; back
  open_puzzle Pipes; sleep 2; for _ in 1 2 3 4; do show_me; done; sleep 3; shot game-pipes; back
  open_puzzle Kings; sleep 2; for _ in 1 2; do show_me; done; sleep 2; shot game-kings; back
}

# The set: the home screen, the home screen in Classic over Arcade ("New Look!"), then twelve games two to a shot.
pass() {
  fresh Classic; shot classic
  fresh Arcade; shot home   # README only: the new-look shot already shows Home
  new_look
  games
  pairs
  mkdir -p "$DIR/games"; mv "$DIR"/game-*.png "$DIR/home.png" "$DIR/games/"   # whole screens, for the README copies
}
pairs() {
  pair sudoku lexicon 2-sudoku-lexicon Sudoku Lexicon
  pair wordwheel arrows 3-wordwheel-arrows "Word Wheel" Arrows
  pair links sand 4-links-sand Links Sand
  pair pipes kings 5-pipes-kings Pipes Kings
  pair nonogram unblock 6-nonogram-unblock Nonogram Unblock
  pair crossword tents 7-crossword-tents Crossword Tents
  pair cages tally 8-cages-tally "Cage Sudoku" Tally
}
pass

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
pass

demo exit
adb shell wm size reset
adb shell wm density reset
for l in $LAUNCHERS; do adb shell pm enable $l >/dev/null 2>&1 || true; done
# README copies are half scale: home, three games whole, and the tablet layout
rm -f docs/screenshots/*.png
convert "$OUT/games/home.png" -resize 50% docs/screenshots/home.png
for g in sudoku lexicon wordwheel:word-wheel; do
  convert "$OUT/games/game-${g%%:*}.png" -resize 50% "docs/screenshots/${g##*:}.png"
done
convert "$TABLET/games/game-sudoku.png" -resize 50% docs/screenshots/tablet-sudoku.png
[ -n "${KEEP_GAMES:-}" ] || rm -r "$OUT/games" "$TABLET/games"   # KEEP_GAMES=1 keeps them to rework the pairs
# F-Droid reads its listing images from the repo (fastlane layout), so they travel with the Play set.
F=fastlane/metadata/android/en-US/images
rm -f $F/phoneScreenshots/*.png $F/sevenInchScreenshots/*.png $F/tenInchScreenshots/*.png
mkdir -p $F/sevenInchScreenshots $F/tenInchScreenshots
cp "$OUT"/*.png $F/phoneScreenshots/
cp "$TABLET"/*.png $F/sevenInchScreenshots/
cp "$TABLET"/*.png $F/tenInchScreenshots/
