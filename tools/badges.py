#!/usr/bin/env python3
"""Writes the README's version badges into the Pages site: <out>/badges/{web,play,fdroid}.json, each a
shields.io endpoint (https://shields.io/badges/endpoint-badge), so every channel reads the same 1.x.y.

  web     the release tag of the commit being deployed (`git describe`; "+n" when n commits past it,
          which a deploy straight after the release replaces). Needs the tags fetched.
  fdroid  our own repo's newest APK, from the copy of the `fdroid` branch the deploy adds (--fdroid).
  fdroid.org  f-droid.org's suggested version, from its public API (it builds from source, days behind).
  play    the production track's newest release, read through an edit that is deleted, never
          committed. Needs PLAY_SERVICE_ACCOUNT_JSON and google-api-python-client; without them, or on
          any error, the badge says "unknown" rather than failing the deploy. Google reviews each
          update, so this is what was sent, which can be a day ahead of what the store shows.

A badge behind the newest release tag is orange, else blue; compared on the last number, the release's
run number, which is also the version code (so a Play release with no name still compares).

usage: badges.py <site dir> [--fdroid <repo dir>]
       badges.py --web-version   (prints the web's version alone)
"""
import json, os, subprocess, sys, urllib.request

PKG = "com.joebywan.daybook"


def web():
    tag = subprocess.run(["git", "describe", "--tags", "--match", "v*"],
                         capture_output=True, text=True, check=True).stdout.strip()
    base, _, rest = tag[1:].partition("-")          # v1.50.388-2-gabc1234 -> 1.50.388, 2-gabc1234
    return base + (f" +{rest.split('-')[0]}" if rest else "")


def fdroid(repo):
    with open(os.path.join(repo, "index-v1.json"), encoding="utf-8") as f:
        apks = json.load(f)["packages"][PKG]
    return max(apks, key=lambda a: a["versionCode"])["versionName"]


def fdroid_org():
    url = f"https://f-droid.org/api/v1/packages/{PKG}"
    with urllib.request.urlopen(url, timeout=20) as r:
        info = json.load(r)
    return next(p["versionName"] for p in info["packages"] if p["versionCode"] == info["suggestedVersionCode"])


def latest():
    """The newest release's run number, or None without tags (then nothing is called behind)."""
    tags = subprocess.run(["git", "tag", "--list", "v*"], capture_output=True, text=True).stdout.split()
    runs = [int(t.rsplit(".", 1)[-1]) for t in tags if t.rsplit(".", 1)[-1].isdigit()]
    return max(runs, default=None)


def run_number(version):
    return int(str(version).split()[0].rsplit(".", 1)[-1])


def play():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"])
    edits = build("androidpublisher", "v3", credentials=creds).edits()
    edit = edits.insert(packageName=PKG, body={}).execute()["id"]
    try:
        track = edits.tracks().get(packageName=PKG, editId=edit, track="production").execute()
    finally:
        edits.delete(packageName=PKG, editId=edit).execute()
    newest = max((r for r in track.get("releases", []) if r.get("versionCodes")),
                 key=lambda r: max(map(int, r["versionCodes"])))
    return newest.get("name") or newest["versionCodes"][-1]


def badge(out, name, label, read, newest):
    try:
        message = read()
        color = "orange" if newest is not None and run_number(message) < newest else "blue"
    except Exception as e:                           # a badge never stops the site deploying
        print(f"{name}: {e!r}", file=sys.stderr)
        message, color = "unknown", "lightgrey"
    with open(os.path.join(out, f"{name}.json"), "w", encoding="utf-8") as f:
        json.dump({"schemaVersion": 1, "label": label, "message": message, "color": color}, f)
    print(f"{label}: {message}")


if "--web-version" in sys.argv:                     # pages.yml stamps the web build with the same version
    print(web())
    sys.exit()
site = sys.argv[1]
repo = sys.argv[sys.argv.index("--fdroid") + 1] if "--fdroid" in sys.argv else None
out = os.path.join(site, "badges")
os.makedirs(out, exist_ok=True)
newest = latest()
badge(out, "web", "web", web, newest)
badge(out, "play", "Google Play", play, newest)
badge(out, "fdroid", "F-Droid (own repo)", lambda: fdroid(repo), newest)
badge(out, "fdroid-org", "f-droid.org", fdroid_org, newest)
