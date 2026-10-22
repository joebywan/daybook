#!/usr/bin/env python3
"""Replaces the Play listing's screenshots: docs/play/screenshots/*.png (phone) and
docs/play/tablet-screenshots/*.png (landscape tablet, both the 7-inch and the 10-inch slot), sorted by name.

Needs PLAY_SERVICE_ACCOUNT_JSON in the environment (the service account needs "Manage store
presence") and `pip install google-api-python-client google-auth`. The change goes to Google review;
the live listing keeps its old images until it passes.
"""
import glob, json, os
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PKG, LANG = "com.joebywan.daybook", "en-AU"
DOCS = os.path.join(os.path.dirname(__file__), "../../docs/play")
phone = sorted(glob.glob(os.path.join(DOCS, "screenshots/*.png")))
tablet = sorted(glob.glob(os.path.join(DOCS, "tablet-screenshots/*.png")))
for name, files in (("phone", phone), ("tablet", tablet)):
    assert 2 <= len(files) <= 8, f"Play takes 2-8 screenshots, found {len(files)} {name}"
slots = {"phoneScreenshots": phone, "sevenInchScreenshots": tablet, "tenInchScreenshots": tablet}

creds = service_account.Credentials.from_service_account_info(
    json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
    scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds).edits()
edit = edits.insert(packageName=PKG, body={}).execute()["id"]
for kind, files in slots.items():
    edits.images().deleteall(packageName=PKG, editId=edit, language=LANG, imageType=kind).execute()
    for f in files:
        edits.images().upload(packageName=PKG, editId=edit, language=LANG, imageType=kind,
                              media_body=MediaFileUpload(f, mimetype="image/png")).execute()
edits.commit(packageName=PKG, editId=edit).execute()
print(f"uploaded {len(phone)} phone and {len(tablet)} tablet screenshots")
