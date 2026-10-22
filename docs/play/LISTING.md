# Google Play listing

Everything the Play Console asks for that is not a build. Assets live beside this
file; the account steps are in the README under "Publishing to Google Play". Limits are Play's
(title 30, short description 80, full description 4000 characters).

Keep the wording free of a puzzle count (it ages) and of any newspaper's or game show's brand name
for a puzzle.

**The text lives in `fastlane/metadata/android/en-US/`** (`title.txt`, `short_description.txt`,
`full_description.txt`), the files F-Droid reads. Change it there: merging to `main` runs the **Play listing
text** workflow (`tools/play/upload-listing.py`), which pushes it to Play, and pull requests check the limits.
Do not edit the text in Play Console; the next run overwrites it.

**Search terms.** "Daybook" alone also finds a popular diary app, so the title says "Daybook Puzzles" and spends the rest
on the most searched genre (sudoku). The short description and the first line of the full one carry the other terms
people type: daily, nonogram, word, logic, no ads, offline. Genre names that are generic (killer sudoku, futoshiki, star
battle, takuzu, hashi) may be named as "-style" descriptions of our puzzles; product names (a card game, a board game,
a newspaper's or app's puzzle) may not. Play's policy keeps "free" and "no ads" out of the title.

## Store listing

**App name, short description, full description:** `fastlane/metadata/android/en-US/` (see above).

**Category:** Game → Puzzle. **Tags:** puzzle, logic, brain games (pick what the console offers).

**Contact details:** email is the owner's choice (Play shows it publicly). Website
`https://knowhowit.com.au/daybook/` (the web version). **Privacy policy URL:**
`https://github.com/joebywan/daybook/blob/main/PRIVACY.md`

## Graphics

| Slot | File | Spec |
|---|---|---|
| App icon | `../../android/play-icon-512.png` | 512x512 |
| Feature graphic | `feature-graphic.png` | 1024x500, no alpha (`python3 docs/social-preview/make.py`) |
| Phone screenshots | `screenshots/1-home.png` ... `7-snap.png` | 1080x1920, 9:16 (the first set was 2:1, which Play also accepted). The emulator's own 1080x2400 is over 2:1 |
| Tablet screenshots (7-inch and 10-inch) | `tablet-screenshots/1-home.png` ... `7-snap.png` | 2560x1440 landscape at 320 dpi (a 1280x720 dp window, so the board sits beside the controls). One set fills both slots |

The screenshots are emulator captures made by `tools/screenshots/capture.sh` (`wm size 1080x1920`, then 2560x1440 for the tablet set, a clean install, the date pinned to 24 Sep 2026, demo-mode
status bar: 9:00, no notifications). Run the **Update screenshots** workflow (Actions, manual): it captures, uploads to the listing with `tools/screenshots/upload-play.py` (the service account needs "Manage store presence"; Google reviews the change and the live listing keeps the old images meanwhile) and opens a PR for the `docs/` images, which makes them part of the same "recapture when
the look changes" duty as `docs/screenshots/`.

## Declarations

- **App access:** all functionality is available without logging in or any special access.
- **Ads:** no.
- **Content rating (IARC questionnaire, Game):** no violence, no sexual content, no profanity, no
  controlled substances, no gambling or simulated gambling, no user-generated content, no
  user-to-user interaction, no location sharing, no digital purchases. Expect Everyone / PEGI 3.
- **Target audience:** 13 and over (13-15, 16-17, 18+). Not designed for children, which avoids the
  Families policy requirements; there is nothing in the app that would fail them, so this is a choice
  and not a necessity.
- **Data safety:** "Does your app collect or share any of the required user data types?" **No.**
  Data is not encrypted in transit because it is never transmitted: the manifest has no `INTERNET`
  permission. Account creation: none. Data deletion: not applicable (uninstalling removes
  everything). Auto Backup is on for the progress file (`app/src/main/res/xml/backup_rules.xml`);
  that is the device's own backup to the user's Google account, not collection by the developer.
- **Permissions:** none requested.
- **Government app, financial features, health features, news app:** no to all.
- **Advertising ID:** the app does not use it (it declares no permission for it).
- **Target API / architecture:** targetSdk 36, minSdk 25 (Android 7.1; `java.time` is desugared). The only native libraries are AndroidX's
  (`graphics.path`, `datastore_shared_counter`) and each ships arm64-v8a and x86_64 alongside the
  32-bit builds, so Play's 64-bit requirement is met; check again if a dependency ever drops them.
