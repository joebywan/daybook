#!/usr/bin/env python3
"""Sets the Play listing's title, short description and full description from
fastlane/metadata/android/en-US/ (title.txt, short_description.txt, full_description.txt), the same
files F-Droid reads, so one edit updates both stores.

Needs PLAY_SERVICE_ACCOUNT_JSON in the environment (the service account needs "Manage store
presence", as tools/screenshots/upload-play.py does) and
`pip install google-api-python-client google-auth`. `--check` only validates the files against
Play's limits and needs neither. The change goes to Google review; the live listing keeps its old
text until it passes.
"""
import json, os, sys, time

PKG, LANG = "com.joebywan.daybook", "en-AU"   # the listing's default language, as the screenshots use
SRC = os.path.join(os.path.dirname(__file__), "../../fastlane/metadata/android/en-US")
LIMITS = {"title": 30, "short_description": 80, "full_description": 4000}


def read():
    text = {}
    for name, limit in LIMITS.items():
        with open(os.path.join(SRC, name + ".txt"), encoding="utf-8") as f:
            value = f.read().strip()
        assert value, f"{name}.txt is empty"
        assert len(value) <= limit, f"{name}.txt is {len(value)} characters; Play allows {limit}"
        text[name] = value
    return text


def upload(text):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"])
    edits = build("androidpublisher", "v3", credentials=creds).edits()
    edit = edits.insert(packageName=PKG, body={}).execute()["id"]
    edits.listings().update(packageName=PKG, editId=edit, language=LANG, body={
        "language": LANG,
        "title": text["title"],
        "shortDescription": text["short_description"],
        "fullDescription": text["full_description"],
    }).execute()
    edits.commit(packageName=PKG, editId=edit).execute()


text = read()
print({k: len(v) for k, v in text.items()})
if "--check" in sys.argv:
    sys.exit(0)
# A release merged at the same time opens its own edit, and Play allows one open edit per app,
# so a clash is retried rather than treated as a failure.
for attempt in range(1, 4):
    try:
        upload(text)
        print("listing text uploaded")
        break
    except Exception as e:
        if attempt == 3:
            raise
        print(f"attempt {attempt} failed ({e}); retrying in 90 s")
        time.sleep(90)
