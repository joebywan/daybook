# F-Droid

Two routes, both fed by the same listing files in `fastlane/metadata/android/en-US/` (title, descriptions,
icon, feature graphic, screenshots). `tools/screenshots/capture.sh` refreshes the screenshots there along
with Play's, so the **Update screenshots** workflow updates every store at once. The text is copied from
`docs/play/LISTING.md`; edit both when the wording changes.

## 1. Our own repo (live with the next merge to `main`)

`https://knowhowit.com.au/daybook/fdroid/repo`. In the F-Droid app: Settings > Repositories > add that address.

- `release.yml` calls `publish-fdroid.yml` after each GitHub Release. It takes the release's APK, checks it with
  `verify-apk.sh`, adds it to the repo, signs the index and pushes `repo/` to the `fdroid` branch (a single commit,
  rewritten each time, newest five APKs). It then runs `pages.yml`, which copies that branch into the site.
  `tools/fdroid/build-repo.sh` is the part that can be run locally (`pip install fdroidserver`).
- **Same key as Play and GitHub**, so an F-Droid install updates in place over those. The index is signed with the
  release key too, so the repo fingerprint is `android/release-key.sha256`. Reusing the key needed no new secret; a
  dedicated index key would be cleaner if this ever matters.
- No new secrets. The workflow uses the existing `ANDROID_KEYSTORE_*` ones.
- The repo's own page (`.../fdroid/repo/index.html`) shows a QR code and the fingerprint to hand out.

## 2. f-droid.org (submitted 2026-10-03: [fdroiddata!51019](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51019))

F-Droid's main repo hosts only free software and builds from source.

- **Licence:** the code is AGPL-3.0-or-later (`LICENSE`), which F-Droid accepts, and the entry says so.
  `docs/word-lists/LICENSE-SCOWL.txt` has to travel with the word lists.
- **Formatting is checked by their CI** (`fdroid rewritemeta`, a newer version and wider wrap than pip's): the `prebuild` lines are plain sed with no quoting for that reason. If the job fails, its log prints the diff it wants.
- **Reproducible, so it updates in place.** The entry has `Binaries:` (the APK on our GitHub Release) and
  `AllowedAPKSigningKeys:` (the release certificate). F-Droid builds the tag, compares its APK with ours byte for byte
  apart from the signature, and if they match publishes *our* signed APK. Without that F-Droid signs with its own key
  and Android refuses to update a Play or GitHub install with it. Checked 2026-10-03: `assembleRelease` of a release's
  commit on a different machine and JDK than CI gave an APK identical to the release's in all 67 non-signature files.
  **The tag must be the commit that was built.** `release.yml` once tagged the branch head instead (a later merge
  landed while the run was queued), so v0.1.100 to v0.1.102 do not match their APKs and cannot verify; the entry
  must name a release made after `--target "$GITHUB_SHA"` was added. F-Droid's scanner also rejects an APK with an extra signing block, which AGP adds unless `dependenciesInfo` is switched off in `app/build.gradle.kts` (it is; first in v0.1.104). Debug a mismatch by diffing the two APKs'
  contents file by file (zip entries outside `META-INF/`), then `dexdump` for the dex.

`com.joebywan.daybook.yml` is the entry as submitted in that merge request to
[gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) (file `metadata/com.joebywan.daybook.yml`).
`fdroid lint` passes except for the category list (which only exists in fdroiddata's own
checkout). It has **not** been through `fdroid build`, which needs F-Droid's build server, so expect the reviewers to ask
for changes. After that it is automatic: `UpdateCheckMode: HTTP` reads the newest
release tag, and F-Droid's bot adds the build entry and publishes a few days later.
The version code and name are the Actions run number, which the build reads from the environment; the entry's `prebuild`
bakes the substituted values into the defaults, and removes the `web/` module F-Droid cannot build.
