# Google Play listing

Everything the Play Console asks for that is not a build, ready to paste. Assets live beside this
file; the account steps are in the README under "Publishing to Google Play". Limits are Play's
(title 30, short description 80, full description 4000 characters).

Keep the wording free of a puzzle count (it ages) and of any newspaper's or game show's brand name
for a puzzle.

## Store listing

**App name** (max 30): `Daybook: Daily Logic Puzzles`

**Short description** (max 80):

```
New logic puzzles every day. No ads, no subscription, works offline.
```

**Full description** (max 4000):

```
Daybook is a daily logic-puzzle app for people who like to think.

Every day brings a fresh set of classic logic puzzles: Sudoku, region and placement puzzles, number and symbol grids, pipe networks, flood-fills, one-line paths and code-breaking. Each comes in three difficulties, Standard, Hard and Expert, and each is built so that it has exactly one answer you can reach by reasoning. Nothing needs guessing.

NO ADS. NO SUBSCRIPTION. NO ACCOUNT.
There are no adverts, no paywall, no sign-in and no in-app purchases. The app does not even ask for network access, so it works on a plane, on a train and in a tunnel.

THE WHOLE ARCHIVE, FROM DAY ONE
Puzzles are made on your device from the date, so every past day is already there. Missed a week? Play it. Want something different? Switch to Random boards at any difficulty.

LEARN AS YOU GO
Stuck? Ask for a hint and Daybook explains the next step in plain language, pointing at the cells it is talking about, rather than just filling one in. Every puzzle has a short walkthrough for first-timers, and "How to play" is always one tap away.

KEEP YOUR STREAK
Track your daily streak, your times and your statistics, all stored privately on your phone.

PRIVATE BY DESIGN
Daybook collects nothing. No analytics, no trackers, no third-party SDKs. Your progress never leaves your device.

Hand-built puzzle generators, proven boards and a calm, uncluttered design. Come back tomorrow for the next one.
```

**Category:** Game → Puzzle. **Tags:** puzzle, logic, brain games (pick what the console offers).

**Contact details:** email is the owner's choice (Play shows it publicly). Website
`https://knowhowit.com.au/daybook/` (the web version). **Privacy policy URL:**
`https://github.com/joebywan/daybook/blob/main/PRIVACY.md`

## Graphics

| Slot | File | Spec |
|---|---|---|
| App icon | `../../android/play-icon-512.png` | 512x512 |
| Feature graphic | `feature-graphic.png` | 1024x500, no alpha (`python3 docs/social-preview/make.py`) |
| Phone screenshots | `screenshots/1-home.png` ... `5-snap.png` | 1080x1920, 9:16 (the first set was 2:1, which Play also accepted). The emulator's own 1080x2400 is over 2:1 |
| Tablet screenshots (7-inch and 10-inch) | the same five | Both slots are marked required in the Console, so the phone shots are reused. The app is portrait-only, so a tablet shows the phone layout |

The screenshots are emulator captures made by `tools/screenshots/capture.sh` (`wm size 1080x1920`, a clean install, the date pinned to 24 Sep 2026, demo-mode
status bar: 9:00, no notifications). Run the **Update screenshots** workflow (Actions, manual): it captures, uploads to the listing with `tools/screenshots/upload-play.py` (the service account needs "Manage store presence"; Google reviews the change and the live listing keeps the old images meanwhile) and opens a PR for the `docs/` images, which makes them part of the same "recapture when
the look changes" duty as `docs/screenshots/`. The 7-inch and 10-inch tablet slots were filled with the same five.

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
- **Target API / architecture:** targetSdk 36, minSdk 26. The only native libraries are AndroidX's
  (`graphics.path`, `datastore_shared_counter`) and each ships arm64-v8a and x86_64 alongside the
  32-bit builds, so Play's 64-bit requirement is met; check again if a dependency ever drops them.
