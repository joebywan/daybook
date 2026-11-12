#!/usr/bin/env python3
"""Sets the Play listing's screenshots to docs/play/screenshots/*.png (phone) and
docs/play/tablet-screenshots/*.png (landscape tablet, both the 7-inch and the 10-inch slot), sorted by name.

A slot whose images already match (Play keeps each image's sha256, in order) is left alone, and when every
slot matches nothing is committed, so a run with the same pictures never sends the listing to review.
`--check` only counts the files and needs no credentials.

Needs PLAY_SERVICE_ACCOUNT_JSON in the environment (the service account needs "Manage store
presence") and `pip install google-api-python-client google-auth`. The change goes to Google review;
the live listing keeps its old images until it passes.
"""
import glob, hashlib, json, os, sys, time

PKG, LANG = "com.joebywan.daybook", "en-AU"
DOCS = os.path.join(os.path.dirname(__file__), "../../docs/play")
phone = sorted(glob.glob(os.path.join(DOCS, "screenshots/*.png")))
tablet = sorted(glob.glob(os.path.join(DOCS, "tablet-screenshots/*.png")))
for name, files in (("phone", phone), ("tablet", tablet)):
    assert 2 <= len(files) <= 8, f"Play takes 2-8 screenshots, found {len(files)} {name}"
print(f"{len(phone)} phone and {len(tablet)} tablet screenshots")
if "--check" in sys.argv:
    sys.exit(0)

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

slots = {"phoneScreenshots": phone, "sevenInchScreenshots": tablet, "tenInchScreenshots": tablet}


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def upload():
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"])
    edits = build("androidpublisher", "v3", credentials=creds).edits()
    edit = edits.insert(packageName=PKG, body={}).execute()["id"]
    changed = []
    for kind, files in slots.items():
        held = edits.images().list(packageName=PKG, editId=edit, language=LANG, imageType=kind).execute()
        if [i.get("sha256") for i in held.get("images", [])] == [sha256(f) for f in files]:
            continue
        changed.append(kind)
        edits.images().deleteall(packageName=PKG, editId=edit, language=LANG, imageType=kind).execute()
        for f in files:
            edits.images().upload(packageName=PKG, editId=edit, language=LANG, imageType=kind,
                                  media_body=MediaFileUpload(f, mimetype="image/png")).execute()
    if not changed:
        edits.delete(packageName=PKG, editId=edit).execute()
        print("Play already has these screenshots; nothing uploaded")
        return
    edits.commit(packageName=PKG, editId=edit).execute()
    print("uploaded", ", ".join(changed))


# A release merged at the same time opens its own edit, and Play allows one open edit per app,
# so a clash is retried rather than treated as a failure (as tools/play/upload-listing.py does).
for attempt in range(1, 4):
    try:
        upload()
        break
    except Exception as e:
        if attempt == 3:
            raise
        print(f"attempt {attempt} failed ({e}); retrying")
        time.sleep(60)
