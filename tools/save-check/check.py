#!/usr/bin/env python3
"""Checks that an installed-for-real APK (the minified release) still loads every frozen save.

usage: check.py app.apk     (one emulator or device attached, a google_apis image so `adb root` works)

SaveCompatibilityTest proves the debug JVM build reads app/src/test/resources/saves/*.json; this runs the same
fixtures through the APK that ships, where R8 has had its way with the serializers. Per batch it writes the saves
into the app's DataStore file as root (written loosely spaced, unlike the app's compact JSON), opens Sudoku and
backs out. Leaving an untouched board calls ProgressStore.clearSavedGame, which decodes every stored game and
writes back what it read. The file is pulled and every save must come back compact (proof the app rewrote it) and
equal, as JSON, to its fixture: SavedGame.decode is lenient, so a broken save shows up as missing or as a lost field,
not as a crash.

The app is installed fresh and the device's date is pinned (see main), so the board opened is never a fixture's.
"""
import glob, json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.joebywan.daybook"
STORE = f"/data/data/{PKG}/files/datastore/daybook.preferences_pb"
KEY = "saved_games"  # ProgressStore.KEY_SAVED
OFFERED = "tutorials_offered"  # ProgressStore.KEY_TUTORIALS_OFFERED: Sudoku's walkthrough seen, so it never opens over the board
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def adb(*args, check=True, binary=False):
    out = subprocess.run(["adb", *args], capture_output=True, check=check).stdout
    return out if binary else out.decode()


# Preferences DataStore's file is a protobuf PreferenceMap: map<string, Value> = 1; Value.string_set = 6; StringSet.strings = 1.
def ld(field, payload):
    n, v = len(payload), bytes([field << 3 | 2])
    while True:
        v += bytes([n & 0x7F | (0x80 if n > 0x7F else 0)])
        n >>= 7
        if not n:
            return v + payload


def encode(key, strings):
    value = ld(6, b"".join(ld(1, s.encode()) for s in strings))
    return ld(1, ld(1, key.encode()) + ld(2, value))


def fields(buf):
    i = 0
    while i < len(buf):
        tag = shift = 0
        while True:
            b = buf[i]; i += 1; tag |= (b & 0x7F) << shift; shift += 7
            if b < 0x80: break
        wire = tag & 7
        if wire == 0:
            while buf[i] & 0x80: i += 1
            i += 1; yield tag >> 3, None
        elif wire in (1, 5):
            i += 8 if wire == 1 else 4; yield tag >> 3, None
        elif wire == 2:
            n = shift = 0
            while True:
                b = buf[i]; i += 1; n |= (b & 0x7F) << shift; shift += 7
                if b < 0x80: break
            yield tag >> 3, buf[i:i + n]; i += n
        else:
            raise ValueError(f"wire type {wire}")


def decode(buf):
    for f, entry in fields(buf):
        e = dict(fields(entry)) if f == 1 else {}
        if e.get(1) == KEY.encode():
            return [s.decode() for _, s in fields(dict(fields(e[2]))[6])]
    return []


def find(text):
    raw = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
    if "<hierarchy" not in raw: return None
    for n in ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>") + 12]).iter("node"):
        if text in (n.get("text"), n.get("content-desc")):
            x0, y0, x1, y1 = map(int, re.findall(r"\d+", n.get("bounds")))
            if y1 > y0: return (x0 + x1) // 2, (y0 + y1) // 2
    return None


def tap(text):
    for _ in range(20):
        xy = find(text)
        if xy:
            adb("shell", "input", "tap", *map(str, xy)); return
        # A slow CI emulator often shows "Pixel Launcher isn't responding" over everything; let it wait.
        if wait := find("Wait"): adb("shell", "input", "tap", *map(str, wait))
        time.sleep(2)
    print(re.findall(r'text="([^"]+)"', adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)))
    sys.exit(f"save-check: no '{text}' on screen")


def run(batch):
    adb("shell", "am", "force-stop", PKG)
    injected = [json.dumps(e, separators=(", ", ": ")) for e in batch.values()]
    local = os.path.join(os.environ.get("RUNNER_TEMP", "/tmp"), "save-check.pb")
    with open(local, "wb") as f: f.write(encode(KEY, injected) + encode(OFFERED, ["sudoku"]))
    adb("push", local, "/data/local/tmp/save-check.pb")
    owner = adb("shell", f"stat -c %u:%g /data/data/{PKG}").strip()
    context = adb("shell", f"stat -c %C /data/data/{PKG}").strip()
    adb("shell", f"rm -rf /data/data/{PKG}/files && mkdir -p {os.path.dirname(STORE)} && cp /data/local/tmp/save-check.pb {STORE}"
                 f" && chown -R {owner} /data/data/{PKG}/files && chcon -R {context} /data/data/{PKG}/files")

    for _ in range(10):                 # a CI emulator has been seen to set its clock back from the network
        adb("shell", "date", "092412002026.00")
        time.sleep(1)
        if adb("shell", "date", "+%F").strip() == "2026-09-24": break
    else:
        sys.exit(f"save-check: could not pin the date; device says {adb('shell', 'date').strip()}")
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    tap("Sudoku")
    time.sleep(4)                       # the clock ticks and the 500 ms save debounce passes: clearSavedGame runs
    adb("shell", "input", "keyevent", "4")
    time.sleep(2)
    adb("shell", "am", "force-stop", PKG)
    if adb("shell", "date", "+%F").strip() != "2026-09-24":
        sys.exit(f"save-check: the device's clock moved during the run ({adb('shell', 'date').strip()}); nothing was checked")

    stored = decode(adb("exec-out", f"cat {STORE}", binary=True))
    if set(stored) & set(injected):
        sys.exit("save-check: the app never rewrote the saves, so nothing was checked")
    back = {}
    for s in stored:
        e = json.loads(s); back[e["key"]] = e
    bad = [k for k, e in batch.items() if back.get(k) != e]
    for k in bad:
        print(f"::error::{'lost' if k not in back else 'changed'} in the release build: {k}")
    return len(batch) - len(bad), bad


def main():
    apk = sys.argv[1]
    adb("root"); time.sleep(2); adb("wait-for-device")
    while adb("shell", "getprop", "sys.boot_completed").strip() != "1": time.sleep(1)
    adb("uninstall", PKG, check=False)
    adb("install", "-r", apk)
    adb("shell", "settings", "put", "global", "hide_error_dialogs", "1")
    # The fixtures are 2026-10-22's boards. Opening one would save it as played, and that save trims the store to
    # SavedGames.KEEP, losing saves for real; so the day is pinned to one no fixture has (capture.sh's).
    adb("shell", "settings", "put", "global", "auto_time", "0")
    adb("shell", "settings", "put", "global", "auto_time_zone", "0")

    # Every distinct save, in batches whose keys differ (later captures repeat earlier boards).
    batches = []
    for path in sorted(glob.glob(os.path.join(ROOT, "app/src/test/resources/saves/*.json"))):
        for e in json.load(open(path)):
            b = next((b for b in batches if b.get(e["key"], e) == e), None)
            if b is None: batches.append(b := {})
            b[e["key"]] = e
    ok, bad = 0, []
    for b in batches:
        o, x = run(b); ok += o; bad += x
    print(f"save-check: {ok} saves loaded unchanged, {len(bad)} lost or changed, in {len(batches)} batch(es)")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
